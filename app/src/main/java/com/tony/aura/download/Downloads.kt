package com.tony.aura.download

import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

enum class Format(val label: String, val isVideo: Boolean) {
    MP3_HIGH("MP3 · Alta", false),
    MP3_LIGHT("MP3 · 128 kbps", false),
    VIDEO_1080("Vídeo · 1080p", true),
    VIDEO_720("Vídeo · 720p", true),
    VIDEO_480("Vídeo · 480p", true),
}

/** Lo que el usuario ha decidido en el editor antes de descargar. */
data class DownloadSpec(
    val url: String,
    val title: String,
    val artist: String,
    val format: Format,
    /** Recorte en segundos; null = desde el principio / hasta el final */
    val startSec: Int? = null,
    val endSec: Int? = null,
    val addToLibrary: Boolean = true,
    val playlistId: String? = null,
    val saveToDevice: Boolean = false,
    val thumbnail: String? = null,
)

enum class DlState { QUEUED, RUNNING, DONE, ERROR, CANCELED }

data class DlJob(
    val id: String = UUID.randomUUID().toString(),
    val spec: DownloadSpec,
    val status: String = "En cola…",
    val progress: Float = -1f,
    val state: DlState = DlState.QUEUED,
    val trackId: String? = null,
    val deviceUri: String? = null,
) {
    val finished get() = state == DlState.DONE || state == DlState.ERROR || state == DlState.CANCELED
}

object Downloads {
    private val _jobs = MutableStateFlow<List<DlJob>>(emptyList())
    val jobs: StateFlow<List<DlJob>> = _jobs.asStateFlow()

    fun add(spec: DownloadSpec): DlJob = DlJob(spec = spec).also { j -> _jobs.update { listOf(j) + it } }
    fun get(id: String): DlJob? = _jobs.value.firstOrNull { it.id == id }
    fun update(id: String, f: (DlJob) -> DlJob) { _jobs.update { list -> list.map { if (it.id == id) f(it) else it } } }

    fun cancel(id: String) {
        val j = get(id) ?: return
        if (j.finished) return
        update(id) { it.copy(state = DlState.CANCELED, status = "Cancelado") }
        try { YoutubeDL.getInstance().destroyProcessById(id) } catch (_: Throwable) {}
    }

    fun clearFinished() { _jobs.update { list -> list.filter { !it.finished } } }
}
