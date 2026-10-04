package com.kregosh.mtglifetracker.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.kregosh.mtglifetracker.shared.INVITE_PAGE_URL
import com.kregosh.mtglifetracker.shared.parseInviteCode
import com.kregosh.mtglifetracker.viewmodel.SessionViewModel
import com.kregosh.mtglifetracker.web.firebase.WebFirebase
import com.kregosh.mtglifetracker.web.firebase.WebSessionApi
import com.kregosh.mtglifetracker.web.firebase.WebSessionConnection
import com.kregosh.mtglifetracker.ui.AppRoot
import com.kregosh.mtglifetracker.web.platform.WebPlatform
import com.kregosh.mtglifetracker.web.platform.WithEmojiFont

private fun inviteCodeParam(): String? = js("new URLSearchParams(window.location.search).get('code')")

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val configured = WebFirebase.isConfigured
    val vm = SessionViewModel(
        prefs = BrowserPrefs(),
        api   = WebSessionApi(),
        connectionFactory = { sessionId, userId, name, startLife -> WebSessionConnection(sessionId, userId, name, startLife) },
    )
    if (configured) {
        // An invite link (?code=ABCD, as on the join page) joins straight away; otherwise
        // the page picks up the session it was last in.
        val code = inviteCodeParam()?.let { parseInviteCode("$INVITE_PAGE_URL?code=$it") }
        if (code != null) vm.handleInviteLink(code) else vm.resumeLastSession()
    }
    val platform = WebPlatform()
    ComposeViewport(viewportContainerId = "app") {
        if (configured) WithEmojiFont { AppRoot(vm, platform) } else NotConfigured()
    }
}

@Composable
private fun NotConfigured() {
    Box(Modifier.fillMaxSize().background(Color(0xFF1D1B20)).padding(24.dp), contentAlignment = Alignment.Center) {
        Text("This page isn't connected to a Firebase project yet.", color = Color.White)
    }
}
