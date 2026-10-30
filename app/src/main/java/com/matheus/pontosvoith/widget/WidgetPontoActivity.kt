package com.matheus.pontosvoith.widget

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Toast
import com.matheus.pontosvoith.dados.Repositorio
import com.matheus.pontosvoith.dados.StatusLocal
import com.matheus.pontosvoith.dados.TipoBatida
import com.matheus.pontosvoith.dados.calcularEstado
import com.matheus.pontosvoith.dados.montarTurnos
import com.matheus.pontosvoith.dados.nomeTipo
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Tela invisível usada pelo widget, pelos atalhos do ícone e pelo bloco das Configurações rápidas.
 * - Registro normal: grava na hora, inicia a captura de GPS em primeiro plano e mostra confirmação.
 * - 2ª entrada ou saída sem entrada: mostra o MESMO aviso do app antes de gravar.
 * - Desfazer: apaga a última batida (dentro de 10 s).
 */
class WidgetPontoActivity : Activity() {

    companion object {
        const val EXTRA_TIPO = "tipo"
        const val EXTRA_DESFAZER_ID = "desfazer_id"
        private val FMT_HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val FMT_HMS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        private val FMT_DIA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private fun Long.fmt(f: DateTimeFormatter): String =
            Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(f)
    }

    private val escopo = MainScope()
    private var finalizado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Desfazer
        val desfazerId = intent?.getLongExtra(EXTRA_DESFAZER_ID, -1L) ?: -1L
        if (desfazerId > 0) {
            escopo.launch {
                val r = runCatching { Repositorio.desfazer(desfazerId) }.getOrNull()
                aviso(
                    if (r != null) "↶ ${nomeTipo(r.tipo)} das ${r.dataHora.fmt(FMT_HMS)} desfeita"
                    else "Tempo para desfazer esgotado"
                )
                fechar()
            }
            return
        }

        val tipo: String = intent?.getStringExtra(EXTRA_TIPO)
            ?.takeIf { it == TipoBatida.ENTRADA || it == TipoBatida.SAIDA }
            ?: run { fechar(); return }

        escopo.launch {
            try {
                val todas = Repositorio.todas()
                val estado = calcularEstado(montarTurnos(todas), todas)
                val aberta = estado.abertoDesde
                when {
                    tipo == TipoBatida.ENTRADA && aberta != null -> confirmar(
                        "Já existe uma entrada aberta",
                        "Há uma ENTRADA aguardando a SAÍDA desde ${aberta.dataHora.fmt(FMT_HM)} de " +
                            "${aberta.dataHora.fmt(FMT_DIA)}.\n\nSe registrar outra entrada, a anterior ficará como turno SEM SAÍDA.",
                        tipo
                    )
                    tipo == TipoBatida.SAIDA && aberta == null -> {
                        val ult = estado.ultimaBatida
                        confirmar(
                            "Não há entrada aberta",
                            "Não existe uma ENTRADA aguardando saída." +
                                (if (ult != null) "\n\nÚltimo registro: ${nomeTipo(ult.tipo).uppercase()} às " +
                                    "${ult.dataHora.fmt(FMT_HM)} de ${ult.dataHora.fmt(FMT_DIA)}." else "") +
                                "\n\nSe registrar, esta saída ficará como SAÍDA SEM ENTRADA.",
                            tipo
                        )
                    }
                    else -> registrar(tipo)
                }
            } catch (e: Exception) {
                aviso("Falha ao registrar: ${e.message ?: "erro"}")
                fechar()
            }
        }
    }

    private fun confirmar(titulo: String, texto: String, tipo: String) {
        var confirmou = false
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("⚠ $titulo")
            .setMessage(texto)
            .setPositiveButton("Registrar mesmo assim") { _, _ ->
                confirmou = true
                escopo.launch { registrar(tipo) }
            }
            .setNegativeButton("Cancelar", null)
            .setOnDismissListener { if (!confirmou) fechar() }
            .show()
    }

    private suspend fun registrar(tipo: String) {
        try {
            val b = Repositorio.registrar(tipo, capturarLocalAqui = false)
            // GPS confiável: captura em primeiro plano enquanto esta tela ainda está visível
            if (b.statusLocal == StatusLocal.PENDENTE) CapturaLocalServico.iniciar(this, b.id)
            val extra = if (b.tipo == TipoBatida.SAIDA && b.almoco) " (−1h almoço)" else ""
            aviso("✓ ${nomeTipo(tipo)} registrada às ${b.dataHora.fmt(FMT_HMS)}$extra")
        } catch (e: Exception) {
            aviso("✕ Falha ao registrar: ${e.message ?: "erro"}")
        } finally {
            fechar()
        }
    }

    private fun aviso(texto: String) {
        Toast.makeText(applicationContext, texto, Toast.LENGTH_SHORT).show()
    }

    private fun fechar() {
        if (finalizado) return
        finalizado = true
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        escopo.cancel()
        super.onDestroy()
    }
}
