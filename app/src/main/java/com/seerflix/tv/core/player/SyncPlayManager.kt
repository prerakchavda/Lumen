package com.seerflix.tv.core.player

import android.util.Log
import com.seerflix.tv.core.i18n.UiStrings
import com.seerflix.tv.core.i18n.stringsForLanguage
import com.seerflix.tv.core.model.SyncPlayGroup
import com.seerflix.tv.core.network.JellyfinApi
import com.seerflix.tv.core.network.JellyfinCreateSyncPlayRequest
import com.seerflix.tv.core.network.JellyfinJoinSyncPlayRequest
import com.seerflix.tv.core.network.JellyfinSeekSyncPlayRequest
import com.seerflix.tv.core.network.JellyfinSocketClient
import com.seerflix.tv.core.network.JellyfinSyncPlayBufferingRequest
import com.seerflix.tv.core.network.JellyfinSyncPlayGroupDto
import com.seerflix.tv.core.network.JellyfinSyncPlayNewQueueRequest
import com.seerflix.tv.core.network.JellyfinSyncPlayPingRequest
import com.seerflix.tv.core.network.ServerEvent
import com.seerflix.tv.core.network.SessionPreferences
import com.seerflix.tv.core.network.SocketState
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
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** What the player has to do for the group. */
sealed interface SyncPlayAction {
    /** The group switched to [itemId]: load it at [startPositionTicks] and report Ready. */
    data class LoadItem(
        val itemId: String,
        val playlistItemId: String,
        val startPositionTicks: Long,
        val isPlaying: Boolean
    ) : SyncPlayAction

    /** Unpause / Pause / Seek / Stop, to be applied at local time [atLocalMs]. */
    data class Command(
        val command: String,
        val atLocalMs: Long,
        val positionTicks: Long,
        val playlistItemId: String?
    ) : SyncPlayAction
}

/**
 * Jellyfin SyncPlay ("Watch Together").
 *
 * The server runs the group: members *request* pause / unpause / seek, every member reports
 * Buffering / Ready, and the server answers with timed commands over the WebSocket. Commands carry
 * a server timestamp, so the clock offset to the server is measured (NTP style) and each command
 * is applied at the same wall-clock moment on every TV.
 */
