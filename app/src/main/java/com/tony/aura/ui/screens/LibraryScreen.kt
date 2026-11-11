package com.tony.aura.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tony.aura.data.DeviceMusic
import com.tony.aura.data.Library
import com.tony.aura.ui.LocalUi
import com.tony.aura.ui.Special
import com.tony.aura.ui.components.Cover
import com.tony.aura.ui.components.EmptyState
import com.tony.aura.ui.components.RoundButton
import com.tony.aura.ui.components.ScreenHeader
import com.tony.aura.ui.components.SectionTitle
import com.tony.aura.ui.components.TextInputDialog
import com.tony.aura.ui.components.redGlow
import com.tony.aura.ui.theme.Aura
import com.tony.aura.ui.theme.Jakarta

@Composable
fun LibraryScreen() {
    val ui = LocalUi.current
    val lib by Library.state.collectAsState()
    val device by DeviceMusic.tracks.collectAsState()
    var creating by remember { mutableStateOf(false) }
    val importFiles = com.tony.aura.ui.components.rememberImportLauncher()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp)) {
        item {
            ScreenHeader("Colección", "Tu biblioteca") {
                RoundButton(Icons.Rounded.Settings, { ui.settingsOpen = true }, contentDescription = "Ajustes")
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                RoundButton(Icons.Rounded.Add, { creating = true }, primary = true, contentDescription = "Nueva playlist")
            }
        }
        item {
            Column {
                CollectionRow("Tus me gusta", "${lib.liked.size} guardadas", Icons.Rounded.Favorite, null, highlight = true) { ui.openPlaylist = Special.LIKED }
                CollectionRow("Todo lo descargado", "${lib.recent.size} elementos", Icons.Rounded.LibraryMusic,
                    lib.recent.firstOrNull()?.coverUri) { ui.openPlaylist = Special.ALL }
                CollectionRow("Vídeos", "${lib.tracks.count { it.isVideo }} vídeos", Icons.Rounded.Movie,
                    lib.tracks.firstOrNull { it.isVideo }?.coverUri) { ui.openPlaylist = Special.VIDEOS }
                CollectionRow("Importar del teléfono", "Audio o vídeo de tus carpetas", Icons.Rounded.FileOpen, null) { importFiles() }
                CollectionRow("Mezclador", "Varias pistas a la vez, cada una con su volumen", Icons.Rounded.Tune, null) { ui.mixerOpen = true }
                CollectionRow("En el teléfono", if (DeviceMusic.loaded) "${device.size} canciones" else "Permite el acceso a la música",
                    Icons.Rounded.PhoneAndroid, null) { ui.openPlaylist = Special.DEVICE }
            }
        }
        item { Column { Spacer(Modifier.height(12.dp)); SectionTitle("Playlists", "Nueva") { creating = true } } }
        if (lib.playlists.isEmpty()) item {
            EmptyState(Icons.AutoMirrored.Rounded.QueueMusic, "Sin playlists", "Crea una y añade canciones desde el descargador o desde el menú ⋮ de cada canción.",
                "Crear playlist") { creating = true }
        }
        items(lib.playlists, key = { it.id }) { p ->
            val tracks = lib.tracksOf(p)
            CollectionRow(p.name, "Playlist · ${tracks.size} elementos", Icons.AutoMirrored.Rounded.QueueMusic, tracks.firstOrNull()?.coverUri) {
                ui.openPlaylist = p.id
            }
        }
    }

    if (creating) TextInputDialog("Nueva playlist", "Nombre", "", "Crear", onDismiss = { creating = false }) { name ->
        val p = Library.createPlaylist(name)
        creating = false
        ui.openPlaylist = p.id
    }
}

@Composable
private fun CollectionRow(
    title: String, subtitle: String, icon: ImageVector, cover: String?,
    highlight: Boolean = false, onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(shape).background(Aura.Surface.copy(alpha = 0.85f))
            .border(1.dp, Aura.Border, shape).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cover == null) Box(
            Modifier.size(52.dp).then(if (highlight) Modifier.redGlow(RoundedCornerShape(14.dp), 12.dp) else Modifier)
                .clip(RoundedCornerShape(14.dp))
                .background(if (highlight) Brush.linearGradient(listOf(Aura.Brand600, Aura.Rose700)) else Brush.linearGradient(listOf(Aura.Slate800, Aura.Base))),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
        else Cover(cover, Modifier.size(52.dp), RoundedCornerShape(14.dp), seed = title)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Aura.Slate400, fontFamily = Jakarta, fontSize = 12.sp, maxLines = 1)
        }
    }
}
