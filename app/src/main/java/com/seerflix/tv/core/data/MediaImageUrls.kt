package com.seerflix.tv.core.data

object MediaImageUrls {
    fun tmdbPoster(path: String?): String? =
        path?.takeIf { it.isNotBlank() }?.let {
            val clean = if (it.startsWith("/")) it else "/$it"
            "https://image.tmdb.org/t/p/w342$clean"
        }

    fun tmdbBackdrop(path: String?): String? =
        path?.takeIf { it.isNotBlank() }?.let {
            val clean = if (it.startsWith("/")) it else "/$it"
            "https://image.tmdb.org/t/p/w780$clean"
        }

    fun tmdbProfile(path: String?): String? =
        path?.takeIf { it.isNotBlank() }?.let {
            val clean = if (it.startsWith("/")) it else "/$it"
            "https://image.tmdb.org/t/p/w185$clean"
        }

    fun jellyfinPrimary(baseUrl: String, itemId: String, maxWidth: Int = 340): String? =
        baseUrl.trim().trimEnd('/').takeIf { it.isNotBlank() && itemId.isNotBlank() }?.let {
            "$it/Items/$itemId/Images/Primary?maxWidth=$maxWidth&quality=82"
        }

    fun jellyfinBackdrop(baseUrl: String, itemId: String, maxWidth: Int = 960): String? =
        baseUrl.trim().trimEnd('/').takeIf { it.isNotBlank() && itemId.isNotBlank() }?.let {
            "$it/Items/$itemId/Images/Backdrop/0?maxWidth=$maxWidth&quality=80"
        }

    fun jellyfinLogo(baseUrl: String, itemId: String, maxWidth: Int = 400): String? =
        baseUrl.trim().trimEnd('/').takeIf { it.isNotBlank() && itemId.isNotBlank() }?.let {
            "$it/Items/$itemId/Images/Logo?maxWidth=$maxWidth&quality=85"
        }

    fun jellyfinUserAvatar(baseUrl: String, userId: String): String? =
        baseUrl.trim().trimEnd('/').takeIf { it.isNotBlank() && userId.isNotBlank() }?.let {
            "$it/Users/$userId/Images/Primary?maxWidth=200&quality=82"
        }
}

