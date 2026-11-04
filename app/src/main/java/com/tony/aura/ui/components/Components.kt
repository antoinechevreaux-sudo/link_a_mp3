package com.tony.aura.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.tony.aura.data.Track
import com.tony.aura.ui.theme.Aura
import com.tony.aura.ui.theme.Jakarta

/** Resplandor rojo de la maqueta (shadow-red-glow). */
fun Modifier.redGlow(shape: Shape, elevation: Dp = 18.dp, alpha: Float = 0.55f) =
    this.shadow(elevation * 0.6f, shape, clip = false, ambientColor = Aura.Brand500.copy(alpha = alpha * 0.35f), spotColor = Aura.Brand500.copy(alpha = alpha * 0.35f))

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Portada con degradado de respaldo cuando no hay imagen. */
@Composable
fun Cover(
    uri: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    isVideo: Boolean = false,
    seed: String = "",
) {
    Box(modifier.clip(shape)) {
        val fallback: @Composable () -> Unit = {
            val hue = (seed.hashCode() and 0x7fffffff) % 3
            val brush = when (hue) {
                0 -> Brush.linearGradient(listOf(Aura.Brand600, Aura.Rose700))
                1 -> Brush.linearGradient(listOf(Aura.Brand900, Aura.Slate800))
                else -> Brush.linearGradient(listOf(Aura.Rose700, Aura.Slate900))
            }
            Box(Modifier.fillMaxSize().background(brush), contentAlignment = Alignment.Center) {
                Icon(
                    if (isVideo) Icons.Rounded.Movie else Icons.Rounded.MusicNote, null,
                    tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(22.dp),
                )
            }
        }
        if (uri.isNullOrBlank()) fallback()
        else SubcomposeAsyncImage(
            model = uri, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { fallback() }, error = { fallback() },
        )
    }
}

@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = Aura.Brand500) {
    Text(
        text.uppercase(), modifier = modifier, color = color,
        fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 2.sp,
    )
}

@Composable
fun ScreenHeader(kicker: String, title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Kicker(kicker)
            Text(title, color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.Black, fontSize = 26.sp, letterSpacing = (-0.5).sp)
        }
        trailing()
    }
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        if (action != null) Text(
            action, color = Aura.Brand500, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 12.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp),
        )
    }
}

@Composable
fun AuraChip(label: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    val shape = CircleShape
    Row(
        Modifier
            .clip(shape)
            .background(if (selected) Aura.Brand500 else Color.Transparent)
            .border(1.dp, if (selected) Aura.Brand500 else Aura.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = if (selected) Aura.OnAccent else Aura.Slate300, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(6.dp)) }
        Text(label, color = if (selected) Aura.OnAccent else Aura.Slate400, fontFamily = Jakarta,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, fontSize = 13.sp)
    }
}

/** Botón circular (play rojo con resplandor, o secundario oscuro). */
@Composable
fun RoundButton(
    icon: ImageVector,
    onClick: () -> Unit,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    primary: Boolean = false,
    tint: Color = Color.White,
    contentDescription: String? = null,
    enabled: Boolean = true,
) {
    val shape = CircleShape
    Box(
        Modifier
            .size(size)
            .then(if (primary) Modifier.redGlow(shape, 20.dp) else Modifier)
            .clip(shape)
            .background(if (primary) Aura.Brand500 else Aura.Surface)
            .then(if (primary) Modifier else Modifier.border(1.dp, Aura.Border, shape))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val t = if (primary && tint == Color.White) Aura.OnAccent else tint
        Icon(icon, contentDescription, tint = if (enabled) t else t.copy(alpha = 0.35f), modifier = Modifier.size(iconSize))
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .height(54.dp)
            .then(if (enabled) Modifier.redGlow(shape, 16.dp) else Modifier)
            .clip(shape)
            .background(if (enabled) Aura.RedButton else Brush.linearGradient(listOf(Aura.Slate800, Aura.Slate800)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = if (enabled) Aura.OnAccent else Aura.Slate400, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = if (enabled) Aura.OnAccent else Aura.Slate400, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.height(44.dp).clip(shape).background(Aura.Surface).border(1.dp, Aura.Border, shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = Aura.Slate200, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = Aura.Slate200, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** Tarjeta oscura con borde sutil. */
@Composable
fun AuraCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(modifier.clip(shape).background(Aura.Surface.copy(alpha = 0.85f)).border(1.dp, Aura.Border, shape).padding(padding)) {
        content()
    }
}

@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    onMore: () -> Unit,
    playing: Boolean = false,
    index: Int? = null,
    subtitle: String? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(if (playing) Aura.Brand500.copy(alpha = 0.10f) else Aura.Surface.copy(alpha = 0.6f))
            .border(1.dp, if (playing) Aura.Brand500.copy(alpha = 0.35f) else Aura.Border.copy(alpha = 0.8f), shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Text("$index", Modifier.width(22.dp), color = Aura.Brand500, fontFamily = Jakarta, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        Box {
            Cover(track.coverUri, Modifier.size(46.dp), RoundedCornerShape(10.dp), track.isVideo, track.title)
            if (playing) Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.GraphicEq, null, tint = Aura.Brand400, modifier = Modifier.size(22.dp)) }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, color = if (playing) Aura.Brand400 else Color.White, fontFamily = Jakarta,
                fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.isVideo) {
                    Text("VÍDEO", color = Aura.Brand400, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Aura.Brand500.copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 1.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(subtitle ?: (track.artist + if (track.durationMs > 0) " · ${formatTime(track.durationMs)}" else ""),
                    color = Aura.Slate400, fontFamily = Jakarta, fontWeight = FontWeight.Medium, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(
            Icons.Rounded.MoreVert, "Opciones", tint = Aura.Slate400,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onMore).padding(6.dp).size(20.dp),
        )
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).redGlow(CircleShape, 20.dp).clip(CircleShape).background(Aura.RedButton),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(title, color = Color.White, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Spacer(Modifier.height(6.dp))
        Text(text, color = Aura.Slate400, fontFamily = Jakarta, fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            PrimaryButton(action, onAction)
        }
    }
}

@Composable
fun AuraDivider() { Box(Modifier.fillMaxWidth().height(1.dp).background(Aura.Border)) }

val BorderSubtle = BorderStroke(1.dp, Aura.Border)

/** Icono de la app: orbe con auriculares. */
@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        androidx.compose.ui.res.painterResource(com.tony.aura.R.drawable.logo_app), "Ember",
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
    )
}

/** El orbe de la app; «respira» despacio mientras suena algo. */
@Composable
fun Orb(modifier: Modifier = Modifier, breathing: Boolean = false) {
    val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "orbe")
    val scale by tr.animateFloat(
        1f, 1.035f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(4200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ), label = "escala",
    )
    val turn by tr.animateFloat(
        0f, 360f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(60000, easing = androidx.compose.animation.core.LinearEasing),
        ), label = "giro",
    )
    androidx.compose.foundation.Image(
        androidx.compose.ui.res.painterResource(com.tony.aura.R.drawable.orbe), null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(CircleShape).graphicsLayer {
            val s = if (breathing) scale else 1f
            scaleX = s; scaleY = s
            rotationZ = if (breathing) turn else 0f
        },
    )
}
