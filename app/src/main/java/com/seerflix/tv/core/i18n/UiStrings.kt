package com.seerflix.tv.core.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class UiStrings(
    val languageCode: String = "en",
    val navHome: String = "Home",
    val navShows: String = "TV Shows",
    val navMovies: String = "Movies",
    val navCollections: String = "Collections",
    val navLiveTv: String = "Live TV",
    val navNewAndPopular: String = "New & Popular",
    val navMyList: String = "My List",
    val navSearch: String = "Search",
    val navRequests: String = "Requests",
    val actionPlay: String = "Play",
    val actionResume: String = "Resume",
    val actionRequestHd: String = "Request HD",
    val actionRequest4k: String = "Request 4K",
    val actionRequestMore: String = "Request",
    val actionTrailer: String = "Trailer",
    val actionMoreInfo: String = "More Info",
    val actionMarkWatched: String = "Mark Watched",
    val actionMarkUnwatched: String = "Mark Unwatched",
    val actionExternalPlayer: String = "External Player",
    val actionReportIssue: String = "Report Issue",
    val actionCustomizeHome: String = "Customize Home",
    val actionWatchTogether: String = "Watch Together",
    val actionApprove: String = "Approve",
    val actionDecline: String = "Decline",
    val actionCancel: String = "Cancel",
    val labelExtras: String = "Extras & Specials",
    val labelVersions: String = "Versions & Editions",
    val labelChapters: String = "Chapters",
    val labelKidsLocked: String = "Requests Locked (Kids Mode)"
)

val SupportedLanguages = listOf(
    "en" to "English",
    "es" to "Español",
    "fr" to "Français",
    "de" to "Deutsch",
    "ja" to "日本語"
)

fun stringsForLanguage(code: String): UiStrings = when (code.lowercase()) {
    "es" -> UiStrings(
        languageCode = "es",
        navHome = "Inicio",
        navShows = "Series",
        navMovies = "Películas",
        navCollections = "Colecciones",
        navLiveTv = "TV en Vivo",
        navNewAndPopular = "Novedades",
        navMyList = "Mi Lista",
        navSearch = "Buscar",
        navRequests = "Solicitudes",
        actionPlay = "Reproducir",
        actionResume = "Reanudar",
        actionRequestHd = "Solicitar HD",
        actionRequest4k = "Solicitar 4K",
        actionRequestMore = "Solicitar",
        actionTrailer = "Tráiler",
        actionMoreInfo = "Más Info",
        actionMarkWatched = "Marcar como Visto",
        actionMarkUnwatched = "Marcar No Visto",
        actionExternalPlayer = "Reproductor Externo",
        actionReportIssue = "Reportar Problema",
        actionCustomizeHome = "Personalizar Inicio",
        actionWatchTogether = "Ver Juntos",
        actionApprove = "Aprobar",
        actionDecline = "Rechazar",
        actionCancel = "Cancelar",
        labelExtras = "Extras y Especiales",
        labelVersions = "Versiones y Ediciones",
        labelChapters = "Capítulos",
        labelKidsLocked = "Solicitudes Bloqueadas (Modo Infantil)"
    )
    "fr" -> UiStrings(
        languageCode = "fr",
        navHome = "Accueil",
        navShows = "Séries",
        navMovies = "Films",
        navCollections = "Collections",
        navLiveTv = "TV en Direct",
        navNewAndPopular = "Nouveautés",
        navMyList = "Ma Liste",
        navSearch = "Rechercher",
        navRequests = "Demandes",
        actionPlay = "Lire",
        actionResume = "Reprendre",
        actionRequestHd = "Demander HD",
        actionRequest4k = "Demander 4K",
        actionRequestMore = "Demander",
        actionTrailer = "Bande-annonce",
        actionMoreInfo = "Plus d'infos",
        actionMarkWatched = "Marquer comme Vu",
        actionMarkUnwatched = "Marquer Non Vu",
        actionExternalPlayer = "Lecteur Externe",
        actionReportIssue = "Signaler un Problème",
        actionCustomizeHome = "Personnaliser l'Accueil",
        actionWatchTogether = "Regarder Ensemble",
        actionApprove = "Approuver",
        actionDecline = "Refuser",
        actionCancel = "Annuler",
        labelExtras = "Bonus et Suppléments",
        labelVersions = "Versions et Éditions",
        labelChapters = "Chapitres",
        labelKidsLocked = "Demandes Verrouillées (Mode Enfant)"
    )
    "de" -> UiStrings(
        languageCode = "de",
        navHome = "Startseite",
        navShows = "Serien",
        navMovies = "Filme",
        navCollections = "Sammlungen",
        navLiveTv = "Live-TV",
        navNewAndPopular = "Neu & Beliebt",
        navMyList = "Meine Liste",
        navSearch = "Suche",
        navRequests = "Anfragen",
        actionPlay = "Abspielen",
        actionResume = "Fortsetzen",
        actionRequestHd = "HD Anfragen",
        actionRequest4k = "4K Anfragen",
        actionRequestMore = "Anfragen",
        actionTrailer = "Trailer",
        actionMoreInfo = "Mehr Infos",
        actionMarkWatched = "Als Gesehen Markieren",
        actionMarkUnwatched = "Als Ungesehen Markieren",
        actionExternalPlayer = "Externer Player",
        actionReportIssue = "Problem Melden",
        actionCustomizeHome = "Startseite Anpassen",
        actionWatchTogether = "Gemeinsam Schauen",
        actionApprove = "Genehmigen",
        actionDecline = "Ablehnen",
        actionCancel = "Abbrechen",
        labelExtras = "Extras & Specials",
        labelVersions = "Versionen & Editionen",
        labelChapters = "Kapitel",
        labelKidsLocked = "Anfragen Gesperrt (Kindermodus)"
    )
    "ja" -> UiStrings(
        languageCode = "ja",
        navHome = "ホーム",
        navShows = "TV番組",
        navMovies = "映画",
        navCollections = "コレクション",
        navLiveTv = "ライブTV",
        navNewAndPopular = "新着・人気",
        navMyList = "マイリスト",
        navSearch = "検索",
        navRequests = "リクエスト",
        actionPlay = "再生",
        actionResume = "続きから再生",
        actionRequestHd = "HD リクエスト",
        actionRequest4k = "4K リクエスト",
        actionRequestMore = "リクエスト",
        actionTrailer = "予告編",
        actionMoreInfo = "詳細情報",
        actionMarkWatched = "視聴済みにする",
        actionMarkUnwatched = "未視聴にする",
        actionExternalPlayer = "外部プレイヤー",
        actionReportIssue = "問題を報告",
        actionCustomizeHome = "ホームをカスタマイズ",
        actionWatchTogether = "一緒に視聴",
        actionApprove = "承認",
        actionDecline = "却下",
        actionCancel = "キャンセル",
        labelExtras = "特典映像",
        labelVersions = "バージョン・エディション",
        labelChapters = "チャプター",
        labelKidsLocked = "リクエスト制限中 (キッズモード)"
    )
    else -> UiStrings()
}

val LocalStrings = staticCompositionLocalOf { UiStrings() }

object AppStrings {
    val current: UiStrings
        @Composable
        @ReadOnlyComposable
        get() = LocalStrings.current
}
