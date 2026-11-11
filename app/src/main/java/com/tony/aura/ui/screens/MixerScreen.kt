package com.tony.aura.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.tony.aura.data.Kind
import com.tony.aura.data.Library
import com.tony.aura.data.Track
import com.tony.aura.download.DownloadService
import com.tony.aura.edit.Layer
import com.tony.aura.edit.Mixer
import com.tony.aura.player.PlayerConnection
import com.tony.aura.ui.LocalUi
import com.tony.aura.ui.components.AuraChip
import com.tony.aura.ui.components.Cover
import com.tony.aura.ui.components.EmptyState
import com.tony.aura.ui.components.TrackPickerSheet
import com.tony.aura.ui.components.auraFieldColors
import com.tony.aura.ui.components.formatTime
import com.tony.aura.ui.theme.Aura
import com.tony.aura.ui.theme.Jakarta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Mezclador: varias pistas a la vez con volumen, entrada y bucle propios; escucha previa y exportación. */
@Composable
fun MixerScreen(onClose: () -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    var saveDialog by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val total = Mixer.totalMs()

    // Un reproductor por pista para la escucha previa
    val players = remember { mutableStateMapOf<String, ExoPlayer>() }
    val starters = remember { mutableListOf<Job>() }
    fun playerFor(l: Layer): ExoPlayer = players.getOrPut(l.key) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(l.track.uri))); prepare()
        }
    }
    fun stopAll() { starters.forEach { it.cancel() }; starters.clear(); players.values.forEach { it.pause() }; playing = false }
    fun startAt(at: Long) {
        stopAll()
        PlayerConnection.controller?.pause()
        Mixer.layers.forEach { l ->
            val p = playerFor(l)
            p.volume = if (l.muted) 0f else l.volume.coerceAtMost(1f)
            p.repeatMode = if (l.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            val local = at - l.offsetMs
            val dur = l.track.durationMs.coerceAtLeast(1)
            when {
                local < 0 -> { p.seekTo(0); starters += scope.launch { delay(-local); p.play() } }
                !l.loop && local >= dur -> p.pause()
                else -> { p.seekTo(if (l.loop) local % dur else local); p.play() }
            }
        }
        pos = at; playing = true
    }
    DisposableEffect(Unit) { onDispose { starters.forEach { it.cancel() }; players.values.forEach { it.release() } } }
    // Reloj de la escucha previa
    LaunchedEffect(playing) {
        val t0 = System.currentTimeMillis() - pos
        while (playing) {
            pos = System.currentTimeMillis() - t0
            if (total > 0 && pos >= total) { stopAll(); pos = 0 }
            delay(100)
        }
    }
    // Ajustes de volumen en vivo
    LaunchedEffect(Mixer.layers.toList()) {
        Mixer.layers.forEach { l -> players[l.key]?.volume = if (l.muted) 0f else l.volume.coerceAtMost(1f) }
        players.keys.filter { k -> Mixer.layers.none { it.key == k } }.forEach { k -> players.remove(k)?.release() }
    }

    Box(Modifier.fillMaxSize().background(Aura.Base)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PlainIcon(Icons.Rounded.Close, 24.dp, Color.White) { stopAll(); onClose() }
                Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                    Text("Mezclador", color = Color.White, fontFamily = Jakarta, fontSize = 20.sp)
                    Text("${Mixer.layers.size} pistas · resultado ${formatTime(total)}", color = Aura.Slate400, fontFamily = Jakarta, fontSize = 12.sp)
                }
                Text(
                    "Guardar",
                    Modifier.clip(CircleShape).background(if (Mixer.layers.any { !it.muted }) Aura.Brand500 else Aura.Slate800)
                        .clickable(enabled = Mixer.layers.any { !it.muted } && !exporting) { stopAll(); saveDialog = true }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    color = Aura.OnAccent, fontFamily = Jakarta, fontSize = 14.sp,
                )
            }

            // Transporte de la escucha previa
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(Aura.Brand500)
                        .clickable(enabled = Mixer.layers.isNotEmpty()) { if (playing) stopAll() else startAt(if (pos >= total) 0 else pos) },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Escuchar", tint = Aura.OnAccent) }
                Text(formatTime(pos), Modifier.padding(start = 12.dp).width(52.dp), color = Color.White, fontFamily = Jakarta, fontSize = 13.sp)
                ThinSeekBar(
                    if (total > 0) (pos.toFloat() / total).coerceIn(0f, 1f) else 0f,
                    { pos = (it * total).toLong() }, { f -> val at = (f * total).toLong(); if (playing) startAt(at) else pos = at },
                    Aura.Brand500, Aura.Slate700, Modifier.weight(1f),
                )
                Text(formatTime(total), Modifier.padding(start = 8.dp), color = Aura.Slate400, fontFamily = Jakarta, fontSize = 12.sp)
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Duración:", color = Aura.Slate400, fontFamily = Jakarta, fontSize = 12.sp)
                AuraChip("La de la 1.ª pista", Mixer.lengthFromFirst, { Mixer.lengthFromFirst = true })
                AuraChip("La más larga", !Mixer.lengthFromFirst, { Mixer.lengthFromFirst = false })
            }

            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
                if (Mixer.layers.isEmpty()) item {
                    EmptyState(Icons.Rounded.Tune, "Mezcla varias pistas",
                        "Por ejemplo, un podcast con ruido marrón y música suave. Cada pista tiene su volumen, cuándo entra y si se repite.",
                        "Añadir pista") { picking = true }
                }
                items(Mixer.layers, key = { it.key }) { l -> LayerCard(l, Mixer.layers.indexOf(l)) }
                if (Mixer.layers.isNotEmpty()) item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(18.dp))
                            .border(1.dp, Aura.Border, RoundedCornerShape(18.dp)).clickable { picking = true }.padding(16.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = Aura.Brand500)
                        Text("Añadir pista", Modifier.padding(start = 8.dp), color = Aura.Brand500, fontFamily = Jakarta, fontSize = 15.sp)
                    }
                }
            }
        }

        if (exporting) Box(Modifier.fillMaxSize().background(Aura.Base.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(progress = { progress }, color = Aura.Brand500, trackColor = Aura.Slate800, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(14.dp))
                Text("Mezclando… ${(progress * 100).toInt()} %", color = Color.White, fontFamily = Jakarta, fontSize = 15.sp)
            }
        }
    }

    if (picking) TrackPickerSheet("Mezclador", "Añadir pista", "Puedes usar también el audio de un vídeo.", includeVideos = true,
        onDismiss = { picking = false }) { Mixer.add(it) }

    if (saveDialog) SaveMixDialog(onDismiss = { saveDialog = false }) { name, kind, toLibrary, toDevice, share ->
        saveDialog = false
        exporting = true; progress = 0f
        scope.launch {
            val msg = withContext(Dispatchers.IO) {
                try {
                    val out = Mixer.render(ctx) { p -> scope.launch { progress = p } }
                    val id = UUID.randomUUID().toString()
                    val stored = File(Library.mediaDir, "$id.mp3")
                    out.copyTo(stored, true); out.delete()
                    val first = Mixer.layers.firstOrNull { !it.muted }?.track
                    var device: String? = null
                    if (toDevice) device = DownloadService.saveToDevice(ctx, stored, name, false).toString()
                    val track = Track(
                        id = id, title = name, artist = "Mezcla", uri = Uri.fromFile(stored).toString(),
                        coverUri = first?.coverUri?.takeIf { it.startsWith("http") }, durationMs = Mixer.totalMs(),
                        deviceUri = device, category = kind.name,
                    )
                    if (toLibrary || share) Library.add(track) else stored.delete()
                    if (share) withContext(Dispatchers.Main) { com.tony.aura.ui.components.shareTrack(ctx, track) }
                    buildList {
                        if (toLibrary) add("guardada en Ember")
                        if (toDevice) add("en Música/Ember")
                    }.joinToString(" y ").ifBlank { "lista para compartir" }.let { "Mezcla $it" }
                } catch (e: Throwable) { "No se pudo mezclar: ${e.message}" }
            }
            exporting = false
            ui.toast(msg)
        }
    }
}

