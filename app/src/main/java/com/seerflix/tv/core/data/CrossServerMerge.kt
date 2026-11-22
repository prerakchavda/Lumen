package com.seerflix.tv.core.data

import com.seerflix.tv.core.network.CrossServerLinks
import com.seerflix.tv.core.network.JellyfinItemDto
import com.seerflix.tv.core.network.JellyfinUserDataDto
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Pure rules for showing several Jellyfin servers as one library.
 *
 * Everything here takes its inputs as "one list per server, home server first" and works on ids
 * that are already server scoped, so two servers can never be confused with each other.
 *
 *  - A movie or series that is on several servers is shown once. Titles are matched by provider id:
 *    TMDB, then IMDb, then TVDB. The copy from the earliest server in the list (the home server
 *    when it has one) is the one that is shown and played first; the others are remembered as
 *    alternates ([CrossServerLinks]).
 *  - Episodes are matched by season and episode number once their series are matched, so a series
 *    that is on two servers gets the union of both servers' episodes.
 *  - Resume position and played state come from whichever copy was played most recently.
 */
internal object CrossServerMerge {

    /** One title after merging: [item] is what is shown, [copies] every copy (shown copy first). */
    data class Merged(val item: JellyfinItemDto, val copies: List<JellyfinItemDto>)

    data class MergedLibrary(val items: List<JellyfinItemDto>, val links: CrossServerLinks)

    enum class Order {
        /** Server order: the home server's items first, then what only the others have. */
        SERVER,
        /** Most recently played first (Continue Watching). */
        LAST_PLAYED,
        /** Most recently added first (Latest). */
        DATE_CREATED
    }

    private fun isMovie(dto: JellyfinItemDto) = dto.type.equals("Movie", ignoreCase = true)
    private fun isSeries(dto: JellyfinItemDto) = dto.type.equals("Series", ignoreCase = true)
    private fun isEpisode(dto: JellyfinItemDto) = dto.type.equals("Episode", ignoreCase = true)

