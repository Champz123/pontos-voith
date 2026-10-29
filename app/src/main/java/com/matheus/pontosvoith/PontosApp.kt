package com.matheus.pontosvoith

import android.app.Application
import com.matheus.pontosvoith.dados.Repositorio

class PontosApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repositorio.init(this)
    }
}
