package com.kregosh.mtglifetracker.backend.routes

import com.kregosh.mtglifetracker.backend.session.SessionManager
import com.kregosh.mtglifetracker.shared.CreateSessionResponse
import com.kregosh.mtglifetracker.shared.SessionInfoResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.sessionRoutes(sessions: SessionManager) {

    route("/api/sessions") {

        // POST /api/sessions  →  create a new session
        post {
            val room = sessions.create()
            call.respond(
                HttpStatusCode.Created,
                CreateSessionResponse(sessionId = room.sessionId, sessionCode = room.code),
            )
        }

        // GET /api/sessions/{id}  →  query by internal UUID
        get("{id}") {
            val id   = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val room = sessions.getById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Session not found")
            call.respond(SessionInfoResponse(room.sessionId, room.code, room.userCount()))
        }
    }

    route("/api/join") {

        // GET /api/join/{code}  →  look up session by short invite code
        get("{code}") {
            val code = call.parameters["code"]?.uppercase()
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            val room = sessions.getByCode(code)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Session not found")
            call.respond(SessionInfoResponse(room.sessionId, room.code, room.userCount()))
        }
    }
}
