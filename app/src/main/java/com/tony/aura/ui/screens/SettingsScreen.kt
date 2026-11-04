package com.tony.aura.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tony.aura.Settings
import com.tony.aura.ui.LocalUi
import com.tony.aura.ui.components.AppLogo
import com.tony.aura.ui.components.AuraChip
import com.tony.aura.ui.components.GhostButton
import com.tony.aura.ui.theme.Aura
import com.tony.aura.ui.theme.Jakarta

@Composable
fun SettingsScreen() {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val ed = DownloadEditor
    LaunchedEffect(Unit) { if (ed.engineVersion == null) ed.loadVersion(ctx) }
    val version = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Throwable) { "?" } }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 110.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                PlainIcon(Icons.AutoMirrored.Rounded.ArrowBack, 24.dp, Color.White) { ui.settingsOpen = false }
                Text("Ajustes", Modifier.padding(start = 8.dp), color = Color.White, fontFamily = Jakarta, fontSize = 24.sp)
            }
        }
        item { Section("Texto") }
        item {
            Text("Tamaño de letra", color = Color.White, fontFamily = Jakarta, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Settings.FONT_SIZES) { (v, label) -> AuraChip(label, Settings.fontScale == v, { Settings.changeFontScale(v) }) }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(18.dp)).background(Aura.Surface).padding(16.dp)) {
                Column {
                    Text("Weekend Check-In: Get Grounded", color = Color.White, fontFamily = Jakarta, fontSize = 17.sp)
                    Text("Así se verán los textos de la app", color = Aura.Slate400, fontFamily = Jakarta, fontSize = 13.sp)
                }
            }
        }
        item { Section("Vídeo") }
        item {
            ToggleSetting("Abrir vídeos a pantalla completa", "Si lo desactivas, empiezan en pequeño dentro del reproductor",
                Settings.videoFullscreen) { Settings.changeVideoFullscreen(it) }
        }
        item {
            ToggleSetting("Ventana flotante al salir", "Al ir a otra app durante un vídeo, sigue en una ventana pequeña",
                Settings.autoPip) { Settings.changeAutoPip(it) }
        }
        item { Section("Descargas") }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Motor de descargas", color = Color.White, fontFamily = Jakarta, fontSize = 15.sp)
                    Text("yt-dlp ${ed.engineVersion ?: "…"}", color = Aura.Slate400, fontFamily = Jakarta, fontSize = 12.sp)
                }
                GhostButton(if (ed.updating) "Actualizando…" else "Actualizar", { ed.updateEngine(ctx) { ui.toast(it) } }, icon = Icons.Rounded.Refresh)
            }
        }
        item { Section("Acerca de") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppLogo(Modifier.size(48.dp))
                Column(Modifier.padding(start = 14.dp)) {
                    Text("Ember", color = Color.White, fontFamily = Jakarta, fontSize = 16.sp)
                    Text("Versión $version", color = Aura.Slate400, fontFamily = Jakarta, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), Modifier.padding(top = 26.dp, bottom = 12.dp), color = Aura.Brand500, fontFamily = Jakarta,
        fontSize = 12.sp, letterSpacing = 1.sp)
}

@Composable
private fun ToggleSetting(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Color.White, fontFamily = Jakarta, fontSize = 15.sp)
            Text(sub, color = Aura.Slate400, fontFamily = Jakarta, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(
            checkedThumbColor = Aura.OnAccent, checkedTrackColor = Aura.Brand500, checkedBorderColor = Aura.Brand500,
            uncheckedThumbColor = Aura.Slate400, uncheckedTrackColor = Aura.Slate900, uncheckedBorderColor = Aura.Border,
        ))
    }
}
