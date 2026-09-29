package com.kregosh.mtglifetracker.shared

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val json = Json {
    classDiscriminator = "type"
    encodeDefaults     = true
    ignoreUnknownKeys  = true
}

class ProtocolSerializationTest {

    // ── ClientMessage ────────────────────────────────────────────────

    @Test
    fun `Join round-trips through JSON`() {
        val original = ClientMessage.Join(userId = "u1", displayName = "Alice")
        val encoded  = json.encodeToString(ClientMessage.serializer(), original)
        val decoded  = json.decodeFromString(ClientMessage.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `Increment serializes with correct type discriminator`() {
        val encoded = json.encodeToString(ClientMessage.serializer(), ClientMessage.Increment)
        assert(encoded.contains("\"type\":\"increment\"")) { "Expected type discriminator in: $encoded" }
    }

    @Test
    fun `Decrement round-trips through JSON`() {
        val encoded = json.encodeToString(ClientMessage.serializer(), ClientMessage.Decrement)
        val decoded = json.decodeFromString(ClientMessage.serializer(), encoded)
        assertIs<ClientMessage.Decrement>(decoded)
    }

    // ── ServerMessage ────────────────────────────────────────────────

    @Test
    fun `State message round-trips through JSON`() {
        val users = listOf(
            UserState("u1", "Alice", 20u),
            UserState("u2", "Bob",   18u),
        )
        val original = ServerMessage.State(users)
        val encoded  = json.encodeToString(ServerMessage.serializer(), original)
        val decoded  = json.decodeFromString(ServerMessage.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `Joined message round-trips through JSON`() {
        val original = ServerMessage.Joined(userId = "u1", sessionCode = "ABC123")
        val encoded  = json.encodeToString(ServerMessage.serializer(), original)
        val decoded  = json.decodeFromString(ServerMessage.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `Error message round-trips through JSON`() {
        val original = ServerMessage.Error("Join first")
        val encoded  = json.encodeToString(ServerMessage.serializer(), original)
        val decoded  = json.decodeFromString(ServerMessage.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `Unknown type field is ignored when decoding ServerMessage`() {
        val raw     = """{"type":"state","users":[],"extra":"ignored"}"""
        val decoded = json.decodeFromString(ServerMessage.serializer(), raw)
        assertIs<ServerMessage.State>(decoded)
        assertEquals(emptyList(), (decoded as ServerMessage.State).users)
    }

    // ── UInt boundary ────────────────────────────────────────────────

    @Test
    fun `UserState with max UInt value round-trips`() {
        val state   = UserState("u1", "Max", UInt.MAX_VALUE)
        val encoded = json.encodeToString(UserState.serializer(), state)
        val decoded = json.decodeFromString(UserState.serializer(), encoded)
        assertEquals(state, decoded)
    }
}
