package com.kregosh.mtglifetracker.shared

// Served from docs/join/ by GitHub Pages. Chat apps make https links tappable,
// unlike the app's own mtgtracker:// scheme; the page hands the code to the app.
const val INVITE_PAGE_URL = "https://kregosh.github.io/MtGLifeTracker/join/"

private val CODE_PATTERN = Regex("^[A-Z0-9]{6,8}$")
private val INVITE_PAGE = Link.parse(INVITE_PAGE_URL)!!

fun inviteUrl(code: String): String = "$INVITE_PAGE_URL?code=$code"

/** The session code in an invite link (https page or mtgtracker://join/CODE), or null. */
fun parseInviteCode(link: String): String? {
    val uri = Link.parse(link) ?: return null
    val code = when {
        uri.scheme == "mtgtracker" && uri.host == "join" ->
            uri.path.trim('/')
        uri.scheme == "https" && uri.host == INVITE_PAGE.host &&
            uri.path.trimEnd('/') == INVITE_PAGE.path.trimEnd('/') ->
            uri.query?.split('&')
                ?.map { it.split('=', limit = 2) }
                ?.firstOrNull { it.size == 2 && it[0] == "code" }
                ?.get(1)
        else -> null
    }
    return code?.uppercase()?.takeIf(CODE_PATTERN::matches)
}

/**
 * The parts of a `scheme://host/path?query#fragment` link that invites use. Plain Kotlin
 * rather than java.net.URI, so the same code runs in the browser.
 */
private class Link(val scheme: String, val host: String, val path: String, val query: String?) {
    companion object {
        fun parse(link: String): Link? {
            val schemeEnd = link.indexOf("://")
            if (schemeEnd <= 0 || link.any { it.isWhitespace() }) return null
            val scheme = link.substring(0, schemeEnd).lowercase()
            val rest   = link.substring(schemeEnd + 3).substringBefore('#')
            val authorityEnd = rest.indexOfAny(charArrayOf('/', '?')).let { if (it < 0) rest.length else it }
            // user@host:port → host; anything before the last @ is not the host.
            val host  = rest.substring(0, authorityEnd).substringAfterLast('@').substringBefore(':').lowercase()
            val tail  = rest.substring(authorityEnd)
            val path  = tail.substringBefore('?')
            val query = if ('?' in tail) tail.substringAfter('?') else null
            return if (host.isEmpty()) null else Link(scheme, host, path, query)
        }
    }
}
