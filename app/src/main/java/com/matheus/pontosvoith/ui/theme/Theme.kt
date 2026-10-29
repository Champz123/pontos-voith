package com.matheus.pontosvoith.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val VoithBlue = Color(0xFF094AFE)
val VoithBlueLight = Color(0xFF4D7DFF)
val VoithNavy = Color(0xFF16307A)
val CorOk = Color(0xFF1FA463)
val CorAviso = Color(0xFFE0A100)
val CorErro = Color(0xFFD93025)

private val LightColors = lightColorScheme(
    primary = VoithBlue, onPrimary = Color.White,
    background = Color.White, onBackground = Color(0xFF0B1B3F),
    surface = Color(0xFFF2F5FF), onSurface = Color(0xFF0B1B3F)
)
private val DarkColors = darkColorScheme(
    primary = VoithBlueLight, onPrimary = Color.White,
    background = Color(0xFF05070D), onBackground = Color(0xFFE6ECFF),
    surface = Color(0xFF0E1426), onSurface = Color(0xFFE6ECFF)
)

@Composable
fun PontosVoithTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
