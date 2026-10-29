package com.matheus.pontosvoith.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.matheus.pontosvoith.dados.Batida
import com.matheus.pontosvoith.dados.Repositorio
import com.matheus.pontosvoith.dados.montarTurnos
import java.time.LocalDate
import java.time.YearMonth

enum class Aba(val rotulo: String) { PONTO("Ponto"), DIA("Dia"), MES("Mês"), PAINEL("Painel"), AJUSTES("Ajustes") }

@Suppress("DEPRECATION")
@Composable
fun AppRaiz(escuro: Boolean, onTema: (Boolean) -> Unit) {
    var aba by rememberSaveable { mutableStateOf(Aba.PONTO) }
    var dia by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var mes by rememberSaveable { mutableStateOf(YearMonth.now()) }

    // Fonte única de dados: recarrega quando o banco muda
    val versao by Repositorio.versao.collectAsState()
    var todas by remember { mutableStateOf<List<Batida>>(emptyList()) }
    LaunchedEffect(versao) { todas = Repositorio.todas() }
    val turnos = remember(todas) { montarTurnos(todas) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Aba.entries.forEach { a ->
                    NavigationBarItem(
                        selected = aba == a,
                        onClick = { aba = a },
                        label = { Text(a.rotulo) },
                        icon = {
                            when (a) {
                                Aba.PONTO -> Icon(Icons.Filled.Home, null)
                                Aba.DIA -> Icon(Icons.Filled.List, null)
                                Aba.MES -> Icon(Icons.Filled.DateRange, null)
                                Aba.PAINEL -> IconeBarras(LocalContentColor.current)
                                Aba.AJUSTES -> Icon(Icons.Filled.Settings, null)
                            }
                        }
                    )
                }
            }
        }
    ) { pad ->
        AnimatedContent(
            targetState = aba,
            modifier = Modifier.fillMaxSize().padding(pad),
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(tween(320)) { it / 4 * dir } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(320)) { -it / 4 * dir } + fadeOut(tween(200)))
            },
            label = "abas"
        ) { a ->
            when (a) {
                Aba.PONTO -> TelaPrincipal(todas, turnos, escuro) { onTema(!escuro) }
                Aba.DIA -> TelaDia(dia, { dia = it }, todas, turnos)
                Aba.MES -> TelaCalendario(mes, { mes = it }, todas, turnos) { d ->
                    dia = d
                    aba = Aba.DIA
                }
                Aba.PAINEL -> TelaPainel(mes, { mes = it }, turnos)
                Aba.AJUSTES -> TelaAjustes(escuro, onTema, todas)
            }
        }
    }
}

/** Ícone simples de gráfico de barras (desenhado). */
@Composable
private fun IconeBarras(cor: Color) {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width / 7f
        val alturas = listOf(0.45f, 0.8f, 0.6f)
        alturas.forEachIndexed { i, h ->
            val x = w * (1 + i * 2)
            drawRoundRect(
                color = cor,
                topLeft = Offset(x, size.height * (0.9f - h)),
                size = Size(w * 1.2f, size.height * h),
                cornerRadius = CornerRadius(2f)
            )
        }
    }
}
