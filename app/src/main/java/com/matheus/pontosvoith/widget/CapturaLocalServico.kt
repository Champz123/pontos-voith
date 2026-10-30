package com.matheus.pontosvoith.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.matheus.pontosvoith.R
import com.matheus.pontosvoith.dados.Repositorio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * V6: serviço curto (até ~30 s) em primeiro plano que captura o GPS das batidas feitas
 * fora do app (widget, atalho, bloco). O Android exige uma notificação silenciosa enquanto roda.
 */
class CapturaLocalServico : Service() {

    companion object {
        private const val EXTRA_ID = "batida_id"
        private const val CANAL = "captura_local"
        private const val NOTIF_ID = 7001

        fun iniciar(ctx: Context, id: Long) {
            val i = Intent(ctx, CapturaLocalServico::class.java).putExtra(EXTRA_ID, id)
            try {
                ContextCompat.startForegroundService(ctx, i)
            } catch (e: Exception) {
                Repositorio.capturarLocalEmSegundoPlano(id)
            }
        }
    }

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_ID, -1L) ?: -1L
        val emPrimeiroPlano = try {
            criarCanal()
            val notif = NotificationCompat.Builder(this, CANAL)
                .setSmallIcon(R.drawable.ic_bloco_ponto)
                .setContentTitle("Pontos Voith")
                .setContentText("Obtendo a localização do ponto…")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .setOngoing(true)
                .build()
            val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            ServiceCompat.startForeground(this, NOTIF_ID, notif, tipo)
            true
        } catch (e: Exception) {
            false
        }

        if (id <= 0) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!emPrimeiroPlano) {
            // Não foi permitido rodar em primeiro plano: tenta mesmo assim e encerra o serviço.
            Repositorio.capturarLocalEmSegundoPlano(id)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        escopo.launch {
            try {
                Repositorio.capturarLocal(id)
            } finally {
                withContext(Dispatchers.Main) { stopSelf(startId) }
            }
        }
        return START_NOT_STICKY
    }

    private fun criarCanal() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CANAL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CANAL, "Captura de localização", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Aparece por alguns segundos ao bater ponto fora do app"
                    setShowBadge(false)
                }
            )
        }
    }

    override fun onDestroy() {
        escopo.cancel()
        super.onDestroy()
    }
}
