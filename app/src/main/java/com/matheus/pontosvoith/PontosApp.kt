package com.matheus.pontosvoith

import android.app.Application
import com.matheus.pontosvoith.dados.Repositorio
import com.matheus.pontosvoith.widget.WidgetPonto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PontosApp : Application() {
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Repositorio.init(this)
        // V5: qualquer alteração no banco (app ou widget) atualiza o widget
        escopo.launch {
            Repositorio.versao.collect {
                runCatching { WidgetPonto.atualizarTodos(this@PontosApp) }
            }
        }
    }
}
