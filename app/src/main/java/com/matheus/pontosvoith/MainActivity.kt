package com.matheus.pontosvoith

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matheus.pontosvoith.ui.theme.PontosVoithTheme
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var dark by remember { mutableStateOf(false) }
            PontosVoithTheme(darkTheme = dark) {
                TelaInicialV1(dark = dark, onToggleTema = { dark = !dark })
            }
        }
    }
}

/**
 * ENTREGA 1 – Tela provisória.
 * Objetivo: validar build no GitHub Actions, ícone, tema claro/escuro e animação.
 * O registro de ponto real entra na Entrega 2.
 */
@Composable
fun TelaInicialV1(dark: Boolean, onToggleTema: () -> Unit) {
    val br = Locale("pt", "BR")
    var agora by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) { agora = LocalDateTime.now(); delay(1000) }
    }

    val pulso = rememberInfiniteTransition(label = "pulso")
    val escala by pulso.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "escala"
    )

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Pontos Voith", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            Text(agora.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                fontSize = 52.sp, fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onBackground)
            Text(agora.format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy", br))
                .replaceFirstChar { it.uppercase() },
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            Spacer(Modifier.height(40.dp))
            Box(
                Modifier.size(170.dp).scale(escala)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("V1", color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(32.dp))
            Text("Build de validação – Entrega 1",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onToggleTema) {
                Text(if (dark) "Tema claro" else "Tema escuro")
            }
        }
    }
}
