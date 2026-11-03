package com.tony.aura.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tony.aura.download.DlState
import com.tony.aura.download.Downloads
import com.tony.aura.player.PlayerConnection
import com.tony.aura.ui.components.AddToPlaylistSheet
import com.tony.aura.ui.components.Cover
import com.tony.aura.ui.components.EditTrackDialog
import com.tony.aura.ui.components.TrackOptionsSheet
import com.tony.aura.ui.components.redGlow
import com.tony.aura.ui.screens.DownloaderScreen
import com.tony.aura.ui.screens.ExploreScreen
import com.tony.aura.ui.screens.HomeScreen
import com.tony.aura.ui.screens.LibraryScreen
import com.tony.aura.ui.screens.NowPlayingScreen
import com.tony.aura.ui.screens.PlaylistScreen
import com.tony.aura.ui.screens.VideoScreen
import com.tony.aura.ui.theme.Aura
import com.tony.aura.ui.theme.Jakarta
import kotlinx.coroutines.delay

@Composable
fun AuraRoot() {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val player by PlayerConnection.ui.collectAsState()

    // Posición del reproductor mientras suena
    LaunchedEffect(player.isPlaying) {
        while (player.isPlaying) { PlayerConnection.tick(); delay(500) }
    }
    LaunchedEffect(ui.message) {
        ui.message?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); ui.message = null }
    }
    LaunchedEffect(player.error) {
        player.error?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }
    }

    BackHandler(enabled = ui.videoFullscreen || ui.openPlaylist != null || ui.tab != Tab.HOME) {
        when {
            ui.videoFullscreen -> ui.videoFullscreen = false
            ui.openPlaylist != null -> ui.openPlaylist = null
            else -> ui.tab = Tab.HOME
        }
    }

    Box(Modifier.fillMaxSize().background(Aura.Base)) {
        // Resplandor carmesí de fondo
        Box(Modifier.fillMaxWidth().height(420.dp).background(Aura.MeshGlow))

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) { ContentArea() }
            BottomNav()
        }

        AnimatedVisibility(
            visible = ui.videoFullscreen && player.current?.isVideo == true,
            enter = fadeIn(), exit = fadeOut(),
        ) { VideoScreen(onClose = { ui.videoFullscreen = false }) }
    }

    ui.optionsFor?.let { t -> TrackOptionsSheet(t, ui.openPlaylist, onDismiss = { ui.optionsFor = null }) }
    ui.addToPlaylistFor?.let { t -> AddToPlaylistSheet(t, onDismiss = { ui.addToPlaylistFor = null }) }
    ui.editTrack?.let { t -> EditTrackDialog(t, onDismiss = { ui.editTrack = null }) }
}


@Composable
private fun ContentArea() {
    val ui = LocalUi.current
    val player by PlayerConnection.ui.collectAsState()
    Box(Modifier.fillMaxSize()) {
                Crossfade(targetState = ui.tab, label = "pestañas") { tab ->
                    Box(Modifier.fillMaxSize().statusBarsPadding()) {
                        when (tab) {
                            Tab.HOME -> HomeScreen()
                            Tab.EXPLORE -> ExploreScreen()
                            Tab.DOWNLOAD -> DownloaderScreen()
                            Tab.LIBRARY -> LibraryScreen()
                            Tab.PLAYER -> NowPlayingScreen()
                        }
                    }
                }
                AnimatedVisibility(
                    visible = ui.openPlaylist != null,
                    enter = slideInHorizontally { it } + fadeIn(),
                    exit = slideOutHorizontally { it } + fadeOut(),
                ) {
                    val id = remember(ui.openPlaylist) { ui.openPlaylist }
                    Box(Modifier.fillMaxSize().background(Aura.Base)) {
                        if (id != null) PlaylistScreen(id)
                    }
                }
                // Mini reproductor
                val showMini = player.current != null && (ui.tab != Tab.PLAYER || ui.openPlaylist != null)
                AnimatedVisibility(
                    visible = showMini,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) { MiniPlayer() }
            }
}

@Composable
private fun MiniPlayer() {
    val ui = LocalUi.current
    val p by PlayerConnection.ui.collectAsState()
    val t = p.current ?: return
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .redGlow(shape, 14.dp, 0.35f)
            .clip(shape)
            .background(Aura.Surface.copy(alpha = 0.96f))
            .border(1.dp, Aura.Brand500.copy(alpha = 0.22f), shape)
            .clickable {
                ui.openPlaylist = null
                if (t.isVideo) ui.videoFullscreen = true else ui.tab = Tab.PLAYER
            },
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(t.coverUri, Modifier.size(42.dp), RoundedCornerShape(10.dp), t.isVideo, t.title)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(t.title, color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.artist, color = Aura.Slate400, fontFamily = Jakarta, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(
                if (p.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Reproducir", tint = Color.White,
                modifier = Modifier.clip(CircleShape).clickable { PlayerConnection.togglePlay() }.padding(8.dp).size(24.dp),
            )
            Icon(
                Icons.Rounded.SkipNext, "Siguiente", tint = Aura.Slate300,
                modifier = Modifier.clip(CircleShape).clickable { PlayerConnection.next() }.padding(8.dp).size(22.dp),
            )
        }
        // Barra de progreso fina
        val frac = if (p.durationMs > 0) (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f) else 0f
        Box(Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp).fillMaxWidth().height(2.dp)) {
            Box(Modifier.fillMaxWidth(frac).height(2.dp).clip(CircleShape).background(Aura.ProgressGradient))
        }
    }
}

@Composable
private fun BottomNav() {
    val ui = LocalUi.current
    val jobs by Downloads.jobs.collectAsState()
    val active = jobs.count { it.state == DlState.RUNNING || it.state == DlState.QUEUED }
    Box(
        Modifier.fillMaxWidth().background(Aura.Base.copy(alpha = 0.96f))
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(62.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(Tab.HOME, Icons.Rounded.Home)
            NavItem(Tab.EXPLORE, Icons.Rounded.Explore)
            // Botón central de descarga
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val selected = ui.tab == Tab.DOWNLOAD && ui.openPlaylist == null
                Box(
                    Modifier.size(46.dp).offset(y = (-4).dp).redGlow(CircleShape, if (selected) 24.dp else 14.dp)
                        .clip(CircleShape).background(Aura.RedButton)
                        .clickable { ui.openPlaylist = null; ui.tab = Tab.DOWNLOAD },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Download, "Descargar", tint = Color.White, modifier = Modifier.size(24.dp))
                }
                if (active > 0) Box(
                    Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = 2.dp).size(18.dp).clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) { Text("$active", color = Aura.Brand600, fontFamily = Jakarta, fontWeight = FontWeight.Black, fontSize = 10.sp) }
            }
            NavItem(Tab.LIBRARY, Icons.Rounded.LibraryMusic)
            NavItem(Tab.PLAYER, Icons.Rounded.Album)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(tab: Tab, icon: ImageVector) {
    val ui = LocalUi.current
    val selected = ui.tab == tab && ui.openPlaylist == null
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                ui.openPlaylist = null; ui.tab = tab
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, tab.label, tint = if (selected) Aura.Brand500 else Aura.Slate400, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(tab.label, color = if (selected) Aura.Brand500 else Aura.Slate400, fontFamily = Jakarta,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = 10.sp)
    }
}