@Composable
private fun LayerCard(l: Layer, index: Int) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(20.dp)).background(Aura.Surface)
            .border(1.dp, Aura.Border, RoundedCornerShape(20.dp)).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(l.track.coverUri, Modifier.size(44.dp), RoundedCornerShape(12.dp), l.track.isVideo, l.track.title)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("${index + 1}. ${l.track.title}", color = if (l.muted) Aura.Slate400 else Color.White, fontFamily = Jakarta,
                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatTime(l.track.durationMs) + if (l.loop) " · en bucle" else "", color = Aura.Slate400, fontFamily = Jakarta, fontSize = 12.sp)
            }
            PlainIcon(if (l.muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, 20.dp, if (l.muted) Aura.Brand400 else Aura.Slate300) {
                Mixer.update(l.key) { it.copy(muted = !it.muted) }
            }
            PlainIcon(Icons.Rounded.Repeat, 20.dp, if (l.loop) Aura.Brand500 else Aura.Slate400) { Mixer.update(l.key) { it.copy(loop = !it.loop) } }
            PlainIcon(Icons.Rounded.Close, 20.dp, Aura.Slate400) { Mixer.remove(l.key) }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Volumen", Modifier.width(72.dp), color = Aura.Slate300, fontFamily = Jakarta, fontSize = 12.sp)
            ThinSeekBar((l.volume / 2f).coerceIn(0f, 1f), { v -> Mixer.update(l.key) { it.copy(volume = v * 2f) } },
                { v -> Mixer.update(l.key) { it.copy(volume = v * 2f) } }, Aura.Brand500, Aura.Slate700, Modifier.weight(1f))
            Text("${(l.volume * 100).toInt()} %", Modifier.width(48.dp).padding(start = 8.dp), color = Aura.Slate400, fontFamily = Jakarta, fontSize = 12.sp)
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Entra en", Modifier.width(72.dp), color = Aura.Slate300, fontFamily = Jakarta, fontSize = 12.sp)
            Step("−10") { Mixer.update(l.key) { it.copy(offsetMs = (it.offsetMs - 10_000).coerceAtLeast(0)) } }
            Step("−1") { Mixer.update(l.key) { it.copy(offsetMs = (it.offsetMs - 1_000).coerceAtLeast(0)) } }
            Text(formatTime(l.offsetMs), Modifier.width(64.dp), color = Color.White, fontFamily = Jakarta, fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Step("+1") { Mixer.update(l.key) { it.copy(offsetMs = it.offsetMs + 1_000) } }
            Step("+10") { Mixer.update(l.key) { it.copy(offsetMs = it.offsetMs + 10_000) } }
        }
    }
}

