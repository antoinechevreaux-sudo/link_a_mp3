package com.tony.aura.download

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

/** yt-dlp + ffmpeg + QuickJS incluidos en la app. */
object Engine {
    @Volatile var ready = false
        private set
    private val lock = Any()
    private const val PREFS = "motor"
    private const val KEY_LAST_UPDATE = "ultima_actualizacion"
    private const val AUTO_UPDATE_MS = 3L * 24 * 60 * 60 * 1000

    fun ensureInit(ctx: Context) {
        synchronized(lock) {
            if (ready) return
            YoutubeDL.getInstance().init(ctx.applicationContext)
            FFmpeg.getInstance().init(ctx.applicationContext)
            ready = true
        }
    }

    fun version(ctx: Context): String =
        (YoutubeDL.getInstance().versionName(ctx.applicationContext)
            ?: YoutubeDL.getInstance().version(ctx.applicationContext) ?: "?")
            .removePrefix("yt-dlp").trim()

    fun update(ctx: Context): Boolean {
        ensureInit(ctx)
        val status = YoutubeDL.getInstance().updateYoutubeDL(ctx.applicationContext, YoutubeDL.UpdateChannel.STABLE)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
        return status == YoutubeDL.UpdateStatus.DONE
    }

    fun maybeAutoUpdate(ctx: Context, onStart: () -> Unit) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - prefs.getLong(KEY_LAST_UPDATE, 0L) < AUTO_UPDATE_MS) return
        try { onStart(); update(ctx) } catch (_: Throwable) {}
    }

    /** Opciones comunes: motor JavaScript para YouTube, reintentos. */
    fun addCommon(ctx: Context, r: YoutubeDLRequest) {
        val qjs = File(ctx.applicationInfo.nativeLibraryDir, "libqjs.so")
        if (qjs.exists()) {
            r.addOption("--js-runtimes", "quickjs:${qjs.absolutePath}")
            r.addOption("--remote-components", "ejs:github")
        }
        r.addOption("--retries", "5")
        r.addOption("--fragment-retries", "5")
    }

    data class Info(
        val url: String,
        val title: String,
        val artist: String,
        val durationSec: Int,
        val thumbnail: String?,
        val site: String,
        val hasVideo: Boolean,
    )

    /** Lee título, autor, duración y miniatura sin descargar nada. */
    fun info(ctx: Context, url: String): Info {
        ensureInit(ctx)
        val r = YoutubeDLRequest(url)
        r.addOption("--no-playlist")
        addCommon(ctx, r)
        val v = YoutubeDL.getInstance().getInfo(r)
        val uploader = (v.uploader ?: "").removeSuffix(" - Topic").trim()
        val formats = v.formats.orEmpty()
        val hasVideo = formats.isEmpty() || formats.any { (it.vcodec ?: "none") != "none" }
        return Info(
            url = v.webpageUrl ?: url,
            title = (v.title ?: v.fulltitle ?: "Sin título").trim(),
            artist = uploader.ifBlank { v.extractorKey ?: "" },
            durationSec = v.duration,
            thumbnail = v.thumbnail,
            site = v.extractorKey ?: "",
            hasVideo = hasVideo,
        )
    }

    fun friendlyError(e: Throwable): String {
        val raw = e.message ?: e.toString()
        val line = raw.lines().map { it.trim() }.lastOrNull { it.contains("ERROR") }?.substringAfter("ERROR:")?.trim()
            ?: raw.lines().map { it.trim() }.lastOrNull { it.isNotBlank() && !it.startsWith("WARNING") }
            ?: raw
        val lower = raw.lowercase()
        return when {
            "unsupported url" in lower -> "Este sitio no es compatible"
            "unable to resolve host" in lower || "network is unreachable" in lower || "failed to resolve" in lower ->
                "Sin conexión a internet"
            "private video" in lower || "sign in" in lower || "login required" in lower ->
                "El contenido es privado o pide iniciar sesión"
            "http error 403" in lower -> "El sitio ha bloqueado la descarga (403). Prueba a actualizar el motor."
            else -> line.take(220)
        }
    }
}
