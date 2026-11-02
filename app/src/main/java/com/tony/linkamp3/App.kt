package com.tony.linkamp3

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "Descargas en curso", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "Descargas terminadas", NotificationManager.IMPORTANCE_DEFAULT)
        )
        // Preparar el motor en segundo plano para que la primera descarga empiece antes
        Thread {
            try { Engine.ensureInit(this) } catch (_: Throwable) {}
            Jobs.changed()
        }.start()
    }

    companion object {
        const val CH_PROGRESS = "progreso"
        const val CH_DONE = "listo"
    }
}
