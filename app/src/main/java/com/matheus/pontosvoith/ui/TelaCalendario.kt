package com.matheus.pontosvoith.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matheus.pontosvoith.dados.Batida
import com.matheus.pontosvoith.dados.Turno
import com.matheus.pontosvoith.dados.horasPorDia
import com.matheus.pontosvoith.dados.paraData
import com.matheus.pontosvoith.dados.turnosDoMes
import com.matheus.pontosvoith.ui.theme.CorAviso
import java.time.LocalDate
import java.time.YearMonth

/** Calendário mensal: dias coloridos conforme horas trabalhadas. Toque abre o dia. */
@Composable
fun TelaCalendario(
    mes: YearMonth,
    onMes: (YearMonth) -> Unit,
    todas: List<Batida>,
    turnos: List<Turno>,
    onAbrirDia: (LocalDate) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val mesAtual = YearMonth.now()
    val horas = remember(turnos) { horasPorDia(turnos) }
    val diasComRegistro = remember(todas) { todas.map { it.dataHora.paraData() }.toSet() }
    val diasIncompletos = remember(turnos) { turnos.filter { it.incompleto }.map { it.dia }.toSet() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text("Calendário", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.primary)
        SeletorPeriodo(
            texto = mes.format(FMT_MES_ANO).capitalizar(),
            onAnterior = { onMes(mes.minusMonths(1)) },
            onProximo = { onMes(mes.plusMonths(1)) },
            proximoHabilitado = mes.isBefore(mesAtual)
        )

        AnimatedContent(
            targetState = mes,
            transitionSpec = {
                val dir = if (targetState.isAfter(initialState)) 1 else -1
                (slideInHorizontally(tween(300)) { it * dir } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { -it * dir } + fadeOut(tween(200)))
            },
            label = "mes"
        ) { m ->
            Column {
                CartaoBase {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("D", "S", "T", "Q", "Q", "S", "S").forEach {
                            Text(
                                it, Modifier.weight(1f), textAlign = TextAlign.Center,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cs.onSurface.copy(alpha = 0.55f)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val offset = m.atDay(1).dayOfWeek.value % 7 // domingo = 0
                    val qtdDias = m.lengthOfMonth()
                    val linhas = (offset + qtdDias + 6) / 7
                    val hoje = LocalDate.now()
                    for (l in 0 until linhas) {
                        Row(Modifier.fillMaxWidth()) {
                            for (c in 0 until 7) {
                                val n = l * 7 + c - offset + 1
                                if (n in 1..qtdDias) {
                                    val data = m.atDay(n)
                                    CelulaDia(
                                        modifier = Modifier.weight(1f),
                                        numero = n,
                                        ms = horas[data] ?: 0L,
                                        temRegistro = data in diasComRegistro,
                                        incompleto = data in diasIncompletos,
                                        hoje = data == hoje,
                                        futuro = data.isAfter(hoje),
                                        onClick = { onAbrirDia(data) }
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f).aspectRatio(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Legenda(cs.primary.copy(alpha = 0.75f), "Horas trabalhadas")
                    Legenda(CorAviso, "Turno incompleto")
                }

                val doMes = turnosDoMes(turnos, m)
                val total = doMes.sumOf { it.duracaoMs }
                val diasTrab = doMes.filter { it.completo }.map { it.dia }.toSet().size
                TituloSecao("Resumo do mês")
                CartaoBase {
                    LinhaResumo("Horas trabalhadas") { HorasAnimadas(total, 18.sp, cs.primary) }
                    LinhaResumo("Dias trabalhados") { NumeroAnimado(diasTrab, 18.sp) }
                    LinhaResumo("Turnos completos") { NumeroAnimado(doMes.count { it.completo }, 18.sp) }
                    LinhaResumo("Turnos incompletos") {
                        val inc = doMes.count { it.incompleto }
                        NumeroAnimado(inc, 18.sp, if (inc > 0) CorAviso else cs.onSurface)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CelulaDia(
    modifier: Modifier,
    numero: Int,
    ms: Long,
    temRegistro: Boolean,
    incompleto: Boolean,
    hoje: Boolean,
    futuro: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val horas = ms / 3_600_000f
    val intensidade = if (ms > 0) 0.25f + 0.65f * (horas / 10f).coerceAtMost(1f) else 0f
    val fundo = when {
        ms > 0 -> cs.primary.copy(alpha = intensidade)
        temRegistro -> cs.primary.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val corTexto = when {
        intensidade > 0.55f -> Color.White
        futuro -> cs.onSurface.copy(alpha = 0.3f)
        else -> cs.onSurface
    }
    Box(
        modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(fundo)
            .then(if (hoje) Modifier.border(2.dp, cs.primary, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(enabled = !futuro) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$numero", fontSize = 14.sp, fontWeight = if (hoje) FontWeight.Bold else FontWeight.Normal, color = corTexto)
            if (ms > 0) Text(duracaoCurta(ms), fontSize = 9.sp, color = corTexto)
        }
        if (incompleto) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(CorAviso)
            )
        }
    }
}

@Composable
private fun Legenda(cor: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(cor))
        Spacer(Modifier.width(6.dp))
        Text(texto, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
    }
}

@Composable
fun LinhaResumo(rotulo: String, valor: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(rotulo, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        valor()
    }
}
