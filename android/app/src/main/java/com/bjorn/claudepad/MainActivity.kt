package com.bjorn.claudepad

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

class MainActivity : AppCompatActivity() {

    private lateinit var etIp: EditText
    private lateinit var etPin: EditText
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Haptics.init(this)
        Glass.apply(this, findViewById(R.id.rootMain))
        Accent.refresh()
        Accent.applyToKey(findViewById(R.id.btnConnect))

        etIp = findViewById(R.id.etIp)
        etPin = findViewById(R.id.etPin)
        tvStatus = findViewById(R.id.tvStatus)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
        etIp.setText(Prefs.ip(this))
        etPin.setText(Prefs.pin(this))

        findViewById<TextView>(R.id.btnConnect).setOnClickListener {
            Haptics.medium()
            connect(etIp.text.toString().trim())
        }
        findViewById<TextView>(R.id.btnUsb).setOnClickListener {
            // Mode USB: PC menjalankan "adb reverse tcp:8765 tcp:8765",
            // jadi server terlihat di 127.0.0.1 dari sisi HP.
            Haptics.medium()
            connect("127.0.0.1")
        }
        findViewById<TextView>(R.id.btnScan).setOnClickListener {
            Haptics.medium()
            scan()
        }
        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            Haptics.light()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        Fonts.apply(findViewById(R.id.rootMain))
    }

    override fun onResume() {
        super.onResume()
        Haptics.enabled = Prefs.hapticEnabled(this)
        // Ambil alih kembali callback dari ControlActivity supaya status
        // koneksi tetap tampil di halaman ini.
        WsClient.onState = { ok, msg ->
            runOnUiThread { tvStatus.text = msg }
        }
        WsClient.onMessage = null
    }

    private fun connect(host: String) {
        if (host.isEmpty()) {
            toast("isi alamat ip dulu, atau tekan cari otomatis")
            return
        }
        val pin = etPin.text.toString().trim()
        Prefs.setIp(this, etIp.text.toString().trim())
        Prefs.setPin(this, pin)

        tvStatus.text = if (Prefs.token(this).isEmpty())
            "menghubungkan ke $host…" else "menghubungkan ($host)…"
        WsClient.onState = { ok, msg ->
            runOnUiThread {
                tvStatus.text = msg
                if (ok) {
                    Haptics.heavy()
                    RemoteService.start(this)
                    startActivity(Intent(this, ControlActivity::class.java))
                } else {
                    Haptics.light()
                }
            }
        }
        WsClient.onNewToken = { t -> Prefs.setToken(this, t) }
        WsClient.connect(host, 8765, pin, appVersion(), Prefs.token(this))
    }

    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
    } catch (e: Exception) { "0" }

    private fun scan() {
        tvStatus.text = "mencari pc di jaringan…"
        Thread {
            var found = false
            // Kirim dari SETIAP interface dengan socket yang TERIKAT ke alamat
            // interface itu. Tanpa pengikatan, Android mengirim broadcast lewat
            // jaringan seluler saat HP jadi hotspot, sehingga PC tak pernah
            // menerima paket pencarian.
            val sockets = mutableListOf<DatagramSocket>()
            try {
                val ifaces = Net.interfaces()
                for (i in ifaces) {
                    try {
                        val ds = DatagramSocket(null)
                        ds.reuseAddress = true
                        ds.broadcast = true
                        ds.bind(InetSocketAddress(i.address, 0))
                        ds.soTimeout = 700
                        sockets.add(ds)
                    } catch (e: Exception) { }
                }
                if (sockets.isEmpty()) {
                    sockets.add(DatagramSocket().apply { broadcast = true; soTimeout = 700 })
                }

                val msg = "DISCOVER_CLAUDEPAD".toByteArray()
                val buf = ByteArray(256)

                attempts@ for (attempt in 1..3) {
                    for ((idx, ds) in sockets.withIndex()) {
                        val targets = mutableSetOf<InetAddress>()
                        ifaces.getOrNull(idx)?.broadcast?.let { targets.add(it) }
                        try { targets.add(InetAddress.getByName("255.255.255.255")) } catch (e: Exception) {}
                        for (t in targets) {
                            try { ds.send(DatagramPacket(msg, msg.size, t, 8766)) }
                            catch (e: Exception) { }
                        }
                    }
                    for (ds in sockets) {
                        try {
                            val resp = DatagramPacket(buf, buf.size)
                            ds.receive(resp)
                            val parts = String(resp.data, 0, resp.length).split("|")
                            if (parts.isNotEmpty() && parts[0] == "CLAUDEPAD") {
                                val ip = resp.address.hostAddress ?: ""
                                found = true
                                runOnUiThread {
                                    etIp.setText(ip)
                                    Haptics.heavy()
                                    tvStatus.text = "ketemu: ${parts.getOrNull(1) ?: ""} ($ip)"
                                }
                                break@attempts
                            }
                        } catch (e: Exception) { /* timeout socket ini */ }
                    }
                }
            } catch (e: Exception) {
            } finally {
                for (ds in sockets) try { ds.close() } catch (e: Exception) {}
            }
            if (!found) runOnUiThread {
                tvStatus.text = "tidak ketemu — tekan ⚙ lalu Diagnosa koneksi"
            }
        }.start()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
