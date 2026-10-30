package com.matheus.pontosvoith.dados

import android.content.Context

object Preferencias {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("config", Context.MODE_PRIVATE)

    /** null = ainda não escolhido (segue o tema do celular). */
    fun temaEscuro(ctx: Context): Boolean? =
        if (sp(ctx).contains("tema_escuro")) sp(ctx).getBoolean("tema_escuro", false) else null

    fun salvarTema(ctx: Context, escuro: Boolean) {
        sp(ctx).edit().putBoolean("tema_escuro", escuro).apply()
    }

    fun gpsAtivo(ctx: Context): Boolean = sp(ctx).getBoolean("gps_ativo", true)
    fun salvarGps(ctx: Context, ativo: Boolean) {
        sp(ctx).edit().putBoolean("gps_ativo", ativo).apply()
    }

    /** E-mail padrão para envio de relatórios e backups. */
    fun email(ctx: Context): String = sp(ctx).getString("email_padrao", "") ?: ""
    fun salvarEmail(ctx: Context, email: String) {
        sp(ctx).edit().putString("email_padrao", email.trim()).apply()
    }

    fun ultimoBackup(ctx: Context): Long = sp(ctx).getLong("ultimo_backup", 0L)
    fun salvarUltimoBackup(ctx: Context, ms: Long) {
        sp(ctx).edit().putLong("ultimo_backup", ms).apply()
    }
}
