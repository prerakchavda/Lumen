package com.seerflix.tv.core.data

import com.seerflix.tv.core.model.CatalogRow
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.core.network.JellyfinItemDto
import kotlinx.serialization.Serializable
import kotlin.math.ln

/** How a profile rated a title: Netflix's "Not for me" / "I like this" / "Love this!". */
@Serializable
enum class TasteRating(val weight: Double) {
    DISLIKE(-2.0),
    LIKE(2.0),
    LOVE(3.5)
}

/**
 * One rated (or "not interested") title. Keyed by TMDB id and/or Jellyfin id so a rating given
 * on a Seerr result still applies once the title is in the library, and the other way round.
 */
@Serializable
data class TasteEntry(
    val mediaType: MediaType,
    val tmdbId: Int? = null,
    val jellyfinId: String? = null,
    val title: String = "",
    val genres: List<String> = emptyList(),
    val rating: TasteRating? = null,
    val updatedAtMs: Long = 0L
) {
    val keys: List<String>
        get() = DiscoveryEngine.keysOf(mediaType, tmdbId, jellyfinId)
}

/** Everything Lumen remembers about one profile's taste (stored per Jellyfin user). */
@Serializable
data class TasteData(
    val ratings: List<TasteEntry> = emptyList(),
    val notInterested: List<TasteEntry> = emptyList(),
    /** Home row id -> how many times a title was opened from it. */
    val rowOpens: Map<String, Int> = emptyMap()
) {
    fun ratingFor(item: UnifiedMediaItem): TasteRating? {
        val keys = DiscoveryEngine.keysOf(item).toSet()
        if (keys.isEmpty()) return null
        return ratings.firstOrNull { e -> e.keys.any { it in keys } }?.rating
    }

    fun isNotInterested(item: UnifiedMediaItem): Boolean {
        val keys = DiscoveryEngine.keysOf(item).toSet()
        return keys.isNotEmpty() && notInterested.any { e -> e.keys.any { it in keys } }
    }

    fun withRating(item: UnifiedMediaItem, rating: TasteRating?, nowMs: Long): TasteData {
        val keys = DiscoveryEngine.keysOf(item).toSet()
        val others = ratings.filterNot { e -> e.keys.any { it in keys } }
        val updated = if (rating == null) others else {
            others + TasteEntry(
                mediaType = DiscoveryEngine.titleType(item.mediaType),
                tmdbId = item.tmdbId,
                jellyfinId = item.jellyfinId,
                title = item.seriesName ?: item.title,
                genres = item.genres,
                rating = rating,
                updatedAtMs = nowMs
            )
        }
        // A thumbs up overrides an earlier "Not interested".
        val ni = if (rating == TasteRating.LIKE || rating == TasteRating.LOVE) {
            notInterested.filterNot { e -> e.keys.any { it in keys } }
        } else notInterested
        return copy(ratings = updated.takeLast(MAX_RATINGS), notInterested = ni)
    }

    fun withNotInterested(item: UnifiedMediaItem, notInterested: Boolean, nowMs: Long): TasteData {
        val keys = DiscoveryEngine.keysOf(item).toSet()
        val others = this.notInterested.filterNot { e -> e.keys.any { it in keys } }
        val updated = if (!notInterested) others else {
            others + TasteEntry(
                mediaType = DiscoveryEngine.titleType(item.mediaType),
                tmdbId = item.tmdbId,
                jellyfinId = item.jellyfinId,
                title = item.seriesName ?: item.title,
                genres = item.genres,
                updatedAtMs = nowMs
            )
        }
        return copy(notInterested = updated.takeLast(MAX_NOT_INTERESTED))
    }

    fun withRowOpened(rowId: String): TasteData {
        val base = DiscoveryEngine.engagementRowKey(rowId) ?: return this
        val next = rowOpens + (base to ((rowOpens[base] ?: 0) + 1))
        // Keep counts small and recent-leaning: halve everything once any row gets busy.
        val decayed = if ((next[base] ?: 0) > ROW_OPEN_CAP) next.mapValues { it.value / 2 } else next
        return copy(rowOpens = decayed)
    }

    companion object {
        const val MAX_RATINGS = 500
        const val MAX_NOT_INTERESTED = 500
        const val ROW_OPEN_CAP = 60
    }
}

/**
 * Pure scoring for Lumen's personalized discovery (no I/O, unit tested). The repository feeds it
 * a profile's watch history, ratings and candidate titles; it decides what each row shows.
 */
