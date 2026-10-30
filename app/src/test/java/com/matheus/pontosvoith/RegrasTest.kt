package com.matheus.pontosvoith

import com.matheus.pontosvoith.dados.Batida
import com.matheus.pontosvoith.dados.Origem
import com.matheus.pontosvoith.dados.StatusLocal
import com.matheus.pontosvoith.dados.TipoBatida
import com.matheus.pontosvoith.dados.calcularEstado
import com.matheus.pontosvoith.dados.horasPorDia
import com.matheus.pontosvoith.dados.montarTurnos
import com.matheus.pontosvoith.exportar.Backup
import com.matheus.pontosvoith.exportar.ExportarExcel
import com.matheus.pontosvoith.exportar.Periodo
import com.matheus.pontosvoith.exportar.horasDecimal
import com.matheus.pontosvoith.exportar.horasHm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipInputStream

/**
 * Testes automáticos das regras do Pontos Voith.
 * Rodam no GitHub a cada envio. Se algum falhar, o APK NÃO é gerado.
 */
class RegrasTest {

    private val H = 3_600_000L
    private val MIN = 60_000L
    private var proximoId = 1L

    private fun ms(t: String): Long = LocalDateTime.parse(t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun batida(tipo: String, t: String, almoco: Boolean = false) = Batida(
        id = proximoId++, tipo = tipo, dataHora = ms(t),
        latitude = null, longitude = null, precisao = null, endereco = null,
        statusLocal = StatusLocal.OK, origem = Origem.NORMAL, ajustado = false,
        dataAjuste = null, valorOriginal = null, obsAjuste = null,
        criadoEm = ms(t), almoco = almoco
    )

    private fun e(t: String) = batida(TipoBatida.ENTRADA, t)
    private fun s(t: String, almoco: Boolean = false) = batida(TipoBatida.SAIDA, t, almoco)

    // ------------------------------------------------------------ turnos

    @Test
    fun `turno simples conta exatamente saida menos entrada`() {
        val t = montarTurnos(listOf(e("2026-10-01T08:00"), s("2026-10-01T17:00")))
        assertEquals(1, t.size)
        assertTrue(t[0].completo)
        assertEquals(9 * H, t[0].duracaoMs)
    }

    @Test
    fun `sem almoco marcado nao desconta nada`() {
        val t = montarTurnos(listOf(e("2026-10-01T08:00"), s("2026-10-01T17:37")))[0]
        assertFalse(t.temAlmoco)
        assertEquals(0L, t.descontoAlmocoMs)
        assertEquals(9 * H + 37 * MIN, t.duracaoMs)
    }

    @Test
    fun `almoco marcado desconta 1 hora`() {
        val t = montarTurnos(listOf(e("2026-10-01T08:00"), s("2026-10-01T17:00", almoco = true)))[0]
        assertTrue(t.temAlmoco)
        assertEquals(9 * H, t.duracaoBrutaMs)
        assertEquals(H, t.descontoAlmocoMs)
        assertEquals(8 * H, t.duracaoMs)
    }

    @Test
    fun `almoco nunca deixa o turno negativo`() {
        val t = montarTurnos(listOf(e("2026-10-01T08:00"), s("2026-10-01T08:30", almoco = true)))[0]
        assertEquals(0L, t.duracaoMs)
        assertEquals(30 * MIN, t.descontoAlmocoMs)
    }

    @Test
    fun `turno que passa da meia-noite conta no dia da entrada`() {
        val t = montarTurnos(listOf(e("2026-10-01T20:00"), s("2026-10-02T03:00")))[0]
        assertEquals(7 * H, t.duracaoMs)
        assertEquals(LocalDate.of(2026, 10, 1), t.dia)
    }

    @Test
    fun `segunda entrada deixa a anterior sem saida`() {
        val t = montarTurnos(listOf(e("2026-10-01T08:00"), e("2026-10-01T09:00"), s("2026-10-01T17:00")))
        assertEquals(2, t.size)
        assertTrue(t[0].incompleto)
        assertEquals("Sem saída", t[0].situacao)
        assertEquals(8 * H, t[1].duracaoMs)
    }

    @Test
    fun `saida sem entrada vira turno incompleto`() {
        val t = montarTurnos(listOf(s("2026-10-01T17:00")))
        assertEquals(1, t.size)
        assertNull(t[0].entrada)
        assertEquals("Saída sem entrada", t[0].situacao)
        assertEquals(0L, t[0].duracaoMs)
    }

    @Test
    fun `entrada sem saida fica em andamento`() {
        val lista = listOf(e("2026-10-01T08:00"))
        val t = montarTurnos(lista)
        assertTrue(t[0].emAndamento)
        assertFalse(t[0].incompleto)
        assertNotNull(calcularEstado(t, lista).abertoDesde)
    }

    @Test
    fun `ordem de gravacao nao altera o resultado`() {
        val a = e("2026-10-01T08:00")
        val b = s("2026-10-01T12:00")
        val c = e("2026-10-01T13:00")
        val d = s("2026-10-01T17:00", almoco = false)
        val normal = montarTurnos(listOf(a, b, c, d)).map { it.duracaoMs }
        val embaralhado = montarTurnos(listOf(d, b, a, c)).map { it.duracaoMs }
        assertEquals(normal, embaralhado)
    }

    @Test
    fun `horas por dia somam varios turnos ja com almoco`() {
        val t = montarTurnos(
            listOf(
                e("2026-10-01T06:00"), s("2026-10-01T10:00"),
                e("2026-10-01T13:00"), s("2026-10-01T19:00", almoco = true)
            )
        )
        assertEquals(4 * H + 5 * H, horasPorDia(t)[LocalDate.of(2026, 10, 1)])
    }

    // ------------------------------------------------------------ período e formatos

    @Test
    fun `periodo usa o dia da entrada`() {
        val t = montarTurnos(listOf(e("2026-10-31T22:00"), s("2026-11-01T06:00")))
        val outubro = Periodo(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "Outubro")
        val novembro = Periodo(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "Novembro")
        assertEquals(1, outubro.turnos(t).size)
        assertEquals(0, novembro.turnos(t).size)
    }

    @Test
    fun `formatos de horas`() {
        assertEquals("08:05", horasHm(8 * H + 5 * MIN))
        assertEquals("00:00", horasHm(-5))
        assertEquals(8.5, horasDecimal(8 * H + 30 * MIN), 0.001)
    }

    // ------------------------------------------------------------ exportação e backup

    @Test
    fun `excel gerado tem as duas abas e o total`() {
        val lista = listOf(e("2026-10-01T08:00"), s("2026-10-01T17:00", almoco = true))
        val t = montarTurnos(lista)
        val p = Periodo(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "Outubro")
        val bytes = ExportarExcel.gerar(p, t, lista)

        val arquivos = HashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var entrada = z.nextEntry
            while (entrada != null) {
                arquivos[entrada.name] = z.readBytes().toString(Charsets.UTF_8)
                entrada = z.nextEntry
            }
        }
        assertTrue(arquivos.containsKey("xl/workbook.xml"))
        val turnos = arquivos["xl/worksheets/sheet1.xml"] ?: error("aba Turnos ausente")
        assertNotNull(arquivos["xl/worksheets/sheet2.xml"])
        assertTrue(turnos.contains(">TOTAL<"))
        assertTrue("total líquido 08:00 esperado", turnos.contains(">08:00<"))
        assertTrue("almoço descontado 01:00 esperado", turnos.contains(">01:00<"))
    }

    @Test
    fun `backup le registros e o almoco`() {
        val json = """
            {"app":"PontosVoith","formato":1,"versao_app":"V6","gerado_em":0,
             "batidas":[
               {"tipo":"ENTRADA","data_hora":1000},
               {"tipo":"SAIDA","data_hora":5000,"almoco":true},
               {"tipo":"INVALIDO","data_hora":1}
             ]}
        """.trimIndent()
        val c = Backup.ler(json.toByteArray())
        assertEquals(2, c.batidas.size)
        assertFalse(c.batidas[0].almoco)
        assertTrue(c.batidas[1].almoco)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `arquivo que nao e backup e recusado`() {
        Backup.ler("isto não é um backup".toByteArray())
    }
}
