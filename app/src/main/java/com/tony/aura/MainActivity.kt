package com.tony.aura

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.tony.aura.data.DeviceMusic
import com.tony.aura.player.PlayerConnection
import com.tony.aura.ui.AuraRoot
import com.tony.aura.ui.LocalUi
import com.tony.aura.ui.Tab
import com.tony.aura.ui.UiState
import com.tony.aura.ui.theme.AuraTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

class MainActivity : ComponentActivity() {

    private val ui = UiState()
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasAudioPermission()) Thread { DeviceMusic.refresh(this) }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        PlayerConnection.connect(this)
        askPermissions()
        handleIntent(intent)
        setContent {
            AuraTheme {
                val conf = LocalConfiguration.current
                val base = LocalDensity.current
                // Diseño pensado para ~420 dp de ancho; en pantallas más estrechas se reduce todo en proporción
                val factor = (conf.screenWidthDp / 420f).coerceIn(0.78f, 1f)
                CompositionLocalProvider(
                    LocalUi provides remember { ui },
                    LocalDensity provides Density(base.density * factor, base.fontScale.coerceAtMost(1.1f) * Settings.fontScale),
                ) {
                    AuraRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    // Al salir de la app con un vídeo a pantalla completa → ventana flotante
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val c = PlayerConnection.controller
        if (Settings.autoPip && ui.videoFullscreen && c != null && c.isPlaying &&
            PlayerConnection.ui.value.current?.isVideo == true) {
            try {
                enterPictureInPictureMode(
                    android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(16, 9)).build()
                )
            } catch (_: Throwable) {}
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        ui.inPip = isInPictureInPictureMode
    }

    override fun onStart() {
        super.onStart()
        PlayerConnection.connect(this)
        if (hasAudioPermission()) Thread { DeviceMusic.refresh(this) }.start()
    }

    override fun onDestroy() {
        // Al salir de la app (atrás o cerrar), la música se detiene
        if (isFinishing && !isChangingConfigurations) {
            PlayerConnection.stopAll()
            PlayerConnection.disconnect()
            stopService(Intent(this, com.tony.aura.player.PlaybackService::class.java))
        }
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        when {
            intent?.action == Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
                val url = text?.let { Regex("https?://\\S+").find(it)?.value?.trimEnd('.', ',', ')', '"', '\'') }
                if (url != null) { ui.sharedUrl = url; ui.tab = Tab.DOWNLOAD; ui.openPlaylist = null; ui.videoFullscreen = false }
                else ui.toast("No he encontrado ningún enlace en lo compartido")
            }
            intent?.getStringExtra(EXTRA_TAB) == "descargar" -> { ui.tab = Tab.DOWNLOAD; ui.openPlaylist = null }
            intent?.getStringExtra(EXTRA_TAB) == "reproductor" -> { ui.tab = Tab.PLAYER; ui.openPlaylist = null }
        }
    }

    private fun hasAudioPermission(): Boolean {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    }

    private fun askPermissions() {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS); add(Manifest.permission.READ_MEDIA_AUDIO)
            } else add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) permissionLauncher.launch(wanted.toTypedArray())
    }

    companion object {
        const val EXTRA_TAB = "pestana"
    }
}
