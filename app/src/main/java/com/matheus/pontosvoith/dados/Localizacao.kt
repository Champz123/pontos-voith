package com.matheus.pontosvoith.dados

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

object Localizacao {

    fun temPermissao(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** GPS nativo (sem Google Play Services e sem internet). Retorna null se não conseguir. */
    @SuppressLint("MissingPermission")
    suspend fun obter(ctx: Context, timeoutMs: Long = 30_000): Location? {
        if (!temPermissao(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provedores = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (provedores.isEmpty()) return null

        // Usa uma posição recente (até 2 min) e precisa, se existir
        val recente = provedores
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 120_000 && it.accuracy <= 100f }
            .maxByOrNull { it.time }
        if (recente != null) return recente

        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        runCatching { lm.removeUpdates(this) }
                        if (cont.isActive) cont.resume(location)
                    }
                    @Deprecated("Compatibilidade Android 8-10")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                try {
                    provedores.forEach { lm.requestLocationUpdates(it, 0L, 0f, listener, Looper.getMainLooper()) }
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
                cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
            }
        }
    }

    /** Converte coordenadas em endereço. Só funciona com internet; senão retorna null. */
    fun endereco(ctx: Context, lat: Double, lon: Double): String? = try {
        if (!Geocoder.isPresent()) null
        else {
            @Suppress("DEPRECATION")
            Geocoder(ctx, Locale("pt", "BR")).getFromLocation(lat, lon, 1)?.firstOrNull()?.getAddressLine(0)
        }
    } catch (e: Exception) {
        null
    }
}
