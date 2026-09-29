package com.kregosh.mtglifetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kregosh.mtglifetracker.ui.screens.HomeScreen
import com.kregosh.mtglifetracker.ui.screens.SessionScreen
import com.kregosh.mtglifetracker.ui.theme.MtGLifeTrackerTheme
import com.kregosh.mtglifetracker.viewmodel.Screen
import com.kregosh.mtglifetracker.viewmodel.SessionViewModel

class MainActivity : ComponentActivity() {

    private val vm: SessionViewModel by viewModels { SessionViewModel.factory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle deep links: mtgtracker://join/{code}
        intent?.data?.let { uri ->
            if (uri.scheme == "mtgtracker" && uri.host == "join") {
                val code = uri.lastPathSegment
                if (!code.isNullOrBlank()) vm.handleInviteLink(code)
            }
        }

        setContent {
            MtGLifeTrackerTheme {
                val screen by vm.screen.collectAsState()
                when (screen) {
                    is Screen.Home    -> HomeScreen(vm)
                    is Screen.Session -> SessionScreen(vm)
                }
            }
        }
    }

    /** Handle deep links arriving while the app is already running. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.data?.let { uri ->
            if (uri.scheme == "mtgtracker" && uri.host == "join") {
                val code = uri.lastPathSegment
                if (!code.isNullOrBlank()) vm.handleInviteLink(code)
            }
        }
    }
}
