package com.chapelcast

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.*
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL
import java.util.zip.ZipInputStream

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
    private val mediaDir = File(act.filesDir, "chapelcast-media").apply { mkdirs() }
    private val page: String by lazy {
        act.assets.open("index.html").bufferedReader().readText()
            .replace("</head>", "<link rel=\"stylesheet\" href=\"/studio-v13.css\"></head>")
            .replace("</body>", "<script src=\"/media-suite-v12.js\"></script><script src=\"/studio-v13.js\"></script></body>")
    }

    override fun serve(s: IHTTPSession): Response {
        if (s.uri == "/state") return newFixedLengthResponse(Response.Status.OK, "application/json", state)
        if (s.uri == "/push" && s.method == Method.POST) {
            val body = HashMap<String, String>(); s.parseBody(body)
            state = body["postData"] ?: state
            return newFixedLengthResponse("ok")
        }
        if (s.uri == "/dl") {
            val f = File(act.cacheDir, "dl.xml")
            if (!f.exists()) return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "No download available")
            return newChunkedResponse(Response.Status.OK, "text/plain; charset=utf-8", FileInputStream(f))
        }
        if (s.uri == "/media-suite-v12.js" || s.uri == "/studio-v13.js" || s.uri == "/studio-v13.css") {
            val name = s.uri.removePrefix("/")
            val type = if (name.endsWith(".css")) "text/css; charset=utf-8" else "application/javascript; charset=utf-8"
            val data = act.assets.open(name).bufferedReader().readText()
            return newFixedLengthResponse(Response.Status.OK, type, data)
        }
        if (s.uri == "/media-save" && s.method == Method.POST) {
            return try {
                val body = HashMap<String, String>(); s.parseBody(body)
                val json = JSONObject(body["postData"] ?: "{}")
                val id = json.optString("id")
                val mime = json.optString("mime", "image/jpeg")
                if (!id.matches(Regex("[A-Za-z0-9-]{1,90}")) ||
                    mime !in setOf("image/jpeg", "image/png", "image/webp", "image/gif")) {
                    throw IllegalArgumentException("Unsupported media identifier or image type")
                }
                val bytes = Base64.decode(json.getString("data"), Base64.DEFAULT)
                if (bytes.size > 8 * 1024 * 1024) throw IllegalArgumentException("Image exceeds 8 MB")
                File(mediaDir, id).writeBytes(bytes)
                File(mediaDir, "$id.mime").writeText(mime)
                newFixedLengthResponse(Response.Status.OK, "text/plain", "saved")
            } catch (e: Exception) {
                newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", e.message ?: "Invalid media")
            }
        }
        if (s.uri == "/media-delete" && s.method == Method.POST) {
            return try {
                val body = HashMap<String, String>(); s.parseBody(body)
                val id = JSONObject(body["postData"] ?: "{}").optString("id")
                if (!id.matches(Regex("[A-Za-z0-9-]{1,90}"))) throw IllegalArgumentException("Invalid media identifier")
                File(mediaDir, id).delete(); File(mediaDir, "$id.mime").delete()
                newFixedLengthResponse("deleted")
            } catch (e: Exception) { newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid media identifier") }
        }
        if (s.uri.startsWith("/media/")) {
            val id = s.uri.removePrefix("/media/")
            if (!id.matches(Regex("[A-Za-z0-9-]{1,90}"))) return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid media identifier")
            val f = File(mediaDir, id)
            if (!f.isFile) return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Media not found")
            val mimeFile = File(mediaDir, "$id.mime")
            val mime = if (mimeFile.isFile) mimeFile.readText() else "image/jpeg"
            return newFixedLengthResponse(Response.Status.OK, mime, FileInputStream(f), f.length())
        }
        val ra = s.remoteIpAddress ?: ""
        val local = ra == "127.0.0.1" || ra == "::1" || ra.startsWith("0:0:0:0:0:0:0:1") || ra == findIp()
        val html = if (local && s.uri != "/output") page
        else page.replace("const OUT=/output|view=output/.test(location.href);", "const OUT=true;")
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }
}

class Bridge(private val dir: File) {
    @Volatile private var st = "idle"
    @JavascriptInterface fun ip(): String = findIp()
    @JavascriptInterface fun status(): String = st
    @JavascriptInterface fun download(url: String) {
        st = "busy"
        Thread {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 60000
                c.instanceFollowRedirects = true
                if (c.responseCode !in 200..299) throw Exception("HTTP " + c.responseCode)
                val bytes = c.inputStream.use { it.readBytes() }
                val out = File(dir, "dl.xml")
                if (bytes.size > 3 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
                    ZipInputStream(bytes.inputStream()).use { z ->
                        var e = z.nextEntry
                        while (e != null && !e.name.lowercase().endsWith(".xml")) e = z.nextEntry
                        if (e == null) throw Exception("No XML file inside the zip")
                        out.writeBytes(z.readBytes())
                    }
                } else out.writeBytes(bytes)
                st = "done"
            } catch (e: Exception) { st = "error: " + (e.message ?: e.javaClass.simpleName) }
        }.start()
    }
}

class MainActivity : Activity() {
    private lateinit var server: Server
    private var cb: ValueCallback<Array<Uri>>? = null
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        server = Server(this, 8080).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
        val wv = WebView(this)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.addJavascriptInterface(Bridge(cacheDir), "Android")
        wv.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(w: WebView, c: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                cb?.onReceiveValue(null); cb = c
                val i = Intent(Intent.ACTION_GET_CONTENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
                startActivityForResult(Intent.createChooser(i, "Choose Bible XML or backup"), 1)
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