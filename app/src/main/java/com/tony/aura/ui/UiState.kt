package com.tony.aura.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.tony.aura.data.Track
import com.tony.aura.player.PlayerConnection

enum class Tab(val label: String) { HOME("Inicio"), EXPLORE("Explorar"), DOWNLOAD("Descargar"), LIBRARY("Colección"), PLAYER("Reproductor") }

/** Colecciones especiales que se abren como una playlist. */
object Special {
    const val LIKED = "@me_gusta"
    const val ALL = "@todo"
    const val VIDEOS = "@videos"
    const val DEVICE = "@dispositivo"
    const val RECENT = "@recientes"
    const val MUSIC = "@musica"
    const val PODCASTS = "@podcasts"
    const val AUDIOBOOKS = "@audiolibros"
}

class UiState {
    var tab by mutableStateOf(Tab.HOME)
    /** Playlist (o colección especial) abierta encima de la pestaña actual */
    var openPlaylist by mutableStateOf<String?>(null)
    var videoFullscreen by mutableStateOf(false)
    var optionsFor by mutableStateOf<Track?>(null)
    var addToPlaylistFor by mutableStateOf<Track?>(null)
    var editTrack by mutableStateOf<Track?>(null)
    var sharedUrl by mutableStateOf<String?>(null)
    var pickAmbient by mutableStateOf(false)
    var pickTimer by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var inPip by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    fun play(tracks: List<Track>, index: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        PlayerConnection.play(tracks, index, shuffle)
        if (tracks[index.coerceIn(tracks.indices)].isVideo) {
            if (com.tony.aura.Settings.videoFullscreen) videoFullscreen = true else tab = Tab.PLAYER
        }
    }

    fun toast(text: String) { message = text }
}

val LocalUi = staticCompositionLocalOf { UiState() }
