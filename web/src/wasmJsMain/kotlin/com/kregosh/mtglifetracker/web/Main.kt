package com.kregosh.mtglifetracker.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.kregosh.mtglifetracker.shared.INVITE_PAGE_URL
import com.kregosh.mtglifetracker.shared.parseInviteCode
import com.kregosh.mtglifetracker.viewmodel.SessionViewModel
import com.kregosh.mtglifetracker.web.firebase.WebFirebase
import com.kregosh.mtglifetracker.web.firebase.WebSessionApi
import com.kregosh.mtglifetracker.web.firebase.WebSessionConnection
import com.kregosh.mtglifetracker.web.ui.WebApp

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
    ComposeViewport(viewportContainerId = "app") { WebApp(vm, configured) }
}
