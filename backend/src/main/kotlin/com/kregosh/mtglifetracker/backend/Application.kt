package com.kregosh.mtglifetracker.backend

import com.kregosh.mtglifetracker.backend.routes.sessionRoutes
import com.kregosh.mtglifetracker.backend.routes.webSocketRoutes
import com.kregosh.mtglifetracker.backend.session.SessionManager
import com.kregosh.mtglifetracker.backend.session.sharedJson
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlin.time.Duration.Companion.seconds

fun main() {
    embeddedServer(CIO, port = System.getenv("PORT")?.toIntOrNull() ?: 8080) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    install(WebSockets) {
        pingPeriod   = 15.seconds
        timeout      = 60.seconds
        maxFrameSize = Long.MAX_VALUE
        masking      = false
    }

    install(ContentNegotiation) {
        json(sharedJson)
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
    }

    install(CallLogging)

    val sessions = SessionManager()

    routing {
        sessionRoutes(sessions)
        webSocketRoutes(sessions)
    }
}
