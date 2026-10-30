package com.matheus.pontosvoith.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.matheus.pontosvoith.dados.Repositorio
import com.matheus.pontosvoith.dados.Turno
import com.matheus.pontosvoith.dados.paraData
import com.matheus.pontosvoith.exportar.Arquivos
import com.matheus.pontosvoith.exportar.Backup
import com.matheus.pontosvoith.exportar.BackupAuto
import com.matheus.pontosvoith.exportar.ExportarExcel
import com.matheus.pontosvoith.exportar.ExportarPdf
import com.matheus.pontosvoith.exportar.F_ARQ_HORA
import com.matheus.pontosvoith.exportar.Mime
import com.matheus.pontosvoith.exportar.Periodo
import com.matheus.pontosvoith.ui.theme.CorAviso
import com.matheus.pontosvoith.ui.theme.CorErro
import com.matheus.pontosvoith.ui.theme.CorOk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

private enum class TipoPeriodo(val rotulo: String) { MES("Mês"), PERSONALIZADO("Personalizado"), TUDO("Tudo") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaAjustes(escuro: Boolean, onTema: (Boolean) -> Unit, todas: List<Batida>, turnos: List<Turno>) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val escopo = rememberCoroutineScope()
    val versao by Repositorio.versao.collectAsState()
    var gps by remember { mutableStateOf(Preferencias.gpsAtivo(ctx)) }
    var email by remember { mutableStateOf(Preferencias.email(ctx)) }
    var ultimoBackup by remember { mutableLongStateOf(Preferencias.ultimoBackup(ctx)) }
    var mensagem by rememberMensagem()
    var ocupado by remember { mutableStateOf(false) }
    val temPermissao = remember { Localizacao.temPermissao(ctx) }

    // ---------------- V6: lixeira
    var lixeira by remember { mutableStateOf<List<Batida>>(emptyList()) }
    LaunchedEffect(versao) { lixeira = Repositorio.lixeira() }
    var mostrarLixeira by remember { mutableStateOf(false) }

    // ---------------- V6: backup automático
    var pastaAuto by remember { mutableStateOf(BackupAuto.nomePasta(ctx)) }
    var autoAtivo by remember { mutableStateOf(Preferencias.backupAutoAtivo(ctx)) }
    var ultimoAuto by remember { mutableLongStateOf(Preferencias.ultimoBackupAuto(ctx)) }
    var erroAuto by remember { mutableStateOf(Preferencias.erroBackupAuto(ctx)) }

    fun backupAutoAgora() {
        if (ocupado) return
        ocupado = true
        escopo.launch {
            val r = BackupAuto.executarAgora(ctx)
            ultimoAuto = Preferencias.ultimoBackupAuto(ctx)
            erroAuto = Preferencias.erroBackupAuto(ctx)
            mensagem = r.fold(
                { Mensagem(TipoMensagem.OK, "Backup salvo na pasta: $it") },
                { Mensagem(TipoMensagem.ERRO, "Falha no backup: ${it.message ?: "erro"}") }
            )
            ocupado = false
        }
    }

