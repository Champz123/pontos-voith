package com.matheus.pontosvoith.exportar

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.matheus.pontosvoith.dados.Turno
import java.io.ByteArrayOutputStream

/** Relatório PDF (A4) com resumo e tabela de turnos, usando o PdfDocument nativo. */
object ExportarPdf {
    private const val L = 595
    private const val A = 842
    private const val M = 36f
    private val AZUL = Color.rgb(9, 74, 254)
    private val AMARELO = Color.rgb(224, 161, 0)

    private val COLS = listOf("Data" to 66f, "Dia" to 34f, "Entrada" to 50f, "Saída" to 78f, "Horas" to 50f, "Situação" to 82f, "Observações" to 163f)

    fun gerar(periodo: Periodo, turnosTodos: List<Turno>, versaoApp: String): ByteArray {
        val turnos = periodo.turnos(turnosTodos)
        val completos = turnos.filter { it.completo }
        val total = completos.sumOf { it.duracaoMs }
        val dias = completos.map { it.dia }.toSet().size
        val incompletos = turnos.count { it.incompleto }

        val doc = PdfDocument()
        val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.rgb(20, 30, 60) }
        val neg = Paint(txt).apply { typeface = Typeface.DEFAULT_BOLD }
        val branco = Paint(neg).apply { color = Color.WHITE }
        val fundo = Paint().apply { color = AZUL }
        val zebra = Paint().apply { color = Color.rgb(242, 245, 255) }
        val cinza = Paint(txt).apply { color = Color.rgb(110, 120, 140); textSize = 8f }

        var pagina = 0
        var page: PdfDocument.Page? = null
        var c: Canvas? = null
        var y = 0f
        val alturaLinha = 16f

        fun rodape() {
            val cv = c ?: return
            cv.drawText("Pontos Voith $versaoApp • gerado em ${System.currentTimeMillis().fmt(F_DIA)} " +
                System.currentTimeMillis().fmt(F_HM), M, A - 20f, cinza)
            val p = "Página $pagina"
            cv.drawText(p, L - M - cinza.measureText(p), A - 20f, cinza)
        }

        fun cabecalhoTabela() {
            val cv = c ?: return
            cv.drawRect(M, y, L - M, y + alturaLinha + 2f, fundo)
            var x = M + 4f
            COLS.forEach { (t, w) -> cv.drawText(t, x, y + 12f, branco); x += w }
            y += alturaLinha + 4f
        }

        fun novaPagina(primeira: Boolean) {
            page?.let { rodape(); doc.finishPage(it) }
            pagina++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(L, A, pagina).create())
            page = p
            c = p.canvas
            val cv = p.canvas
            if (primeira) {
                cv.drawRect(0f, 0f, L.toFloat(), 78f, fundo)
                val titulo = Paint(branco).apply { textSize = 20f }
                cv.drawText("Pontos Voith — Relatório de horas", M, 40f, titulo)
                val sub = Paint(branco).apply { textSize = 11f; typeface = Typeface.DEFAULT }
                cv.drawText("Período: ${periodo.descricao}", M, 62f, sub)
                y = 100f
                val caixas = listOf(
                    "Horas trabalhadas" to horasHm(total),
                    "Dias trabalhados" to "$dias",
                    "Turnos completos" to "${completos.size}",
                    "Turnos incompletos" to "$incompletos"
                )
                val wCaixa = (L - 2 * M - 3 * 8f) / 4f
                caixas.forEachIndexed { i, (rot, valor) ->
                    val x0 = M + i * (wCaixa + 8f)
                    cv.drawRoundRect(x0, y, x0 + wCaixa, y + 48f, 8f, 8f, zebra)
                    cv.drawText(rot, x0 + 8f, y + 16f, cinza)
                    val vp = Paint(neg).apply {
                        textSize = 16f
                        color = if (i == 3 && incompletos > 0) AMARELO else AZUL
                    }
                    cv.drawText(valor, x0 + 8f, y + 38f, vp)
                }
                y += 66f
            } else {
                y = M
            }
            cabecalhoTabela()
        }

        novaPagina(primeira = true)

        if (turnos.isEmpty()) {
            c?.drawText("Nenhum registro no período.", M + 4f, y + 12f, txt)
        }
        turnos.forEachIndexed { i, t ->
            if (y + alturaLinha > A - 50f) novaPagina(primeira = false)
            val cv = c ?: return@forEachIndexed
            if (i % 2 == 1) cv.drawRect(M, y - 2f, L - M, y + alturaLinha - 2f, zebra)
            val e = t.entrada
            val s = t.saida
            val saidaTxt = s?.let {
                val outroDia = e != null && it.dataHora.fmt(F_DIA) != e.dataHora.fmt(F_DIA)
                it.dataHora.fmt(F_HM) + if (outroDia) " (${it.dataHora.fmt(F_DIA).substring(0, 5)})" else ""
            } ?: "--:--"
            val valores = listOf(
                t.dia.format(F_DIA),
                t.dia.format(F_SEMANA),
                e?.dataHora?.fmt(F_HM) ?: "--:--",
                saidaTxt,
                if (t.completo) horasHm(t.duracaoMs) else "",
                t.situacao,
                (selos(e) + selos(s)).joinToString(" • ")
            )
            var x = M + 4f
            valores.forEachIndexed { ci, v ->
                val w = COLS[ci].second
                val p = when {
                    ci == 4 -> neg
                    ci == 5 && !t.completo -> Paint(txt).apply { color = AMARELO }
                    else -> txt
                }
                cv.drawText(cortar(v, p, w - 6f), x, y + 10f, p)
                x += w
            }
            y += alturaLinha
        }

        if (y + 30f > A - 50f) novaPagina(primeira = false)
        c?.let { cv ->
            cv.drawLine(M, y + 2f, L - M, y + 2f, Paint().apply { color = AZUL; strokeWidth = 1.2f })
            cv.drawText("TOTAL", M + 4f, y + 16f, neg)
            val xHoras = M + 4f + COLS.take(4).sumOf { it.second.toDouble() }.toFloat()
            cv.drawText(horasHm(total), xHoras, y + 16f, Paint(neg).apply { color = AZUL })
        }
        page?.let { rodape(); doc.finishPage(it) }

        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    private fun cortar(s: String, p: Paint, max: Float): String {
        if (p.measureText(s) <= max) return s
        var t = s
        while (t.isNotEmpty() && p.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }
}
