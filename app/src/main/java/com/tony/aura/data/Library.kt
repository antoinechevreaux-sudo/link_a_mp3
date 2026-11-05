package com.tony.aura.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

enum class Kind(val label: String, val plural: String) {
    MUSIC("Música", "Música"),
    VIDEO("Vídeo", "Vídeos"),
    PODCAST("Podcast", "Podcasts"),
    AUDIOBOOK("Audiolibro", "Audiolibros");

    companion object {
        fun parse(s: String?): Kind? = entries.firstOrNull { it.name == s }

        /** Adivina el tipo por el título y la duración (se puede cambiar después). */
        fun guess(title: String, durationSec: Int, isVideo: Boolean): Kind {
            if (isVideo) return VIDEO
            val t = title.lowercase()
            return when {
                listOf("audiolibro", "audiobook", "livre audio", "audioboek", "audiolivro", "capítulo", "chapter").any { it in t } -> AUDIOBOOK
                listOf("podcast", "episodio", "episode", "ep.", "entrevista", "interview").any { it in t } -> PODCAST
                durationSec > 25 * 60 -> PODCAST
                else -> MUSIC
            }
        }
    }
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    /** file:// (copia interna de Aura) o content:// (música del dispositivo) */
    val uri: String,
    val coverUri: String? = null,
    val durationMs: Long = 0L,
    val isVideo: Boolean = false,
    val sourceUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val liked: Boolean = false,
    /** Copia guardada en Música/Aura del teléfono, si existe */
    val deviceUri: String? = null,
    val fromDevice: Boolean = false,
    val plays: Int = 0,
    val lastPlayed: Long = 0L,
    val category: String? = null,
) {
    val kind: Kind get() = if (isVideo) Kind.VIDEO else (Kind.parse(category)?.takeIf { it != Kind.VIDEO } ?: Kind.MUSIC)
}

data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

data class LibraryState(
    val tracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
) {
    private val byId by lazy { tracks.associateBy { it.id } }
    fun track(id: String): Track? = byId[id]
    fun tracksOf(p: Playlist): List<Track> = p.trackIds.mapNotNull { byId[it] }
    val liked get() = tracks.filter { it.liked }.sortedByDescending { it.addedAt }
    val recent get() = tracks.filter { !it.fromDevice }.sortedByDescending { it.addedAt }
    val recentlyPlayed get() = tracks.filter { it.lastPlayed > 0 }.sortedByDescending { it.lastPlayed }
}

