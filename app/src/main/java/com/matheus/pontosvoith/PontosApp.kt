package com.matheus.pontosvoith

import android.app.Application
import android.content.ComponentName
import android.service.quicksettings.TileService
import com.matheus.pontosvoith.atalhos.BlocoPonto
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
        // Qualquer alteração (app, widget, atalho ou bloco) atualiza o widget e o bloco
        escopo.launch {
            Repositorio.versao.collect {
                runCatching { WidgetPonto.atualizarTodos(this@PontosApp) }
                runCatching {
                    TileService.requestListeningState(this@PontosApp, ComponentName(this@PontosApp, BlocoPonto::class.java))
                }
            }
        }
    }
}
