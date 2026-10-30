package com.matheus.pontosvoith.dados

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Desconto de almoço (1 hora). */
const val ALMOCO_MS = 3_600_000L

object TipoBatida {
    const val ENTRADA = "ENTRADA"
    const val SAIDA = "SAIDA"
}

object StatusLocal {
    const val PENDENTE = "PENDENTE"
    const val OK = "OK"
    const val INDISPONIVEL = "INDISPONIVEL"
    const val SEM_PERMISSAO = "SEM_PERMISSAO"
    const val DESATIVADO = "DESATIVADO"
    const val MANUAL = "MANUAL"
}

object Origem {
    const val NORMAL = "NORMAL"
    const val RETROATIVA = "RETROATIVA"
}

data class Batida(
    val id: Long,
    val tipo: String,
    val dataHora: Long,
    val latitude: Double?,
    val longitude: Double?,
    val precisao: Double?,
    val endereco: String?,
    val statusLocal: String,
    val origem: String,
    val ajustado: Boolean,
    val dataAjuste: Long?,
    val valorOriginal: Long?,
    val obsAjuste: String?,
    val criadoEm: Long,
    /** V6: só vale na SAÍDA. true = desconta 1h de almoço do turno. */
    val almoco: Boolean = false,
    /** V6: preenchido quando o registro está na Lixeira. */
    val excluidoEm: Long? = null
) {
    val retroativa: Boolean get() = origem == Origem.RETROATIVA
    val entrada: Boolean get() = tipo == TipoBatida.ENTRADA
}

fun nomeTipo(t: String) = if (t == TipoBatida.ENTRADA) "Entrada" else "Saída"

fun Long.paraData(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

/** Um turno = ENTRADA + próxima SAÍDA (pode atravessar a meia-noite). */
data class Turno(val entrada: Batida?, val saida: Batida?, val emAndamento: Boolean = false) {
    val completo: Boolean get() = entrada != null && saida != null
    val incompleto: Boolean get() = !completo && !emAndamento
    val inicio: Long get() = entrada?.dataHora ?: saida?.dataHora ?: 0L

    /** Saída − Entrada, sem nenhum desconto. */
    val duracaoBrutaMs: Long
        get() {
            val e = entrada
            val s = saida
            return if (e != null && s != null) (s.dataHora - e.dataHora).coerceAtLeast(0) else 0L
        }

    /** true se a SAÍDA foi marcada com almoço de 1h. */
    val temAlmoco: Boolean get() = completo && saida?.almoco == true

    /** Quanto foi descontado de almoço (nunca deixa o turno negativo). */
    val descontoAlmocoMs: Long get() = if (temAlmoco) minOf(ALMOCO_MS, duracaoBrutaMs) else 0L

    /** Horas trabalhadas = bruto − almoço (quando marcado). */
    val duracaoMs: Long get() = duracaoBrutaMs - descontoAlmocoMs

    /** O turno pertence ao dia da ENTRADA (ex.: 20:00 -> 03:00 conta no dia da entrada). */
    val dia: LocalDate get() = inicio.paraData()
    val situacao: String
        get() = when {
            completo -> "Completo"
            emAndamento -> "Em andamento"
            entrada == null -> "Saída sem entrada"
            else -> "Sem saída"
        }
}

/** Monta os turnos a partir de todas as batidas (ordem cronológica). */
fun montarTurnos(batidas: List<Batida>): List<Turno> {
    val ord = batidas.sortedWith(compareBy({ it.dataHora }, { it.id }))
    val res = ArrayList<Turno>()
    var aberta: Batida? = null
    for (b in ord) {
        if (b.tipo == TipoBatida.ENTRADA) {
            if (aberta != null) res += Turno(aberta, null)
            aberta = b
        } else {
            if (aberta != null) {
                res += Turno(aberta, b); aberta = null
            } else {
                res += Turno(null, b)
            }
        }
    }
    if (aberta != null) res += Turno(aberta, null, emAndamento = true)
    return res
}

data class EstadoTurno(val abertoDesde: Batida?, val ultimoCompleto: Turno?, val ultimaBatida: Batida?)

fun calcularEstado(turnos: List<Turno>, todas: List<Batida>): EstadoTurno = EstadoTurno(
    abertoDesde = turnos.lastOrNull()?.takeIf { it.emAndamento }?.entrada,
    ultimoCompleto = turnos.lastOrNull { it.completo },
    ultimaBatida = todas.maxWithOrNull(compareBy({ it.dataHora }, { it.id }))
)

/** Horas trabalhadas (ms) por dia, somente turnos completos (já com desconto de almoço). */
fun horasPorDia(turnos: List<Turno>): Map<LocalDate, Long> =
    turnos.filter { it.completo }.groupBy { it.dia }.mapValues { (_, l) -> l.sumOf { it.duracaoMs } }

fun turnosDoMes(turnos: List<Turno>, mes: YearMonth): List<Turno> =
    turnos.filter { YearMonth.from(it.dia) == mes }
