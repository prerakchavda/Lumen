package com.seerflix.tv.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class UpcomingDateFormatterTest {

    private val today = LocalDate.of(2026, 5, 10)

    @Test
    fun `formats today for movies and tonight for episodes`() {
        assertEquals(
            "Today",
            UpcomingDateFormatter.formatComingSoonBadge("2026-05-10", today = today)
        )
        assertEquals(
            "S3 E4 • Tonight",
            UpcomingDateFormatter.formatComingSoonBadge("2026-05-10", episodeLabel = "S3 E4", today = today)
        )
    }

    @Test
    fun `formats tomorrow`() {
        assertEquals(
            "Tomorrow",
            UpcomingDateFormatter.formatComingSoonBadge("2026-05-11", today = today)
        )
        assertEquals(
            "S1 E2 • Tomorrow",
            UpcomingDateFormatter.formatComingSoonBadge("2026-05-11", episodeLabel = "S1 E2", today = today)
        )
    }

    @Test
    fun `formats days within two weeks`() {
        assertEquals(
            "In 5 days",
            UpcomingDateFormatter.formatComingSoonBadge("2026-05-15", today = today)
        )
        assertEquals(
            "In 14 days",
            UpcomingDateFormatter.formatComingSoonBadge("2026-05-24", today = today)
        )
    }

    @Test
    fun `formats short month and day beyond 14 days`() {
        assertEquals(
            "May 25",
            UpcomingDateFormatter.formatComingSoonBadge("2026-05-25", today = today)
        )
        assertEquals(
            "S2 E1 • Jun 12",
            UpcomingDateFormatter.formatComingSoonBadge("2026-06-12", episodeLabel = "S2 E1", today = today)
        )
    }

    @Test
    fun `returns null for past dates`() {
        assertNull(UpcomingDateFormatter.formatComingSoonBadge("2026-05-09", today = today))
    }

    @Test
    fun `parses full ISO offset timestamps`() {
        assertEquals(
            "In 3 days",
            UpcomingDateFormatter.formatComingSoonBadge("2026-05-13T00:00:00.0000000Z", today = today)
        )
    }
}
