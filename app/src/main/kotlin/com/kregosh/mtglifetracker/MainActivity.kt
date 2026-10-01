package com.kregosh.mtglifetracker

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import com.kregosh.mtglifetracker.data.AppColorScheme
import com.kregosh.mtglifetracker.data.UserPreferences
import com.kregosh.mtglifetracker.network.FirebaseSessionApi
import com.kregosh.mtglifetracker.network.FirebaseSessionConnection
import com.kregosh.mtglifetracker.shared.parseInviteCode
import com.kregosh.mtglifetracker.ui.components.LightningOverlay
import com.kregosh.mtglifetracker.ui.screens.HomeScreen
import com.kregosh.mtglifetracker.ui.screens.SessionScreen
import com.kregosh.mtglifetracker.ui.screens.SettingsScreen
import com.kregosh.mtglifetracker.ui.theme.LocalCardBackground
import com.kregosh.mtglifetracker.ui.theme.LocalHasBackground
import com.kregosh.mtglifetracker.ui.theme.MtGLifeTrackerTheme
import com.kregosh.mtglifetracker.viewmodel.Screen
import com.kregosh.mtglifetracker.viewmodel.SessionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

        setContent {
            val screen          by vm.screen.collectAsState()
            val bgUriString     by vm.backgroundImageUri.collectAsState()
            val cardBgUriString by vm.cardBackgroundImageUri.collectAsState()
            val isStormPreset   by vm.isStormPreset.collectAsState()
            val colorSchemePref by vm.colorScheme.collectAsState()
            val context         = LocalContext.current

            var bgBitmap     by remember { mutableStateOf<ImageBitmap?>(null) }
            var cardBgBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

            LaunchedEffect(bgUriString, colorSchemePref) {
                bgBitmap = withContext(Dispatchers.IO) {
                    bgUriString?.let { uriStr ->
                        runCatching {
                            val resolveCtx = if (uriStr.startsWith("android.resource://")) {
                                val nightMode = when (colorSchemePref) {
                                    AppColorScheme.DARK   -> Configuration.UI_MODE_NIGHT_YES
                                    AppColorScheme.LIGHT  -> Configuration.UI_MODE_NIGHT_NO
                                    AppColorScheme.SYSTEM -> null
                                }
                                if (nightMode != null) {
                                    val cfg = Configuration(context.resources.configuration)
                                    cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
                                    context.createConfigurationContext(cfg)
                                } else context
                            } else context
                            resolveCtx.contentResolver.openInputStream(Uri.parse(uriStr))
                                ?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                        }.getOrNull()
                    }
                }
            }

            LaunchedEffect(cardBgUriString) {
                cardBgBitmap = withContext(Dispatchers.IO) {
                    cardBgUriString?.let { uriStr ->
                        runCatching {
                            context.contentResolver.openInputStream(Uri.parse(uriStr))
                                ?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                        }.getOrNull()
                    }
                }
            }

            MtGLifeTrackerTheme(colorScheme = colorSchemePref) {
                CompositionLocalProvider(
                    LocalHasBackground  provides (bgBitmap != null),
                    LocalCardBackground provides cardBgBitmap,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        bgBitmap?.let { bmp ->
                            Image(
                                bitmap             = bmp,
                                contentDescription = null,
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier.fillMaxSize(),
                            )
                        }

                        if (isStormPreset) {
                            LightningOverlay()
                        }

                        when (screen) {
                            is Screen.Home     -> HomeScreen(vm)
                            is Screen.Session  -> SessionScreen(vm)
                            is Screen.Settings -> SettingsScreen(vm)
                        }
                    }
                }
            }
        }
    }

    // Presets used to be saved by numeric resource ID, which never matched the
    // name-based storm check and isn't stable across builds.
    private fun migrateLegacyPresetUri() {
        val uri = vm.backgroundImageUri.value ?: return
        val prefix = "android.resource://$packageName/"
        val byId = mapOf(
            "$prefix${R.drawable.bg_arcane_storm}" to "${prefix}drawable/bg_arcane_storm",
            "$prefix${R.drawable.bg_mana_orbs}"    to "${prefix}drawable/bg_mana_orbs",
        )
        byId[uri]?.let(vm::setBackgroundImage)
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
