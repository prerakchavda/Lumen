package com.seerflix.tv.core.player

import com.seerflix.tv.core.model.SyncPlayGroup
import com.seerflix.tv.core.network.JellyfinApi
import com.seerflix.tv.core.network.JellyfinCreateSyncPlayRequest
import com.seerflix.tv.core.network.JellyfinJoinSyncPlayRequest
import com.seerflix.tv.core.network.JellyfinSeekSyncPlayRequest
import com.seerflix.tv.core.network.JellyfinSyncPlayGroupDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncPlayManager @Inject constructor(
    private val jellyfinApi: JellyfinApi
) {
    private val _availableGroups = MutableStateFlow<List<SyncPlayGroup>>(emptyList())
    val availableGroups: StateFlow<List<SyncPlayGroup>> = _availableGroups.asStateFlow()

    private val _activeGroup = MutableStateFlow<SyncPlayGroup?>(null)
    val activeGroup: StateFlow<SyncPlayGroup?> = _activeGroup.asStateFlow()

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
        val clean = groupName.trim().ifBlank { "Living Room Watch Party" }
        jellyfinApi.createSyncPlayGroup(JellyfinCreateSyncPlayRequest(groupName = clean))
        val groups = jellyfinApi.getSyncPlayGroups().map { it.toModel() }
        _availableGroups.value = groups
        val created = groups.firstOrNull { it.groupName.equals(clean, ignoreCase = true) }
            ?: groups.lastOrNull()
        _activeGroup.value = created
        created
    }

    suspend fun joinGroup(groupId: String): Result<SyncPlayGroup?> = runCatching {
        jellyfinApi.joinSyncPlayGroup(JellyfinJoinSyncPlayRequest(groupId = groupId))
        val groups = jellyfinApi.getSyncPlayGroups().map { it.toModel() }
        _availableGroups.value = groups
        val joined = groups.firstOrNull { it.groupId == groupId }
        _activeGroup.value = joined
        joined
    }

    suspend fun leaveGroup(): Result<Unit> = runCatching {
        jellyfinApi.leaveSyncPlayGroup()
        _activeGroup.value = null
        refreshGroups()
    }

    suspend fun notifyPause() {
        if (_activeGroup.value != null) {
            runCatching { jellyfinApi.syncPlayPause() }
        }
    }

    suspend fun notifyUnpause() {
        if (_activeGroup.value != null) {
            runCatching { jellyfinApi.syncPlayUnpause() }
        }
    }

    suspend fun notifySeek(positionTicks: Long) {
        if (_activeGroup.value != null) {
            runCatching { jellyfinApi.syncPlaySeek(JellyfinSeekSyncPlayRequest(positionTicks)) }
        }
    }

    private fun JellyfinSyncPlayGroupDto.toModel(): SyncPlayGroup = SyncPlayGroup(
        groupId = groupId,
        groupName = groupName.ifBlank { "Watch Party" },
        state = state,
        participants = participants,
        isPlaying = state.equals("Playing", ignoreCase = true)
    )
}
