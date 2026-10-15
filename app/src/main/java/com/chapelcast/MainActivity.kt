package com.chapelcast

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import fi.iki.elonen.NanoHTTPD
import java.net.NetworkInterface

fun findIp(): String {
    val addrs = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp }
        .flatMap { n -> n.inetAddresses.toList()
            .filter { !it.isLoopbackAddress && it.hostAddress?.contains('.') == true }
            .map { n.name to it.hostAddress!! } }
    return (addrs.firstOrNull { it.first.matches(Regex("(wlan|ap|swlan|eth).*")) }
        ?: addrs.firstOrNull())?.second ?: ""
}

class Server(private val act: Activity, port: Int) : NanoHTTPD(port) {
    @Volatile private var state = """{"mode":"full","theme":"t-night","text":"","ref":""}"""
    private val page: String by lazy { act.assets.open("index.html").bufferedReader().readText() }

    override fun serve(s: IHTTPSession): Response {
        if (s.uri == "/state") return newFixedLengthResponse(Response.Status.OK, "application/json", state)
        if (s.uri == "/push" && s.method == Method.POST) {
            val body = HashMap<String, String>(); s.parseBody(body)
            state = body["postData"] ?: state
            return newFixedLengthResponse("ok")
        }
        // the phone itself gets the controller; every other device gets the output screen
        val local = s.remoteIpAddress == "127.0.0.1"
        val html = if (local && s.uri != "/output") page
        else page.replace("const OUT=/output|view=output/.test(location.href);", "const OUT=true;")
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }
}

class Bridge { @JavascriptInterface fun ip(): String = findIp() }

class MainActivity : Activity() {
    private lateinit var server: Server
    private var cb: ValueCallback<Array<Uri>>? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        server = Server(this, 8080).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
        val wv = WebView(this)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.addJavascriptInterface(Bridge(), "Android")
        wv.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(w: WebView, c: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                cb?.onReceiveValue(null); cb = c
                val i = Intent(Intent.ACTION_GET_CONTENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
                startActivityForResult(Intent.createChooser(i, "Choose Bible XML"), 1)
                return true
            }
        }
        wv.loadUrl("http://localhost:8080/")
        setContentView(wv)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        if (req == 1) { cb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data)); cb = null }
        else super.onActivityResult(req, res, data)
    }

    override fun onDestroy() { server.stop(); super.onDestroy() }
}
