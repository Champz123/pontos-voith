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

    // ------------------------------------------------ V6: almoço
    /** Caixa "Almoço de 1h" (vale para a próxima SAÍDA; é desmarcada após a saída). */
    fun almoco(ctx: Context): Boolean = sp(ctx).getBoolean("almoco_1h", false)
    fun salvarAlmoco(ctx: Context, ativo: Boolean) {
        sp(ctx).edit().putBoolean("almoco_1h", ativo).commit()
    }

    // ------------------------------------------------ V6: desfazer
    /** (id, momento) do último registro feito pelo botão/widget/atalho. */
    fun ultimoRegistro(ctx: Context): Pair<Long, Long> =
        sp(ctx).getLong("ultimo_reg_id", 0L) to sp(ctx).getLong("ultimo_reg_em", 0L)

    fun salvarUltimoRegistro(ctx: Context, id: Long, em: Long) {
        sp(ctx).edit().putLong("ultimo_reg_id", id).putLong("ultimo_reg_em", em).commit()
    }

    fun limparUltimoRegistro(ctx: Context) {
        sp(ctx).edit().remove("ultimo_reg_id").remove("ultimo_reg_em").commit()
    }

    // ------------------------------------------------ V6: backup automático
    fun pastaBackupAuto(ctx: Context): String? = sp(ctx).getString("backup_auto_pasta", null)
    fun salvarPastaBackupAuto(ctx: Context, uri: String?) {
        sp(ctx).edit().putString("backup_auto_pasta", uri).apply()
    }

    fun backupAutoAtivo(ctx: Context): Boolean = sp(ctx).getBoolean("backup_auto_ativo", true)
    fun salvarBackupAutoAtivo(ctx: Context, ativo: Boolean) {
        sp(ctx).edit().putBoolean("backup_auto_ativo", ativo).apply()
    }

    fun ultimoBackupAuto(ctx: Context): Long = sp(ctx).getLong("backup_auto_ultimo", 0L)
    fun salvarUltimoBackupAuto(ctx: Context, ms: Long) {
        sp(ctx).edit().putLong("backup_auto_ultimo", ms).apply()
    }

    fun erroBackupAuto(ctx: Context): String? = sp(ctx).getString("backup_auto_erro", null)
    fun salvarErroBackupAuto(ctx: Context, erro: String?) {
        val e = sp(ctx).edit()
        if (erro == null) e.remove("backup_auto_erro") else e.putString("backup_auto_erro", erro)
        e.apply()
    }
}
