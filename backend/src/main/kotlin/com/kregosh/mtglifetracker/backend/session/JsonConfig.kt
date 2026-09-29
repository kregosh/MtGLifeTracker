package com.kregosh.mtglifetracker.backend.session

import kotlinx.serialization.json.Json

/** Single Json instance shared across backend code. */
val sharedJson = Json {
    classDiscriminator = "type"
    encodeDefaults = true
    ignoreUnknownKeys = true
}