    val escolherPasta = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            Preferencias.salvarPastaBackupAuto(ctx, uri.toString())
            pastaAuto = BackupAuto.nomePasta(ctx)
            backupAutoAgora() // primeiro backup já na escolha da pasta
        }
    }

    // ---------------- período
    var tipoPeriodo by rememberSaveable { mutableStateOf(TipoPeriodo.MES) }
    var mes by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var de by rememberSaveable { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var ate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    val periodo: Periodo = remember(tipoPeriodo, mes, de, ate, todas) {
        when (tipoPeriodo) {
            TipoPeriodo.MES -> Periodo(mes.atDay(1), mes.atEndOfMonth(), mes.format(FMT_MES_ANO).capitalizar())
            TipoPeriodo.PERSONALIZADO -> {
                val i = minOf(de, ate)
                val f = maxOf(de, ate)
                Periodo(i, f, "${i.format(FMT_DIA)} a ${f.format(FMT_DIA)}")
            }
            TipoPeriodo.TUDO -> {
                val i = todas.minOfOrNull { it.dataHora }?.paraData() ?: LocalDate.now()
                val f = maxOf(LocalDate.now(), todas.maxOfOrNull { it.dataHora }?.paraData() ?: LocalDate.now())
                Periodo(i, f, "Todo o período (${i.format(FMT_DIA)} a ${f.format(FMT_DIA)})")
            }
        }
    }
    val turnosPeriodo = remember(periodo, turnos) { periodo.turnos(turnos) }
    val totalPeriodo = turnosPeriodo.filter { it.completo }.sumOf { it.duracaoMs }

    // ---------------- salvar (Storage Access Framework)
    var pendente by remember { mutableStateOf<ByteArray?>(null) }
    fun aoEscolherDestino(uri: Uri?) {
        val bytes = pendente
        pendente = null
        if (uri == null || bytes == null) return
        escopo.launch {
            mensagem = try {
                withContext(Dispatchers.IO) { Arquivos.salvarEmUri(ctx, uri, bytes) }
                Mensagem(TipoMensagem.OK, "Arquivo salvo")
            } catch (e: Exception) {
                Mensagem(TipoMensagem.ERRO, e.message ?: "Falha ao salvar")
            }
        }
    }
    val salvarXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Mime.XLSX)) { aoEscolherDestino(it) }
    val salvarPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Mime.PDF)) { aoEscolherDestino(it) }
    var salvandoBackup by remember { mutableStateOf(false) }
    val salvarJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Mime.JSON)) { uri ->
        val eraBackup = salvandoBackup
        salvandoBackup = false
        aoEscolherDestino(uri)
        if (uri != null && eraBackup) {
            val agora = System.currentTimeMillis()
            Preferencias.salvarUltimoBackup(ctx, agora); ultimoBackup = agora
        }
    }

    // ---------------- restaurar
    var backupLido by remember { mutableStateOf<Backup.Conteudo?>(null) }
    val abrirBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            escopo.launch {
                try {
                    backupLido = withContext(Dispatchers.IO) { Backup.ler(Arquivos.lerUri(ctx, uri)) }
                } catch (e: Exception) {
                    mensagem = Mensagem(TipoMensagem.ERRO, e.message ?: "Backup inválido")
                }
            }
        }
    }

    fun gerar(tipo: String, enviar: Boolean) {
        if (ocupado) return
        ocupado = true
        escopo.launch {
            try {
                val (bytes, nome, mime) = withContext(Dispatchers.Default) {
                    when (tipo) {
                        "xlsx" -> Triple(ExportarExcel.gerar(periodo, turnos, todas), "PontosVoith_${periodo.sufixoArquivo}.xlsx", Mime.XLSX)
                        "pdf" -> Triple(ExportarPdf.gerar(periodo, turnos, VERSAO_APP), "PontosVoith_${periodo.sufixoArquivo}.pdf", Mime.PDF)
                        else -> Triple(
                            Backup.gerar(ctx, todas, VERSAO_APP),
                            "PontosVoith_backup_${LocalDateTime.now().format(F_ARQ_HORA)}.json", Mime.JSON
                        )
                    }
                }
                if (enviar) {
                    val backup = tipo == "json"
                    Arquivos.enviar(
                        ctx, nome, mime, bytes, Preferencias.email(ctx),
                        assunto = if (backup) "Pontos Voith – Backup" else "Pontos Voith – Relatório ${periodo.descricao}",
                        corpo = if (backup) "Backup completo do Pontos Voith (${todas.size} registros)."
                        else "Relatório de horas trabalhadas.\nPeríodo: ${periodo.descricao}\nTotal: ${duracaoTexto(totalPeriodo)}"
                    )
                    if (backup) {
                        val agora = System.currentTimeMillis()
                        Preferencias.salvarUltimoBackup(ctx, agora); ultimoBackup = agora
                    }
                } else {
                    pendente = bytes
                    when (tipo) {
                        "xlsx" -> salvarXlsx.launch(nome)
                        "pdf" -> salvarPdf.launch(nome)
                        else -> { salvandoBackup = true; salvarJson.launch(nome) }
                    }
                }
            } catch (e: Exception) {
                mensagem = Mensagem(TipoMensagem.ERRO, "Falha ao gerar: ${e.message ?: "erro"}")
            } finally {
                ocupado = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text("Ajustes", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.primary)
        MensagemAnimada(mensagem)
        if (ocupado) LinearProgressIndicator(Modifier.fillMaxWidth())

        // ===================================================== EXPORTAR
        TituloSecao("Exportar relatório (Excel / PDF)")
        CartaoBase {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TipoPeriodo.entries.forEach { t ->
                    FilterChip(selected = tipoPeriodo == t, onClick = { tipoPeriodo = t }, label = { Text(t.rotulo) })
                }
            }
            AnimatedContent(
                targetState = tipoPeriodo,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
                label = "periodo"
            ) { t ->
                when (t) {
                    TipoPeriodo.MES -> SeletorPeriodo(
                        texto = mes.format(FMT_MES_ANO).capitalizar(),
                        onAnterior = { mes = mes.minusMonths(1) },
                        onProximo = { mes = mes.plusMonths(1) },
                        proximoHabilitado = mes.isBefore(YearMonth.now())
                    )
                    TipoPeriodo.PERSONALIZADO -> Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = {
                            DatePickerDialog(ctx, { _, a, m, d -> de = LocalDate.of(a, m + 1, d) },
                                de.year, de.monthValue - 1, de.dayOfMonth).show()
                        }, Modifier.weight(1f)) { Text("De ${de.format(FMT_DIA_CURTO)}/${de.year % 100}") }
                        OutlinedButton(onClick = {
                            DatePickerDialog(ctx, { _, a, m, d -> ate = LocalDate.of(a, m + 1, d) },
                                ate.year, ate.monthValue - 1, ate.dayOfMonth).show()
                        }, Modifier.weight(1f)) { Text("Até ${ate.format(FMT_DIA_CURTO)}/${ate.year % 100}") }
                    }
                    TipoPeriodo.TUDO -> Text(
                        periodo.descricao, Modifier.padding(vertical = 12.dp),
                        fontSize = 14.sp, color = cs.onSurface
                    )
                }
            }
            Text(
                "${turnosPeriodo.count { it.completo }} turno(s) completo(s) • ${duracaoTexto(totalPeriodo)}",
                fontSize = 13.sp, color = cs.onSurface.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(10.dp))
            LinhaAcoes("Excel", ocupado, { gerar("xlsx", false) }, { gerar("xlsx", true) })
            Spacer(Modifier.height(6.dp))
            LinhaAcoes("PDF", ocupado, { gerar("pdf", false) }, { gerar("pdf", true) })
        }

        // ===================================================== V6: BACKUP AUTOMÁTICO
        TituloSecao("Backup automático (semanal)")
        CartaoBase {
            Text(
                if (pastaAuto != null) "Pasta: $pastaAuto" else "Nenhuma pasta escolhida",
                fontWeight = FontWeight.Medium, color = if (pastaAuto != null) cs.onSurface else CorAviso
            )
            Text(
                if (ultimoAuto > 0) "Último automático: ${ultimoAuto.formatar(FMT_DIA)} às ${ultimoAuto.formatar(FMT_HM)}"
                else "Ainda não executado",
                fontSize = 13.sp, color = cs.onSurface.copy(alpha = 0.75f)
            )
            erroAuto?.let { Text("Último erro: $it", fontSize = 12.sp, color = CorErro) }
            Text(
                "Roda sozinho a cada 7 dias, quando você abre o app ou bate o ponto. Guarda os 8 mais recentes " +
                    "(arquivos PontosVoith_auto_…). Para proteger contra perda do celular, escolha uma pasta " +
                    "sincronizada com a nuvem ou um cartão SD.",
                fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.65f)
            )
            Spacer(Modifier.height(6.dp))
            LinhaSwitch("Ativado", "Gerar backup semanal automaticamente", autoAtivo) {
                autoAtivo = it
                Preferencias.salvarBackupAutoAtivo(ctx, it)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { escolherPasta.launch(null) }, Modifier.weight(1f), enabled = !ocupado) {
                    Text(if (pastaAuto == null) "Escolher pasta" else "Trocar pasta")
                }
                Button(onClick = { backupAutoAgora() }, Modifier.weight(1f), enabled = pastaAuto != null && !ocupado) {
                    Text("Fazer agora")
                }
            }
        }

        // ===================================================== BACKUP MANUAL
        TituloSecao("Backup manual (troca de celular)")
        CartaoBase {
            Text(
                if (ultimoBackup > 0) "Último backup manual: ${ultimoBackup.formatar(FMT_DIA)} às ${ultimoBackup.formatar(FMT_HM)}"
                else "Nenhum backup manual gerado ainda.",
                fontSize = 13.sp, color = if (ultimoBackup > 0) cs.onSurface.copy(alpha = 0.75f) else CorAviso
            )
            Text(
                "Inclui todos os ${todas.size} registros e os ajustes. Guarde o arquivo fora do celular (e-mail, Drive, PC).",
                fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.65f)
            )
            Spacer(Modifier.height(10.dp))
            LinhaAcoes("Backup", ocupado, { gerar("json", false) }, { gerar("json", true) })
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { abrirBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
                modifier = Modifier.fillMaxWidth(), enabled = !ocupado
            ) { Text("Restaurar backup…") }
        }

        // ===================================================== V6: LIXEIRA
        TituloSecao("Lixeira")
        CartaoBase {
            Text(
                if (lixeira.isEmpty()) "A lixeira está vazia."
                else "${lixeira.size} registro(s) excluído(s). São apagados de vez após 30 dias.",
                fontSize = 13.sp, color = cs.onSurface.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { mostrarLixeira = true }, Modifier.fillMaxWidth(), enabled = lixeira.isNotEmpty()) {
                Text("Abrir lixeira (${lixeira.size})")
            }
        }

        // ===================================================== E-MAIL
        TituloSecao("E-mail padrão")
        CartaoBase {
            Text("Preenchido automaticamente ao tocar em \"Enviar\".", fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.7f))
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

        // ===================================================== APARÊNCIA / GPS
        TituloSecao("Aparência")
        CartaoBase { LinhaSwitch("Tema escuro", "Azul e preto", escuro) { onTema(it) } }

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

        TituloSecao("Atalhos rápidos")
        CartaoBase {
            Text("• Widget: segure a tela inicial > Widgets > Pontos Voith.", fontSize = 13.sp, color = cs.onSurface)
            Text("• Ícone: segure o ícone do app > Entrada / Saída.", fontSize = 13.sp, color = cs.onSurface)
            Text("• Configurações rápidas: puxe a barra do topo > lápis (editar) > arraste \"Bater ponto\".", fontSize = 13.sp, color = cs.onSurface)
        }

        TituloSecao("Sobre")
        CartaoBase {
            LinhaResumo("Versão") { Text(VERSAO_APP, fontWeight = FontWeight.SemiBold, color = cs.onSurface) }
            LinhaResumo("Registros salvos") { Text("${todas.size}", fontWeight = FontWeight.SemiBold, color = cs.onSurface) }
            Text("Dados armazenados somente neste celular.", fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.6f))
        }
        Spacer(Modifier.height(16.dp))
    }

    // ===================================================== diálogos
    if (mostrarLixeira) {
        DialogoLixeira(
            itens = lixeira,
            onFechar = { mostrarLixeira = false },
            onRestaurar = { ids ->
                escopo.launch {
                    val n = Repositorio.restaurarDaLixeira(ids)
                    mensagem = Mensagem(TipoMensagem.OK, "$n registro(s) restaurado(s)")
                    if (n >= lixeira.size) mostrarLixeira = false
                }
            },
            onEsvaziar = {
                mostrarLixeira = false
                escopo.launch {
                    val n = Repositorio.esvaziarLixeira()
                    mensagem = Mensagem(TipoMensagem.OK, "$n registro(s) apagado(s) definitivamente")
                }
            }
        )
    }

    backupLido?.let { b ->
        DialogoRestaurar(
            conteudo = b,
            atuais = todas.size,
            onCancelar = { backupLido = null },
            onConfirmar = { substituir ->
                backupLido = null
                escopo.launch {
                    mensagem = try {
                        val (ins, ign) = Repositorio.restaurar(b.batidas, substituir)
                        Backup.aplicarConfig(ctx, b.config)
                        gps = Preferencias.gpsAtivo(ctx)
                        email = Preferencias.email(ctx)
                        Preferencias.temaEscuro(ctx)?.let { if (it != escuro) onTema(it) }
                        Mensagem(
                            TipoMensagem.OK,
                            "Backup restaurado: $ins registro(s)" + if (ign > 0) " • $ign duplicado(s) ignorado(s)" else ""
                        )
                    } catch (e: Exception) {
                        Mensagem(TipoMensagem.ERRO, "Falha ao restaurar: ${e.message ?: "erro"}")
                    }
                }
            }
        )
    }
}

