package com.bjorn.claudepad

import android.content.Context

/** Penyimpanan setting sederhana. */
object Prefs {
    private const val NAME = "claudepad"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun hapticEnabled(ctx: Context) = sp(ctx).getBoolean("haptic", true)
    fun setHaptic(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("haptic", v).apply()

    /** Rotasi input trackpad 90° (layout tidak berubah). */
    fun inputRotated(ctx: Context) = sp(ctx).getBoolean("input_rotated", false)
    fun setInputRotated(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("input_rotated", v).apply()

    fun sensitivity(ctx: Context) = sp(ctx).getFloat("sens", 1.4f)
    fun setSensitivity(ctx: Context, v: Float) = sp(ctx).edit().putFloat("sens", v).apply()

    fun naturalScroll(ctx: Context) = sp(ctx).getBoolean("natscroll", false)
    fun setNaturalScroll(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("natscroll", v).apply()

    /** Intensitas blur/frost background, 0..100. */
    fun blurIntensity(ctx: Context) = sp(ctx).getInt("blur", 55)
    fun setBlurIntensity(ctx: Context, v: Int) = sp(ctx).edit().putInt("blur", v).apply()

    /** Kekuatan haptic, 0.0..2.0 (1.0 = normal, 0 = sama dengan mati). */
    fun hapticStrength(ctx: Context) = sp(ctx).getFloat("haptic_str", 1.0f)
    fun setHapticStrength(ctx: Context, v: Float) = sp(ctx).edit().putFloat("haptic_str", v).apply()

    fun keepAwake(ctx: Context) = sp(ctx).getBoolean("awake", true)
    fun setKeepAwake(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("awake", v).apply()

    fun ip(ctx: Context): String = sp(ctx).getString("ip", "") ?: ""
    fun setIp(ctx: Context, v: String) = sp(ctx).edit().putString("ip", v).apply()

    fun pin(ctx: Context): String = sp(ctx).getString("pin", "") ?: ""
    fun setPin(ctx: Context, v: String) = sp(ctx).edit().putString("pin", v).apply()
}