object DiscoveryEngine {

    /** Series and episodes rate as the series. */
    fun titleType(type: MediaType): MediaType = if (type == MediaType.EPISODE) MediaType.SERIES else type

    fun keysOf(type: MediaType, tmdbId: Int?, jellyfinId: String?): List<String> {
        val t = if (titleType(type) == MediaType.MOVIE) "m" else "s"
        return listOfNotNull(
            tmdbId?.takeIf { it > 0 }?.let { "$t:t$it" },
            jellyfinId?.takeIf { it.isNotBlank() }?.let { "j:$it" }
        )
    }

    fun keysOf(item: UnifiedMediaItem): List<String> = keysOf(item.mediaType, item.tmdbId, item.jellyfinId)

    /** One entry per title across rows and sources (TMDB id when known, else the item id). */
    fun identity(item: UnifiedMediaItem): String {
        val t = if (titleType(item.mediaType) == MediaType.MOVIE) "m" else "s"
        return item.tmdbId?.let { "$t:t$it" } ?: "$t:i${item.jellyfinId ?: item.id}"
    }

    // ---------------------------------------------------------------------------------------
    // Seeds: the titles a profile's recommendations grow from.
    // ---------------------------------------------------------------------------------------

    data class Seed(
        val item: UnifiedMediaItem,
        val weight: Double,
        /** Why it's a seed; the strongest reason wins for display. */
        val reason: SeedReason
    )

    enum class SeedReason { LOVED, LIKED, FAVORITE, WATCHING, WATCHED }

    /**
     * Builds weighted seeds from history. Recent activity counts more (each older position decays),
     * a thumbs up or love counts most, and anything rated "Not for me" is never a seed.
     * [resume] and [played] must already be titles (series, not episodes) in most-recent order.
     */
    fun buildSeeds(
        resume: List<UnifiedMediaItem>,
        played: List<UnifiedMediaItem>,
        favorites: List<UnifiedMediaItem>,
        taste: TasteData,
        limit: Int = 12
    ): List<Seed> {
        val byId = LinkedHashMap<String, Seed>()
        fun add(item: UnifiedMediaItem, weight: Double, reason: SeedReason) {
            if (item.tmdbId == null && item.jellyfinId == null) return
            val rating = taste.ratingFor(item)
            if (rating == TasteRating.DISLIKE || taste.isNotInterested(item)) return
            val boosted = when (rating) {
                TasteRating.LOVE -> weight + 2.5
                TasteRating.LIKE -> weight + 1.5
                else -> weight
            }
            val effReason = when (rating) {
                TasteRating.LOVE -> SeedReason.LOVED
                TasteRating.LIKE -> SeedReason.LIKED
                else -> reason
            }
            val key = identity(item)
            val prev = byId[key]
            byId[key] = if (prev == null) Seed(item, boosted, effReason)
            else prev.copy(weight = prev.weight + boosted * 0.5, reason = minOf(prev.reason, effReason))
        }
        resume.forEachIndexed { i, it -> add(it, 1.6 * decay(i), SeedReason.WATCHING) }
        played.forEachIndexed { i, it -> add(it, 1.3 * decay(i), SeedReason.WATCHED) }
        favorites.forEachIndexed { i, it -> add(it, 1.2 * decay(i, 0.96), SeedReason.FAVORITE) }
        // Rated titles that aren't in recent history still steer recommendations.
        taste.ratings.asReversed()
            .filter { it.rating == TasteRating.LIKE || it.rating == TasteRating.LOVE }
            .take(10)
            .forEachIndexed { i, e ->
                add(
                    UnifiedMediaItem(
                        id = e.jellyfinId ?: "tmdb_${e.tmdbId}",
                        tmdbId = e.tmdbId,
                        jellyfinId = e.jellyfinId,
                        mediaType = e.mediaType,
                        title = e.title,
                        genres = e.genres
                    ),
                    0.8 * decay(i),
                    SeedReason.LIKED
                )
            }
        return byId.values.sortedByDescending { it.weight }.take(limit)
    }

    private fun decay(index: Int, rate: Double = 0.88): Double = Math.pow(rate, index.toDouble())

    // ---------------------------------------------------------------------------------------
    // Genre affinity.
    // ---------------------------------------------------------------------------------------

