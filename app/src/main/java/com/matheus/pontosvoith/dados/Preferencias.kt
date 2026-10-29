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
}
