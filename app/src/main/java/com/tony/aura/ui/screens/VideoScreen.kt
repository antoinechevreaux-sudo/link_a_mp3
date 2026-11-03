package com.tony.aura.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tony.aura.player.PlayerConnection
import com.tony.aura.ui.components.AuraChip
import com.tony.aura.ui.components.RoundButton
import com.tony.aura.ui.theme.Aura
import com.tony.aura.ui.theme.Jakarta
import kotlinx.coroutines.delay

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** Vídeo a pantalla completa, en horizontal, con controles propios. */
@OptIn(UnstableApi::class)
@Composable
fun VideoScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val p by PlayerConnection.ui.collectAsState()
    var controls by remember { mutableStateOf(true) }
    var showSpeeds by remember { mutableStateOf(false) }
    var landscape by remember { mutableStateOf(true) }
    var touch by remember { mutableIntStateOf(0) }
    var flash by remember { mutableStateOf<String?>(null) }

    // Horizontal + barras del sistema ocultas mientras dura
    DisposableEffect(Unit) {
        val act = ctx.findActivity()
        val window = act?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            ctl?.show(WindowInsetsCompat.Type.systemBars())
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(landscape) {
        ctx.findActivity()?.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    // Los controles se ocultan solos a los 3 s
    LaunchedEffect(controls, touch, p.isPlaying) {
        if (controls && p.isPlaying && !showSpeeds) { delay(3000); controls = false }
    }
    LaunchedEffect(flash) { if (flash != null) { delay(700); flash = null } }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controls = !controls; touch++ },
                    onDoubleTap = { o ->
                        if (o.x < size.width / 2) { PlayerConnection.back15(); flash = "−15 s" }
                        else { PlayerConnection.forward15(); flash = "+15 s" }
                    },
                )
            },
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    keepScreenOn = true
                    player = PlayerConnection.controller
                }
            },
            update = { v -> if (v.player !== PlayerConnection.controller) v.player = PlayerConnection.controller },
            onRelease = { v -> v.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        flash?.let {
            Text(it, Modifier.align(Alignment.Center).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 18.dp, vertical = 10.dp),
                color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }

        AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent, Color.Black.copy(alpha = 0.8f)))
            )) {
                // Barra superior
                Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlainIcon(Icons.Rounded.KeyboardArrowDown, 26.dp, Color.White) { onClose() }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(p.current?.title ?: "", color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(p.current?.artist ?: "", color = Aura.Slate300, fontFamily = Jakarta, fontSize = 12.sp, maxLines = 1)
                    }
                    SpeedPill(speedLabel(p.speed), p.speed != 1f) { showSpeeds = !showSpeeds; touch++ }
                    Spacer(Modifier.width(6.dp))
                    PlainIcon(Icons.Rounded.ScreenRotation, 22.dp, Color.White) { landscape = !landscape; touch++ }
                }
                // Controles centrales
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(64.dp)) {
                    SkipButton(false, 52.dp) { PlayerConnection.back15(); touch++ }
                    // Botón blanco como en YouTube
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(Color.White)
                            .clickable { PlayerConnection.togglePlay(); touch++ },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (p.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Reproducir",
                            tint = Color.Black, modifier = Modifier.size(34.dp))
                    }
                    SkipButton(true, 52.dp) { PlayerConnection.forward15(); touch++ }
                }
                // Velocidades y barra de progreso
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    if (showSpeeds) LazyRow(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(PlayerConnection.SPEEDS) { s -> SpeedPill(speedLabel(s), s == p.speed) { PlayerConnection.setSpeed(s); touch++ } }
                    }
                    SeekBar(p, light = true)
                }
            }
        }
    }
}


/** Píldora de velocidad compacta para la pantalla completa. */
@Composable
private fun SpeedPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        Modifier.clip(CircleShape)
            .background(if (selected) Aura.Brand500 else Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 12.sp,
    )
}
