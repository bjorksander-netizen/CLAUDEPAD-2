package com.bjorn.claudepad

import android.app.Dialog
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ControlActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var trackpad: TrackpadView
    private lateinit var volumeSlider: VolumeSlider
    private var suppressWatcher = false
    private var advancePopup: PopupWindow? = null

    // fallback volume: kalau server tidak punya pycaw, pakai tombol media
    private var absVolume = true
    private var volErrToasted = false
    private var lastVolSent = -1
    private var lastVolTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control)
        Haptics.init(this)
        Glass.apply(this, findViewById(R.id.rootControl))

        if (Prefs.keepAwake(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        tvStatus = findViewById(R.id.tvStatus)
        trackpad = findViewById(R.id.trackpad)
        volumeSlider = findViewById(R.id.volumeSlider)

        setupConnectionCallbacks()
        setupTrackpad()
        setupMouseButtons()
        setupKeyboard()
        setupShortcuts()
        setupVolume()
        setupMedia()
        setupDpad()
        setupTopBar()
        applyAccent()

        Fonts.apply(findViewById(R.id.rootControl))
    }

    // ---------------------------------------------------------------- visual --
    /** Warna aksen mengikuti wallpaper. */
    private fun applyAccent() {
        Accent.refresh()
        Accent.applyToKey(findViewById(R.id.kEnter))
        Accent.applyToKey(findViewById(R.id.mPlay))
        findViewById<DpadView>(R.id.dpad).accentColor = Accent.bg(this)
    }

    // ---------------------------------------------------------------- setup --
    private fun setupConnectionCallbacks() {
        WsClient.onState = { ok, msg ->
            runOnUiThread {
                tvStatus.text = if (ok) WsClient.hostName else msg
                if (!ok) finish()
            }
        }
        WsClient.onMessage = { o ->
            when (o.optString("t")) {
                "vol" -> if (!o.isNull("v")) {
                    runOnUiThread { volumeSlider.value = o.optInt("v", volumeSlider.value) }
                }
                "volerr" -> runOnUiThread { onVolumeError() }
            }
        }
        val host = WsClient.hostName
        tvStatus.text = "$host · ${WsClient.transport}"
        trackpad.deviceName = host
        setupPing()
    }

    // ---------------------------------------------------------------- ping ---
    private val pingHandler = Handler(Looper.getMainLooper())
    private lateinit var tvPing: TextView
    private var pingRunning = false

    /** Indikator latensi, hanya untuk koneksi WiFi/Hotspot. */
    private fun setupPing() {
        tvPing = findViewById(R.id.tvPing)
        if (WsClient.transport != "wifi") {
            tvPing.visibility = View.GONE
            return
        }
        tvPing.visibility = View.VISIBLE
        tvPing.text = "…"
        WsClient.onPing = { ms -> runOnUiThread { renderPing(ms) } }

        pingRunning = true
        val tick = object : Runnable {
            override fun run() {
                if (!pingRunning || !WsClient.connected) return
                WsClient.measurePing()
                pingHandler.postDelayed(this, 2000)
            }
        }
        pingHandler.post(tick)
    }

    private fun renderPing(ms: Int) {
        tvPing.text = "${ms}ms"
        // warna mengikuti kualitas koneksi
        val color = when {
            ms < 40 -> 0xFF4ADE80.toInt()   // hijau  - sangat baik
            ms < 90 -> 0xFFA3E635.toInt()   // hijau kekuningan - baik
            ms < 180 -> 0xFFFBBF24.toInt()  // kuning - cukup
            ms < 350 -> 0xFFFB923C.toInt()  // jingga - lambat
            else -> 0xFFFF6B6B.toInt()      // merah  - buruk
        }
        tvPing.setTextColor(color)
    }

    private fun setupTrackpad() {
        trackpad.sensitivity = Prefs.sensitivity(this)
        trackpad.naturalScroll = Prefs.naturalScroll(this)
        trackpad.inputRotation = Prefs.inputRotation(this)
        trackpad.pointerLocation = Prefs.pointerLocation(this)
        trackpad.showTaps = Prefs.showTaps(this)
        trackpad.listener = object : TrackpadView.Listener {
            override fun onMove(dx: Int, dy: Int) = WsClient.move(dx, dy)
            override fun onLeftClick() = WsClient.click("left")
            override fun onRightClick() = WsClient.click("right")
            override fun onScroll(notches: Int) = WsClient.scroll(notches)
            override fun onZoom(direction: Int) = WsClient.zoom(direction)
            override fun onGesture(name: String) = WsClient.gesture(name)
            override fun onDragStart() = WsClient.buttonDown("left")
            override fun onDragEnd() = WsClient.buttonUp("left")
        }
    }

    private fun tap(id: Int, level: Haptics.Level = Haptics.Level.LIGHT, action: () -> Unit) {
        findViewById<View>(id).setOnClickListener {
            Haptics.fire(level)
            action()
        }
    }

    private fun setupMouseButtons() {
        tap(R.id.btnLeft) { WsClient.click("left") }
        tap(R.id.btnMiddle) { WsClient.click("middle") }
        tap(R.id.btnRight) { WsClient.click("right") }
    }

    private lateinit var typeLabel: TextView
    private var typeDialog: Dialog? = null

    private fun setupKeyboard() {
        typeLabel = findViewById(R.id.etType)
        typeLabel.setOnClickListener {
            Haptics.light()
            showTypePopup()
        }
    }

    /**
     * Pop-up mengetik: kartu di tengah layar dengan latar diburamkan
     * (mode fokus). Kartu hanya tumbuh ke ATAS saat teks bertambah, karena
     * tepi bawahnya dikunci setelah pengukuran pertama.
     * Teks dikirim per huruf, jadi langsung muncul di PC.
     */
    private fun showTypePopup() {
        if (typeDialog?.isShowing == true) return

        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(R.layout.popup_type)
        val root = dialog.findViewById<FrameLayout>(R.id.typeRoot)
        val card = dialog.findViewById<View>(R.id.typeCard)
        val et = dialog.findViewById<EditText>(R.id.etPopup)
        val enter = dialog.findViewById<TextView>(R.id.btnPopupEnter)
        Fonts.apply(root)
        Accent.applyToKey(enter)

        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                      WindowManager.LayoutParams.MATCH_PARENT)
            // ADJUST_NOTHING: jendela TIDAK digeser/diperkecil saat keyboard
            // muncul, sehingga panel tetap di tengah layar.
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                             WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            // Latar diburamkan supaya fokus hanya ke panel ini.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    attributes = attributes.apply { blurBehindRadius = 90 }
                } catch (e: Exception) { }
            }
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.55f)
        }
        root.setBackgroundColor(Color.argb(0x66, 6, 6, 12))
        root.setOnClickListener { dialog.dismiss() }   // ketuk luar = tutup

        // --- panel tetap di tengah; pertumbuhan hanya ke ATAS ---
        // Tinggi awal kartu dicatat sekali, lalu setiap penambahan tinggi
        // digeser ke bawah setengahnya. Efeknya tepi bawah diam di tempat
        // dan kartu memanjang ke atas, sementara posisinya tetap terpusat.
        var baseHeight = 0
        card.viewTreeObserver.addOnGlobalLayoutListener {
            if (card.height > 0) {
                if (baseHeight == 0) baseHeight = card.height
                card.translationY = ((card.height - baseHeight) / 2f)
            }
        }

        // Keyboard ditutup -> tutup panel.
        val decor = dialog.window?.decorView
        decor?.viewTreeObserver?.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            private var everOpen = false
            override fun onGlobalLayout() {
                val r = Rect()
                decor.getWindowVisibleDisplayFrame(r)
                val screenH = decor.height.takeIf { it > 0 } ?: return
                val keyboard = screenH - r.bottom
                if (keyboard > screenH * 0.15) everOpen = true
                else if (everOpen) decor.post { dialog.dismiss() }
            }
        })

        // --- pengiriman teks per huruf, backspace tetap berfungsi ---
        et.addTextChangedListener(object : TextWatcher {
            private var before = ""
            override fun beforeTextChanged(s: CharSequence, st: Int, c: Int, a: Int) {
                before = s.toString()
            }
            override fun onTextChanged(s: CharSequence, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (suppressWatcher) return
                val now = s.toString()
                if (now.length > before.length && now.startsWith(before)) {
                    WsClient.text(now.substring(before.length))
                    Haptics.tick()
                } else if (now.length < before.length && before.startsWith(now)) {
                    repeat(before.length - now.length) { WsClient.key("backspace") }
                    Haptics.tick()
                } else if (now != before) {
                    repeat(before.length) { WsClient.key("backspace") }
                    if (now.isNotEmpty()) WsClient.text(now)
                }
                typeLabel.text = if (now.isEmpty()) "ketik di sini" else now
            }
        })

        // Enter dari tombol: kirim, kosongkan, pop-up TETAP terbuka
        fun sendEnter() {
            Haptics.medium()
            WsClient.key("enter")
            suppressWatcher = true
            et.setText("")
            suppressWatcher = false
            typeLabel.text = "ketik di sini"
        }
        enter.setOnClickListener { sendEnter() }

        // Enter dari keyboard: jangan sisipkan baris baru, kirim saja
        et.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                sendEnter(); true
            } else false
        }

        dialog.setOnDismissListener {
            typeDialog = null
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(et.windowToken, 0)
        }

        typeDialog = dialog
        dialog.show()
        et.requestFocus()
        et.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun setupShortcuts() {
        tap(R.id.kCopy, Haptics.Level.MEDIUM) { WsClient.key("c", listOf("ctrl")) }
        tap(R.id.kPaste, Haptics.Level.MEDIUM) { WsClient.key("v", listOf("ctrl")) }
        tap(R.id.kUndo, Haptics.Level.MEDIUM) { WsClient.key("z", listOf("ctrl")) }
        tap(R.id.kWinTab, Haptics.Level.MEDIUM) { WsClient.key("tab", listOf("win")) }
        findViewById<TextView>(R.id.btnAdvance).setOnClickListener {
            Haptics.medium()
            toggleAdvancePopup(it)
        }
    }

    /** Panel Advance: pop-up persegi berisi esc / tab / win / del. */
    private fun toggleAdvancePopup(anchor: View) {
        advancePopup?.let {
            if (it.isShowing) { it.dismiss(); advancePopup = null; return }
        }
        val content = LayoutInflater.from(this).inflate(R.layout.popup_advance, null)
        Fonts.apply(content)
        val popup = PopupWindow(content,
            (210 * resources.displayMetrics.density).toInt(),
            (210 * resources.displayMetrics.density).toInt(),
            true)
        popup.elevation = 24f
        fun bind(id: Int, action: () -> Unit) {
            content.findViewById<View>(id).setOnClickListener {
                Haptics.medium(); action()
            }
        }
        bind(R.id.kEsc) { WsClient.key("esc") }
        bind(R.id.kTab) { WsClient.key("tab") }
        bind(R.id.kWin) { WsClient.key("win") }
        bind(R.id.kDel) { WsClient.key("delete") }
        // Ctrl+, — pintasan buka pengaturan di banyak aplikasi Windows
        bind(R.id.kSettings) { WsClient.key(",", listOf("ctrl")) }
        popup.setOnDismissListener { advancePopup = null }
        popup.showAsDropDown(anchor, 0,
            (-260 * resources.displayMetrics.density).toInt())
        advancePopup = popup
    }

    // ---------------------------------------------------------------- volume --
    private fun setupVolume() {
        volumeSlider.value = WsClient.volume
        volumeSlider.onValueChanged = { v ->
            val now = System.currentTimeMillis()
            if (v != lastVolSent && now - lastVolTime >= 40L) {
                sendVolume(v)
            }
        }
        volumeSlider.onCommit = { v ->
            WsClient.volume = v
            if (v != lastVolSent) sendVolume(v)
        }
        WsClient.volGet()
    }

    private fun sendVolume(v: Int) {
        if (absVolume) {
            lastVolSent = v
            lastVolTime = System.currentTimeMillis()
            WsClient.volSet(v)
        } else {
            // fallback: kirim langkah volup/voldown (±2% per langkah)
            val prev = if (lastVolSent < 0) volumeSlider.value else lastVolSent
            val steps = (v - prev) / 2
            if (steps != 0) {
                repeat(kotlin.math.abs(steps)) {
                    WsClient.media(if (steps > 0) "volup" else "voldown")
                }
                lastVolSent = prev + steps * 2
                lastVolTime = System.currentTimeMillis()
            }
        }
    }

    private fun onVolumeError() {
        absVolume = false
        if (!volErrToasted) {
            volErrToasted = true
            Toast.makeText(this,
                "server tanpa pycaw — slider memakai mode bertingkat",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun setupMedia() {
        tap(R.id.mPlay, Haptics.Level.MEDIUM) { WsClient.media("playpause") }
        tap(R.id.mPrev) { WsClient.media("prev") }
        tap(R.id.mNext) { WsClient.media("next") }
        tap(R.id.mMute, Haptics.Level.MEDIUM) { WsClient.media("mute") }
    }

    private fun setupDpad() {
        findViewById<DpadView>(R.id.dpad).onDirection = { dir -> WsClient.key(dir) }
    }

    private fun setupTopBar() {
        val btnRotate = findViewById<TextView>(R.id.btnRotate)
        fun renderRotate() {
            val deg = trackpad.inputRotation
            btnRotate.alpha = if (deg == 0) 0.6f else 1f
            btnRotate.text = when (deg) {
                90 -> "⤡"
                270 -> "⤣"
                else -> "⤢"
            }
        }
        renderRotate()
        btnRotate.setOnClickListener {
            Haptics.heavy()
            // Hanya arah INPUT trackpad yang berputar — layout tidak berubah.
            // Siklus: 0° -> 90° -> 270° -> 0°
            val next = Prefs.nextRotation(trackpad.inputRotation)
            trackpad.inputRotation = next
            Prefs.setInputRotation(this, next)
            renderRotate()
            Toast.makeText(this,
                if (next == 0) "input normal" else "input diputar ${next}°",
                Toast.LENGTH_SHORT).show()
        }
        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            Haptics.light()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<TextView>(R.id.btnDisconnect).setOnClickListener {
            Haptics.heavy()
            WsClient.disconnect()
            finish()
        }
    }

    // ---------------------------------------------------------------- state --
    override fun onResume() {
        super.onResume()
        Haptics.enabled = Prefs.hapticEnabled(this)
        Haptics.strength = Prefs.hapticStrength(this)
        Glass.apply(this, findViewById(R.id.rootControl))
        trackpad.sensitivity = Prefs.sensitivity(this)
        trackpad.naturalScroll = Prefs.naturalScroll(this)
        trackpad.inputRotation = Prefs.inputRotation(this)
        trackpad.pointerLocation = Prefs.pointerLocation(this)
        trackpad.showTaps = Prefs.showTaps(this)
        if (!WsClient.connected) finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        pingRunning = false
        pingHandler.removeCallbacksAndMessages(null)
        WsClient.onPing = null
        typeDialog?.dismiss()
        advancePopup?.dismiss()
        if (isFinishing && !isChangingConfigurations) WsClient.disconnect()
    }
}
