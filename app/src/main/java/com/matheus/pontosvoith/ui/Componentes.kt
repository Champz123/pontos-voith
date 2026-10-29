package com.matheus.pontosvoith.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
fun BotaoPonto(
    texto: String,
    cor: Color,
    destaque: Boolean,
    habilitado: Boolean,
    onClick: () -> Unit
) {
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

/** Ponto verde pulsando (indica turno em andamento). */
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

enum class TipoMensagem { OK, AVISO, ERRO }
data class Mensagem(val tipo: TipoMensagem, val texto: String, val id: Long = System.nanoTime())

/** Texto animado de confirmação (sucesso/aviso/erro) abaixo dos botões. */
@Composable
fun MensagemAnimada(mensagem: Mensagem?) {
    AnimatedContent(
        targetState = mensagem,
        transitionSpec = {
            (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 }) togetherWith fadeOut(tween(200))
        },
        label = "mensagem"
    ) { m ->
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
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
