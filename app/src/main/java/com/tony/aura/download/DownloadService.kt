package com.tony.aura.download

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.tony.aura.AuraApp
import com.tony.aura.MainActivity
import com.tony.aura.R
import com.tony.aura.data.Library
import com.tony.aura.data.Track
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class DownloadService : Service() {

    private val executor = Executors.newSingleThreadExecutor()
    private val pending = AtomicInteger(0)
    @Volatile private var lastStartId = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var nm: NotificationManager
    private var lastUi = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        startForeground(NOTIF_ID, progressNotification(null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Aura:descarga")
            .apply { acquire(3 * 60 * 60 * 1000L) }
    }

    override fun onDestroy() {
        try { wakeLock?.release() } catch (_: Throwable) {}
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val id = intent?.getStringExtra(EXTRA_ID)
        when (intent?.action) {
            ACTION_CANCEL -> { if (id != null) Downloads.cancel(id); if (pending.get() == 0) finishIfIdle() }
            ACTION_START -> {
                if (id == null || Downloads.get(id) == null) { if (pending.get() == 0) finishIfIdle() }
                else {
                    pending.incrementAndGet()
                    executor.execute {
                        try { run(id) } finally { if (pending.decrementAndGet() == 0) finishIfIdle() }
                    }
                }
            }
            else -> if (pending.get() == 0) finishIfIdle()
        }
        return START_NOT_STICKY
    }

    private fun finishIfIdle() {
        if (stopSelfResult(lastStartId)) stopForeground(STOP_FOREGROUND_REMOVE)
    }

    // ----------------------------------------------------------------- trabajo

    private fun set(id: String, force: Boolean = false, f: (DlJob) -> DlJob) {
        Downloads.update(id, f)
        val now = System.currentTimeMillis()
        if (force || now - lastUi > 500) {
            lastUi = now
            Downloads.get(id)?.let { nm.notify(NOTIF_ID, progressNotification(it)) }
        }
    }

    private fun canceled(id: String) = Downloads.get(id)?.state == DlState.CANCELED

    private fun run(id: String) {
        val job = Downloads.get(id) ?: return
        if (job.state == DlState.CANCELED) return
        val spec = job.spec
        set(id, true) { it.copy(state = DlState.RUNNING, status = "Preparando…") }
        val tmp = File(cacheDir, "descargas/$id")
        try {
            Engine.ensureInit(this)
            Engine.maybeAutoUpdate(this) { set(id, true) { it.copy(status = "Actualizando el motor…") } }
            if (canceled(id)) return
            tmp.deleteRecursively(); tmp.mkdirs()

            val trimmed = spec.startSec != null || spec.endSec != null
            set(id, true) { it.copy(status = if (trimmed) "Descargando el recorte…" else "Conectando…") }
            val response = YoutubeDL.getInstance().execute(buildRequest(spec, tmp), id) { p, eta, line ->
                onLine(id, p, eta, line, trimmed)
            }
            if (canceled(id)) return

            val wanted = if (spec.format.isVideo) setOf("mp4", "mkv", "webm") else setOf("mp3")
            val media = tmp.listFiles()?.firstOrNull { it.name.startsWith("media.") && it.extension.lowercase() in wanted }
                ?: throw IOException(response.err.lines().lastOrNull { it.contains("ERROR") } ?: "No se ha generado ningún archivo")
            val cover = tmp.listFiles()?.firstOrNull {
                it.name.startsWith("cover.") && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")
            }

            set(id, true) { it.copy(status = "Guardando…", progress = -1f) }
            val trackId = UUID.randomUUID().toString()
            val stored = File(Library.mediaDir, "$trackId.${media.extension}")
            media.copyTo(stored, overwrite = true)
            val storedCover = cover?.let { c -> File(Library.coverDir, "$trackId.${c.extension}").also { c.copyTo(it, true) } }
            val duration = durationOf(stored)

            var deviceUri: String? = null
            if (spec.saveToDevice) deviceUri = saveToDevice(this, stored, spec.title, spec.format.isVideo).toString()

            if (spec.addToLibrary) {
                val track = Track(
                    id = trackId, title = spec.title, artist = spec.artist,
                    uri = Uri.fromFile(stored).toString(),
                    coverUri = storedCover?.let { Uri.fromFile(it).toString() } ?: spec.thumbnail,
                    durationMs = duration, isVideo = spec.format.isVideo, sourceUrl = spec.url,
                    deviceUri = deviceUri,
                    category = (if (spec.format.isVideo) com.tony.aura.data.Kind.VIDEO else spec.kind).name,
                )
                Library.add(track)
                spec.playlistId?.let { Library.addToPlaylist(it, track) }
            } else {
                stored.delete(); storedCover?.delete()
            }

            val where = buildList {
                if (spec.addToLibrary) add(if (spec.playlistId != null) "playlist" else "biblioteca")
                if (spec.saveToDevice) add(if (spec.format.isVideo) "Películas/Ember" else "Música/Ember")
            }.joinToString(" y ")
            set(id, true) {
                it.copy(state = DlState.DONE, progress = 100f, status = "Listo · guardado en $where",
                    trackId = if (spec.addToLibrary) trackId else null, deviceUri = deviceUri)
            }
            notifyFinished(spec.title, "Guardado en $where", true)
        } catch (e: Throwable) {
            if (canceled(id) || e is YoutubeDL.CanceledException || e is InterruptedException) {
                set(id, true) { it.copy(state = DlState.CANCELED, status = "Cancelado") }
            } else {
                val msg = Engine.friendlyError(e)
                set(id, true) { it.copy(state = DlState.ERROR, status = msg) }
                notifyFinished(spec.title, msg, false)
            }
        } finally {
            tmp.deleteRecursively()
        }
    }

    private fun literal(s: String) = s.replace(":", " -").replace("%", "%%").replace("\n", " ").trim()

    private fun buildRequest(spec: DownloadSpec, dir: File): YoutubeDLRequest {
        val r = YoutubeDLRequest(spec.url)
        when (spec.format) {
            Format.MP3_HIGH, Format.MP3_LIGHT -> {
                r.addOption("-f", "ba/b")
                r.addOption("-x")
                r.addOption("--audio-format", "mp3")
                r.addOption("--audio-quality", if (spec.format == Format.MP3_LIGHT) "128K" else "0")
                r.addOption("--embed-thumbnail")
            }
            else -> {
                val h = when (spec.format) { Format.VIDEO_1080 -> 1080; Format.VIDEO_720 -> 720; else -> 480 }
                r.addOption(
                    "-f",
                    "bv*[height<=$h][vcodec^=avc1]+ba[ext=m4a]/bv*[height<=$h][ext=mp4]+ba[ext=m4a]/b[height<=$h][ext=mp4]/bv*[height<=$h]+ba/b"
                )
                r.addOption("--merge-output-format", "mp4")
            }
        }
        if (spec.startSec != null || spec.endSec != null) {
            val from = spec.startSec ?: 0
            val to = spec.endSec?.toString() ?: "inf"
            r.addOption("--download-sections", "*$from-$to")
            r.addOption("--force-keyframes-at-cuts")
        }
        // Título y artista elegidos en el editor (también quedan grabados en el archivo)
        if (spec.title.isNotBlank()) r.addOption("--parse-metadata", "${literal(spec.title)}%(none|)s:(?P<meta_title>.+)")
        if (spec.artist.isNotBlank()) r.addOption("--parse-metadata", "${literal(spec.artist)}%(none|)s:(?P<meta_artist>.+)")
        r.addOption("--write-thumbnail")
        r.addOption("--convert-thumbnails", "jpg")
        r.addOption("--embed-metadata")
        r.addOption("--no-playlist")
        r.addOption("--no-mtime")
        r.addOption("--no-part")
        r.addOption("-o", File(dir, "media.%(ext)s").absolutePath)
        r.addOption("-o", "thumbnail:" + File(dir, "cover.%(ext)s").absolutePath)
        Engine.addCommon(this, r)
        return r
    }

    private fun onLine(id: String, progress: Float, eta: Long, line: String, trimmed: Boolean) {
        if (canceled(id)) return
        val l = line.trim()
        when {
            l.startsWith("[ExtractAudio]") -> { set(id, true) { it.copy(status = "Convirtiendo a MP3…", progress = -1f) }; return }
            l.startsWith("[EmbedThumbnail]") -> { set(id) { it.copy(status = "Añadiendo portada…") }; return }
            l.startsWith("[Merger]") -> { set(id, true) { it.copy(status = "Uniendo vídeo y audio…", progress = -1f) }; return }
            l.startsWith("[ModifyChapters]") || l.startsWith("[FixupM4a]") -> { set(id) { it.copy(status = "Ajustando el recorte…") }; return }
        }
        if (progress >= 0f && l.startsWith("[download]") && l.contains("%")) {
            val etaTxt = if (eta > 0) " · quedan ${eta / 60}:${(eta % 60).toString().padStart(2, '0')}" else ""
            set(id) { it.copy(progress = progress, status = "Descargando ${progress.toInt()} %$etaTxt") }
        } else if (trimmed && l.contains("time=")) {
            val t = Regex("time=(\\d+:\\d+:\\d+)").find(l)?.groupValues?.get(1)
            if (t != null) set(id) { it.copy(status = "Descargando el recorte… $t") }
        }
    }

    private fun durationOf(f: File): Long = try {
        MediaMetadataRetriever().use { m ->
            m.setDataSource(f.absolutePath)
            m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
    } catch (_: Throwable) { 0L }

    // ----------------------------------------------------------- notificaciones

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "descargar")
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun progressNotification(job: DlJob?): Notification {
        val b = NotificationCompat.Builder(this, AuraApp.CH_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(job?.spec?.title ?: "Ember")
            .setContentText(job?.status ?: "Preparando…")
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setColor(0xFFEF4444.toInt())
            .setContentIntent(openApp())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        val p = job?.progress ?: -1f
        b.setProgress(100, p.coerceIn(0f, 100f).toInt(), p < 0f)
        if (job != null && !job.finished) {
            val cancel = PendingIntent.getService(
                this, job.id.hashCode(),
                Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_ID, job.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(0, "Cancelar", cancel)
        }
        return b.build()
    }

    private fun notifyFinished(title: String, text: String, ok: Boolean) {
        val n = NotificationCompat.Builder(this, AuraApp.CH_DONE)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFFEF4444.toInt())
            .setContentTitle(if (ok) "✓ $title" else "No se pudo descargar: $title")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        try { nm.notify(title.hashCode(), n) } catch (_: SecurityException) {}
    }

    companion object {
        const val NOTIF_ID = 2001
        const val ACTION_START = "com.tony.aura.DESCARGAR"
        const val ACTION_CANCEL = "com.tony.aura.CANCELAR"
        const val EXTRA_ID = "id"

        fun enqueue(ctx: Context, spec: DownloadSpec): DlJob {
            val job = Downloads.add(spec)
            ctx.startForegroundService(
                Intent(ctx, DownloadService::class.java).setAction(ACTION_START).putExtra(EXTRA_ID, job.id)
            )
            return job
        }

        /** Copia un archivo de Aura a Música/Aura (o Películas/Aura) del teléfono. */
        fun saveToDevice(ctx: Context, f: File, title: String, isVideo: Boolean): Uri {
            val collection = if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                             else MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val dir = (if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC) + "/Ember"
            val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), " ").replace(Regex("\\s+"), " ").trim().take(120).ifBlank { "Aura" }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$safe.${f.extension}")
                put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "audio/mpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, dir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val cr = ctx.contentResolver
            val uri = cr.insert(collection, values) ?: throw IOException("No se pudo crear el archivo en $dir")
            try {
                cr.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
                    ?: throw IOException("No se pudo escribir el archivo")
                values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                cr.update(uri, values, null, null)
            } catch (e: Throwable) {
                cr.delete(uri, null, null); throw e
            }
            return uri
        }
    }
}