/** Biblioteca de Aura: canciones, vídeos y playlists, guardada en un JSON privado. */
object Library {
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()
    private lateinit var file: File
    lateinit var mediaDir: File
        private set
    lateinit var coverDir: File
        private set
    private val io = Executors.newSingleThreadExecutor()

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "biblioteca.json")
        mediaDir = File(ctx.filesDir, "media").apply { mkdirs() }
        coverDir = File(ctx.filesDir, "portadas").apply { mkdirs() }
        _state.value = load()
    }

    // ----------------------------------------------------------- canciones

    fun add(track: Track) {
        mutate { s -> s.copy(tracks = listOf(track) + s.tracks.filter { it.id != track.id }) }
    }

    /** Añade la pista si no existía (p. ej. música del dispositivo) y la devuelve. */
    fun ensure(track: Track): Track {
        state.value.track(track.id)?.let { return it }
        add(track)
        return track
    }

    fun update(id: String, change: (Track) -> Track) {
        mutate { s -> s.copy(tracks = s.tracks.map { if (it.id == id) change(it) else it }) }
    }

    fun toggleLike(track: Track) {
        ensure(track)
        update(track.id) { it.copy(liked = !it.liked) }
    }

    fun markPlayed(id: String) {
        update(id) { it.copy(plays = it.plays + 1, lastPlayed = System.currentTimeMillis()) }
    }

    fun delete(id: String) {
        val t = state.value.track(id) ?: return
        mutate { s ->
            s.copy(
                tracks = s.tracks.filter { it.id != id },
                playlists = s.playlists.map { p -> p.copy(trackIds = p.trackIds - id) },
            )
        }
        if (!t.fromDevice) io.execute {
            Uri.parse(t.uri).path?.let { File(it).delete() }
            t.coverUri?.let { c -> Uri.parse(c).path?.let { File(it).takeIf { f -> f.startsWith(coverDir) }?.delete() } }
        }
    }

    // ----------------------------------------------------------- playlists

    fun createPlaylist(name: String): Playlist {
        val p = Playlist(UUID.randomUUID().toString(), name.trim().ifBlank { "Nueva playlist" })
        mutate { s -> s.copy(playlists = s.playlists + p) }
        return p
    }

    fun renamePlaylist(id: String, name: String) {
        mutate { s -> s.copy(playlists = s.playlists.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it }) }
    }

    fun deletePlaylist(id: String) {
        mutate { s -> s.copy(playlists = s.playlists.filter { it.id != id }) }
    }

    fun addToPlaylist(playlistId: String, track: Track) {
        ensure(track)
        mutate { s ->
            s.copy(playlists = s.playlists.map {
                if (it.id == playlistId && track.id !in it.trackIds) it.copy(trackIds = it.trackIds + track.id) else it
            })
        }
    }

    /** Nuevo orden de una playlist (arrastrando o por fecha). */
    fun reorderPlaylist(playlistId: String, ids: List<String>) {
        mutate { s -> s.copy(playlists = s.playlists.map { if (it.id == playlistId) it.copy(trackIds = ids.filter { i -> i in it.trackIds }) else it }) }
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) {
        mutate { s -> s.copy(playlists = s.playlists.map { if (it.id == playlistId) it.copy(trackIds = it.trackIds - trackId) else it }) }
    }

    // ----------------------------------------------------------- persistencia

    private fun mutate(f: (LibraryState) -> LibraryState) {
        _state.update(f)
        val snapshot = _state.value
        io.execute { save(snapshot) }
    }

    private fun save(s: LibraryState) {
        val root = JSONObject()
        root.put("tracks", JSONArray().apply {
            s.tracks.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id); put("title", t.title); put("artist", t.artist); put("uri", t.uri)
                    put("cover", t.coverUri ?: ""); put("duration", t.durationMs); put("video", t.isVideo)
                    put("source", t.sourceUrl ?: ""); put("added", t.addedAt); put("liked", t.liked)
                    put("device", t.deviceUri ?: ""); put("fromDevice", t.fromDevice)
                    put("plays", t.plays); put("lastPlayed", t.lastPlayed); put("cat", t.category ?: "")
                })
            }
        })
        root.put("playlists", JSONArray().apply {
            s.playlists.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("name", p.name); put("created", p.createdAt)
                    put("tracks", JSONArray(p.trackIds))
                })
            }
        })
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString())
        tmp.renameTo(file)
    }

    private fun load(): LibraryState = try {
        if (!file.exists()) LibraryState() else {
            val root = JSONObject(file.readText())
            val tracks = root.optJSONArray("tracks")?.let { a ->
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    Track(
                        id = o.getString("id"), title = o.optString("title"), artist = o.optString("artist"),
                        uri = o.getString("uri"), coverUri = o.optString("cover").ifBlank { null },
                        durationMs = o.optLong("duration"), isVideo = o.optBoolean("video"),
                        sourceUrl = o.optString("source").ifBlank { null }, addedAt = o.optLong("added"),
                        liked = o.optBoolean("liked"), deviceUri = o.optString("device").ifBlank { null },
                        fromDevice = o.optBoolean("fromDevice"), plays = o.optInt("plays"),
                        lastPlayed = o.optLong("lastPlayed"),
                        category = o.optString("cat").ifBlank { null },
                    )
                }
            } ?: emptyList()
            val playlists = root.optJSONArray("playlists")?.let { a ->
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    val ids = o.optJSONArray("tracks")
                    Playlist(
                        id = o.getString("id"), name = o.optString("name"), createdAt = o.optLong("created"),
                        trackIds = if (ids == null) emptyList() else (0 until ids.length()).map { ids.getString(it) },
                    )
                }
            } ?: emptyList()
            LibraryState(tracks, playlists)
        }
    } catch (_: Throwable) {
        LibraryState()
    }
}
