package com.tony.linkamp3

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Motor yt-dlp + ffmpeg (incluidos en la app). */
object Engine {
    @Volatile var ready = false
        private set
    private val lock = Any()
    private const val PREFS = "motor"
    private const val KEY_LAST_UPDATE = "ultima_actualizacion"
    private const val AUTO_UPDATE_MS = 3L * 24 * 60 * 60 * 1000 // cada 3 días

    fun ensureInit(ctx: Context) {
        synchronized(lock) {
            if (ready) return
            YoutubeDL.getInstance().init(ctx.applicationContext)
            FFmpeg.getInstance().init(ctx.applicationContext)
            ready = true
        }
    }

    fun version(ctx: Context): String? =
        YoutubeDL.getInstance().versionName(ctx.applicationContext)
            ?: YoutubeDL.getInstance().version(ctx.applicationContext)

    /** Actualiza yt-dlp a la última versión estable. Devuelve true si se ha actualizado. */
    fun update(ctx: Context): Boolean {
        ensureInit(ctx)
        val status = YoutubeDL.getInstance()
            .updateYoutubeDL(ctx.applicationContext, YoutubeDL.UpdateChannel.STABLE)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
        return status == YoutubeDL.UpdateStatus.DONE
    }

    /** Actualiza en silencio si hace más de 3 días de la última vez. Nunca lanza errores. */
    fun maybeAutoUpdate(ctx: Context, onStart: () -> Unit) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_UPDATE, 0L)
        if (System.currentTimeMillis() - last < AUTO_UPDATE_MS) return
        try {
            onStart()
            update(ctx)
        } catch (_: Throwable) {
            // sin conexión o GitHub no responde: seguimos con la versión actual
        }
    }
}

enum class Mode(val label: String) {
    MP3_ALTA("MP3 alta calidad"),
    MP3_LIGERO("MP3 ligero (128 kbps)"),
    LISTA("Lista completa en MP3"),
    VIDEO("Vídeo MP4")
}

enum class State { QUEUED, RUNNING, DONE, ERROR, CANCELED }

class Job(val url: String, val mode: Mode) {
    val id: String = UUID.randomUUID().toString()
    @Volatile var title: String = url
    @Volatile var status: String = "En cola…"
    @Volatile var progress: Float = -1f // -1 = indeterminado
    @Volatile var state: State = State.QUEUED
    @Volatile var results: List<Uri> = emptyList()
    @Volatile var isVideo: Boolean = mode == Mode.VIDEO
    val finished get() = state == State.DONE || state == State.ERROR || state == State.CANCELED
}

/** Lista de descargas de la sesión, compartida entre el servicio y la pantalla. */
object Jobs {
    val all = CopyOnWriteArrayList<Job>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun add(url: String, mode: Mode): Job = Job(url, mode).also { all.add(0, it); changed() }
    fun find(id: String?): Job? = all.firstOrNull { it.id == id }

    fun cancel(id: String?) {
        val job = find(id) ?: return
        if (job.finished) return
        job.state = State.CANCELED
        job.status = "Cancelado"
        try { YoutubeDL.getInstance().destroyProcessById(job.id) } catch (_: Throwable) {}
        changed()
    }

    fun clearFinished() { all.removeAll { it.finished }; changed() }

    fun listen(l: () -> Unit) { listeners.add(l) }
    fun unlisten(l: () -> Unit) { listeners.remove(l) }
    fun changed() { main.post { listeners.forEach { it() } } }
}
