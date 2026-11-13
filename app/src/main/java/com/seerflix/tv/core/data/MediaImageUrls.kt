package com.seerflix.tv.core.data

object MediaImageUrls {
    fun tmdbPoster(path: String?): String? =
        path?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w500$it" }

    fun tmdbBackdrop(path: String?): String? =
        path?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w1280$it" }

    fun tmdbProfile(path: String?): String? =
        path?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w185$it" }

    fun jellyfinPrimary(baseUrl: String, itemId: String, maxWidth: Int = 500): String? =
        baseUrl.takeIf { it.isNotBlank() }?.let {
            "$it/Items/$itemId/Images/Primary?maxWidth=$maxWidth&quality=90"
        }

    fun jellyfinBackdrop(baseUrl: String, itemId: String, maxWidth: Int = 1280): String? =
        baseUrl.takeIf { it.isNotBlank() }?.let {
            "$it/Items/$itemId/Images/Backdrop/0?maxWidth=$maxWidth&quality=90"
        }

    fun jellyfinLogo(baseUrl: String, itemId: String, maxWidth: Int = 600): String? =
        baseUrl.takeIf { it.isNotBlank() }?.let {
            "$it/Items/$itemId/Images/Logo?maxWidth=$maxWidth&quality=90"
        }

    fun jellyfinUserAvatar(baseUrl: String, userId: String): String? =
        baseUrl.takeIf { it.isNotBlank() }?.let {
            "$it/Users/$userId/Images/Primary?maxWidth=300&quality=90"
        }
}
