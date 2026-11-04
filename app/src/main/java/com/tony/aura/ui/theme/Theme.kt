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
    // Paleta «Luna azul»: azul noche, lavanda y crema. Los nombres antiguos se conservan
    // para no tocar cada pantalla; ahora apuntan a los tonos nuevos.
    val Brand50 = Color(0xFFF3F2FE)
    val Brand100 = Color(0xFFE6E5FC)
    val Brand400 = Color(0xFFCFCCFA)   // texto resaltado
    val Brand500 = Color(0xFFB9B6F7)   // acento lavanda
    val Brand600 = Color(0xFF9C98EE)
    val Brand700 = Color(0xFF5E5BB8)
    val Brand900 = Color(0xFF2E2C66)
    val Brand950 = Color(0xFF1C1A45)
    val Rose400 = Color(0xFF8FA6F0)
    val Rose600 = Color(0xFF6F7BE0)
    val Rose700 = Color(0xFF3A3F94)
    val Rose200 = Color(0xFFF2E8D4)    // crema
    val OnAccent = Color(0xFF1C1A45)

    val Base = Color(0xFF0D1220)
    val Surface = Color(0xFF151C2E)
    val Border = Color(0xFF1F2740)
    val Elevated = Color(0xFF1D2640)
    val Slate950 = Color(0xFF0A0F1A)
    val Slate900 = Color(0xFF111829)
    val Slate800 = Color(0xFF222B45)
    val Slate700 = Color(0xFF2A3350)
    val Slate400 = Color(0xFF8D96AE)
    val Slate300 = Color(0xFFB3BACC)
    val Slate200 = Color(0xFFDCE0EC)
    val White = Color(0xFFE9ECF5)

    val HeroGradient = Brush.linearGradient(listOf(Color(0xFF2E2C66), Color(0xFF151C2E)))
    val CardGradient = Brush.linearGradient(listOf(Slate800, Base))
    val RedButton = Brush.linearGradient(listOf(Color(0xFFB9B6F7), Color(0xFFA9B4F4)))
    val ProgressGradient = Brush.horizontalGradient(listOf(Color(0xFF8FA6F0), Brand500))
    val MeshGlow = Brush.radialGradient(
        colors = listOf(Brand500.copy(alpha = 0.07f), Color.Transparent),
        radius = 900f,
    )
}

val Jakarta = FontFamily(
    Font(R.font.outfit_light, FontWeight.Light),
    Font(R.font.outfit_light, FontWeight.Normal),
    Font(R.font.outfit_regular, FontWeight.Medium),
    Font(R.font.outfit_medium, FontWeight.SemiBold),
    Font(R.font.outfit_medium, FontWeight.Bold),
    Font(R.font.outfit_semibold, FontWeight.ExtraBold),
    Font(R.font.outfit_semibold, FontWeight.Black),
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
            onPrimary = Aura.OnAccent,
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
