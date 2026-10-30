package com.matheus.pontosvoith.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matheus.pontosvoith.dados.Batida
import com.matheus.pontosvoith.dados.nomeTipo
import com.matheus.pontosvoith.ui.theme.CorErro

/** V6: Lixeira — restaurar item a item, restaurar tudo ou esvaziar (com confirmação). */
@Composable
fun DialogoLixeira(
    itens: List<Batida>,
    onFechar: () -> Unit,
    onRestaurar: (List<Long>) -> Unit,
    onEsvaziar: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var confirmarEsvaziar by remember { mutableStateOf(false) }
    val dia = 24L * 3600 * 1000

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Lixeira (${itens.size})") },
        text = {
            Column {
                Text(
                    "Registros excluídos ficam aqui por 30 dias e depois são apagados de vez.",
                    fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(8.dp))
                if (itens.isEmpty()) {
                    Text("A lixeira está vazia.", color = cs.onSurface.copy(alpha = 0.7f))
                }
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    itens.forEach { b ->
                        val exc = b.excluidoEm ?: 0L
                        val restam = (30 - (System.currentTimeMillis() - exc) / dia).coerceAtLeast(0)
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${nomeTipo(b.tipo)} — ${b.dataHora.formatar(FMT_DIA)} ${b.dataHora.formatar(FMT_HM)}",
                                    fontWeight = FontWeight.Medium, color = cs.onSurface, fontSize = 14.sp
                                )
                                Text(
                                    "Excluído em ${exc.formatar(FMT_DIA)} • apaga em $restam dia(s)",
                                    fontSize = 11.sp, color = cs.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            TextButton(onClick = { onRestaurar(listOf(b.id)) }) { Text("Restaurar") }
                        }
                    }
                }
                if (itens.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { confirmarEsvaziar = true }) { Text("Esvaziar lixeira", color = CorErro) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onRestaurar(itens.map { it.id }) }, enabled = itens.isNotEmpty()) { Text("Restaurar tudo") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Fechar") } }
    )

    if (confirmarEsvaziar) {
        AlertDialog(
            onDismissRequest = { confirmarEsvaziar = false },
            icon = { Text("!", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = CorErro) },
            title = { Text("Esvaziar a lixeira?") },
            text = { Text("Os ${itens.size} registro(s) serão apagados DEFINITIVAMENTE. Esta ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = { confirmarEsvaziar = false; onEsvaziar() }) {
                    Text("Apagar de vez", color = CorErro, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarEsvaziar = false }) { Text("Cancelar") } }
        )
    }
}
