package com.matheus.pontosvoith.exportar

import com.matheus.pontosvoith.dados.Batida
import com.matheus.pontosvoith.dados.Turno
import com.matheus.pontosvoith.dados.paraData
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val BR: Locale = Locale("pt", "BR")
val F_DIA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
val F_HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
val F_HMS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
val F_SEMANA: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", BR)
val F_ARQ: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
val F_ARQ_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")

fun Long.fmt(f: DateTimeFormatter): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(f)

fun horasHm(ms: Long): String {
    val m = ms.coerceAtLeast(0) / 60000
    return String.format(BR, "%02d:%02d", m / 60, m % 60)
}

fun horasDecimal(ms: Long): Double = Math.round(ms.coerceAtLeast(0) / 36000.0) / 100.0

fun localTexto(b: Batida?): String {
    if (b == null) return ""
    b.endereco?.let { return it }
    val la = b.latitude
    val lo = b.longitude
    return if (la != null && lo != null) String.format(Locale.US, "%.5f, %.5f", la, lo) else ""
}

fun selos(b: Batida?): List<String> = buildList {
    if (b == null) return@buildList
    val nome = if (b.entrada) "Entrada" else "Saída"
    if (b.ajustado) add("$nome ajustada")
    if (b.retroativa) add("$nome retroativa")
    b.obsAjuste?.let { add(it) }
}

/** Período do relatório (inclusive nas duas pontas). */
data class Periodo(val inicio: LocalDate, val fim: LocalDate, val descricao: String) {
    fun contem(d: LocalDate) = !d.isBefore(inicio) && !d.isAfter(fim)
    fun turnos(t: List<Turno>) = t.filter { contem(it.dia) }.sortedBy { it.inicio }
    fun batidas(b: List<Batida>) = b.filter { contem(it.dataHora.paraData()) }.sortedBy { it.dataHora }
    val sufixoArquivo: String get() = "${inicio.format(F_ARQ)}_a_${fim.format(F_ARQ)}"
}
