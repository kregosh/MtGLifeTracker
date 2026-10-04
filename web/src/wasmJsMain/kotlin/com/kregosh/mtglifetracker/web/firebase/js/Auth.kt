@file:JsModule("firebase/auth")

package com.kregosh.mtglifetracker.web.firebase.js

import kotlin.js.Promise

external interface Auth : JsAny {
    val currentUser: User?
    fun authStateReady(): Promise<JsAny?>
}

external interface User : JsAny {
    val uid: String
}

external interface UserCredential : JsAny {
    val user: User
}

/** Keeps the signed-in user in IndexedDB, so the player ID survives reloads. */
external fun getAuth(app: JsAny): Auth
external fun signInAnonymously(auth: Auth): Promise<UserCredential>
external fun connectAuthEmulator(auth: Auth, url: String, options: JsAny)
