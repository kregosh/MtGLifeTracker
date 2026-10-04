@file:JsModule("firebase/database")

package com.kregosh.mtglifetracker.web.firebase.js

import kotlin.js.Promise

external interface Database : JsAny

external interface DatabaseReference : JsAny {
    val key: String?
}

external interface DataSnapshot : JsAny {
    val key: String?
    fun `val`(): JsAny?
    fun exists(): Boolean
}

external interface OnDisconnect : JsAny {
    fun set(value: JsAny?): Promise<JsAny?>
    fun remove(): Promise<JsAny?>
    fun cancel(): Promise<JsAny?>
}

external fun getDatabase(app: JsAny): Database
external fun connectDatabaseEmulator(db: Database, host: String, port: Int)
external fun ref(db: Database, path: String): DatabaseReference
external fun child(parent: DatabaseReference, path: String): DatabaseReference
external fun get(query: DatabaseReference): Promise<DataSnapshot>
external fun set(ref: DatabaseReference, value: JsAny?): Promise<JsAny?>
external fun update(ref: DatabaseReference, values: JsAny): Promise<JsAny?>
external fun remove(ref: DatabaseReference): Promise<JsAny?>
external fun runTransaction(ref: DatabaseReference, transactionUpdate: (JsAny?) -> JsAny?): Promise<JsAny?>
/** Returns the function that stops listening. */
external fun onValue(query: DatabaseReference, callback: (DataSnapshot) -> Unit, cancelCallback: (JsAny) -> Unit): JsAny
external fun onChildAdded(query: DatabaseReference, callback: (DataSnapshot) -> Unit): JsAny
external fun onDisconnect(ref: DatabaseReference): OnDisconnect
external fun serverTimestamp(): JsAny
