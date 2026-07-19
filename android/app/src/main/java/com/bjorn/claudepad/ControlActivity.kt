package com.bjorn.claudepad

import android.os.Bundle
import android.content.Intent
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
    }

    private fun setupTrackpad() {
        trackpad.sensitivity = Prefs.sensitivity(this)
        trackpad.naturalScroll = Prefs.naturalScroll(this)
        trackpad.inputRotated = Prefs.inputRotated(this)
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
                if (now.length > before.length && now.startsWith(before)) {
                    WsClient.text(now.substring(before.length))
                    Haptics.tick()
                } else if (now.length < before.length && before.startsWith(now)) {
                    repeat(before.length - now.length) { WsClient.key("backspace") }
                    Haptics.tick()
                } else if (now != before) {
                    // perubahan kompleks (autocorrect): hapus lalu ketik ulang
                    repeat(before.length) { WsClient.key("backspace") }
                    if (now.isNotEmpty()) WsClient.text(now)
                }
            }
        })
        tap(R.id.kBksp, Haptics.Level.MEDIUM) { WsClient.key("backspace") }
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
            (200 * resources.displayMetrics.density).toInt(),
            (200 * resources.displayMetrics.density).toInt(),
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
            btnRotate.alpha = if (trackpad.inputRotated) 1f else 0.6f
        }
        renderRotate()
        btnRotate.setOnClickListener {
            Haptics.heavy()
            // Hanya arah INPUT trackpad yang berputar — layout tidak berubah.
            val next = !trackpad.inputRotated
            trackpad.inputRotated = next
            Prefs.setInputRotated(this, next)
            renderRotate()
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
        trackpad.inputRotated = Prefs.inputRotated(this)
        if (!WsClient.connected) finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        advancePopup?.dismiss()
        if (isFinishing && !isChangingConfigurations) WsClient.disconnect()
    }
}
