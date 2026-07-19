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

class MainActivity : AppCompatActivity() {

    private lateinit var etIp: EditText
    private lateinit var etPin: EditText
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Haptics.init(this)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply { blurBehindRadius = 70 }
            } catch (e: Exception) { }
        }
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
        WsClient.connect(host, 8765, pin)
    }

    private fun scan() {
        tvStatus.text = "mencari pc di jaringan…"
        Thread {
            var sock: DatagramSocket? = null
            try {
                sock = DatagramSocket()
                sock.broadcast = true
                sock.soTimeout = 2500
                val msg = "DISCOVER_CLAUDEPAD".toByteArray()
                sock.send(DatagramPacket(msg, msg.size,
                    InetAddress.getByName("255.255.255.255"), 8766))
                val buf = ByteArray(256)
                val resp = DatagramPacket(buf, buf.size)
                sock.receive(resp)
                val parts = String(resp.data, 0, resp.length).split("|")
                if (parts.isNotEmpty() && parts[0] == "CLAUDEPAD") {
                    val ip = resp.address.hostAddress ?: ""
                    runOnUiThread {
                        etIp.setText(ip)
                        Haptics.heavy()
                        tvStatus.text = "ketemu: ${parts.getOrNull(1) ?: ""} ($ip)"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { tvStatus.text = "tidak ketemu — pastikan server jalan & satu jaringan" }
            } finally {
                sock?.close()
            }
        }.start()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
