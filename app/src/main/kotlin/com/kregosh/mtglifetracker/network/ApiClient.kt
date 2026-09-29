package com.kregosh.mtglifetracker.network

import com.kregosh.mtglifetracker.BuildConfig
import com.kregosh.mtglifetracker.shared.CreateSessionResponse
import com.kregosh.mtglifetracker.shared.SessionInfoResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Thin HTTP client for session lifecycle REST calls.
 *
 * WebSocket communication is handled separately by [SessionWebSocket].
 */
class ApiClient : SessionApi {

    private val json = Json {
        classDiscriminator  = "type"
        encodeDefaults      = true
        ignoreUnknownKeys   = true
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(Logging) { level = LogLevel.INFO }
    }

    private val base = BuildConfig.SERVER_BASE_URL

    override suspend fun createSession(): CreateSessionResponse =
        client.post("$base/api/sessions").body()

    override suspend fun getSessionByCode(code: String): SessionInfoResponse =
        client.get("$base/api/join/$code").body()

    override suspend fun getSessionById(sessionId: String): SessionInfoResponse =
        client.get("$base/api/sessions/$sessionId").body()

    override fun close() = client.close()
}
