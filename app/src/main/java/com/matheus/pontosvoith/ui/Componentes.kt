package com.matheus.pontosvoith.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matheus.pontosvoith.ui.theme.CorAviso
import com.matheus.pontosvoith.ui.theme.CorErro
import com.matheus.pontosvoith.ui.theme.CorOk

/** Botão redondo de ponto: encolhe ao tocar e, se for o "próximo esperado", emite ondas. */
@Composable
fun BotaoPonto(texto: String, cor: Color, destaque: Boolean, habilitado: Boolean, onClick: () -> Unit) {
    val interacao = remember { MutableInteractionSource() }
    val pressionado by interacao.collectIsPressedAsState()
    val escala by animateFloatAsState(if (pressionado) 0.90f else 1f, tween(160), label = "escala")
    val onda = rememberInfiniteTransition(label = "onda")
    val progresso by onda.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "progresso"
    )
    Box(Modifier.size(156.dp), contentAlignment = Alignment.Center) {
        if (destaque) {
            Canvas(Modifier.matchParentSize()) {
                val raioMax = size.minDimension / 2f
                val raioBase = raioMax * 0.78f
                drawCircle(
                    color = cor.copy(alpha = (1f - progresso) * 0.35f),
                    radius = raioBase + (raioMax - raioBase) * progresso
                )
            }
        }
        Box(
            Modifier
                .size(122.dp)
                .scale(escala)
                .alpha(if (habilitado) 1f else 0.6f)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(lerp(cor, Color.White, 0.12f), cor, lerp(cor, Color.Black, 0.25f))))
                .clickable(interactionSource = interacao, indication = null, enabled = habilitado, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(texto, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun PontoPulsante(cor: Color = CorOk) {
    val t = rememberInfiniteTransition(label = "pulso")
    val a by t.animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a"
    )
    Box(Modifier.size(10.dp).alpha(a).clip(CircleShape).background(cor))
}

/** Contador cujos dígitos "rolam" ao mudar. */
@Composable
fun ContadorAnimado(texto: String, tamanho: TextUnit = 40.sp, cor: Color = MaterialTheme.colorScheme.onSurface) {
    Row {
        texto.forEachIndexed { i, ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    (slideInVertically(tween(260)) { it } + fadeIn(tween(260))) togetherWith
                        (slideOutVertically(tween(260)) { -it } + fadeOut(tween(200)))
                },
                label = "digito$i"
            ) { c ->
                Text(c.toString(), fontSize = tamanho, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = cor)
            }
        }
    }
}

/** Texto de horas que "conta" até o valor ao aparecer/mudar. */
@Composable
fun HorasAnimadas(ms: Long, tamanho: TextUnit = 22.sp, cor: Color = MaterialTheme.colorScheme.onSurface) {
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(ms) { anim.animateTo(ms.toFloat(), tween(900, easing = FastOutSlowInEasing)) }
    Text(duracaoTexto(anim.value.toLong()), fontSize = tamanho, fontWeight = FontWeight.SemiBold, color = cor)
}

@Composable
fun NumeroAnimado(valor: Int, tamanho: TextUnit = 22.sp, cor: Color = MaterialTheme.colorScheme.onSurface) {
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(valor) { anim.animateTo(valor.toFloat(), tween(700, easing = FastOutSlowInEasing)) }
    Text(anim.value.toInt().toString(), fontSize = tamanho, fontWeight = FontWeight.SemiBold, color = cor)
}

enum class TipoMensagem { OK, AVISO, ERRO }
data class Mensagem(val tipo: TipoMensagem, val texto: String, val id: Long = System.nanoTime())

/** Texto animado de confirmação (sucesso/aviso/erro). */
@Composable
fun MensagemAnimada(mensagem: Mensagem?) {
    AnimatedContent(
        targetState = mensagem,
        transitionSpec = { (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 }) togetherWith fadeOut(tween(200)) },
        label = "mensagem"
    ) { m ->
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            if (m != null) {
                val cor = when (m.tipo) {
                    TipoMensagem.OK -> CorOk
                    TipoMensagem.AVISO -> CorAviso
                    TipoMensagem.ERRO -> CorErro
                }
                val icone = when (m.tipo) {
                    TipoMensagem.OK -> "✓"
                    TipoMensagem.AVISO -> "!"
                    TipoMensagem.ERRO -> "✕"
                }
                Text("$icone  ${m.texto}", color = cor, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, fontSize = 15.sp)
            }
        }
    }
}

/** Mensagem que some sozinha após alguns segundos. */
@Composable
fun rememberMensagem(): MutableState<Mensagem?> {
    val estado = remember { mutableStateOf<Mensagem?>(null) }
    LaunchedEffect(estado.value) {
        if (estado.value != null) {
            kotlinx.coroutines.delay(3500)
            estado.value = null
        }
    }
    return estado
}

/** Seletor  ‹ texto ›  usado para dia e mês. */
@Composable
fun SeletorPeriodo(
    texto: String,
    onAnterior: () -> Unit,
    onProximo: () -> Unit,
    proximoHabilitado: Boolean = true,
    onTexto: (() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onAnterior) { Text("‹", fontSize = 30.sp, color = cs.primary) }
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .then(if (onTexto != null) Modifier.clickable { onTexto() } else Modifier)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(texto, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = cs.onBackground, textAlign = TextAlign.Center)
        }
        IconButton(onClick = onProximo, enabled = proximoHabilitado) {
            Text("›", fontSize = 30.sp, color = if (proximoHabilitado) cs.primary else cs.onBackground.copy(alpha = 0.25f))
        }
    }
}

@Composable
fun Selo(texto: String, cor: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(cor.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(texto, color = cor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TituloSecao(texto: String) {
    Text(
        texto, Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
fun CartaoBase(modifier: Modifier = Modifier, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), content = conteudo)
    }
}
