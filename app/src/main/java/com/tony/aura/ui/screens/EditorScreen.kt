package com.tony.aura.ui.screens

import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FirstPage
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.LastPage
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tony.aura.data.Track
import com.tony.aura.edit.EditSpec
import com.tony.aura.edit.MediaTools
import com.tony.aura.edit.Seg
import com.tony.aura.player.PlayerConnection
import com.tony.aura.ui.LocalUi
import com.tony.aura.ui.components.Orb
import com.tony.aura.ui.components.formatTime
import com.tony.aura.ui.theme.Aura
import com.tony.aura.ui.theme.Jakarta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Editor de clips: vista previa, línea de tiempo, dividir, borrar tramos, recortar, fundidos y volumen. */
@OptIn(UnstableApi::class)
@Composable
fun EditorScreen(track: Track, onClose: () -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var file by remember { mutableStateOf<File?>(null) }
    var duration by remember { mutableLongStateOf(track.durationMs) }
    var wave by remember { mutableStateOf<FloatArray?>(null) }
    var thumbs by remember { mutableStateOf<List<Bitmap?>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val segs = remember { mutableStateListOf<Seg>() }
    val undo = remember { mutableStateListOf<List<Seg>>() }
    var selected by remember { mutableIntStateOf(0) }
    var head by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var fadeIn by remember { mutableFloatStateOf(0f) }
    var fadeOut by remember { mutableFloatStateOf(0f) }
    var volume by remember { mutableFloatStateOf(1f) }
    var panel by remember { mutableStateOf<String?>(null) }
    var askSave by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) {
        PlayerConnection.controller?.pause()
        onDispose { player.release() }
    }

    // Carga: archivo local, duración, forma de onda y fotogramas
    LaunchedEffect(track.id) {
        try {
            val f = withContext(Dispatchers.IO) { MediaTools.localFile(ctx, track) }
            file = f
            val d = withContext(Dispatchers.IO) { MediaTools.durationMs(f) }.takeIf { it > 0 } ?: track.durationMs
            duration = d
            segs.clear(); segs.add(Seg(0, d))
            player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(f)))
            player.prepare()
            if (track.isVideo) launch { thumbs = withContext(Dispatchers.IO) { MediaTools.thumbnails(f, d, 16, 160, 90) } }
            wave = withContext(Dispatchers.IO) { try { MediaTools.waveform(ctx, f, d, 1200) } catch (_: Throwable) { FloatArray(0) } }
        } catch (e: Throwable) {
            loadError = e.message ?: "No se pudo abrir el archivo"
        }
    }

    // Reproducción de la vista previa saltándose los tramos borrados
    LaunchedEffect(playing) {
        if (playing) player.play() else player.pause()
        while (playing) {
            val pos = player.currentPosition
            val inside = segs.firstOrNull { pos >= it.start && pos < it.end }
            if (inside == null) {
                val next = segs.firstOrNull { it.start > pos }
                if (next == null) { playing = false; player.pause(); break }
                player.seekTo(next.start)
            }
            head = player.currentPosition
            delay(40)
        }
    }

    fun pushUndo() { undo.add(segs.toList()); if (undo.size > 30) undo.removeAt(0) }
    fun selectAt(ms: Long) { segs.indexOfFirst { ms >= it.start && ms <= it.end }.takeIf { it >= 0 }?.let { selected = it } }
    fun seek(ms: Long) { head = ms.coerceIn(0, duration); player.seekTo(head) }
    /** Escucha (o ve) un instante alrededor del punto elegido. */
    fun audition(ms: Long) {
        seek(ms)
        if (playing) return
        scope.launch { player.play(); delay(1200); if (!playing) { player.pause(); player.seekTo(head) } }
    }

    val kept = segs.sumOf { it.length }

    Box(Modifier.fillMaxSize().background(Aura.Base)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Barra superior
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PlainIcon(Icons.Rounded.Close, 24.dp, Color.White) { onClose() }
                Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                    Text("Editar", color = Aura.Slate400, fontFamily = Jakarta, fontSize = 12.sp)
                    Text(track.title, color = Color.White, fontFamily = Jakarta, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "Guardar",
                    Modifier.clip(CircleShape).background(if (file != null) Aura.Brand500 else Aura.Slate800)
                        .clickable(enabled = file != null && !exporting) { askSave = true }.padding(horizontal = 18.dp, vertical = 9.dp),
                    color = Aura.OnAccent, fontFamily = Jakarta, fontSize = 14.sp,
                )
            }

            // Vista previa
            Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                when {
                    loadError != null -> Text(loadError!!, color = Aura.Brand400, fontFamily = Jakarta)
                    track.isVideo -> Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)).background(Color.Black)) {
                        AndroidView(
                            factory = { c -> PlayerView(c).apply {
                                useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setShutterBackgroundColor(android.graphics.Color.BLACK); this.player = player
                            } },
                            onRelease = { it.player = null },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) { Orb(Modifier.size(180.dp), breathing = playing) }
                }
            }

            // Tiempo y transporte
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(head), color = Color.White, fontFamily = Jakarta, fontSize = 14.sp)
                Text(" / ${formatTime(duration)}", color = Aura.Slate400, fontFamily = Jakarta, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                PlainIcon(Icons.Rounded.ZoomOut, 20.dp, Aura.Slate300) { zoom = (zoom / 2f).coerceAtLeast(1f) }
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Aura.Elevated).clickable(enabled = file != null) { playing = !playing },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Reproducir", tint = Color.White) }
                PlainIcon(Icons.Rounded.ZoomIn, 20.dp, Aura.Slate300) { zoom = (zoom * 2f).coerceAtMost(32f) }
                Spacer(Modifier.weight(1f))
                Text("Resultado ${formatTime(kept)}", color = Aura.Brand400, fontFamily = Jakarta, fontSize = 12.sp)
            }

            // Línea de tiempo
            Timeline(
                duration = duration, segs = segs, selected = selected, head = head, zoom = zoom,
                wave = wave, thumbs = thumbs, isVideo = track.isVideo, playing = playing,
                onScrub = { ms -> seek(ms) },
                onScrubEnd = { ms -> selectAt(ms); audition(ms) },
                onTap = { ms -> selectAt(ms); audition(ms) },
            )

            // Ajustes del panel activo
            when (panel) {
                "fundido" -> Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    LabeledSlider("Entrada", fadeIn / 5f, "${"%.1f".format(fadeIn)} s") { fadeIn = it * 5f }
                    LabeledSlider("Salida", fadeOut / 5f, "${"%.1f".format(fadeOut)} s") { fadeOut = it * 5f }
                }
                "volumen" -> Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    LabeledSlider("Volumen", volume / 2f, "${(volume * 100).toInt()} %") { volume = (it * 2f).coerceAtLeast(0.05f); player.volume = volume.coerceAtMost(1f) }
                }
            }

            // Herramientas (estilo CapCut)
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Tool(Icons.Rounded.ContentCut, "Dividir") {
                    val i = segs.indexOfFirst { head > it.start + 100 && head < it.end - 100 }
                    if (i >= 0) { pushUndo(); val g = segs[i]; segs[i] = Seg(g.start, head); segs.add(i + 1, Seg(head, g.end)); selected = i + 1 }
                    else ui.toast("Mueve el cabezal dentro de un tramo")
                }
                Tool(Icons.Rounded.FirstPage, "Inicio aquí") {
                    val g = segs.getOrNull(selected)
                    if (g != null && head > g.start && head < g.end - 100) { pushUndo(); segs[selected] = Seg(head, g.end) }
                    else ui.toast("Coloca el cabezal dentro del tramo elegido")
                }
                Tool(Icons.Rounded.LastPage, "Fin aquí") {
                    val g = segs.getOrNull(selected)
                    if (g != null && head > g.start + 100 && head < g.end) { pushUndo(); segs[selected] = Seg(g.start, head) }
                    else ui.toast("Coloca el cabezal dentro del tramo elegido")
                }
                Tool(Icons.Rounded.Delete, "Borrar tramo") {
                    if (segs.size > 1 && selected in segs.indices) { pushUndo(); segs.removeAt(selected); selected = selected.coerceAtMost(segs.size - 1) }
                    else ui.toast("Divide primero: no se puede borrar el único tramo")
                }
                Tool(Icons.Rounded.Gradient, "Fundido", panel == "fundido") { panel = if (panel == "fundido") null else "fundido" }
                Tool(Icons.Rounded.VolumeUp, "Volumen", panel == "volumen") { panel = if (panel == "volumen") null else "volumen" }
                Tool(Icons.AutoMirrored.Rounded.Undo, "Deshacer") {
                    undo.removeLastOrNull()?.let { segs.clear(); segs.addAll(it); selected = selected.coerceAtMost(segs.size - 1) }
                }
            }
        }

        if (exporting) Box(Modifier.fillMaxSize().background(Aura.Base.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(progress = { progress }, color = Aura.Brand500, trackColor = Aura.Slate800, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(14.dp))
                Text("Guardando… ${(progress * 100).toInt()} %", color = Color.White, fontFamily = Jakarta, fontSize = 15.sp)
            }
        }
    }

    if (askSave) AlertDialog(
        onDismissRequest = { askSave = false }, containerColor = Aura.Elevated,
        title = { Text("Guardar cambios", color = Color.White, fontFamily = Jakarta) },
        text = {
            Text(
                if (track.fromDevice) "El archivo es del teléfono: se guardará una copia editada en Ember."
                else "¿Sustituyes el archivo o guardas una copia nueva? La copia conserva el original.",
                color = Aura.Slate300, fontFamily = Jakarta,
            )
        },
        confirmButton = {
            Row {
                if (!track.fromDevice) TextButton(onClick = { askSave = false; save(ctx, track, segs.toList(), fadeIn, fadeOut, volume, true, { exporting = it }, { progress = it }, ui::toast, onClose, scope) }) {
                    Text("Sustituir", color = Aura.Brand400, fontFamily = Jakarta)
                }
                TextButton(onClick = { askSave = false; save(ctx, track, segs.toList(), fadeIn, fadeOut, volume, false, { exporting = it }, { progress = it }, ui::toast, onClose, scope) }) {
                    Text("Guardar copia", color = Aura.Brand400, fontFamily = Jakarta)
                }
            }
        },
        dismissButton = { TextButton(onClick = { askSave = false }) { Text("Cancelar", color = Aura.Slate300, fontFamily = Jakarta) } },
    )
}

