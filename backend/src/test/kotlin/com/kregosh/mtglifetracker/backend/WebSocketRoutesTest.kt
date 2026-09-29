package com.kregosh.mtglifetracker.backend

import com.kregosh.mtglifetracker.backend.routes.webSocketRoutes
import com.kregosh.mtglifetracker.backend.session.SessionManager
import com.kregosh.mtglifetracker.backend.session.sharedJson
import com.kregosh.mtglifetracker.shared.ClientMessage
import com.kregosh.mtglifetracker.shared.ServerMessage
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.WebSockets
import io.ktor.websocket.*
import kotlin.test.*

class WebSocketRoutesTest {

    private fun encode(msg: ClientMessage): String =
        sharedJson.encodeToString(ClientMessage.serializer(), msg)

    private fun decode(frame: Frame): ServerMessage =
        sharedJson.decodeFromString(ServerMessage.serializer(), (frame as Frame.Text).readText())

    private fun testApp(
        block: suspend ApplicationTestBuilder.(sessions: SessionManager) -> Unit,
    ) = testApplication {
        val sessions = SessionManager()
        install(WebSockets)
        routing { webSocketRoutes(sessions) }
        block(sessions)
    }

    private fun ApplicationTestBuilder.wsClient() = createClient {
        install(ClientWebSockets)
    }

    @Test
    fun `unknown sessionId closes connection immediately`() = testApp { _ ->
        wsClient().webSocket("/ws/sessions/no-such-id") {
            val reason = closeReason.await()
            assertEquals(CloseReason.Codes.CANNOT_ACCEPT, reason?.knownReason)
        }
    }

    @Test
    fun `Join sends Joined then State back to client`() = testApp { sessions ->
        val room = sessions.create()
        wsClient().webSocket("/ws/sessions/${room.sessionId}") {
            send(Frame.Text(encode(ClientMessage.Join("u1", "Alice"))))

            val joined = decode(incoming.receive())
            assertIs<ServerMessage.Joined>(joined)
            assertEquals("u1", (joined as ServerMessage.Joined).userId)
            assertEquals(room.code, joined.sessionCode)

            val state = decode(incoming.receive())
            assertIs<ServerMessage.State>(state)
            assertEquals(1, (state as ServerMessage.State).users.size)
            assertEquals("Alice", state.users.first().displayName)
        }
    }

    @Test
    fun `second Join on same connection returns Already joined error`() = testApp { sessions ->
        val room = sessions.create()
        wsClient().webSocket("/ws/sessions/${room.sessionId}") {
            send(Frame.Text(encode(ClientMessage.Join("u1", "Alice"))))
            incoming.receive() // Joined
            incoming.receive() // State

            send(Frame.Text(encode(ClientMessage.Join("u1", "Alice"))))
            val err = decode(incoming.receive())
            assertIs<ServerMessage.Error>(err)
            assertEquals("Already joined", (err as ServerMessage.Error).message)
        }
    }

    @Test
    fun `Adjust before Join returns Join first error`() = testApp { sessions ->
        val room = sessions.create()
        wsClient().webSocket("/ws/sessions/${room.sessionId}") {
            send(Frame.Text(encode(ClientMessage.Adjust("life", -1))))
            val err = decode(incoming.receive())
            assertIs<ServerMessage.Error>(err)
            assertEquals("Join first", (err as ServerMessage.Error).message)
        }
    }

    @Test
    fun `AddCustomStat before Join returns Join first error`() = testApp { sessions ->
        val room = sessions.create()
        wsClient().webSocket("/ws/sessions/${room.sessionId}") {
            send(Frame.Text(encode(ClientMessage.AddCustomStat("Energy"))))
            val err = decode(incoming.receive())
            assertIs<ServerMessage.Error>(err)
            assertEquals("Join first", (err as ServerMessage.Error).message)
        }
    }

    @Test
    fun `AddCustomStat with blank name returns Invalid stat name`() = testApp { sessions ->
        val room = sessions.create()
        wsClient().webSocket("/ws/sessions/${room.sessionId}") {
            send(Frame.Text(encode(ClientMessage.Join("u1", "Alice"))))
            incoming.receive() // Joined
            incoming.receive() // State

            send(Frame.Text(encode(ClientMessage.AddCustomStat("   "))))
            val err = decode(incoming.receive())
            assertIs<ServerMessage.Error>(err)
            assertEquals("Invalid stat name", (err as ServerMessage.Error).message)
        }
    }

    @Test
    fun `AddCustomStat with name longer than 32 chars returns Invalid stat name`() = testApp { sessions ->
        val room = sessions.create()
        wsClient().webSocket("/ws/sessions/${room.sessionId}") {
            send(Frame.Text(encode(ClientMessage.Join("u1", "Alice"))))
            incoming.receive() // Joined
            incoming.receive() // State

            send(Frame.Text(encode(ClientMessage.AddCustomStat("A".repeat(33)))))
            val err = decode(incoming.receive())
            assertIs<ServerMessage.Error>(err)
            assertEquals("Invalid stat name", (err as ServerMessage.Error).message)
        }
    }

    @Test
    fun `malformed JSON returns Invalid message format error`() = testApp { sessions ->
        val room = sessions.create()
        wsClient().webSocket("/ws/sessions/${room.sessionId}") {
            send(Frame.Text("{not valid json}"))
            val err = decode(incoming.receive())
            assertIs<ServerMessage.Error>(err)
            assertEquals("Invalid message format", (err as ServerMessage.Error).message)
        }
    }

    @Test
    fun `Adjust after Join updates stat and broadcasts new State`() = testApp { sessions ->
        val room = sessions.create()
        wsClient().webSocket("/ws/sessions/${room.sessionId}") {
            send(Frame.Text(encode(ClientMessage.Join("u1", "Alice"))))
            incoming.receive() // Joined
            incoming.receive() // State (life=20)

            send(Frame.Text(encode(ClientMessage.Adjust("life", -3))))
            val state = decode(incoming.receive()) as ServerMessage.State
            assertEquals(17u, state.users.first().life)
        }
    }

    @Test
    fun `AddCustomStat after Join broadcasts State with new stat column`() = testApp { sessions ->
        val room = sessions.create()
        wsClient().webSocket("/ws/sessions/${room.sessionId}") {
            send(Frame.Text(encode(ClientMessage.Join("u1", "Alice"))))
            incoming.receive() // Joined
            incoming.receive() // State

            send(Frame.Text(encode(ClientMessage.AddCustomStat("Energy"))))
            val state = decode(incoming.receive()) as ServerMessage.State
            assertEquals(listOf("Energy"), state.customStatNames)
            assertEquals(0u, state.users.first().customStats["Energy"])
        }
    }

    @Test
    fun `disconnect removes user and prunes empty session`() = testApp { sessions ->
        val room = sessions.create()
        val sessionId = room.sessionId
        wsClient().webSocket("/ws/sessions/$sessionId") {
            send(Frame.Text(encode(ClientMessage.Join("u1", "Alice"))))
            incoming.receive() // Joined
            incoming.receive() // State
        }
        assertNull(sessions.getById(sessionId))
    }
}
