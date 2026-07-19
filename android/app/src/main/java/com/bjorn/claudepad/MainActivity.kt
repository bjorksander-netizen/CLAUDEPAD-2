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
import java.net.NetworkInterface

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

        tvStatus.text = "menghubungkan ke $host…"
        WsClient.onState = { ok, msg ->
            runOnUiThread {
                tvStatus.text = msg
                if (ok) {
                    Haptics.heavy()
                    startActivity(Intent(this, ControlActivity::class.java))
                } else {
                    Haptics.light()
                }
            }
        }
        WsClient.connect(host, 8765, pin, appVersion())
    }

    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
    } catch (e: Exception) { "0" }

    private fun scan() {
        tvStatus.text = "mencari pc di jaringan…"
        Thread {
            var found = false
            var sock: DatagramSocket? = null
            try {
                sock = DatagramSocket()
                sock.broadcast = true
                sock.soTimeout = 1800
                val msg = "DISCOVER_CLAUDEPAD".toByteArray()

                // target: broadcast global + broadcast tiap interface (hotspot
                // sering tidak meneruskan 255.255.255.255)
                val targets = mutableSetOf<InetAddress>()
                try { targets.add(InetAddress.getByName("255.255.255.255")) } catch (e: Exception) {}
                try {
                    val ifs = NetworkInterface.getNetworkInterfaces()
                    while (ifs.hasMoreElements()) {
                        val ni = ifs.nextElement()
                        if (!ni.isUp || ni.isLoopback) continue
                        for (ia in ni.interfaceAddresses) {
                            ia.broadcast?.let { targets.add(it) }
                        }
                    }
                } catch (e: Exception) {}

                val buf = ByteArray(256)
                attempts@ for (attempt in 1..3) {
                    for (t in targets) {
                        try { sock.send(DatagramPacket(msg, msg.size, t, 8766)) }
                        catch (e: Exception) {}
                    }
                    try {
                        val resp = DatagramPacket(buf, buf.size)
                        sock.receive(resp)
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
                    } catch (e: Exception) { /* timeout percobaan ini, ulangi */ }
                }
            } catch (e: Exception) {
            } finally {
                sock?.close()
            }
            if (!found) runOnUiThread {
                tvStatus.text = "tidak ketemu — cek firewall pc (jalankan ulang " +
                    "start_server.bat) & pastikan satu jaringan"
            }
        }.start()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
