package com.bjorn.claudepad

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ControlActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var trackpad: TrackpadView
    private lateinit var volumeSlider: VolumeSlider
    private var suppressWatcher = false
    private var advanceOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyOrientation()
        setContentView(R.layout.activity_control)
        Haptics.init(this)

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
        setupAdvancePanel()
        setupVolume()
        setupMedia()
        setupDpad()
        setupTopBar()

        Fonts.apply(findViewById(R.id.rootControl))
    }

    // ---------------------------------------------------------------- setup --
    private fun applyOrientation() {
        requestedOrientation = if (Prefs.landscape(this))
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        else
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun setupConnectionCallbacks() {
        WsClient.onState = { ok, msg ->
            runOnUiThread {
                tvStatus.text = if (ok) WsClient.hostName else msg
                if (!ok) finish()
            }
        }
        WsClient.onMessage = { o ->
            if (o.optString("t") == "vol" && !o.isNull("v")) {
                runOnUiThread { volumeSlider.value = o.optInt("v", volumeSlider.value) }
            }
        }
        val host = WsClient.hostName
        tvStatus.text = "$host · ${WsClient.transport}"
        trackpad.deviceName = host
    }

    private fun setupTrackpad() {
        trackpad.sensitivity = Prefs.sensitivity(this)
        trackpad.naturalScroll = Prefs.naturalScroll(this)
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

    private fun setupKeyboard() {
        val etType = findViewById<EditText>(R.id.etType)
        etType.addTextChangedListener(object : TextWatcher {
            private var before = ""
            override fun beforeTextChanged(s: CharSequence, st: Int, c: Int, a: Int) {
                before = s.toString()
            }
            override fun onTextChanged(s: CharSequence, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (suppressWatcher) return
                val now = s.toString()
                // Hanya penambahan teks yang dikirim; penghapusan diabaikan
                // (fitur backspace dihapus sesuai permintaan).
                if (now.length > before.length && now.startsWith(before)) {
                    WsClient.text(now.substring(before.length))
                    Haptics.tick()
                }
            }
        })

        tap(R.id.kEnter, Haptics.Level.MEDIUM) {
            WsClient.key("enter")
            suppressWatcher = true
            etType.setText("")
            suppressWatcher = false
        }
    }

    private fun setupShortcuts() {
        tap(R.id.kCopy, Haptics.Level.MEDIUM) { WsClient.key("c", listOf("ctrl")) }
        tap(R.id.kPaste, Haptics.Level.MEDIUM) { WsClient.key("v", listOf("ctrl")) }
        tap(R.id.kUndo, Haptics.Level.MEDIUM) { WsClient.key("z", listOf("ctrl")) }
        tap(R.id.kWinTab, Haptics.Level.MEDIUM) { WsClient.key("tab", listOf("win")) }
    }

    private fun setupAdvancePanel() {
        val panel = findViewById<View>(R.id.advancePanel)
        val btn = findViewById<TextView>(R.id.btnAdvance)
        btn.setOnClickListener {
            Haptics.medium()
            advanceOpen = !advanceOpen
            panel.visibility = if (advanceOpen) View.VISIBLE else View.GONE
            btn.text = if (advanceOpen) "⋮" else "⋯"
        }
        tap(R.id.kEsc, Haptics.Level.MEDIUM) { WsClient.key("esc") }
        tap(R.id.kTab, Haptics.Level.MEDIUM) { WsClient.key("tab") }
        tap(R.id.kWin, Haptics.Level.MEDIUM) { WsClient.key("win") }
        tap(R.id.kDel, Haptics.Level.MEDIUM) { WsClient.key("delete") }
    }

    private var lastVolSent = -1
    private var lastVolTime = 0L

    private fun setupVolume() {
        volumeSlider.value = WsClient.volume
        // Throttle: hindari membanjiri server saat slider digeser cepat.
        volumeSlider.onValueChanged = { v ->
            val now = System.currentTimeMillis()
            if (v != lastVolSent && now - lastVolTime >= 40L) {
                lastVolSent = v
                lastVolTime = now
                WsClient.volSet(v)
            }
        }
        volumeSlider.onCommit = { v ->
            WsClient.volume = v
            if (v != lastVolSent) {          // pastikan nilai akhir selalu terkirim
                lastVolSent = v
                WsClient.volSet(v)
            }
        }
        WsClient.volGet()
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
        findViewById<TextView>(R.id.btnRotate).setOnClickListener {
            Haptics.heavy()
            val next = !Prefs.landscape(this)
            Prefs.setLandscape(this, next)
            applyOrientation()
        }
        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            Haptics.light()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    // ---------------------------------------------------------------- state --
    override fun onResume() {
        super.onResume()
        // setting bisa berubah dari halaman Setting
        Haptics.enabled = Prefs.hapticEnabled(this)
        trackpad.sensitivity = Prefs.sensitivity(this)
        trackpad.naturalScroll = Prefs.naturalScroll(this)
        applyOrientation()
        if (!WsClient.connected) finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && !isChangingConfigurations) WsClient.disconnect()
    }
}
