package com.bjorn.claudepad

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import androidx.core.graphics.ColorUtils

/**
 * Warna aksen aplikasi mengikuti wallpaper perangkat (Material You).
 * Android 12+ : diambil dari system_accent1 (dinamis dari wallpaper).
 * Di bawahnya : jatuh ke ungu default #7C6CFF.
 */
object Accent {

    private const val FALLBACK = 0xFF7C6CFF.toInt()

    fun color(ctx: Context): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                ctx.getColor(android.R.color.system_accent1_300)
            } catch (e: Exception) {
                FALLBACK
            }
        } else FALLBACK
    }

    /** Aksen dengan alpha untuk latar tombol kaca. */
    fun bg(ctx: Context): Int = ColorUtils.setAlphaComponent(color(ctx), 0x99)
    fun bgPressed(ctx: Context): Int = ColorUtils.setAlphaComponent(color(ctx), 0xCC)

    /** Timpa latar tombol aksen (glass_key_accent) dengan warna dinamis. */
    fun applyToKey(v: View) {
        val ctx = v.context
        val radius = 16f * ctx.resources.displayMetrics.density
        val d = GradientDrawable().apply {
            cornerRadius = radius
            setColor(bg(ctx))
            setStroke((1 * ctx.resources.displayMetrics.density).toInt(),
                Color.parseColor("#40FFFFFF"))
        }
        v.background = d
    }
}
