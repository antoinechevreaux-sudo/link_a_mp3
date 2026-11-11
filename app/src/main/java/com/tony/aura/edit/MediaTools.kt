package com.tony.aura.edit

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.tony.aura.data.Library
import com.tony.aura.data.Track
import com.tony.aura.download.Engine
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/** Un tramo que se conserva del archivo original (milisegundos). */
data class Seg(val start: Long, val end: Long) {
    val length get() = end - start
}

data class EditSpec(
    val segments: List<Seg>,
    val fadeInSec: Float = 0f,
    val fadeOutSec: Float = 0f,
    val volume: Float = 1f,
)

/** Herramientas de edición con el ffmpeg que ya lleva la app (el mismo que usa yt-dlp). */
object MediaTools {

    private fun ffmpeg(ctx: Context): File = File(ctx.applicationInfo.nativeLibraryDir, "libffmpeg.so")

    private fun env(ctx: Context): Map<String, String> {
        val pk = File(ctx.noBackupFilesDir, "youtubedl-android/packages")
        return mapOf(
            "LD_LIBRARY_PATH" to "${pk}/ffmpeg/usr/lib:${pk}/python/usr/lib",
            "TMPDIR" to ctx.cacheDir.absolutePath,
        )
    }

    /** Devuelve una ruta de archivo legible por ffmpeg (copia los content:// a la caché). */
    fun localFile(ctx: Context, t: Track): File {
        val u = Uri.parse(t.uri)
        if (u.scheme == "file") return File(u.path!!)
        val ext = ctx.contentResolver.getType(u)?.substringAfter('/')?.let { if (it == "mpeg") "mp3" else it } ?: "mp3"
        val f = File(ctx.cacheDir, "editar/${t.id.replace(':', '_')}.$ext")
        if (!f.exists()) {
            f.parentFile?.mkdirs()
            ctx.contentResolver.openInputStream(u)?.use { i -> f.outputStream().use { i.copyTo(it) } }
                ?: throw IOException("No se pudo leer el archivo")
        }
        return f
    }

    fun hasAudio(f: File): Boolean = try {
        MediaMetadataRetriever().use { m -> m.setDataSource(f.absolutePath); m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes" }
    } catch (_: Throwable) { true }

    fun durationMs(f: File): Long = try {
        MediaMetadataRetriever().use { m -> m.setDataSource(f.absolutePath); m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L }
    } catch (_: Throwable) { 0L }

    /** Forma de onda: [bins] valores entre 0 y 1. */
    fun waveform(ctx: Context, f: File, durationMs: Long, bins: Int): FloatArray {
        Engine.ensureInit(ctx)
        val durSec = (durationMs / 1000.0).coerceAtLeast(1.0)
        val rate = (600000 / durSec).toInt().coerceIn(20, 2000)
        val err = File(ctx.cacheDir, "ffmpeg_onda.log")
        val pb = ProcessBuilder(listOf(ffmpeg(ctx).absolutePath, "-v", "error", "-i", f.absolutePath, "-vn", "-ac", "1",
            "-ar", rate.toString(), "-f", "s16le", "pipe:1"))
            .redirectError(err)
        pb.environment().putAll(env(ctx))
        val p = pb.start()
        val bytes = p.inputStream.readBytes()
        p.waitFor()
        val n = bytes.size / 2
        val out = FloatArray(bins)
        if (n == 0) return out
        val per = n.toDouble() / bins
        for (b in 0 until bins) {
            val s0 = (b * per).toInt(); val s1 = ((b + 1) * per).toInt().coerceAtMost(n)
            var mx = 0
            var i = s0
            val step = ((s1 - s0) / 64).coerceAtLeast(1)
            while (i < s1) {
                val v = (bytes[2 * i].toInt() and 0xff) or (bytes[2 * i + 1].toInt() shl 8)
                val a = abs(v.toShort().toInt())
                if (a > mx) mx = a
                i += step
            }
            out[b] = mx / 32768f
        }
        val peak = out.maxOrNull()?.takeIf { it > 0f } ?: 1f
        for (b in out.indices) out[b] = (out[b] / peak).coerceIn(0f, 1f)
        return out
    }

    /** Fotogramas para la línea de tiempo de un vídeo. */
    fun thumbnails(f: File, durationMs: Long, count: Int, w: Int, h: Int): List<Bitmap?> = try {
        MediaMetadataRetriever().use { m ->
            m.setDataSource(f.absolutePath)
            (0 until count).map { i ->
                val t = (durationMs * (i + 0.5) / count).toLong() * 1000
                try { m.getScaledFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, w, h) } catch (_: Throwable) { null }
            }
        }
    } catch (_: Throwable) { List(count) { null } }

