package com.tony.aura.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.tony.aura.data.Library
import com.tony.aura.player.PlayerConnection
import com.tony.aura.player.PlayerUi
import com.tony.aura.ui.LocalUi
import com.tony.aura.ui.Tab
import com.tony.aura.ui.components.AuraChip
import com.tony.aura.ui.components.Cover
import com.tony.aura.ui.components.EmptyState
import com.tony.aura.ui.components.GhostButton
import com.tony.aura.ui.components.RoundButton
import com.tony.aura.ui.components.SectionTitle
import com.tony.aura.ui.components.TrackRow
import com.tony.aura.ui.components.formatTime
import com.tony.aura.ui.components.redGlow
import com.tony.aura.ui.theme.Aura
import com.tony.aura.ui.theme.Jakarta

fun speedLabel(s: Float): String {
    val txt = if (s % 1f == 0f) s.toInt().toString() else s.toString().trimEnd('0').replace('.', ',')
    return "$txt×"
}

@Composable
fun NowPlayingScreen() {
    val ui = LocalUi.current
    val p by PlayerConnection.ui.collectAsState()
    val lib by Library.state.collectAsState()
    val t = p.current

    if (t == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Spacer(Modifier.height(60.dp))
            EmptyState(Icons.Rounded.MusicNote, "No suena nada", "Elige una canción de tu colección o descarga una nueva.", "Ir a la colección") {
                ui.tab = Tab.LIBRARY
            }
        }
        return
    }
    val liked = lib.track(t.id)?.liked == true
    var showSpeeds by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Icons.Rounded.KeyboardArrowDown, { ui.tab = Tab.HOME }, size = 36.dp, contentDescription = "Cerrar")
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("REPRODUCIENDO AHORA", color = Aura.Brand500, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.sp, letterSpacing = 2.sp)
                    Text(if (p.queue.size > 1) "${p.index + 1} de ${p.queue.size}" else "Aura", color = Aura.Slate200,
                        fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                RoundButton(Icons.Rounded.MoreHoriz, { ui.optionsFor = t }, size = 36.dp, contentDescription = "Opciones")
            }
        }
        item {
            val shape = RoundedCornerShape(28.dp)
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.fillMaxWidth(0.82f).aspectRatio(1f).redGlow(shape, 40.dp, 0.6f)
                        .border(2.dp, Aura.Brand500.copy(alpha = 0.3f), shape)
                        .clickable(enabled = t.isVideo) { ui.videoFullscreen = true },
                ) {
                    Cover(t.coverUri, Modifier.fillMaxSize(), shape, t.isVideo, t.title)
                    Box(Modifier.fillMaxSize().clip(shape).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
                    if (t.isVideo) Box(
                        Modifier.align(Alignment.Center).size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Fullscreen, "Pantalla completa", tint = Color.White, modifier = Modifier.size(36.dp)) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(t.title, color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.Black, fontSize = 22.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, letterSpacing = (-0.3).sp)
                    Text(t.artist, color = Aura.Slate400, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                RoundButton(
                    if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, { Library.toggleLike(t) },
                    size = 44.dp, tint = if (liked) Aura.Brand500 else Aura.Slate400, contentDescription = "Me gusta",
                )
            }
        }
        item { Wave(p.isPlaying) }
        item { SeekBar(p) }
        item {
            // Controles: -15 · anterior · play · siguiente · +15
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                SkipButton(false) { PlayerConnection.back15() }
                PlainIcon(Icons.Rounded.SkipPrevious, 34.dp) { PlayerConnection.previous() }
                RoundButton(if (p.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, { PlayerConnection.togglePlay() },
                    size = 70.dp, iconSize = 36.dp, primary = true, contentDescription = "Reproducir")
                PlainIcon(Icons.Rounded.SkipNext, 34.dp) { PlayerConnection.next() }
                SkipButton(true) { PlayerConnection.forward15() }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                PlainIcon(Icons.Rounded.Shuffle, 22.dp, if (p.shuffle) Aura.Brand500 else Aura.Slate400) { PlayerConnection.toggleShuffle() }
                AuraChip(speedLabel(p.speed), p.speed != 1f, { showSpeeds = !showSpeeds }, Icons.Rounded.Speed)
                if (t.isVideo) GhostButton("Pantalla completa", { ui.videoFullscreen = true }, icon = Icons.Rounded.Fullscreen)
                PlainIcon(
                    if (p.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, 22.dp,
                    if (p.repeatMode != Player.REPEAT_MODE_OFF) Aura.Brand500 else Aura.Slate400,
                ) { PlayerConnection.cycleRepeat() }
            }
        }
        if (showSpeeds) item {
            LazyRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PlayerConnection.SPEEDS) { s -> AuraChip(speedLabel(s), s == p.speed, { PlayerConnection.setSpeed(s) }) }
            }
        }
        val upNext = if (p.queue.size > 1) p.queue.drop(p.index + 1).take(20) else emptyList()
        if (upNext.isNotEmpty()) {
            item { Column { Spacer(Modifier.height(24.dp)); SectionTitle("A continuación") } }
            itemsIndexed(upNext) { i, track ->
                TrackRow(track, { PlayerConnection.skipToIndex(p.index + 1 + i) }, { ui.optionsFor = track })
            }
        }
    }
}

@Composable
fun SeekBar(p: PlayerUi, light: Boolean = false) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val dur = p.durationMs.coerceAtLeast(1L).toFloat()
    val value = if (dragging) dragValue else (p.positionMs / dur).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Slider(
            value = value,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { PlayerConnection.seekTo((dragValue * dur).toLong()); dragging = false },
            colors = SliderDefaults.colors(
                thumbColor = Color.White, activeTrackColor = Aura.Brand500,
                inactiveTrackColor = if (light) Color.White.copy(alpha = 0.25f) else Aura.Slate800,
            ),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(if (dragging) (dragValue * dur).toLong() else p.positionMs), color = Aura.Slate400,
                fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(formatTime(p.durationMs), color = Aura.Slate400, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

/** Botón de salto de 15 s (icono circular con el número). */
@Composable
fun SkipButton(forward: Boolean, size: Dp = 48.dp, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size * 0.72f).border(2.dp, Aura.Slate200, CircleShape), contentAlignment = Alignment.Center) {
            Text("15", color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.27f).sp)
        }
        Box(
            Modifier.align(if (forward) Alignment.TopEnd else Alignment.TopStart).padding(top = size * 0.08f, start = size * 0.1f, end = size * 0.1f)
                .size(size * 0.22f).clip(CircleShape).background(Aura.Brand500),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (forward) "+" else "−", color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.18f).sp)
        }
    }
}

@Composable
fun PlainIcon(icon: ImageVector, size: Dp, tint: Color = Aura.Slate200, onClick: () -> Unit) {
    Box(Modifier.clip(CircleShape).clickable(onClick = onClick).padding(8.dp), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size))
    }
}

@Composable
private fun Wave(playing: Boolean) {
    val tr = rememberInfiniteTransition(label = "onda")
    val bars = listOf(0, 200, 400, 100, 300, 150, 350)
    Row(Modifier.fillMaxWidth().height(28.dp).padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        bars.forEachIndexed { i, delay ->
            val h by tr.animateFloat(
                0.2f, 1f,
                infiniteRepeatable(tween(600, delayMillis = delay), RepeatMode.Reverse), label = "barra$i",
            )
            Box(
                Modifier.padding(horizontal = 2.dp).width(4.dp).fillMaxHeight(if (playing) h else 0.2f)
                    .clip(CircleShape).background(if (i % 3 == 1) Aura.Brand400 else Aura.Brand500),
            )
        }
    }
}
