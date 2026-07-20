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

    private fun buildClient(host: String): OkHttpClient {
        val b = OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .connectTimeout(8, TimeUnit.SECONDS)
        // Ikat ke interface yang satu subnet dengan PC. Tanpa ini, saat HP
        // jadi hotspot sambil data seluler aktif, Android mengirim paket
        // lewat seluler dan koneksi selalu gagal.
        Net.localAddressFor(host)?.let { local ->
            boundVia = local.hostAddress ?: ""
            b.socketFactory(Net.BoundSocketFactory(local))
        } ?: run { boundVia = "" }
        return b.build()
    }

    /** Alamat lokal yang dipakai untuk koneksi terakhir (untuk diagnosa). */
    @Volatile var boundVia: String = ""
        private set

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
    /** MAC PC untuk Wake-on-LAN (fitur eksperimental). */
    @Volatile var macAddress: String = ""
        private set
    @Volatile var volume: Int = 50

    /** Ping terakhir dalam ms; -1 kalau belum terukur. */
    @Volatile var pingMs: Int = -1
        private set

    private var pingSentAt = 0L
    /** Dipanggil setiap kali ping baru terukur. */
    var onPing: ((Int) -> Unit)? = null

    var onState: ((Boolean, String) -> Unit)? = null
    /** Dipanggil saat server memberi token pairing baru untuk disimpan. */
    var onNewToken: ((String) -> Unit)? = null
    var onMessage: ((JSONObject) -> Unit)? = null

    // --- data koneksi terakhir, untuk menyambung ulang otomatis ---
    private var lastHost = ""
    private var lastPort = 8765
    private var lastPin = ""
    private var lastToken = ""
    private var lastVersion = ""
    private var retryCount = 0
    private var manualClose = false

    /** Sambung ulang otomatis saat koneksi putus sesaat. */
    var autoReconnect = true
    var onReconnecting: ((Int) -> Unit)? = null

    fun connect(host: String, port: Int, pin: String, appVersion: String,
                token: String = "") {
        lastHost = host; lastPort = port; lastPin = pin
        lastVersion = appVersion; lastToken = token
        manualClose = false
        openSocket()
    }

    private fun openSocket() {
        val host = lastHost
        val port = lastPort
        val pin = lastPin
        val token = lastToken
        val appVersion = lastVersion
        closeQuietly()
        val req = Request.Builder().url("ws://$host:$port/ws").build()
        ws = buildClient(host).newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val auth = JSONObject().put("t", "auth")
                    .put("pin", pin).put("ver", appVersion)
                if (token.isNotEmpty()) auth.put("token", token)
                webSocket.send(auth.toString())
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
                        macAddress = o.optString("mac", "")
                        val fresh = o.optString("token", "")
                        if (fresh.isNotEmpty()) onNewToken?.invoke(fresh)
                        retryCount = 0
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
                    "pong" -> {
                        if (pingSentAt > 0L) {
                            pingMs = (System.currentTimeMillis() - pingSentAt).toInt()
                                .coerceAtMost(9999)
                            pingSentAt = 0L
                            PingLog.record(pingMs, transport, hostName)
                            onPing?.invoke(pingMs)
                        }
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
                val raw = t.message ?: "koneksi gagal"
                val msg = if (raw.contains("failed to connect", true) ||
                              raw.contains("ETIMEDOUT", true)) {
                    "tidak sampai ke pc — tekan ⚙ lalu Diagnosa koneksi"
                } else raw
                if (!tryReconnect()) onState?.invoke(false, msg)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                if (!tryReconnect()) onState?.invoke(false, "terputus")
            }
        })
    }

    /**
     * Coba sambung ulang dengan jeda menaik. Putus karena WiFi tersendat
     * sesaat tidak lagi melempar pengguna keluar dari layar trackpad.
     */
    private fun tryReconnect(): Boolean {
        if (manualClose || !autoReconnect || lastHost.isEmpty()) return false
        if (retryCount >= 5) return false
        retryCount++
        val delay = (retryCount * 900L).coerceAtMost(4000L)
        onReconnecting?.invoke(retryCount)
        android.os.Handler(android.os.Looper.getMainLooper())
            .postDelayed({ if (!manualClose && !connected) openSocket() }, delay)
        return true
    }

    private fun closeQuietly() {
        ws?.close(1000, null)
        ws = null
    }

    fun disconnect() {
        manualClose = true
        connected = false
        pingMs = -1
        pingSentAt = 0L
        retryCount = 0
        closeQuietly()
    }

    private fun send(o: JSONObject) { ws?.send(o.toString()) }
    private fun send(t: String) = send(JSONObject().put("t", t))

    fun move(dx: Int, dy: Int) = send(JSONObject().put("t", "move").put("dx", dx).put("dy", dy))

    fun click(b: String, double: Boolean = false) =
        send(JSONObject().put("t", "click").put("b", b).put("double", double))

    fun buttonDown(b: String) = send(JSONObject().put("t", "down").put("b", b))
    fun buttonUp(b: String) = send(JSONObject().put("t", "up").put("b", b))

    fun scroll(dy: Int) = send(JSONObject().put("t", "scroll").put("dy", dy))
    fun scrollHorizontal(dx: Int) = send(JSONObject().put("t", "scroll").put("dx", dx))
    fun zoom(dir: Int) = send(JSONObject().put("t", "zoom").put("dir", dir))
    fun gesture(g: String) = send(JSONObject().put("t", "gesture").put("g", g))

    fun text(s: String) = send(JSONObject().put("t", "text").put("s", s))

    fun key(k: String, mods: List<String> = emptyList()) {
        val o = JSONObject().put("t", "key").put("k", k)
        if (mods.isNotEmpty()) o.put("mods", JSONArray(mods))
        send(o)
    }

    fun media(a: String) = send(JSONObject().put("t", "media").put("a", a))

    /** Nyalakan/matikan radio PC: "wifi", "bluetooth", atau "hotspot". */
    fun radio(device: String) = send(JSONObject().put("t", "radio").put("d", device))

    /** shutdown / restart / sleep / lock / screenoff / logoff / hibernate. */
    fun power(action: String) = send(JSONObject().put("t", "power").put("a", action))

    fun volSet(v: Int) = send(JSONObject().put("t", "volset").put("v", v))
    fun volGet() = send("volget")

    /** Kirim ping untuk mengukur latensi. Diabaikan bila ping sebelumnya belum dibalas. */
    fun measurePing() {
        if (ws == null) return
        val now = System.currentTimeMillis()
        if (pingSentAt > 0L && now - pingSentAt < 4000) return   // masih menunggu balasan
        pingSentAt = now
        send("ping")
    }
}
