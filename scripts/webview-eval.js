#!/usr/bin/env node
/**
 * Debug helper: evaluates a JS expression in the app's WebView page on the
 * emulator through the DevTools protocol and prints the result as JSON.
 *
 * Needs a debug build and a forwarded port:
 *   PID=$(adb -s emulator-5554 shell pidof io.github.conorryan.whistletabber.debug | tr -d '\r')
 *   adb -s emulator-5554 forward tcp:9222 localabstract:webview_devtools_remote_$PID
 * Run: node scripts/webview-eval.js "document.title"
 */
const expression = process.argv[2];
if (!expression) {
    console.error("usage: webview-eval.js <expression>");
    process.exit(2);
}

(async () => {
    const pages = await (await fetch("http://localhost:9222/json")).json();
    const page = pages.find((p) => p.url.includes("appassets.androidplatform.net"));
    if (!page) throw new Error("No Whistle Tabber page found");
    const ws = new WebSocket(page.webSocketDebuggerUrl);
    ws.onopen = () => ws.send(JSON.stringify({
        id: 1,
        method: "Runtime.evaluate",
        params: { expression, returnByValue: true, awaitPromise: true },
    }));
    ws.onmessage = (msg) => {
        const data = JSON.parse(msg.data);
        if (data.id !== 1) return;
        const result = data.result.exceptionDetails ? data.result.exceptionDetails : data.result.result.value;
        console.log(JSON.stringify(result, null, 2));
        ws.close();
    };
})().catch((e) => {
    console.error(e.message);
    process.exit(1);
});
