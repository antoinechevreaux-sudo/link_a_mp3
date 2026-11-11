package com.tony.aura.edit

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tony.aura.data.Track
import com.tony.aura.download.Engine
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID

/** Una pista dentro de la mezcla. */
data class Layer(
    val key: String = UUID.randomUUID().toString(),
    val track: Track,
    val volume: Float = 1f,
    val offsetMs: Long = 0L,
    val loop: Boolean = false,
    val muted: Boolean = false,
)

/** Estado del mezclador; vive fuera de la pantalla para no perderse al navegar. */
object Mixer {
    val layers = mutableStateListOf<Layer>()
    /** true: dura lo que la primera pista; false: lo que la más larga (sin contar bucles) */
    var lengthFromFirst by mutableStateOf(true)

    fun add(t: Track) { layers.add(Layer(track = t, loop = layers.isNotEmpty() && t.durationMs in 1..120_000)) }
    fun update(key: String, f: (Layer) -> Layer) {
        val i = layers.indexOfFirst { it.key == key }; if (i >= 0) layers[i] = f(layers[i])
    }
    fun remove(key: String) { layers.removeAll { it.key == key } }

    fun totalMs(): Long {
        val active = layers.filter { !it.muted }
        if (active.isEmpty()) return 0L
        if (lengthFromFirst) active.first().let { return it.offsetMs + it.track.durationMs }
        val finite = active.filter { !it.loop }
        return (if (finite.isEmpty()) active else finite).maxOf { it.offsetMs + it.track.durationMs }
    }

    private fun f(v: Float) = String.format(Locale.US, "%.3f", v)

    /** Mezcla todas las pistas en un MP3 (en la caché). */
    fun render(ctx: Context, onProgress: (Float) -> Unit): File {
        Engine.ensureInit(ctx)
        val active = layers.filter { !it.muted }
        if (active.isEmpty()) throw IOException("No hay pistas activas")
        val total = totalMs()
        val totalS = f(total / 1000f)
        val inputs = active.map { MediaTools.localFile(ctx, it.track) }
        val out = File(ctx.cacheDir, "editar/mezcla_${UUID.randomUUID()}.mp3").apply { parentFile?.mkdirs() }

        fun args(normalize: Boolean): List<String> = buildList {
            addAll(listOf("-y", "-hide_banner"))
            active.forEachIndexed { i, l -> if (l.loop) addAll(listOf("-stream_loop", "-1")); addAll(listOf("-i", inputs[i].absolutePath)) }
            val fc = StringBuilder()
            active.forEachIndexed { i, l ->
                val vol = if (normalize) l.volume else l.volume * active.size
                fc.append("[$i:a]aresample=44100,aformat=channel_layouts=stereo,volume=${f(vol)},adelay=${l.offsetMs}|${l.offsetMs},atrim=end=$totalS[m$i];")
            }
            active.indices.forEach { fc.append("[m$it]") }
            fc.append("amix=inputs=${active.size}:duration=longest:dropout_transition=0")
            if (normalize) fc.append(":normalize=0")
            fc.append(",atrim=end=$totalS,alimiter=limit=0.95[out]")
            addAll(listOf("-filter_complex", fc.toString(), "-map", "[out]", "-c:a", "libmp3lame", "-q:a", "2", out.absolutePath))
        }
        var r = MediaTools.runFfmpeg(ctx, args(true), total, onProgress)
        if (r.first != 0) r = MediaTools.runFfmpeg(ctx, args(false), total, onProgress) // ffmpeg sin «normalize»
        if (r.first != 0 || !out.exists() || out.length() == 0L) {
            out.delete(); throw IOException(r.second.lines().lastOrNull { it.isNotBlank() }?.take(200) ?: "ffmpeg falló")
        }
        return out
    }

}
