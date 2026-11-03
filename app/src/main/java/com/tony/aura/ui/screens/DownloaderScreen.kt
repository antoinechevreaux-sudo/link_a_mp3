@file:OptIn(ExperimentalMaterial3Api::class)

package com.tony.aura.ui.screens

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tony.aura.data.Kind
import com.tony.aura.data.Library
import com.tony.aura.download.DlJob
import com.tony.aura.download.DlState
import com.tony.aura.download.DownloadService
import com.tony.aura.download.DownloadSpec
import com.tony.aura.download.Downloads
import com.tony.aura.download.Engine
import com.tony.aura.download.Format
import com.tony.aura.ui.LocalUi
import com.tony.aura.ui.components.AuraCard
import com.tony.aura.ui.components.AuraChip
import com.tony.aura.ui.components.Cover
import com.tony.aura.ui.components.GhostButton
import com.tony.aura.ui.components.Kicker
import com.tony.aura.ui.components.PrimaryButton
import com.tony.aura.ui.components.ScreenHeader
import com.tony.aura.ui.components.SectionTitle
import com.tony.aura.ui.components.TextInputDialog
import com.tony.aura.ui.components.auraFieldColors
import com.tony.aura.ui.components.formatTime
import com.tony.aura.ui.theme.Aura
import com.tony.aura.ui.theme.Jakarta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Estado del editor; vive fuera de la pantalla para no perderse al cambiar de pestaña. */
object DownloadEditor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    var url by mutableStateOf("")
    var analyzing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var info by mutableStateOf<Engine.Info?>(null)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var format by mutableStateOf(Format.MP3_HIGH)
    var kind by mutableStateOf(Kind.MUSIC)
    var trim by mutableStateOf(false)
    var start by mutableStateOf(0f)
    var end by mutableStateOf(0f)
    var addToLibrary by mutableStateOf(true)
    var playlistId by mutableStateOf<String?>(null)
    var saveToDevice by mutableStateOf(false)
    var engineVersion by mutableStateOf<String?>(null)
    var updating by mutableStateOf(false)

    fun analyze(ctx: Context) {
        val u = url.trim().let { if (it.isNotEmpty() && !it.startsWith("http")) "https://$it" else it }
        if (u.isEmpty() || analyzing) return
        url = u
        analyzing = true; error = null; info = null
        scope.launch {
            try {
                val i = withContext(Dispatchers.IO) { Engine.info(ctx.applicationContext, u) }
                info = i
                title = i.title
                artist = i.artist
                trim = false
                start = 0f
                end = i.durationSec.toFloat().coerceAtLeast(0f)
                if (!i.hasVideo && format.isVideo) format = Format.MP3_HIGH
                kind = Kind.guess(i.title, i.durationSec, false)
            } catch (e: Throwable) {
                error = Engine.friendlyError(e)
            } finally {
                analyzing = false
            }
        }
    }

    fun clear() { url = ""; info = null; error = null; trim = false }

    fun spec(): DownloadSpec? {
        val i = info ?: return null
        val dur = i.durationSec
        val s = if (trim && dur > 0) start.toInt().takeIf { it > 0 } else null
        val e = if (trim && dur > 0) end.toInt().takeIf { it < dur } else null
        return DownloadSpec(
            url = i.url, title = title.trim().ifBlank { i.title }, artist = artist.trim(), format = format,
            startSec = s, endSec = e, addToLibrary = addToLibrary,
            playlistId = if (addToLibrary) playlistId else null, saveToDevice = saveToDevice, thumbnail = i.thumbnail,
            kind = if (format.isVideo) Kind.VIDEO else kind,
        )
    }

    fun loadVersion(ctx: Context) {
        scope.launch {
            engineVersion = withContext(Dispatchers.IO) {
                try { Engine.ensureInit(ctx.applicationContext); Engine.version(ctx.applicationContext) } catch (_: Throwable) { null }
            }
        }
    }

    fun updateEngine(ctx: Context, onDone: (String) -> Unit) {
        if (updating) return
        updating = true
        scope.launch {
            val msg = withContext(Dispatchers.IO) {
                try { if (Engine.update(ctx.applicationContext)) "Motor actualizado" else "El motor ya estaba al día" }
                catch (e: Throwable) { "No se pudo actualizar: ${e.message ?: "sin conexión"}" }
            }
            updating = false
            loadVersion(ctx)
            onDone(msg)
        }
    }
}

