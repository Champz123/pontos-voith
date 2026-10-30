package com.matheus.pontosvoith.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matheus.pontosvoith.dados.Batida
import com.matheus.pontosvoith.dados.nomeTipo
import com.matheus.pontosvoith.ui.theme.CorAviso
import com.matheus.pontosvoith.ui.theme.CorErro

/**
 * Confirmação de exclusão (1 ou vários registros). V6: vão para a Lixeira (30 dias).
 * O botão "Excluir" só habilita após marcar "Entendo…".
 */
@Composable
fun DialogoExclusao(
    registros: List<Batida>,
    avisoTurno: Boolean,
    onCancelar: () -> Unit,
    onConfirmar: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var entendi by remember { mutableStateOf(false) }
    val n = registros.size
    val ord = registros.sortedBy { it.dataHora }
    val especiais = registros.count { it.ajustado || it.retroativa }

    AlertDialog(
        onDismissRequest = onCancelar,
        icon = { Text("!", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = CorErro) },
        title = { Text(if (n == 1) "Excluir 1 registro?" else "Excluir $n registros?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ord.take(6).forEach { b ->
                    Text(
                        "• ${nomeTipo(b.tipo)} — ${b.dataHora.formatar(FMT_DIA)} ${b.dataHora.formatar(FMT_HORA)}",
                        fontSize = 14.sp, color = cs.onSurface
                    )
                }
                if (n > 6) Text("… e mais ${n - 6} registro(s)", fontSize = 13.sp, color = cs.onSurface.copy(alpha = 0.7f))

                Spacer(Modifier.height(12.dp))
                Aviso("Os registros vão para a LIXEIRA (Ajustes > Lixeira) e podem ser restaurados por 30 dias.", cs.primary)
                if (avisoTurno) Aviso("Alguns turnos ficarão incompletos (entrada ou saída sem par) e as horas deles deixarão de ser somadas.", CorAviso)
                if (especiais > 0) Aviso("$especiais registro(s) selecionado(s) são AJUSTADOS ou RETROATIVOS.", CorAviso)

                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { entendi = !entendi },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = entendi, onCheckedChange = { entendi = it })
                    Text("Entendo que os registros serão excluídos", fontSize = 14.sp, color = cs.onSurface)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmar, enabled = entendi) {
                Text("Excluir", color = if (entendi) CorErro else cs.onSurface.copy(alpha = 0.38f), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

@Composable
private fun Aviso(texto: String, cor: Color) {
    Text("⚠ $texto", fontSize = 13.sp, color = cor, modifier = Modifier.padding(vertical = 2.dp))
}
