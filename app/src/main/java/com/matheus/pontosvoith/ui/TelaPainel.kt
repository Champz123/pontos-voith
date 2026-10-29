package com.matheus.pontosvoith.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matheus.pontosvoith.dados.Turno
import com.matheus.pontosvoith.dados.turnosDoMes
import com.matheus.pontosvoith.ui.theme.CorAviso
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Dashboard (somente horas trabalhadas) + gráficos animados. */
@Composable
fun TelaPainel(mes: YearMonth, onMes: (YearMonth) -> Unit, turnos: List<Turno>) {
    val cs = MaterialTheme.colorScheme
    val hoje = LocalDate.now()
    val inicioSemana = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val completos = remember(turnos) { turnos.filter { it.completo } }

    val msHoje = completos.filter { it.dia == hoje }.sumOf { it.duracaoMs }
    val msSemana = completos.filter { !it.dia.isBefore(inicioSemana) && !it.dia.isAfter(hoje) }.sumOf { it.duracaoMs }
    val doMes = remember(turnos, mes) { turnosDoMes(turnos, mes) }
    val msMes = doMes.filter { it.completo }.sumOf { it.duracaoMs }
    val diasTrab = doMes.filter { it.completo }.map { it.dia }.toSet().size
    val media = if (diasTrab > 0) msMes / diasTrab else 0L
    val incompletos = doMes.count { it.incompleto }

    val barras = remember(doMes, mes) {
        val arr = FloatArray(mes.lengthOfMonth())
        doMes.filter { it.completo }.forEach { arr[it.dia.dayOfMonth - 1] += it.duracaoMs / 3_600_000f }
        arr.toList()
    }
    val meses = remember(mes) { (5 downTo 0).map { mes.minusMonths(it.toLong()) } }
    val linha = remember(completos, meses) {
        meses.map { m -> completos.filter { YearMonth.from(it.dia) == m }.sumOf { it.duracaoMs } / 3_600_000f }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text("Painel", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.primary)
        Text("Somente horas trabalhadas (Saída − Entrada).", fontSize = 12.sp, color = cs.onBackground.copy(alpha = 0.6f))
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CartaoKpi("Hoje", Modifier.weight(1f)) { HorasAnimadas(msHoje, 20.sp, cs.primary) }
            CartaoKpi("Esta semana", Modifier.weight(1f)) { HorasAnimadas(msSemana, 20.sp, cs.primary) }
        }

        Spacer(Modifier.height(6.dp))
        SeletorPeriodo(
            texto = mes.format(FMT_MES_ANO).capitalizar(),
            onAnterior = { onMes(mes.minusMonths(1)) },
            onProximo = { onMes(mes.plusMonths(1)) },
            proximoHabilitado = mes.isBefore(YearMonth.now())
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CartaoKpi("Total do mês", Modifier.weight(1f)) { HorasAnimadas(msMes, 20.sp, cs.primary) }
            CartaoKpi("Média / dia trabalhado", Modifier.weight(1f)) { HorasAnimadas(media, 20.sp) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CartaoKpi("Dias trabalhados", Modifier.weight(1f)) { NumeroAnimado(diasTrab, 20.sp) }
            CartaoKpi("Turnos incompletos", Modifier.weight(1f)) {
                NumeroAnimado(incompletos, 20.sp, if (incompletos > 0) CorAviso else cs.onSurface)
            }
        }

        TituloSecao("Horas por dia — ${mes.format(FMT_MES_ANO)}")
        CartaoBase { GraficoBarras(barras, chave = mes) }

        TituloSecao("Últimos 6 meses")
        CartaoBase { GraficoLinha(linha, meses.map { it.format(FMT_MES_CURTO) }, chave = mes) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CartaoKpi(titulo: String, modifier: Modifier, valor: @Composable () -> Unit) {
    CartaoBase(modifier) {
        Text(titulo, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), maxLines = 1)
        Spacer(Modifier.height(4.dp))
        valor()
    }
}

/** Barras animadas (crescem de baixo para cima). */
@Composable
fun GraficoBarras(valores: List<Float>, chave: Any) {
    val cs = MaterialTheme.colorScheme
    val prog = remember(chave) { Animatable(0f) }
    LaunchedEffect(chave) { prog.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val cor = cs.primary
    val corGrade = cs.onSurface.copy(alpha = 0.10f)
    val corTexto = cs.onSurface.copy(alpha = 0.6f).toArgb()
    val max = maxOf(valores.maxOrNull() ?: 0f, 1f)

    if (valores.all { it == 0f }) {
        SemDados()
        return
    }
    Canvas(Modifier.fillMaxWidth().height(190.dp)) {
        val paint = android.graphics.Paint().apply {
            color = corTexto; textSize = 10.dp.toPx(); isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val rodape = 16.dp.toPx()
        val topo = 14.dp.toPx()
        val baseY = size.height - rodape
        val altUtil = baseY - topo
        // linhas de grade (25/50/75/100%)
        for (i in 1..4) {
            val y = baseY - altUtil * i / 4f
            drawLine(corGrade, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }
        paint.textAlign = android.graphics.Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText(duracaoCurta((max * 3_600_000).toLong()), 0f, topo - 3.dp.toPx(), paint)
        paint.textAlign = android.graphics.Paint.Align.CENTER

        val n = valores.size
        val larg = size.width / n
        valores.forEachIndexed { i, v ->
            val h = (v / max) * altUtil * prog.value
            if (h > 0f) {
                drawRoundRect(
                    color = cor,
                    topLeft = Offset(i * larg + larg * 0.18f, baseY - h),
                    size = Size(larg * 0.64f, h),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
            }
            val dia = i + 1
            if (dia == 1 || dia % 5 == 0) {
                drawContext.canvas.nativeCanvas.drawText("$dia", i * larg + larg / 2f, size.height - 3.dp.toPx(), paint)
            }
        }
    }
}

/** Linha animada (desenhada da esquerda para a direita). */
@Composable
fun GraficoLinha(valores: List<Float>, rotulos: List<String>, chave: Any) {
    val cs = MaterialTheme.colorScheme
    val prog = remember(chave) { Animatable(0f) }
    LaunchedEffect(chave) { prog.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    val cor = cs.primary
    val corTexto = cs.onSurface.copy(alpha = 0.6f).toArgb()
    val corValor = cs.onSurface.toArgb()
    val max = maxOf(valores.maxOrNull() ?: 0f, 1f)

    if (valores.all { it == 0f }) {
        SemDados()
        return
    }
    Canvas(Modifier.fillMaxWidth().height(190.dp)) {
        val paint = android.graphics.Paint().apply {
            textSize = 10.dp.toPx(); isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER
        }
        val margemX = 22.dp.toPx()
        val topo = 22.dp.toPx()
        val baseY = size.height - 18.dp.toPx()
        val altUtil = baseY - topo
        val n = valores.size
        val passo = if (n > 1) (size.width - 2 * margemX) / (n - 1) else 0f
        val pontos = valores.mapIndexed { i, v -> Offset(margemX + i * passo, baseY - (v / max) * altUtil) }

        // até onde desenhar
        val alcance = prog.value * (n - 1)
        val path = Path()
        val area = Path()
        pontos.forEachIndexed { i, p ->
            if (i > alcance + 1e-3f) return@forEachIndexed
            if (i == 0) { path.moveTo(p.x, p.y); area.moveTo(p.x, baseY); area.lineTo(p.x, p.y) }
            else { path.lineTo(p.x, p.y); area.lineTo(p.x, p.y) }
        }
        // segmento parcial
        val iBase = alcance.toInt()
        if (iBase < n - 1) {
            val f = alcance - iBase
            val a = pontos[iBase]
            val b = pontos[iBase + 1]
            val px = a.x + (b.x - a.x) * f
            val py = a.y + (b.y - a.y) * f
            path.lineTo(px, py); area.lineTo(px, py); area.lineTo(px, baseY)
        } else {
            area.lineTo(pontos.last().x, baseY)
        }
        area.close()
        drawPath(area, cor.copy(alpha = 0.12f))
        drawPath(path, cor, style = Stroke(width = 3.dp.toPx()))

        pontos.forEachIndexed { i, p ->
            paint.color = corTexto
            drawContext.canvas.nativeCanvas.drawText(rotulos[i], p.x, size.height - 3.dp.toPx(), paint)
            if (i <= alcance + 1e-3f) {
                drawCircle(Color.White, 5.dp.toPx(), p)
                drawCircle(cor, 3.5.dp.toPx(), p)
                paint.color = corValor
                drawContext.canvas.nativeCanvas.drawText(
                    duracaoCurta((valores[i] * 3_600_000).toLong()), p.x, p.y - 9.dp.toPx(), paint
                )
            }
        }
    }
}

@Composable
private fun SemDados() {
    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text("Sem horas registradas neste período.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

