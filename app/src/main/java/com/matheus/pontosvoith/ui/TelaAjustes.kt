package com.matheus.pontosvoith.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Patterns
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matheus.pontosvoith.VERSAO_APP
import com.matheus.pontosvoith.dados.Batida
import com.matheus.pontosvoith.dados.Localizacao
import com.matheus.pontosvoith.dados.Preferencias
import com.matheus.pontosvoith.ui.theme.CorAviso
import com.matheus.pontosvoith.ui.theme.CorOk

@Composable
fun TelaAjustes(escuro: Boolean, onTema: (Boolean) -> Unit, todas: List<Batida>) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var gps by remember { mutableStateOf(Preferencias.gpsAtivo(ctx)) }
    var email by remember { mutableStateOf(Preferencias.email(ctx)) }
    var mensagem by rememberMensagem()
    val temPermissao = remember { Localizacao.temPermissao(ctx) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text("Ajustes", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.primary)

        TituloSecao("Aparência")
        CartaoBase {
            LinhaSwitch("Tema escuro", "Azul e preto", escuro) { onTema(it) }
        }

        TituloSecao("Localização")
        CartaoBase {
            LinhaSwitch("Registrar GPS", "Salva as coordenadas em cada batida (funciona offline)", gps) {
                gps = it
                Preferencias.salvarGps(ctx, it)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (temPermissao) "Permissão de localização: concedida" else "Permissão de localização: não concedida",
                fontSize = 12.sp, color = if (temPermissao) CorOk else CorAviso
            )
            TextButton(onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) { Text("Abrir permissões do app") }
        }

        TituloSecao("E-mail padrão")
        CartaoBase {
            Text("Usado para enviar relatórios e backups (disponível na próxima versão).", fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.7f))
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = email, onValueChange = { email = it.trim() },
                label = { Text("seu@email.com") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = {
                if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    mensagem = Mensagem(TipoMensagem.ERRO, "E-mail inválido")
                } else {
                    Preferencias.salvarEmail(ctx, email)
                    mensagem = Mensagem(TipoMensagem.OK, "E-mail salvo")
                }
            }) { Text("Salvar e-mail") }
        }
        MensagemAnimada(mensagem)

        TituloSecao("Exportação e backup")
        CartaoBase {
            Text(
                "Excel, PDF, backup e restauração chegam na Entrega 4.",
                fontSize = 13.sp, color = cs.onSurface.copy(alpha = 0.7f)
            )
        }

        TituloSecao("Sobre")
        CartaoBase {
            LinhaResumo("Versão") { Text(VERSAO_APP, fontWeight = FontWeight.SemiBold, color = cs.onSurface) }
            LinhaResumo("Registros salvos") { Text("${todas.size}", fontWeight = FontWeight.SemiBold, color = cs.onSurface) }
            Text("Dados armazenados somente neste celular.", fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.6f))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LinhaSwitch(titulo: String, subtitulo: String, valor: Boolean, onMudar: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, color = cs.onSurface, fontWeight = FontWeight.Medium)
            Text(subtitulo, fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.65f))
        }
        Switch(checked = valor, onCheckedChange = onMudar)
    }
}
