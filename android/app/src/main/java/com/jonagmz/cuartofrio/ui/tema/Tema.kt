package com.jonagmz.cuartofrio.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Claro = lightColorScheme(
    primary = Color(0xFF0B5CAD), onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E3FF), onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF00838F), onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8F0F4), onSecondaryContainer = Color(0xFF002023),
    tertiary = Color(0xFFB35C00), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC2), onTertiaryContainer = Color(0xFF2E1500),
    error = Color(0xFFBA1A1A), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FAFE), surface = Color(0xFFF6FAFE),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F4F9),
    surfaceContainer = Color(0xFFEAEEF4), surfaceContainerHigh = Color(0xFFE4E8EE),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFFA7C8FF), onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF00468A), onPrimaryContainer = Color(0xFFD5E3FF),
    secondary = Color(0xFF7FD6E0), onSecondary = Color(0xFF00363C),
    secondaryContainer = Color(0xFF004F57), onSecondaryContainer = Color(0xFFC8F0F4),
    tertiary = Color(0xFFFFB77C), onTertiary = Color(0xFF4C2700),
    tertiaryContainer = Color(0xFF6C3A00), onTertiaryContainer = Color(0xFFFFDCC2),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1418), surface = Color(0xFF0F1418),
    surfaceContainerLowest = Color(0xFF0A0F12), surfaceContainerLow = Color(0xFF171C20),
    surfaceContainer = Color(0xFF1B2024), surfaceContainerHigh = Color(0xFF262B2F),
)

private val Tipografia = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Light, fontSize = 76.sp, letterSpacing = (-2).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Colores para estados del equipo, iguales en claro y oscuro salvo el tono. */
object ColoresEstado {
    val frio = Color(0xFF1E88E5)
    val ok = Color(0xFF2E7D32)
    val aviso = Color(0xFFEF6C00)
    val alarma = Color(0xFFD32F2F)
    val humedad = Color(0xFF00897B)
    val potencia = Color(0xFF7E57C2)
}

val NumerosTabulares = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun TemaCuartoFrio(oscuro: Boolean = isSystemInDarkTheme(), contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (oscuro) Oscuro else Claro, typography = Tipografia, content = contenido)
}