@Composable
fun DownloaderScreen() {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val ed = DownloadEditor
    val jobs by Downloads.jobs.collectAsState()

    LaunchedEffect(ui.sharedUrl) {
        ui.sharedUrl?.let { ed.url = it; ui.sharedUrl = null; ed.analyze(ctx) }
    }
    LaunchedEffect(Unit) { if (ed.engineVersion == null) ed.loadVersion(ctx) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp)) {
        item { ScreenHeader("Descargador", "Descargar") }
        item {
            AuraCard {
                Column {
                    Kicker("Enlace")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = ed.url, onValueChange = { ed.url = it; ed.error = null }, singleLine = true,
                        placeholder = { Text("https://youtube.com/…", fontFamily = Jakarta) },
                        leadingIcon = { Icon(Icons.Rounded.Link, null, tint = Aura.Brand500) },
                        trailingIcon = {
                            Row {
                                if (ed.url.isNotEmpty()) Icon(Icons.Rounded.Close, "Borrar", tint = Aura.Slate400,
                                    modifier = Modifier.clip(CircleShape).clickable { ed.clear() }.padding(8.dp))
                                Icon(Icons.Rounded.ContentPaste, "Pegar", tint = Aura.Slate200,
                                    modifier = Modifier.clip(CircleShape).clickable {
                                        paste(ctx)?.let { ed.url = it; focus.clearFocus(); ed.analyze(ctx) }
                                            ?: ui.toast("No hay ningún enlace en el portapapeles")
                                    }.padding(8.dp))
                            }
                        },
                        colors = auraFieldColors(), shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { focus.clearFocus(); ed.analyze(ctx) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        if (ed.analyzing) "Leyendo el enlace…" else "Analizar enlace",
                        { focus.clearFocus(); ed.analyze(ctx) }, Modifier.fillMaxWidth(), Icons.Rounded.Search,
                        enabled = !ed.analyzing && ed.url.isNotBlank(),
                    )
                    Text(
                        "YouTube, SoundCloud, Instagram, TikTok, X, Vimeo, Bandcamp, podcasts y cientos más.",
                        Modifier.padding(top = 10.dp), color = Aura.Slate400, fontFamily = Jakarta, fontSize = 11.sp,
                    )
                }
            }
        }
        if (ed.analyzing) item {
            Row(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Aura.Brand500, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("Leyendo título, duración y formatos…", color = Aura.Slate300, fontFamily = Jakarta, fontSize = 13.sp)
            }
        }
        ed.error?.let { err -> item {
            Text(err, Modifier.padding(vertical = 14.dp), color = Aura.Brand400, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        } }
        ed.info?.let { info -> item { Column { Spacer(Modifier.height(14.dp)); Editor(info) } } }

        if (jobs.isNotEmpty()) {
            item { Column { Spacer(Modifier.height(18.dp)); SectionTitle("Descargas", "Limpiar") { Downloads.clearFinished() } } }
            items(jobs, key = { it.id }) { JobCard(it) }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Motor: yt-dlp ${ed.engineVersion ?: "…"}", Modifier.weight(1f), color = Aura.Slate400, fontFamily = Jakarta, fontSize = 11.sp)
                GhostButton(if (ed.updating) "Actualizando…" else "Actualizar motor", { ed.updateEngine(ctx) { ui.toast(it) } },
                    icon = Icons.Rounded.Refresh)
            }
        }
    }
}

private fun paste(ctx: Context): String? {
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
    return text?.let { Regex("https?://\\S+").find(it)?.value }
}

