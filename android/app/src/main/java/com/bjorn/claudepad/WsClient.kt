package com.bjorn.claudepad

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object WsClient {

    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(4, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null

    @Volatile var connected = false
        private set

    // Info dari server, dipakai di trackpad & halaman setting
    @Volatile var hostName: String = "—"
        private set
    @Volatile var transport: String = "—"
        private set
    @Volatile var serverVersion: String = "—"
        private set
    @Volatile var volume: Int = 50

    var onState: ((Boolean, String) -> Unit)? = null
    var onMessage: ((JSONObject) -> Unit)? = null

    fun connect(host: String, port: Int, pin: String, appVersion: String) {
        disconnect()
        val req = Request.Builder().url("ws://$host:$port").build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(JSONObject().put("t", "auth")
                    .put("pin", pin).put("ver", appVersion).toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val o = try { JSONObject(text) } catch (e: Exception) { return }
                when (o.optString("t")) {
                    "auth_ok" -> {
                        connected = true
                        hostName = o.optString("host", "PC")
                        transport = o.optString("transport", "wifi")
                        serverVersion = o.optString("version", "—")
                        if (!o.isNull("vol")) volume = o.optInt("vol", 50)
                        onState?.invoke(true, "terhubung")
                    }
                    "auth_fail" -> {
                        connected = false
                        val msg = if (o.optString("reason") == "version") {
                            "versi tidak cocok — server v" + o.optString("server", "?") +
                                ", apk v" + o.optString("app", "?") + ". samakan dulu keduanya."
                        } else "pin salah"
                        onState?.invoke(false, msg)
                        webSocket.close(1000, null)
                    }
                    "vol" -> {
                        if (!o.isNull("v")) volume = o.optInt("v", volume)
                        onMessage?.invoke(o)
                    }
                    else -> onMessage?.invoke(o)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
                onState?.invoke(false, t.message ?: "koneksi gagal")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                onState?.invoke(false, "terputus")
            }
        })
    }

    fun disconnect() {
        connected = false
        ws?.close(1000, null)
        ws = null
    }

    private fun send(o: JSONObject) { ws?.send(o.toString()) }
    private fun send(t: String) = send(JSONObject().put("t", t))

    fun move(dx: Int, dy: Int) = send(JSONObject().put("t", "move").put("dx", dx).put("dy", dy))

    fun click(b: String, double: Boolean = false) =
        send(JSONObject().put("t", "click").put("b", b).put("double", double))

    fun buttonDown(b: String) = send(JSONObject().put("t", "down").put("b", b))
    fun buttonUp(b: String) = send(JSONObject().put("t", "up").put("b", b))

    fun scroll(dy: Int) = send(JSONObject().put("t", "scroll").put("dy", dy))
    fun zoom(dir: Int) = send(JSONObject().put("t", "zoom").put("dir", dir))
    fun gesture(g: String) = send(JSONObject().put("t", "gesture").put("g", g))

    fun text(s: String) = send(JSONObject().put("t", "text").put("s", s))

    fun key(k: String, mods: List<String> = emptyList()) {
        val o = JSONObject().put("t", "key").put("k", k)
        if (mods.isNotEmpty()) o.put("mods", JSONArray(mods))
        send(o)
    }

    fun media(a: String) = send(JSONObject().put("t", "media").put("a", a))

    fun volSet(v: Int) = send(JSONObject().put("t", "volset").put("v", v))
    fun volGet() = send("volget")
}
