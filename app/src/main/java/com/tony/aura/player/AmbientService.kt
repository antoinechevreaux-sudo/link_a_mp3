package com.tony.aura.player

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tony.aura.AuraApp
import com.tony.aura.MainActivity
import com.tony.aura.R

/**
 * Mantiene vivo el sonido de fondo con la pantalla apagada y aunque la pista
 * principal haya terminado (servicio en primer plano con su notificación).
 */
class AmbientService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForeground(NOTIF_ID, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> Ambient.toggle()
            ACTION_STOP -> { Ambient.clear(); return START_NOT_STICKY }
        }
        if (Ambient.state.value.track == null) stopSelf() else refresh()
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Ambient.clear()
        stopSelf()
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun refresh() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build())
    }

    private fun action(act: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, AmbientService::class.java).setAction(act),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun build(): Notification {
        val s = Ambient.state.value
        val timer = SleepTimer.state.value
        val text = buildString {
            append(if (s.playing) "Ruido de fondo en bucle" else "Fondo en pausa")
            if (timer.endsAt > 0) append(" · se apaga a las ${SleepTimer.endsAtLabel()}")
        }
        return NotificationCompat.Builder(this, AuraApp.CH_AMBIENT)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFFEF4444.toInt())
            .setContentTitle(s.track?.title ?: "Ruido de fondo")
            .setContentText(text)
            .setOngoing(true).setSilent(true).setOnlyAlertOnce(true)
            .setContentIntent(PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "reproductor"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ))
            .addAction(0, if (s.playing) "Pausar" else "Reanudar", action(ACTION_TOGGLE, 1))
            .addAction(0, "Detener", action(ACTION_STOP, 2))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val NOTIF_ID = 3001
        const val ACTION_TOGGLE = "com.tony.aura.FONDO_PAUSA"
        const val ACTION_STOP = "com.tony.aura.FONDO_STOP"
        @Volatile var instance: AmbientService? = null

        fun start(ctx: Context) {
            if (instance != null) { instance?.refresh(); return }
            try { ctx.startForegroundService(Intent(ctx, AmbientService::class.java)) } catch (_: Throwable) {}
        }

        fun stop(ctx: Context) {
            instance?.let { it.stopForeground(STOP_FOREGROUND_REMOVE); it.stopSelf() }
            instance = null
        }
    }
}
