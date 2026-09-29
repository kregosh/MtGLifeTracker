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
    fun `Adjust serializes with correct type discriminator`() {
        val msg     = ClientMessage.Adjust(stat = "life", delta = -1)
        val encoded = json.encodeToString(ClientMessage.serializer(), msg)
        assert(encoded.contains("\"type\":\"adjust\"")) { "Expected type discriminator in: $encoded" }
        assert(encoded.contains("\"stat\":\"life\""))   { "Expected stat field in: $encoded" }
    }

    @Test
    fun `Adjust round-trips through JSON`() {
        val original = ClientMessage.Adjust(stat = "commander", delta = 3)
        val encoded  = json.encodeToString(ClientMessage.serializer(), original)
        val decoded  = json.decodeFromString(ClientMessage.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `AddCustomStat round-trips through JSON`() {
        val original = ClientMessage.AddCustomStat(name = "Energy")
        val encoded  = json.encodeToString(ClientMessage.serializer(), original)
        val decoded  = json.decodeFromString(ClientMessage.serializer(), encoded)
        assertEquals(original, decoded)
    }

    // ── ServerMessage ────────────────────────────────────────────────

    @Test
    fun `State message round-trips through JSON`() {
        val users = listOf(
            UserState("u1", "Alice"),
            UserState("u2", "Bob", life = 18u),
        )
        val original = ServerMessage.State(users, customStatNames = listOf("Energy"))
        val encoded  = json.encodeToString(ServerMessage.serializer(), original)
        val decoded  = json.decodeFromString(ServerMessage.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `State message with custom stats round-trips`() {
        val user = UserState(
            id             = "u1",
            displayName    = "Alice",
            life           = 20u,
            commanderDamage = 3u,
            poisonDamage   = 0u,
            customStats    = mapOf("Energy" to 5u),
        )
        val original = ServerMessage.State(listOf(user), listOf("Energy"))
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
    fun `UserState with max UInt life round-trips`() {
        val state   = UserState("u1", "Max", life = UInt.MAX_VALUE)
        val encoded = json.encodeToString(UserState.serializer(), state)
        val decoded = json.decodeFromString(UserState.serializer(), encoded)
        assertEquals(state, decoded)
    }

    @Test
    fun `UserState defaults: life=20, no damage, no custom stats`() {
        val state = UserState("u1", "Alice")
        assertEquals(20u,         state.life)
        assertEquals(0u,          state.commanderDamage)
        assertEquals(0u,          state.poisonDamage)
        assertEquals(emptyMap(),  state.customStats)
    }
}
