package com.matheus.pontosvoith.dados

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.matheus.pontosvoith.exportar.BackupAuto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Repositorio {
    /** Tempo para desfazer a última batida. */
    const val JANELA_DESFAZER_MS = 10_000L
    private const val LIXEIRA_MS = 30L * 24 * 3600 * 1000
    private const val ATIVOS = "excluido_em IS NULL"

    private lateinit var app: Context
    private lateinit var db: PontoDb

    /** Escopo do app: a captura do GPS continua mesmo se a tela girar/fechar. */
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _versao = MutableStateFlow(0)
    /** Muda a cada alteração no banco: as telas, o widget e o bloco recarregam ao observar. */
    val versao: StateFlow<Int> = _versao

    fun init(ctx: Context) {
        app = ctx.applicationContext
        db = PontoDb(app)
        escopo.launch {
            val w = db.writableDatabase
            val cv = ContentValues().apply { put("status_local", StatusLocal.INDISPONIVEL) }
            w.update(
                "batidas", cv, "status_local = ? AND data_hora < ?",
                arrayOf(StatusLocal.PENDENTE, (System.currentTimeMillis() - 120_000).toString())
            )
            // Lixeira: apaga de vez o que está lá há mais de 30 dias
            val apagados = w.delete(
                "batidas", "excluido_em IS NOT NULL AND excluido_em < ?",
                arrayOf((System.currentTimeMillis() - LIXEIRA_MS).toString())
            )
            if (apagados > 0) notificar()
            preencherEnderecosPendentes()
            runCatching { BackupAuto.executarSeNecessario(app) }
        }
    }

    fun notificar() = _versao.update { it + 1 }

    // ---------------------------------------------------------------- registrar

    /**
     * Registra uma batida agora.
     * - SAÍDA usa a caixa "Almoço de 1h" e depois a desmarca.
     * - capturarLocalAqui=false: o chamador (widget) inicia a captura em primeiro plano.
     */
    suspend fun registrar(tipo: String, capturarLocalAqui: Boolean = true): Batida = withContext(Dispatchers.IO) {
        val agora = System.currentTimeMillis()
        val gpsLigado = Preferencias.gpsAtivo(app)
        val comPermissao = Localizacao.temPermissao(app)
        val status = when {
            !gpsLigado -> StatusLocal.DESATIVADO
            !comPermissao -> StatusLocal.SEM_PERMISSAO
            else -> StatusLocal.PENDENTE
        }
        val almoco = tipo == TipoBatida.SAIDA && Preferencias.almoco(app)
        val cv = ContentValues().apply {
            put("tipo", tipo)
            put("data_hora", agora)
            put("status_local", status)
            put("origem", Origem.NORMAL)
            put("ajustado", 0)
            put("almoco", if (almoco) 1 else 0)
            put("criado_em", agora)
        }
        val id = db.writableDatabase.insertOrThrow("batidas", null, cv)
        if (tipo == TipoBatida.SAIDA && almoco) Preferencias.salvarAlmoco(app, false)
        Preferencias.salvarUltimoRegistro(app, id, agora)
        notificar()
        if (status == StatusLocal.PENDENTE && capturarLocalAqui) escopo.launch { capturarLocal(id) }
        escopo.launch { runCatching { BackupAuto.executarSeNecessario(app) } }
        buscar(id) ?: error("Registro não encontrado após gravar")
    }

    /**
     * Desfaz (apaga de vez, sem lixeira) uma batida recém-registrada.
     * Só funciona dentro da janela de desfazer. Retorna a batida desfeita ou null.
     */
    suspend fun desfazer(id: Long): Batida? = withContext(Dispatchers.IO) {
        val b = buscar(id) ?: return@withContext null
        if (b.excluidoEm != null) return@withContext null
        if (System.currentTimeMillis() - b.criadoEm > JANELA_DESFAZER_MS + 5_000) return@withContext null
        db.writableDatabase.delete("batidas", "id = ?", arrayOf(id.toString()))
        // Se era uma saída com almoço, volta a marcar a caixa
        if (b.tipo == TipoBatida.SAIDA && b.almoco) Preferencias.salvarAlmoco(app, true)
        if (Preferencias.ultimoRegistro(app).first == id) Preferencias.limparUltimoRegistro(app)
        notificar()
        b
    }

    /** Inclusão manual (retroativa): fica marcada como RETROATIVA no histórico. */
    suspend fun incluirManual(tipo: String, dataHora: Long, obs: String?, almoco: Boolean = false) = withContext(Dispatchers.IO) {
        require(dataHora <= System.currentTimeMillis()) { "Não é possível registrar no futuro" }
        val cv = ContentValues().apply {
            put("tipo", tipo)
            put("data_hora", dataHora)
            put("status_local", StatusLocal.MANUAL)
            put("origem", Origem.RETROATIVA)
            put("ajustado", 0)
            put("almoco", if (tipo == TipoBatida.SAIDA && almoco) 1 else 0)
            put("obs_ajuste", obs?.trim()?.takeIf { it.isNotEmpty() })
            put("criado_em", System.currentTimeMillis())
        }
        db.writableDatabase.insertOrThrow("batidas", null, cv)
        notificar()
    }

    /** Edita uma batida e a marca como "ajustada" (guarda o horário original). */
    suspend fun editar(id: Long, tipo: String, dataHora: Long, obs: String?, almoco: Boolean): Boolean = withContext(Dispatchers.IO) {
        require(dataHora <= System.currentTimeMillis()) { "Não é possível registrar no futuro" }
        val atual = buscar(id) ?: return@withContext false
        val obsLimpa = obs?.trim()?.takeIf { it.isNotEmpty() }
        val almocoFinal = tipo == TipoBatida.SAIDA && almoco
        val mudouAlmoco = atual.almoco != almocoFinal
        if (atual.tipo == tipo && atual.dataHora == dataHora && atual.obsAjuste == obsLimpa && !mudouAlmoco) {
            return@withContext false
        }

        val notas = ArrayList<String>()
        obsLimpa?.let { notas += it }
        if (atual.tipo != tipo) notas += "Tipo alterado de ${nomeTipo(atual.tipo)} para ${nomeTipo(tipo)}"
        if (mudouAlmoco) notas += if (almocoFinal) "Almoço 1h marcado" else "Almoço 1h desmarcado"
        val obsFinal = notas.joinToString(" • ").takeIf { it.isNotEmpty() }

        val cv = ContentValues().apply {
            put("tipo", tipo)
            put("data_hora", dataHora)
            put("almoco", if (almocoFinal) 1 else 0)
            put("obs_ajuste", obsFinal)
            if (!atual.retroativa && (atual.dataHora != dataHora || atual.tipo != tipo || mudouAlmoco)) {
                put("ajustado", 1)
                put("data_ajuste", System.currentTimeMillis())
                if (atual.valorOriginal == null && atual.dataHora != dataHora) put("valor_original", atual.dataHora)
            }
        }
        db.writableDatabase.update("batidas", cv, "id = ?", arrayOf(id.toString()))
        notificar()
        true
    }

    // ---------------------------------------------------------------- lixeira

    suspend fun excluir(id: Long) = excluirVarios(listOf(id))

    /** Move para a Lixeira (pode ser restaurado por 30 dias). Retorna a quantidade. */
    suspend fun excluirVarios(ids: Collection<Long>): Int = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext 0
        val w = db.writableDatabase
        val cv = ContentValues().apply { put("excluido_em", System.currentTimeMillis()) }
        var n = 0
        w.beginTransaction()
        try {
            ids.forEach { n += w.update("batidas", cv, "id = ? AND $ATIVOS", arrayOf(it.toString())) }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        notificar()
        n
    }

    suspend fun lixeira(): List<Batida> = withContext(Dispatchers.IO) {
        db.readableDatabase.query("batidas", null, "excluido_em IS NOT NULL", null, null, null, "excluido_em DESC, data_hora DESC")
            .use { c -> buildList { while (c.moveToNext()) add(c.toBatida()) } }
    }

    suspend fun restaurarDaLixeira(ids: Collection<Long>): Int = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext 0
        val w = db.writableDatabase
        val cv = ContentValues().apply { putNull("excluido_em") }
        var n = 0
        w.beginTransaction()
        try {
            ids.forEach { n += w.update("batidas", cv, "id = ? AND excluido_em IS NOT NULL", arrayOf(it.toString())) }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        notificar()
        n
    }

    suspend fun esvaziarLixeira(): Int = withContext(Dispatchers.IO) {
        val n = db.writableDatabase.delete("batidas", "excluido_em IS NOT NULL", null)
        notificar()
        n
    }

    // ---------------------------------------------------------------- backup

    /**
     * Restaura batidas de um backup. Retorna (inseridos, ignorados).
     * substituir=true: os registros atuais vão para a Lixeira (não são apagados de vez).
     */
    suspend fun restaurar(lista: List<Batida>, substituir: Boolean): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        var ins = 0
        var ign = 0
        w.beginTransaction()
        try {
            if (substituir) {
                val cv = ContentValues().apply { put("excluido_em", System.currentTimeMillis()) }
                w.update("batidas", cv, ATIVOS, null)
            }
            val existentes = HashSet<String>()
            w.query("batidas", arrayOf("tipo", "data_hora"), ATIVOS, null, null, null, null).use { c ->
                while (c.moveToNext()) existentes += "${c.getString(0)}|${c.getLong(1)}"
            }
            lista.forEach { b ->
                val chave = "${b.tipo}|${b.dataHora}"
                if (chave in existentes) {
                    ign++
                } else {
                    existentes += chave
                    val cv = ContentValues().apply {
                        put("tipo", b.tipo)
                        put("data_hora", b.dataHora)
                        if (b.latitude != null) put("latitude", b.latitude)
                        if (b.longitude != null) put("longitude", b.longitude)
                        if (b.precisao != null) put("precisao", b.precisao)
                        if (b.endereco != null) put("endereco", b.endereco)
                        put("status_local", if (b.statusLocal == StatusLocal.PENDENTE) StatusLocal.INDISPONIVEL else b.statusLocal)
                        put("origem", b.origem)
                        put("ajustado", if (b.ajustado) 1 else 0)
                        put("almoco", if (b.tipo == TipoBatida.SAIDA && b.almoco) 1 else 0)
                        if (b.dataAjuste != null) put("data_ajuste", b.dataAjuste)
                        if (b.valorOriginal != null) put("valor_original", b.valorOriginal)
                        if (b.obsAjuste != null) put("obs_ajuste", b.obsAjuste)
                        put("criado_em", b.criadoEm)
                    }
                    w.insertOrThrow("batidas", null, cv)
                    ins++
                }
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        notificar()
        ins to ign
    }

    // ---------------------------------------------------------------- localização

    /** Captura a localização de uma batida (usado pelo app e pelo serviço do widget). */
    suspend fun capturarLocal(id: Long) {
        val loc = runCatching { Localizacao.obter(app) }.getOrNull()
        val cv = ContentValues()
        if (loc == null) {
            cv.put("status_local", StatusLocal.INDISPONIVEL)
        } else {
            cv.put("latitude", loc.latitude)
            cv.put("longitude", loc.longitude)
            cv.put("precisao", loc.accuracy.toDouble())
            cv.put("status_local", StatusLocal.OK)
        }
        withContext(Dispatchers.IO) {
            db.writableDatabase.update("batidas", cv, "id = ?", arrayOf(id.toString()))
        }
        notificar()
        if (loc != null) withContext(Dispatchers.IO) { preencherEndereco(id, loc.latitude, loc.longitude) }
    }

    /** Alternativa quando o serviço em primeiro plano não pode ser iniciado. */
    fun capturarLocalEmSegundoPlano(id: Long) {
        escopo.launch { capturarLocal(id) }
    }

    private fun preencherEndereco(id: Long, lat: Double, lon: Double) {
        val end = Localizacao.endereco(app, lat, lon) ?: return
        val cv = ContentValues().apply { put("endereco", end) }
        db.writableDatabase.update("batidas", cv, "id = ?", arrayOf(id.toString()))
        notificar()
    }

    fun preencherEnderecosPendentes() {
        escopo.launch {
            val pend = mutableListOf<Triple<Long, Double, Double>>()
            db.readableDatabase.query(
                "batidas", arrayOf("id", "latitude", "longitude"),
                "latitude IS NOT NULL AND endereco IS NULL AND $ATIVOS", null, null, null, "id DESC", "10"
            ).use { c -> while (c.moveToNext()) pend += Triple(c.getLong(0), c.getDouble(1), c.getDouble(2)) }
            pend.forEach { (id, lat, lon) -> preencherEndereco(id, lat, lon) }
        }
    }

    // ---------------------------------------------------------------- consultas

    /** Todas as batidas ativas (fora da Lixeira), em ordem cronológica. */
    suspend fun todas(): List<Batida> = withContext(Dispatchers.IO) {
        db.readableDatabase.query("batidas", null, ATIVOS, null, null, null, "data_hora ASC, id ASC")
            .use { c -> buildList { while (c.moveToNext()) add(c.toBatida()) } }
    }

    private fun buscar(id: Long): Batida? =
        db.readableDatabase.query("batidas", null, "id = ?", arrayOf(id.toString()), null, null, null)
            .use { c -> if (c.moveToFirst()) c.toBatida() else null }

    private fun Cursor.toBatida(): Batida {
        fun dbl(col: String): Double? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getDouble(it) }
        fun lng(col: String): Long? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getLong(it) }
        fun str(col: String): String? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getString(it) }
        return Batida(
            id = getLong(getColumnIndexOrThrow("id")),
            tipo = getString(getColumnIndexOrThrow("tipo")),
            dataHora = getLong(getColumnIndexOrThrow("data_hora")),
            latitude = dbl("latitude"),
            longitude = dbl("longitude"),
            precisao = dbl("precisao"),
            endereco = str("endereco"),
            statusLocal = getString(getColumnIndexOrThrow("status_local")),
            origem = getString(getColumnIndexOrThrow("origem")),
            ajustado = getInt(getColumnIndexOrThrow("ajustado")) == 1,
            dataAjuste = lng("data_ajuste"),
            valorOriginal = lng("valor_original"),
            obsAjuste = str("obs_ajuste"),
            criadoEm = lng("criado_em") ?: 0L,
            almoco = getInt(getColumnIndexOrThrow("almoco")) == 1,
            excluidoEm = lng("excluido_em")
        )
    }
}
