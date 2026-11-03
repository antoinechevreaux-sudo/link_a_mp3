package com.tony.aura.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.tony.aura.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Capa de fondo: un segundo reproductor que suena a la vez que el principal
 * (p. ej. lluvia bajo un podcast). Va en bucle, con volumen propio y sin
 * quitarle el foco de audio al reproductor principal.
 */
object Ambient {
    data class State(
        val track: Track? = null,
        val playing: Boolean = false,
        val volume: Float = 0.45f,
        val mainVolume: Float = 1f,
        /** Si está activo, la capa se pausa y reanuda junto con la pista principal */
        val linked: Boolean = true,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var player: ExoPlayer? = null
    private lateinit var app: Context

    fun init(ctx: Context) { app = ctx.applicationContext }

    private fun player(): ExoPlayer = player ?: ExoPlayer.Builder(app)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            false, // no pide el foco de audio: así no pausa al principal
        )
        .build()
        .also { p ->
            p.repeatMode = Player.REPEAT_MODE_ONE
            p.volume = _state.value.volume
            p.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(playing = isPlaying) }
                }
            })
            player = p
        }

    fun set(track: Track) {
        val p = player()
        p.setMediaItem(MediaItem.fromUri(Uri.parse(track.uri)))
        p.prepare()
        p.play()
        _state.update { it.copy(track = track) }
    }

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun setVolume(v: Float) {
        val vol = v.coerceIn(0f, 1f)
        player?.volume = vol
        _state.update { it.copy(volume = vol) }
    }

    fun setMainVolume(v: Float) {
        val vol = v.coerceIn(0f, 1f)
        PlayerConnection.controller?.volume = vol
        _state.update { it.copy(mainVolume = vol) }
    }

    fun setLinked(linked: Boolean) { _state.update { it.copy(linked = linked) } }

    /** Lo llama el reproductor principal cuando empieza o se pausa. */
    fun onMainPlaying(isPlaying: Boolean) {
        val s = _state.value
        val p = player ?: return
        if (!s.linked || s.track == null) return
        if (isPlaying && !p.isPlaying) p.play()
        if (!isPlaying && p.isPlaying) p.pause()
    }

    fun clear() {
        player?.release()
        player = null
        _state.update { State(volume = it.volume, mainVolume = it.mainVolume, linked = it.linked) }
    }
}
