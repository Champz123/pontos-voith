package com.matheus.pontosvoith.exportar

import android.content.Context
import com.matheus.pontosvoith.dados.Batida
import com.matheus.pontosvoith.dados.Origem
import com.matheus.pontosvoith.dados.Preferencias
import com.matheus.pontosvoith.dados.StatusLocal
import com.matheus.pontosvoith.dados.TipoBatida
import org.json.JSONArray
import org.json.JSONObject

/** Backup completo em JSON (registros + ajustes), para trocar de celular. */
object Backup {
    private const val ASSINATURA = "PontosVoith"
    private const val FORMATO = 1

    fun gerar(ctx: Context, batidas: List<Batida>, versaoApp: String): ByteArray {
        val raiz = JSONObject()
        raiz.put("app", ASSINATURA)
        raiz.put("formato", FORMATO)
        raiz.put("versao_app", versaoApp)
        raiz.put("gerado_em", System.currentTimeMillis())
        raiz.put("config", JSONObject().apply {
            Preferencias.temaEscuro(ctx)?.let { put("tema_escuro", it) }
            put("gps_ativo", Preferencias.gpsAtivo(ctx))
            put("email_padrao", Preferencias.email(ctx))
        })
        val arr = JSONArray()
        batidas.forEach { b ->
            arr.put(JSONObject().apply {
                put("tipo", b.tipo)
                put("data_hora", b.dataHora)
                b.latitude?.let { put("latitude", it) }
                b.longitude?.let { put("longitude", it) }
                b.precisao?.let { put("precisao", it) }
                b.endereco?.let { put("endereco", it) }
                put("status_local", b.statusLocal)
                put("origem", b.origem)
                put("ajustado", b.ajustado)
                b.dataAjuste?.let { put("data_ajuste", it) }
                b.valorOriginal?.let { put("valor_original", it) }
                b.obsAjuste?.let { put("obs_ajuste", it) }
                put("criado_em", b.criadoEm)
            })
        }
        raiz.put("batidas", arr)
        return raiz.toString(2).toByteArray(Charsets.UTF_8)
    }

    data class Conteudo(val batidas: List<Batida>, val geradoEm: Long, val versaoApp: String, val config: JSONObject?)

    /** Lê e valida um backup. Lança exceção com mensagem clara se for inválido. */
    fun ler(bytes: ByteArray): Conteudo {
        val raiz = try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw IllegalArgumentException("Arquivo não é um backup válido do Pontos Voith.")
        }
        if (raiz.optString("app") != ASSINATURA) throw IllegalArgumentException("Arquivo não é um backup do Pontos Voith.")
        if (raiz.optInt("formato", 0) > FORMATO) throw IllegalArgumentException("Backup gerado por uma versão mais nova do app.")
        val arr = raiz.optJSONArray("batidas") ?: JSONArray()
        val lista = ArrayList<Batida>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val tipo = o.getString("tipo")
            if (tipo != TipoBatida.ENTRADA && tipo != TipoBatida.SAIDA) continue
            lista += Batida(
                id = 0,
                tipo = tipo,
                dataHora = o.getLong("data_hora"),
                latitude = if (o.has("latitude")) o.getDouble("latitude") else null,
                longitude = if (o.has("longitude")) o.getDouble("longitude") else null,
                precisao = if (o.has("precisao")) o.getDouble("precisao") else null,
                endereco = if (o.has("endereco")) o.getString("endereco") else null,
                statusLocal = o.optString("status_local", StatusLocal.INDISPONIVEL),
                origem = o.optString("origem", Origem.NORMAL),
                ajustado = o.optBoolean("ajustado", false),
                dataAjuste = if (o.has("data_ajuste")) o.getLong("data_ajuste") else null,
                valorOriginal = if (o.has("valor_original")) o.getLong("valor_original") else null,
                obsAjuste = if (o.has("obs_ajuste")) o.getString("obs_ajuste") else null,
                criadoEm = o.optLong("criado_em", o.getLong("data_hora"))
            )
        }
        return Conteudo(lista, raiz.optLong("gerado_em", 0L), raiz.optString("versao_app", "?"), raiz.optJSONObject("config"))
    }

    fun aplicarConfig(ctx: Context, cfg: JSONObject?) {
        if (cfg == null) return
        if (cfg.has("tema_escuro")) Preferencias.salvarTema(ctx, cfg.getBoolean("tema_escuro"))
        if (cfg.has("gps_ativo")) Preferencias.salvarGps(ctx, cfg.getBoolean("gps_ativo"))
        val email = cfg.optString("email_padrao", "")
        if (email.isNotBlank()) Preferencias.salvarEmail(ctx, email)
    }
}
