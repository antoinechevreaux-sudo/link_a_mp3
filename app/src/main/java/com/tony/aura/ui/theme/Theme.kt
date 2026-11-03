package com.tony.aura.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tony.aura.R

/** Paleta de la maqueta Aura: carmesí sobre azul noche. */
object Aura {
    val Brand50 = Color(0xFFFFF1F2)
    val Brand100 = Color(0xFFFFE4E6)
    val Brand400 = Color(0xFFF87171)
    val Brand500 = Color(0xFFEF4444)
    val Brand600 = Color(0xFFDC2626)
    val Brand700 = Color(0xFFB91C1C)
    val Brand900 = Color(0xFF7F1D1D)
    val Brand950 = Color(0xFF450A0A)
    val Rose400 = Color(0xFFFB7185)
    val Rose600 = Color(0xFFE11D48)
    val Rose700 = Color(0xFFBE123C)
    val Rose200 = Color(0xFFFECDD3)

    val Base = Color(0xFF0B0F19)
    val Surface = Color(0xFF111827)
    val Border = Color(0xFF1F2937)
    val Elevated = Color(0xFF1A2234)
    val Slate950 = Color(0xFF020617)
    val Slate900 = Color(0xFF0F172A)
    val Slate800 = Color(0xFF1E293B)
    val Slate700 = Color(0xFF334155)
    val Slate400 = Color(0xFF94A3B8)
    val Slate300 = Color(0xFFCBD5E1)
    val Slate200 = Color(0xFFE2E8F0)
    val White = Color.White

    val HeroGradient = Brush.linearGradient(
        0f to Brand500, 0.5f to Color(0xFF991B1B), 1f to Base,
    )
    val CardGradient = Brush.linearGradient(listOf(Slate800, Base))
    val RedButton = Brush.linearGradient(listOf(Brand600, Brand500, Rose400))
    val ProgressGradient = Brush.horizontalGradient(listOf(Rose600, Brand500))
    val MeshGlow = Brush.radialGradient(
        colors = listOf(Brand500.copy(alpha = 0.32f), Color.Transparent),
        radius = 900f,
    )
}

val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
    Font(R.font.jakarta_extrabold, FontWeight.Black),
)

private fun style(size: Int, weight: FontWeight, spacing: Float = 0f) =
    TextStyle(fontFamily = Jakarta, fontSize = size.sp, fontWeight = weight, letterSpacing = spacing.sp)

private val AuraTypography = Typography(
    displaySmall = style(30, FontWeight.Black, -0.5f),
    headlineMedium = style(26, FontWeight.Black, -0.5f),
    headlineSmall = style(22, FontWeight.Black, -0.3f),
    titleLarge = style(20, FontWeight.ExtraBold, -0.2f),
    titleMedium = style(16, FontWeight.Bold),
    titleSmall = style(14, FontWeight.Bold),
    bodyLarge = style(16, FontWeight.Medium),
    bodyMedium = style(14, FontWeight.Medium),
    bodySmall = style(12, FontWeight.Medium),
    labelLarge = style(14, FontWeight.Bold),
    labelMedium = style(12, FontWeight.Bold),
    labelSmall = style(10, FontWeight.ExtraBold, 1.5f),
)

@Composable
fun AuraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Aura.Brand500,
            onPrimary = Aura.White,
            secondary = Aura.Rose400,
            background = Aura.Base,
            onBackground = Aura.White,
            surface = Aura.Surface,
            onSurface = Aura.White,
            surfaceVariant = Aura.Elevated,
            onSurfaceVariant = Aura.Slate400,
            outline = Aura.Border,
            error = Aura.Brand400,
        ),
        typography = AuraTypography,
        content = content,
    )
}
