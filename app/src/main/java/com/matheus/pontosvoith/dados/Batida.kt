package com.matheus.pontosvoith.dados

object TipoBatida {
    const val ENTRADA = "ENTRADA"
    const val SAIDA = "SAIDA"
}

object StatusLocal {
    const val PENDENTE = "PENDENTE"
    const val OK = "OK"
    const val INDISPONIVEL = "INDISPONIVEL"
    const val SEM_PERMISSAO = "SEM_PERMISSAO"
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
    val ajustado: Boolean
)

/** Estado calculado a partir das batidas: turno aberto e último turno fechado. */
data class EstadoTurno(
    val abertoDesde: Batida?,
    val ultimoInicio: Batida?,
    val ultimoFim: Batida?
) {
    val ultimoDuracaoMs: Long?
        get() = if (ultimoInicio != null && ultimoFim != null) ultimoFim.dataHora - ultimoInicio.dataHora else null
}

/**
 * Pareia cada ENTRADA com a próxima SAÍDA (mesmo que seja no dia seguinte,
 * ex.: 20:00 -> 03:00 é um único turno). Batidas sem par não geram turno.
 */
fun calcularEstado(batidas: List<Batida>): EstadoTurno {
    var aberta: Batida? = null
    var ultIni: Batida? = null
    var ultFim: Batida? = null
    for (b in batidas.sortedWith(compareBy({ it.dataHora }, { it.id }))) {
        if (b.tipo == TipoBatida.ENTRADA) {
            aberta = b
        } else if (aberta != null) {
            ultIni = aberta; ultFim = b; aberta = null
        }
    }
    return EstadoTurno(aberta, ultIni, ultFim)
}
