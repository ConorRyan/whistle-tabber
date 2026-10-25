package io.github.conorryan.whistletabber.web

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** Shows the controller's WebView; the controller, not this composable, owns its lifetime. */
@Composable
fun TuneWebViewHost(controller: TuneWebController, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = {
            controller.webView.also { (it.parent as? ViewGroup)?.removeView(it) }
        },
    )
}
