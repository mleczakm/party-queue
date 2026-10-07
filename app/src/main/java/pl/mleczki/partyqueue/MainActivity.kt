package pl.mleczki.partyqueue

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        setContent { PartyTheme { HostApp(app, initialTab) } }
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun startPlaybackService() {
        ContextCompat.startForegroundService(this, Intent(this, PlaybackService::class.java))
    }
}
