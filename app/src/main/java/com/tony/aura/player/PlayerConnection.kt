package com.tony.aura.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.tony.aura.data.Library
import com.tony.aura.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerUi(
    val current: Track? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Track> = emptyList(),
    val index: Int = 0,
    val error: String? = null,
)

/** Puente entre la interfaz y el servicio de reproducción. */
object PlayerConnection {
    val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()

    var controller: MediaController? = null
        private set
    private var connecting = false
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private var queue: List<Track> = emptyList()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) =
            Ambient.onMainPlayWhenReady(playWhenReady, reason)
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.mediaId?.let { Library.markPlayed(it) }
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) SleepTimer.onTrackEnded()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) SleepTimer.onTrackEnded()
        }
        override fun onPlayerError(error: PlaybackException) {
            _ui.value = _ui.value.copy(error = "No se puede reproducir: ${error.errorCodeName}")
        }
    }

    fun connect(ctx: Context) {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            connecting = false
            val c = try { future.get() } catch (_: Throwable) { null } ?: return@addListener
            controller = c
            c.addListener(listener)
            c.volume = Ambient.state.value.mainVolume
            pending.forEach { it(c) }
            pending.clear()
            refresh()
        }, ContextCompat.getMainExecutor(ctx))
    }

    /** Para la reproducción y vacía la cola (al cerrar la app). */
    fun stopAll() {
        controller?.let { it.stop(); it.clearMediaItems() }
        Ambient.clear()
        SleepTimer.cancel()
        queue = emptyList()
        _ui.value = PlayerUi()
    }

    fun disconnect() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) action(c) else pending += action
    }

    fun refresh() {
        val c = controller ?: return
        val idx = c.currentMediaItemIndex
        val id = c.currentMediaItem?.mediaId
        val cur = id?.let { i -> Library.state.value.track(i) ?: queue.firstOrNull { it.id == i } }
        _ui.value = _ui.value.copy(
            current = cur,
            isPlaying = c.isPlaying,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: (cur?.durationMs ?: 0L),
            speed = c.playbackParameters.speed,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queue = queue,
            index = idx,
        )
    }

    /** Actualiza solo la posición (se llama cada medio segundo mientras suena). */
    fun tick() {
        val c = controller ?: return
        _ui.value = _ui.value.copy(positionMs = c.currentPosition.coerceAtLeast(0), isPlaying = c.isPlaying)
    }

    private fun Track.toMediaItem(): MediaItem {
        val u = Uri.parse(this.uri)
        val meta = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(coverUri?.let { Uri.parse(it) })
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build()
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(u)
            .setMediaMetadata(meta)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(u).build())
            .build()
    }

    fun play(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        queue = tracks
        _ui.value = _ui.value.copy(error = null, current = tracks[startIndex.coerceIn(tracks.indices)])
        withController { c ->
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(tracks.map { it.toMediaItem() }, startIndex.coerceIn(tracks.indices), 0L)
            c.prepare()
            c.play()
        }
    }

    fun playNext(track: Track) = withController { c ->
        if (c.mediaItemCount == 0) { play(listOf(track)); return@withController }
        queue = queue.toMutableList().apply { add((c.currentMediaItemIndex + 1).coerceAtMost(size), track) }
        c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem())
    }

    fun addToQueue(track: Track) = withController { c ->
        if (c.mediaItemCount == 0) { play(listOf(track)); return@withController }
        queue = queue + track
        c.addMediaItem(track.toMediaItem())
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0)
            c.play()
        }
    }

    fun next() = withController { it.seekToNext() }
    fun previous() = withController { c ->
        if (c.currentPosition > 4000 || !c.hasPreviousMediaItem()) c.seekTo(0) else c.seekToPrevious()
    }
    fun back15() = withController { it.seekBack() }
    fun forward15() = withController { it.seekForward() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms); refresh() }
    fun skipToIndex(i: Int) = withController { it.seekTo(i, 0L) }

    fun setSpeed(speed: Float) = withController { it.playbackParameters = PlaybackParameters(speed) }
    fun cycleSpeed() {
        val cur = _ui.value.speed
        val next = SPEEDS.firstOrNull { it > cur + 0.01f } ?: SPEEDS.first()
        setSpeed(next)
    }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Quita una pista de la cola si estaba (por ejemplo, al borrarla). */
    fun forget(trackId: String) = withController { c ->
        for (i in c.mediaItemCount - 1 downTo 0) if (c.getMediaItemAt(i).mediaId == trackId) c.removeMediaItem(i)
        queue = queue.filter { it.id != trackId }
        refresh()
    }
}
