@file:OptIn(ExperimentalMaterial3Api::class)

package com.tony.aura.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.tony.aura.data.Kind
import com.tony.aura.data.Library
import com.tony.aura.data.Track
import com.tony.aura.download.DownloadService
import com.tony.aura.player.PlayerConnection
import com.tony.aura.ui.LocalUi
import com.tony.aura.ui.theme.Aura
import com.tony.aura.ui.theme.Jakarta
import java.io.File

@Composable
fun auraFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Aura.Brand500, unfocusedBorderColor = Aura.Border,
    focusedContainerColor = Aura.Slate900, unfocusedContainerColor = Aura.Slate900,
    cursorColor = Aura.Brand500, focusedTextColor = Color.White, unfocusedTextColor = Color.White,
    focusedLabelColor = Aura.Brand400, unfocusedLabelColor = Aura.Slate400,
    focusedPlaceholderColor = Aura.Slate400, unfocusedPlaceholderColor = Aura.Slate400,
)

@Composable
private fun SheetItem(icon: ImageVector, text: String, tint: Color = Aura.Slate200, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, color = if (tint == Aura.Brand400) Aura.Brand400 else Color.White, fontFamily = Jakarta,
            fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

/** Menú de una canción o vídeo. */
@Composable
fun TrackOptionsSheet(track: Track, playlistId: String?, onDismiss: () -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val lib by Library.state.collectAsState()
    val current = lib.track(track.id) ?: track
    val playlist = playlistId?.let { id -> lib.playlists.firstOrNull { it.id == id } }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Aura.Elevated,
        dragHandle = { Box(Modifier.padding(top = 10.dp).size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Aura.Slate700)) },
    ) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(current.coverUri, Modifier.size(54.dp), RoundedCornerShape(12.dp), current.isVideo, current.title)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(current.title, color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(current.artist, color = Aura.Slate400, fontFamily = Jakarta, fontSize = 13.sp, maxLines = 1)
                }
            }
            AuraDivider()
            Spacer(Modifier.height(6.dp))
            SheetItem(Icons.Rounded.PlaylistPlay, "Reproducir a continuación") { PlayerConnection.playNext(current); ui.toast("Sonará a continuación"); onDismiss() }
            SheetItem(Icons.AutoMirrored.Rounded.QueueMusic, "Añadir a la cola") { PlayerConnection.addToQueue(current); ui.toast("Añadida a la cola"); onDismiss() }
            SheetItem(
                if (current.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (current.liked) "Quitar de Me gusta" else "Me gusta",
                tint = if (current.liked) Aura.Brand400 else Aura.Slate200,
            ) { Library.toggleLike(current); onDismiss() }
            SheetItem(Icons.AutoMirrored.Rounded.PlaylistAdd, "Añadir a una playlist") { ui.addToPlaylistFor = current; onDismiss() }
            if (playlist != null) SheetItem(Icons.Rounded.Delete, "Quitar de «${playlist.name}»") {
                Library.removeFromPlaylist(playlist.id, current.id); onDismiss()
            }
            if (!current.fromDevice) {
                SheetItem(Icons.Rounded.Edit, "Editar título, artista y tipo") { ui.editTrack = current; onDismiss() }
                SheetItem(Icons.Rounded.PhoneAndroid, if (current.deviceUri != null) "Ya está en el dispositivo" else "Guardar en el dispositivo") {
                    if (current.deviceUri == null) saveTrackToDevice(ctx, current) { ui.toast(it) }
                    onDismiss()
                }
            }
            SheetItem(Icons.Rounded.Share, "Compartir archivo") { shareTrack(ctx, current); onDismiss() }
            if (!current.fromDevice || lib.track(current.id) != null) {
                SheetItem(Icons.Rounded.Delete, if (current.fromDevice) "Quitar de Ember" else "Eliminar de Ember", tint = Aura.Brand400) { confirmDelete = true }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        containerColor = Aura.Elevated,
        title = { Text("¿Eliminar «${current.title}»?", fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, color = Color.White) },
        text = {
            Text(
                if (current.deviceUri != null) "Se borra de Ember y de tus playlists. La copia en el dispositivo se conserva."
                else "Se borra de Ember y de tus playlists.",
                fontFamily = Jakarta, color = Aura.Slate300,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                PlayerConnection.forget(current.id); Library.delete(current.id); confirmDelete = false; onDismiss()
            }) { Text("Eliminar", color = Aura.Brand400, fontFamily = Jakarta, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar", color = Aura.Slate300, fontFamily = Jakarta) } },
    )
}

/** Elegir playlist (o crear una nueva) para una canción. */
@Composable
fun AddToPlaylistSheet(track: Track, onDismiss: () -> Unit) {
    val ui = LocalUi.current
    val lib by Library.state.collectAsState()
    var creating by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Aura.Elevated,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 12.dp)) {
            Kicker("Añadir a")
            Text("Tus playlists", color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Spacer(Modifier.height(12.dp))
            SheetItem(Icons.Rounded.Add, "Nueva playlist", tint = Aura.Brand400) { creating = true }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                lib.playlists.forEach { p ->
                    val has = track.id in p.trackIds
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (!has) { Library.addToPlaylist(p.id, track); ui.toast("Añadida a «${p.name}»") }
                                onDismiss()
                            }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val cover = lib.tracksOf(p).firstOrNull()
                        Cover(cover?.coverUri, Modifier.size(44.dp), RoundedCornerShape(10.dp), seed = p.name)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(p.name, color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${p.trackIds.size} elementos", color = Aura.Slate400, fontFamily = Jakarta, fontSize = 12.sp)
                        }
                        if (has) Text("Ya está", color = Aura.Brand400, fontFamily = Jakarta, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (creating) TextInputDialog("Nueva playlist", "Nombre", "", "Crear", onDismiss = { creating = false }) { name ->
        val p = Library.createPlaylist(name)
        Library.addToPlaylist(p.id, track)
        ui.toast("Añadida a «${p.name}»")
        creating = false
        onDismiss()
    }
}

@Composable
fun TextInputDialog(
    title: String, label: String, initial: String, confirm: String,
    onDismiss: () -> Unit, onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Aura.Elevated,
        title = { Text(title, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, color = Color.White) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, label = { Text(label, fontFamily = Jakarta) },
                singleLine = true, colors = auraFieldColors(), shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) {
                Text(confirm, color = Aura.Brand400, fontFamily = Jakarta, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Aura.Slate300, fontFamily = Jakarta) } },
    )
}

@Composable
fun EditTrackDialog(track: Track, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(track.title) }
    var artist by remember { mutableStateOf(track.artist) }
    var kind by remember { mutableStateOf(track.kind) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Aura.Elevated,
        title = { Text("Editar", fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Título", fontFamily = Jakarta) },
                    colors = auraFieldColors(), shape = RoundedCornerShape(14.dp), singleLine = true)
                OutlinedTextField(artist, { artist = it }, label = { Text("Artista", fontFamily = Jakarta) },
                    colors = auraFieldColors(), shape = RoundedCornerShape(14.dp), singleLine = true)
                if (!track.isVideo) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Kind.MUSIC, Kind.PODCAST, Kind.AUDIOBOOK).forEach { k -> AuraChip(k.label, kind == k, { kind = k }) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Library.update(track.id) { it.copy(title = title.trim().ifBlank { it.title }, artist = artist.trim(), category = kind.name) }
                onDismiss()
            }) { Text("Guardar", color = Aura.Brand400, fontFamily = Jakarta, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Aura.Slate300, fontFamily = Jakarta) } },
    )
}

