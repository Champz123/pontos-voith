package com.matheus.pontosvoith.exportar

import com.matheus.pontosvoith.dados.Batida
import com.matheus.pontosvoith.dados.Turno
import com.matheus.pontosvoith.dados.nomeTipo
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Gera .xlsx sem bibliotecas externas (XML compactado), mantendo o APK leve.
 * Abas: "Turnos" (com total) e "Registros" (todas as batidas).
 */
object ExportarExcel {

    private sealed class Cel {
        data class Txt(val v: String, val estilo: Int = 0) : Cel()
        data class Num(val v: Double, val estilo: Int = 2) : Cel()
    }

    // estilos: 0 normal | 1 cabeçalho azul | 2 número 0,00 | 3 negrito | 4 número negrito
    private const val ESTILOS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="1"><numFmt numFmtId="164" formatCode="0.00"/></numFmts>
<fonts count="3"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF094AFE"/><bgColor indexed="64"/></patternFill></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="5"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="164" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1" applyNumberFormat="1"/></cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""

    fun gerar(periodo: Periodo, turnosTodos: List<Turno>, batidasTodas: List<Batida>): ByteArray {
        val turnos = periodo.turnos(turnosTodos)
        val batidas = periodo.batidas(batidasTodas)

        // ---- aba Turnos
        val linT = ArrayList<List<Cel>>()
        linT += listOf("Data", "Dia", "Entrada", "Saída", "Data da saída", "Almoço descontado", "Horas (hh:mm)", "Horas (decimal)",
            "Situação", "Local da entrada", "Local da saída", "Observações").map { Cel.Txt(it, 1) }
        turnos.forEach { t ->
            val e = t.entrada
            val s = t.saida
            linT += listOf(
                Cel.Txt(t.dia.format(F_DIA)),
                Cel.Txt(t.dia.format(F_SEMANA)),
                Cel.Txt(e?.dataHora?.fmt(F_HM) ?: ""),
                Cel.Txt(s?.dataHora?.fmt(F_HM) ?: ""),
                Cel.Txt(s?.dataHora?.fmt(F_DIA) ?: ""),
                Cel.Txt(if (t.temAlmoco) horasHm(t.descontoAlmocoMs) else ""),
                Cel.Txt(if (t.completo) horasHm(t.duracaoMs) else ""),
                if (t.completo) Cel.Num(horasDecimal(t.duracaoMs)) else Cel.Txt(""),
                Cel.Txt(t.situacao),
                Cel.Txt(localTexto(e)),
                Cel.Txt(localTexto(s)),
                Cel.Txt((selos(e) + selos(s)).joinToString(" • "))
            )
        }
        val total = turnos.filter { it.completo }.sumOf { it.duracaoMs }
        val totalAlmoco = turnos.sumOf { it.descontoAlmocoMs }
        linT += emptyList<Cel>()
        linT += listOf(
            Cel.Txt("TOTAL", 3), Cel.Txt(""), Cel.Txt(""), Cel.Txt(""), Cel.Txt(""),
            Cel.Txt(if (totalAlmoco > 0) horasHm(totalAlmoco) else "", 3),
            Cel.Txt(horasHm(total), 3), Cel.Num(horasDecimal(total), 4),
            Cel.Txt("${turnos.count { it.completo }} turno(s) completo(s)", 3)
        )
        linT += listOf(Cel.Txt("Período: ${periodo.descricao}"))

        // ---- aba Registros
        val linR = ArrayList<List<Cel>>()
        linR += listOf("Data", "Hora", "Tipo", "Almoço 1h", "Latitude", "Longitude", "Precisão (m)", "Endereço",
            "Ajustado", "Horário original", "Retroativo", "Observação").map { Cel.Txt(it, 1) }
        batidas.forEach { b ->
            linR += listOf(
                Cel.Txt(b.dataHora.fmt(F_DIA)),
                Cel.Txt(b.dataHora.fmt(F_HMS)),
                Cel.Txt(nomeTipo(b.tipo)),
                Cel.Txt(if (!b.entrada && b.almoco) "Sim" else ""),
                b.latitude?.let { Cel.Num(it, 0) } ?: Cel.Txt(""),
                b.longitude?.let { Cel.Num(it, 0) } ?: Cel.Txt(""),
                b.precisao?.let { Cel.Num(Math.round(it).toDouble(), 0) } ?: Cel.Txt(""),
                Cel.Txt(b.endereco ?: ""),
                Cel.Txt(if (b.ajustado) "Sim" else "Não"),
                Cel.Txt(b.valorOriginal?.let { "${it.fmt(F_DIA)} ${it.fmt(F_HMS)}" } ?: ""),
                Cel.Txt(if (b.retroativa) "Sim" else "Não"),
                Cel.Txt(b.obsAjuste ?: "")
            )
        }

        val larguraT = listOf(12, 7, 9, 9, 13, 17, 14, 15, 18, 40, 40, 40)
        val larguraR = listOf(12, 10, 9, 10, 12, 12, 12, 45, 10, 20, 11, 40)

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun add(nome: String, conteudo: String) {
                z.putNextEntry(ZipEntry(nome))
                z.write(conteudo.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
            add("[Content_Types].xml", CONTENT_TYPES)
            add("_rels/.rels", RELS)
            add("xl/workbook.xml", WORKBOOK)
            add("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            add("xl/styles.xml", ESTILOS)
            add("xl/worksheets/sheet1.xml", planilha(linT, larguraT))
            add("xl/worksheets/sheet2.xml", planilha(linR, larguraR))
        }
        return out.toByteArray()
    }

    private fun planilha(linhas: List<List<Cel>>, larguras: List<Int>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        sb.append("<cols>")
        larguras.forEachIndexed { i, w ->
            sb.append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""")
        }
        sb.append("</cols><sheetData>")
        linhas.forEachIndexed { li, cels ->
            val r = li + 1
            sb.append("""<row r="$r">""")
            cels.forEachIndexed { ci, c ->
                val ref = coluna(ci) + r
                when (c) {
                    is Cel.Txt -> if (c.v.isNotEmpty() || c.estilo != 0) {
                        sb.append("""<c r="$ref" t="inlineStr" s="${c.estilo}"><is><t xml:space="preserve">""")
                        sb.append(escapar(c.v))
                        sb.append("</t></is></c>")
                    }
                    is Cel.Num -> {
                        val num = String.format(Locale.US, "%.6f", c.v).trimEnd('0').trimEnd('.')
                        sb.append("""<c r="$ref" s="${c.estilo}"><v>$num</v></c>""")
                    }
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun coluna(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            sb.insert(0, ('A' + r))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun escapar(s: String): String = buildString {
        s.forEach { ch ->
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                ch.code < 0x20 && ch != '\n' && ch != '\t' && ch != '\r' -> append(' ')
                else -> append(ch)
            }
        }
    }

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""

    private const val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private const val WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Turnos" sheetId="1" r:id="rId1"/><sheet name="Registros" sheetId="2" r:id="rId2"/></sheets></workbook>"""

    private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
}
