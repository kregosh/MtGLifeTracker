package com.kregosh.mtglifetracker.backend.routes

import com.kregosh.mtglifetracker.backend.session.ConnectedUser
import com.kregosh.mtglifetracker.backend.session.SessionManager
import com.kregosh.mtglifetracker.backend.session.sharedJson
import com.kregosh.mtglifetracker.shared.ClientMessage
import com.kregosh.mtglifetracker.shared.ServerMessage
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("WebSocketRoutes")

fun Route.webSocketRoutes() {

    // ws://host/ws/sessions/{sessionId}
    webSocket("/ws/sessions/{sessionId}") {
        val sessionId = call.parameters["sessionId"]
        val room      = sessionId?.let { SessionManager.getById(it) }

        if (room == null) {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Session not found"))
            return@webSocket
        }

        var connectedUser: ConnectedUser? = null

        try {
            for (frame in incoming) {
                if (frame !is Frame.Text) continue  // no inline lambda, plain for-loop ok

                val text = frame.readText()

                val msg = runCatching {
                    sharedJson.decodeFromString(ClientMessage.serializer(), text)
                }.getOrNull()

                if (msg == null) {
                    log.warn("Bad message from client: $text")
                    sendError("Invalid message format")
                    continue
                }

                handleMessage(msg, room, connectedUser) { user ->
                    connectedUser = user
                }
            }
        } catch (e: Exception) {
            log.debug("WebSocket closed with exception: ${e.message}")
        } finally {
            connectedUser?.let {
                room.removeUser(it.userId)
                room.broadcastState()
                SessionManager.pruneIfEmpty(room)
            }
        }
    }
}

private suspend fun DefaultWebSocketServerSession.handleMessage(
    msg          : ClientMessage,
    room         : com.kregosh.mtglifetracker.backend.session.SessionRoom,
    connectedUser: ConnectedUser?,
    setUser      : (ConnectedUser) -> Unit,
) {
    when (msg) {
        is ClientMessage.Join -> {
            if (connectedUser != null) {
                sendError("Already joined")
                return
            }
            val user = ConnectedUser(
                userId      = msg.userId,
                displayName = msg.displayName,
                socket      = this,
            )
            setUser(user)
            room.addUser(user)

            val joined = ServerMessage.Joined(userId = user.userId, sessionCode = room.code)
            send(Frame.Text(sharedJson.encodeToString(ServerMessage.serializer(), joined)))
            room.broadcastState()
        }

        ClientMessage.Increment -> {
            if (connectedUser == null) { sendError("Join first"); return }
            room.increment(connectedUser.userId)
            room.broadcastState()
        }

        ClientMessage.Decrement -> {
            if (connectedUser == null) { sendError("Join first"); return }
            room.decrement(connectedUser.userId)
            room.broadcastState()
        }
    }
}

private suspend fun DefaultWebSocketServerSession.sendError(message: String) {
    val err = ServerMessage.Error(message)
    send(Frame.Text(sharedJson.encodeToString(ServerMessage.serializer(), err)))
}
