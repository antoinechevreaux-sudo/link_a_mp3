package com.tony.aura.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.tony.aura.player.Ambient
import com.tony.aura.ui.components.rememberTimerLabel
import androidx.compose.material.icons.rounded.Bedtime
import com.tony.aura.ui.components.AuraCard
import com.tony.aura.ui.components.Kicker
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
                    Text(if (p.queue.size > 1) "${p.index + 1} de ${p.queue.size}" else "Ember", color = Aura.Slate200,
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
                val timer = rememberTimerLabel()
                AuraChip(timer ?: "Apagar", timer != null, { ui.pickTimer = true }, Icons.Rounded.Bedtime)
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
        item { Spacer(Modifier.height(22.dp)) }
        item { MixCard() }
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
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        ThinSeekBar(
            fraction = value,
            onScrub = { dragging = true; dragValue = it },
            onSeekEnd = { f -> PlayerConnection.seekTo((f * dur).toLong()); dragging = false },
            activeColor = if (light) Color.White else Aura.Brand500,
            inactiveColor = if (light) Color.White.copy(alpha = 0.28f) else Aura.Slate700,
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(if (dragging) (dragValue * dur).toLong() else p.positionMs), color = if (light) Color.White else Aura.Slate400,
                fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(formatTime(p.durationMs), color = if (light) Color.White else Aura.Slate400, fontFamily = Jakarta,
                fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
        }
    }
}

/** Barra de progreso fina (línea de 3 dp con un punto blanco), arrastrable o con toque. */
@Composable
fun ThinSeekBar(
    fraction: Float,
    onScrub: (Float) -> Unit,
    onSeekEnd: (Float) -> Unit,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    val scrub by rememberUpdatedState(onScrub)
    val end by rememberUpdatedState(onSeekEnd)
    Canvas(
        modifier.fillMaxWidth().height(26.dp)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val f = (o.x / size.width).coerceIn(0f, 1f)
                    scrub(f); end(f)
                }
            }
            .pointerInput(Unit) {
                var cur = 0f
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; cur = (o.x / size.width).coerceIn(0f, 1f); scrub(cur) },
                    onDragEnd = { dragging = false; end(cur) },
                    onDragCancel = { dragging = false; end(cur) },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        cur = (cur + dx / size.width).coerceIn(0f, 1f); scrub(cur)
                    },
                )
            },
    ) {
        val y = size.height / 2
        val th = (if (dragging) 4.dp else 3.dp).toPx()
        val x = size.width * fraction.coerceIn(0f, 1f)
        drawLine(inactiveColor, Offset(0f, y), Offset(size.width, y), th, StrokeCap.Round)
        drawLine(activeColor, Offset(0f, y), Offset(x, y), th, StrokeCap.Round)
        drawCircle(Color.White, radius = (if (dragging) 8.dp else 6.dp).toPx(), center = Offset(x, y))
    }
}

/** Botón de salto de 15 s: flecha circular fina con el número dentro (como en YouTube). */
@Composable
fun SkipButton(forward: Boolean, size: Dp = 48.dp, tint: Color = Color.White, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.78f)) {
            val s = this.size.minDimension
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val r = s * 0.40f
            val w = s * 0.075f
            val start = -75f
            scale(scaleX = if (forward) -1f else 1f, scaleY = 1f, pivot = c) {
                drawArc(
                    color = tint, startAngle = start, sweepAngle = 285f, useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
                    style = Stroke(width = w, cap = StrokeCap.Round),
                )
                // Punta de flecha en el inicio del arco, apuntando hacia atrás
                val a = Math.toRadians(start.toDouble())
                val px = c.x + r * cos(a).toFloat()
                val py = c.y + r * sin(a).toFloat()
                val dx = sin(a).toFloat(); val dy = -cos(a).toFloat()      // tangente
                val nx = cos(a).toFloat(); val ny = sin(a).toFloat()       // normal
                val len = s * 0.16f
                val path = Path().apply {
                    moveTo(px + dx * len, py + dy * len)
                    lineTo(px + nx * len * 0.75f, py + ny * len * 0.75f)
                    lineTo(px - nx * len * 0.75f, py - ny * len * 0.75f)
                    close()
                }
                drawPath(path, tint)
            }
        }
        Text("15", Modifier.padding(top = size * 0.06f), color = tint, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.26f).sp)
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

/** Mezcla: una segunda pista sonando de fondo, con volúmenes independientes. */
@Composable
private fun MixCard() {
    val ui = LocalUi.current
    val a by Ambient.state.collectAsState()
    AuraCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Layers, null, tint = Aura.Brand500, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Kicker("Mezcla · capa de fondo")
            }
            val t = a.track
            if (t == null) {
                Text("Pon lluvia, mar o ruido blanco debajo de lo que escuchas.", Modifier.padding(top = 8.dp, bottom = 12.dp),
                    color = Aura.Slate300, fontFamily = Jakarta, fontSize = 13.sp)
                GhostButton("Añadir sonido de fondo", { ui.pickAmbient = true }, Modifier.fillMaxWidth(), Icons.Rounded.Add)
            } else {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(t.coverUri, Modifier.size(44.dp), RoundedCornerShape(10.dp), seed = t.title)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(t.title, color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (a.playing) "Sonando en bucle" else "En pausa", color = if (a.playing) Aura.Brand400 else Aura.Slate400,
                            fontFamily = Jakarta, fontSize = 12.sp)
                    }
                    PlainIcon(if (a.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 24.dp, Color.White) { Ambient.toggle() }
                    PlainIcon(Icons.Rounded.SwapHoriz, 22.dp) { ui.pickAmbient = true }
                    PlainIcon(Icons.Rounded.Close, 22.dp) { Ambient.clear() }
                }
                VolumeRow("Principal", a.mainVolume) { Ambient.setMainVolume(it) }
                VolumeRow("Fondo", a.volume) { Ambient.setVolume(it) }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { Ambient.setLinked(!a.linked) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Pausar junto con la principal", Modifier.weight(1f), color = Aura.Slate200, fontFamily = Jakarta, fontSize = 13.sp)
                    Switch(
                        checked = a.linked, onCheckedChange = { Ambient.setLinked(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White, checkedTrackColor = Aura.Brand500, checkedBorderColor = Aura.Brand500,
                            uncheckedThumbColor = Aura.Slate400, uncheckedTrackColor = Aura.Slate900, uncheckedBorderColor = Aura.Border,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun VolumeRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(72.dp), color = Aura.Slate300, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        ThinSeekBar(value, onChange, onChange, Aura.Brand500, Aura.Slate700, Modifier.weight(1f))
        Text("${(value * 100).toInt()} %", Modifier.width(44.dp).padding(start = 8.dp), color = Aura.Slate400, fontFamily = Jakarta, fontSize = 11.sp)
    }
}
