package com.kregosh.mtglifetracker.web.firebase.js

import kotlinx.serialization.json.*

// Small bridges between Kotlin and the JS values the Firebase SDK hands around. Values
// cross as JSON, so they arrive in the same shape SessionSchema already parses on Android
// (maps, lists, Long/Double numbers, strings, booleans).

private fun jsonStringify(value: JsAny?): String = js("JSON.stringify(value === undefined ? null : value)")
private fun jsonParse(text: String): JsAny? = js("JSON.parse(text)")

fun callFunction(f: JsAny): Unit = js("f()")
fun errorMessage(error: JsAny): String = js("String(error && error.message ? error.message : error)")
fun setProperty(target: JsAny, key: String, value: JsAny?): Unit = js("{ target[key] = value; }")
fun nowMillis(): Double = js("Date.now()")
fun randomId(): String = js("crypto.randomUUID()")

/** A JS value as plain Kotlin: Map, List, Long, Double, String, Boolean or null. */
fun JsAny?.toKotlin(): Any? = Json.parseToJsonElement(jsonStringify(this)).toPlain()

/** Plain Kotlin values (as above) as a JS value. */
fun Any?.toJs(): JsAny? = jsonParse(toJsonElement().toString())

private fun JsonElement.toPlain(): Any? = when (this) {
    JsonNull         -> null
    is JsonPrimitive -> if (isString) content else booleanOrNull ?: longOrNull ?: doubleOrNull
    is JsonObject    -> mapValues { (_, v) -> v.toPlain() }
    is JsonArray     -> map { it.toPlain() }
}

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null          -> JsonNull
    is String     -> JsonPrimitive(this)
    is Boolean    -> JsonPrimitive(this)
    is Number     -> JsonPrimitive(this)
    is Map<*, *>  -> JsonObject(entries.associate { (k, v) -> k.toString() to v.toJsonElement() })
    is List<*>    -> JsonArray(map { it.toJsonElement() })
    else          -> error("Can't send ${this::class.simpleName} to Firebase")
}
