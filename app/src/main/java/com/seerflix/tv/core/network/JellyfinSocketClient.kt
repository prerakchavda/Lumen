package com.seerflix.tv.core.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Messages the Jellyfin server pushes to this device over its WebSocket. */
sealed interface ServerEvent {
    /** "Play on" from another Jellyfin app. */
    data class Play(
        val itemIds: List<String>,
        val startIndex: Int,
        val startPositionTicks: Long,
        val mediaSourceId: String?,
        val playCommand: String
    ) : ServerEvent

    /** Pause / Unpause / PlayPause / Stop / Seek / NextTrack / PreviousTrack / Rewind / FastForward. */
    data class Playstate(val command: String, val seekPositionTicks: Long?) : ServerEvent

    data class GeneralCommand(val name: String, val arguments: Map<String, String>) : ServerEvent

    /** Items were added, updated or removed in a library. */
    data class LibraryChanged(val changedItemCount: Int) : ServerEvent

    /** Played state, progress or favourites changed for the signed-in user (possibly on another device). */
    data class UserDataChanged(val itemIds: List<String>) : ServerEvent

    data class SyncPlayCommand(
        val groupId: String,
        val playlistItemId: String?,
        val command: String,
        /** Server time (epoch ms) at which the command should take effect. */
        val whenServerMs: Long,
        val positionTicks: Long
    ) : ServerEvent

    data class SyncPlayGroupUpdate(val groupId: String?, val type: String, val data: JsonElement?) : ServerEvent
}

enum class SocketState { DISCONNECTED, CONNECTING, CONNECTED }

/**
 * The single live connection to the Jellyfin server.
 *
 * Opened while the app is in the foreground and a profile is signed in. It is what makes this TV
 * show up under "Play on" in other Jellyfin apps, carries SyncPlay group commands (which the
 * server only sends over the socket), and tells Home when the library or watch state changed.
 */