// ------------------------------------------------------------------ acciones

fun shareUriFor(ctx: Context, track: Track): Uri {
    val u = Uri.parse(track.uri)
    return if (u.scheme == "file") FileProvider.getUriForFile(ctx, ctx.packageName + ".archivos", File(u.path!!)) else u
}

fun shareTrack(ctx: Context, track: Track) {
    try {
        val i = Intent(Intent.ACTION_SEND)
            .setType(if (track.isVideo) "video/mp4" else "audio/mpeg")
            .putExtra(Intent.EXTRA_STREAM, shareUriFor(ctx, track))
            .putExtra(Intent.EXTRA_TITLE, track.title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Compartir").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Throwable) {
    }
}

fun saveTrackToDevice(ctx: Context, track: Track, onResult: (String) -> Unit) {
    val main = android.os.Handler(android.os.Looper.getMainLooper())
    Thread {
        val msg = try {
            val path = Uri.parse(track.uri).path ?: throw IllegalStateException("Archivo no encontrado")
            val uri = DownloadService.saveToDevice(ctx, File(path), track.title, track.isVideo)
            Library.update(track.id) { it.copy(deviceUri = uri.toString()) }
            if (track.isVideo) "Guardado en Películas/Ember" else "Guardado en Música/Ember"
        } catch (e: Throwable) {
            "No se pudo guardar: ${e.message}"
        }
        main.post { onResult(msg) }
    }.start()
}