    private fun s(ms: Long) = String.format(Locale.US, "%.3f", ms / 1000.0)
    private fun s(sec: Float) = String.format(Locale.US, "%.3f", sec)

    private fun filter(spec: EditSpec, video: Boolean, audio: Boolean): String {
        val total = spec.segments.sumOf { it.length } / 1000f
        val sb = StringBuilder()
        spec.segments.forEachIndexed { i, g ->
            if (video) sb.append("[0:v]trim=start=${s(g.start)}:end=${s(g.end)},setpts=PTS-STARTPTS[v$i];")
            if (audio) sb.append("[0:a]atrim=start=${s(g.start)}:end=${s(g.end)},asetpts=PTS-STARTPTS[a$i];")
        }
        spec.segments.indices.forEach { i -> if (video) sb.append("[v$i]"); if (audio) sb.append("[a$i]") }
        sb.append("concat=n=${spec.segments.size}:v=${if (video) 1 else 0}:a=${if (audio) 1 else 0}")
        if (video) sb.append("[vc]")
        if (audio) sb.append("[ac]")
        sb.append(";")
        if (video) {
            sb.append("[vc]null")
            if (spec.fadeInSec > 0) sb.append(",fade=t=in:st=0:d=${s(spec.fadeInSec)}")
            if (spec.fadeOutSec > 0) sb.append(",fade=t=out:st=${s((total - spec.fadeOutSec).coerceAtLeast(0f))}:d=${s(spec.fadeOutSec)}")
            sb.append("[vout];")
        }
        if (audio) {
            sb.append("[ac]volume=${s(spec.volume)}")
            if (spec.fadeInSec > 0) sb.append(",afade=t=in:st=0:d=${s(spec.fadeInSec)}")
            if (spec.fadeOutSec > 0) sb.append(",afade=t=out:st=${s((total - spec.fadeOutSec).coerceAtLeast(0f))}:d=${s(spec.fadeOutSec)}")
            sb.append("[aout]")
        }
        return sb.toString().trimEnd(';')
    }

    fun runFfmpeg(ctx: Context, args: List<String>, totalMs: Long, onProgress: (Float) -> Unit) = run(ctx, args, totalMs, onProgress)

    private fun run(ctx: Context, args: List<String>, totalMs: Long, onProgress: (Float) -> Unit): Pair<Int, String> {
        val pb = ProcessBuilder(listOf(ffmpeg(ctx).absolutePath) + args).redirectErrorStream(true)
        pb.environment().putAll(env(ctx))
        val p = pb.start()
        val log = StringBuilder()
        val re = Regex("time=(\\d+):(\\d+):(\\d+\\.\\d+)")
        p.inputStream.bufferedReader().forEachLine { line ->
            if (log.length < 20000) log.appendLine(line)
            re.find(line)?.let { m ->
                val t = (m.groupValues[1].toLong() * 3600 + m.groupValues[2].toLong() * 60) * 1000 + (m.groupValues[3].toDouble() * 1000).toLong()
                if (totalMs > 0) onProgress((t.toFloat() / totalMs).coerceIn(0f, 1f))
            }
        }
        return p.waitFor() to log.toString()
    }

