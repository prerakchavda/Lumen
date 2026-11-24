package com.seerflix.tv.core.data

import com.seerflix.tv.core.network.SeerrMovieDetailsDto
import com.seerflix.tv.core.network.SeerrReleaseCountryDto
import com.seerflix.tv.core.network.SeerrReleaseDateDto
import com.seerflix.tv.core.network.SeerrReleasesDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StreamingDiscoveryTest {

    private val today = LocalDate.of(2026, 4, 1)

    @Test
    fun `theater-only recent movie is excluded from home availability`() {
        val theaterOnly = movieWithReleases(
            id = 1,
            releaseDate = "2026-03-20",
            releases = listOf(
                SeerrReleaseCountryDto(
                    countryCode = "US",
                    releaseDates = listOf(
                        SeerrReleaseDateDto(certification = "PG-13", releaseDate = "2026-03-20T00:00:00.000Z", type = 3)
                    )
                )
            )
        )
        assertNull(MovieReleaseClassifier.homeReleaseDate(theaterOnly, "US"))
        assertFalse(MovieReleaseClassifier.isAvailableAtHome(theaterOnly, today, "US"))
    }

    @Test
    fun `movie with future digital date is excluded from home availability and classified as coming soon`() {
        val upcomingDigital = movieWithReleases(
            id = 2,
            releaseDate = "2026-03-10",
            releases = listOf(
                SeerrReleaseCountryDto(
                    countryCode = "US",
                    releaseDates = listOf(
                        SeerrReleaseDateDto(releaseDate = "2026-03-10T00:00:00.000Z", type = 3),
                        SeerrReleaseDateDto(releaseDate = "2026-04-14T00:00:00.000Z", type = 4)
                    )
                )
            )
        )
        assertFalse(MovieReleaseClassifier.isAvailableAtHome(upcomingDigital, today, "US"))
        val homeDate = MovieReleaseClassifier.homeReleaseDate(upcomingDigital, "US")
        assertEquals(LocalDate.of(2026, 4, 14), homeDate?.date)
        assertEquals(HomeReleaseType.DIGITAL, homeDate?.type)
        assertEquals("Digital • In 13 days", MovieReleaseClassifier.formatUpcomingHomeBadge(homeDate!!, today))
    }

    @Test
    fun `movie with past digital or physical date is available at home`() {
        val outOnDigital = movieWithReleases(
            id = 3,
            releaseDate = "2026-02-01",
            releases = listOf(
                SeerrReleaseCountryDto(
                    countryCode = "US",
                    releaseDates = listOf(
                        SeerrReleaseDateDto(releaseDate = "2026-02-01T00:00:00.000Z", type = 3),
                        SeerrReleaseDateDto(releaseDate = "2026-03-25T00:00:00.000Z", type = 4),
                        SeerrReleaseDateDto(releaseDate = "2026-05-01T00:00:00.000Z", type = 5)
                    )
                )
            )
        )
        assertTrue(MovieReleaseClassifier.isAvailableAtHome(outOnDigital, today, "US"))
        assertEquals(LocalDate.of(2026, 3, 25), MovieReleaseClassifier.homeReleaseDate(outOnDigital, "US")?.date)
    }

    @Test
    fun `preferred country is chosen before US and international fallback`() {
        val multiRegion = movieWithReleases(
            id = 4,
            releaseDate = "2026-02-01",
            releases = listOf(
                SeerrReleaseCountryDto(
                    countryCode = "US",
                    releaseDates = listOf(SeerrReleaseDateDto(releaseDate = "2026-04-20T00:00:00.000Z", type = 5))
                ),
                SeerrReleaseCountryDto(
                    countryCode = "GB",
                    releaseDates = listOf(SeerrReleaseDateDto(releaseDate = "2026-04-05T00:00:00.000Z", type = 4))
                )
            )
        )
        assertEquals(LocalDate.of(2026, 4, 5), MovieReleaseClassifier.homeReleaseDate(multiRegion, "GB")?.date)
        assertEquals(LocalDate.of(2026, 4, 20), MovieReleaseClassifier.homeReleaseDate(multiRegion, "US")?.date)
        assertEquals(HomeReleaseType.PHYSICAL, MovieReleaseClassifier.homeReleaseDate(multiRegion, "US")?.type)
    }

    @Test
    fun `older catalog movie with only theatrical entry past 120 days is still allowed`() {
        val olderCatalog = movieWithReleases(
            id = 5,
            releaseDate = "2024-06-01",
            releases = listOf(
                SeerrReleaseCountryDto(
                    countryCode = "US",
                    releaseDates = listOf(SeerrReleaseDateDto(releaseDate = "2024-06-01T00:00:00.000Z", type = 3))
                )
            )
        )
        assertTrue(MovieReleaseClassifier.isAvailableAtHome(olderCatalog, today, "US"))
    }

    @Test
    fun `missing typed releases falls back cleanly to releaseDate`() {
        val stub = SeerrMovieDetailsDto(id = 6, title = "Stub", releaseDate = "2026-03-10", releases = null)
        assertTrue(MovieReleaseClassifier.isAvailableAtHome(stub, today, "US"))

        val futureStub = SeerrMovieDetailsDto(id = 7, title = "Future Stub", releaseDate = "2026-08-01", releases = null)
        assertFalse(MovieReleaseClassifier.isAvailableAtHome(futureStub, today, "US"))
    }

    private fun movieWithReleases(
        id: Int,
        releaseDate: String,
        releases: List<SeerrReleaseCountryDto>
    ) = SeerrMovieDetailsDto(
        id = id,
        title = "Movie $id",
        releaseDate = releaseDate,
        releases = SeerrReleasesDto(results = releases)
    )
}
