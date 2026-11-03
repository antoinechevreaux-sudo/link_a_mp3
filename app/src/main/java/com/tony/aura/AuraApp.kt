package com.tony.aura

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.tony.aura.data.Library
import com.tony.aura.download.Engine

class AuraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Library.init(this)
        com.tony.aura.player.Ambient.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_DOWNLOADS, "Descargas en curso", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_AMBIENT, "Sonido de fondo", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_DONE, "Descargas terminadas", NotificationManager.IMPORTANCE_DEFAULT))
        // Prepara yt-dlp en segundo plano para que la primera descarga empiece antes
        Thread { try { Engine.ensureInit(this) } catch (_: Throwable) {} }.start()
    }

    companion object {
        const val CH_DOWNLOADS = "descargas"
        const val CH_DONE = "descargas_listas"
        const val CH_AMBIENT = "fondo"
    }
}
