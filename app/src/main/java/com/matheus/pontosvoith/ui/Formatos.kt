package com.matheus.pontosvoith.ui

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val LOCALE_BR: Locale = Locale("pt", "BR")
val FMT_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
val FMT_HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
val FMT_DIA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
val FMT_DIA_CURTO: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
val FMT_DATA_LONGA: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy", LOCALE_BR)
val FMT_DIA_SEMANA: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd/MM/yyyy", LOCALE_BR)
val FMT_MES_ANO: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", LOCALE_BR)
val FMT_MES_CURTO: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM/yy", LOCALE_BR)

fun Long.formatar(f: DateTimeFormatter): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(f)

fun LocalDateTime.paraMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun String.capitalizar(): String = replaceFirstChar { it.titlecase(LOCALE_BR) }

fun duracaoHms(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000)
    return String.format(LOCALE_BR, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

fun duracaoTexto(ms: Long): String {
    val m = ms.coerceAtLeast(0) / 60000
    return "${m / 60}h ${String.format(LOCALE_BR, "%02d", m % 60)}min"
}

fun duracaoCurta(ms: Long): String {
    val m = ms.coerceAtLeast(0) / 60000
    return if (m % 60 == 0L) "${m / 60}h" else "${m / 60}h${String.format(LOCALE_BR, "%02d", m % 60)}"
}
