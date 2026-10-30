package com.matheus.pontosvoith.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matheus.pontosvoith.dados.Batida
import com.matheus.pontosvoith.dados.StatusLocal
import com.matheus.pontosvoith.dados.TipoBatida
import com.matheus.pontosvoith.dados.Turno
import com.matheus.pontosvoith.ui.theme.CorAviso
import com.matheus.pontosvoith.ui.theme.CorOk
import com.matheus.pontosvoith.ui.theme.CorRetro
import com.matheus.pontosvoith.ui.theme.VoithNavy

fun textoLocal(b: Batida): String = when (b.statusLocal) {
    StatusLocal.PENDENTE -> "Obtendo localização…"
    StatusLocal.SEM_PERMISSAO -> "Sem permissão de localização"
    StatusLocal.DESATIVADO -> "GPS desativado nos ajustes"
    StatusLocal.MANUAL -> "Registro manual (sem localização)"
    StatusLocal.INDISPONIVEL -> "Localização indisponível"
    else -> if (b.latitude != null && b.longitude != null)
        String.format(LOCALE_BR, "%.5f, %.5f  (±%.0f m)", b.latitude, b.longitude, b.precisao ?: 0.0)
    else "Localização indisponível"
}

/**
 * Cartão de uma batida, com selos "Ajustado" e "Retroativo".
 * Toque = onClick | Toque longo = onLongClick (seleção múltipla).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemBatida(
    b: Batida,
    mostrarData: Boolean = true,
    modoSelecao: Boolean = false,
    selecionado: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    val entrada = b.tipo == TipoBatida.ENTRADA
    val corTipo = if (entrada) cs.primary else VoithNavy
    val fundo by animateColorAsState(
        if (selecionado) cs.primary.copy(alpha = 0.16f) else cs.surface, tween(220), label = "fundo"
    )
    val borda by animateColorAsState(
        if (selecionado) cs.primary else Color.Transparent, tween(220), label = "borda"
    )
    val forma = RoundedCornerShape(14.dp)
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(forma)
            .border(2.dp, borda, forma)
            .then(
                if (onClick != null || onLongClick != null)
                    Modifier.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
                else Modifier
            ),
        shape = forma,
        colors = CardDefaults.cardColors(containerColor = fundo)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                visible = modoSelecao,
                enter = expandHorizontally(tween(220)) + fadeIn(tween(220)),
                exit = shrinkHorizontally(tween(180)) + fadeOut(tween(150))
            ) {
                Row {
                    CaixaSelecao(selecionado)
                    Spacer(Modifier.width(12.dp))
                }
            }
            Box(Modifier.size(12.dp).clip(CircleShape).background(corTipo))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (entrada) "ENTRADA" else "SAÍDA", fontWeight = FontWeight.Bold, color = corTipo, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (mostrarData) "${b.dataHora.formatar(FMT_DIA)}  ${b.dataHora.formatar(FMT_HORA)}"
                        else b.dataHora.formatar(FMT_HORA),
                        fontSize = 14.sp, color = cs.onSurface
                    )
                }
                if (b.ajustado || b.retroativa) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (b.ajustado) Selo("AJUSTADO", CorAviso)
                        if (b.retroativa) Selo("RETROATIVO", CorRetro)
                    }
                }
                Text("📍 ${textoLocal(b)}", fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.65f))
                b.endereco?.let { Text(it, fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.65f), maxLines = 2) }
                val orig = b.valorOriginal
                if (b.ajustado && orig != null) {
                    Text(
                        "Original: ${orig.formatar(FMT_DIA)} ${orig.formatar(FMT_HORA)}",
                        fontSize = 12.sp, color = CorAviso
                    )
                }
                b.obsAjuste?.let { Text("Obs.: $it", fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.75f)) }
            }
            if (onClick != null && !modoSelecao) Text("✎", color = cs.primary, fontSize = 18.sp)
        }
    }
}

@Composable
private fun CaixaSelecao(marcado: Boolean) {
    val cs = MaterialTheme.colorScheme
    val fundo by animateColorAsState(if (marcado) cs.primary else Color.Transparent, tween(180), label = "cx")
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(fundo)
            .border(2.dp, if (marcado) cs.primary else cs.onSurface.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(visible = marcado, enter = scaleIn(tween(180)), exit = scaleOut(tween(120))) {
            Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Linha de turno: "20:00 → 03:00 (+1)  7h 00min". */
@Composable
fun LinhaTurno(t: Turno) {
    val cs = MaterialTheme.colorScheme
    val e = t.entrada
    val s = t.saida
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        val ini = e?.dataHora?.formatar(FMT_HM) ?: "--:--"
        val fim = s?.dataHora?.formatar(FMT_HM) ?: "--:--"
        val virou = if (e != null && s != null) {
            val d = java.time.temporal.ChronoUnit.DAYS.between(
                java.time.Instant.ofEpochMilli(e.dataHora).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                java.time.Instant.ofEpochMilli(s.dataHora).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            )
            if (d > 0) " (+$d)" else ""
        } else ""
        Text("$ini  →  $fim$virou", Modifier.weight(1f), color = cs.onSurface, fontSize = 15.sp)
        when {
            t.completo -> Text(duracaoTexto(t.duracaoMs), fontWeight = FontWeight.SemiBold, color = cs.primary)
            t.emAndamento -> Selo("EM ANDAMENTO", CorOk)
            e == null -> Selo("SAÍDA SEM ENTRADA", CorAviso)
            else -> Selo("SEM SAÍDA", CorAviso)
        }
    }
}
