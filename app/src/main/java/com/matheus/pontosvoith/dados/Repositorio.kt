package com.matheus.pontosvoith.dados

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Repositorio {
    private lateinit var app: Context
    private lateinit var db: PontoDb

    /** Escopo do app: a captura do GPS continua mesmo se a tela girar/fechar. */
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _versao = MutableStateFlow(0)
    /** Muda a cada alteração no banco: as telas recarregam ao observar. */
    val versao: StateFlow<Int> = _versao

    fun init(ctx: Context) {
        app = ctx.applicationContext
        db = PontoDb(app)
        escopo.launch {
            val cv = ContentValues().apply { put("status_local", StatusLocal.INDISPONIVEL) }
            db.writableDatabase.update(
                "batidas", cv, "status_local = ? AND data_hora < ?",
                arrayOf(StatusLocal.PENDENTE, (System.currentTimeMillis() - 120_000).toString())
            )
            preencherEnderecosPendentes()
        }
    }

    private fun notificar() = _versao.update { it + 1 }

    // ---------------------------------------------------------------- registrar

    suspend fun registrar(tipo: String): Batida = withContext(Dispatchers.IO) {
        val agora = System.currentTimeMillis()
        val gpsLigado = Preferencias.gpsAtivo(app)
        val comPermissao = Localizacao.temPermissao(app)
        val status = when {
            !gpsLigado -> StatusLocal.DESATIVADO
            !comPermissao -> StatusLocal.SEM_PERMISSAO
            else -> StatusLocal.PENDENTE
        }
        val cv = ContentValues().apply {
            put("tipo", tipo)
            put("data_hora", agora)
            put("status_local", status)
            put("origem", Origem.NORMAL)
            put("ajustado", 0)
            put("criado_em", agora)
        }
        val id = db.writableDatabase.insertOrThrow("batidas", null, cv)
        notificar()
        if (status == StatusLocal.PENDENTE) escopo.launch { capturarLocal(id) }
        buscar(id) ?: error("Registro não encontrado após gravar")
    }

    /** Inclusão manual (retroativa): fica marcada como RETROATIVA no histórico. */
    suspend fun incluirManual(tipo: String, dataHora: Long, obs: String?) = withContext(Dispatchers.IO) {
        require(dataHora <= System.currentTimeMillis()) { "Não é possível registrar no futuro" }
        val cv = ContentValues().apply {
            put("tipo", tipo)
            put("data_hora", dataHora)
            put("status_local", StatusLocal.MANUAL)
            put("origem", Origem.RETROATIVA)
            put("ajustado", 0)
            put("obs_ajuste", obs?.trim()?.takeIf { it.isNotEmpty() })
            put("criado_em", System.currentTimeMillis())
        }
        db.writableDatabase.insertOrThrow("batidas", null, cv)
        notificar()
    }

    /** Edita uma batida e a marca como "ajustada" (guarda o horário original). */
    suspend fun editar(id: Long, tipo: String, dataHora: Long, obs: String?): Boolean = withContext(Dispatchers.IO) {
        require(dataHora <= System.currentTimeMillis()) { "Não é possível registrar no futuro" }
        val atual = buscar(id) ?: return@withContext false
        val obsLimpa = obs?.trim()?.takeIf { it.isNotEmpty() }
        if (atual.tipo == tipo && atual.dataHora == dataHora && atual.obsAjuste == obsLimpa) return@withContext false

        val obsFinal = if (atual.tipo != tipo) {
            listOfNotNull(obsLimpa, "Tipo alterado de ${nomeTipo(atual.tipo)} para ${nomeTipo(tipo)}").joinToString(" • ")
        } else obsLimpa

        val cv = ContentValues().apply {
            put("tipo", tipo)
            put("data_hora", dataHora)
            put("obs_ajuste", obsFinal)
            if (!atual.retroativa && (atual.dataHora != dataHora || atual.tipo != tipo)) {
                put("ajustado", 1)
                put("data_ajuste", System.currentTimeMillis())
                if (atual.valorOriginal == null) put("valor_original", atual.dataHora)
            }
        }
        db.writableDatabase.update("batidas", cv, "id = ?", arrayOf(id.toString()))
        notificar()
        true
    }

    suspend fun excluir(id: Long) = excluirVarios(listOf(id))

    /** Exclusão em lote (transação única). Retorna a quantidade excluída. */
    suspend fun excluirVarios(ids: Collection<Long>): Int = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext 0
        val w = db.writableDatabase
        var n = 0
        w.beginTransaction()
        try {
            ids.forEach { n += w.delete("batidas", "id = ?", arrayOf(it.toString())) }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        notificar()
        n
    }

    // ---------------------------------------------------------------- backup

    /** Restaura batidas de um backup. substituir=true apaga tudo antes. Retorna (inseridos, ignorados). */
    suspend fun restaurar(lista: List<Batida>, substituir: Boolean): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val w = db.writableDatabase
        var ins = 0
        var ign = 0
        w.beginTransaction()
        try {
            if (substituir) w.delete("batidas", null, null)
            val existentes = HashSet<String>()
            if (!substituir) {
                w.query("batidas", arrayOf("tipo", "data_hora"), null, null, null, null, null).use { c ->
                    while (c.moveToNext()) existentes += "${c.getString(0)}|${c.getLong(1)}"
                }
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

    private suspend fun capturarLocal(id: Long) {
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
        db.writableDatabase.update("batidas", cv, "id = ?", arrayOf(id.toString()))
        notificar()
        if (loc != null) preencherEndereco(id, loc.latitude, loc.longitude)
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
                "latitude IS NOT NULL AND endereco IS NULL", null, null, null, "id DESC", "10"
            ).use { c -> while (c.moveToNext()) pend += Triple(c.getLong(0), c.getDouble(1), c.getDouble(2)) }
            pend.forEach { (id, lat, lon) -> preencherEndereco(id, lat, lon) }
        }
    }

    // ---------------------------------------------------------------- consultas

    suspend fun todas(): List<Batida> = withContext(Dispatchers.IO) {
        db.readableDatabase.query("batidas", null, null, null, null, null, "data_hora ASC, id ASC")
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
            criadoEm = lng("criado_em") ?: 0L
        )
    }
}