private fun save(
    ctx: android.content.Context, track: Track, segs: List<Seg>, fadeIn: Float, fadeOut: Float, volume: Float, replace: Boolean,
    setExporting: (Boolean) -> Unit, setProgress: (Float) -> Unit, toast: (String) -> Unit, onClose: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (replace && PlayerConnection.ui.value.current?.id == track.id) PlayerConnection.forget(track.id)
    setExporting(true); setProgress(0f)
    scope.launch {
        val msg = withContext(Dispatchers.IO) {
            try {
                MediaTools.export(ctx, track, EditSpec(segs, fadeIn, fadeOut, volume), replace) { p -> scope.launch { setProgress(p) } }
                if (replace) "Archivo editado" else "Copia editada guardada en tu biblioteca"
            } catch (e: Throwable) { "No se pudo guardar: ${e.message}" }
        }
        setExporting(false)
        toast(msg)
        if (!msg.startsWith("No se pudo")) onClose()
    }
}

@Composable
private fun Timeline(
    duration: Long, segs: List<Seg>, selected: Int, head: Long, zoom: Float,
    wave: FloatArray?, thumbs: List<Bitmap?>, isVideo: Boolean, playing: Boolean,
    onScrub: (Long) -> Unit, onScrubEnd: (Long) -> Unit, onTap: (Long) -> Unit,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        val viewW = maxWidth
        val totalW = viewW * zoom
        val totalPx = with(density) { totalW.toPx() }
        fun msAt(x: Float) = ((x / totalPx).coerceIn(0f, 1f) * duration).toLong()
        // Mantener el cabezal a la vista mientras suena
        LaunchedEffect(head, zoom) {
            val x = (head.toFloat() / duration.coerceAtLeast(1)) * totalPx
            val vw = with(density) { viewW.toPx() }
            if (x < scroll.value || x > scroll.value + vw - 40) scroll.scrollTo((x - vw / 3).toInt().coerceAtLeast(0))
        }
        Column(Modifier.horizontalScroll(scroll)) {
            // Regla: arrastra aquí para mover el cabezal y oír/ver el punto de corte
            Canvas(
                Modifier.width(totalW).height(26.dp)
                    .pointerInput(totalPx, duration) {
                        var x = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { o -> x = o.x; onScrub(msAt(x)) },
                            onDragEnd = { onScrubEnd(msAt(x)) },
                            onHorizontalDrag = { ch, dx -> ch.consume(); x = (x + dx).coerceIn(0f, totalPx); onScrub(msAt(x)) },
                        )
                    }
                    .pointerInput(totalPx, duration) { detectTapGestures { o -> onTap(msAt(o.x)) } },
            ) {
                val ticks = (10 * zoom).toInt().coerceAtLeast(10)
                for (i in 0..ticks) {
                    val x = size.width * i / ticks
                    drawLine(Aura.Slate700, Offset(x, size.height * (if (i % 5 == 0) 0.35f else 0.6f)), Offset(x, size.height), 1.dp.toPx())
                }
            }
            // Pistas: fotogramas (vídeo) y forma de onda
            Canvas(
                Modifier.width(totalW).height(if (isVideo) 96.dp else 84.dp)
                    .pointerInput(totalPx, duration) { detectTapGestures { o -> onTap(msAt(o.x)) } },
            ) {
                val w = size.width; val h = size.height
                val waveTop = if (isVideo) h * 0.62f else 0f
                // fotogramas
                if (isVideo && thumbs.isNotEmpty()) {
                    val tw = w / thumbs.size
                    thumbs.forEachIndexed { i, b ->
                        if (b != null) drawImage(
                            b.asImageBitmap(), dstOffset = IntOffset((i * tw).toInt(), 0),
                            dstSize = IntSize(tw.toInt() + 1, (h * 0.58f).toInt()),
                        ) else drawRect(Aura.Slate800, Offset(i * tw, 0f), Size(tw, h * 0.58f))
                    }
                }
                // forma de onda
                val wv = wave
                val wh = h - waveTop
                if (wv != null && wv.isNotEmpty()) {
                    val bars = (w / 3.dp.toPx()).toInt().coerceAtLeast(1)
                    for (b in 0 until bars) {
                        val idx = (b.toFloat() / bars * wv.size).toInt().coerceIn(0, wv.size - 1)
                        val amp = wv[idx].coerceAtLeast(0.04f) * wh * 0.9f
                        val x = b * 3.dp.toPx()
                        drawLine(Aura.Rose400.copy(alpha = 0.85f), Offset(x, waveTop + wh / 2 - amp / 2), Offset(x, waveTop + wh / 2 + amp / 2), 2.dp.toPx())
                    }
                } else drawRect(Aura.Slate900, Offset(0f, waveTop), Size(w, wh))
                // tramos borrados oscurecidos, tramos conservados enmarcados
                var last = 0L
                val d = duration.coerceAtLeast(1).toFloat()
                segs.forEach { g ->
                    if (g.start > last) drawRect(Color.Black.copy(alpha = 0.68f), Offset(last / d * w, 0f), Size((g.start - last) / d * w, h))
                    last = g.end
                }
                if (last < duration) drawRect(Color.Black.copy(alpha = 0.68f), Offset(last / d * w, 0f), Size((duration - last) / d * w, h))
                segs.forEachIndexed { i, g ->
                    val sel = i == selected
                    drawRoundRect(
                        if (sel) Aura.Brand500 else Color.White.copy(alpha = 0.35f),
                        Offset(g.start / d * w + 1, 1f), Size((g.length / d * w - 2).coerceAtLeast(2f), h - 2),
                        CornerRadius(6.dp.toPx()), style = Stroke(if (sel) 3.dp.toPx() else 1.dp.toPx()),
                    )
                }
                // cabezal
                val hx = head / d * w
                drawLine(Color.White, Offset(hx, 0f), Offset(hx, h), 2.dp.toPx())
            }
        }
        // marcador del cabezal sobre la regla (sigue al desplazamiento)
        val headDp = with(density) { ((head.toFloat() / duration.coerceAtLeast(1)) * totalPx - scroll.value).toDp() }
        if (headDp >= 0.dp && headDp <= viewW) Box(
            Modifier.padding(start = (headDp - 7.dp).coerceAtLeast(0.dp), top = 6.dp).size(14.dp).clip(CircleShape).background(Color.White)
                .border(2.dp, if (playing) Aura.Brand500 else Aura.Base, CircleShape),
        )
    }
    Text(
        "Toca o arrastra sobre la regla para mover el cabezal: oirás o verás ese punto. Toca un tramo para elegirlo.",
        Modifier.padding(horizontal = 16.dp), color = Aura.Slate400, fontFamily = Jakarta, fontSize = 11.sp,
    )
}

@Composable
private fun Tool(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (active) Aura.Brand500 else Aura.Slate200, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (active) Aura.Brand500 else Aura.Slate400, fontFamily = Jakarta, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, valueText: String, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(76.dp), color = Aura.Slate300, fontFamily = Jakarta, fontSize = 12.sp)
        ThinSeekBar(value.coerceIn(0f, 1f), onChange, onChange, Aura.Brand500, Aura.Slate700, Modifier.weight(1f))
        Text(valueText, Modifier.width(56.dp).padding(start = 8.dp), color = Aura.Slate400, fontFamily = Jakarta, fontSize = 12.sp)
    }
}

