package com.kregosh.mtglifetracker.web.firebase

import com.kregosh.mtglifetracker.web.firebase.js.*

private const val EMULATOR_PROJECT = "demo-mtglifetracker"

private fun configuredOptions(): JsAny? = js("window.MTG_FIREBASE_CONFIG || null")
private fun emulatorRequested(): Boolean = js("new URLSearchParams(window.location.search).has('emulator')")
private fun emulatorHost(): String = js("window.location.hostname || '127.0.0.1'")

/**
 * The Firebase app for this page. Its config comes from firebase-config.js, which the Pages
 * deploy writes from a repository secret, so no project details live in the source. With
 * `?emulator` in the URL the page talks to a local Firebase emulator instead.
 */
object WebFirebase {
    val usingEmulator: Boolean = emulatorRequested()

    private val app: JsAny by lazy {
        val options = if (usingEmulator) mapOf(
            "apiKey"      to "demo-key",
            "projectId"   to EMULATOR_PROJECT,
            "databaseURL" to "http://${emulatorHost()}:9000?ns=$EMULATOR_PROJECT",
        ).toJs()!! else configuredOptions() ?: error("Firebase is not configured (firebase-config.js)")
        initializeApp(options)
    }

    val auth: Auth by lazy {
        getAuth(app).also { if (usingEmulator) connectAuthEmulator(it, "http://${emulatorHost()}:9099", mapOf("disableWarnings" to true).toJs()!!) }
    }

    val db: Database by lazy {
        getDatabase(app).also { if (usingEmulator) connectDatabaseEmulator(it, emulatorHost(), 9000) }
    }

    val isConfigured: Boolean get() = usingEmulator || configuredOptions() != null
}
