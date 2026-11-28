package com.seerflix.tv.core.data

import com.seerflix.tv.core.network.SessionPreferences
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PinAttemptThrottleTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private var now = 1_000_000L

    private suspend fun PinAttemptThrottle.failTimes(n: Int, id: String = "p1"): Long {
        var last = 0L
        repeat(n) { last = recordFailure(listOf(id)) }
        return last
    }

    @Test
    fun `locks after five wrong PINs and escalates on each further round`() = runTest {
        val throttle = PinAttemptThrottle(null) { now }
        assertEquals(0L, throttle.failTimes(4))
        assertEquals(0L, throttle.remainingLockoutMs(listOf("p1")))
        assertEquals(30_000L, throttle.failTimes(1))
        assertEquals(30_000L, throttle.remainingLockoutMs(listOf("p1")))
        // Other profiles are not affected.
        assertEquals(0L, throttle.remainingLockoutMs(listOf("p2")))

        now += 30_000L
        assertEquals(0L, throttle.remainingLockoutMs(listOf("p1")))
        assertEquals(60_000L, throttle.failTimes(5))
        now += 60_000L
        assertEquals(5 * 60_000L, throttle.failTimes(5))
    }

    @Test
    fun `correct PIN resets the counter and escalation`() = runTest {
        val throttle = PinAttemptThrottle(null) { now }
        throttle.failTimes(5)
        now += 30_000L
        throttle.recordSuccess("p1")
        assertEquals(0L, throttle.failTimes(4))
        assertEquals(30_000L, throttle.failTimes(1))
    }

    @Test
    fun `lockout survives a new throttle instance through SessionPreferences`() = runTest {
        val json = Json { ignoreUnknownKeys = true }
        val prefs = SessionPreferences(java.io.File(tempFolder.root, "pin_throttle.preferences_pb"), json)
        PinAttemptThrottle(prefs) { now }.failTimes(5)

        val restarted = PinAttemptThrottle(prefs) { now + 10_000L }
        assertEquals(20_000L, restarted.remainingLockoutMs(listOf("p1")))
        // Half-finished rounds are kept too, so restarting the app doesn't hand out fresh tries.
        PinAttemptThrottle(prefs) { now }.failTimes(3, id = "p2")
        assertEquals(30_000L, PinAttemptThrottle(prefs) { now }.failTimes(2, id = "p2"))
    }
}
