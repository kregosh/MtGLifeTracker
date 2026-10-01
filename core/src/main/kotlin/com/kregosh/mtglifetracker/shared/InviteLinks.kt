package com.kregosh.mtglifetracker.shared

import java.net.URI

// Served from docs/join/ by GitHub Pages. Chat apps make https links tappable,
// unlike the app's own mtgtracker:// scheme; the page hands the code to the app.
const val INVITE_PAGE_URL = "https://kregosh.github.io/MtGLifeTracker/join/"

private val CODE_PATTERN = Regex("^[A-Z0-9]{6,8}$")
private val INVITE_PAGE = URI(INVITE_PAGE_URL)

fun inviteUrl(code: String): String = "$INVITE_PAGE_URL?code=$code"

/** The session code in an invite link (https page or mtgtracker://join/CODE), or null. */
fun parseInviteCode(link: String): String? {
    val uri = runCatching { URI(link) }.getOrNull() ?: return null
    val code = when {
        uri.scheme == "mtgtracker" && uri.host == "join" ->
            uri.path?.trim('/')
        uri.scheme == "https" && uri.host == INVITE_PAGE.host &&
            uri.path?.trimEnd('/') == INVITE_PAGE.path.trimEnd('/') ->
            uri.rawQuery?.split('&')
                ?.map { it.split('=', limit = 2) }
                ?.firstOrNull { it.size == 2 && it[0] == "code" }
                ?.get(1)
        else -> null
    }
    return code?.uppercase()?.takeIf(CODE_PATTERN::matches)
}
