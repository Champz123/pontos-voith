package com.matheus.pontosvoith.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matheus.pontosvoith.VERSAO_APP
import com.matheus.pontosvoith.dados.Batida
import com.matheus.pontosvoith.dados.EstadoTurno
import com.matheus.pontosvoith.dados.Localizacao
import com.matheus.pontosvoith.dados.Preferencias
import com.matheus.pontosvoith.dados.Repositorio
import com.matheus.pontosvoith.dados.TipoBatida
import com.matheus.pontosvoith.dados.Turno
import com.matheus.pontosvoith.dados.calcularEstado
import com.matheus.pontosvoith.ui.theme.CorAviso
import com.matheus.pontosvoith.ui.theme.CorOk
import com.matheus.pontosvoith.ui.theme.VoithNavy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TelaPrincipal(
    todas: List<Batida>,
    turnos: List<Turno>,
    escuro: Boolean,
    onAlternarTema: () -> Unit
) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    val estado = remember(turnos) { calcularEstado(turnos) }
    val recentes = remember(todas) { todas.asReversed().take(6) }

    var agora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            agora = System.currentTimeMillis()
            delay(1000 - agora % 1000)
        }
    }

    var mensagem by rememberMensagem()

    // Permissão de localização (só pede se o GPS estiver ativo nos Ajustes)
    val gpsAtivo = remember { Preferencias.gpsAtivo(ctx) }
    var temPermissao by remember { mutableStateOf(Localizacao.temPermissao(ctx)) }
    val pedirPermissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        temPermissao = Localizacao.temPermissao(ctx)
    }
    LaunchedEffect(Unit) {
        if (gpsAtivo && !temPermissao) pedirPermissao.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    var registrando by remember { mutableStateOf(false) }
    var confirmarSegundaEntrada by remember { mutableStateOf(false) }

    fun baterPonto(tipo: String) {
        if (registrando) return
        registrando = true
        val havia = estado.abertoDesde != null
        escopo.launch {
            try {
                val b = Repositorio.registrar(tipo)
                val hora = b.dataHora.formatar(FMT_HORA)
                mensagem = when {
                    tipo == TipoBatida.ENTRADA && havia ->
                        Mensagem(TipoMensagem.AVISO, "Entrada registrada às $hora. A entrada anterior ficou sem saída.")
                    tipo == TipoBatida.SAIDA && !havia ->
                        Mensagem(TipoMensagem.AVISO, "Saída registrada às $hora. Não havia entrada aberta.")
                    tipo == TipoBatida.ENTRADA -> Mensagem(TipoMensagem.OK, "Entrada registrada às $hora")
                    else -> Mensagem(TipoMensagem.OK, "Saída registrada às $hora")
                }
            } catch (e: Exception) {
                mensagem = Mensagem(TipoMensagem.ERRO, "Falha ao registrar: ${e.message ?: "erro desconhecido"}")
            } finally {
                delay(1200)
                registrando = false
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Pontos Voith", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.primary)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onAlternarTema) { Text(if (escuro) "☀  Claro" else "☾  Escuro") }
            }

            Spacer(Modifier.height(8.dp))
            Text(agora.formatar(FMT_HORA), fontSize = 54.sp, fontWeight = FontWeight.Light, color = cs.onBackground)
            Text(agora.formatar(FMT_DATA_LONGA).capitalizar(), color = cs.onBackground.copy(alpha = 0.7f))

            Spacer(Modifier.height(16.dp))
            CartaoStatus(estado, agora)

            Spacer(Modifier.height(12.dp))
            val proximo = if (estado.abertoDesde == null) TipoBatida.ENTRADA else TipoBatida.SAIDA
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BotaoPonto("ENTRADA", cs.primary, proximo == TipoBatida.ENTRADA, !registrando) {
                    // NOVO V3: avisa ANTES de registrar uma 2ª entrada
                    if (estado.abertoDesde != null) confirmarSegundaEntrada = true
                    else baterPonto(TipoBatida.ENTRADA)
                }
                BotaoPonto("SAÍDA", VoithNavy, proximo == TipoBatida.SAIDA, !registrando) { baterPonto(TipoBatida.SAIDA) }
            }
            MensagemAnimada(mensagem)

            if (gpsAtivo && !temPermissao) {
                AvisoPermissao {
                    pedirPermissao.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }
            }

            TituloSecao("Últimos registros")
            Column(Modifier.fillMaxWidth().animateContentSize(tween(300))) {
                if (recentes.isEmpty()) {
                    Text(
                        "Nenhum registro ainda. Toque em ENTRADA para começar.",
                        color = cs.onBackground.copy(alpha = 0.6f), fontSize = 14.sp
                    )
                }
                recentes.forEach { ItemBatida(it) }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Pontos Voith $VERSAO_APP • dados salvos somente neste celular",
                fontSize = 11.sp, color = cs.onBackground.copy(alpha = 0.45f), textAlign = TextAlign.Center
            )
        }
    }

    // ------------------------------------------------ aviso de 2ª entrada
    val aberta = estado.abertoDesde
    if (confirmarSegundaEntrada && aberta != null) {
        AlertDialog(
            onDismissRequest = { confirmarSegundaEntrada = false },
            icon = { Text("!", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = CorAviso) },
            title = { Text("Já existe uma entrada aberta") },
            text = {
                Text(
                    "Há uma ENTRADA aguardando a SAÍDA desde " +
                        "${aberta.dataHora.formatar(FMT_HM)} de ${aberta.dataHora.formatar(FMT_DIA)} " +
                        "(há ${duracaoTexto(agora - aberta.dataHora)}).\n\n" +
                        "Se registrar outra entrada, a anterior ficará como turno SEM SAÍDA."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmarSegundaEntrada = false
                    baterPonto(TipoBatida.ENTRADA)
                }) { Text("Registrar mesmo assim", color = CorAviso) }
            },
            dismissButton = {
                TextButton(onClick = { confirmarSegundaEntrada = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun CartaoStatus(estado: EstadoTurno, agora: Long) {
    val cs = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface)
    ) {
        AnimatedContent(
            targetState = estado.abertoDesde,
            transitionSpec = { (fadeIn(tween(350)) + scaleIn(tween(350), initialScale = 0.96f)) togetherWith fadeOut(tween(200)) },
            label = "status"
        ) { aberta ->
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (aberta != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PontoPulsante(CorOk)
                        Spacer(Modifier.width(8.dp))
                        Text("TURNO EM ANDAMENTO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CorOk, letterSpacing = 1.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    ContadorAnimado(duracaoHms(agora - aberta.dataHora))
                    Text(
                        "Entrada às ${aberta.dataHora.formatar(FMT_HM)} de ${aberta.dataHora.formatar(FMT_DIA_CURTO)}",
                        fontSize = 13.sp, color = cs.onSurface.copy(alpha = 0.7f)
                    )
                } else {
                    Text("SEM TURNO ABERTO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cs.onSurface.copy(alpha = 0.6f), letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    val t = estado.ultimoCompleto
                    val ini = t?.entrada
                    val fim = t?.saida
                    if (t != null && ini != null && fim != null) {
                        Text("Último turno: ${duracaoTexto(t.duracaoMs)}", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                        Text(
                            "${ini.dataHora.formatar(FMT_DIA_CURTO)} ${ini.dataHora.formatar(FMT_HM)}  →  " +
                                "${fim.dataHora.formatar(FMT_DIA_CURTO)} ${fim.dataHora.formatar(FMT_HM)}",
                            fontSize = 13.sp, color = cs.onSurface.copy(alpha = 0.7f)
                        )
                    } else {
                        Text("Nenhum turno concluído ainda", fontSize = 15.sp, color = cs.onSurface.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AvisoPermissao(onPermitir: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    CartaoBase {
        Text("Localização desativada", fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        Text(
            "Os pontos são salvos normalmente, mas sem GPS. Se o botão não abrir o pedido, ative em " +
                "Ajustes > Abrir permissões do app.",
            fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.7f)
        )
        TextButton(onClick = onPermitir) { Text("Permitir localização") }
    }
}
