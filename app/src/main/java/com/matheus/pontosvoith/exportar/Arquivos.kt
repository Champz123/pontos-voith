package com.matheus.pontosvoith.exportar

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object Mime {
    const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val PDF = "application/pdf"
    const val JSON = "application/json"
}

object Arquivos {

    fun salvarEmUri(ctx: Context, uri: Uri, bytes: ByteArray) {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: throw IllegalStateException("Não foi possível gravar o arquivo.")
    }

    fun lerUri(ctx: Context, uri: Uri): ByteArray =
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Não foi possível ler o arquivo.")

    /** Grava em cache e abre o app de e-mail (ou outro) com o anexo. */
    fun enviar(ctx: Context, nome: String, mime: String, bytes: ByteArray, email: String, assunto: String, corpo: String) {
        val dir = File(ctx.cacheDir, "exportacoes").apply { mkdirs() }
        dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 86_400_000L) it.delete() }
        val arq = File(dir, nome)
        arq.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.arquivos", arq)
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, assunto)
            putExtra(Intent.EXTRA_TEXT, corpo)
            if (email.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(envio, "Enviar com…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    }
}
