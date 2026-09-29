package com.kregosh.mtglifetracker.network

import com.kregosh.mtglifetracker.BuildConfig
import com.kregosh.mtglifetracker.shared.ClientMessage
import com.kregosh.mtglifetracker.shared.ServerMessage
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("SessionWebSocket")

sealed interface WsState {
    object Connecting   : WsState
    object Connected    : WsState
    object Reconnecting : WsState
    object Closed       : WsState   // intentional user-driven teardown
    data class Failed(val reason: String) : WsState
}

class SessionWebSocket(
    private val sessionId: String,
    private val userId: String,
    private val displayName: String,
) : SessionConnection {

    private val json = Json {
        classDiscriminator = "type"
        encodeDefaults     = true
        ignoreUnknownKeys  = true
    }

    private val client = HttpClient(OkHttp) { install(WebSockets) }
    private val scope  = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _messages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)
    override val messages: SharedFlow<ServerMessage> = _messages.asSharedFlow()

    private val _state = MutableStateFlow<WsState>(WsState.Connecting)
    override val connectionState: StateFlow<WsState> = _state.asStateFlow()

    private val commandQueue = Channel<ClientMessage>(Channel.BUFFERED)

    override fun connect() {
        scope.launch { runWithRetry() }
    }

    private suspend fun runWithRetry() {
        var attempt = 0
        while (scope.isActive) {
            _state.value = if (attempt == 0) WsState.Connecting else WsState.Reconnecting
            try {
                client.webSocket("${BuildConfig.SERVER_WS_URL}/ws/sessions/$sessionId") {
                    _state.value = WsState.Connected

                    val join = ClientMessage.Join(userId = userId, displayName = displayName)
                    send(Frame.Text(json.encodeToString(ClientMessage.serializer(), join)))

                    val sendJob = launch {
                        try {
                            while (true) {
                                val msg = commandQueue.receive()
                                send(Frame.Text(json.encodeToString(ClientMessage.serializer(), msg)))
                            }
                        } catch (_: ClosedReceiveChannelException) {}
                    }

                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val text = frame.readText()
                        runCatching { json.decodeFromString(ServerMessage.serializer(), text) }
                            .onSuccess { _messages.emit(it) }
                            .onFailure { log.warn("Unrecognised server message: $text") }
                    }
                    sendJob.cancel()
                }
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                log.warn("WebSocket error (attempt ${++attempt}): ${e.message}")
            }

            if (!scope.isActive) break
            val delayMs = minOf(2_000L * (1 shl minOf(attempt - 1, 4)), 30_000L)
            log.info("Reconnecting in ${delayMs}ms …")
            delay(delayMs)
        }
        if (_state.value !is WsState.Closed) {
            _state.value = WsState.Failed("Disconnected")
        }
    }

    override fun adjust(stat: String, delta: Int) {
        scope.launch { commandQueue.send(ClientMessage.Adjust(stat, delta)) }
    }

    override fun addCustomStat(name: String) {
        scope.launch { commandQueue.send(ClientMessage.AddCustomStat(name)) }
    }

    override fun close() {
        _state.value = WsState.Closed
        commandQueue.close()
        scope.cancel()
        client.close()
    }
}
