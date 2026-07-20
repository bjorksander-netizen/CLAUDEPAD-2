package com.bjorn.claudepad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
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

    /**
     * Rotasi INPUT trackpad dalam derajat: 0, 90, atau 270.
     * Layout tidak berubah sama sekali — hanya arah input yang diputar.
     *   90°  : geser ke kanan  -> kursor ke ATAS
     *   270° : geser ke kanan  -> kursor ke BAWAH
     */
    /** Garis bidik + koordinat jari (seperti Pointer location Android). */
    var pointerLocation = false
        set(value) { field = value; invalidate() }

    /** Riak lingkaran saat layar disentuh. */
    var showTaps = true

    var inputRotation = 0
        set(value) {
            field = if (value in intArrayOf(0, 90, 270)) value else 0
            invalidate()
        }

    /** (dx,dy) layar -> (dx,dy) kursor sesuai rotasi yang aktif. */
    private fun tx(dx: Float, dy: Float): Pair<Float, Float> = when (inputRotation) {
        90 -> Pair(dy, -dx)
        270 -> Pair(-dy, dx)
        else -> Pair(dx, dy)
    }

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

    // ---- umpan balik visual ----
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.parseColor("#8073E0FF")
    }
    private val coordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC73E0FF")
        textAlign = Paint.Align.LEFT
    }
    private val touchDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B373E0FF")
    }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    /** Jari yang sedang menempel: id -> posisi. */
    private val livePointers = LinkedHashMap<Int, PointF>()

    /** Riak yang sedang meredup. */
    private class Ripple(val x: Float, val y: Float, val start: Long)
    private val ripples = ArrayList<Ripple>()
    private val rippleDuration = 420L

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

        // Teks di dalam kotak trackpad ikut berputar mengikuti rotasi input,
        // supaya arah "atas" yang dirasakan pengguna sesuai dengan tulisannya.
        canvas.save()
        canvas.rotate(inputRotation.toFloat(), width / 2f, height / 2f)

        val d = resources.displayMetrics.density
        if (inputRotation != 0) {
            hintPaint.textSize = 11f * d
            canvas.drawText("input diputar ${inputRotation}°",
                width / 2f, height / 2f - 26f * d, hintPaint)
            hintPaint.textSize = 10f * d
        }
        canvas.drawText(deviceName, width / 2f, height / 2f, namePaint)
        canvas.drawText("1 jari gerak · 2 jari scroll/zoom · 3 jari gesture",
            width / 2f, height / 2f + 22f * d, hintPaint)
        canvas.restore()

        drawFeedback(canvas, d)
    }

    /** Riak sentuhan dan garis bidik posisi jari. */
    private fun drawFeedback(canvas: Canvas, d: Float) {
        val now = System.currentTimeMillis()

        // riak yang meredup
        if (showTaps && ripples.isNotEmpty()) {
            val it = ripples.iterator()
            while (it.hasNext()) {
                val rp = it.next()
                val t = (now - rp.start).toFloat() / rippleDuration
                if (t >= 1f) { it.remove(); continue }
                val radius = (10f + 34f * t) * d
                ripplePaint.color = Color.argb(
                    ((1f - t) * 200).toInt().coerceIn(0, 255), 115, 224, 255)
                ripplePaint.strokeWidth = (2.5f * (1f - t) + 0.8f) * d
                canvas.drawCircle(rp.x, rp.y, radius, ripplePaint)
            }
            postInvalidateOnAnimation()
        }

        if (livePointers.isEmpty()) return

        // titik jari
        if (showTaps) {
            for (p in livePointers.values) {
                canvas.drawCircle(p.x, p.y, 9f * d, touchDotPaint)
            }
        }

        // garis bidik + koordinat
        if (pointerLocation) {
            coordPaint.textSize = 10f * d
            for ((i, p) in livePointers.values.withIndex()) {
                canvas.drawLine(0f, p.y, width.toFloat(), p.y, crossPaint)
                canvas.drawLine(p.x, 0f, p.x, height.toFloat(), crossPaint)
                canvas.drawText(
                    "${p.x.toInt()}, ${p.y.toInt()}",
                    (p.x + 8f * d).coerceAtMost(width - 60f * d),
                    (p.y - 8f * d).coerceAtLeast(12f * d) + i * 12f * d,
                    coordPaint)
            }
        }
    }

    private fun trackPointers(e: MotionEvent) {
        livePointers.clear()
        for (i in 0 until e.pointerCount) {
            livePointers[e.getPointerId(i)] = PointF(e.getX(i), e.getY(i))
        }
        invalidate()
    }

    private fun addRipple(x: Float, y: Float) {
        if (!showTaps) return
        if (ripples.size > 8) ripples.removeAt(0)
        ripples.add(Ripple(x, y, System.currentTimeMillis()))
        postInvalidateOnAnimation()
    }

    private fun dist(e: MotionEvent): Float {
        if (e.pointerCount < 2) return 0f
        return hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0))
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (pointerLocation || showTaps) trackPointers(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                addRipple(e.x, e.y)
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
                val pi = e.actionIndex
                addRipple(e.getX(pi), e.getY(pi))
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
                            val (gx, gy) = tx(x - threeStartX, y - threeStartY)
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
                            scrollAccum += when (inputRotation) {
                                90 -> -dx
                                270 -> dx
                                else -> dy
                            }
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
                        val (mx, my) = tx(dx, dy)
                        listener?.onMove((mx * sensitivity).toInt(), (my * sensitivity).toInt())
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
        if (e.actionMasked == MotionEvent.ACTION_UP ||
            e.actionMasked == MotionEvent.ACTION_CANCEL) {
            livePointers.clear()
            invalidate()
        }
        return true
    }
}
