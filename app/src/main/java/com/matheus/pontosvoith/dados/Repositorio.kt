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
            // Capturas interrompidas (app fechado no meio) viram "indisponível"
            val cv = ContentValues().apply { put("status_local", StatusLocal.INDISPONIVEL) }
            db.writableDatabase.update(
                "batidas", cv, "status_local = ? AND data_hora < ?",
                arrayOf(StatusLocal.PENDENTE, (System.currentTimeMillis() - 120_000).toString())
            )
            preencherEnderecosPendentes()
        }
    }

    private fun notificar() = _versao.update { it + 1 }

    suspend fun registrar(tipo: String): Batida = withContext(Dispatchers.IO) {
        val agora = System.currentTimeMillis()
        val comPermissao = Localizacao.temPermissao(app)
        val cv = ContentValues().apply {
            put("tipo", tipo)
            put("data_hora", agora)
            put("status_local", if (comPermissao) StatusLocal.PENDENTE else StatusLocal.SEM_PERMISSAO)
            put("origem", "NORMAL")
            put("ajustado", 0)
            put("criado_em", agora)
        }
        // 1º grava o ponto (nunca espera o GPS)...
        val id = db.writableDatabase.insertOrThrow("batidas", null, cv)
        notificar()
        // 2º ...depois tenta a localização em segundo plano
        if (comPermissao) escopo.launch { capturarLocal(id) }
        buscar(id) ?: error("Registro não encontrado após gravar")
    }

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

    /** Tenta preencher endereços que ficaram vazios (registrados sem internet). */
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

    suspend fun ultimas(limite: Int): List<Batida> = withContext(Dispatchers.IO) {
        db.readableDatabase.query(
            "batidas", null, null, null, null, null, "data_hora DESC, id DESC", limite.toString()
        ).use { c -> buildList { while (c.moveToNext()) add(c.toBatida()) } }
    }

    private fun buscar(id: Long): Batida? =
        db.readableDatabase.query("batidas", null, "id = ?", arrayOf(id.toString()), null, null, null)
            .use { c -> if (c.moveToFirst()) c.toBatida() else null }

    private fun Cursor.toBatida(): Batida {
        fun dbl(col: String): Double? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getDouble(it) }
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
            ajustado = getInt(getColumnIndexOrThrow("ajustado")) == 1
        )
    }
}
