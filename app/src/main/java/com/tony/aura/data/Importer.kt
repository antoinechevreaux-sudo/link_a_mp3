package com.tony.aura.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

/** Importa audio o vídeo del teléfono (selector de archivos) a la biblioteca de Ember. */
object Importer {
    fun import(ctx: Context, uris: List<Uri>): List<Track> = uris.mapNotNull { u ->
        try { importOne(ctx, u) } catch (_: Throwable) { null }
    }

    private fun importOne(ctx: Context, u: Uri): Track {
        val cr = ctx.contentResolver
        var name = "Importado"
        cr.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        val mime = cr.getType(u) ?: ""
        val isVideo = mime.startsWith("video")
        val ext = name.substringAfterLast('.', "").lowercase().takeIf { it.length in 2..4 }
            ?: when { isVideo -> "mp4"; mime.contains("mpeg") -> "mp3"; mime.contains("ogg") -> "ogg"; else -> "m4a" }
        val id = UUID.randomUUID().toString()
        val dest = File(Library.mediaDir, "$id.$ext")
        cr.openInputStream(u)?.use { i -> dest.outputStream().use { i.copyTo(it) } } ?: error("no se pudo leer")

        var title = name.substringBeforeLast('.')
        var artist = "Importado"
        var duration = 0L
        var cover: String? = null
        try {
            MediaMetadataRetriever().use { m ->
                m.setDataSource(dest.absolutePath)
                m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
                m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it }
                duration = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val bytes = m.embeddedPicture
                val f = File(Library.coverDir, "$id.jpg")
                if (bytes != null) { f.writeBytes(bytes); cover = Uri.fromFile(f).toString() }
                else if (isVideo) m.getFrameAtTime(1_000_000)?.let { bmp ->
                    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                    cover = Uri.fromFile(f).toString()
                }
            }
        } catch (_: Throwable) {}
        val track = Track(
            id = id, title = title, artist = artist, uri = Uri.fromFile(dest).toString(), coverUri = cover,
            durationMs = duration, isVideo = isVideo,
            category = Kind.guess(title, (duration / 1000).toInt(), isVideo).name,
        )
        Library.add(track)
        return track
    }
}
