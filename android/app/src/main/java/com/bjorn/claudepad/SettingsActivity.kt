package com.bjorn.claudepad

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val README_URL = "https://github.com/bjorksander-netizen/CLAUDEPAD#readme"

        val CHANGELOG = """
            v2.0
            • Tema baru bergaya Control Center: panel kaca tembus pandang
              yang memperlihatkan wallpaper HP
            • Gesture Windows Precision Touchpad lengkap:
              2 jari scroll & pinch zoom, 3 jari untuk Task View,
              Show Desktop, dan ganti aplikasi
            • Slider volume absolut menggantikan tombol mute
            • D-Pad ala DualShock untuk tombol arah, dengan auto-repeat
            • Getaran haptic bertingkat sesuai bobot aksi
            • Tombol ubah orientasi (vertikal / horizontal)
            • Halaman Setting baru
            • Tombol Esc, Tab, Win, Del dikelompokkan ke menu Advance
            • Alt+Tab diganti Win+Tab
            • Semua tombol memakai simbol fungsi
            • Font monospace JetBrains Mono
            • Fitur clipboard sync dan backspace dihapus

            v1.0
            • Rilis pertama: trackpad, keyboard, clipboard sync,
              media control, koneksi WiFi/Hotspot & USB
        """.trimIndent()

        val GESTURE_GUIDE = """
            1 jari geser        gerakkan kursor
            1 jari tap          klik kiri
            2 jari tap          klik kanan
            2 jari geser        scroll
            2 jari cubit        zoom (Ctrl + scroll)
            3 jari ke atas      Task View
            3 jari ke bawah     Show Desktop
            3 jari kiri/kanan   ganti aplikasi
            tap 2x lalu tahan   drag & drop
        """.trimIndent()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        Haptics.init(this)

        bindConnectionInfo()
        bindToggles()
        bindSensitivity()
        bindAbout()

        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            Haptics.light()
            finish()
        }
        findViewById<TextView>(R.id.btnDisconnect).setOnClickListener {
            Haptics.heavy()
            WsClient.disconnect()
            val i = Intent(this, MainActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(i)
            finish()
        }

        Fonts.apply(findViewById(R.id.rootSettings))
    }

    private fun bindConnectionInfo() {
        val connected = WsClient.connected
        findViewById<TextView>(R.id.tvConnStatus).apply {
            text = if (connected) "terhubung" else "terputus"
            setTextColor(getColor(if (connected) R.color.green else R.color.red))
        }
        findViewById<TextView>(R.id.tvTransport).text =
            if (!connected) "—" else when (WsClient.transport) {
                "usb" -> "kabel usb"
                "wifi" -> "wifi / hotspot"
                else -> WsClient.transport
            }
        findViewById<TextView>(R.id.tvHost).text = if (connected) WsClient.hostName else "—"
        findViewById<TextView>(R.id.tvServerVer).text =
            if (connected) "v${WsClient.serverVersion}" else "—"
    }

    private fun toggleRow(rowId: Int, labelId: Int, get: () -> Boolean, set: (Boolean) -> Unit,
                          onText: String = "aktif", offText: String = "nonaktif") {
        val label = findViewById<TextView>(labelId)
        fun render() { label.text = if (get()) onText else offText }
        render()
        findViewById<View>(rowId).setOnClickListener {
            set(!get())
            Haptics.medium()
            render()
        }
    }

    private fun bindToggles() {
        toggleRow(R.id.rowHaptic, R.id.tvHaptic,
            { Prefs.hapticEnabled(this) },
            { v -> Prefs.setHaptic(this, v); Haptics.enabled = v })

        toggleRow(R.id.rowNatural, R.id.tvNatural,
            { Prefs.naturalScroll(this) },
            { v -> Prefs.setNaturalScroll(this, v) })

        toggleRow(R.id.rowAwake, R.id.tvAwake,
            { Prefs.keepAwake(this) },
            { v -> Prefs.setKeepAwake(this, v) })

        toggleRow(R.id.rowOrientation, R.id.tvOrientation,
            { Prefs.landscape(this) },
            { v -> Prefs.setLandscape(this, v) },
            onText = "horizontal", offText = "vertikal")
    }

    private fun bindSensitivity() {
        val tv = findViewById<TextView>(R.id.tvSens)
        val seek = findViewById<SeekBar>(R.id.seekSens)
        // progress 0..40  ->  sensitivitas 0.5x .. 4.5x
        fun toSens(p: Int) = 0.5f + p / 10f
        val current = Prefs.sensitivity(this)
        seek.progress = ((current - 0.5f) * 10f).toInt().coerceIn(0, 40)
        tv.text = String.format("%.1fx", current)

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                val s = toSens(p)
                tv.text = String.format("%.1fx", s)
                if (fromUser) {
                    Prefs.setSensitivity(this@SettingsActivity, s)
                    Haptics.tick()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) { Haptics.light() }
        })
    }

    private fun bindAbout() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "—"
        } catch (e: Exception) { "—" }
        findViewById<TextView>(R.id.tvVersion).text = "v$version"

        findViewById<View>(R.id.rowChangelog).setOnClickListener {
            Haptics.light()
            showText("change log", CHANGELOG)
        }
        findViewById<View>(R.id.rowGesture).setOnClickListener {
            Haptics.light()
            showText("panduan gesture", GESTURE_GUIDE)
        }
        findViewById<View>(R.id.rowHelp).setOnClickListener {
            Haptics.light()
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(README_URL)))
            } catch (e: Exception) {
                showText("bantuan", "Tidak ada browser.\n\nBuka manual:\n$README_URL")
            }
        }
    }

    private fun showText(title: String, body: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton("tutup", null)
            .show()
    }
}