@Composable
private fun Editor(info: Engine.Info) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val ed = DownloadEditor
    val lib by Library.state.collectAsState()
    var newPlaylist by remember { mutableStateOf(false) }
    val dur = info.durationSec

    AuraCard(padding = PaddingValues(0.dp)) {
        Column {
            // Miniatura
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                Cover(info.thumbnail, Modifier.fillMaxSize(), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), seed = info.title)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Aura.Surface))))
                if (info.site.isNotBlank()) Badge(info.site, Modifier.align(Alignment.TopStart).padding(12.dp))
                if (dur > 0) Badge(formatTime(dur * 1000L), Modifier.align(Alignment.TopEnd).padding(12.dp))
            }
            Column(Modifier.padding(16.dp)) {
                Kicker("Editar antes de descargar")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(ed.title, { ed.title = it }, label = { Text("Título", fontFamily = Jakarta) },
                    colors = auraFieldColors(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(), maxLines = 2)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(ed.artist, { ed.artist = it }, label = { Text("Artista", fontFamily = Jakarta) },
                    colors = auraFieldColors(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(), singleLine = true)

                Label("Formato")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Format.entries.filter { info.hasVideo || !it.isVideo }) { f ->
                        AuraChip(f.label, ed.format == f, { ed.format = f })
                    }
                }

                if (!ed.format.isVideo) {
                    Label("Tipo")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(Kind.MUSIC, Kind.PODCAST, Kind.AUDIOBOOK)) { k -> AuraChip(k.label, ed.kind == k, { ed.kind = k }) }
                    }
                }

                // Recorte
                if (dur > 0) {
                    ToggleRow(Icons.Rounded.ContentCut, "Recortar el clip", "Descarga solo el fragmento que elijas", ed.trim) {
                        ed.trim = it
                        if (it && ed.end <= ed.start) { ed.start = 0f; ed.end = dur.toFloat() }
                    }
                    if (ed.trim) {
                        RangeSlider(
                            value = ed.start..ed.end,
                            onValueChange = { r -> ed.start = r.start; ed.end = r.endInclusive },
                            valueRange = 0f..dur.toFloat(),
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Aura.Brand500,
                                inactiveTrackColor = Aura.Slate800),
                        )
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TimeAdjust("Inicio", ed.start.toInt(),
                                { ed.start = (ed.start - 1).coerceAtLeast(0f) },
                                { ed.start = (ed.start + 1).coerceAtMost(ed.end - 1) })
                            Spacer(Modifier.weight(1f))
                            TimeAdjust("Fin", ed.end.toInt(),
                                { ed.end = (ed.end - 1).coerceAtLeast(ed.start + 1) },
                                { ed.end = (ed.end + 1).coerceAtMost(dur.toFloat()) })
                        }
                        Text(
                            "Duración del clip: ${formatTime(((ed.end - ed.start).toLong()) * 1000)}",
                            Modifier.padding(top = 6.dp), color = Aura.Brand400, fontFamily = Jakarta,
                            fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        )
                    }
                }

                // Destino
                Label("Destino")
                ToggleRow(Icons.Rounded.LibraryMusic, "Añadir a Ember", "Para escucharlo aquí y en tus playlists", ed.addToLibrary) { ed.addToLibrary = it }
                if (ed.addToLibrary) {
                    LazyRow(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { AuraChip("Sin playlist", ed.playlistId == null, { ed.playlistId = null }) }
                        items(lib.playlists, key = { it.id }) { p -> AuraChip(p.name, ed.playlistId == p.id, { ed.playlistId = p.id }) }
                        item { AuraChip("Nueva", false, { newPlaylist = true }, Icons.Rounded.Add) }
                    }
                }
                ToggleRow(
                    Icons.Rounded.PhoneAndroid, "Guardar en el dispositivo",
                    if (ed.format.isVideo) "En Películas/Ember, visible en la galería" else "En Música/Ember, visible en otras apps",
                    ed.saveToDevice,
                ) { ed.saveToDevice = it }

                Spacer(Modifier.height(14.dp))
                val ok = ed.addToLibrary || ed.saveToDevice
                PrimaryButton(
                    if (ok) "Descargar" else "Elige al menos un destino",
                    {
                        ed.spec()?.let { s ->
                            DownloadService.enqueue(ctx, s)
                            ui.toast("Descargando «${s.title}»")
                            ed.clear()
                        }
                    },
                    Modifier.fillMaxWidth(), Icons.Rounded.Download, enabled = ok,
                )
            }
        }
    }

    if (newPlaylist) TextInputDialog("Nueva playlist", "Nombre", "", "Crear", onDismiss = { newPlaylist = false }) { name ->
        ed.playlistId = Library.createPlaylist(name).id
        newPlaylist = false
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier) {
    Text(text, modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 10.dp, vertical = 4.dp),
        color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 11.sp)
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), Modifier.padding(top = 18.dp, bottom = 10.dp), color = Aura.Slate300, fontFamily = Jakarta,
        fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.2.sp)
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (checked) Aura.Brand500.copy(alpha = 0.15f) else Aura.Slate900),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (checked) Aura.Brand400 else Aura.Slate400, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = Aura.Slate400, fontFamily = Jakarta, fontSize = 11.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = Aura.Brand500, checkedBorderColor = Aura.Brand500,
                uncheckedThumbColor = Aura.Slate400, uncheckedTrackColor = Aura.Slate900, uncheckedBorderColor = Aura.Border,
            ),
        )
    }
}

