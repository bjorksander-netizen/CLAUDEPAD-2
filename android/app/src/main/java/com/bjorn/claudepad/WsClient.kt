package com.bjorn.claudepad

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Klien WebSocket ke server CLAUDEPAD.
 *
 * Sejak v3.0 lalu lintas terenkripsi: alur handshake-nya
 *   1. onOpen  -> kirim {"t":"hello"} (plaintext)
 *   2. server balas {"t":"hello_ok","salt":...}
 *   3. turunkan kunci sesi dari PIN/token + salt, kirim {"t":"auth"} (plaintext)
 *   4. server balas auth_ok, lalu SEMUA pesan berikutnya biner terenkripsi.
 */
object WsClient {

    private fun buildClient(host: String): OkHttpClient {
        val b = OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .connectTimeout(8, TimeUnit.SECONDS)
        // Ikat ke interface yang satu subnet dengan PC, supaya paket tidak
        // lari ke jaringan seluler saat HP menjadi hotspot.
        Net.localAddressFor(host)?.let { local ->
            boundVia = local.hostAddress ?: ""
            b.socketFactory(Net.BoundSocketFactory(local))
        } ?: run { boundVia = "" }
        return b.build()
    }

    @Volatile var boundVia: String = ""
        private set

    /** true bila lalu lintas terenkripsi (v3.0+). */
    @Volatile var encrypted = false
        private set
    private var crypto: CryptoBox? = null

    private var ws: WebSocket? = null

    @Volatile var connected = false
        private set

    @Volatile var hostName: String = "—"
        private set
    @Volatile var transport: String = "—"
        private set
    @Volatile var serverVersion: String = "—"
        private set
    @Volatile var macAddress: String = ""
        private set
    @Volatile var volume: Int = 50

    @Volatile var pingMs: Int = -1
        private set
    private var pingSentAt = 0L
    var onPing: ((Int) -> Unit)? = null

    var onState: ((Boolean, String) -> Unit)? = null
    var onNewToken: ((String) -> Unit)? = null
    var onMessage: ((JSONObject) -> Unit)? = null

    private var lastHost = ""
    private var lastPort = 8765
    private var lastPin = ""
    private var lastToken = ""
    private var lastVersion = ""
    private var retryCount = 0
    private var manualClose = false

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
                crypto = null
                encrypted = false
                webSocket.send(JSONObject().put("t", "hello").toString())
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val box = crypto ?: return
                val plain = try { String(box.open(bytes.toByteArray())) }
                            catch (e: Exception) { return }
                handleJson(plain, webSocket, pin, token, appVersion)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleJson(text, webSocket, pin, token, appVersion)
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

    private fun handleJson(text: String, webSocket: WebSocket,
                           pin: String, token: String, appVersion: String) {
        val o = try { JSONObject(text) } catch (e: Exception) { return }
        when (o.optString("t")) {
            "hello_ok" -> {
                // Turunkan kunci sesi dari rahasia yang sudah sama-sama
                // dimiliki (PIN atau token pairing), lalu kirim auth.
                try {
                    val salt = hexToBytes(o.optString("salt"))
                    val secret = if (token.isNotEmpty()) token else pin
                    crypto = CryptoBox.derive(secret, salt)
                } catch (e: Exception) { crypto = null }
                val auth = JSONObject().put("t", "auth")
                    .put("pin", pin).put("ver", appVersion)
                if (token.isNotEmpty()) auth.put("token", token)
                webSocket.send(auth.toString())      // auth tetap plaintext
            }
            "auth_ok" -> {
                connected = true
                hostName = o.optString("host", "PC")
                transport = o.optString("transport", "wifi")
                serverVersion = o.optString("version", "—")
                if (!o.isNull("vol")) volume = o.optInt("vol", 50)
                macAddress = o.optString("mac", "")
                encrypted = o.optBoolean("encrypted", false)
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
                    pingMs = (System.currentTimeMillis() - pingSentAt).toInt().coerceAtMost(9999)
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

    private fun hexToBytes(hex: String): ByteArray {
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            out[i] = ((Character.digit(hex[i * 2], 16) shl 4) +
                      Character.digit(hex[i * 2 + 1], 16)).toByte()
        }
        return out
    }

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
        crypto = null
        encrypted = false
        closeQuietly()
    }

    // ---------------------------------------------------------------- kirim --
    private fun send(o: JSONObject) {
        val sock = ws ?: return
        val box = crypto
        if (box != null && encrypted) {
            try { sock.send(box.seal(o.toString().toByteArray()).toByteString()) }
            catch (e: Exception) { sock.send(o.toString()) }
        } else {
            sock.send(o.toString())
        }
    }

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
    fun radio(device: String) = send(JSONObject().put("t", "radio").put("d", device))
    fun power(action: String) = send(JSONObject().put("t", "power").put("a", action))
    fun volSet(v: Int) = send(JSONObject().put("t", "volset").put("v", v))
    fun volGet() = send("volget")

    fun measurePing() {
        if (ws == null) return
        val now = System.currentTimeMillis()
        if (pingSentAt > 0L && now - pingSentAt < 4000) return
        pingSentAt = now
        send("ping")
    }
}
