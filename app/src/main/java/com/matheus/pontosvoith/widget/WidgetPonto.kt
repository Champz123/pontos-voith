package com.matheus.pontosvoith.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.matheus.pontosvoith.MainActivity
import com.matheus.pontosvoith.R
import com.matheus.pontosvoith.dados.Repositorio
import com.matheus.pontosvoith.dados.TipoBatida
import com.matheus.pontosvoith.dados.calcularEstado
import com.matheus.pontosvoith.dados.montarTurnos
import com.matheus.pontosvoith.dados.nomeTipo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Widget da tela inicial (RemoteViews nativo, sem bibliotecas extras).
 * Mostra o status do turno (com contador ao vivo) e os botões ENTRADA / SAÍDA.
 */
class WidgetPonto : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pendente = goAsync()
        escopo.launch {
            try {
                atualizarTodos(context)
            } finally {
                pendente.finish()
            }
        }
    }

    companion object {
        private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val FMT_HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val FMT_DIA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
        private const val AZUL = 0xFF094AFE.toInt()

        private fun Long.fmt(f: DateTimeFormatter): String =
            Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(f)

        /** Redesenha todos os widgets instalados com o estado atual do banco. */
        suspend fun atualizarTodos(ctx: Context) {
            val app = ctx.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, WidgetPonto::class.java))
            if (ids.isEmpty()) return

            val todas = Repositorio.todas()
            val estado = calcularEstado(montarTurnos(todas), todas)
            val aberta = estado.abertoDesde
            val v = RemoteViews(app.packageName, R.layout.widget_ponto)

            if (aberta != null) {
                // Turno em andamento: contador ao vivo (o próprio Android faz ele andar)
                val decorrido = System.currentTimeMillis() - aberta.dataHora
                v.setViewVisibility(R.id.widget_cronometro, View.VISIBLE)
                v.setViewVisibility(R.id.widget_principal, View.GONE)
                v.setChronometer(R.id.widget_cronometro, SystemClock.elapsedRealtime() - decorrido, null, true)
                v.setViewVisibility(R.id.widget_indicador, View.VISIBLE)
                v.setTextViewText(R.id.widget_status, "TURNO EM ANDAMENTO")
                v.setTextViewText(
                    R.id.widget_detalhe,
                    "Entrada às ${aberta.dataHora.fmt(FMT_HM)} de ${aberta.dataHora.fmt(FMT_DIA)}"
                )
            } else {
                v.setChronometer(R.id.widget_cronometro, SystemClock.elapsedRealtime(), null, false)
                v.setViewVisibility(R.id.widget_cronometro, View.GONE)
                v.setViewVisibility(R.id.widget_principal, View.VISIBLE)
                v.setViewVisibility(R.id.widget_indicador, View.GONE)
                v.setTextViewText(R.id.widget_status, "SEM TURNO ABERTO")
                val ult = estado.ultimoCompleto
                val ini = ult?.entrada
                val fim = ult?.saida
                if (ult != null && ini != null && fim != null) {
                    val m = ult.duracaoMs / 60000
                    v.setTextViewText(R.id.widget_principal, "Último turno: ${m / 60}h ${"%02d".format(m % 60)}min")
                } else {
                    v.setTextViewText(R.id.widget_principal, "Toque em ENTRADA")
                }
                val b = estado.ultimaBatida
                v.setTextViewText(
                    R.id.widget_detalhe,
                    if (b != null) "Último registro: ${nomeTipo(b.tipo)} ${b.dataHora.fmt(FMT_HM)} de ${b.dataHora.fmt(FMT_DIA)}"
                    else "Nenhum registro ainda"
                )
            }

            // Destaca o botão "esperado" (igual ao app)
            val entradaEsperada = aberta == null
            estiloBotao(v, R.id.widget_btn_entrada, entradaEsperada)
            estiloBotao(v, R.id.widget_btn_saida, !entradaEsperada)

            v.setOnClickPendingIntent(R.id.widget_btn_entrada, intentPonto(app, TipoBatida.ENTRADA, 1))
            v.setOnClickPendingIntent(R.id.widget_btn_saida, intentPonto(app, TipoBatida.SAIDA, 2))
            v.setOnClickPendingIntent(R.id.widget_cabecalho, intentAbrirApp(app))

            manager.updateAppWidget(ids, v)
        }

        private fun estiloBotao(v: RemoteViews, id: Int, ativo: Boolean) {
            v.setInt(id, "setBackgroundResource", if (ativo) R.drawable.widget_botao_ativo else R.drawable.widget_botao_inativo)
            v.setTextColor(id, if (ativo) AZUL else Color.WHITE)
        }

        private fun intentPonto(ctx: Context, tipo: String, codigo: Int): PendingIntent {
            val i = Intent(ctx, WidgetPontoActivity::class.java).apply {
                putExtra(WidgetPontoActivity.EXTRA_TIPO, tipo)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            return PendingIntent.getActivity(
                ctx, codigo, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun intentAbrirApp(ctx: Context): PendingIntent {
            val i = Intent(ctx, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return PendingIntent.getActivity(
                ctx, 3, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
