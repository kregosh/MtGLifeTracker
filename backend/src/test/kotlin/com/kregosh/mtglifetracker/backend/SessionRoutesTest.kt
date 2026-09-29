package com.kregosh.mtglifetracker.backend

import com.kregosh.mtglifetracker.backend.routes.sessionRoutes
import com.kregosh.mtglifetracker.backend.session.SessionManager
import com.kregosh.mtglifetracker.backend.session.sharedJson
import com.kregosh.mtglifetracker.shared.CreateSessionResponse
import com.kregosh.mtglifetracker.shared.SessionInfoResponse
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionRoutesTest {

    private fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        install(ContentNegotiation) { json(sharedJson) }
        val sessions = SessionManager()
        routing { sessionRoutes(sessions) }
        block()
    }

    @Test
    fun `POST api-sessions returns 201 with sessionId and code`() = testApp {
        val resp = client.post("/api/sessions")
        assertEquals(HttpStatusCode.Created, resp.status)
        val body = sharedJson.decodeFromString(CreateSessionResponse.serializer(), resp.bodyAsText())
        assertTrue(body.sessionId.isNotBlank())
        assertTrue(body.sessionCode.isNotBlank())
    }

    @Test
    fun `GET api-sessions-id returns session info`() = testApp {
        val created = sharedJson.decodeFromString(
            CreateSessionResponse.serializer(),
            client.post("/api/sessions").bodyAsText(),
        )
        val resp = client.get("/api/sessions/${created.sessionId}")
        assertEquals(HttpStatusCode.OK, resp.status)
        val info = sharedJson.decodeFromString(SessionInfoResponse.serializer(), resp.bodyAsText())
        assertEquals(created.sessionId,   info.sessionId)
        assertEquals(created.sessionCode, info.sessionCode)
    }

    @Test
    fun `GET api-sessions-id returns 404 for unknown id`() = testApp {
        val resp = client.get("/api/sessions/does-not-exist")
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `GET api-join-code returns session info`() = testApp {
        val created = sharedJson.decodeFromString(
            CreateSessionResponse.serializer(),
            client.post("/api/sessions").bodyAsText(),
        )
        val resp = client.get("/api/join/${created.sessionCode}")
        assertEquals(HttpStatusCode.OK, resp.status)
        val info = sharedJson.decodeFromString(SessionInfoResponse.serializer(), resp.bodyAsText())
        assertEquals(created.sessionId, info.sessionId)
    }

    @Test
    fun `GET api-join-code is case-insensitive`() = testApp {
        val created = sharedJson.decodeFromString(
            CreateSessionResponse.serializer(),
            client.post("/api/sessions").bodyAsText(),
        )
        val resp = client.get("/api/join/${created.sessionCode.lowercase()}")
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `GET api-join-code returns 404 for unknown code`() = testApp {
        val resp = client.get("/api/join/ZZZZZZ")
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }
}