    /**
     * Genre -> affinity in 0..1 (lower-cased genre names). Seeds add their weight to each of their
     * genres; titles rated "Not for me" subtract. Normalized so the favourite genre is 1.0.
     */
    fun genreAffinity(seeds: List<Seed>, taste: TasteData): Map<String, Double> {
        val raw = HashMap<String, Double>()
        seeds.forEach { seed ->
            val genres = seed.item.genres.map { normalizeGenre(it) }.distinct()
            if (genres.isEmpty()) return@forEach
            val share = seed.weight / genres.size.coerceAtLeast(1).toDouble().let { Math.sqrt(it) }
            genres.forEach { g -> raw[g] = (raw[g] ?: 0.0) + share }
        }
        taste.ratings.filter { it.rating == TasteRating.DISLIKE }.forEach { e ->
            e.genres.map { normalizeGenre(it) }.distinct().forEach { g -> raw[g] = (raw[g] ?: 0.0) - 0.6 }
        }
        val max = raw.values.maxOrNull()?.takeIf { it > 0 } ?: return emptyMap()
        return raw.mapValues { (it.value / max).coerceIn(-1.0, 1.0) }
    }

    /** "Science Fiction", "Sci-Fi & Fantasy" and "Sci-Fi" all count as the same taste. */
    fun normalizeGenre(genre: String): String {
        val g = genre.trim().lowercase()
        return when {
            g.startsWith("sci") || g.contains("science fiction") -> "sci-fi"
            g.startsWith("action") || g == "adventure" -> "action"
            g.startsWith("war") -> "war"
            g == "kids" || g == "children" -> "family"
            else -> g
        }
    }

    /** Average affinity of [genres] (missing genres count as neutral 0). */
    fun affinityOf(genres: List<String>, affinity: Map<String, Double>): Double {
        if (genres.isEmpty() || affinity.isEmpty()) return 0.0
        val scores = genres.map { affinity[normalizeGenre(it)] ?: 0.0 }
        // The best-matching genre matters most; the rest temper it.
        return scores.max() * 0.7 + scores.average() * 0.3
    }

    // ---------------------------------------------------------------------------------------
    // Multi-seed ranking: "Top Picks for <profile>".
    // ---------------------------------------------------------------------------------------

