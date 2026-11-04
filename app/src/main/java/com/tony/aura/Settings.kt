package com.tony.aura

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Ajustes del usuario (se guardan en el teléfono). */
object Settings {
    val FONT_SIZES = listOf(0.9f to "Pequeña", 1f to "Normal", 1.15f to "Grande", 1.3f to "Muy grande")

    var fontScale by mutableFloatStateOf(1f)
        private set
    var videoFullscreen by mutableStateOf(true)
        private set
    var autoPip by mutableStateOf(true)
        private set
    private var prefs: android.content.SharedPreferences? = null

    fun init(ctx: Context) {
        val p = ctx.getSharedPreferences("ajustes_app", Context.MODE_PRIVATE)
        prefs = p
        fontScale = p.getFloat("letra", 1f)
        videoFullscreen = p.getBoolean("video_completa", true)
        autoPip = p.getBoolean("pip", true)
    }

    fun changeFontScale(v: Float) { fontScale = v; prefs?.edit()?.putFloat("letra", v)?.apply() }
    fun changeVideoFullscreen(v: Boolean) { videoFullscreen = v; prefs?.edit()?.putBoolean("video_completa", v)?.apply() }
    fun changeAutoPip(v: Boolean) { autoPip = v; prefs?.edit()?.putBoolean("pip", v)?.apply() }
}
