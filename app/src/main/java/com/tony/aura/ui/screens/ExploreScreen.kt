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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tony.aura.data.DeviceMusic
import com.tony.aura.data.Library
import com.tony.aura.player.PlayerConnection
import com.tony.aura.ui.LocalUi
import com.tony.aura.ui.Special
import com.tony.aura.ui.Tab
import com.tony.aura.ui.components.EmptyState
import com.tony.aura.ui.components.PrimaryButton
import com.tony.aura.ui.components.ScreenHeader
import com.tony.aura.ui.components.TrackRow
import com.tony.aura.ui.components.auraFieldColors
import com.tony.aura.ui.theme.Aura
import com.tony.aura.ui.theme.Jakarta

@Composable
fun ExploreScreen() {
    val ui = LocalUi.current
    val lib by Library.state.collectAsState()
    val device by DeviceMusic.tracks.collectAsState()
    val player by PlayerConnection.ui.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }

    val all = remember(lib, device) {
        val ids = lib.tracks.map { it.id }.toSet()
        lib.tracks + device.filter { it.id !in ids }
    }
    val q = query.trim().lowercase()
    val results = remember(all, q) {
        if (q.isEmpty()) emptyList()
        else all.filter { q in it.title.lowercase() || q in it.artist.lowercase() }
    }
    val top = remember(lib) { lib.tracks.filter { it.plays > 0 }.sortedByDescending { it.plays }.take(10) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp)) {
        item { ScreenHeader("Catálogo", "Buscar y explorar") }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("¿Qué quieres escuchar hoy?", fontFamily = Jakarta, fontWeight = FontWeight.SemiBold) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Aura.Brand500) },
                trailingIcon = {
                    if (query.isNotEmpty()) Icon(Icons.Rounded.Close, "Borrar", tint = Aura.Slate400,
                        modifier = Modifier.clickable { query = "" })
                },
                colors = auraFieldColors(), shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { Spacer(Modifier.height(20.dp)) }

        if (q.isNotEmpty()) {
            if (q.startsWith("http")) item {
                PrimaryButton("Descargar este enlace", {
                    ui.sharedUrl = query.trim(); ui.tab = Tab.DOWNLOAD; query = ""
                }, Modifier.fillMaxWidth(), Icons.Rounded.Download)
            } else if (results.isEmpty()) item {
                EmptyState(Icons.Rounded.Search, "Sin resultados", "No hay nada que coincida con «${query.trim()}» en tu biblioteca ni en el teléfono.")
            } else {
                item { SmallTitle("${results.size} resultados") }
                itemsIndexed(results, key = { _, t -> t.id }) { i, t ->
                    TrackRow(t, { ui.play(results, i) }, { ui.optionsFor = t }, playing = player.current?.id == t.id)
                }
            }
        } else {
            item { SmallTitle("Categorías") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Category("Música", "${lib.tracks.count { !it.isVideo && !it.fromDevice }}", Icons.Rounded.Headphones,
                            Brush.linearGradient(listOf(Aura.Brand600, Aura.Slate900)), Modifier.weight(1f)) { ui.openPlaylist = Special.ALL }
                        Category("Vídeos", "${lib.tracks.count { it.isVideo }}", Icons.Rounded.Movie,
                            Brush.linearGradient(listOf(Aura.Rose700, Aura.Slate900)), Modifier.weight(1f)) { ui.openPlaylist = Special.VIDEOS }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Category("Me gusta", "${lib.liked.size}", Icons.Rounded.Favorite,
                            Brush.linearGradient(listOf(Aura.Brand900, Aura.Slate900)), Modifier.weight(1f)) { ui.openPlaylist = Special.LIKED }
                        Category("En el teléfono", "${device.size}", Icons.Rounded.PhoneAndroid,
                            Brush.linearGradient(listOf(Aura.Slate800, Aura.Slate950)), Modifier.weight(1f)) { ui.openPlaylist = Special.DEVICE }
                    }
                }
            }
            if (top.isNotEmpty()) {
                item { Column { Spacer(Modifier.height(22.dp)); SmallTitle("Más escuchados") } }
                itemsIndexed(top, key = { _, t -> t.id }) { i, t ->
                    TrackRow(t, { ui.play(top, i) }, { ui.optionsFor = t }, playing = player.current?.id == t.id, index = i + 1,
                        subtitle = "${t.artist} · ${t.plays} reproducciones")
                }
            }
        }
    }
}

@Composable
private fun SmallTitle(text: String) {
    Text(text.uppercase(), Modifier.padding(bottom = 12.dp), color = Aura.Slate300, fontFamily = Jakarta,
        fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.2.sp)
}

@Composable
private fun Category(title: String, count: String, icon: ImageVector, brush: Brush, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.height(88.dp).clip(shape).background(brush).border(1.dp, Aura.Brand500.copy(alpha = 0.18f), shape)
            .clickable(onClick = onClick).padding(12.dp),
    ) {
        Column {
            Text(title, color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.Black, fontSize = 14.sp)
            Text(count, color = Color.White.copy(alpha = 0.7f), fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
        }
        Icon(icon, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomEnd).offset(6.dp, 6.dp).size(46.dp).alpha(0.2f))
    }
}