@Composable
private fun LinhaAcoes(rotulo: String, ocupado: Boolean, onSalvar: () -> Unit, onEnviar: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(rotulo, Modifier.width(64.dp), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        OutlinedButton(onClick = onSalvar, enabled = !ocupado, modifier = Modifier.weight(1f)) { Text("Salvar") }
        Button(onClick = onEnviar, enabled = !ocupado, modifier = Modifier.weight(1f)) { Text("Enviar") }
    }
}

@Composable
private fun DialogoRestaurar(
    conteudo: Backup.Conteudo,
    atuais: Int,
    onCancelar: () -> Unit,
    onConfirmar: (substituir: Boolean) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var substituir by remember { mutableStateOf(false) }
    var entendi by remember { mutableStateOf(false) }
    val podeConfirmar = !substituir || entendi
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Restaurar backup") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val gerado = if (conteudo.geradoEm > 0)
                    "${conteudo.geradoEm.formatar(FMT_DIA)} às ${conteudo.geradoEm.formatar(FMT_HM)}" else "data desconhecida"
                Text("Backup de $gerado (app ${conteudo.versaoApp})", color = cs.onSurface)
                Text("${conteudo.batidas.size} registro(s) no arquivo • $atuais neste celular", fontSize = 13.sp, color = cs.onSurface.copy(alpha = 0.75f))
                Spacer(Modifier.height(12.dp))
                OpcaoRadio("Mesclar (recomendado)", "Adiciona apenas os registros que não existem aqui.", !substituir) {
                    substituir = false
                }
                OpcaoRadio("Substituir tudo", "Move os $atuais registros atuais para a Lixeira e usa só os do backup.", substituir) {
                    substituir = true
                }
                if (substituir) {
                    Spacer(Modifier.height(8.dp))
                    Text("⚠ Os registros atuais irão para a Lixeira (restauráveis por 30 dias).", fontSize = 13.sp, color = CorErro)
                    Row(Modifier.fillMaxWidth().clickable { entendi = !entendi }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = entendi, onCheckedChange = { entendi = it })
                        Text("Entendo e quero substituir", fontSize = 14.sp, color = cs.onSurface)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirmar(substituir) }, enabled = podeConfirmar) {
                Text(
                    if (substituir) "Substituir" else "Mesclar",
                    color = if (!podeConfirmar) cs.onSurface.copy(alpha = 0.38f) else if (substituir) CorErro else cs.primary
                )
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

@Composable
private fun OpcaoRadio(titulo: String, sub: String, marcado: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = marcado, onClick = onClick)
        Column {
            Text(titulo, color = cs.onSurface, fontWeight = FontWeight.Medium)
            Text(sub, fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.65f))
        }
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
