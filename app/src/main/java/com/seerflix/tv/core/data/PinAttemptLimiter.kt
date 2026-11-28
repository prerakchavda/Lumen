package com.seerflix.tv.core.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brute-force throttle for the 4-digit profile / parent PINs, shared by every PIN prompt and kept
 * on disk: an in-memory counter reset whenever the screen was recreated or the app force-stopped,
 * which made the 5-tries limit easy to sidestep with a remote.
 */
@Singleton
class PinAttemptLimiter internal constructor(
    private val store: SharedPreferences?,
    private val clock: () -> Long
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        System::currentTimeMillis
    )

    private var failedAttempts = store?.getInt(KEY_FAILED, 0) ?: 0
    private var lockedUntilMs = store?.getLong(KEY_LOCKED_UNTIL, 0L) ?: 0L

    /** Milliseconds until PIN entry is allowed again; 0 when it is allowed now. */
    @Synchronized
    fun remainingLockoutMs(): Long {
        val now = clock()
        // A clock set backwards must not stretch the lockout beyond its normal length.
        if (lockedUntilMs - now > LOCKOUT_MS) lockedUntilMs = now + LOCKOUT_MS
        return (lockedUntilMs - now).coerceAtLeast(0L)
    }

    /** Records a wrong PIN; returns true when it started a lockout. */
    @Synchronized
    fun recordFailure(): Boolean {
        failedAttempts++
        val locked = failedAttempts >= MAX_ATTEMPTS
        if (locked) {
            failedAttempts = 0
            lockedUntilMs = clock() + LOCKOUT_MS
        }
        persist()
        return locked
    }

    @Synchronized
    fun recordSuccess() {
        if (failedAttempts == 0 && lockedUntilMs == 0L) return
        failedAttempts = 0
        lockedUntilMs = 0L
        persist()
    }

    private fun persist() {
        store?.edit()
            ?.putInt(KEY_FAILED, failedAttempts)
            ?.putLong(KEY_LOCKED_UNTIL, lockedUntilMs)
            ?.apply()
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MS = 30_000L
        private const val PREFS_NAME = "pin_attempts"
        private const val KEY_FAILED = "failed"
        private const val KEY_LOCKED_UNTIL = "locked_until"

        /** In-memory limiter for tests and previews. */
        fun inMemory(clock: () -> Long = System::currentTimeMillis) = PinAttemptLimiter(null, clock)
    }
}
