package io.github.conorryan.whistletabber.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.conorryan.whistletabber.render.TunePage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

const val APP_ORIGIN = "https://appassets.androidplatform.net"
const val PAGE_URL = "$APP_ORIGIN/assets/web/index.html"
private const val BRIDGE_NAME = "WhistleBridge"
private const val TAG = "TuneWebView"

/**
 * Routes the page's origin: bundled files under /assets/, generated whistle
 * samples under /sound/. Nothing else resolves, and the page never touches the
 * network.
 */
fun buildAssetLoader(context: Context, sound: SoundPathHandler = SoundPathHandler()): WebViewAssetLoader =
    WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .addPathHandler("/sound/", sound)
        .build()

/**
 * Owns one WebView running whistle-page.js. Commands sent before the page
 * reports ready are queued; events arrive on [events].
 */
class TuneWebController(
    context: Context,
    private val sound: SoundPathHandler = SoundPathHandler(),
) : TunePage {
    private val _events = MutableSharedFlow<BridgeEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<BridgeEvent> = _events.asSharedFlow()

    private val pending = ArrayDeque<BridgeCommand>()
    private var ready = false

    val webView: WebView = createWebView(context)

    override fun send(command: BridgeCommand) {
        if (!ready) {
            // A newer render replaces any queued one: only the latest tune matters.
            if (command is BridgeCommand.Render) pending.removeAll { it is BridgeCommand.Render }
            pending.addLast(command)
            return
        }
        webView.evaluateJavascript(BridgeCodec.script(command), null)
    }

    fun destroy() {
        webView.evaluateJavascript(BridgeCodec.script(BridgeCommand.Stop), null)
        webView.destroy()
    }

    /** Called on the main thread with each raw message from the page. */
    private fun onMessage(message: String) {
        val event = BridgeCodec.decode(message)
        if (event == null) {
            Log.w(TAG, "Ignoring unknown message: $message")
            return
        }
        if (event is BridgeEvent.Ready) {
            ready = true
            while (pending.isNotEmpty()) send(pending.removeFirst())
        }
        _events.tryEmit(event)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context): WebView {
        val loader = buildAssetLoader(context, sound)
        // Debug builds only: lets chrome://inspect (or adb + DevTools protocol) see the page.
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        return WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    loader.shouldInterceptRequest(request.url)

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.addWebMessageListener(this, BRIDGE_NAME, setOf(APP_ORIGIN)) { _, message, _, _, _ ->
                    message.data?.let(::onMessage)
                }
            } else {
                addJavascriptInterface(LegacyBridge(), BRIDGE_NAME)
            }
            loadUrl(PAGE_URL)
        }
    }

    /** Fallback for WebViews without WEB_MESSAGE_LISTENER; same postMessage shape. */
    private inner class LegacyBridge {
        @JavascriptInterface
        fun postMessage(message: String) {
            webView.post { onMessage(message) }
        }
    }
}
