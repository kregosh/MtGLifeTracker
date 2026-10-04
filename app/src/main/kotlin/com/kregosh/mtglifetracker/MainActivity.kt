package com.kregosh.mtglifetracker

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kregosh.mtglifetracker.data.UserPreferences
import com.kregosh.mtglifetracker.network.FirebaseSessionApi
import com.kregosh.mtglifetracker.network.FirebaseSessionConnection
import com.kregosh.mtglifetracker.shared.parseInviteCode
import com.kregosh.mtglifetracker.ui.AppRoot
import com.kregosh.mtglifetracker.ui.theme.migratedPresetUri
import com.kregosh.mtglifetracker.viewmodel.Screen
import com.kregosh.mtglifetracker.viewmodel.SessionViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: SessionViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SessionViewModel(
                    prefs     = UserPreferences(application),
                    api       = FirebaseSessionApi(),
                    connectionFactory = { id, uid, name, startLife -> FirebaseSessionConnection(id, uid, name, startLife) },
                ) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        migrateLegacyPresetUri()

        // On recreation (rotation, theme change) the launch intent was already handled.
        if (savedInstanceState == null && !handleInvite(intent)) vm.resumeLastSession()

        val platform = AndroidPlatform(this)
        setContent { AppRoot(vm, platform) }

        keepSessionAliveInBackground()
    }

    // Without notification permission the service still runs; its notification is just hidden.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** Runs [SessionService] for as long as the player is in a session. */
    private fun keepSessionAliveInBackground() {
        lifecycleScope.launch {
            combine(vm.screen, vm.sessionUi) { screen, ui -> (screen as? Screen.Session)?.let { ui.sessionCode } }
                .distinctUntilChanged()
                .collect { code ->
                    if (code == null) {
                        SessionService.stop(this@MainActivity)
                    } else {
                        askForNotificationsOnce()
                        SessionService.start(this@MainActivity, code)
                    }
                }
        }
    }

    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("session_service", MODE_PRIVATE)
        if (prefs.getBoolean("asked", false)) return
        prefs.edit().putBoolean("asked", true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onDestroy() {
        // Closing the app ends its session (see SessionViewModel.onCleared), so the service goes too.
        if (isFinishing) SessionService.stop(this)
        super.onDestroy()
    }

    // Presets used to be saved by numeric resource ID, which never matched the
    // name-based storm check and isn't stable across builds.
    private fun migrateLegacyPresetUri() {
        val legacyIds = mapOf(
            R.drawable.bg_arcane_storm to "bg_arcane_storm",
            R.drawable.bg_mana_orbs    to "bg_mana_orbs",
        )
        migratedPresetUri(vm.backgroundImageUri.value, packageName, legacyIds)?.let(vm::setBackgroundImage)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleInvite(intent)
    }

    private fun handleInvite(intent: Intent?): Boolean {
        val code = intent?.data?.let { parseInviteCode(it.toString()) } ?: return false
        vm.handleInviteLink(code)
        return true
    }
}
