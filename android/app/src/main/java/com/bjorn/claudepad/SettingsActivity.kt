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
            v2.2
            • Perbaikan WiFi/Hotspot & cari otomatis: server kini membuka
              firewall Windows otomatis; pencarian memakai broadcast
              per-interface dengan 3 percobaan
            • Blur background diperbaiki: blur asli (Android 12+) plus
              lapisan frost yang bekerja di semua perangkat
            • Warna aksen kini diambil dari WallpaperColors (Android 8.1+),
              bukan hanya Material You
            • Slider intensitas blur & slider kekuatan haptic di setting
            • Fitur backspace dikembalikan (tombol ⌫ + hapus di kolom ketik)
            • Kunci versi: koneksi ditolak bila versi APK ≠ versi server
            • Tombol putuskan koneksi (⏻) di bar atas & di jendela server

            v2.1
            • Perbaikan fatal: trackpad hilang karena panel bawah
              melahap seluruh tinggi layar — tinggi baris bawah kini tetap
            • Panel D-Pad disamakan ukurannya dengan panel media
            • Slider volume diperbaiki (inisialisasi COM audio di server)
              plus mode cadangan bila server tanpa pycaw
            • Menu Advance kini pop-up persegi, bukan baris dropdown
            • Wallpaper di belakang aplikasi kini diblur (Android 12+)
            • Fitur orientasi diluruskan: hanya arah INPUT trackpad yang
              berputar 90°, layout tidak berubah sama sekali
            • Warna aksen mengikuti wallpaper perangkat (Material You)

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
        Glass.apply(this, findViewById(R.id.rootSettings))

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

        bindRotationRow()
    }

    /** Rotasi input berputar antara 0°, 90°, dan 270°. */
    private fun bindRotationRow() {
        val label = findViewById<TextView>(R.id.tvOrientation)
        fun render() {
            val deg = Prefs.inputRotation(this)
            label.text = if (deg == 0) "normal" else "diputar ${deg}°"
        }
        render()
        findViewById<View>(R.id.rowOrientation).setOnClickListener {
            Haptics.medium()
            Prefs.setInputRotation(this, Prefs.nextRotation(Prefs.inputRotation(this)))
            render()
        }
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

        // ---- blur background ----
        val tvBlur = findViewById<TextView>(R.id.tvBlur)
        val seekBlur = findViewById<SeekBar>(R.id.seekBlur)
        seekBlur.progress = Prefs.blurIntensity(this)
        tvBlur.text = "${seekBlur.progress}%"
        seekBlur.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                tvBlur.text = "$p%"
                if (fromUser) {
                    Prefs.setBlurIntensity(this@SettingsActivity, p)
                    // pratinjau langsung di halaman ini
                    Glass.apply(this@SettingsActivity, findViewById(R.id.rootSettings))
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) { Haptics.light() }
        })

        // ---- kekuatan haptic ----
        val tvH = findViewById<TextView>(R.id.tvHapticStr)
        val seekH = findViewById<SeekBar>(R.id.seekHaptic)
        seekH.progress = (Prefs.hapticStrength(this) * 100).toInt().coerceIn(0, 200)
        tvH.text = "${seekH.progress}%"
        seekH.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                tvH.text = "$p%"
                if (fromUser) {
                    val str = p / 100f
                    Prefs.setHapticStrength(this@SettingsActivity, str)
                    Haptics.strength = str
                    Haptics.medium()   // rasakan langsung kekuatannya
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
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
        findViewById<View>(R.id.rowDiagnose).setOnClickListener {
            Haptics.medium()
            runDiagnostic()
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

    /** Jalankan diagnosa di thread terpisah lalu tampilkan laporannya. */
    private fun runDiagnostic() {
        val dlg = AlertDialog.Builder(this)
            .setTitle("diagnosa koneksi")
            .setMessage("menjalankan pemeriksaan…")
            .setCancelable(false)
            .create()
        dlg.show()
        val ip = Prefs.ip(this)
        Thread {
            val report = try {
                Diagnostic.run(this, ip)
            } catch (e: Exception) {
                "Diagnosa gagal: ${e.message}"
            }
            runOnUiThread {
                dlg.dismiss()
                AlertDialog.Builder(this)
                    .setTitle("diagnosa koneksi")
                    .setMessage(report)
                    .setPositiveButton("tutup", null)
                    .setNeutralButton("salin") { _, _ ->
                        val cm = getSystemService(CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        cm.setPrimaryClip(
                            android.content.ClipData.newPlainText("diagnosa", report))
                        android.widget.Toast.makeText(this, "laporan disalin",
                            android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
        }.start()
    }

    private fun showText(title: String, body: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton("tutup", null)
            .show()
    }
}