@Singleton
class SyncPlayManager @Inject constructor(
    private val jellyfinApi: JellyfinApi,
    private val socket: JellyfinSocketClient,
    private val json: Json,
    private val prefs: SessionPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** UI strings for the active profile's language (notices and default group names). */
    private val strings: UiStrings
        get() = stringsForLanguage(prefs.cachedUiLanguage)

    private val _availableGroups = MutableStateFlow<List<SyncPlayGroup>>(emptyList())
    val availableGroups: StateFlow<List<SyncPlayGroup>> = _availableGroups.asStateFlow()

    private val _activeGroup = MutableStateFlow<SyncPlayGroup?>(null)
    val activeGroup: StateFlow<SyncPlayGroup?> = _activeGroup.asStateFlow()

    private val _actions = MutableSharedFlow<SyncPlayAction>(extraBufferCapacity = 16)
    val actions: SharedFlow<SyncPlayAction> = _actions.asSharedFlow()

    /** Short notices for the UI ("Alex joined", "Waiting for others..."). */
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    /** Server clock minus local clock, in ms. */
    @Volatile var serverOffsetMs: Long = 0L
        private set

    @Volatile var currentPlaylistItemId: String? = null
        private set

    /** Item the local player is showing (set by the player), so a group item change can be routed. */
    @Volatile var localPlayingItemId: String? = null

    private var syncJob: Job? = null

    /** Group commands only arrive over the socket; without it, act locally instead of waiting forever. */
    val isActive: Boolean
        get() = _activeGroup.value != null && socket.state.value == SocketState.CONNECTED

    init {
        scope.launch {
            socket.events.collect { event ->
                when (event) {
                    is ServerEvent.SyncPlayCommand -> onCommand(event)
                    is ServerEvent.SyncPlayGroupUpdate -> onGroupUpdate(event)
                    else -> Unit
                }
            }
        }
    }

    fun serverNowMs(): Long = System.currentTimeMillis() + serverOffsetMs

    suspend fun refreshGroups(): Result<List<SyncPlayGroup>> = runCatching {
        val groups = jellyfinApi.getSyncPlayGroups().map { it.toModel() }
        _availableGroups.value = groups
        val currentId = _activeGroup.value?.groupId
        if (currentId != null) {
            _activeGroup.value = groups.firstOrNull { it.groupId == currentId }
        }
        groups
    }

    suspend fun createGroup(groupName: String): Result<SyncPlayGroup?> = runCatching {
        val clean = groupName.trim().ifBlank { strings.syncPlayDefaultRoom }
        socket.start()
        val existingIds = runCatching { jellyfinApi.getSyncPlayGroups().map { it.groupId }.toSet() }
            .getOrDefault(emptySet())
        _activeGroup.value = null
        jellyfinApi.createSyncPlayGroup(JellyfinCreateSyncPlayRequest(groupName = clean))
        val groups = jellyfinApi.getSyncPlayGroups().map { it.toModel() }
        _availableGroups.value = groups
        // The server's GroupJoined (over the socket) may already have named our group; otherwise
        // take the group that didn't exist before. Matching by name picked another household's
        // room with the same (default) name, and every command for ours was then ignored.
        val joinedViaSocket = _activeGroup.value?.groupId
        val created = groups.firstOrNull { it.groupId == joinedViaSocket }
            ?: groups.filter { it.groupId !in existingIds }
                .let { fresh -> fresh.singleOrNull() ?: fresh.firstOrNull { it.groupName.equals(clean, ignoreCase = true) } }
            ?: groups.firstOrNull { it.groupName.equals(clean, ignoreCase = true) }
            ?: groups.lastOrNull()
        _activeGroup.value = created
        startClockSync()
        created
    }

    suspend fun joinGroup(groupId: String): Result<SyncPlayGroup?> = runCatching {
        socket.start()
        jellyfinApi.joinSyncPlayGroup(JellyfinJoinSyncPlayRequest(groupId = groupId))
        val groups = jellyfinApi.getSyncPlayGroups().map { it.toModel() }
        _availableGroups.value = groups
        val joined = groups.firstOrNull { it.groupId == groupId }
        _activeGroup.value = joined
        startClockSync()
        joined
    }

    suspend fun leaveGroup(): Result<Unit> = runCatching {
        jellyfinApi.leaveSyncPlayGroup()
        clearGroup()
        refreshGroups()
    }

    private fun clearGroup() {
        _activeGroup.value = null
        currentPlaylistItemId = null
        syncJob?.cancel()
    }

    // ---- Requests from this TV ----

    suspend fun requestPause() = request { jellyfinApi.syncPlayPause() }

    suspend fun requestUnpause() = request { jellyfinApi.syncPlayUnpause() }

    suspend fun requestSeek(positionTicks: Long) =
        request { jellyfinApi.syncPlaySeek(JellyfinSeekSyncPlayRequest(positionTicks)) }

    /** Makes [itemId] the group's queue; the server answers with a PlayQueue update to everyone. */
    suspend fun startQueue(itemId: String, startPositionTicks: Long) = request {
        jellyfinApi.syncPlaySetNewQueue(
            JellyfinSyncPlayNewQueueRequest(
                playingQueue = listOf(itemId),
                playingItemPosition = 0,
                startPositionTicks = startPositionTicks
            )
        )
    }

    suspend fun reportBuffering(positionTicks: Long, isPlaying: Boolean) = reportState(false, positionTicks, isPlaying)

    suspend fun reportReady(positionTicks: Long, isPlaying: Boolean) = reportState(true, positionTicks, isPlaying)

    private suspend fun reportState(ready: Boolean, positionTicks: Long, isPlaying: Boolean) {
        val playlistItemId = currentPlaylistItemId ?: return
        if (_activeGroup.value == null) return
        val body = JellyfinSyncPlayBufferingRequest(
            whenUtc = JellyfinSocketClient.formatServerTime(serverNowMs()),
            positionTicks = positionTicks,
            isPlaying = isPlaying,
            playlistItemId = playlistItemId
        )
        runCatching {
            if (ready) jellyfinApi.syncPlayReady(body) else jellyfinApi.syncPlayBuffering(body)
        }.onFailure { Log.w(TAG, "Ready/Buffering report failed: ${it.message}") }
    }

    private suspend fun request(block: suspend () -> Unit): Boolean {
        if (_activeGroup.value == null) return false
        return runCatching { block() }
            .onFailure { Log.w(TAG, "SyncPlay request failed: ${it.message}") }
            .isSuccess
    }

    // ---- Clock ----

    private fun startClockSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive && _activeGroup.value != null) {
                measureOffset()
                delay(CLOCK_RESYNC_MS)
            }
        }
    }

    /** Best of a few NTP-style samples (lowest round trip = least asymmetric delay). */
    private suspend fun measureOffset() {
        var best: Pair<Long, Long>? = null // rtt to offset
        repeat(CLOCK_SAMPLES) {
            runCatching {
                val t0 = System.currentTimeMillis()
                val resp = jellyfinApi.getUtcTime()
                val t3 = System.currentTimeMillis()
                val t1 = JellyfinSocketClient.parseServerTime(resp.requestReceptionTime) ?: return@runCatching
                val t2 = JellyfinSocketClient.parseServerTime(resp.responseTransmissionTime) ?: return@runCatching
                val sample = clockSample(t0, t1, t2, t3)
                if (best == null || sample.first < best!!.first) best = sample
            }
            delay(150L)
        }
        best?.let { (rtt, offset) ->
            serverOffsetMs = offset
            runCatching { jellyfinApi.syncPlayPing(JellyfinSyncPlayPingRequest(rtt)) }
        }
    }

    // ---- Server messages ----

    private fun onCommand(event: ServerEvent.SyncPlayCommand) {
        val group = _activeGroup.value ?: return
        if (event.groupId.isNotBlank() && event.groupId != group.groupId) return
        _activeGroup.value = group.copy(
            state = when (event.command) {
                "Unpause" -> "Playing"
                "Pause", "Seek" -> "Paused"
                "Stop" -> "Idle"
                else -> group.state
            },
            isPlaying = event.command == "Unpause",
            positionTicks = event.positionTicks
        )
        _actions.tryEmit(
            SyncPlayAction.Command(
                command = event.command,
                atLocalMs = event.whenServerMs - serverOffsetMs,
                positionTicks = event.positionTicks,
                playlistItemId = event.playlistItemId
            )
        )
    }

    private fun onGroupUpdate(event: ServerEvent.SyncPlayGroupUpdate) {
        when (event.type) {
            "GroupJoined" -> {
                val dto = runCatching {
                    json.decodeFromJsonElement<JellyfinSyncPlayGroupDto>(event.data ?: return)
                }.getOrNull()
                if (dto != null) _activeGroup.value = dto.toModel()
                startClockSync()
            }
            "GroupLeft", "NotInGroup", "GroupDoesNotExist" -> {
                if (_activeGroup.value != null) _notices.tryEmit(strings.syncPlayNoticeLeft)
                clearGroup()
            }
            "LibraryAccessDenied" -> _notices.tryEmit(strings.syncPlayNoticeAccessDenied)
            "UserJoined" -> event.data.asText()?.let { _notices.tryEmit(strings.syncPlayNoticeUserJoined.format(it)) }
            "UserLeft" -> event.data.asText()?.let { _notices.tryEmit(strings.syncPlayNoticeUserLeft.format(it)) }
            "StateUpdate" -> {
                val state = (event.data as? JsonObject)?.get("State").asText() ?: return
                _activeGroup.value = _activeGroup.value?.copy(
                    state = state,
                    isPlaying = state.equals("Playing", ignoreCase = true)
                )
                if (state == "Waiting") _notices.tryEmit(strings.syncPlayNoticeWaiting)
            }
            "PlayQueue" -> onPlayQueue(event.data as? JsonObject ?: return)
        }
    }

    private fun onPlayQueue(data: JsonObject) {
        val update = parsePlayQueue(data) ?: return
        currentPlaylistItemId = update.playlistItemId
        _activeGroup.value = _activeGroup.value?.copy(playingItemId = update.itemId)
        if (update.reason in ITEM_CHANGE_REASONS) {
            _actions.tryEmit(
                SyncPlayAction.LoadItem(
                    itemId = update.itemId,
                    playlistItemId = update.playlistItemId,
                    startPositionTicks = update.startPositionTicks,
                    isPlaying = update.isPlaying
                )
            )
        }
    }

    private fun JellyfinSyncPlayGroupDto.toModel(): SyncPlayGroup = SyncPlayGroup(
        groupId = groupId,
        groupName = groupName.ifBlank { strings.syncPlayGroupFallback },
        state = state,
        participants = participants,
        isPlaying = state.equals("Playing", ignoreCase = true)
    )

    data class PlayQueueUpdate(
        val reason: String,
        val itemId: String,
        val playlistItemId: String,
        val startPositionTicks: Long,
        val isPlaying: Boolean
    )

    companion object {
        private const val TAG = "LumenSyncPlay"
        private const val CLOCK_SAMPLES = 3
        private const val CLOCK_RESYNC_MS = 60_000L

        /** Queue changes that mean "play a different item now"; others (shuffle, reorder) don't. */
        private val ITEM_CHANGE_REASONS = setOf("NewPlaylist", "SetCurrentItem", "NextItem", "PreviousItem")

        /** Returns (round trip, server - local offset) from one NTP-style exchange. */
        fun clockSample(t0: Long, t1: Long, t2: Long, t3: Long): Pair<Long, Long> {
            val rtt = (t3 - t0) - (t2 - t1)
            val offset = ((t1 - t0) + (t2 - t3)) / 2
            return rtt to offset
        }

        fun parsePlayQueue(data: JsonObject): PlayQueueUpdate? {
            val playlist = data["Playlist"] as? JsonArray ?: return null
            val index = (data["PlayingItemIndex"] as? JsonPrimitive)?.longOrNull?.toInt() ?: 0
            val entry = playlist.getOrNull(index) as? JsonObject ?: return null
            return PlayQueueUpdate(
                reason = data["Reason"].asText() ?: "NewPlaylist",
                itemId = normalizeId(entry["ItemId"].asText() ?: return null),
                playlistItemId = entry["PlaylistItemId"].asText() ?: return null,
                startPositionTicks = (data["StartPositionTicks"] as? JsonPrimitive)?.longOrNull ?: 0L,
                isPlaying = (data["IsPlaying"] as? JsonPrimitive)?.booleanOrNull ?: false
            )
        }

        private fun JsonElement?.asText(): String? = (this as? JsonPrimitive)?.contentOrNull

        /** Jellyfin ids appear both with and without dashes; compare them in one form. */
        fun normalizeId(id: String): String = id.replace("-", "").lowercase()
    }
}
