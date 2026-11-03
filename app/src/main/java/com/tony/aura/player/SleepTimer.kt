package com.tony.aura.player

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Temporizador de apagado: por minutos o al final del episodio, con bajada suave de volumen. */
object SleepTimer {
    data class State(
        /** Hora (System.currentTimeMillis) a la que se apaga; 0 = sin temporizador por tiempo */
        val endsAt: Long = 0L,
        val endOfTrack: Boolean = false,
        /** Si también apaga el sonido de fondo */
        val includeAmbient: Boolean = true,
        val minutes: Int = 0,
    ) {
        val active get() = endsAt > 0 || endOfTrack
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private val main = Handler(Looper.getMainLooper())
    private val fire = Runnable { fadeOutAndStop() }

    fun startMinutes(min: Int) {
        main.removeCallbacks(fire)
        _state.update { it.copy(endsAt = System.currentTimeMillis() + min * 60_000L, endOfTrack = false, minutes = min) }
        main.postDelayed(fire, min * 60_000L)
        AmbientService.instance?.refresh()
    }

    fun startEndOfTrack() {
        main.removeCallbacks(fire)
        _state.update { it.copy(endsAt = 0L, endOfTrack = true, minutes = 0) }
        AmbientService.instance?.refresh()
    }

    fun setIncludeAmbient(v: Boolean) { _state.update { it.copy(includeAmbient = v) } }

    fun cancel() {
        main.removeCallbacks(fire)
        _state.update { it.copy(endsAt = 0L, endOfTrack = false, minutes = 0) }
        AmbientService.instance?.refresh()
    }

    fun remainingMs(): Long = (_state.value.endsAt - System.currentTimeMillis()).coerceAtLeast(0L)

    fun endsAtLabel(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(_state.value.endsAt))

    /** Lo llama el reproductor cuando termina una pista o la cola. */
    fun onTrackEnded() {
        if (_state.value.endOfTrack) {
            PlayerConnection.controller?.pause()
            if (_state.value.includeAmbient) Ambient.fadeOutAndStop {}
            cancel()
        }
    }

    /** Baja el volumen poco a poco durante ~10 s y apaga. */
    private fun fadeOutAndStop() {
        val includeAmbient = _state.value.includeAmbient
        val c = PlayerConnection.controller
        val startMain = Ambient.state.value.mainVolume
        val steps = 20
        var i = 0
        val step = object : Runnable {
            override fun run() {
                i++
                val f = 1f - i / steps.toFloat()
                c?.volume = startMain * f
                if (i < steps) main.postDelayed(this, 500) else {
                    c?.pause()
                    c?.volume = startMain
                }
            }
        }
        main.post(step)
        if (includeAmbient) Ambient.fadeOutAndStop {}
        cancel()
    }
}
