package com.seerflix.tv.core.data

import com.seerflix.tv.core.network.PinThrottleEntry
import com.seerflix.tv.core.network.SessionPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brute-force throttle for profile PINs. It is app-wide and persisted (not per ViewModel) because a
 * 4-digit PIN is trivially enumerable with a remote if reopening the picker or killing the app
 * resets the counter. Every [MAX_ATTEMPTS] wrong guesses lock the profile for an escalating time;
 * a correct PIN clears it.
 */
@Singleton
class PinAttemptThrottle internal constructor(
    private val prefs: SessionPreferences?,
    private val clock: () -> Long
) {
    @Inject
    constructor(prefs: SessionPreferences) : this(prefs, System::currentTimeMillis)

    private val mutex = Mutex()
    private var state: MutableMap<String, PinThrottleEntry>? = null

    /** Milliseconds until any of [profileIds] may be tried again; 0 when none is locked. */
    suspend fun remainingLockoutMs(profileIds: Collection<String>): Long = mutex.withLock {
        val entries = load()
        val now = clock()
        val remaining = profileIds.maxOfOrNull { (entries[it]?.lockedUntilMs ?: 0L) - now } ?: 0L
        // Capped so a clock set backwards can't lock a profile for far longer than intended.
        remaining.coerceIn(0L, LOCKOUT_STEPS_MS.last())
    }

    suspend fun recordSuccess(profileId: String) = mutex.withLock {
        if (load().remove(profileId) != null) persist()
    }

    /**
     * Counts a wrong PIN against every profile in [profileIds] (a parent challenge tests all parent
     * PINs at once). Returns the lockout it started, or 0 when attempts remain.
     */
    suspend fun recordFailure(profileIds: Collection<String>): Long = mutex.withLock {
        val entries = load()
        val now = clock()
        var started = 0L
        for (id in profileIds) {
            val entry = entries[id] ?: PinThrottleEntry()
            val failures = entry.failures + 1
            entries[id] = if (failures < MAX_ATTEMPTS) {
                entry.copy(failures = failures)
            } else {
                val strikes = entry.strikes + 1
                val duration = LOCKOUT_STEPS_MS[minOf(strikes, LOCKOUT_STEPS_MS.size) - 1]
                started = maxOf(started, duration)
                PinThrottleEntry(failures = 0, strikes = strikes, lockedUntilMs = now + duration)
            }
        }
        persist()
        started
    }

    private suspend fun load(): MutableMap<String, PinThrottleEntry> =
        state ?: (prefs?.pinThrottleState().orEmpty().toMutableMap()).also { state = it }

    private suspend fun persist() {
        prefs?.savePinThrottleState(state.orEmpty().toMap())
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        val LOCKOUT_STEPS_MS = longArrayOf(30_000L, 60_000L, 5 * 60_000L, 15 * 60_000L, 60 * 60_000L)
    }
}