@Singleton
class JellyfinSocketClient @Inject constructor(
    @JellyfinClient private val okHttpClient: OkHttpClient,
    private val prefs: SessionPreferences,
    private val jellyfinApi: JellyfinApi,
    private val json: Json
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ServerEvent> = _events.asSharedFlow()

    private val _state = MutableStateFlow(SocketState.DISCONNECTED)
    val state: StateFlow<SocketState> = _state.asStateFlow()

    // The socket client gets its own ping interval and no read timeout; everything else (TLS
    // settings, failover base URL, auth header) comes from the API client.
    private val socketHttp: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            // Own dispatcher: the WebSocket reader loop holds its dispatcher slot for the whole
            // connection, which would otherwise cost the Jellyfin API one of its per-host slots.
            .dispatcher(Dispatcher())
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }

    @Volatile private var socket: WebSocket? = null
    @Volatile private var wanted = false
    @Volatile private var connectedToken: String? = null
    private var reconnectJob: Job? = null
    private var keepAliveJob: Job? = null
    @Volatile private var attempt = 0

    /** Called when the app comes to the foreground or the signed-in profile changes. */
    @Synchronized
    fun start() {
        wanted = true
        val token = prefs.cachedToken
        if (token.isNullOrBlank() || prefs.jellyfinUrlCandidates.isEmpty()) return
        if (socket != null && connectedToken == token) return
        closeSocket()
        connect(token)
    }

    /** Called when the app goes to the background or the user signs out. */
    @Synchronized
    fun stop() {
        wanted = false
        reconnectJob?.cancel()
        closeSocket()
    }

    fun send(messageType: String, data: JsonElement? = null) {
        val payload = buildMap<String, JsonElement> {
            put("MessageType", JsonPrimitive(messageType))
            if (data != null) put("Data", data)
        }
        socket?.send(JsonObject(payload).toString())
    }

    private fun closeSocket() {
        keepAliveJob?.cancel()
        socket?.close(1000, null)
        socket = null
        connectedToken = null
        _state.value = SocketState.DISCONNECTED
    }

    private fun connect(token: String) {
        _state.value = SocketState.CONNECTING
        connectedToken = token
        // Placeholder host: the failover interceptor rewrites it to the reachable server URL
        // (keeping any base path such as /jellyfin) and the auth interceptor adds the token header.
        val url = "http://jellyfin.local/socket?deviceId=${prefs.cachedDeviceId}"
        val request = Request.Builder().url(url).build()
        socket = socketHttp.newWebSocket(request, Listener(token))
    }

    private fun scheduleReconnect(token: String) {
        if (!wanted) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            attempt++
            // 2 s, 4 s, 8 s ... capped at 60 s.
            delay((1_000L shl attempt.coerceAtMost(6)).coerceAtMost(60_000L))
            synchronized(this@JellyfinSocketClient) {
                if (wanted && socket == null && prefs.cachedToken == token) connect(token)
            }
        }
    }

    private inner class Listener(private val token: String) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (webSocket !== socket) return
            attempt = 0
            _state.value = SocketState.CONNECTED
            Log.i(TAG, "Connected")
            scope.launch { reportCapabilities() }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket !== socket) return
            runCatching { handleMessage(text) }.onFailure { Log.w(TAG, "Bad message", it) }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = onGone(webSocket, null)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = onGone(webSocket, t)

        private fun onGone(webSocket: WebSocket, error: Throwable?) {
            synchronized(this@JellyfinSocketClient) {
                if (webSocket !== socket) return
                Log.i(TAG, "Disconnected: ${error?.message ?: "closed"}")
                keepAliveJob?.cancel()
                socket = null
                connectedToken = null
                _state.value = SocketState.DISCONNECTED
                scheduleReconnect(token)
            }
        }
    }

    /**
     * Tells the server whether this session can be remote-controlled ("Play on" target). With
     * remote control off, empty capabilities are posted: Jellyfin keeps a session's capabilities
     * across reconnects (same device id), so just not posting them left the TV controllable.
     */
    private suspend fun reportCapabilities() {
        val allow = prefs.cachedAllowRemoteControl
        runCatching {
            jellyfinApi.postSessionCapabilities(
                JellyfinSessionCapabilitiesRequest(
                    playableMediaTypes = if (allow) listOf("Video") else emptyList(),
                    supportedCommands = if (allow) SUPPORTED_COMMANDS else emptyList(),
                    supportsMediaControl = allow,
                    supportsPersistentIdentifier = true
                )
            )
        }.onFailure { Log.w(TAG, "Capabilities report failed: ${it.message}") }
    }

    /**
     * Remote-control commands from other Jellyfin apps. Dropped while "Allow remote control" is
     * off: the server still forwards them to any session with an open socket. Admin messages
     * (DisplayMessage), library/user-data updates and SyncPlay are not remote control.
     */
    private fun isBlockedRemoteControl(event: ServerEvent): Boolean {
        if (prefs.cachedAllowRemoteControl) return false
        return when (event) {
            is ServerEvent.Play, is ServerEvent.Playstate -> true
            is ServerEvent.GeneralCommand -> event.name != "DisplayMessage"
            else -> false
        }
    }

    private fun handleMessage(text: String) {
        val root = json.parseToJsonElement(text).jsonObject
        val type = root.string("MessageType") ?: return
        val data = root["Data"]
        val event: ServerEvent? = when (type) {
            "ForceKeepAlive" -> {
                val seconds = (data as? JsonPrimitive)?.longOrNull ?: 60L
                startKeepAlive(seconds)
                null
            }
            "KeepAlive" -> null
            "Play" -> data.obj()?.let { d ->
                ServerEvent.Play(
                    itemIds = d.stringList("ItemIds"),
                    startIndex = d.long("StartIndex")?.toInt() ?: 0,
                    startPositionTicks = d.long("StartPositionTicks") ?: 0L,
                    mediaSourceId = d.string("MediaSourceId"),
                    playCommand = d.string("PlayCommand") ?: "PlayNow"
                )
            }
            "Playstate" -> data.obj()?.let { d ->
                ServerEvent.Playstate(d.string("Command") ?: return, d.long("SeekPositionTicks"))
            }
            "GeneralCommand" -> data.obj()?.let { d ->
                val args = (d["Arguments"] as? JsonObject)?.mapValues { (_, v) ->
                    (v as? JsonPrimitive)?.contentOrNull.orEmpty()
                }.orEmpty()
                ServerEvent.GeneralCommand(d.string("Name") ?: return, args)
            }
            "LibraryChanged" -> data.obj()?.let { d ->
                val count = listOf("ItemsAdded", "ItemsUpdated", "ItemsRemoved", "FoldersAddedTo", "FoldersRemovedFrom")
                    .sumOf { (d[it] as? JsonArray)?.size ?: 0 }
                ServerEvent.LibraryChanged(count)
            }
            "UserDataChanged" -> data.obj()?.let { d ->
                val ids = (d["UserDataList"] as? JsonArray)?.mapNotNull {
                    (it as? JsonObject)?.string("ItemId")
                }.orEmpty()
                ServerEvent.UserDataChanged(ids)
            }
            "SyncPlayCommand" -> data.obj()?.let { d ->
                ServerEvent.SyncPlayCommand(
                    groupId = d.string("GroupId").orEmpty(),
                    playlistItemId = d.string("PlaylistItemId"),
                    command = d.string("Command") ?: return,
                    whenServerMs = parseServerTime(d.string("When")) ?: System.currentTimeMillis(),
                    positionTicks = d.long("PositionTicks") ?: 0L
                )
            }
            "SyncPlayGroupUpdate" -> data.obj()?.let { d ->
                ServerEvent.SyncPlayGroupUpdate(d.string("GroupId"), d.string("Type") ?: return, d["Data"])
            }
            else -> null
        }
        if (event != null && !isBlockedRemoteControl(event)) _events.tryEmit(event)
    }

    // Called from the socket reader thread; synchronized with stop()/closeSocket() so a keep-alive
    // message racing a disconnect can't leave a loop running after the socket closed.
    @Synchronized
    private fun startKeepAlive(timeoutSeconds: Long) {
        keepAliveJob?.cancel()
        if (socket == null) return
        keepAliveJob = scope.launch {
            val interval = (timeoutSeconds * 1000L / 2).coerceAtLeast(5_000L)
            while (isActive) {
                send("KeepAlive")
                delay(interval)
            }
        }
    }

    companion object {
        private const val TAG = "LumenSocket"

        /** General commands this app acts on; advertised so other clients only offer these. */
        val SUPPORTED_COMMANDS = listOf(
            "DisplayMessage",
            "DisplayContent",
            "GoHome",
            "Back",
            "PlayMediaSource",
            "PlayNext",
            "PlayState"
        )

        /** Jellyfin sends ISO-8601 UTC times with 7 fractional digits ("2026-11-15T10:00:00.1234567Z"). */
        fun parseServerTime(raw: String?): Long? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val trimmed = raw.trim().removeSuffix("Z")
                val (main, fraction) = trimmed.split('.').let { it[0] to it.getOrNull(1).orEmpty() }
                val millis = (fraction + "000").take(3).toLong()
                java.time.LocalDateTime.parse(main).toInstant(java.time.ZoneOffset.UTC).toEpochMilli() + millis
            }.getOrNull()
        }

        fun formatServerTime(epochMs: Long): String =
            java.time.Instant.ofEpochMilli(epochMs).toString()

        private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
        private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
        @Suppress("unused")
        private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
        private fun JsonObject.stringList(key: String): List<String> =
            (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
    }
}
