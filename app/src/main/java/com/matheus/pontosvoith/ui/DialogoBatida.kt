package com.matheus.pontosvoith.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matheus.pontosvoith.dados.Batida
import com.matheus.pontosvoith.dados.TipoBatida
import com.matheus.pontosvoith.ui.theme.CorAviso
import com.matheus.pontosvoith.ui.theme.CorErro
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Diálogo para INCLUIR (inicial = null) ou EDITAR uma batida. */
@Composable
fun DialogoBatida(
    inicial: Batida?,
    diaPadrao: LocalDate,
    onFechar: () -> Unit,
    onSalvar: (tipo: String, dataHora: Long, obs: String?) -> Unit,
    onExcluir: (() -> Unit)?
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val zona = ZoneId.systemDefault()
    val origLdt = inicial?.let { Instant.ofEpochMilli(it.dataHora).atZone(zona).toLocalDateTime() }

    var tipo by remember { mutableStateOf(inicial?.tipo ?: TipoBatida.ENTRADA) }
    var data by remember { mutableStateOf(origLdt?.toLocalDate() ?: diaPadrao) }
    var hora by remember {
        mutableStateOf(origLdt?.toLocalTime()?.withSecond(0)?.withNano(0) ?: LocalTime.now().withSecond(0).withNano(0))
    }
    var obs by remember { mutableStateOf(inicial?.obsAjuste ?: "") }
    var erro by remember { mutableStateOf<String?>(null) }
    var confirmarExclusao by remember { mutableStateOf(false) }

    fun calcularMillis(): Long {
        if (inicial != null && origLdt != null &&
            origLdt.toLocalDate() == data && origLdt.hour == hora.hour && origLdt.minute == hora.minute
        ) return inicial.dataHora
        return LocalDateTime.of(data, hora).paraMillis()
    }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text(if (inicial == null) "Incluir registro manual" else "Editar registro") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Tipo", fontSize = 13.sp, color = cs.onSurface.copy(alpha = 0.7f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(TipoBatida.ENTRADA to "Entrada", TipoBatida.SAIDA to "Saída").forEach { (t, rotulo) ->
                        if (tipo == t) {
                            Button(onClick = { tipo = t }, Modifier.weight(1f)) { Text(rotulo) }
                        } else {
                            OutlinedButton(onClick = { tipo = t }, Modifier.weight(1f)) { Text(rotulo) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Data e hora", fontSize = 13.sp, color = cs.onSurface.copy(alpha = 0.7f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(ctx, { _, a, m, d -> data = LocalDate.of(a, m + 1, d); erro = null },
                                data.year, data.monthValue - 1, data.dayOfMonth
                            ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                        },
                        Modifier.weight(1.3f)
                    ) { Text(data.format(FMT_DIA)) }
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(ctx, { _, h, min -> hora = LocalTime.of(h, min); erro = null },
                                hora.hour, hora.minute, true
                            ).show()
                        },
                        Modifier.weight(1f)
                    ) { Text(hora.format(FMT_HM)) }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = obs, onValueChange = { obs = it.take(200) },
                    label = { Text("Observação (opcional)") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 3
                )
                if (inicial == null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Será marcado como RETROATIVO no histórico.", fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.7f))
                } else if (!inicial.retroativa) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Alterar data, hora ou tipo marca o registro como AJUSTADO (o horário original fica guardado).",
                        fontSize = 12.sp, color = CorAviso
                    )
                }
                erro?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = CorErro, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                if (onExcluir != null) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { confirmarExclusao = true }) { Text("Excluir registro", color = CorErro) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ms = calcularMillis()
                if (ms > System.currentTimeMillis()) {
                    erro = "Data/hora no futuro não é permitida."
                } else {
                    onSalvar(tipo, ms, obs)
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } }
    )

    if (confirmarExclusao && onExcluir != null && inicial != null) {
        DialogoExclusao(
            registros = listOf(inicial),
            avisoTurno = false,
            onCancelar = { confirmarExclusao = false },
            onConfirmar = { confirmarExclusao = false; onExcluir() }
        )
    }
}
