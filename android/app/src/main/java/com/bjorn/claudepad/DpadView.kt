package com.bjorn.claudepad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * D-Pad bundar bergaya faset: piringan gelap mengilap dengan potongan
 * segi banyak yang memantulkan cahaya berbeda-beda, ditambah dua kaki kecil
 * di bawah. Empat arah dengan auto-repeat saat ditahan.
 */
class DpadView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Dipanggil dengan "up" / "down" / "left" / "right". */
    var onDirection: ((String) -> Unit)? = null

    /** Warna sorot arah yang ditekan (diisi dari Accent). */
    var accentColor: Int = Color.parseColor("#997C6CFF")
        set(value) { field = value; invalidate() }

    private val facetPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#59FFFFFF")
    }
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF15151C")
    }
    private val pressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6FFFFFF")
    }

    private val facet = Path()
    private val arrow = Path()
    private val footRect = RectF()
    private var pressed: String? = null

    private val repeatDelay = 380L
    private val repeatInterval = 55L
    private var repeatRunnable: Runnable? = null

    /** Sudut potongan faset — jumlah ganjil membuat pantulan terlihat alami. */
    private val facetCount = 14

    private fun cx() = width / 2f
    private fun cy() = height / 2f - height * 0.02f
    private fun radius() = min(width, height) / 2f * 0.86f

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val r = radius()
        basePaint.shader = LinearGradient(
            cx() - r, cy() - r, cx() + r, cy() + r,
            intArrayOf(
                Color.parseColor("#FF4A4A55"),
                Color.parseColor("#FF2A2A33"),
                Color.parseColor("#FF15151B")
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        glossPaint.shader = RadialGradient(
            cx() - r * 0.35f, cy() - r * 0.45f, r * 1.1f,
            intArrayOf(Color.parseColor("#40FFFFFF"), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = cx(); val cy = cy(); val r = radius()

        // dua kaki kecil di bagian bawah
        val footW = r * 0.34f
        val footH = r * 0.16f
        footRect.set(cx - r * 0.55f, cy + r * 0.86f, cx - r * 0.55f + footW, cy + r * 0.86f + footH)
        canvas.drawRoundRect(footRect, footH * 0.4f, footH * 0.4f, footPaint)
        footRect.set(cx + r * 0.55f - footW, cy + r * 0.86f, cx + r * 0.55f, cy + r * 0.86f + footH)
        canvas.drawRoundRect(footRect, footH * 0.4f, footH * 0.4f, footPaint)

        // piringan dasar
        canvas.drawCircle(cx, cy, r, basePaint)

        // potongan faset: segitiga dari pusat ke tepi, terang-gelap bergantian
        val step = (2.0 * Math.PI / facetCount).toFloat()
        for (i in 0 until facetCount) {
            val a0 = i * step - Math.PI.toFloat() / 2f
            val a1 = a0 + step
            facet.reset()
            facet.moveTo(cx, cy)
            facet.lineTo(cx + r * cos(a0), cy + r * sin(a0))
            facet.lineTo(cx + r * cos(a1), cy + r * sin(a1))
            facet.close()
            // sudut menghadap sumber cahaya (kiri atas) tampak lebih terang
            val light = ((cos(a0 + step / 2f - 2.4f) + 1f) / 2f)
            val alpha = (18 + light * 46).toInt().coerceIn(0, 255)
            facetPaint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawPath(facet, facetPaint)
        }

        // sorot arah yang sedang ditekan (seperempat piringan)
        pressed?.let { dir ->
            val startAngle = when (dir) {
                "up" -> -135f
                "right" -> -45f
                "down" -> 45f
                else -> 135f
            }
            pressPaint.color = accentColor
            val rect = RectF(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(rect, startAngle, 90f, true, pressPaint)
        }

        // kilap dan tepi
        canvas.drawCircle(cx, cy, r, glossPaint)
        canvas.drawCircle(cx, cy, r, rimPaint)

        // panah arah
        val a = r * 0.26f
        val off = r * 0.62f
        drawArrow(canvas, cx, cy - off, a, 0)
        drawArrow(canvas, cx, cy + off, a, 180)
        drawArrow(canvas, cx - off, cy, a, 270)
        drawArrow(canvas, cx + off, cy, a, 90)
    }

    private fun drawArrow(canvas: Canvas, x: Float, y: Float, size: Float, rotation: Int) {
        arrow.reset()
        val h = size * 0.55f
        arrow.moveTo(0f, -h)
        arrow.lineTo(h * 0.9f, h * 0.6f)
        arrow.lineTo(-h * 0.9f, h * 0.6f)
        arrow.close()
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation.toFloat())
        canvas.drawPath(arrow, arrowPaint)
        canvas.restore()
    }

    private fun hitTest(x: Float, y: Float): String? {
        val dx = x - cx()
        val dy = y - cy()
        val r = radius()
        val dist2 = dx * dx + dy * dy
        // pusat mati, dan di luar piringan juga diabaikan
        if (dist2 < (r * 0.22f) * (r * 0.22f)) return null
        if (dist2 > r * r) return null
        return if (abs(dx) > abs(dy)) {
            if (dx > 0) "right" else "left"
        } else {
            if (dy > 0) "down" else "up"
        }
    }

    private fun startRepeat(dir: String) {
        stopRepeat()
        val r = object : Runnable {
            override fun run() {
                onDirection?.invoke(dir)
                Haptics.tick()
                postDelayed(this, repeatInterval)
            }
        }
        repeatRunnable = r
        postDelayed(r, repeatDelay)
    }

    private fun stopRepeat() {
        repeatRunnable?.let { removeCallbacks(it) }
        repeatRunnable = null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val dir = hitTest(e.x, e.y) ?: return true
                pressed = dir
                invalidate()
                Haptics.light()
                onDirection?.invoke(dir)
                startRepeat(dir)
            }
            MotionEvent.ACTION_MOVE -> {
                val dir = hitTest(e.x, e.y)
                if (dir != pressed) {
                    pressed = dir
                    invalidate()
                    stopRepeat()
                    if (dir != null) {
                        Haptics.light()
                        onDirection?.invoke(dir)
                        startRepeat(dir)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pressed = null
                stopRepeat()
                invalidate()
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopRepeat()
    }
}
