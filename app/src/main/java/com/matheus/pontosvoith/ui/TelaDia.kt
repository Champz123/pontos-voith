package com.matheus.pontosvoith.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import com.matheus.pontosvoith.ui.theme.CorErro
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Histórico do dia.
 * - Toque: editar | Toque longo: entra no modo de seleção múltipla.
 * - No modo de seleção: toque marca/desmarca; "Todos"; "Excluir" (vai para a Lixeira).
 */
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
    var selecionados by remember { mutableStateOf(setOf<Long>()) }
    var confirmarExclusao by remember { mutableStateOf(false) }
    val modoSelecao = selecionados.isNotEmpty()
    val hoje = LocalDate.now()

    val doDia = remember(todas, dia) { todas.filter { it.dataHora.paraData() == dia } }

    LaunchedEffect(dia) { selecionados = emptySet() }
    LaunchedEffect(todas) {
        val ids = todas.map { it.id }.toSet()
        selecionados = selecionados.filter { it in ids }.toSet()
    }
    BackHandler(enabled = modoSelecao) { selecionados = emptySet() }

    fun alternar(id: Long) {
        selecionados = if (id in selecionados) selecionados - id else selecionados + id
    }

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
            AnimatedContent(
                targetState = modoSelecao,
                transitionSpec = { (fadeIn(tween(220)) + slideInVertically(tween(220)) { -it / 2 }) togetherWith fadeOut(tween(150)) },
                label = "cabecalho"
            ) { selecao ->
                if (!selecao) {
                    Text("Histórico do dia", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.primary)
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cs.primary.copy(alpha = 0.12f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selecionados = emptySet() }) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancelar seleção", tint = cs.onSurface)
                            }
                            Text(
                                "${selecionados.size} selecionado(s)", Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold, color = cs.onSurface
                            )
                            val todosMarcados = doDia.isNotEmpty() && doDia.all { it.id in selecionados }
                            TextButton(onClick = {
                                selecionados = if (todosMarcados) emptySet() else doDia.map { it.id }.toSet()
                            }) { Text(if (todosMarcados) "Nenhum" else "Todos") }
                            IconButton(onClick = { confirmarExclusao = true }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Excluir selecionados", tint = CorErro)
                            }
                        }
                    }
                }
            }

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
                val registrosDia = remember(todas, d) { todas.filter { it.dataHora.paraData() == d } }
                val turnosDia = remember(turnos, d) { turnos.filter { it.dia == d } }
                val total = turnosDia.sumOf { it.duracaoMs }
                val almocoDia = turnosDia.sumOf { it.descontoAlmocoMs }
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
                        if (almocoDia > 0) {
                            Text("Almoço descontado: ${duracaoTexto(almocoDia)}", fontSize = 12.sp, color = CorAviso)
                        }
                        if (turnosDia.any { it.emAndamento }) {
                            Text("Há um turno em andamento (não somado).", fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.6f))
                        }
                    }

                    if (turnosDia.isNotEmpty()) {
                        TituloSecao("Turnos (iniciados neste dia)")
                        CartaoBase { turnosDia.forEach { LinhaTurno(it) } }
                    }

                    TituloSecao("Registros do dia")
                    if (registrosDia.isEmpty()) {
                        Text("Nenhum registro neste dia.", color = cs.onBackground.copy(alpha = 0.6f), fontSize = 14.sp)
                    } else {
                        Text(
                            if (modoSelecao) "Toque para marcar/desmarcar. Voltar cancela a seleção."
                            else "Toque para editar • Segure para selecionar vários • 📍 abre o mapa",
                            fontSize = 12.sp, color = cs.onBackground.copy(alpha = 0.55f)
                        )
                        registrosDia.forEach { b ->
                            ItemBatida(
                                b = b,
                                mostrarData = false,
                                modoSelecao = modoSelecao,
                                selecionado = b.id in selecionados,
                                onLongClick = { alternar(b.id) },
                                onClick = { if (modoSelecao) alternar(b.id) else editando = b }
                            )
                        }
                    }
                }
            }
            MensagemAnimada(mensagem)
            Spacer(Modifier.height(88.dp))
        }

        AnimatedVisibility(
            visible = !modoSelecao,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            enter = scaleIn(tween(200)) + fadeIn(tween(200)),
            exit = scaleOut(tween(150)) + fadeOut(tween(150))
        ) {
            ExtendedFloatingActionButton(
                onClick = { incluindo = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Incluir") },
                containerColor = cs.primary,
                contentColor = cs.onPrimary
            )
        }
    }

    if (confirmarExclusao) {
        val alvo = todas.filter { it.id in selecionados }
        val ids = alvo.map { it.id }.toSet()
        val quebraTurno = turnos.any { t ->
            val e = t.entrada
            val s = t.saida
            e != null && s != null && ((e.id in ids) xor (s.id in ids))
        }
        DialogoExclusao(
            registros = alvo,
            avisoTurno = quebraTurno,
            onCancelar = { confirmarExclusao = false },
            onConfirmar = {
                confirmarExclusao = false
                val lista = ids.toList()
                selecionados = emptySet()
                executar {
                    val n = Repositorio.excluirVarios(lista)
                    if (n == 1) "1 registro movido para a Lixeira" else "$n registros movidos para a Lixeira"
                }
            }
        )
    }

    if (incluindo) {
        DialogoBatida(
            inicial = null,
            diaPadrao = dia,
            onFechar = { incluindo = false },
            onSalvar = { tipo, ms, obs, almoco ->
                incluindo = false
                executar {
                    Repositorio.incluirManual(tipo, ms, obs, almoco)
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
            onSalvar = { tipo, ms, obs, almoco ->
                editando = null
                executar {
                    if (Repositorio.editar(b.id, tipo, ms, obs, almoco)) "Registro ajustado" else "Nenhuma alteração"
                }
            },
            onExcluir = {
                editando = null
                executar {
                    Repositorio.excluir(b.id)
                    "Registro movido para a Lixeira"
                }
            }
        )
    }
}
