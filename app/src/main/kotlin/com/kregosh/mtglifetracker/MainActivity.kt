package com.kregosh.mtglifetracker

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
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
import com.kregosh.mtglifetracker.ui.screens.HomeScreen
import com.kregosh.mtglifetracker.ui.screens.SessionScreen
import com.kregosh.mtglifetracker.ui.screens.SettingsScreen
import com.kregosh.mtglifetracker.ui.theme.AppColorScheme
import com.kregosh.mtglifetracker.ui.theme.LocalCardBackground
import com.kregosh.mtglifetracker.ui.theme.LocalHasBackground
import com.kregosh.mtglifetracker.ui.theme.MtGLifeTrackerTheme
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kregosh.mtglifetracker.data.UserPreferences
import com.kregosh.mtglifetracker.network.FirebaseSessionApi
import com.kregosh.mtglifetracker.network.FirebaseSessionConnection
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
                    wsFactory = { id, uid, name, startLife -> FirebaseSessionConnection(id, uid, name, startLife) },
                ) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        intent?.data?.let { uri ->
            if (uri.scheme == "mtgtracker" && uri.host == "join") {
                uri.lastPathSegment?.takeIf { it.isNotBlank() }?.let { vm.handleInviteLink(it) }
            }
        }

        setContent {
            val screen          by vm.screen.collectAsState()
            val bgUriString     by vm.backgroundImageUri.collectAsState()
            val cardBgUriString by vm.cardBackgroundImageUri.collectAsState()
            val colorSchemePref by vm.colorScheme.collectAsState()
            val context         = LocalContext.current

            val appColorScheme = when (colorSchemePref) {
                "light"  -> AppColorScheme.LIGHT
                "system" -> AppColorScheme.SYSTEM
                else     -> AppColorScheme.DARK
            }

            var bgBitmap     by remember { mutableStateOf<ImageBitmap?>(null) }
            var cardBgBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

            LaunchedEffect(bgUriString) {
                bgBitmap = withContext(Dispatchers.IO) {
                    bgUriString?.let { uriStr ->
                        runCatching {
                            context.contentResolver.openInputStream(Uri.parse(uriStr))
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

            MtGLifeTrackerTheme(colorScheme = appColorScheme) {
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

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.data?.let { uri ->
            if (uri.scheme == "mtgtracker" && uri.host == "join") {
                uri.lastPathSegment?.takeIf { it.isNotBlank() }?.let { vm.handleInviteLink(it) }
            }
        }
    }
}