    private fun providerId(dto: JellyfinItemDto, name: String): String? =
        (dto.providerIds[name] ?: dto.providerIds.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value)
            ?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "0" }

    /**
     * Match keys of a movie / series in priority order (TMDB, IMDb, TVDB). Movies and series never
     * match each other. Anything else (episodes, box sets, ...) has no keys.
     */
    fun providerKeys(dto: JellyfinItemDto): List<String> {
        val kind = when {
            isMovie(dto) -> "m"
            isSeries(dto) -> "s"
            else -> return emptyList()
        }
        return buildList(3) {
            providerId(dto, "Tmdb")?.let { add("$kind:tmdb:$it") }
            providerId(dto, "Imdb")?.let { add("$kind:imdb:$it") }
            providerId(dto, "Tvdb")?.let { add("$kind:tvdb:$it") }
        }
    }

    fun parseDateMs(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return runCatching { Instant.parse(iso).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }
            .getOrNull()
    }

    private fun hasProgress(data: JellyfinUserDataDto?): Boolean =
        data != null && (data.played || data.playbackPositionTicks > 0L || !data.lastPlayedDate.isNullOrBlank())

    /**
     * The watch state for a title given each copy's (first = shown copy): the copy that was played
     * most recently wins; with no play date anywhere the shown copy's own state is kept. A favourite
     * on any server makes the title a favourite.
     */
    fun mergeUserData(copies: List<JellyfinUserDataDto?>): JellyfinUserDataDto? {
        val primary = copies.firstOrNull()
        if (copies.size <= 1) return primary
        var best: JellyfinUserDataDto? = primary
        var bestMs = if (hasProgress(primary)) parseDateMs(primary?.lastPlayedDate) ?: Long.MIN_VALUE else Long.MIN_VALUE
        var bestHasProgress = hasProgress(primary)
        for (i in 1 until copies.size) {
            val candidate = copies[i]
            if (!hasProgress(candidate)) continue
            val ms = parseDateMs(candidate?.lastPlayedDate) ?: Long.MIN_VALUE
            if (!bestHasProgress || ms > bestMs) {
                best = candidate
                bestMs = ms
                bestHasProgress = true
            }
        }
        val favorite = copies.any { it?.isFavorite == true }
        return when {
            best == null -> if (favorite) JellyfinUserDataDto(isFavorite = true) else null
            best.isFavorite == favorite -> best
            else -> best.copy(isFavorite = favorite)
        }
    }

    private fun lastPlayedMs(dto: JellyfinItemDto): Long = parseDateMs(dto.userData?.lastPlayedDate) ?: Long.MIN_VALUE

    private fun withMergedUserData(copies: List<JellyfinItemDto>): JellyfinItemDto {
        val primary = copies.first()
        if (copies.size == 1) return primary
        val merged = mergeUserData(copies.map { it.userData })
        return if (merged === primary.userData) primary else primary.copy(userData = merged)
    }

    /**
     * Merges whole libraries (movies and series): the result lists every title once and records
     * which items are copies of each other.
     */
    fun mergeLibrary(perServer: List<List<JellyfinItemDto>>): MergedLibrary {
        val groups = groupTitles(perServer, CrossServerLinks.EMPTY)
        val primaryOf = HashMap<String, String>()
        val copies = HashMap<String, List<String>>()
        val items = ArrayList<JellyfinItemDto>(groups.size)
        for (group in groups) {
            items += withMergedUserData(group)
            if (group.size > 1) {
                val primaryId = group.first().id
                copies[primaryId] = group.map { it.id }
                for (i in 1 until group.size) primaryOf[group[i].id] = primaryId
            }
        }
        return MergedLibrary(items, CrossServerLinks(primaryOf, copies))
    }

    /**
     * Merges any item lists (rows, search results, Continue Watching...). Movies and series are
     * matched through [links] and their provider ids; episodes through their (linked) series plus
     * season / episode number. Anything else is kept as is.
     */
    fun mergeLists(
        perServer: List<List<JellyfinItemDto>>,
        links: CrossServerLinks = CrossServerLinks.EMPTY,
        order: Order = Order.SERVER
    ): List<Merged> {
        val nonEmpty = perServer.count { it.isNotEmpty() }
        val merged = if (nonEmpty <= 1) {
            // One server answered: nothing to match, keep its list exactly as it came.
            perServer.firstOrNull { it.isNotEmpty() }.orEmpty().map { Merged(it, listOf(it)) }
        } else {
            groupTitles(perServer, links).map { Merged(withMergedUserData(it), it) }
        }
        return when (order) {
            Order.SERVER -> merged
            // sortedBy is stable: items without a date keep their place at the end.
            Order.LAST_PLAYED -> if (nonEmpty <= 1) merged else merged.sortedByDescending { lastPlayedMs(it.item) }
            Order.DATE_CREATED -> if (nonEmpty <= 1) merged else merged.sortedByDescending { m ->
                m.copies.maxOf { parseDateMs(it.dateCreated) ?: Long.MIN_VALUE }
            }
        }
    }

    fun mergeItems(
        perServer: List<List<JellyfinItemDto>>,
        links: CrossServerLinks = CrossServerLinks.EMPTY,
        order: Order = Order.SERVER
    ): List<JellyfinItemDto> = mergeLists(perServer, links, order).map { it.item }

    /** Groups copies of the same title; each group is in server order, groups in first-seen order. */
    private fun groupTitles(
        perServer: List<List<JellyfinItemDto>>,
        links: CrossServerLinks
    ): List<List<JellyfinItemDto>> {
        val groups = ArrayList<MutableList<JellyfinItemDto>>()
        val groupServers = ArrayList<MutableSet<Int>>()
        val byKey = HashMap<String, Int>()

        perServer.forEachIndexed { serverIdx, items ->
            for (dto in items) {
                val keys = matchKeys(dto, links)
                // A title matches an earlier one from a *different* server only: two entries on the
                // same server (say a 4K and an HD library) stay separate, as they always have.
                val hit = keys.firstNotNullOfOrNull { key ->
                    byKey[key]?.takeIf { serverIdx !in groupServers[it] }
                }
                if (hit != null) {
                    groups[hit] += dto
                    groupServers[hit] += serverIdx
                    keys.forEach { byKey.putIfAbsent(it, hit) }
                } else {
                    val idx = groups.size
                    groups += mutableListOf(dto)
                    groupServers += mutableSetOf(serverIdx)
                    keys.forEach { byKey.putIfAbsent(it, idx) }
                }
            }
        }
        return groups
    }

    private fun matchKeys(dto: JellyfinItemDto, links: CrossServerLinks): List<String> = when {
        isEpisode(dto) -> {
            val series = dto.seriesId
            val season = dto.parentIndexNumber
            val episode = dto.indexNumber
            if (series.isNullOrBlank() || season == null || episode == null) emptyList()
            else listOf("e:${links.canonical(series)}:$season:$episode")
        }
        isMovie(dto) || isSeries(dto) -> buildList {
            // Known link from the library index first, then the provider ids themselves (covers
            // titles the index hasn't caught up with yet).
            add("id:${links.canonical(dto.id)}")
            addAll(providerKeys(dto))
        }
        else -> emptyList()
    }

    /**
     * Episodes of one series from each server that has it, merged by season / episode number and
     * sorted. An episode missing on the first server is filled in from the next one that has it.
     */
    fun mergeEpisodes(perServer: List<List<JellyfinItemDto>>): List<Merged> {
        val groups = LinkedHashMap<String, MutableList<JellyfinItemDto>>()
        perServer.forEachIndexed { serverIdx, episodes ->
            val seenHere = HashSet<String>()
            for (ep in episodes) {
                val season = ep.parentIndexNumber
                val number = ep.indexNumber
                // Unnumbered episodes can't be matched; a second file for the same number on the
                // same server (a multi-version episode) stays its own entry.
                val key = if (season != null && number != null && seenHere.add("$season:$number")) {
                    "$season:$number"
                } else {
                    "u:$serverIdx:${ep.id}"
                }
                groups.getOrPut(key) { mutableListOf() } += ep
            }
        }
        return groups.values
            .map { Merged(withMergedUserData(it), it) }
            .sortedWith(compareBy({ it.item.parentIndexNumber ?: Int.MAX_VALUE }, { it.item.indexNumber ?: Int.MAX_VALUE }))
    }

    /** Seasons of one series from each server, merged by season number (first server's wins). */
    fun mergeSeasons(perServer: List<List<JellyfinItemDto>>): List<Merged> {
        val groups = LinkedHashMap<String, MutableList<JellyfinItemDto>>()
        perServer.forEachIndexed { serverIdx, seasons ->
            val seenHere = HashSet<Int>()
            for (season in seasons) {
                val number = season.indexNumber
                val key = if (number != null && seenHere.add(number)) "n:$number" else "u:$serverIdx:${season.id}"
                groups.getOrPut(key) { mutableListOf() } += season
            }
        }
        return groups.values.map { copies ->
            val primary = copies.first()
            // The episode count on the card is the most any one server has for that season.
            val count = copies.mapNotNull { it.childCount }.maxOrNull()
            Merged(if (count != null && count != primary.childCount) primary.copy(childCount = count) else primary, copies)
        }.sortedBy { it.item.indexNumber ?: Int.MAX_VALUE }
    }

    /**
     * Order of two items for a library grid sorted server-side by [sortBy] (Jellyfin's `SortBy`),
     * used to interleave pages from several servers. Only the leading sort field matters here; the
     * servers already sorted their own pages.
     */
    fun browseComparator(sortBy: String, descending: Boolean): Comparator<JellyfinItemDto>? {
        val base: Comparator<JellyfinItemDto> = when (sortBy.substringBefore(',').trim().lowercase()) {
            "sortname" -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.sortName ?: it.name }
            "datecreated" -> compareBy { parseDateMs(it.dateCreated) ?: Long.MIN_VALUE }
            "premieredate" -> compareBy {
                parseDateMs(it.premiereDate) ?: it.productionYear?.let { year -> (year - 1970L) * YEAR_MS } ?: Long.MIN_VALUE
            }
            "communityrating" -> compareBy { it.communityRating ?: Double.NEGATIVE_INFINITY }
            // Random (or anything unknown): no meaningful order, the caller alternates servers.
            else -> return null
        }
        return if (descending) base.reversed() else base
    }

    private const val YEAR_MS = 31_556_952_000L
}
