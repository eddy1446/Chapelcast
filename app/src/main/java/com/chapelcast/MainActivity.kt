// Android Studio: new "Empty Views Activity" (Kotlin), minSdk 21.
// app/build.gradle:  implementation "org.nanohttpd:nanohttpd:2.3.1"
// Manifest: <uses-permission android:name="android.permission.INTERNET"/>
//           <uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
//           <application android:usesCleartextTraffic="true" ...>
// Copy ../index.html to app/src/main/assets/index.html
package com.chapelcast

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import fi.iki.elonen.NanoHTTPD
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.NetworkInterface
import java.util.concurrent.CopyOnWriteArrayList

class Server(private val act: Activity, port: Int) : NanoHTTPD(port) {
    @Volatile private var state = """{"mode":"full","theme":"t-night","text":"","ref":""}"""
    private val clients = CopyOnWriteArrayList<PipedOutputStream>()

    override fun serve(s: IHTTPSession): Response = when {
        s.uri == "/state" -> newFixedLengthResponse(Response.Status.OK, "application/json", state)
        s.uri == "/push" && s.method == Method.POST -> {
            val body = HashMap<String, String>(); s.parseBody(body)
            state = body["postData"] ?: state
            val line = "data: ${state.replace("\n", "")}\n\n".toByteArray()
            clients.forEach { try { it.write(line); it.flush() } catch (e: Exception) { clients.remove(it) } }
            newFixedLengthResponse("ok")
        }
        s.uri == "/events" -> {
            val out = PipedOutputStream(); val inp = PipedInputStream(out, 65536)
            clients.add(out)
            newChunkedResponse(Response.Status.OK, "text/event-stream", inp).apply {
                addHeader("Cache-Control", "no-cache")
            }
        }
        else -> newFixedLengthResponse(Response.Status.OK, "text/html",
            act.assets.open("index.html").bufferedReader().readText())   // "/" and "/output" both serve the app
    }
}

class MainActivity : Activity() {
    private lateinit var server: Server
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        server = Server(this, 8080).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
        val wv = WebView(this)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true       // localStorage + IndexedDB
        wv.loadUrl("http://localhost:8080/")       // operator screen
        setContentView(wv)
        // Output screens (laptop/projector/OBS browser source): http://<ip>:8080/output
        android.widget.Toast.makeText(this, "Output: http://${ip()}:8080/output", android.widget.Toast.LENGTH_LONG).show()
    }
    private fun ip(): String = NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains('.') == true }?.hostAddress ?: "?"
    override fun onDestroy() { server.stop(); super.onDestroy() }
}
