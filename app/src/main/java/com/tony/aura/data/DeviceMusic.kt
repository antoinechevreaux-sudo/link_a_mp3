package com.tony.aura.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Canciones que ya están en el teléfono (requiere permiso de audio). */
object DeviceMusic {
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()
    @Volatile var loaded = false
        private set

    fun refresh(ctx: Context) {
        val list = mutableListOf<Track>()
        try {
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection = arrayOf(
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DATE_ADDED,
            )
            ctx.contentResolver.query(
                collection, projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 20000",
                null, "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val iDate = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val art = ContentUris.withAppendedId(
                        android.net.Uri.parse("content://media/external/audio/albumart"), c.getLong(iAlbum)
                    )
                    val artist = c.getString(iArtist).orEmpty().let { if (it == "<unknown>") "" else it }
                    list += Track(
                        id = "dev:$id", title = c.getString(iTitle).orEmpty(),
                        artist = artist.ifBlank { "Artista desconocido" },
                        uri = uri.toString(), coverUri = art.toString(), durationMs = c.getLong(iDur),
                        addedAt = c.getLong(iDate) * 1000, fromDevice = true,
                    )
                }
            }
        } catch (_: Throwable) {
        }
        _tracks.value = list
        loaded = true
    }
}