    /**
     * Aplica la edición. Si [replace] es true sustituye el archivo de Ember;
     * si no, crea una copia nueva en la biblioteca. Devuelve la pista resultante.
     */
    fun export(ctx: Context, t: Track, spec: EditSpec, replace: Boolean, onProgress: (Float) -> Unit): Track {
        Engine.ensureInit(ctx)
        val input = localFile(ctx, t)
        val video = t.isVideo
        val audio = hasAudio(input)
        val ext = if (video) "mp4" else (input.extension.lowercase().takeIf { it in setOf("mp3", "m4a", "ogg", "opus", "wav", "flac") } ?: "mp3")
        val out = File(ctx.cacheDir, "editar/salida_${UUID.randomUUID()}.$ext")
        out.parentFile?.mkdirs()
        val totalMs = spec.segments.sumOf { it.length }

        fun args(videoCodec: List<String>): List<String> = buildList {
            addAll(listOf("-y", "-hide_banner", "-i", input.absolutePath, "-filter_complex", filter(spec, video, audio)))
            if (video) { add("-map"); add("[vout]") }
            if (audio) { add("-map"); add("[aout]") }
            add("-map_metadata"); add("0")
            if (video) {
                addAll(videoCodec)
                if (audio) addAll(listOf("-c:a", "aac", "-b:a", "160k"))
                addAll(listOf("-movflags", "+faststart"))
            } else when (ext) {
                "mp3" -> addAll(listOf("-map", "0:v?", "-c:v", "copy", "-disposition:v", "attached_pic", "-c:a", "libmp3lame", "-q:a", "2", "-id3v2_version", "3"))
                "m4a" -> addAll(listOf("-c:a", "aac", "-b:a", "192k"))
                "opus", "ogg" -> addAll(listOf("-c:a", "libopus", "-b:a", "128k"))
                "flac" -> addAll(listOf("-c:a", "flac"))
                else -> addAll(listOf("-c:a", "pcm_s16le"))
            }
            add(out.absolutePath)
        }

        var (code, log) = run(ctx, args(listOf("-c:v", "libx264", "-preset", "veryfast", "-crf", "22", "-pix_fmt", "yuv420p")), totalMs, onProgress)
        if (code != 0 && video) {
            // Si este ffmpeg no trae x264, se usa un códec que siempre está
            val r = run(ctx, args(listOf("-c:v", "mpeg4", "-q:v", "3")), totalMs, onProgress)
            code = r.first; log = r.second
        }
        if (code != 0 && !video && ext == "mp3") {
            // Reintento sin la portada, por si el archivo trae una imagen rara
            val plain = args(emptyList()).toMutableList()
            val i = plain.indexOf("0:v?"); if (i > 0) repeat(6) { plain.removeAt(i - 1) }
            val r = run(ctx, plain, totalMs, onProgress); code = r.first; log = r.second
        }
        if (code != 0 || !out.exists() || out.length() == 0L) {
            out.delete()
            throw IOException(log.lines().lastOrNull { it.isNotBlank() }?.take(200) ?: "ffmpeg falló")
        }
        val newDur = durationMs(out).takeIf { it > 0 } ?: totalMs

        return if (replace && !t.fromDevice) {
            val target = File(Uri.parse(t.uri).path!!)
            val final = if (target.extension.equals(ext, true)) target else File(target.parentFile, target.nameWithoutExtension + "." + ext)
            out.copyTo(final, overwrite = true); out.delete()
            if (final != target) target.delete()
            Library.update(t.id) { it.copy(uri = Uri.fromFile(final).toString(), durationMs = newDur) }
            Library.state.value.track(t.id) ?: t
        } else {
            val id = UUID.randomUUID().toString()
            val dest = File(Library.mediaDir, "$id.$ext")
            out.copyTo(dest, overwrite = true); out.delete()
            val cover = t.coverUri?.let { c ->
                val cu = Uri.parse(c)
                if (cu.scheme == "file") {
                    val src = File(cu.path!!)
                    if (src.exists()) File(Library.coverDir, "$id.${src.extension}").also { src.copyTo(it, true) }.let { Uri.fromFile(it).toString() } else null
                } else c
            }
            val copy = t.copy(
                id = id, title = t.title + " (editado)", uri = Uri.fromFile(dest).toString(), coverUri = cover,
                durationMs = newDur, addedAt = System.currentTimeMillis(), liked = false, deviceUri = null,
                fromDevice = false, plays = 0, lastPlayed = 0L,
            )
            Library.add(copy)
            copy
        }
    }
}