@Composable
private fun TimeAdjust(label: String, seconds: Int, minus: () -> Unit, plus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SmallStep("−1", minus)
        Column(Modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label.uppercase(), color = Aura.Slate400, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, letterSpacing = 1.sp)
            Text(formatTime(seconds * 1000L), color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
        SmallStep("+1", plus)
    }
}

@Composable
private fun SmallStep(text: String, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(Aura.Slate900).border(1.dp, Aura.Border, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(text, color = Aura.Slate200, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

@Composable
private fun JobCard(job: DlJob) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val lib by Library.state.collectAsState()
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(shape).background(Aura.Surface.copy(alpha = 0.85f))
            .border(1.dp, if (job.state == DlState.RUNNING) Aura.Brand500.copy(alpha = 0.3f) else Aura.Border, shape).padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(job.spec.thumbnail, Modifier.size(52.dp), RoundedCornerShape(12.dp), job.spec.format.isVideo, job.spec.title)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(job.spec.title, color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val clip = if (job.spec.startSec != null || job.spec.endSec != null)
                    " · ✂ ${formatTime((job.spec.startSec ?: 0) * 1000L)}–${job.spec.endSec?.let { formatTime(it * 1000L) } ?: "fin"}" else ""
                Text(job.spec.format.label + clip, color = Aura.Slate400, fontFamily = Jakarta, fontSize = 11.sp, maxLines = 1)
                Text(job.status, color = when (job.state) {
                    DlState.DONE -> Color(0xFF4ADE80); DlState.ERROR -> Aura.Brand400; else -> Aura.Slate300
                }, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            when (job.state) {
                DlState.QUEUED, DlState.RUNNING -> Icon(Icons.Rounded.Close, "Cancelar", tint = Aura.Slate300,
                    modifier = Modifier.clip(CircleShape).clickable { Downloads.cancel(job.id) }.padding(8.dp))
                DlState.DONE -> job.trackId?.let { lib.track(it) }?.let { t ->
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Aura.Brand500).clickable { ui.play(listOf(t), 0) },
                        contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, "Reproducir", tint = Color.White) }
                }
                else -> Icon(Icons.Rounded.Refresh, "Reintentar", tint = Aura.Slate200,
                    modifier = Modifier.clip(CircleShape).clickable { DownloadService.enqueue(ctx, job.spec) }.padding(8.dp))
            }
        }
        if (!job.finished) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Aura.Slate800)) {
                if (job.progress >= 0f) Box(Modifier.fillMaxWidth((job.progress / 100f).coerceIn(0.02f, 1f)).height(5.dp)
                    .clip(CircleShape).background(Aura.ProgressGradient))
                else androidx.compose.material3.LinearProgressIndicator(
                    Modifier.fillMaxWidth().height(5.dp), color = Aura.Brand500, trackColor = Aura.Slate800,
                )
            }
        }
    }
}