@Composable
private fun Step(text: String, onClick: () -> Unit) {
    Box(Modifier.padding(horizontal = 3.dp).size(40.dp, 34.dp).clip(RoundedCornerShape(10.dp)).background(Aura.Slate800).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Text(text, color = Aura.Slate200, fontFamily = Jakarta, fontSize = 12.sp) }
}

@Composable
private fun SaveMixDialog(onDismiss: () -> Unit, onSave: (String, Kind, Boolean, Boolean, Boolean) -> Unit) {
    var name by remember { mutableStateOf("Mezcla " + java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale("es")).format(java.util.Date())) }
    var kind by remember { mutableStateOf(Kind.MUSIC) }
    var toLibrary by remember { mutableStateOf(true) }
    var toDevice by remember { mutableStateOf(false) }
    var share by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Aura.Elevated,
        title = { Text("Guardar la mezcla", color = Color.White, fontFamily = Jakarta) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre", fontFamily = Jakarta) }, singleLine = true,
                    colors = auraFieldColors(), shape = RoundedCornerShape(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Kind.MUSIC, Kind.PODCAST, Kind.AUDIOBOOK).forEach { k -> AuraChip(k.label, kind == k, { kind = k }) }
                }
                CheckRow("Añadir a Ember como audio nuevo", toLibrary) { toLibrary = it }
                CheckRow("Exportar a Música/Ember del teléfono", toDevice) { toDevice = it }
                CheckRow("Compartir al terminar", share) { share = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank() && (toLibrary || toDevice || share)) onSave(name.trim(), kind, toLibrary, toDevice, share) }) {
                Text("Mezclar y guardar", color = Aura.Brand400, fontFamily = Jakarta)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Aura.Slate300, fontFamily = Jakarta) } },
    )
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(checked, onChange, colors = androidx.compose.material3.CheckboxDefaults.colors(
            checkedColor = Aura.Brand500, checkmarkColor = Aura.OnAccent, uncheckedColor = Aura.Slate400))
        Text(text, color = Aura.Slate200, fontFamily = Jakarta, fontSize = 14.sp)
    }
}
