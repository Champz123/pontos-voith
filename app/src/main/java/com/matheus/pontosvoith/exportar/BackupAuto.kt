package com.matheus.pontosvoith.exportar

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.matheus.pontosvoith.VERSAO_APP
import com.matheus.pontosvoith.dados.Preferencias
import com.matheus.pontosvoith.dados.Repositorio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * Backup automático semanal numa pasta escolhida pelo usuário.
 * Não usa serviço em segundo plano: é verificado ao abrir o app e a cada batida.
 * Se já se passaram 7 dias desde o último, gera um novo. Mantém os 8 mais recentes.
 */
object BackupAuto {
    const val INTERVALO_MS = 7L * 24 * 3600 * 1000
    private const val PREFIXO = "PontosVoith_auto_"
    private const val MANTER = 8
    private val trava = Mutex()

    fun pasta(ctx: Context): Uri? = Preferencias.pastaBackupAuto(ctx)?.let { Uri.parse(it) }

    /** Nome legível da pasta (ex.: "Documents/PontosVoith"). */
    fun nomePasta(ctx: Context): String? {
        val u = pasta(ctx) ?: return null
        val id = runCatching { DocumentsContract.getTreeDocumentId(u) }.getOrNull() ?: return null
        val caminho = id.substringAfter(':', id)
        return caminho.ifBlank { "Armazenamento interno (raiz)" }
    }

    suspend fun executarSeNecessario(ctx: Context): Boolean {
        if (!Preferencias.backupAutoAtivo(ctx)) return false
        if (pasta(ctx) == null) return false
        if (System.currentTimeMillis() - Preferencias.ultimoBackupAuto(ctx) < INTERVALO_MS) return false
        return executarAgora(ctx).isSuccess
    }

    /** Gera o backup agora. Sucesso = nome do arquivo criado. */
    suspend fun executarAgora(ctx: Context): Result<String> = withContext(Dispatchers.IO) {
        trava.withLock {
            val r = runCatching {
                val arvore = pasta(ctx) ?: error("Nenhuma pasta escolhida")
                val cr = ctx.contentResolver
                val docId = DocumentsContract.getTreeDocumentId(arvore)
                val pastaUri = DocumentsContract.buildDocumentUriUsingTree(arvore, docId)
                val nome = PREFIXO + LocalDateTime.now().format(F_ARQ_HORA) + ".json"
                val bytes = Backup.gerar(ctx, Repositorio.todas(), VERSAO_APP)
                val novo = DocumentsContract.createDocument(cr, pastaUri, "application/octet-stream", nome)
                    ?: error("Não foi possível criar o arquivo na pasta (permissão perdida? escolha a pasta de novo)")
                cr.openOutputStream(novo, "wt")?.use { it.write(bytes) }
                    ?: error("Não foi possível gravar o arquivo")
                runCatching { limparAntigos(cr, arvore, docId) }
                nome
            }
            if (r.isSuccess) {
                Preferencias.salvarUltimoBackupAuto(ctx, System.currentTimeMillis())
                Preferencias.salvarErroBackupAuto(ctx, null)
            } else {
                Preferencias.salvarErroBackupAuto(ctx, r.exceptionOrNull()?.message ?: "erro desconhecido")
            }
            r
        }
    }

    private fun limparAntigos(cr: ContentResolver, arvore: Uri, docId: String) {
        val filhos = DocumentsContract.buildChildDocumentsUriUsingTree(arvore, docId)
        val lista = ArrayList<Pair<String, String>>()
        cr.query(
            filhos,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val nome = c.getString(1) ?: continue
                if (nome.startsWith(PREFIXO)) lista += id to nome
            }
        }
        lista.sortedByDescending { it.second }.drop(MANTER).forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(arvore, id)) }
        }
    }
}
