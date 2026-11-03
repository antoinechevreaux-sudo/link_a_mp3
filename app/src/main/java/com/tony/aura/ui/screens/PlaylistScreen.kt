package com.tony.aura.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tony.aura.data.DeviceMusic
import com.tony.aura.data.Library
import com.tony.aura.data.Track
import com.tony.aura.player.PlayerConnection
import com.tony.aura.ui.LocalUi
import com.tony.aura.ui.Special
import com.tony.aura.ui.Tab
import com.tony.aura.ui.components.Cover
import com.tony.aura.ui.components.EmptyState
import com.tony.aura.ui.components.Kicker
import com.tony.aura.ui.components.RoundButton
import com.tony.aura.ui.components.TextInputDialog
import com.tony.aura.ui.components.TrackRow
import com.tony.aura.ui.components.formatTime
import com.tony.aura.ui.components.redGlow
import com.tony.aura.ui.theme.Aura
import com.tony.aura.ui.theme.Jakarta

@Composable
fun PlaylistScreen(id: String) {
    val ui = LocalUi.current
    val lib by Library.state.collectAsState()
    val device by DeviceMusic.tracks.collectAsState()
    val player by PlayerConnection.ui.collectAsState()
    val playlist = lib.playlists.firstOrNull { it.id == id }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val (kicker, title, tracks: List<Track>) = when (id) {
        Special.LIKED -> Triple("Colección", "Tus me gusta", lib.liked)
        Special.ALL -> Triple("Colección", "Todo lo descargado", lib.recent)
        Special.VIDEOS -> Triple("Colección", "Vídeos", lib.tracks.filter { it.isVideo })
        Special.DEVICE -> Triple("En el teléfono", "Tu música", device)
        else -> Triple("Playlist", playlist?.name ?: "Playlist", playlist?.let { lib.tracksOf(it) } ?: emptyList())
    }
    val total = tracks.sumOf { it.durationMs }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
        item {
            Box(Modifier.fillMaxWidth()) {
                // Cabecera con degradado carmesí
                Box(Modifier.fillMaxWidth().height(320.dp).background(
                    Brush.verticalGradient(listOf(Aura.Brand700.copy(alpha = 0.55f), Aura.Base))
                ))
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, { ui.openPlaylist = null }, size = 38.dp, contentDescription = "Atrás")
                        Spacer(Modifier.weight(1f))
                        if (playlist != null) Box {
                            RoundButton(Icons.Rounded.MoreHoriz, { menu = true }, size = 38.dp, contentDescription = "Más")
                            DropdownMenu(menu, { menu = false }, containerColor = Aura.Elevated) {
                                DropdownMenuItem(
                                    text = { Text("Cambiar nombre", fontFamily = Jakarta, color = Color.White) },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = Aura.Slate300) },
                                    onClick = { menu = false; renaming = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Eliminar playlist", fontFamily = Jakarta, color = Aura.Brand400) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Aura.Brand400) },
                                    onClick = { menu = false; deleting = true },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    val shape = RoundedCornerShape(24.dp)
                    Box(Modifier.align(Alignment.CenterHorizontally).size(190.dp).redGlow(shape, 30.dp, 0.5f)
                        .border(2.dp, Aura.Brand500.copy(alpha = 0.3f), shape)) {
                        Cover(tracks.firstOrNull()?.coverUri, Modifier.fillMaxSize(), shape, seed = title)
                    }
                    Spacer(Modifier.height(18.dp))
                    Kicker(kicker)
                    Text(title, color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.Black, fontSize = 26.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${tracks.size} elementos" + if (total > 0) " · ${formatTime(total)}" else "",
                        color = Aura.Slate400, fontFamily = Jakarta, fontSize = 12.sp,
                    )
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Spacer(Modifier.weight(1f))
                        RoundButton(Icons.Rounded.Shuffle, { ui.play(tracks, (tracks.indices).random(), shuffle = true) },
                            size = 46.dp, enabled = tracks.isNotEmpty(), contentDescription = "Aleatorio")
                        RoundButton(Icons.Rounded.PlayArrow, { ui.play(tracks, 0) }, size = 56.dp, iconSize = 30.dp,
                            primary = true, enabled = tracks.isNotEmpty(), contentDescription = "Reproducir")
                    }
                }
            }
        }
        if (tracks.isEmpty()) item {
            if (id == Special.DEVICE && !DeviceMusic.loaded)
                EmptyState(Icons.Rounded.MusicNote, "Sin acceso a tu música",
                    "Permite a Aura leer el audio del teléfono en Ajustes → Apps → Aura → Permisos.")
            else EmptyState(Icons.Rounded.MusicNote, "Aquí no hay nada aún",
                "Descarga algo y elige esta playlist como destino, o añade canciones desde su menú ⋮.",
                "Ir al descargador") { ui.openPlaylist = null; ui.tab = Tab.DOWNLOAD }
        }
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
            Box(Modifier.padding(horizontal = 16.dp)) {
                TrackRow(t, { ui.play(tracks, i) }, { ui.optionsFor = t }, playing = player.current?.id == t.id, index = i + 1)
            }
        }
    }

    if (renaming && playlist != null) TextInputDialog("Cambiar nombre", "Nombre", playlist.name, "Guardar",
        onDismiss = { renaming = false }) { Library.renamePlaylist(playlist.id, it); renaming = false }
    if (deleting && playlist != null) AlertDialog(
        onDismissRequest = { deleting = false }, containerColor = Aura.Elevated,
        title = { Text("¿Eliminar «${playlist.name}»?", fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, color = Color.White) },
        text = { Text("Las canciones siguen en tu biblioteca.", fontFamily = Jakarta, color = Aura.Slate300, textAlign = TextAlign.Start) },
        confirmButton = {
            TextButton(onClick = { Library.deletePlaylist(playlist.id); deleting = false; ui.openPlaylist = null }) {
                Text("Eliminar", color = Aura.Brand400, fontFamily = Jakarta, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancelar", color = Aura.Slate300, fontFamily = Jakarta) } },
    )
}