    /**
     * Merges each seed's recommendations into one ranked list. A title recommended by several of
     * a profile's titles rises; genre affinity, community rating and "can play it right now" break
     * ties. Seeds themselves, watched titles and anything disliked / not interested are dropped.
     */
    fun rankRecommendations(
        perSeed: List<Pair<Seed, List<UnifiedMediaItem>>>,
        affinity: Map<String, Double>,
        taste: TasteData,
        exclude: Set<String> = emptySet(),
        limit: Int = 25
    ): List<UnifiedMediaItem> {
        data class Acc(var item: UnifiedMediaItem, var score: Double, var hits: Int)
        val seedIds = perSeed.map { identity(it.first.item) }.toSet()
        val acc = LinkedHashMap<String, Acc>()
        perSeed.forEach { (seed, recs) ->
            recs.forEachIndexed { rank, rec ->
                val id = identity(rec)
                if (id in seedIds || id in exclude) return@forEachIndexed
                val contribution = seed.weight / (1.0 + rank * 0.12)
                val cur = acc[id]
                if (cur == null) {
                    acc[id] = Acc(rec, contribution, 1)
                } else {
                    cur.score += contribution
                    cur.hits += 1
                    // Prefer the richer copy (the one that's in the library).
                    if (cur.item.jellyfinId == null && rec.jellyfinId != null) cur.item = rec
                }
            }
        }
        return acc.values
            .filterNot { it.item.isWatched }
            .filterNot { taste.isNotInterested(it.item) || taste.ratingFor(it.item) == TasteRating.DISLIKE }
            .map { a ->
                val item = a.item
                val score = a.score +
                    (a.hits - 1) * 0.6 +
                    affinityOf(item.genres, affinity) * 1.2 +
                    ((item.voteAverage ?: 6.0) - 6.0) * 0.15 +
                    (if (item.jellyfinId != null) 0.5 else 0.0)
                item to score
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
    }

    // ---------------------------------------------------------------------------------------
    // Rows built from the library index (no network): what's already on the server.
    // ---------------------------------------------------------------------------------------

    private const val TICKS_PER_MINUTE = 600_000_000L
    private const val DAY_MS = 86_400_000L

    private fun JellyfinItemDto.isTitle(): Boolean =
        type.equals("Movie", ignoreCase = true) || type.equals("Series", ignoreCase = true)

    private fun JellyfinItemDto.untouched(): Boolean {
        val ud = userData ?: return true
        return !ud.played && ud.playbackPositionTicks <= 0L && ud.lastPlayedDate == null
    }

    private fun JellyfinItemDto.affinity(affinity: Map<String, Double>) = affinityOf(genres, affinity)

    /**
     * "Unwatched in Your Library": titles nobody on this profile has started, best match first.
     * Each candidate is scored on genre affinity and community rating.
     */
    fun unwatchedInLibrary(
        index: List<JellyfinItemDto>,
        affinity: Map<String, Double>,
        typeFilter: MediaType? = null,
        limit: Int = 25
    ): List<JellyfinItemDto> = index.asSequence()
        .filter { it.isTitle() && it.untouched() && it.matches(typeFilter) }
        .map { it to (it.affinity(affinity) * 2.0 + ((it.communityRating ?: 6.0) - 6.0) * 0.5) }
        .sortedByDescending { it.second }
        .map { it.first }
        .take(limit)
        .toList()

    /**
     * "Hidden Gems on Your Server": well rated, not new, never played, and not already a favorite.
     * Older titles that a "Recently Added" row would never surface.
     */
    fun hiddenGems(
        index: List<JellyfinItemDto>,
        affinity: Map<String, Double>,
        currentYear: Int,
        typeFilter: MediaType? = null,
        limit: Int = 20
    ): List<JellyfinItemDto> = index.asSequence()
        .filter { it.isTitle() && it.untouched() && it.matches(typeFilter) }
        .filter { it.userData?.isFavorite != true }
        .filter { (it.communityRating ?: 0.0) >= 7.2 }
        .filter { (it.productionYear ?: currentYear) <= currentYear - 4 }
        .map { it to ((it.communityRating ?: 0.0) + it.affinity(affinity) * 1.5) }
        .sortedByDescending { it.second }
        .map { it.first }
        .take(limit)
        .toList()

    /** "Added a While Ago, Never Started": on the server 30+ days and nobody pressed play. */
    fun forgottenInLibrary(
        index: List<JellyfinItemDto>,
        affinity: Map<String, Double>,
        nowMs: Long,
        typeFilter: MediaType? = null,
        minAgeDays: Int = 30,
        limit: Int = 20
    ): List<JellyfinItemDto> {
        val cutoff = nowMs - minAgeDays * DAY_MS
        return index.asSequence()
            .filter { it.isTitle() && it.untouched() && it.matches(typeFilter) }
            .mapNotNull { dto -> CrossServerMerge.parseDateMs(dto.dateCreated)?.takeIf { it <= cutoff }?.let { dto to it } }
            .map { (dto, created) ->
                // Gentle preference for things added recently-ish (1-6 months) and that fit the taste.
                val ageDays = (nowMs - created) / DAY_MS
                val freshness = if (ageDays <= 180) 0.5 else 0.0
                dto to (dto.affinity(affinity) * 2.0 + ((dto.communityRating ?: 6.0) - 6.0) * 0.3 + freshness)
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
            .toList()
    }

    /** "Short on Time?": unwatched movies that run 60-100 minutes. */
    fun shortTonight(
        index: List<JellyfinItemDto>,
        affinity: Map<String, Double>,
        maxMinutes: Int = 100,
        limit: Int = 20
    ): List<JellyfinItemDto> = index.asSequence()
        .filter { it.type.equals("Movie", ignoreCase = true) && it.untouched() }
        .filter { (it.runTimeTicks ?: 0L) in (60L * TICKS_PER_MINUTE)..(maxMinutes * TICKS_PER_MINUTE) }
        .map { it to (it.affinity(affinity) * 2.0 + ((it.communityRating ?: 6.0) - 6.0) * 0.4) }
        .sortedByDescending { it.second }
        .map { it.first }
        .take(limit)
        .toList()

    /**
     * Library genres to build rows for (Jellyfin-only Home and "More <Genre>" rows): the profile's
     * favourite genres first, then the library's biggest, each with at least [minItems] titles.
     */
    fun topLibraryGenres(
        index: List<JellyfinItemDto>,
        affinity: Map<String, Double>,
        typeFilter: MediaType? = null,
        count: Int = 4,
        minItems: Int = 6
    ): List<String> {
        val sizes = HashMap<String, Int>()
        val display = HashMap<String, String>()
        index.forEach { dto ->
            if (!dto.isTitle() || !dto.matches(typeFilter)) return@forEach
            dto.genres.forEach { g ->
                val n = normalizeGenre(g)
                sizes[n] = (sizes[n] ?: 0) + 1
                display.putIfAbsent(n, g)
            }
        }
        val maxSize = sizes.values.maxOrNull()?.toDouble() ?: return emptyList()
        return sizes.filter { it.value >= minItems }
            .map { (g, size) -> g to ((affinity[g] ?: 0.0) * 2.0 + size / maxSize) }
            .sortedByDescending { it.second }
            .take(count)
            .mapNotNull { display[it.first] }
    }

    /** Library titles of [genre], unwatched and best rated first. */
    fun libraryGenreRow(
        index: List<JellyfinItemDto>,
        genre: String,
        typeFilter: MediaType? = null,
        limit: Int = 20
    ): List<JellyfinItemDto> {
        val n = normalizeGenre(genre)
        return index.asSequence()
            .filter { it.isTitle() && it.matches(typeFilter) && it.genres.any { g -> normalizeGenre(g) == n } }
            .sortedWith(compareBy<JellyfinItemDto> { !it.untouched() }.thenByDescending { it.communityRating ?: 0.0 })
            .take(limit)
            .toList()
    }

    private fun JellyfinItemDto.matches(typeFilter: MediaType?): Boolean = when (typeFilter) {
        null -> true
        MediaType.MOVIE -> type.equals("Movie", ignoreCase = true)
        MediaType.SERIES, MediaType.EPISODE -> type.equals("Series", ignoreCase = true)
    }

    // ---------------------------------------------------------------------------------------
    // "More from <person>".
    // ---------------------------------------------------------------------------------------

    data class PersonPick(val id: String, val name: String, val isDirector: Boolean, val score: Double)

    /**
     * The director or actor who shows up most across a profile's seeds (needs at least two titles,
     * so one film's lead doesn't get a whole row). Directors count a bit more than cast.
     */
    fun favoritePerson(seedPeople: List<Pair<Double, List<com.seerflix.tv.core.network.JellyfinPersonDto>>>): PersonPick? {
        data class Tally(val name: String, var score: Double, var titles: Int, var director: Boolean)
        val tally = HashMap<String, Tally>()
        seedPeople.forEach { (weight, people) ->
            people.asSequence()
                .filter { !it.id.isNullOrBlank() && it.name.isNotBlank() }
                .filter { it.type.equals("Director", true) || it.type.equals("Actor", true) }
                .distinctBy { it.id }
                .forEachIndexed { billing, p ->
                    val isDirector = p.type.equals("Director", true)
                    // Only the top of the cast list says much about taste.
                    if (!isDirector && billing > 6) return@forEachIndexed
                    val t = tally.getOrPut(p.id!!) { Tally(p.name, 0.0, 0, isDirector) }
                    t.score += weight * (if (isDirector) 1.4 else 1.0)
                    t.titles += 1
                    t.director = t.director || isDirector
                }
        }
        return tally.entries
            .filter { it.value.titles >= 2 }
            .maxByOrNull { it.value.score }
            ?.let { PersonPick(it.key, it.value.name, it.value.director, it.value.score) }
    }

    // ---------------------------------------------------------------------------------------
    // "Popular in Your Household".
    // ---------------------------------------------------------------------------------------

    data class HouseholdTitle(val titleId: String, val viewers: Int, val lastPlayedMs: Long)

    /**
     * Ranks what the other people on the server / this TV watched lately: titles watched by more
     * people first, then most recent. Episodes count toward their series. [perUser] is each other
     * user's recently played items.
     */
    fun householdPopular(
        perUser: List<List<JellyfinItemDto>>,
        nowMs: Long,
        windowDays: Int = 60,
        limit: Int = 20
    ): List<HouseholdTitle> {
        val cutoff = nowMs - windowDays * DAY_MS
        val viewers = HashMap<String, MutableSet<Int>>()
        val last = HashMap<String, Long>()
        perUser.forEachIndexed { userIdx, items ->
            items.forEach { dto ->
                val titleId = if (dto.type.equals("Episode", true)) dto.seriesId ?: return@forEach else dto.id
                if (!dto.type.equals("Episode", true) && !dto.isTitle()) return@forEach
                val played = CrossServerMerge.parseDateMs(dto.userData?.lastPlayedDate) ?: return@forEach
                if (played < cutoff) return@forEach
                viewers.getOrPut(titleId) { mutableSetOf() } += userIdx
                last[titleId] = maxOf(last[titleId] ?: 0L, played)
            }
        }
        return viewers.map { (id, users) -> HouseholdTitle(id, users.size, last[id] ?: 0L) }
            .sortedWith(compareByDescending<HouseholdTitle> { it.viewers }.thenByDescending { it.lastPlayedMs })
            .take(limit)
    }

    // ---------------------------------------------------------------------------------------
    // Whole-page polish: hide "Not interested", dedupe across rows, order rows by engagement.
    // ---------------------------------------------------------------------------------------

    /** Rows whose items are the profile's own state (never filtered or deduped). */
    private val PERSONAL_STATE_ROWS = setOf(
        "continue_watching", "next_up", "my_list", "ready_to_watch", "watch_it_again", "collections"
    )

    /** Rows that keep their position at the top of the page. */
    private val PINNED_TOP_ROWS = listOf("ready_to_watch", "continue_watching", "next_up")

    private fun isChartRow(id: String) =
        id.startsWith("top10_") || id.startsWith("np_top10") || id.startsWith("genre_top10_") || id == "np_trending"

    /** Drops "Not interested" and "Not for me" titles from every discovery row. */
    fun filterDisliked(rows: List<CatalogRow>, taste: TasteData): List<CatalogRow> {
        if (taste.notInterested.isEmpty() && taste.ratings.none { it.rating == TasteRating.DISLIKE }) return rows
        return rows.mapNotNull { row ->
            if (row.id in PERSONAL_STATE_ROWS) return@mapNotNull row
            val kept = row.items.filterNot { taste.isNotInterested(it) || taste.ratingFor(it) == TasteRating.DISLIKE }
            when {
                kept.size == row.items.size -> row
                kept.isEmpty() -> null
                else -> row.copy(items = kept)
            }
        }
    }

    /**
     * The same poster shouldn't fill four rows. Walking the page top to bottom, a discovery row
     * loses titles an earlier discovery row already showed. Charts (Top 10) and personal rows keep
     * everything, and a row that would shrink below [minItems] keeps its original list instead.
     */
    fun dedupeAcrossRows(rows: List<CatalogRow>, minItems: Int = 6): List<CatalogRow> {
        val seen = HashSet<String>()
        return rows.map { row ->
            if (row.id in PERSONAL_STATE_ROWS || isChartRow(row.id) || row.id.startsWith("lib_")) {
                return@map row
            }
            val fresh = row.items.filterNot { identity(it) in seen }
            val result = if (fresh.size >= minItems || fresh.size == row.items.size) row.copy(items = fresh) else row
            result.items.forEach { seen += identity(it) }
            result
        }
    }

    /** Row ids that share one engagement counter ("because_you_watched_2" counts as "because_you_watched"). */
    fun engagementRowKey(rowId: String): String? {
        if (rowId.isBlank()) return null
        return when {
            rowId.startsWith("because_you_watched") -> "because_you_watched"
            rowId.startsWith("more_from_person") -> "more_from_person"
            rowId.startsWith("library_genre_") -> "library_genre"
            else -> rowId
        }
    }

    /**
     * Netflix-style row order: rows this profile opens titles from drift up the page, gently
     * (a row moves at most a few places per doubling of opens). Pinned rows stay on top in their
     * order; everything else keeps the catalog order when there's no history.
     */
    fun orderByEngagement(rows: List<CatalogRow>, rowOpens: Map<String, Int>): List<CatalogRow> {
        if (rowOpens.isEmpty() || rows.size < 3) return rows
        val pinned = PINNED_TOP_ROWS.mapNotNull { id -> rows.firstOrNull { it.id == id } }
        val rest = rows.filterNot { it.id in PINNED_TOP_ROWS }
        val total = rowOpens.values.sum().coerceAtLeast(1)
        val ranked = rest.mapIndexed { index, row ->
            val opens = engagementRowKey(row.id)?.let { rowOpens[it] } ?: 0
            val share = opens.toDouble() / total
            row to (index - 2.0 * ln(1.0 + opens) - 4.0 * share)
        }.sortedBy { it.second }.map { it.first }
        return pinned + ranked
    }
}
