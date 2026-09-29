package com.kregosh.mtglifetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.kregosh.mtglifetracker.ui.theme.LocalHasBackground
import com.kregosh.mtglifetracker.viewmodel.SessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: SessionViewModel) {
    val loading by vm.homeLoading.collectAsState()
    val error   by vm.homeError.collectAsState()
    val hasBg   = LocalHasBackground.current

    var codeInput by remember { mutableStateOf("") }

    Scaffold(
        containerColor = if (hasBg) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("MtG Life Tracker") },
                colors = if (hasBg) TopAppBarDefaults.topAppBarColors(
                    containerColor         = Color.Black.copy(alpha = 0.45f),
                    titleContentColor      = Color.White,
                    actionIconContentColor = Color.White,
                ) else TopAppBarDefaults.topAppBarColors(),
                actions = {
                    IconButton(onClick = vm::openSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text  = "Welcome, ${vm.displayName.ifBlank { "Player" }}",
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick  = vm::createSession,
                enabled  = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Create new session")
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value          = codeInput,
                onValueChange  = { codeInput = it.uppercase() },
                label          = { Text("Invite code") },
                placeholder    = { Text("ABC123") },
                singleLine     = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction      = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = {
                    if (codeInput.isNotBlank()) vm.joinByCode(codeInput)
                }),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick  = { if (codeInput.isNotBlank()) vm.joinByCode(codeInput) },
                enabled  = codeInput.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Link, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Join session")
            }

            error?.let { msg ->
                Spacer(Modifier.height(16.dp))
                Text(msg, color = MaterialTheme.colorScheme.error)
            }

            if (loading) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            }
        }
    }
}

