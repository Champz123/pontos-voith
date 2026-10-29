package com.matheus.pontosvoith.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matheus.pontosvoith.VERSAO_APP
import com.matheus.pontosvoith.dados.Batida
import com.matheus.pontosvoith.dados.EstadoTurno
import com.matheus.pontosvoith.dados.Localizacao
import com.matheus.pontosvoith.dados.Repositorio
import com.matheus.pontosvoith.dados.StatusLocal
import com.matheus.pontosvoith.dados.TipoBatida
import com.matheus.pontosvoith.dados.calcularEstado
import com.matheus.pontosvoith.ui.theme.CorOk
import com.matheus.pontosvoith.ui.theme.VoithNavy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TelaPrincipal(escuro: Boolean, onAlternarTema: () -> Unit) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    // Dados
    val versao by Repositorio.versao.collectAsState()
    var registros by remember { mutableStateOf<List<Batida>>(emptyList()) }
    LaunchedEffect(versao) { registros = Repositorio.ultimas(60) }
    val estado = remember(registros) { calcularEstado(registros) }

    // Relógio
    var agora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            agora = System.currentTimeMillis()
            delay(1000 - agora % 1000)
        }
    }

    // Mensagem animada (some após 3,5 s)
    var mensagem by remember { mutableStateOf<Mensagem?>(null) }
    LaunchedEffect(mensagem) {
        if (mensagem != null) { delay(3500); mensagem = null }
    }

    // Permissão de localização
    var temPermissao by remember { mutableStateOf(Localizacao.temPermissao(ctx)) }
    val pedirPermissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        temPermissao = Localizacao.temPermissao(ctx)
    }
    LaunchedEffect(Unit) {
        if (!temPermissao) pedirPermissao.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    var registrando by remember { mutableStateOf(false) }

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
                delay(1200) // evita toque duplo
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
            // Cabeçalho
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Pontos Voith", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.primary)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onAlternarTema) { Text(if (escuro) "☀  Claro" else "☾  Escuro") }
            }

            // Relógio
            Spacer(Modifier.height(8.dp))
            Text(agora.formatar(FMT_HORA), fontSize = 54.sp, fontWeight = FontWeight.Light, color = cs.onBackground)
            Text(
                agora.formatar(FMT_DATA_LONGA).replaceFirstChar { it.uppercase() },
                color = cs.onBackground.copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(16.dp))
            CartaoStatus(estado, agora)

            // Botões
            Spacer(Modifier.height(12.dp))
            val proximo = if (estado.abertoDesde == null) TipoBatida.ENTRADA else TipoBatida.SAIDA
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BotaoPonto("ENTRADA", cs.primary, proximo == TipoBatida.ENTRADA, !registrando) { baterPonto(TipoBatida.ENTRADA) }
                BotaoPonto("SAÍDA", VoithNavy, proximo == TipoBatida.SAIDA, !registrando) { baterPonto(TipoBatida.SAIDA) }
            }
            MensagemAnimada(mensagem)

            if (!temPermissao) {
                AvisoPermissao {
                    pedirPermissao.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // Últimos registros
            Text(
                "Últimos registros", Modifier.fillMaxWidth().padding(bottom = 6.dp),
                fontWeight = FontWeight.SemiBold, color = cs.onBackground
            )
            Column(Modifier.fillMaxWidth().animateContentSize(tween(300))) {
                if (registros.isEmpty()) {
                    Text(
                        "Nenhum registro ainda. Toque em ENTRADA para começar.",
                        color = cs.onBackground.copy(alpha = 0.6f), fontSize = 14.sp
                    )
                }
                registros.take(6).forEach { ItemBatida(it) }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Pontos Voith $VERSAO_APP • dados salvos somente neste celular",
                fontSize = 11.sp, color = cs.onBackground.copy(alpha = 0.45f), textAlign = TextAlign.Center
            )
        }
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
                Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 16.dp),
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
                    val dur = estado.ultimoDuracaoMs
                    val ini = estado.ultimoInicio
                    val fim = estado.ultimoFim
                    if (dur != null && ini != null && fim != null) {
                        Text("Último turno: ${duracaoTexto(dur)}", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
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
private fun ItemBatida(b: Batida) {
    val cs = MaterialTheme.colorScheme
    val entrada = b.tipo == TipoBatida.ENTRADA
    val corTipo = if (entrada) cs.primary else VoithNavy
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
                        "${b.dataHora.formatar(FMT_DIA)}  ${b.dataHora.formatar(FMT_HORA)}",
                        fontSize = 14.sp, color = cs.onSurface
                    )
                }
                val local = when (b.statusLocal) {
                    StatusLocal.PENDENTE -> "Obtendo localização…"
                    StatusLocal.SEM_PERMISSAO -> "Sem permissão de localização"
                    StatusLocal.INDISPONIVEL -> "Localização indisponível"
                    else -> if (b.latitude != null && b.longitude != null)
                        String.format(LOCALE_BR, "%.5f, %.5f  (±%.0f m)", b.latitude, b.longitude, b.precisao ?: 0.0)
                    else "Localização indisponível"
                }
                Text("📍 $local", fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.65f))
                b.endereco?.let {
                    Text(it, fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.65f), maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun AvisoPermissao(onPermitir: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Localização desativada", fontWeight = FontWeight.SemiBold, color = cs.onSurface)
            Text(
                "Os pontos são salvos normalmente, mas sem GPS. Se o botão não abrir o pedido, ative em " +
                    "Configurações > Apps > Pontos Voith > Permissões.",
                fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.7f)
            )
            TextButton(onClick = onPermitir) { Text("Permitir localização") }
        }
    }
}
