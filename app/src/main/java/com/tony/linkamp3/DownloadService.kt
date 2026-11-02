package com.tony.linkamp3

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class DownloadService : Service() {

    private val executor = Executors.newSingleThreadExecutor()
    private val pending = AtomicInteger(0)
    @Volatile private var lastStartId = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var nm: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        startForeground(
            NOTIF_ID, progressNotification(null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LinkAMp3:descarga")
            .apply { acquire(3 * 60 * 60 * 1000L) }
    }

    override fun onDestroy() {
        try { wakeLock?.release() } catch (_: Throwable) {}
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_CANCEL -> {
                Jobs.cancel(intent.getStringExtra(EXTRA_ID))
                if (pending.get() == 0) finishIfIdle()
            }
            ACTION_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL)
                val mode = Mode.entries.getOrElse(intent.getIntExtra(EXTRA_MODE, 0)) { Mode.MP3_ALTA }
                if (url.isNullOrBlank()) {
                    if (pending.get() == 0) finishIfIdle()
                } else {
                    val job = Jobs.add(url, mode)
                    pending.incrementAndGet()
                    executor.execute {
                        try { run(job) } finally {
                            if (pending.decrementAndGet() == 0) finishIfIdle()
                        }
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

    // ---------------------------------------------------------------- descarga

    private var lastUiUpdate = 0L

    private fun publish(job: Job, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUiUpdate < 400) return
        lastUiUpdate = now
        Jobs.changed()
        nm.notify(NOTIF_ID, progressNotification(job))
    }

    private fun run(job: Job) {
        if (job.state == State.CANCELED) return
        job.state = State.RUNNING
        job.status = "Preparando…"
        publish(job, true)
        val tmp = File(cacheDir, "descargas/${job.id}")
        try {
            Engine.ensureInit(this)
            Engine.maybeAutoUpdate(this) { job.status = "Actualizando el motor (yt-dlp)…"; publish(job, true) }
            if (job.state == State.CANCELED) return

            tmp.deleteRecursively(); tmp.mkdirs()
            val request = buildRequest(job, tmp)
            job.status = "Conectando…"
            publish(job, true)

            val response = YoutubeDL.getInstance().execute(request, job.id) { progress, eta, line ->
                handleLine(job, progress, eta, line)
            }
            if (job.state == State.CANCELED) return

            val wanted = if (job.mode == Mode.VIDEO) setOf("mp4", "mkv", "webm") else setOf("mp3")
            val files = tmp.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in wanted }
                .sortedBy { it.absolutePath }
                .toList()
            if (files.isEmpty()) {
                throw IOException(errorLine(response.err).ifBlank { "No se ha generado ningún archivo" })
            }

            job.status = "Guardando…"
            job.progress = -1f
            publish(job, true)
            job.results = files.map { f ->
                val sub = f.parentFile?.takeIf { it != tmp }?.name
                saveToMediaStore(f, sub)
            }
            if (files.size == 1) job.title = files[0].nameWithoutExtension
            job.progress = 100f
            job.state = State.DONE
            job.status = if (files.size == 1) "Guardado en ${folderLabel(job)}"
                         else "${files.size} archivos guardados en ${folderLabel(job)}"
            publish(job, true)
            doneNotification(job)
        } catch (e: Throwable) {
            if (job.state == State.CANCELED || e is YoutubeDL.CanceledException || e is InterruptedException) {
                job.state = State.CANCELED
                job.status = "Cancelado"
            } else {
                job.state = State.ERROR
                job.status = friendlyError(e)
                errorNotification(job)
            }
            publish(job, true)
        } finally {
            tmp.deleteRecursively()
        }
    }

    private fun buildRequest(job: Job, dir: File): YoutubeDLRequest {
        val r = YoutubeDLRequest(job.url)
        val single = "%(title).150B.%(ext)s"
        when (job.mode) {
            Mode.MP3_ALTA, Mode.MP3_LIGERO, Mode.LISTA -> {
                r.addOption("-x")
                r.addOption("--audio-format", "mp3")
                r.addOption("--audio-quality", if (job.mode == Mode.MP3_LIGERO) "128K" else "0")
                r.addOption("--embed-thumbnail")
                r.addOption("--convert-thumbnails", "jpg")
            }
            Mode.VIDEO -> {
                r.addOption("-f", "bv*[ext=mp4][height<=1080]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b")
                r.addOption("--merge-output-format", "mp4")
            }
        }
        if (job.mode == Mode.LISTA) {
            r.addOption("--yes-playlist")
            r.addOption("-o", File(dir, "%(playlist_title|Lista).80B/%(playlist_index)03d - $single").absolutePath)
        } else {
            r.addOption("--no-playlist")
            r.addOption("-o", File(dir, single).absolutePath)
        }
        // Motor JavaScript incluido en la app: YouTube lo exige para resolver sus desafíos
        val qjs = File(applicationInfo.nativeLibraryDir, "libqjs.so")
        if (qjs.exists()) {
            r.addOption("--js-runtimes", "quickjs:${qjs.absolutePath}")
            r.addOption("--remote-components", "ejs:github")
        }
        r.addOption("--embed-metadata")
        r.addOption("--no-mtime")
        r.addOption("--windows-filenames")
        r.addOption("--no-part")
        r.addOption("--retries", "5")
        r.addOption("--fragment-retries", "5")
        return r
    }

    private var playlistInfo = ""

    private fun handleLine(job: Job, progress: Float, eta: Long, line: String) {
        if (job.state == State.CANCELED) return
        val l = line.trim()
        when {
            l.startsWith("[download] Destination:") -> {
                job.title = File(l.removePrefix("[download] Destination:").trim()).nameWithoutExtension
                    .replace(Regex("^\\d{3} - "), "")
            }
            l.startsWith("[download] Downloading item") || l.startsWith("[download] Downloading video") -> {
                // "[download] Downloading item 3 of 12"
                playlistInfo = Regex("(\\d+) of (\\d+)").find(l)?.let { "${it.groupValues[1]}/${it.groupValues[2]} · " } ?: ""
            }
            l.startsWith("[ExtractAudio]") -> { job.status = "${playlistInfo}Convirtiendo a MP3…"; job.progress = -1f; publish(job, true); return }
            l.startsWith("[EmbedThumbnail]") -> { job.status = "${playlistInfo}Añadiendo portada…"; publish(job); return }
            l.startsWith("[Merger]") -> { job.status = "Uniendo vídeo y audio…"; job.progress = -1f; publish(job, true); return }
        }
        if (progress >= 0f && l.startsWith("[download]") && l.contains("%")) {
            job.progress = progress
            val etaTxt = if (eta > 0) " · quedan ${eta / 60}:${(eta % 60).toString().padStart(2, '0')}" else ""
            job.status = "${playlistInfo}Descargando ${progress.toInt()} %$etaTxt"
        }
        publish(job)
    }

    private fun folderLabel(job: Job) = if (job.isVideo) "Películas/Link a MP3" else "Música/Link a MP3"

    private fun saveToMediaStore(f: File, sub: String?): Uri {
        val isVideo = f.extension.lowercase() != "mp3"
        val collection = if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                         else MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val base = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC
        val rel = "$base/Link a MP3" + (sub?.let { "/$it" } ?: "")
        val mime = when (f.extension.lowercase()) {
            "mp3" -> "audio/mpeg"; "mkv" -> "video/x-matroska"; "webm" -> "video/webm"; else -> "video/mp4"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(collection, values)
            ?: throw IOException("No se pudo crear el archivo en $rel")
        try {
            contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("No se pudo escribir el archivo")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        } catch (e: Throwable) {
            contentResolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    private fun errorLine(err: String?): String =
        err.orEmpty().lines().lastOrNull { it.contains("ERROR") }?.substringAfter("ERROR:")?.trim().orEmpty()

    private fun friendlyError(e: Throwable): String {
        val raw = (e.message ?: e.toString())
        val line = errorLine(raw).ifBlank {
            raw.lines().map { it.trim() }.lastOrNull { it.isNotBlank() && !it.startsWith("WARNING") }
                ?: raw.lines().firstOrNull { it.isNotBlank() }.orEmpty()
        }
        val lower = raw.lowercase()
        return when {
            "unsupported url" in lower -> "Este sitio no es compatible"
            "unable to resolve host" in lower || "network is unreachable" in lower || "failed to resolve" in lower ->
                "Sin conexión a internet"
            "private video" in lower || "sign in" in lower || "login" in lower ->
                "El contenido es privado o pide iniciar sesión. Prueba a actualizar el motor."
            "http error 403" in lower -> "El sitio ha bloqueado la descarga (403). Prueba a actualizar el motor."
            else -> "Error: " + line.take(220)
        }
    }

    // ---------------------------------------------------------- notificaciones

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun progressNotification(job: Job?): Notification {
        val b = NotificationCompat.Builder(this, App.CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(job?.title ?: "Link a MP3")
            .setContentText(job?.status ?: "Preparando…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        val p = job?.progress ?: -1f
        b.setProgress(100, p.coerceIn(0f, 100f).toInt(), p < 0f)
        if (job != null && !job.finished) {
            val cancel = PendingIntent.getService(
                this, job.id.hashCode(),
                Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_ID, job.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            b.addAction(0, "Cancelar", cancel)
        }
        return b.build()
    }

    private fun doneNotification(job: Job) {
        val first = job.results.firstOrNull() ?: return
        val tap = if (job.results.size == 1) {
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(first, if (job.isVideo) "video/*" else "audio/mpeg")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            PendingIntent.getActivity(this, job.id.hashCode(), view,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else openAppIntent()
        val n = NotificationCompat.Builder(this, App.CH_DONE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("✅ ${job.title}")
            .setContentText(job.status)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        try { nm.notify(job.id.hashCode(), n) } catch (_: SecurityException) {}
    }

    private fun errorNotification(job: Job) {
        val n = NotificationCompat.Builder(this, App.CH_DONE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("❌ No se pudo descargar")
            .setContentText(job.status)
            .setStyle(NotificationCompat.BigTextStyle().bigText(job.url + "\n" + job.status))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        try { nm.notify(job.id.hashCode(), n) } catch (_: SecurityException) {}
    }

    companion object {
        const val NOTIF_ID = 1001
        const val ACTION_DOWNLOAD = "com.tony.linkamp3.DESCARGAR"
        const val ACTION_CANCEL = "com.tony.linkamp3.CANCELAR"
        const val EXTRA_URL = "url"
        const val EXTRA_MODE = "modo"
        const val EXTRA_ID = "id"

        fun start(ctx: Context, url: String, mode: Mode) {
            ctx.startForegroundService(
                Intent(ctx, DownloadService::class.java)
                    .setAction(ACTION_DOWNLOAD)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_MODE, mode.ordinal)
            )
        }
    }
}
