package com.matheus.pontosvoith.ui

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matheus.pontosvoith.dados.Batida
import com.matheus.pontosvoith.dados.Repositorio
import com.matheus.pontosvoith.dados.Turno
import com.matheus.pontosvoith.dados.paraData
import com.matheus.pontosvoith.ui.theme.CorAviso
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Histórico do dia: total trabalhado, turnos e registros (editar/excluir/incluir). */
@Composable
fun TelaDia(
    dia: LocalDate,
    onDia: (LocalDate) -> Unit,
    todas: List<Batida>,
    turnos: List<Turno>
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val escopo = rememberCoroutineScope()
    var mensagem by rememberMensagem()
    var editando by remember { mutableStateOf<Batida?>(null) }
    var incluindo by remember { mutableStateOf(false) }
    val hoje = LocalDate.now()

    fun executar(acao: suspend () -> String) {
        escopo.launch {
            mensagem = try {
                Mensagem(TipoMensagem.OK, acao())
            } catch (e: Exception) {
                Mensagem(TipoMensagem.ERRO, e.message ?: "Erro ao salvar")
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("Histórico do dia", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.primary)
            SeletorPeriodo(
                texto = dia.format(FMT_DIA_SEMANA).capitalizar(),
                onAnterior = { onDia(dia.minusDays(1)) },
                onProximo = { onDia(dia.plusDays(1)) },
                proximoHabilitado = dia.isBefore(hoje),
                onTexto = {
                    DatePickerDialog(ctx, { _, a, m, d -> onDia(LocalDate.of(a, m + 1, d)) },
                        dia.year, dia.monthValue - 1, dia.dayOfMonth
                    ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                }
            )

            AnimatedContent(
                targetState = dia,
                transitionSpec = {
                    val dir = if (targetState.isAfter(initialState)) 1 else -1
                    (slideInHorizontally(tween(280)) { it / 3 * dir } + fadeIn(tween(280))) togetherWith
                        (slideOutHorizontally(tween(280)) { -it / 3 * dir } + fadeOut(tween(180)))
                },
                label = "dia"
            ) { d ->
                val doDia = remember(todas, d) { todas.filter { it.dataHora.paraData() == d } }
                val turnosDia = remember(turnos, d) { turnos.filter { it.dia == d } }
                val total = turnosDia.sumOf { it.duracaoMs }
                val incompletos = turnosDia.count { it.incompleto }

                Column(Modifier.fillMaxWidth()) {
                    CartaoBase {
                        Text("Horas trabalhadas", fontSize = 13.sp, color = cs.onSurface.copy(alpha = 0.7f))
                        HorasAnimadas(total, 28.sp, cs.primary)
                        Text(
                            "${turnosDia.count { it.completo }} turno(s) completo(s)" +
                                if (incompletos > 0) " • $incompletos incompleto(s)" else "",
                            fontSize = 13.sp,
                            color = if (incompletos > 0) CorAviso else cs.onSurface.copy(alpha = 0.7f)
                        )
                        if (turnosDia.any { it.emAndamento }) {
                            Text("Há um turno em andamento (não somado).", fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.6f))
                        }
                    }

                    if (turnosDia.isNotEmpty()) {
                        TituloSecao("Turnos (iniciados neste dia)")
                        CartaoBase { turnosDia.forEach { LinhaTurno(it) } }
                    }

                    TituloSecao("Registros do dia")
                    if (doDia.isEmpty()) {
                        Text("Nenhum registro neste dia.", color = cs.onBackground.copy(alpha = 0.6f), fontSize = 14.sp)
                    } else {
                        Text("Toque em um registro para editar ou excluir.", fontSize = 12.sp, color = cs.onBackground.copy(alpha = 0.55f))
                        doDia.forEach { b -> ItemBatida(b, mostrarData = false) { editando = b } }
                    }
                }
            }
            MensagemAnimada(mensagem)
            Spacer(Modifier.height(88.dp))
        }

        ExtendedFloatingActionButton(
            onClick = { incluindo = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Incluir") },
            containerColor = cs.primary,
            contentColor = cs.onPrimary
        )
    }

    if (incluindo) {
        DialogoBatida(
            inicial = null,
            diaPadrao = dia,
            onFechar = { incluindo = false },
            onSalvar = { tipo, ms, obs ->
                incluindo = false
                executar {
                    Repositorio.incluirManual(tipo, ms, obs)
                    onDia(java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate())
                    "Registro incluído (retroativo)"
                }
            },
            onExcluir = null
        )
    }

    editando?.let { b ->
        DialogoBatida(
            inicial = b,
            diaPadrao = dia,
            onFechar = { editando = null },
            onSalvar = { tipo, ms, obs ->
                editando = null
                executar {
                    if (Repositorio.editar(b.id, tipo, ms, obs)) "Registro ajustado" else "Nenhuma alteração"
                }
            },
            onExcluir = {
                editando = null
                executar {
                    Repositorio.excluir(b.id)
                    "Registro excluído"
                }
            }
        )
    }
}
