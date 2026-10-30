package com.matheus.pontosvoith

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.matheus.pontosvoith.dados.Preferencias
import com.matheus.pontosvoith.ui.AppRaiz
import com.matheus.pontosvoith.ui.theme.PontosVoithTheme

const val VERSAO_APP = "V5"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = this
        setContent {
            val sistemaEscuro = isSystemInDarkTheme()
            var escuro by rememberSaveable { mutableStateOf(Preferencias.temaEscuro(ctx) ?: sistemaEscuro) }
            PontosVoithTheme(darkTheme = escuro) {
                AppRaiz(escuro = escuro, onTema = { novo ->
                    escuro = novo
                    Preferencias.salvarTema(ctx, novo)
                })
            }
        }
    }
}
