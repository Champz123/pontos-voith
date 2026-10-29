package com.matheus.pontosvoith.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/** Cartão de uma batida, com selos "Ajustado" e "Retroativo". */
@Composable
fun ItemBatida(b: Batida, mostrarData: Boolean = true, onClick: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    val entrada = b.tipo == TipoBatida.ENTRADA
    val corTipo = if (entrada) cs.primary else VoithNavy
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                if (b.ajustado && b.valorOriginal != null) {
                    Text(
                        "Original: ${b.valorOriginal.formatar(FMT_DIA)} ${b.valorOriginal.formatar(FMT_HORA)}",
                        fontSize = 12.sp, color = CorAviso
                    )
                }
                b.obsAjuste?.let { Text("Obs.: $it", fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.75f)) }
            }
            if (onClick != null) Text("✎", color = cs.primary, fontSize = 18.sp)
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
