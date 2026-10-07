package pl.mleczki.partyqueue

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startPlaybackService() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The host screen doubles as the player; keep it awake while it is on screen.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val app = application as PartyApp
        // A jukebox must not quit on BACK: keep the player alive and just hide the screen.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
        // Debug builds only: lets scripts open a tab or a page without tapping (see docs/QA.md).
        val debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        val initialTab = if (debuggable) intent.getIntExtra("tab", 0) else 0
        if (debuggable) intent.getStringExtra("browse")?.let { app.player.browseTo(it) }
        setContent {
            val mode by app.themeMode.collectAsStateWithLifecycle()
            PartyTheme(mode) {
                // The header is always a dark gradient, so the status bar icons stay light; the bottom follows the theme.
                val dark = LocalDarkTheme.current
                SideEffect {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    )
                }
                HostApp(app, mode, initialTab)
            }
        }
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun startPlaybackService() {
        ContextCompat.startForegroundService(this, Intent(this, PlaybackService::class.java))
    }
}
