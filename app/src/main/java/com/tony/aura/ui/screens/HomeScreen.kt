package com.tony.aura.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tony.aura.data.Library
import com.tony.aura.data.Track
import com.tony.aura.player.PlayerConnection
import com.tony.aura.ui.LocalUi
import com.tony.aura.ui.Special
import com.tony.aura.ui.Tab
import com.tony.aura.ui.components.AuraChip
import com.tony.aura.ui.components.Cover
import com.tony.aura.ui.components.EmptyState
import com.tony.aura.ui.components.Kicker
import com.tony.aura.ui.components.RoundButton
import com.tony.aura.ui.components.ScreenHeader
import com.tony.aura.ui.components.SectionTitle
import com.tony.aura.ui.components.TrackRow
import com.tony.aura.ui.components.redGlow
import com.tony.aura.ui.theme.Aura
import com.tony.aura.ui.theme.Jakarta
import com.tony.aura.data.Kind
import com.tony.aura.ui.components.AppLogo

private enum class HomeFilter(val label: String, val kind: Kind?) {
    ALL("Todas", null), MUSIC("Música", Kind.MUSIC), VIDEO("Vídeos", Kind.VIDEO),
    PODCAST("Podcasts", Kind.PODCAST), AUDIOBOOK("Audiolibros", Kind.AUDIOBOOK)
}

@Composable
fun HomeScreen() {
    val ui = LocalUi.current
    val lib by Library.state.collectAsState()
    val player by PlayerConnection.ui.collectAsState()
    var filter by rememberSaveable { mutableStateOf(HomeFilter.ALL) }

    val recent = remember(lib, filter) {
        filter.kind?.let { k -> lib.recent.filter { it.kind == k } } ?: lib.recent
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
    ) {
        item {
            // Encabezado: icono de la app + categorías
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                AppLogo(Modifier.size(40.dp))
                Spacer(Modifier.width(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                    items(HomeFilter.entries) { f -> AuraChip(f.label, f == filter, { filter = f }) }
                }
            }
        }
        if (filter != HomeFilter.ALL) {
            item { CategoryList(filter, recent) }
            return@LazyColumn
        }
        item {
            // Accesos rápidos (2 columnas)
            val tiles = buildList {
                add(Tile("Tus me gusta", null, Icons.Rounded.Favorite, Special.LIKED))
                add(Tile("Todo lo tuyo", lib.recent.firstOrNull()?.coverUri, Icons.Rounded.LibraryMusic, Special.ALL))
                lib.playlists.take(4).forEach { p -> add(Tile(p.name, lib.tracksOf(p).firstOrNull()?.coverUri, Icons.Rounded.GraphicEq, p.id)) }
                if (size < 4) add(Tile("Vídeos", lib.tracks.firstOrNull { it.isVideo }?.coverUri, Icons.Rounded.Movie, Special.VIDEOS))
            }.take(6)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { t -> QuickTile(t, Modifier.weight(1f)) { ui.openPlaylist = t.target } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(22.dp)) }

        if (recent.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.Download,
                    "Tu música empieza aquí",
                    "Pega un enlace de YouTube, SoundCloud, Instagram… y guárdalo en Ember o en tu teléfono.",
                    "Ir al descargador",
                ) { ui.tab = Tab.DOWNLOAD }
            }
        } else {
            item { SectionTitle("Añadido recientemente", "Ver todo") { ui.openPlaylist = Special.ALL } }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(recent.take(12)) { i, t -> CoverCard(t) { ui.play(recent, i) } }
                }
            }
            val played = lib.recentlyPlayed.take(5)
            if (played.isNotEmpty()) {
                item { Column { Spacer(Modifier.height(18.dp)); SectionTitle("Escuchado recientemente") } }
                itemsIndexed(played) { i, t ->
                    TrackRow(t, onClick = { ui.play(played, i) }, onMore = { ui.optionsFor = t }, playing = player.current?.id == t.id)
                }
            }
        }
    }
}

private data class Tile(val title: String, val cover: String?, val icon: ImageVector, val target: String)

@Composable
private fun QuickTile(t: Tile, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.clip(shape).background(Aura.Surface.copy(alpha = 0.85f)).border(1.dp, Aura.Border, shape)
            .clickable(onClick = onClick).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (t.cover == null) Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Aura.Brand600, Aura.Rose700))),
            contentAlignment = Alignment.Center,
        ) { Icon(t.icon, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        else Cover(t.cover, Modifier.size(40.dp), RoundedCornerShape(10.dp), seed = t.title)
        Text(t.title, Modifier.padding(start = 10.dp), color = Aura.Slate200, fontFamily = Jakarta,
            fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CoverCard(t: Track, width: Int = 132, onClick: () -> Unit) {
    Column(Modifier.width(width.dp).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)).border(1.dp, Aura.Border, RoundedCornerShape(18.dp))) {
            Cover(t.coverUri, Modifier.fillMaxSize(), RoundedCornerShape(18.dp), t.isVideo, t.title)
            Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(32.dp).redGlow(CircleShape, 10.dp)
                    .clip(CircleShape).background(Aura.Brand600),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(t.title, color = Aura.Slate200, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(t.artist, color = Aura.Slate400, fontFamily = Jakarta, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CategoryList(filter: HomeFilter, tracks: List<Track>) {
    val ui = LocalUi.current
    val player by PlayerConnection.ui.collectAsState()
    Column {
        if (tracks.isEmpty()) {
            EmptyState(
                Icons.Rounded.Download, "Aún no hay ${filter.label.lowercase()}",
                "Al descargar, elige el tipo «${filter.kind?.label ?: ""}» y aparecerá aquí. También puedes cambiar el tipo desde el menú ⋮ de cada elemento.",
                "Ir al descargador",
            ) { ui.tab = Tab.DOWNLOAD }
        } else {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Kicker("Categoría")
                    Text(filter.label, color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.Black, fontSize = 24.sp)
                    Text("${tracks.size} elementos", color = Aura.Slate400, fontFamily = Jakarta, fontSize = 12.sp)
                }
                RoundButton(Icons.Rounded.PlayArrow, { ui.play(tracks, 0) }, size = 50.dp, iconSize = 26.dp, primary = true,
                    contentDescription = "Reproducir todo")
            }
            tracks.forEachIndexed { i, t ->
                TrackRow(t, onClick = { ui.play(tracks, i) }, onMore = { ui.optionsFor = t }, playing = player.current?.id == t.id)
            }
        }
    }
}
