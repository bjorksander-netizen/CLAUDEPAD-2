package com.bjorn.claudepad

import android.content.Context

/** Penyimpanan setting sederhana. */
object Prefs {
    private const val NAME = "claudepad"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun hapticEnabled(ctx: Context) = sp(ctx).getBoolean("haptic", true)
    fun setHaptic(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("haptic", v).apply()

    fun landscape(ctx: Context) = sp(ctx).getBoolean("landscape", false)
    fun setLandscape(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("landscape", v).apply()

    fun sensitivity(ctx: Context) = sp(ctx).getFloat("sens", 1.4f)
    fun setSensitivity(ctx: Context, v: Float) = sp(ctx).edit().putFloat("sens", v).apply()

    fun naturalScroll(ctx: Context) = sp(ctx).getBoolean("natscroll", false)
    fun setNaturalScroll(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("natscroll", v).apply()

    fun keepAwake(ctx: Context) = sp(ctx).getBoolean("awake", true)
    fun setKeepAwake(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("awake", v).apply()

    fun ip(ctx: Context): String = sp(ctx).getString("ip", "") ?: ""
    fun setIp(ctx: Context, v: String) = sp(ctx).edit().putString("ip", v).apply()

    fun pin(ctx: Context): String = sp(ctx).getString("pin", "") ?: ""
    fun setPin(ctx: Context, v: String) = sp(ctx).edit().putString("pin", v).apply()
}
