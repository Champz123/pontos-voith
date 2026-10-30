package com.matheus.pontosvoith.atalhos

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.matheus.pontosvoith.dados.Repositorio
import com.matheus.pontosvoith.dados.TipoBatida
import com.matheus.pontosvoith.dados.calcularEstado
import com.matheus.pontosvoith.dados.montarTurnos
import com.matheus.pontosvoith.widget.WidgetPontoActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * V6: bloco "Bater ponto" nas Configurações rápidas (barra que desce do topo).
 * Mostra o próximo registro esperado e, ao tocar, registra pelo mesmo fluxo do widget.
 */
class BlocoPonto : TileService() {

    private val escopo = MainScope()
    private val fmtHm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun onStartListening() {
        super.onStartListening()
        escopo.launch { atualizar() }
    }

    private suspend fun atualizar() {
        val tile = qsTile ?: return
        val todas = runCatching { Repositorio.todas() }.getOrElse { return }
        val aberta = calcularEstado(montarTurnos(todas), todas).abertoDesde
        tile.label = if (aberta != null) "Bater SAÍDA" else "Bater ENTRADA"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (aberta != null) {
                "Desde " + Instant.ofEpochMilli(aberta.dataHora).atZone(ZoneId.systemDefault()).format(fmtHm)
            } else "Pontos Voith"
        }
        tile.state = if (aberta != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        escopo.launch {
            val todas = runCatching { Repositorio.todas() }.getOrElse { return@launch }
            val aberta = calcularEstado(montarTurnos(todas), todas).abertoDesde
            val tipo = if (aberta != null) TipoBatida.SAIDA else TipoBatida.ENTRADA
            if (isLocked) unlockAndRun { abrirRegistro(tipo) } else abrirRegistro(tipo)
        }
    }

    private fun abrirRegistro(tipo: String) {
        val i = Intent(this, WidgetPontoActivity::class.java).apply {
            putExtra(WidgetPontoActivity.EXTRA_TIPO, tipo)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(this, 20, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }

    override fun onDestroy() {
        escopo.cancel()
        super.onDestroy()
    }
}
