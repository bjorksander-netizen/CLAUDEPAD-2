package com.bjorn.claudepad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Trackpad dengan gesture ala Windows Precision Touchpad:
 *  1 jari geser        -> gerakkan kursor
 *  1 jari tap          -> klik kiri
 *  2 jari tap          -> klik kanan
 *  2 jari geser        -> scroll
 *  2 jari pinch        -> zoom (Ctrl + scroll)
 *  3 jari ke atas      -> Task View
 *  3 jari ke bawah     -> Show Desktop
 *  3 jari kiri/kanan   -> ganti aplikasi
 *  tap 2x lalu tahan   -> drag & drop
 */
class TrackpadView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onMove(dx: Int, dy: Int)
        fun onLeftClick()
        fun onRightClick()
        fun onScroll(notches: Int)
        fun onZoom(direction: Int)
        fun onGesture(name: String)
        fun onDragStart()
        fun onDragEnd()
    }

    var listener: Listener? = null
    var sensitivity = 1.4f
    var naturalScroll = false

    /** Nama PC yang tampil di tengah trackpad. */
    var deviceName: String = "—"
        set(value) { field = value; invalidate() }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#26FFFFFF")
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#40FFFFFF")
    }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D9FFFFFF")
        textAlign = Paint.Align.CENTER
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FFFFFF")
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    // --- state ---
    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var lastTapTime = 0L
    private var maxPointers = 0
    private var moved = false
    private var dragging = false
    private var scrollAccum = 0f
    private var pinchStartDist = 0f
    private var pinchAccum = 0f
    private var isPinching = false
    private var threeFingerFired = false
    private var threeStartX = 0f
    private var threeStartY = 0f

    private val tapTimeout = 250L
    private val doubleTapTimeout = 300L
    private val touchSlop = 24f
    private val scrollStep = 60f
    private val pinchStep = 90f
    private val threeSwipeMin = 90f

    init {
        namePaint.typeface = Fonts.mono(context)
        hintPaint.typeface = Fonts.mono(context)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = 2f
        rect.set(pad, pad, width - pad, height - pad)
        val r = 22f * resources.displayMetrics.density
        canvas.drawRoundRect(rect, r, r, bgPaint)
        canvas.drawRoundRect(rect, r, r, strokePaint)

        namePaint.textSize = 15f * resources.displayMetrics.density
        hintPaint.textSize = 10f * resources.displayMetrics.density
        canvas.drawText(deviceName, width / 2f, height / 2f, namePaint)
        canvas.drawText("1 jari gerak · 2 jari scroll/zoom · 3 jari gesture",
            width / 2f, height / 2f + 22f * resources.displayMetrics.density, hintPaint)
    }

    private fun dist(e: MotionEvent): Float {
        if (e.pointerCount < 2) return 0f
        return hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0))
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x; lastY = e.y
                downX = e.x; downY = e.y
                downTime = System.currentTimeMillis()
                maxPointers = 1
                moved = false
                scrollAccum = 0f
                pinchAccum = 0f
                isPinching = false
                threeFingerFired = false
                if (downTime - lastTapTime < doubleTapTimeout) {
                    dragging = true
                    Haptics.medium()
                    listener?.onDragStart()
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = maxOf(maxPointers, e.pointerCount)
                lastX = e.getX(0); lastY = e.getY(0)
                if (e.pointerCount == 2) {
                    pinchStartDist = dist(e)
                    isPinching = false
                } else if (e.pointerCount == 3) {
                    threeStartX = e.getX(0)
                    threeStartY = e.getY(0)
                    threeFingerFired = false
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val x = e.getX(0); val y = e.getY(0)
                val dx = x - lastX; val dy = y - lastY
                if (abs(x - downX) > touchSlop || abs(y - downY) > touchSlop) moved = true

                when {
                    // ---- 3 jari: gesture sistem ----
                    e.pointerCount >= 3 -> {
                        if (!threeFingerFired) {
                            val gx = x - threeStartX
                            val gy = y - threeStartY
                            if (abs(gx) > threeSwipeMin || abs(gy) > threeSwipeMin) {
                                val g = if (abs(gx) > abs(gy)) {
                                    if (gx > 0) "appnext" else "appprev"
                                } else {
                                    if (gy < 0) "taskview" else "showdesktop"
                                }
                                threeFingerFired = true
                                Haptics.heavy()
                                listener?.onGesture(g)
                            }
                        }
                    }

                    // ---- 2 jari: pinch zoom atau scroll ----
                    e.pointerCount == 2 -> {
                        val d = dist(e)
                        val delta = d - pinchStartDist
                        if (!isPinching && abs(delta) > touchSlop * 2) isPinching = true

                        if (isPinching) {
                            pinchAccum += (d - pinchStartDist)
                            pinchStartDist = d
                            while (pinchAccum >= pinchStep) {
                                Haptics.tick(); listener?.onZoom(1); pinchAccum -= pinchStep
                            }
                            while (pinchAccum <= -pinchStep) {
                                Haptics.tick(); listener?.onZoom(-1); pinchAccum += pinchStep
                            }
                        } else {
                            scrollAccum += dy
                            val dir = if (naturalScroll) -1 else 1
                            while (scrollAccum >= scrollStep) {
                                Haptics.tick(); listener?.onScroll(120 * dir); scrollAccum -= scrollStep
                            }
                            while (scrollAccum <= -scrollStep) {
                                Haptics.tick(); listener?.onScroll(-120 * dir); scrollAccum += scrollStep
                            }
                        }
                    }

                    // ---- 1 jari: gerakkan kursor ----
                    moved || dragging -> {
                        listener?.onMove((dx * sensitivity).toInt(), (dy * sensitivity).toInt())
                    }
                }
                lastX = x; lastY = y
            }

            MotionEvent.ACTION_UP -> {
                val now = System.currentTimeMillis()
                if (dragging) {
                    dragging = false
                    Haptics.light()
                    listener?.onDragEnd()
                } else if (!moved && !threeFingerFired && now - downTime < tapTimeout) {
                    when {
                        maxPointers >= 3 -> { /* tap 3 jari: tidak ada aksi */ }
                        maxPointers == 2 -> { Haptics.light(); listener?.onRightClick() }
                        else -> { Haptics.light(); listener?.onLeftClick(); lastTapTime = now }
                    }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (dragging) { dragging = false; listener?.onDragEnd() }
            }
        }
        return true
    }
}
