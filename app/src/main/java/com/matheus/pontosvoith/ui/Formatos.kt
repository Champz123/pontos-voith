package com.matheus.pontosvoith.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val LOCALE_BR: Locale = Locale("pt", "BR")
val FMT_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
val FMT_HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
val FMT_DIA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
val FMT_DIA_CURTO: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
val FMT_DATA_LONGA: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy", LOCALE_BR)

fun Long.formatar(f: DateTimeFormatter): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(f)

/** 29400000 -> "08:10:00" */
fun duracaoHms(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000)
    return String.format(LOCALE_BR, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

/** 29400000 -> "8h 10min" */
fun duracaoTexto(ms: Long): String {
    val m = ms.coerceAtLeast(0) / 60000
    return "${m / 60}h ${String.format(LOCALE_BR, "%02d", m % 60)}min"
}
