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
    val labelKidsLocked: String = "Requests Locked (Kids Mode)",

    // ---- Settings (0.7) ----
    val navSettings: String = "Settings",
    val settingsTitle: String = "Settings",
    val settingsPlaybackSection: String = "Playback",
    val settingsMatchCapsTitle: String = "Match this TV's capabilities",
    val settingsMatchCapsDesc: String = "The server converts anything this box, TV or receiver can't play: HDR on an SDR screen, DTS without a receiver, missing codecs. Turn off only if playback looks wrong after detection.",
    val settingsDetected: String = "Detected",
    val settingsPassthroughTitle: String = "Audio passthrough",
    val settingsPassthroughDesc: String = "Send Dolby and DTS audio untouched to a receiver or soundbar that supports it.",
    val settingsDownmixTitle: String = "Stereo downmix",
    val settingsDownmixDesc: String = "Mix surround sound down to 2.0 for TV speakers or late-night viewing.",
    val settingsFrameRateTitle: String = "Match frame rate",
    val settingsAutoSkipIntroTitle: String = "Skip intros automatically",
    val settingsAutoSkipRecapTitle: String = "Skip recaps automatically",
    val settingsAutoplayNextTitle: String = "Play next episode automatically",
    val settingsSubtitlesSection: String = "Subtitles",
    val settingsSubtitleSize: String = "Text size",
    val settingsSubtitleColor: String = "Text color",
    val settingsSubtitleBackground: String = "Background",
    val settingsSubtitleEdge: String = "Edge style",
    val settingsSubtitlePosition: String = "Position",
    val settingsSubtitlePreview: String = "This is how subtitles will look.",
    val settingsRemoteSection: String = "Remote control",
    val settingsRemoteTitle: String = "Allow control from other Jellyfin apps",
    val settingsRemoteDesc: String = "Shows this TV under \"Play on\" in Jellyfin on the web, phones and computers, and lets them pause, seek or send messages.",
    val settingsConnected: String = "Connected to server",
    val settingsConnecting: String = "Connecting...",
    val settingsDisconnected: String = "Not connected",
    val settingsDiagnosticsSection: String = "Help & diagnostics",
    val settingsSendLogsTitle: String = "Send logs to my Jellyfin server",
    val settingsSendLogsDesc: String = "Saves a report (device, playback support, recent app log, last crash) on your own server under Dashboard > Logs. Sign-in tokens are removed and nothing is sent anywhere else.",
    val settingsSendingLogs: String = "Sending...",
    val settingsLogsSent: String = "Saved on your server as %s",
    val settingsLogsDisabled: String = "Your server doesn't accept app logs. An admin can turn on \"Allow client log upload\" in Dashboard > General.",
    val settingsLogsFailed: String = "Couldn't send logs: %s",
    val settingsCrashPending: String = "Lumen closed unexpectedly last time; that report will be included.",
    val settingsServerSection: String = "Server & account",
    val settingsServerTitle: String = "Server connection",
    val settingsServerDesc: String = "Jellyfin and Seerr addresses, sign-in and Jellyfin-only mode.",
    val settingsAboutSection: String = "About",
    val settingsVersion: String = "Version %s",
    val optionOn: String = "On",
    val optionOff: String = "Off",
    val frameRateSeamless: String = "Seamless only",
    val frameRateAlways: String = "Always",
    val sizeSmall: String = "Small",
    val sizeMedium: String = "Medium",
    val sizeLarge: String = "Large",
    val sizeExtraLarge: String = "Extra large",
    val backgroundNone: String = "None",
    val backgroundLight: String = "Light",
    val backgroundMedium: String = "Medium",
    val backgroundSolid: String = "Solid",
    val colorWhite: String = "White",
    val colorYellow: String = "Yellow",
    val colorCyan: String = "Cyan",
    val colorGreen: String = "Green",
    val colorGrey: String = "Light gray",
    val edgeOutline: String = "Outline",
    val edgeDropShadow: String = "Drop shadow",
    val edgeRaised: String = "Raised",
    val edgeNone: String = "None",
    val positionBottom: String = "Bottom",
    val positionRaised: String = "Raised",
    val positionHigh: String = "High",
    val crashPromptTitle: String = "Lumen closed unexpectedly",
    val crashPromptBody: String = "Send a report to your Jellyfin server so the problem can be looked into? It stays on your server.",
    val crashPromptSend: String = "Send report",
    val crashPromptDismiss: String = "Not now",

    // ---- Library browsing (0.7) ----
    val libraryBrowseAll: String = "Browse all",
    val libraryAllMovies: String = "All Movies",
    val libraryAllShows: String = "All TV Shows",
    val librarySort: String = "Sort",
    val sortName: String = "A-Z",
    val sortDateAdded: String = "Date added",
    val sortReleased: String = "Release date",
    val sortRating: String = "Rating",
    val sortRandom: String = "Random",
    val libraryFilterAll: String = "All",
    val libraryFilterUnwatched: String = "Unwatched",
    val libraryFilterWatched: String = "Watched",
    val libraryFilterFavorites: String = "Favorites",
    val libraryAllGenres: String = "All genres",
    val libraryGenre: String = "Genre",
    val libraryTitleCount: String = "%d titles",
    val libraryEmpty: String = "Nothing matches these filters.",
    val libraryJumpHint: String = "Jump to letter",

    // ---- DVR (0.7) ----
    val dvrRecord: String = "Record",
    val dvrRecordSeries: String = "Record series",
    val dvrCancelRecording: String = "Cancel recording",
    val dvrCancelSeries: String = "Cancel series recording",
    val dvrScheduled: String = "Scheduled recordings",
    val dvrNoneScheduled: String = "No recordings scheduled",
    val dvrRecordingSet: String = "Recording scheduled",
    val dvrSeriesSet: String = "Series recording scheduled",
    val dvrCancelled: String = "Recording cancelled",
    val dvrFailed: String = "Couldn't change the recording: %s",
    val dvrRecordingBadge: String = "REC",
    val dvrWatchLive: String = "Watch live",
    val dvrProgramOptions: String = "Program options"
) {
    // The fields below are body properties, not constructor parameters: the JVM caps a
    // constructor at 255 parameters. stringsForLanguage() sets their translations in apply {}.
    // They are not part of equals()/copy(); languageCode already identifies an instance.

    // ---- Common (0.7) ----
    var commonRetry: String = "Retry"
        internal set
    var commonGoBack: String = "Go Back"
        internal set
    var commonBack: String = "Back"
        internal set
    var commonClose: String = "Close"
        internal set
    var commonRefresh: String = "Refresh"
        internal set
    var commonSave: String = "Save"
        internal set
    var commonSending: String = "Sending..."
        internal set
    var commonActive: String = "Active"
        internal set
    var commonDefault: String = "Default"
        internal set
    var commonMovie: String = "Movie"
        internal set
    var commonSeries: String = "Series"
        internal set
    var commonTvSeries: String = "TV Series"
        internal set
    var commonInLibrary: String = "In Library"
        internal set
    var commonDownloading: String = "Downloading"
        internal set
    var commonRequested: String = "Requested"
        internal set
    var commonMatchPercent: String = "%d%% Match"
        internal set
    var commonMinutesShort: String = "%dm"
        internal set

    // ---- Details screen (0.7) ----
    var detailsTitleNotFound: String = "Title not found"
        internal set
    var detailsSeasonsEpisodes: String = "Seasons & Episodes"
        internal set
    var detailsPartOf: String = "Part of %s"
        internal set
    var detailsRequestMissing: String = "Request Missing (%d)"
        internal set
    var detailsMoreLikeThis: String = "More Like This"
        internal set
    var detailsTrailersMore: String = "Trailers & More"
        internal set
    var detailsCast: String = "Cast"
        internal set
    var detailsPlayTrailer: String = "Play Trailer"
        internal set
    var detailsPlayExtra: String = "Play Extra"
        internal set
    var detailsRequest4kMore: String = "Request 4K / More"
        internal set
    var detailsRequestHd4k: String = "Request HD / 4K"
        internal set
    var detailsInMyList: String = "In My List"
        internal set
    var detailsReminderSet: String = "Reminder Set"
        internal set
    var detailsRemindMe: String = "Remind Me"
        internal set
    var detailsRemoveProgress: String = "Remove Progress"
        internal set
    var detailsCancelRequest: String = "Cancel Request"
        internal set
    var detailsTierAvailable: String = "%s Available"
        internal set
    var detailsTierPartial: String = "%s Partial"
        internal set
    var detailsTierDownloading: String = "%s Downloading"
        internal set
    var detailsTierRequested: String = "%s Requested"
        internal set
    var detailsTierNotInLibrary: String = "%s Not in Library"
        internal set
    var detailsNotOnServer: String = "Not on Server"
        internal set
    var detailsRequestTitle: String = "Request \"%s\""
        internal set
    var detailsRequestIntro: String = "Choose quality tier, Radarr/Sonarr server, profile & root folder:"
        internal set
    var detailsQualityHd: String = "HD (1080p)"
        internal set
    var detailsQuality4k: String = "4K Ultra HD (2160p)"
        internal set
    var detailsAlreadyInLibrary: String = "Already in library"
        internal set
    var detailsAlreadyRequested: String = "Already requested"
        internal set
    var detailsStandardProfile: String = "Standard Radarr / Sonarr profile"
        internal set
    var details4kProfile: String = "4K Radarr / Sonarr profile"
        internal set
    var detailsLoadingProfiles: String = "Loading Radarr / Sonarr profiles..."
        internal set
    var detailsSelectSeasons: String = "Select Seasons (%d selected):"
        internal set
    var detailsSendRequest: String = "Send Request"
        internal set
    var detailsServer: String = "Server"
        internal set
    var detailsQualityProfile: String = "Quality Profile"
        internal set
    var detailsRootFolder: String = "Root Folder"
        internal set
    var detailsIssueHint: String = "Describe the playback or subtitle issue (optional)"
        internal set
    var detailsReporting: String = "Reporting..."
        internal set
    var detailsSubmitIssue: String = "Submit Issue"
        internal set
    var issueVideo: String = "Video Problem"
        internal set
    var issueAudio: String = "Audio Sync / Missing"
        internal set
    var issueSubtitle: String = "Subtitles"
        internal set
    var issueOther: String = "Other"
        internal set

    // ---- Home screen (0.7) ----
    var homeProfileFallback: String = "Profile"
        internal set
    var homeLoadingHub: String = "Loading your %s-style hub…"
        internal set
    var homeMyListEmpty: String = "Your My List is empty"
        internal set
    var homeMyListEmptyHint: String = "Add movies and shows from the Hero banner or Details screen to find them here."
        internal set
    var homeNoTitlesMatch: String = "No titles match \"%s\""
        internal set
    var homeNoTitlesMatchHint: String = "Switch back to All or pick another genre to keep browsing."
        internal set
    var homeNothingYet: String = "Nothing to show here yet"
        internal set
    var homeNothingYetHint: String = "Your libraries returned no titles. Use Refresh in the menu to try again."
        internal set
    var homeAppearance: String = "Appearance"
        internal set
    var homeUpdate: String = "Update"
        internal set
    var homeAppUpdate: String = "App Update"
        internal set
    var homeServers: String = "Servers"
        internal set
    var homeFilterWatchNow: String = "Watch Now"
        internal set
    var homeFilterRequestable: String = "Requestable"
        internal set
    var homeJellyfinOnlyMode: String = "Jellyfin-Only Mode"
        internal set
    var homeKidsModeActive: String = "Kids Mode Active"
        internal set
    var homeSyncPlayChip: String = "SyncPlay: %s"
        internal set
    var homeRequestedDownloading: String = "Requested / Downloading"
        internal set
    var homeAvailableToRequest: String = "Available to Request"
        internal set
    var homeRemoveFromMyList: String = "Remove from My List"
        internal set
    var homeAddToMyList: String = "Add to My List"
        internal set
    var homeDetailsCastTrailers: String = "Details, Cast & Trailers"
        internal set
    var homeRemoveFromContinue: String = "Remove from Continue Watching"
        internal set
    var homeNoCollections: String = "No Jellyfin Collections found"
        internal set
    var homeNoCollectionsHint: String = "Create BoxSets in Jellyfin to browse sagas and complete missing sequels here."
        internal set
    var homeCollectionsSubtitle: String = "Browse your Jellyfin BoxSets and discover missing entries to complete every saga."
        internal set
    var homeCollectionEmpty: String = "No titles found in this collection."
        internal set
    var homeMissingFromLibrary: String = "Missing from Library"
        internal set
    var homeDetails: String = "Details"
        internal set
    var homeJustArrived: String = "Just Arrived • %s"
        internal set
    var homeJustArrivedBody: String = "Your Seerr request finished downloading and is ready to watch."
        internal set
    var homeDismiss: String = "Dismiss"
        internal set
    var genreAll: String = "All Genres"
        internal set
    var genreAction: String = "Action"
        internal set
    var genreComedy: String = "Comedy"
        internal set
    var genreSciFi: String = "Sci-Fi"
        internal set
    var genreHorrorMystery: String = "Horror & Mystery"
        internal set
    var genreDrama: String = "Drama"
        internal set
    var genreCrime: String = "Crime"
        internal set
    var genreThriller: String = "Thriller"
        internal set
    var genreDocumentary: String = "Documentary"
        internal set
    var genreAnimation: String = "Animation"
        internal set

    // ---- Live TV guide (0.7) ----
    var liveNoChannels: String = "No Live TV Channels Found"
        internal set
    var liveNoChannelsHint: String = "Configure an IPTV / HDHomeRun tuner & XMLTV guide in Jellyfin Dashboard → Live TV."
        internal set
    var liveGuideTitle: String = "Live TV & DVR Guide"
        internal set
    var liveChannelCount: String = "%d channel(s) available • Select any channel to tune in live."
        internal set
    var liveStream: String = "Live Stream"
        internal set
    var liveOnNow: String = "ON NOW • %s"
        internal set
    var liveUpNext: String = "UP NEXT"
        internal set
    var liveTuneIn: String = "Tune In"
        internal set
    var liveDvrRecordings: String = "DVR Recordings"
        internal set

    // ---- Customize Home dialog (0.7) ----
    var customizeTitle: String = "Customize Home Layout • %s"
        internal set
    var customizePinLibraries: String = "Pin Jellyfin Libraries as Home Rows"
        internal set
    var customizeStartupTab: String = "Default Startup Tab"
        internal set
    var customizeHideReorder: String = "Hide / Reorder Home Rows"
        internal set
    var customizeShow: String = "Show"
        internal set
    var customizeHide: String = "Hide"
        internal set
    var customizeUp: String = "Up"
        internal set
    var customizeDown: String = "Down"
        internal set
    var customizeSave: String = "Save Layout"
        internal set

    // ---- SyncPlay dialog (0.7) ----
    var syncPlayTitle: String = "SyncPlay • Watch Together"
        internal set
    var syncPlayDefaultRoom: String = "Movie Night"
        internal set
    var syncPlayActiveRoom: String = "Active Room: %s"
        internal set
    var syncPlayViewers: String = "%1\$d viewer(s) • %2\$s"
        internal set
    var syncPlayLeave: String = "Leave Room"
        internal set
    var syncPlayNewName: String = "New Watch Party Name"
        internal set
    var syncPlayCreate: String = "Create Watch Party"
        internal set
    var syncPlayRooms: String = "Active Jellyfin SyncPlay Rooms"
        internal set
    var syncPlayNoRooms: String = "No active rooms yet. Create one above and start playing!"
        internal set
    var syncPlayParticipants: String = "%1\$d participant(s) • %2\$s"
        internal set
    var syncPlayJoin: String = "Join"
        internal set

    // ---- App update dialog (0.7) ----
    var updateAvailable: String = "Lumen %s Available"
        internal set
    var updateReleaseLink: String = "GitHub Release: %s"
        internal set

    // ---- Player (0.7) ----
    var playerSubtitleSyncHud: String = "Subtitle Sync: %s"
        internal set
    var playerSkippedRecapUndo: String = "Skipped Recap • Undo"
        internal set
    var playerSkippedIntroUndo: String = "Skipped Intro • Undo"
        internal set
    var playerSkipRecap: String = "Skip Recap"
        internal set
    var playerSkipIntro: String = "Skip Intro"
        internal set
    var playerNextEpisode: String = "Next Episode"
        internal set
    var playerPause: String = "Pause"
        internal set
    var playerBack10: String = "Back 10s"
        internal set
    var playerForward10: String = "Forward 10s"
        internal set
    var playerEpisodes: String = "Episodes"
        internal set
    var playerAudioSubtitles: String = "Audio & Subtitles"
        internal set
    var playerPlaybackSettings: String = "Playback Settings"
        internal set
    var playerVideoStream: String = "Video Stream"
        internal set
    var playerAudioInfo: String = "Audio: %s"
        internal set
    var playerQualityInfo: String = "Quality: %1\$s • %2\$s"
        internal set
    var playerNextIn: String = "Next Episode in %ds"
        internal set
    var playerUpNext: String = "Up Next"
        internal set
    var playerPlayNow: String = "Play Now"
        internal set
    var playerWatchCredits: String = "Watch Credits"
        internal set
    var playerLive: String = "LIVE"
        internal set
    var playerSeekPreview: String = "Seek preview"
        internal set
    var playerSelectEpisode: String = "Select an episode to play"
        internal set
    var playerNotOnJellyfin: String = "Not available on Jellyfin"
        internal set
    var playerNowPlaying: String = "Now Playing"
        internal set
    var playerChaptersCount: String = "Chapters (%d)"
        internal set
    var playerAudioTracks: String = "Audio Tracks"
        internal set
    var playerAudioTrackN: String = "Audio %d"
        internal set
    var playerSubtitleTrackN: String = "Subtitle %d"
        internal set
    var playerAssEngine: String = "ASS / SSA Subtitle Engine"
        internal set
    var playerAssTrackN: String = "ASS Track #%s"
        internal set
    var playerSubtitleSync: String = "Subtitle Sync (%s)"
        internal set
    var playerSubtitleSyncBitmap: String = "Text subtitles only — bitmap tracks (PGS/VobSub) cannot be shifted."
        internal set
    var playerSearchSubtitles: String = "Search / Download Subtitles"
        internal set
    var playerSearching: String = "Searching…"
        internal set
    var playerSpeed: String = "Playback Speed"
        internal set
    var playerVideoQuality: String = "Video Quality"
        internal set
    var playerAudioRouting: String = "Home Theater & Audio Routing"
        internal set
    var playerPassthrough: String = "Audio Passthrough (HDMI / Atmos / DTS)"
        internal set
    var playerDownmix: String = "Stereo 2.0 Downmix (Night / Soundbar)"
        internal set
    var playerAutomation: String = "Automation & Display"
        internal set
    var playerSubtitleSize: String = "Subtitle Size"
        internal set
    var playerSubtitleBackground: String = "Subtitle Background"
        internal set
    var playerStreamInfo: String = "Stream Info Overlay"
        internal set
    var qualityAuto: String = "Auto (Direct Play)"
        internal set
    var assStyled: String = "Styled (ASS)"
        internal set
    var assBurnIn: String = "Server Burn-In"
        internal set
    var assPlain: String = "Plain Text"
        internal set

    // ---- Profiles (0.7) ----
    var profileDone: String = "Done"
        internal set
    var profileManage: String = "Manage Profiles"
        internal set
    var profileWhoIsWatching: String = "Who's watching?"
        internal set
    var profileSignInPassword: String = "Sign In with Password"
        internal set
    var profileQuickConnect: String = "Quick Connect"
        internal set
    var profileQuickConnectHint: String = "Open Jellyfin or Seerr on your phone/computer → Quick Connect, and enter this code:"
        internal set
    var profileWaitingApproval: String = "Waiting for approval…"
        internal set
    var profileDetectedAccounts: String = "Detected Jellyfin Accounts:"
        internal set
    var profileUsername: String = "Jellyfin Username"
        internal set
    var profilePassword: String = "Password"
        internal set
    var profileSigningIn: String = "Signing in..."
        internal set
    var profileSignIn: String = "Sign In"
        internal set
    var profileUseQuickConnect: String = "Use Quick Connect"
        internal set
    var profileUsePassword: String = "Use Password"
        internal set
    var profileEnterPin: String = "Enter PIN for %s"
        internal set
    var profileSettingsTitle: String = "Profile Settings • %s"
        internal set
    var profileTabAppearance: String = "Appearance & Language"
        internal set
    var profileTabTheater: String = "Home Theater"
        internal set
    var profileTabKids: String = "Kids & PIN"
        internal set
    var profileUiPreset: String = "UI Preset"
        internal set
    var profileAppLanguage: String = "App UI Language"
        internal set
    var profileMetadataLanguage: String = "Metadata / Seerr Language"
        internal set
    var profileAutoplayPreviews: String = "Autoplay Previews While Browsing"
        internal set
    var profilePassthrough: String = "HDMI Audio Passthrough (TrueHD / DTS-HD / EAC3)"
        internal set
    var profileDownmix: String = "Stereo Downmix (TV Speakers / Night Mode)"
        internal set
    var profileAssSubtitles: String = "ASS / SSA Anime Subtitles"
        internal set
    var profilePreferredLanguage: String = "Preferred Audio / Subtitle Language"
        internal set
    var profileAudioChip: String = "Aud: %s"
        internal set
    var profileSubtitleChip: String = "Sub: %s"
        internal set
    var profileKidsMode: String = "Kids Profile Mode (Parental Filter)"
        internal set
    var profileAllowKidsRequests: String = "Allow Seerr Requests in Kids Mode"
        internal set
    var profileMaxRating: String = "Max Allowed Content Rating"
        internal set
    var ratingAll: String = "All Ages (Unrestricted)"
        internal set
    var ratingTeen: String = "Teen (PG-13 / TV-14)"
        internal set
    var ratingFamily: String = "Family (PG / TV-PG)"
        internal set
    var ratingKids: String = "Kids Only (G / TV-Y)"
        internal set
    var profilePinLocked: String = "Profile PIN (Currently Locked)"
        internal set
    var profileSetPin: String = "Set 4-Digit Profile PIN"
        internal set
    var profilePinNewPlaceholder: String = "•••• (enter new 4 digits)"
        internal set
    var profilePinPlaceholder: String = "4 digits (optional)"
        internal set
    var profileRemovePin: String = "Remove PIN"
        internal set
    var profilePinInvalid: String = "PIN must be exactly 4 digits."
        internal set
    var profileConfirmDelete: String = "Confirm Delete"
        internal set
    var profileDelete: String = "Delete Profile"
        internal set
    var profileEdit: String = "Edit Profile"
        internal set
    var profilePinProtected: String = "PIN Protected"
        internal set
    var profileAdd: String = "Add Profile"
        internal set

    // ---- UI presets (0.7) ----
    var presetPickerTitle: String = "Appearance & UI Preset"
        internal set
    var presetPickerSubtitle: String = "Personalize the layout and look for %s"
        internal set
    var presetNetflixDesc: String = "Left icon rail • Vertical posters • Top 10 numerals • Crimson accent"
        internal set
    var presetAppleTvDesc: String = "Floating glass bar • Tall full-bleed hero • 16:9 widescreen cards"
        internal set
    var presetDisneyDesc: String = "Top header bar • Midnight navy canvas • Electric blue glow"
        internal set

    // ---- Requests screen (0.7) ----
    var requestsTitle: String = "Requests & Downloads"
        internal set
    var requestsSubtitle: String = "Live status synced with Seerr v3+ • Auto-refreshes every 15s"
        internal set
    var requestsSubtitleJellyfinOnly: String = "Jellyfin-Only Mode • Add a Seerr URL in Server Settings to enable requests"
        internal set
    var requestsSeerrNotConnected: String = "Seerr Not Connected (Jellyfin-Only Mode)"
        internal set
    var requestsSeerrNotConnectedHint: String = "Add a Seerr URL in Server Setup to browse, submit, approve, or track media requests."
        internal set
    var requestsEmpty: String = "No requests in this view yet."
        internal set
    var requestsRequestedBy: String = "Requested by %1\$s • %2\$s"
        internal set
    var requestsFilterAvailable: String = "Available"
        internal set

    // ---- Search screen (0.7) ----
    var searchVoicePrompt: String = "Search movies, TV shows, or actors"
        internal set
    var searchPlaceholderSeerr: String = "Search movies, TV shows, or cast (Library + Seerr)..."
        internal set
    var searchPlaceholderLibrary: String = "Search movies & TV shows in your Jellyfin library..."
        internal set
    var searchVoice: String = "Voice Search"
        internal set
    var searchFilmography: String = "Filmography • %s"
        internal set
    var searchGenreHeader: String = "Genre • %s"
        internal set
    var searchClear: String = "Clear"
        internal set
    var searchTopSearches: String = "Top Searches"
        internal set
    var searchTypeHint: String = "Type a movie, TV show, or actor name to search."
        internal set
    var searchNoMatches: String = "No matching titles found."
        internal set
    var searchNoResults: String = "No results found for \"%s\""
        internal set
    var searchPeople: String = "People"
        internal set

    // ---- Setup screen (0.7) ----
    var setupTitle: String = "TV Server Setup"
        internal set
    var setupUpdateReady: String = "Update %s Ready"
        internal set
    var setupIntro: String = "Add your Jellyfin server (and optional Seerr v3+ request server). Local LAN URLs auto-failover to remote URLs when away from home."
        internal set
    var setupJellyfinUrl: String = "Jellyfin Server URL (Primary / Remote)"
        internal set
    var setupJellyfinUrlPlaceholder: String = "https://jellyfin.example.com or http://192.168.1.100:8096"
        internal set
    var setupJellyfinLocalUrl: String = "Jellyfin Local LAN URL (Optional Dual Failover)"
        internal set
    var setupJellyfinOnlyDesc: String = "Skip Seerr/Jellyseerr and browse your Jellyfin library directly"
        internal set
    var setupSeerrUrl: String = "Seerr v3+ / Jellyseerr URL (Primary / Remote)"
        internal set
    var setupSeerrLocalUrl: String = "Seerr Local LAN URL (Optional Dual Failover)"
        internal set
    var setupSeerrApiKey: String = "Seerr API Key (Optional — from Seerr Settings)"
        internal set
    var setupSeerrApiKeyPlaceholder: String = "Leave blank to sign in automatically with your Jellyfin account"
        internal set
    var setupSelfSigned: String = "Trust Self-Signed HTTPS Certificates"
        internal set
    var setupSelfSignedDesc: String = "Useful for homelab reverse proxies using local CA certs"
        internal set
    var setupConnect: String = "Connect & Continue"
        internal set
    var setupDiscovery: String = "LAN Auto-Discovery"
        internal set
    var setupDiscoveryScanning: String = "Broadcasting Jellyfin discovery on UDP 7359…"
        internal set
    var setupDiscoveryNone: String = "No servers auto-discovered yet. Press Scan to search your LAN."
        internal set
    var setupCompanion: String = "Phone Companion Setup"
        internal set
    var setupCompanionHint: String = "Hate typing URLs with a TV remote? Scan with your phone on the same Wi-Fi to paste URLs directly:"
        internal set
    var setupCompanionQr: String = "Setup Companion QR Code"
        internal set
    var setupCompanionOffline: String = "Connect TV to Wi-Fi / Ethernet to start mobile companion web setup."
        internal set
    var setupScanNetwork: String = "Scan Network"
        internal set
    var setupScanning: String = "Scanning…"
        internal set
    var setupScan: String = "Scan"
        internal set

    // ---- Hero banner & cards (0.7) ----
    var heroFilm: String = "FILM"
        internal set
    var heroSeries: String = "SERIES"
        internal set
    var heroFeaturedFilm: String = "FEATURED FILM"
        internal set
    var heroFeaturedSeries: String = "FEATURED SERIES"
        internal set
    var heroViewProgress: String = "View Progress"
        internal set
    var heroTogglePreviewAudio: String = "Toggle preview audio"
        internal set
    var cardDownloading: String = "Downloading..."
        internal set
    var cardPartial: String = "Partial"
        internal set
    var cardBadgePlay: String = "PLAY"
        internal set
    var cardBadgeRequest: String = "+ Request"
        internal set
    var cardCollection: String = "COLLECTION"
        internal set
    var cardTopRank: String = "TOP %d"
        internal set
    var cardHoldOk: String = "Hold OK"
        internal set
    var profileMenuSwitchProfile: String = "Switch Profile"
        internal set
    var homeFilterAllTitles: String = "All titles"
        internal set
    var cardTimeLeft: String = "%s left"
        internal set
    var librarySortFilter: String = "Sort & Filter"
        internal set
    var librarySortBy: String = "Sort by"
        internal set
    var libraryShow: String = "Show"
        internal set
    var detailsMore: String = "More"
        internal set
    var detailsTabEpisodes: String = "Episodes"
        internal set
    var detailsTabCollection: String = "Collection"
        internal set
    var profileParentPinName: String = "a parent profile"
        internal set

    // ---- Several Jellyfin servers ----
    var serversSection: String = "Other servers"
        internal set
    var serversAddTitle: String = "Add another server"
        internal set
    var serversAddDesc: String = "Merge a friend's Jellyfin library into Home, search and your libraries. Requests, Live TV and Watch Together stay on your own server."
        internal set
    var serverStatusOnline: String = "Signed in as %s"
        internal set
    var serverStatusOffline: String = "Can't reach this server right now. Its titles are hidden until it is back."
        internal set
    var serverStatusAuth: String = "This server signed you out. Select to sign in again."
        internal set
    var serverStatusNotSignedIn: String = "This profile isn't signed in here. Select to sign in."
        internal set
    var serverBitrateTitle: String = "Streaming quality limit for %s"
        internal set
    var serverBitrateNoLimit: String = "No limit"
        internal set
    var serverRemove: String = "Remove %s"
        internal set
    var serverRemoveDesc: String = "Its titles disappear from this TV for every profile. Nothing on the server is deleted."
        internal set
    var serverRemoveConfirm: String = "Select again to remove %s"
        internal set
    var serverSignIn: String = "Sign in"
        internal set
    var serverSignInAgain: String = "Sign in again"
        internal set
    var serverReSignInTitle: String = "Sign in again to %s"
        internal set
    var serverReSignInBody: String = "This server no longer accepts the saved sign-in (the password may have changed). Its titles are hidden until you sign in again. Everything else keeps working."
        internal set
    var serverReSignInLater: String = "Later"
        internal set
    var addServerIntro: String = "Enter the address of the other Jellyfin server. If it is on this network, pick it from the list. You sign in on the next step."
        internal set
    var addServerAddress: String = "Server address"
        internal set
    var addServerLocalAddress: String = "Address on this network (optional)"
        internal set
    var addServerTrustSelfSigned: String = "Trust this server's self-signed certificate"
        internal set
    var addServerContinue: String = "Continue"
        internal set
    var addServerChecking: String = "Checking..."
        internal set
    var addServerPhoneHint: String = "Or type the address on your phone: scan the code while on the same Wi-Fi."
        internal set
    var addServerSignInTitle: String = "Sign in to %s"
        internal set
    var addServerSignInHint: String = "Your username from this profile is filled in. Enter the password for your account on this server. Only the sign-in token is kept, never the password."
        internal set
    var addServerUsername: String = "Username"
        internal set
    var addServerPassword: String = "Password"
        internal set
    var addServerSigningIn: String = "Signing in..."
        internal set
    var addServerUseQuickConnect: String = "Use Quick Connect"
        internal set
    var addServerUsePassword: String = "Use a password instead"
        internal set
    var addServerQuickConnectHint: String = "On a device where you are already signed in to %s, open Quick Connect and enter this code:"
        internal set
    var addServerQuickConnectWaiting: String = "Waiting for approval..."
        internal set
    var addServerSyncing: String = "Signed in. Adding the library of %s..."
        internal set
    var addServerErrorUsername: String = "Enter a username."
        internal set
    var addServerErrorCredentials: String = "Couldn't sign in as %s. Check the password, try a different username, or use Quick Connect."
        internal set
    var addServerErrorQuickConnect: String = "Quick Connect isn't available on this server. Sign in with a password instead."
        internal set
    var addServerErrorQuickConnectExpired: String = "The Quick Connect code wasn't approved in time. Try again or use a password."
        internal set
    var homeServerOffline: String = "%s is offline. Showing everything else."
        internal set
    var homeServerNeedsSignIn: String = "%s needs a new sign-in (Settings > Other servers)."
        internal set
    var homeNothingForProfileHint: String = "Nothing here is rated for this profile. Titles without a rating are hidden on Kids and rating-limited profiles."
        internal set
}

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
        labelKidsLocked = "Solicitudes Bloqueadas (Modo Infantil)",

        // Settings (0.7)
        navSettings = "Ajustes",
        settingsTitle = "Ajustes",
        settingsPlaybackSection = "Reproducción",
        settingsMatchCapsTitle = "Adaptar a las capacidades de este TV",
        settingsMatchCapsDesc = "El servidor convierte lo que este dispositivo, TV o receptor no pueda reproducir: HDR en una pantalla SDR, DTS sin receptor o códecs no compatibles. Desactívalo solo si la reproducción se ve mal tras la detección.",
        settingsDetected = "Detectado",
        settingsPassthroughTitle = "Audio passthrough",
        settingsPassthroughDesc = "Envía el audio Dolby y DTS sin modificar a un receptor o barra de sonido compatible.",
        settingsDownmixTitle = "Mezcla estéreo",
        settingsDownmixDesc = "Convierte el sonido envolvente a 2.0 para los altavoces del TV o para ver de noche.",
        settingsFrameRateTitle = "Ajustar la frecuencia de imagen",
        settingsAutoSkipIntroTitle = "Saltar intros automáticamente",
        settingsAutoSkipRecapTitle = "Saltar resúmenes automáticamente",
        settingsAutoplayNextTitle = "Reproducir el siguiente episodio automáticamente",
        settingsSubtitlesSection = "Subtítulos",
        settingsSubtitleSize = "Tamaño del texto",
        settingsSubtitleColor = "Color del texto",
        settingsSubtitleBackground = "Fondo",
        settingsSubtitleEdge = "Estilo del borde",
        settingsSubtitlePosition = "Posición",
        settingsSubtitlePreview = "Así se verán los subtítulos.",
        settingsRemoteSection = "Control remoto",
        settingsRemoteTitle = "Permitir el control desde otras apps de Jellyfin",
        settingsRemoteDesc = "Muestra este TV en \"Reproducir en\" de Jellyfin en la web, móviles y ordenadores, y les permite pausar, avanzar o enviar mensajes.",
        settingsConnected = "Conectado al servidor",
        settingsConnecting = "Conectando...",
        settingsDisconnected = "Sin conexión",
        settingsDiagnosticsSection = "Ayuda y diagnóstico",
        settingsSendLogsTitle = "Enviar registros a mi servidor Jellyfin",
        settingsSendLogsDesc = "Guarda un informe (dispositivo, compatibilidad de reproducción, registro reciente de la app y último fallo) en tu propio servidor, en Panel > Registros. Los tokens de inicio de sesión se eliminan y no se envía nada a ningún otro sitio.",
        settingsSendingLogs = "Enviando...",
        settingsLogsSent = "Guardado en tu servidor como %s",
        settingsLogsDisabled = "Tu servidor no acepta registros de apps. Un administrador puede activar \"Permitir subida de registros de clientes\" en Panel > General.",
        settingsLogsFailed = "No se pudieron enviar los registros: %s",
        settingsCrashPending = "Lumen se cerró inesperadamente la última vez; se incluirá ese informe.",
        settingsServerSection = "Servidor y cuenta",
        settingsServerTitle = "Conexión al servidor",
        settingsServerDesc = "Direcciones de Jellyfin y Seerr, inicio de sesión y modo solo Jellyfin.",
        settingsAboutSection = "Acerca de",
        settingsVersion = "Versión %s",
        optionOn = "Activado",
        optionOff = "Desactivado",
        frameRateSeamless = "Solo sin cortes",
        frameRateAlways = "Siempre",
        sizeSmall = "Pequeño",
        sizeMedium = "Mediano",
        sizeLarge = "Grande",
        sizeExtraLarge = "Muy grande",
        backgroundNone = "Ninguno",
        backgroundLight = "Claro",
        backgroundMedium = "Medio",
        backgroundSolid = "Opaco",
        colorWhite = "Blanco",
        colorYellow = "Amarillo",
        colorCyan = "Cian",
        colorGreen = "Verde",
        colorGrey = "Gris claro",
        edgeOutline = "Contorno",
        edgeDropShadow = "Sombra",
        edgeRaised = "Relieve",
        edgeNone = "Ninguno",
        positionBottom = "Abajo",
        positionRaised = "Algo más arriba",
        positionHigh = "Arriba",
        crashPromptTitle = "Lumen se cerró inesperadamente",
        crashPromptBody = "¿Enviar un informe a tu servidor Jellyfin para que se pueda revisar el problema? Se queda en tu servidor.",
        crashPromptSend = "Enviar informe",
        crashPromptDismiss = "Ahora no",

        // Library browsing (0.7)
        libraryBrowseAll = "Ver todo",
        libraryAllMovies = "Todas las películas",
        libraryAllShows = "Todas las series",
        librarySort = "Ordenar",
        sortName = "A-Z",
        sortDateAdded = "Fecha de adición",
        sortReleased = "Fecha de estreno",
        sortRating = "Valoración",
        sortRandom = "Aleatorio",
        libraryFilterAll = "Todo",
        libraryFilterUnwatched = "No vistos",
        libraryFilterWatched = "Vistos",
        libraryFilterFavorites = "Favoritos",
        libraryAllGenres = "Todos los géneros",
        libraryGenre = "Género",
        libraryTitleCount = "%d títulos",
        libraryEmpty = "Nada coincide con estos filtros.",
        libraryJumpHint = "Ir a la letra",

        // DVR (0.7)
        dvrRecord = "Grabar",
        dvrRecordSeries = "Grabar serie",
        dvrCancelRecording = "Cancelar grabación",
        dvrCancelSeries = "Cancelar grabación de la serie",
        dvrScheduled = "Grabaciones programadas",
        dvrNoneScheduled = "No hay grabaciones programadas",
        dvrRecordingSet = "Grabación programada",
        dvrSeriesSet = "Grabación de la serie programada",
        dvrCancelled = "Grabación cancelada",
        dvrFailed = "No se pudo cambiar la grabación: %s",
        dvrRecordingBadge = "REC",
        dvrWatchLive = "Ver en directo",
        dvrProgramOptions = "Opciones del programa"
    ).apply {
        // Common (0.8)
        commonRetry = "Reintentar"
        commonGoBack = "Volver"
        commonBack = "Atrás"
        commonClose = "Cerrar"
        commonRefresh = "Actualizar"
        commonSave = "Guardar"
        commonSending = "Enviando..."
        commonActive = "Activo"
        commonDefault = "Por defecto"
        commonMovie = "Película"
        commonSeries = "Serie"
        commonTvSeries = "Serie"
        commonInLibrary = "En la biblioteca"
        commonDownloading = "Descargando"
        commonRequested = "Solicitado"
        commonMatchPercent = "%d%% de coincidencia"
        commonMinutesShort = "%d min"

        // Details screen (0.8)
        detailsTitleNotFound = "Título no encontrado"
        detailsSeasonsEpisodes = "Temporadas y episodios"
        detailsPartOf = "Parte de %s"
        detailsRequestMissing = "Pedir los que faltan (%d)"
        detailsMoreLikeThis = "Títulos similares"
        detailsTrailersMore = "Tráileres y más"
        detailsCast = "Reparto"
        detailsPlayTrailer = "Ver tráiler"
        detailsPlayExtra = "Reproducir extra"
        detailsRequest4kMore = "Solicitar 4K / más"
        detailsRequestHd4k = "Solicitar HD / 4K"
        detailsInMyList = "En Mi Lista"
        detailsReminderSet = "Recordatorio activado"
        detailsRemindMe = "Recordármelo"
        detailsRemoveProgress = "Quitar progreso"
        detailsCancelRequest = "Cancelar solicitud"
        detailsTierAvailable = "%s disponible"
        detailsTierPartial = "%s parcial"
        detailsTierDownloading = "%s descargando"
        detailsTierRequested = "%s solicitado"
        detailsTierNotInLibrary = "%s no está en la biblioteca"
        detailsNotOnServer = "No está en el servidor"
        detailsRequestTitle = "Solicitar \"%s\""
        detailsRequestIntro = "Elige la calidad, el servidor Radarr/Sonarr, el perfil y la carpeta raíz:"
        detailsAlreadyInLibrary = "Ya está en la biblioteca"
        detailsAlreadyRequested = "Ya solicitado"
        detailsStandardProfile = "Perfil estándar de Radarr / Sonarr"
        details4kProfile = "Perfil 4K de Radarr / Sonarr"
        detailsLoadingProfiles = "Cargando perfiles de Radarr / Sonarr..."
        detailsSelectSeasons = "Elige temporadas (%d seleccionadas):"
        detailsSendRequest = "Enviar solicitud"
        detailsServer = "Servidor"
        detailsQualityProfile = "Perfil de calidad"
        detailsRootFolder = "Carpeta raíz"
        detailsIssueHint = "Describe el problema de reproducción o subtítulos (opcional)"
        detailsReporting = "Enviando..."
        detailsSubmitIssue = "Enviar problema"
        issueVideo = "Problema de vídeo"
        issueAudio = "Audio desfasado / ausente"
        issueSubtitle = "Subtítulos"
        issueOther = "Otro"

        // Home screen (0.8)
        homeProfileFallback = "Perfil"
        homeLoadingHub = "Cargando tu inicio estilo %s…"
        homeMyListEmpty = "Tu lista está vacía"
        homeMyListEmptyHint = "Añade películas y series desde el banner principal o la ficha para encontrarlas aquí."
        homeNoTitlesMatch = "Ningún título coincide con \"%s\""
        homeNoTitlesMatchHint = "Vuelve a Todo o elige otro género para seguir explorando."
        homeNothingYet = "Aún no hay nada aquí"
        homeNothingYetHint = "Tus bibliotecas no devolvieron títulos. Usa Actualizar en el menú para volver a intentarlo."
        homeAppearance = "Apariencia"
        homeUpdate = "Actualización"
        homeAppUpdate = "Actualizar app"
        homeServers = "Servidores"
        homeFilterWatchNow = "Ver ahora"
        homeFilterRequestable = "Para solicitar"
        homeJellyfinOnlyMode = "Modo solo Jellyfin"
        homeKidsModeActive = "Modo infantil activo"
        homeRequestedDownloading = "Solicitado / descargando"
        homeAvailableToRequest = "Disponible para solicitar"
        homeRemoveFromMyList = "Quitar de Mi Lista"
        homeAddToMyList = "Añadir a Mi Lista"
        homeDetailsCastTrailers = "Detalles, reparto y tráileres"
        homeRemoveFromContinue = "Quitar de Seguir viendo"
        homeNoCollections = "No se encontraron colecciones de Jellyfin"
        homeNoCollectionsHint = "Crea colecciones (BoxSets) en Jellyfin para explorar sagas y completar aquí las secuelas que faltan."
        homeCollectionsSubtitle = "Explora tus colecciones de Jellyfin y descubre qué falta para completar cada saga."
        homeCollectionEmpty = "No hay títulos en esta colección."
        homeMissingFromLibrary = "Falta en la biblioteca"
        homeDetails = "Detalles"
        homeJustArrived = "Recién llegado • %s"
        homeJustArrivedBody = "Tu solicitud de Seerr ya se descargó y está lista para ver."
        homeDismiss = "Descartar"
        genreAll = "Todos los géneros"
        genreAction = "Acción"
        genreComedy = "Comedia"
        genreSciFi = "Ciencia ficción"
        genreHorrorMystery = "Terror y misterio"
        genreDrama = "Drama"
        genreCrime = "Crimen"
        genreThriller = "Suspense"
        genreDocumentary = "Documental"
        genreAnimation = "Animación"

        // Live TV guide (0.8)
        liveNoChannels = "No se encontraron canales de TV en vivo"
        liveNoChannelsHint = "Configura un sintonizador IPTV / HDHomeRun y una guía XMLTV en Panel de Jellyfin → TV en vivo."
        liveGuideTitle = "Guía de TV en vivo y grabaciones"
        liveChannelCount = "%d canal(es) disponible(s) • Elige un canal para verlo en directo."
        liveStream = "Emisión en directo"
        liveOnNow = "AHORA • %s"
        liveUpNext = "A CONTINUACIÓN"
        liveTuneIn = "Sintonizar"
        liveDvrRecordings = "Grabaciones"

        // Customize Home dialog (0.8)
        customizeTitle = "Personalizar inicio • %s"
        customizePinLibraries = "Fijar bibliotecas de Jellyfin como filas de inicio"
        customizeStartupTab = "Pestaña de inicio"
        customizeHideReorder = "Ocultar / reordenar filas"
        customizeShow = "Mostrar"
        customizeHide = "Ocultar"
        customizeUp = "Subir"
        customizeDown = "Bajar"
        customizeSave = "Guardar diseño"

        // SyncPlay dialog (0.8)
        syncPlayTitle = "SyncPlay • Ver juntos"
        syncPlayDefaultRoom = "Noche de cine"
        syncPlayActiveRoom = "Sala activa: %s"
        syncPlayViewers = "%1\$d espectador(es) • %2\$s"
        syncPlayLeave = "Salir de la sala"
        syncPlayNewName = "Nombre de la nueva sala"
        syncPlayCreate = "Crear sala"
        syncPlayRooms = "Salas de SyncPlay activas en Jellyfin"
        syncPlayNoRooms = "Aún no hay salas activas. ¡Crea una arriba y empieza a ver!"
        syncPlayParticipants = "%1\$d participante(s) • %2\$s"
        syncPlayJoin = "Unirse"

        // App update dialog (0.8)
        updateAvailable = "Lumen %s disponible"
        updateReleaseLink = "Versión en GitHub: %s"

        // Player (0.8)
        playerSubtitleSyncHud = "Sincronía de subtítulos: %s"
        playerSkippedRecapUndo = "Resumen omitido • Deshacer"
        playerSkippedIntroUndo = "Intro omitida • Deshacer"
        playerSkipRecap = "Saltar resumen"
        playerSkipIntro = "Saltar intro"
        playerNextEpisode = "Siguiente episodio"
        playerPause = "Pausa"
        playerBack10 = "Retroceder 10 s"
        playerForward10 = "Avanzar 10 s"
        playerEpisodes = "Episodios"
        playerAudioSubtitles = "Audio y subtítulos"
        playerPlaybackSettings = "Ajustes de reproducción"
        playerVideoStream = "Vídeo"
        playerQualityInfo = "Calidad: %1\$s • %2\$s"
        playerNextIn = "Siguiente episodio en %d s"
        playerUpNext = "A continuación"
        playerPlayNow = "Reproducir ahora"
        playerWatchCredits = "Ver créditos"
        playerLive = "EN VIVO"
        playerSeekPreview = "Vista previa de la posición"
        playerSelectEpisode = "Elige un episodio para reproducir"
        playerNotOnJellyfin = "No disponible en Jellyfin"
        playerNowPlaying = "Reproduciendo"
        playerChaptersCount = "Capítulos (%d)"
        playerAudioTracks = "Pistas de audio"
        playerSubtitleTrackN = "Subtítulo %d"
        playerAssEngine = "Motor de subtítulos ASS / SSA"
        playerAssTrackN = "Pista ASS n.º %s"
        playerSubtitleSync = "Sincronía de subtítulos (%s)"
        playerSubtitleSyncBitmap = "Solo subtítulos de texto: las pistas de imagen (PGS/VobSub) no se pueden desplazar."
        playerSearchSubtitles = "Buscar / descargar subtítulos"
        playerSearching = "Buscando…"
        playerSpeed = "Velocidad de reproducción"
        playerVideoQuality = "Calidad de vídeo"
        playerAudioRouting = "Cine en casa y salida de audio"
        playerPassthrough = "Audio passthrough (HDMI / Atmos / DTS)"
        playerDownmix = "Mezcla estéreo 2.0 (noche / barra de sonido)"
        playerAutomation = "Automatización y pantalla"
        playerSubtitleSize = "Tamaño de subtítulos"
        playerSubtitleBackground = "Fondo de subtítulos"
        playerStreamInfo = "Información de la transmisión"
        qualityAuto = "Auto (reproducción directa)"
        assStyled = "Con estilo (ASS)"
        assBurnIn = "Incrustado en servidor"
        assPlain = "Texto simple"

        // Profiles (0.8)
        profileDone = "Listo"
        profileManage = "Administrar perfiles"
        profileWhoIsWatching = "¿Quién está viendo?"
        profileSignInPassword = "Iniciar sesión con contraseña"
        profileQuickConnect = "Conexión rápida"
        profileQuickConnectHint = "Abre Jellyfin o Seerr en tu móvil u ordenador → Conexión rápida e introduce este código:"
        profileWaitingApproval = "Esperando aprobación…"
        profileDetectedAccounts = "Cuentas de Jellyfin detectadas:"
        profileUsername = "Usuario de Jellyfin"
        profilePassword = "Contraseña"
        profileSigningIn = "Iniciando sesión..."
        profileSignIn = "Iniciar sesión"
        profileUseQuickConnect = "Usar conexión rápida"
        profileUsePassword = "Usar contraseña"
        profileEnterPin = "Introduce el PIN de %s"
        profileSettingsTitle = "Ajustes del perfil • %s"
        profileTabAppearance = "Apariencia e idioma"
        profileTabTheater = "Cine en casa"
        profileTabKids = "Niños y PIN"
        profileUiPreset = "Estilo de interfaz"
        profileAppLanguage = "Idioma de la app"
        profileMetadataLanguage = "Idioma de metadatos / Seerr"
        profileAutoplayPreviews = "Reproducir avances automáticamente al navegar"
        profilePassthrough = "Audio passthrough por HDMI (TrueHD / DTS-HD / EAC3)"
        profileDownmix = "Mezcla estéreo (altavoces del TV / modo nocturno)"
        profileAssSubtitles = "Subtítulos de anime ASS / SSA"
        profilePreferredLanguage = "Idioma preferido de audio / subtítulos"
        profileKidsMode = "Perfil infantil (control parental)"
        profileAllowKidsRequests = "Permitir solicitudes de Seerr en modo infantil"
        profileMaxRating = "Clasificación máxima permitida"
        ratingAll = "Todas las edades (sin restricciones)"
        ratingTeen = "Adolescentes (PG-13 / TV-14)"
        ratingFamily = "Familiar (PG / TV-PG)"
        ratingKids = "Solo niños (G / TV-Y)"
        profilePinLocked = "PIN del perfil (bloqueado)"
        profileSetPin = "Establecer PIN de 4 dígitos"
        profilePinNewPlaceholder = "•••• (nuevo PIN de 4 dígitos)"
        profilePinPlaceholder = "4 dígitos (opcional)"
        profileRemovePin = "Quitar PIN"
        profilePinInvalid = "El PIN debe tener exactamente 4 dígitos."
        profileConfirmDelete = "Confirmar eliminación"
        profileDelete = "Eliminar perfil"
        profileEdit = "Editar perfil"
        profilePinProtected = "Protegido con PIN"
        profileAdd = "Añadir perfil"

        // UI presets (0.8)
        presetPickerTitle = "Apariencia y estilo"
        presetPickerSubtitle = "Personaliza el diseño y el aspecto para %s"
        presetNetflixDesc = "Barra lateral de iconos • Pósteres verticales • Números del Top 10 • Acento carmesí"
        presetAppleTvDesc = "Barra de cristal flotante • Portada a pantalla completa • Tarjetas panorámicas 16:9"
        presetDisneyDesc = "Barra superior • Fondo azul marino • Brillo azul eléctrico"

        // Requests screen (0.8)
        requestsTitle = "Solicitudes y descargas"
        requestsSubtitle = "Estado en tiempo real sincronizado con Seerr v3+ • Se actualiza cada 15 s"
        requestsSubtitleJellyfinOnly = "Modo solo Jellyfin • Añade una URL de Seerr en los ajustes del servidor para activar las solicitudes"
        requestsSeerrNotConnected = "Seerr no conectado (modo solo Jellyfin)"
        requestsSeerrNotConnectedHint = "Añade una URL de Seerr en la configuración del servidor para ver, enviar, aprobar o seguir solicitudes."
        requestsEmpty = "Aún no hay solicitudes aquí."
        requestsRequestedBy = "Solicitado por %1\$s • %2\$s"
        requestsFilterAvailable = "Disponible"

        // Search screen (0.8)
        searchVoicePrompt = "Busca películas, series o actores"
        searchPlaceholderSeerr = "Busca películas, series o reparto (biblioteca + Seerr)..."
        searchPlaceholderLibrary = "Busca películas y series en tu biblioteca de Jellyfin..."
        searchVoice = "Búsqueda por voz"
        searchFilmography = "Filmografía • %s"
        searchGenreHeader = "Género • %s"
        searchClear = "Borrar"
        searchTopSearches = "Búsquedas populares"
        searchTypeHint = "Escribe el nombre de una película, serie o actor para buscar."
        searchNoMatches = "No se encontraron títulos."
        searchNoResults = "No hay resultados para \"%s\""
        searchPeople = "Personas"

        // Setup screen (0.8)
        setupTitle = "Configuración del servidor"
        setupUpdateReady = "Actualización %s lista"
        setupIntro = "Añade tu servidor Jellyfin (y, si quieres, un servidor de solicitudes Seerr v3+). Fuera de casa, las URL de la red local cambian automáticamente a las remotas."
        setupJellyfinUrl = "URL del servidor Jellyfin (principal / remota)"
        setupJellyfinUrlPlaceholder = "https://jellyfin.example.com o http://192.168.1.100:8096"
        setupJellyfinLocalUrl = "URL local de Jellyfin en LAN (opcional, respaldo)"
        setupJellyfinOnlyDesc = "Omite Seerr/Jellyseerr y explora directamente tu biblioteca de Jellyfin"
        setupSeerrUrl = "URL de Seerr v3+ / Jellyseerr (principal / remota)"
        setupSeerrLocalUrl = "URL local de Seerr en LAN (opcional, respaldo)"
        setupSeerrApiKey = "Clave API de Seerr (opcional, en los ajustes de Seerr)"
        setupSeerrApiKeyPlaceholder = "Déjalo vacío para iniciar sesión automáticamente con tu cuenta de Jellyfin"
        setupSelfSigned = "Confiar en certificados HTTPS autofirmados"
        setupSelfSignedDesc = "Útil para proxies inversos domésticos con certificados de una CA local"
        setupConnect = "Conectar y continuar"
        setupDiscovery = "Detección automática en la red"
        setupDiscoveryScanning = "Buscando servidores Jellyfin por UDP 7359…"
        setupDiscoveryNone = "Aún no se ha detectado ningún servidor. Pulsa Buscar para explorar tu red local."
        setupCompanion = "Configurar desde el móvil"
        setupCompanionHint = "¿Cansado de escribir URL con el mando? Escanea con tu móvil en la misma Wi-Fi para pegarlas directamente:"
        setupCompanionQr = "Código QR de configuración"
        setupCompanionOffline = "Conecta el TV a Wi-Fi o Ethernet para configurarlo desde el móvil."
        setupScanNetwork = "Buscar en la red"
        setupScanning = "Buscando…"
        setupScan = "Buscar"

        // Hero banner & cards (0.8)
        heroFilm = "PELÍCULA"
        heroSeries = "SERIE"
        heroFeaturedFilm = "PELÍCULA DESTACADA"
        heroFeaturedSeries = "SERIE DESTACADA"
        heroViewProgress = "Ver progreso"
        heroTogglePreviewAudio = "Activar o silenciar el audio"
        cardDownloading = "Descargando..."
        cardPartial = "Parcial"
        cardBadgePlay = "VER"
        cardBadgeRequest = "+ SOL"
        cardCollection = "COLECCIÓN"
        cardHoldOk = "Mantén OK"
        profileMenuSwitchProfile = "Cambiar de perfil"
        homeFilterAllTitles = "Todos los títulos"
        cardTimeLeft = "quedan %s"
        librarySortFilter = "Ordenar y filtrar"
        librarySortBy = "Ordenar por"
        libraryShow = "Mostrar"
        detailsMore = "Más"
        detailsTabEpisodes = "Episodios"
        detailsTabCollection = "Colección"
        profileParentPinName = "un perfil de adulto"
        homeNothingForProfileHint = "No hay nada clasificado para este perfil. Los títulos sin clasificación se ocultan en perfiles infantiles o con límite de edad."
    }
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
        labelKidsLocked = "Demandes Verrouillées (Mode Enfant)",

        // Settings (0.7)
        navSettings = "Paramètres",
        settingsTitle = "Paramètres",
        settingsPlaybackSection = "Lecture",
        settingsMatchCapsTitle = "Adapter aux capacités de ce téléviseur",
        settingsMatchCapsDesc = "Le serveur convertit ce que ce boîtier, ce téléviseur ou l'ampli ne peut pas lire : HDR sur un écran SDR, DTS sans ampli, codecs manquants. Ne désactivez cette option que si la lecture semble incorrecte après la détection.",
        settingsDetected = "Détecté",
        settingsPassthroughTitle = "Audio passthrough",
        settingsPassthroughDesc = "Envoie le son Dolby et DTS intact vers un ampli ou une barre de son compatible.",
        settingsDownmixTitle = "Mixage stéréo",
        settingsDownmixDesc = "Réduit le son surround en 2.0 pour les haut-parleurs du téléviseur ou l'écoute de nuit.",
        settingsFrameRateTitle = "Adapter la fréquence d'images",
        settingsAutoSkipIntroTitle = "Passer les génériques automatiquement",
        settingsAutoSkipRecapTitle = "Passer les résumés automatiquement",
        settingsAutoplayNextTitle = "Lire l'épisode suivant automatiquement",
        settingsSubtitlesSection = "Sous-titres",
        settingsSubtitleSize = "Taille du texte",
        settingsSubtitleColor = "Couleur du texte",
        settingsSubtitleBackground = "Arrière-plan",
        settingsSubtitleEdge = "Style des contours",
        settingsSubtitlePosition = "Position",
        settingsSubtitlePreview = "Voici l'aspect des sous-titres.",
        settingsRemoteSection = "Contrôle à distance",
        settingsRemoteTitle = "Autoriser le contrôle depuis d'autres applis Jellyfin",
        settingsRemoteDesc = "Affiche ce téléviseur dans « Lire sur » de Jellyfin sur le web, les téléphones et les ordinateurs, qui peuvent alors mettre en pause, naviguer ou envoyer des messages.",
        settingsConnected = "Connecté au serveur",
        settingsConnecting = "Connexion...",
        settingsDisconnected = "Non connecté",
        settingsDiagnosticsSection = "Aide et diagnostic",
        settingsSendLogsTitle = "Envoyer les journaux à mon serveur Jellyfin",
        settingsSendLogsDesc = "Enregistre un rapport (appareil, compatibilité de lecture, journal récent de l'appli, dernier plantage) sur votre propre serveur, dans Tableau de bord > Journaux. Les jetons de connexion sont retirés et rien n'est envoyé ailleurs.",
        settingsSendingLogs = "Envoi...",
        settingsLogsSent = "Enregistré sur votre serveur sous %s",
        settingsLogsDisabled = "Votre serveur n'accepte pas les journaux d'applis. Un administrateur peut activer « Autoriser l'envoi des journaux client » dans Tableau de bord > Général.",
        settingsLogsFailed = "Impossible d'envoyer les journaux : %s",
        settingsCrashPending = "Lumen s'est fermé de façon inattendue la dernière fois ; ce rapport sera inclus.",
        settingsServerSection = "Serveur et compte",
        settingsServerTitle = "Connexion au serveur",
        settingsServerDesc = "Adresses Jellyfin et Seerr, connexion et mode Jellyfin uniquement.",
        settingsAboutSection = "À propos",
        settingsVersion = "Version %s",
        optionOn = "Activé",
        optionOff = "Désactivé",
        frameRateSeamless = "Sans coupure uniquement",
        frameRateAlways = "Toujours",
        sizeSmall = "Petit",
        sizeMedium = "Moyen",
        sizeLarge = "Grand",
        sizeExtraLarge = "Très grand",
        backgroundNone = "Aucun",
        backgroundLight = "Léger",
        backgroundMedium = "Moyen",
        backgroundSolid = "Opaque",
        colorWhite = "Blanc",
        colorYellow = "Jaune",
        colorCyan = "Cyan",
        colorGreen = "Vert",
        colorGrey = "Gris clair",
        edgeOutline = "Contour",
        edgeDropShadow = "Ombre portée",
        edgeRaised = "Relief",
        edgeNone = "Aucun",
        positionBottom = "En bas",
        positionRaised = "Surélevé",
        positionHigh = "En haut",
        crashPromptTitle = "Lumen s'est fermé de façon inattendue",
        crashPromptBody = "Envoyer un rapport à votre serveur Jellyfin pour que le problème puisse être analysé ? Il reste sur votre serveur.",
        crashPromptSend = "Envoyer le rapport",
        crashPromptDismiss = "Plus tard",

        // Library browsing (0.7)
        libraryBrowseAll = "Tout parcourir",
        libraryAllMovies = "Tous les films",
        libraryAllShows = "Toutes les séries",
        librarySort = "Trier",
        sortName = "A-Z",
        sortDateAdded = "Date d'ajout",
        sortReleased = "Date de sortie",
        sortRating = "Note",
        sortRandom = "Aléatoire",
        libraryFilterAll = "Tout",
        libraryFilterUnwatched = "Non vus",
        libraryFilterWatched = "Vus",
        libraryFilterFavorites = "Favoris",
        libraryAllGenres = "Tous les genres",
        libraryGenre = "Genre",
        libraryTitleCount = "%d titres",
        libraryEmpty = "Aucun titre ne correspond à ces filtres.",
        libraryJumpHint = "Aller à la lettre",

        // DVR (0.7)
        dvrRecord = "Enregistrer",
        dvrRecordSeries = "Enregistrer la série",
        dvrCancelRecording = "Annuler l'enregistrement",
        dvrCancelSeries = "Annuler l'enregistrement de la série",
        dvrScheduled = "Enregistrements programmés",
        dvrNoneScheduled = "Aucun enregistrement programmé",
        dvrRecordingSet = "Enregistrement programmé",
        dvrSeriesSet = "Enregistrement de la série programmé",
        dvrCancelled = "Enregistrement annulé",
        dvrFailed = "Impossible de modifier l'enregistrement : %s",
        dvrRecordingBadge = "REC",
        dvrWatchLive = "Regarder en direct",
        dvrProgramOptions = "Options du programme"
    ).apply {
        // Common (0.8)
        commonRetry = "Réessayer"
        commonGoBack = "Retour"
        commonBack = "Retour"
        commonClose = "Fermer"
        commonRefresh = "Actualiser"
        commonSave = "Enregistrer"
        commonSending = "Envoi..."
        commonActive = "Actif"
        commonDefault = "Par défaut"
        commonMovie = "Film"
        commonSeries = "Série"
        commonTvSeries = "Série TV"
        commonInLibrary = "Dans la bibliothèque"
        commonDownloading = "En téléchargement"
        commonRequested = "Demandé"
        commonMatchPercent = "%d%% de correspondance"
        commonMinutesShort = "%d min"

        // Details screen (0.8)
        detailsTitleNotFound = "Titre introuvable"
        detailsSeasonsEpisodes = "Saisons et épisodes"
        detailsPartOf = "Fait partie de %s"
        detailsRequestMissing = "Demander les manquants (%d)"
        detailsMoreLikeThis = "Titres similaires"
        detailsTrailersMore = "Bandes-annonces et plus"
        detailsCast = "Distribution"
        detailsPlayTrailer = "Lire la bande-annonce"
        detailsPlayExtra = "Lire le bonus"
        detailsRequest4kMore = "Demander 4K / plus"
        detailsRequestHd4k = "Demander HD / 4K"
        detailsInMyList = "Dans Ma Liste"
        detailsReminderSet = "Rappel activé"
        detailsRemindMe = "Me le rappeler"
        detailsRemoveProgress = "Effacer la progression"
        detailsCancelRequest = "Annuler la demande"
        detailsTierAvailable = "%s disponible"
        detailsTierPartial = "%s partiel"
        detailsTierDownloading = "%s en téléchargement"
        detailsTierRequested = "%s demandé"
        detailsTierNotInLibrary = "%s absent de la bibliothèque"
        detailsNotOnServer = "Absent du serveur"
        detailsRequestTitle = "Demander « %s »"
        detailsRequestIntro = "Choisissez la qualité, le serveur Radarr/Sonarr, le profil et le dossier racine :"
        detailsAlreadyInLibrary = "Déjà dans la bibliothèque"
        detailsAlreadyRequested = "Déjà demandé"
        detailsStandardProfile = "Profil Radarr / Sonarr standard"
        details4kProfile = "Profil Radarr / Sonarr 4K"
        detailsLoadingProfiles = "Chargement des profils Radarr / Sonarr..."
        detailsSelectSeasons = "Choisir les saisons (%d sélectionnées) :"
        detailsSendRequest = "Envoyer la demande"
        detailsServer = "Serveur"
        detailsQualityProfile = "Profil de qualité"
        detailsRootFolder = "Dossier racine"
        detailsIssueHint = "Décrivez le problème de lecture ou de sous-titres (facultatif)"
        detailsReporting = "Envoi..."
        detailsSubmitIssue = "Signaler"
        issueVideo = "Problème vidéo"
        issueAudio = "Audio décalé / absent"
        issueSubtitle = "Sous-titres"
        issueOther = "Autre"

        // Home screen (0.8)
        homeProfileFallback = "Profil"
        homeLoadingHub = "Chargement de votre accueil style %s…"
        homeMyListEmpty = "Votre liste est vide"
        homeMyListEmptyHint = "Ajoutez des films et des séries depuis la bannière ou la fiche détaillée pour les retrouver ici."
        homeNoTitlesMatch = "Aucun titre pour « %s »"
        homeNoTitlesMatchHint = "Revenez à Tout ou choisissez un autre genre pour continuer."
        homeNothingYet = "Rien à afficher pour l'instant"
        homeNothingYetHint = "Vos bibliothèques n'ont renvoyé aucun titre. Utilisez Actualiser dans le menu pour réessayer."
        homeAppearance = "Apparence"
        homeUpdate = "Mise à jour"
        homeAppUpdate = "Mise à jour"
        homeServers = "Serveurs"
        homeFilterWatchNow = "Regarder"
        homeFilterRequestable = "À demander"
        homeJellyfinOnlyMode = "Mode Jellyfin uniquement"
        homeKidsModeActive = "Mode enfant actif"
        homeSyncPlayChip = "SyncPlay : %s"
        homeRequestedDownloading = "Demandé / en téléchargement"
        homeAvailableToRequest = "Disponible à la demande"
        homeRemoveFromMyList = "Retirer de Ma Liste"
        homeAddToMyList = "Ajouter à Ma Liste"
        homeDetailsCastTrailers = "Détails, distribution et bandes-annonces"
        homeRemoveFromContinue = "Retirer de Reprendre la lecture"
        homeNoCollections = "Aucune collection Jellyfin trouvée"
        homeNoCollectionsHint = "Créez des collections (BoxSets) dans Jellyfin pour parcourir les sagas et compléter ici les suites manquantes."
        homeCollectionsSubtitle = "Parcourez vos collections Jellyfin et découvrez les titres manquants pour compléter chaque saga."
        homeCollectionEmpty = "Aucun titre dans cette collection."
        homeMissingFromLibrary = "Absent de la bibliothèque"
        homeDetails = "Détails"
        homeJustArrived = "Nouveauté • %s"
        homeJustArrivedBody = "Votre demande Seerr est téléchargée et prête à être regardée."
        homeDismiss = "Ignorer"
        genreAll = "Tous les genres"
        genreAction = "Action"
        genreComedy = "Comédie"
        genreSciFi = "Science-fiction"
        genreHorrorMystery = "Horreur et mystère"
        genreDrama = "Drame"
        genreCrime = "Policier"
        genreThriller = "Thriller"
        genreDocumentary = "Documentaire"
        genreAnimation = "Animation"

        // Live TV guide (0.8)
        liveNoChannels = "Aucune chaîne TV en direct trouvée"
        liveNoChannelsHint = "Configurez un tuner IPTV / HDHomeRun et un guide XMLTV dans Tableau de bord Jellyfin → TV en direct."
        liveGuideTitle = "Guide TV en direct et enregistrements"
        liveChannelCount = "%d chaîne(s) disponible(s) • Choisissez une chaîne pour la regarder en direct."
        liveStream = "Direct"
        liveOnNow = "EN CE MOMENT • %s"
        liveUpNext = "ENSUITE"
        liveTuneIn = "Regarder"
        liveDvrRecordings = "Enregistrements"

        // Customize Home dialog (0.8)
        customizeTitle = "Personnaliser l'accueil • %s"
        customizePinLibraries = "Épingler des bibliothèques Jellyfin en rangées"
        customizeStartupTab = "Onglet au démarrage"
        customizeHideReorder = "Masquer / réorganiser les rangées"
        customizeShow = "Afficher"
        customizeHide = "Masquer"
        customizeUp = "Monter"
        customizeDown = "Descendre"
        customizeSave = "Enregistrer"

        // SyncPlay dialog (0.8)
        syncPlayTitle = "SyncPlay • Regarder ensemble"
        syncPlayDefaultRoom = "Soirée ciné"
        syncPlayActiveRoom = "Salon actif : %s"
        syncPlayViewers = "%1\$d spectateur(s) • %2\$s"
        syncPlayLeave = "Quitter le salon"
        syncPlayNewName = "Nom du nouveau salon"
        syncPlayCreate = "Créer un salon"
        syncPlayRooms = "Salons SyncPlay Jellyfin actifs"
        syncPlayNoRooms = "Aucun salon actif. Créez-en un ci-dessus et lancez la lecture !"
        syncPlayParticipants = "%1\$d participant(s) • %2\$s"
        syncPlayJoin = "Rejoindre"

        // App update dialog (0.8)
        updateAvailable = "Lumen %s disponible"
        updateReleaseLink = "Version GitHub : %s"

        // Player (0.8)
        playerSubtitleSyncHud = "Synchro des sous-titres : %s"
        playerSkippedRecapUndo = "Résumé passé • Annuler"
        playerSkippedIntroUndo = "Générique passé • Annuler"
        playerSkipRecap = "Passer le résumé"
        playerSkipIntro = "Passer le générique"
        playerNextEpisode = "Épisode suivant"
        playerPause = "Pause"
        playerBack10 = "Reculer de 10 s"
        playerForward10 = "Avancer de 10 s"
        playerEpisodes = "Épisodes"
        playerAudioSubtitles = "Audio et sous-titres"
        playerPlaybackSettings = "Paramètres de lecture"
        playerVideoStream = "Flux vidéo"
        playerAudioInfo = "Audio : %s"
        playerQualityInfo = "Qualité : %1\$s • %2\$s"
        playerNextIn = "Épisode suivant dans %d s"
        playerUpNext = "À suivre"
        playerPlayNow = "Lire maintenant"
        playerWatchCredits = "Voir le générique"
        playerLive = "DIRECT"
        playerSeekPreview = "Aperçu de la position"
        playerSelectEpisode = "Choisissez un épisode à lire"
        playerNotOnJellyfin = "Indisponible sur Jellyfin"
        playerNowPlaying = "En cours de lecture"
        playerChaptersCount = "Chapitres (%d)"
        playerAudioTracks = "Pistes audio"
        playerSubtitleTrackN = "Sous-titre %d"
        playerAssEngine = "Moteur de sous-titres ASS / SSA"
        playerAssTrackN = "Piste ASS n° %s"
        playerSubtitleSync = "Synchro des sous-titres (%s)"
        playerSubtitleSyncBitmap = "Sous-titres texte uniquement — les pistes image (PGS/VobSub) ne peuvent pas être décalées."
        playerSearchSubtitles = "Rechercher / télécharger des sous-titres"
        playerSearching = "Recherche…"
        playerSpeed = "Vitesse de lecture"
        playerVideoQuality = "Qualité vidéo"
        playerAudioRouting = "Home cinéma et sortie audio"
        playerPassthrough = "Audio passthrough (HDMI / Atmos / DTS)"
        playerDownmix = "Mixage stéréo 2.0 (nuit / barre de son)"
        playerAutomation = "Automatisation et affichage"
        playerSubtitleSize = "Taille des sous-titres"
        playerSubtitleBackground = "Fond des sous-titres"
        playerStreamInfo = "Infos sur le flux"
        qualityAuto = "Auto (lecture directe)"
        assStyled = "Stylisé (ASS)"
        assBurnIn = "Incrusté par le serveur"
        assPlain = "Texte brut"

        // Profiles (0.8)
        profileDone = "Terminé"
        profileManage = "Gérer les profils"
        profileWhoIsWatching = "Qui regarde ?"
        profileSignInPassword = "Se connecter avec un mot de passe"
        profileQuickConnect = "Connexion rapide"
        profileQuickConnectHint = "Ouvrez Jellyfin ou Seerr sur votre téléphone ou ordinateur → Connexion rapide, puis saisissez ce code :"
        profileWaitingApproval = "En attente d'approbation…"
        profileDetectedAccounts = "Comptes Jellyfin détectés :"
        profileUsername = "Nom d'utilisateur Jellyfin"
        profilePassword = "Mot de passe"
        profileSigningIn = "Connexion..."
        profileSignIn = "Se connecter"
        profileUseQuickConnect = "Connexion rapide"
        profileUsePassword = "Mot de passe"
        profileEnterPin = "Saisissez le code PIN de %s"
        profileSettingsTitle = "Paramètres du profil • %s"
        profileTabAppearance = "Apparence et langue"
        profileTabTheater = "Home cinéma"
        profileTabKids = "Enfants et PIN"
        profileUiPreset = "Style d'interface"
        profileAppLanguage = "Langue de l'appli"
        profileMetadataLanguage = "Langue des métadonnées / Seerr"
        profileAutoplayPreviews = "Lecture auto des aperçus pendant la navigation"
        profilePassthrough = "Audio passthrough HDMI (TrueHD / DTS-HD / EAC3)"
        profileDownmix = "Mixage stéréo (haut-parleurs TV / mode nuit)"
        profileAssSubtitles = "Sous-titres anime ASS / SSA"
        profilePreferredLanguage = "Langue audio / sous-titres préférée"
        profileAudioChip = "Audio : %s"
        profileSubtitleChip = "ST : %s"
        profileKidsMode = "Profil enfant (contrôle parental)"
        profileAllowKidsRequests = "Autoriser les demandes Seerr en mode enfant"
        profileMaxRating = "Classification maximale autorisée"
        ratingAll = "Tous âges (sans restriction)"
        ratingTeen = "Ados (PG-13 / TV-14)"
        ratingFamily = "Famille (PG / TV-PG)"
        ratingKids = "Enfants uniquement (G / TV-Y)"
        profilePinLocked = "Code PIN du profil (verrouillé)"
        profileSetPin = "Définir un code PIN à 4 chiffres"
        profilePinNewPlaceholder = "•••• (nouveau code à 4 chiffres)"
        profilePinPlaceholder = "4 chiffres (facultatif)"
        profileRemovePin = "Retirer le PIN"
        profilePinInvalid = "Le code PIN doit comporter exactement 4 chiffres."
        profileConfirmDelete = "Confirmer"
        profileDelete = "Supprimer le profil"
        profileEdit = "Modifier le profil"
        profilePinProtected = "Protégé par PIN"
        profileAdd = "Ajouter un profil"

        // UI presets (0.8)
        presetPickerTitle = "Apparence et style"
        presetPickerSubtitle = "Personnalisez la disposition et l'apparence pour %s"
        presetNetflixDesc = "Barre d'icônes à gauche • Affiches verticales • Chiffres du Top 10 • Accent pourpre"
        presetAppleTvDesc = "Barre en verre flottante • Grande bannière plein écran • Vignettes 16:9"
        presetDisneyDesc = "Barre d'en-tête • Fond bleu nuit • Halo bleu électrique"

        // Requests screen (0.8)
        requestsTitle = "Demandes et téléchargements"
        requestsSubtitle = "Statut en direct synchronisé avec Seerr v3+ • Actualisé toutes les 15 s"
        requestsSubtitleJellyfinOnly = "Mode Jellyfin uniquement • Ajoutez une URL Seerr dans les paramètres du serveur pour activer les demandes"
        requestsSeerrNotConnected = "Seerr non connecté (mode Jellyfin uniquement)"
        requestsSeerrNotConnectedHint = "Ajoutez une URL Seerr dans la configuration du serveur pour parcourir, envoyer, approuver ou suivre les demandes."
        requestsEmpty = "Aucune demande dans cette vue."
        requestsRequestedBy = "Demandé par %1\$s • %2\$s"
        requestsFilterAvailable = "Disponible"

        // Search screen (0.8)
        searchVoicePrompt = "Recherchez des films, séries ou acteurs"
        searchPlaceholderSeerr = "Films, séries ou acteurs (bibliothèque + Seerr)..."
        searchPlaceholderLibrary = "Films et séries de votre bibliothèque Jellyfin..."
        searchVoice = "Recherche vocale"
        searchFilmography = "Filmographie • %s"
        searchClear = "Effacer"
        searchTopSearches = "Recherches populaires"
        searchTypeHint = "Saisissez le nom d'un film, d'une série ou d'un acteur."
        searchNoMatches = "Aucun titre correspondant."
        searchNoResults = "Aucun résultat pour « %s »"
        searchPeople = "Personnes"

        // Setup screen (0.8)
        setupTitle = "Configuration du serveur"
        setupUpdateReady = "Mise à jour %s prête"
        setupIntro = "Ajoutez votre serveur Jellyfin (et, en option, un serveur de demandes Seerr v3+). Hors de chez vous, les URL du réseau local basculent automatiquement vers les URL distantes."
        setupJellyfinUrl = "URL du serveur Jellyfin (principale / distante)"
        setupJellyfinUrlPlaceholder = "https://jellyfin.example.com ou http://192.168.1.100:8096"
        setupJellyfinLocalUrl = "URL locale Jellyfin sur le LAN (facultatif, secours)"
        setupJellyfinOnlyDesc = "Ignorer Seerr/Jellyseerr et parcourir directement votre bibliothèque Jellyfin"
        setupSeerrUrl = "URL Seerr v3+ / Jellyseerr (principale / distante)"
        setupSeerrLocalUrl = "URL locale Seerr sur le LAN (facultatif, secours)"
        setupSeerrApiKey = "Clé API Seerr (facultatif — dans les paramètres Seerr)"
        setupSeerrApiKeyPlaceholder = "Laissez vide pour vous connecter automatiquement avec votre compte Jellyfin"
        setupSelfSigned = "Faire confiance aux certificats HTTPS auto-signés"
        setupSelfSignedDesc = "Utile pour les proxys inverses de homelab avec une autorité de certification locale"
        setupConnect = "Connecter et continuer"
        setupDiscovery = "Détection automatique sur le réseau"
        setupDiscoveryScanning = "Recherche de serveurs Jellyfin via UDP 7359…"
        setupDiscoveryNone = "Aucun serveur détecté pour l'instant. Appuyez sur Rechercher pour analyser votre réseau."
        setupCompanion = "Configuration depuis le téléphone"
        setupCompanionHint = "Pas envie de saisir des URL à la télécommande ? Scannez avec votre téléphone sur le même Wi-Fi pour les coller directement :"
        setupCompanionQr = "QR code de configuration"
        setupCompanionOffline = "Connectez le téléviseur au Wi-Fi ou à l'Ethernet pour le configurer depuis votre téléphone."
        setupScanNetwork = "Analyser le réseau"
        setupScanning = "Recherche…"
        setupScan = "Rechercher"

        // Hero banner & cards (0.8)
        heroFilm = "FILM"
        heroSeries = "SÉRIE"
        heroFeaturedFilm = "FILM À LA UNE"
        heroFeaturedSeries = "SÉRIE À LA UNE"
        heroViewProgress = "Voir la progression"
        heroTogglePreviewAudio = "Activer ou couper le son de l'aperçu"
        cardDownloading = "Téléchargement..."
        cardPartial = "Partiel"
        cardBadgePlay = "LIRE"
        cardBadgeRequest = "+ DEM"
        cardCollection = "COLLECTION"
        cardHoldOk = "Maintenir OK"
        profileMenuSwitchProfile = "Changer de profil"
        homeFilterAllTitles = "Tous les titres"
        cardTimeLeft = "%s restantes"
        librarySortFilter = "Trier et filtrer"
        librarySortBy = "Trier par"
        libraryShow = "Afficher"
        detailsMore = "Plus"
        detailsTabEpisodes = "Épisodes"
        detailsTabCollection = "Collection"
        profileParentPinName = "un profil parent"
        homeNothingForProfileHint = "Rien ici n'est classé pour ce profil. Les titres sans classification sont masqués sur les profils Enfants ou limités."
    }
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
        labelKidsLocked = "Anfragen Gesperrt (Kindermodus)",

        // Settings (0.7)
        navSettings = "Einstellungen",
        settingsTitle = "Einstellungen",
        settingsPlaybackSection = "Wiedergabe",
        settingsMatchCapsTitle = "An dieses TV-Gerät anpassen",
        settingsMatchCapsDesc = "Der Server wandelt alles um, was diese Box, der Fernseher oder der Receiver nicht abspielen kann: HDR auf einem SDR-Bildschirm, DTS ohne Receiver, fehlende Codecs. Nur ausschalten, wenn die Wiedergabe nach der Erkennung fehlerhaft aussieht.",
        settingsDetected = "Erkannt",
        settingsPassthroughTitle = "Audio-Passthrough",
        settingsPassthroughDesc = "Dolby- und DTS-Ton unverändert an einen kompatiblen Receiver oder eine Soundbar senden.",
        settingsDownmixTitle = "Stereo-Downmix",
        settingsDownmixDesc = "Surround-Ton für TV-Lautsprecher oder spätabends auf 2.0 heruntermischen.",
        settingsFrameRateTitle = "Bildrate anpassen",
        settingsAutoSkipIntroTitle = "Intros automatisch überspringen",
        settingsAutoSkipRecapTitle = "Rückblicke automatisch überspringen",
        settingsAutoplayNextTitle = "Nächste Folge automatisch abspielen",
        settingsSubtitlesSection = "Untertitel",
        settingsSubtitleSize = "Textgröße",
        settingsSubtitleColor = "Textfarbe",
        settingsSubtitleBackground = "Hintergrund",
        settingsSubtitleEdge = "Randstil",
        settingsSubtitlePosition = "Position",
        settingsSubtitlePreview = "So sehen die Untertitel aus.",
        settingsRemoteSection = "Fernsteuerung",
        settingsRemoteTitle = "Steuerung durch andere Jellyfin-Apps erlauben",
        settingsRemoteDesc = "Zeigt diesen Fernseher unter „Abspielen auf“ in Jellyfin im Web, auf Handys und Computern an. Diese können dann pausieren, spulen oder Nachrichten senden.",
        settingsConnected = "Mit Server verbunden",
        settingsConnecting = "Verbinden...",
        settingsDisconnected = "Nicht verbunden",
        settingsDiagnosticsSection = "Hilfe & Diagnose",
        settingsSendLogsTitle = "Logs an meinen Jellyfin-Server senden",
        settingsSendLogsDesc = "Speichert einen Bericht (Gerät, Wiedergabeunterstützung, aktuelles App-Log, letzter Absturz) auf deinem eigenen Server unter Dashboard > Logs. Anmelde-Tokens werden entfernt, und nichts wird anderswohin gesendet.",
        settingsSendingLogs = "Wird gesendet...",
        settingsLogsSent = "Auf deinem Server gespeichert als %s",
        settingsLogsDisabled = "Dein Server nimmt keine App-Logs an. Ein Admin kann „Client-Log-Upload erlauben“ unter Dashboard > Allgemein aktivieren.",
        settingsLogsFailed = "Logs konnten nicht gesendet werden: %s",
        settingsCrashPending = "Lumen wurde beim letzten Mal unerwartet beendet; dieser Bericht wird mitgesendet.",
        settingsServerSection = "Server & Konto",
        settingsServerTitle = "Serververbindung",
        settingsServerDesc = "Jellyfin- und Seerr-Adressen, Anmeldung und Nur-Jellyfin-Modus.",
        settingsAboutSection = "Info",
        settingsVersion = "Version %s",
        optionOn = "An",
        optionOff = "Aus",
        frameRateSeamless = "Nur nahtlos",
        frameRateAlways = "Immer",
        sizeSmall = "Klein",
        sizeMedium = "Mittel",
        sizeLarge = "Groß",
        sizeExtraLarge = "Sehr groß",
        backgroundNone = "Kein",
        backgroundLight = "Leicht",
        backgroundMedium = "Mittel",
        backgroundSolid = "Deckend",
        colorWhite = "Weiß",
        colorYellow = "Gelb",
        colorCyan = "Cyan",
        colorGreen = "Grün",
        colorGrey = "Hellgrau",
        edgeOutline = "Kontur",
        edgeDropShadow = "Schlagschatten",
        edgeRaised = "Erhaben",
        edgeNone = "Keiner",
        positionBottom = "Unten",
        positionRaised = "Angehoben",
        positionHigh = "Hoch",
        crashPromptTitle = "Lumen wurde unerwartet beendet",
        crashPromptBody = "Einen Bericht an deinen Jellyfin-Server senden, damit das Problem untersucht werden kann? Er bleibt auf deinem Server.",
        crashPromptSend = "Bericht senden",
        crashPromptDismiss = "Nicht jetzt",

        // Library browsing (0.7)
        libraryBrowseAll = "Alle anzeigen",
        libraryAllMovies = "Alle Filme",
        libraryAllShows = "Alle Serien",
        librarySort = "Sortieren",
        sortName = "A-Z",
        sortDateAdded = "Hinzugefügt",
        sortReleased = "Erscheinungsdatum",
        sortRating = "Bewertung",
        sortRandom = "Zufällig",
        libraryFilterAll = "Alle",
        libraryFilterUnwatched = "Ungesehen",
        libraryFilterWatched = "Gesehen",
        libraryFilterFavorites = "Favoriten",
        libraryAllGenres = "Alle Genres",
        libraryGenre = "Genre",
        libraryTitleCount = "%d Titel",
        libraryEmpty = "Nichts passt zu diesen Filtern.",
        libraryJumpHint = "Zu Buchstabe springen",

        // DVR (0.7)
        dvrRecord = "Aufnehmen",
        dvrRecordSeries = "Serie aufnehmen",
        dvrCancelRecording = "Aufnahme abbrechen",
        dvrCancelSeries = "Serienaufnahme abbrechen",
        dvrScheduled = "Geplante Aufnahmen",
        dvrNoneScheduled = "Keine Aufnahmen geplant",
        dvrRecordingSet = "Aufnahme geplant",
        dvrSeriesSet = "Serienaufnahme geplant",
        dvrCancelled = "Aufnahme abgebrochen",
        dvrFailed = "Aufnahme konnte nicht geändert werden: %s",
        dvrRecordingBadge = "REC",
        dvrWatchLive = "Live ansehen",
        dvrProgramOptions = "Sendungsoptionen"
    ).apply {
        // Common (0.8)
        commonRetry = "Erneut versuchen"
        commonGoBack = "Zurück"
        commonBack = "Zurück"
        commonClose = "Schließen"
        commonRefresh = "Aktualisieren"
        commonSave = "Speichern"
        commonSending = "Wird gesendet..."
        commonActive = "Aktiv"
        commonDefault = "Standard"
        commonMovie = "Film"
        commonSeries = "Serie"
        commonTvSeries = "TV-Serie"
        commonInLibrary = "In der Mediathek"
        commonDownloading = "Wird geladen"
        commonRequested = "Angefragt"
        commonMatchPercent = "%d%% Übereinstimmung"
        commonMinutesShort = "%d Min."

        // Details screen (0.8)
        detailsTitleNotFound = "Titel nicht gefunden"
        detailsSeasonsEpisodes = "Staffeln & Folgen"
        detailsPartOf = "Teil von %s"
        detailsRequestMissing = "Fehlende anfragen (%d)"
        detailsMoreLikeThis = "Ähnliche Titel"
        detailsTrailersMore = "Trailer & mehr"
        detailsCast = "Besetzung"
        detailsPlayTrailer = "Trailer abspielen"
        detailsPlayExtra = "Extra abspielen"
        detailsRequest4kMore = "4K / mehr anfragen"
        detailsRequestHd4k = "HD / 4K anfragen"
        detailsInMyList = "In Meiner Liste"
        detailsReminderSet = "Erinnerung aktiv"
        detailsRemindMe = "Erinnern"
        detailsRemoveProgress = "Fortschritt entfernen"
        detailsCancelRequest = "Anfrage abbrechen"
        detailsTierAvailable = "%s verfügbar"
        detailsTierPartial = "%s teilweise"
        detailsTierDownloading = "%s wird geladen"
        detailsTierRequested = "%s angefragt"
        detailsTierNotInLibrary = "%s nicht in Mediathek"
        detailsNotOnServer = "Nicht auf Server"
        detailsRequestTitle = "„%s“ anfragen"
        detailsRequestIntro = "Qualitätsstufe, Radarr-/Sonarr-Server, Profil und Stammordner wählen:"
        detailsAlreadyInLibrary = "Bereits in der Mediathek"
        detailsAlreadyRequested = "Bereits angefragt"
        detailsStandardProfile = "Standardprofil Radarr / Sonarr"
        details4kProfile = "4K-Profil Radarr / Sonarr"
        detailsLoadingProfiles = "Radarr-/Sonarr-Profile werden geladen..."
        detailsSelectSeasons = "Staffeln wählen (%d ausgewählt):"
        detailsSendRequest = "Anfrage senden"
        detailsServer = "Server"
        detailsQualityProfile = "Qualitätsprofil"
        detailsRootFolder = "Stammordner"
        detailsIssueHint = "Wiedergabe- oder Untertitelproblem beschreiben (optional)"
        detailsReporting = "Wird gemeldet..."
        detailsSubmitIssue = "Problem melden"
        issueVideo = "Videoproblem"
        issueAudio = "Ton asynchron / fehlt"
        issueSubtitle = "Untertitel"
        issueOther = "Sonstiges"

        // Home screen (0.8)
        homeProfileFallback = "Profil"
        homeLoadingHub = "Startseite im %s-Stil wird geladen…"
        homeMyListEmpty = "Deine Liste ist leer"
        homeMyListEmptyHint = "Füge Filme und Serien über das Banner oder die Detailseite hinzu, um sie hier zu finden."
        homeNoTitlesMatch = "Keine Titel für „%s“"
        homeNoTitlesMatchHint = "Wechsle zurück zu Alle oder wähle ein anderes Genre."
        homeNothingYet = "Hier gibt es noch nichts"
        homeNothingYetHint = "Deine Mediatheken haben keine Titel geliefert. Nutze „Aktualisieren“ im Menü für einen neuen Versuch."
        homeAppearance = "Darstellung"
        homeUpdate = "Update"
        homeAppUpdate = "App-Update"
        homeServers = "Server"
        homeFilterWatchNow = "Jetzt ansehen"
        homeFilterRequestable = "Anfragbar"
        homeJellyfinOnlyMode = "Nur-Jellyfin-Modus"
        homeKidsModeActive = "Kindermodus aktiv"
        homeRequestedDownloading = "Angefragt / wird geladen"
        homeAvailableToRequest = "Kann angefragt werden"
        homeRemoveFromMyList = "Aus Meiner Liste entfernen"
        homeAddToMyList = "Zu Meiner Liste hinzufügen"
        homeDetailsCastTrailers = "Details, Besetzung & Trailer"
        homeRemoveFromContinue = "Aus „Weiterschauen“ entfernen"
        homeNoCollections = "Keine Jellyfin-Sammlungen gefunden"
        homeNoCollectionsHint = "Lege in Jellyfin Sammlungen (BoxSets) an, um hier Filmreihen zu durchstöbern und fehlende Fortsetzungen zu ergänzen."
        homeCollectionsSubtitle = "Durchstöbere deine Jellyfin-Sammlungen und finde fehlende Teile jeder Filmreihe."
        homeCollectionEmpty = "Keine Titel in dieser Sammlung."
        homeMissingFromLibrary = "Fehlt in der Mediathek"
        homeDetails = "Details"
        homeJustArrived = "Neu da • %s"
        homeJustArrivedBody = "Deine Seerr-Anfrage ist heruntergeladen und bereit zum Ansehen."
        homeDismiss = "Ausblenden"
        genreAll = "Alle Genres"
        genreAction = "Action"
        genreComedy = "Komödie"
        genreSciFi = "Sci-Fi"
        genreHorrorMystery = "Horror & Mystery"
        genreDrama = "Drama"
        genreCrime = "Krimi"
        genreThriller = "Thriller"
        genreDocumentary = "Doku"
        genreAnimation = "Animation"

        // Live TV guide (0.8)
        liveNoChannels = "Keine Live-TV-Sender gefunden"
        liveNoChannelsHint = "Richte im Jellyfin-Dashboard → Live-TV einen IPTV-/HDHomeRun-Tuner und einen XMLTV-Guide ein."
        liveGuideTitle = "Live-TV & Aufnahmen"
        liveChannelCount = "%d Sender verfügbar • Wähle einen Sender, um live einzuschalten."
        liveStream = "Livestream"
        liveOnNow = "JETZT • %s"
        liveUpNext = "DANACH"
        liveTuneIn = "Einschalten"
        liveDvrRecordings = "Aufnahmen"

        // Customize Home dialog (0.8)
        customizeTitle = "Startseite anpassen • %s"
        customizePinLibraries = "Jellyfin-Mediatheken als Reihen anheften"
        customizeStartupTab = "Start-Tab"
        customizeHideReorder = "Reihen ausblenden / sortieren"
        customizeShow = "Zeigen"
        customizeHide = "Ausblenden"
        customizeUp = "Hoch"
        customizeDown = "Runter"
        customizeSave = "Speichern"

        // SyncPlay dialog (0.8)
        syncPlayTitle = "SyncPlay • Gemeinsam schauen"
        syncPlayDefaultRoom = "Filmabend"
        syncPlayActiveRoom = "Aktiver Raum: %s"
        syncPlayViewers = "%1\$d Zuschauer • %2\$s"
        syncPlayLeave = "Raum verlassen"
        syncPlayNewName = "Name der Watch-Party"
        syncPlayCreate = "Watch-Party erstellen"
        syncPlayRooms = "Aktive Jellyfin-SyncPlay-Räume"
        syncPlayNoRooms = "Noch keine aktiven Räume. Erstelle oben einen und leg los!"
        syncPlayParticipants = "%1\$d Teilnehmer • %2\$s"
        syncPlayJoin = "Beitreten"

        // App update dialog (0.8)
        updateAvailable = "Lumen %s verfügbar"
        updateReleaseLink = "GitHub-Release: %s"

        // Player (0.8)
        playerSubtitleSyncHud = "Untertitel-Sync: %s"
        playerSkippedRecapUndo = "Rückblick übersprungen • Zurück"
        playerSkippedIntroUndo = "Intro übersprungen • Zurück"
        playerSkipRecap = "Rückblick überspringen"
        playerSkipIntro = "Intro überspringen"
        playerNextEpisode = "Nächste Folge"
        playerPause = "Pause"
        playerBack10 = "10 s zurück"
        playerForward10 = "10 s vor"
        playerEpisodes = "Folgen"
        playerAudioSubtitles = "Audio & Untertitel"
        playerPlaybackSettings = "Wiedergabe"
        playerVideoStream = "Videostream"
        playerQualityInfo = "Qualität: %1\$s • %2\$s"
        playerNextIn = "Nächste Folge in %d s"
        playerUpNext = "Als Nächstes"
        playerPlayNow = "Jetzt abspielen"
        playerWatchCredits = "Abspann ansehen"
        playerLive = "LIVE"
        playerSeekPreview = "Vorschau"
        playerSelectEpisode = "Wähle eine Folge zum Abspielen"
        playerNotOnJellyfin = "Nicht auf Jellyfin verfügbar"
        playerNowPlaying = "Läuft gerade"
        playerChaptersCount = "Kapitel (%d)"
        playerAudioTracks = "Audiospuren"
        playerSubtitleTrackN = "Untertitel %d"
        playerAssEngine = "ASS-/SSA-Untertitel"
        playerAssTrackN = "ASS-Spur %s"
        playerSubtitleSync = "Untertitel-Sync (%s)"
        playerSubtitleSyncBitmap = "Nur Text-Untertitel – Bildspuren (PGS/VobSub) lassen sich nicht verschieben."
        playerSearchSubtitles = "Untertitel suchen / laden"
        playerSearching = "Suche…"
        playerSpeed = "Geschwindigkeit"
        playerVideoQuality = "Videoqualität"
        playerAudioRouting = "Heimkino & Audioausgabe"
        playerPassthrough = "Audio-Passthrough (HDMI / Atmos / DTS)"
        playerDownmix = "Stereo-2.0-Downmix (Nacht / Soundbar)"
        playerAutomation = "Automatik & Anzeige"
        playerSubtitleSize = "Untertitelgröße"
        playerSubtitleBackground = "Untertitelhintergrund"
        playerStreamInfo = "Stream-Infos einblenden"
        qualityAuto = "Auto (Direktwiedergabe)"
        assStyled = "Formatiert (ASS)"
        assBurnIn = "Server-Einbrennen"
        assPlain = "Nur Text"

        // Profiles (0.8)
        profileDone = "Fertig"
        profileManage = "Profile verwalten"
        profileWhoIsWatching = "Wer schaut gerade?"
        profileSignInPassword = "Mit Passwort anmelden"
        profileQuickConnect = "Quick Connect"
        profileQuickConnectHint = "Öffne Jellyfin oder Seerr auf Handy oder Computer → Quick Connect und gib diesen Code ein:"
        profileWaitingApproval = "Warte auf Bestätigung…"
        profileDetectedAccounts = "Gefundene Jellyfin-Konten:"
        profileUsername = "Jellyfin-Benutzername"
        profilePassword = "Passwort"
        profileSigningIn = "Anmeldung..."
        profileSignIn = "Anmelden"
        profileUseQuickConnect = "Quick Connect nutzen"
        profileUsePassword = "Passwort nutzen"
        profileEnterPin = "PIN für %s eingeben"
        profileSettingsTitle = "Profileinstellungen • %s"
        profileTabAppearance = "Darstellung & Sprache"
        profileTabTheater = "Heimkino"
        profileTabKids = "Kinder & PIN"
        profileUiPreset = "UI-Stil"
        profileAppLanguage = "App-Sprache"
        profileMetadataLanguage = "Sprache für Metadaten / Seerr"
        profileAutoplayPreviews = "Vorschauen beim Stöbern automatisch abspielen"
        profilePassthrough = "HDMI-Audio-Passthrough (TrueHD / DTS-HD / EAC3)"
        profileDownmix = "Stereo-Downmix (TV-Lautsprecher / Nachtmodus)"
        profileAssSubtitles = "ASS-/SSA-Anime-Untertitel"
        profilePreferredLanguage = "Bevorzugte Audio-/Untertitelsprache"
        profileAudioChip = "Ton: %s"
        profileSubtitleChip = "UT: %s"
        profileKidsMode = "Kinderprofil (Jugendschutz)"
        profileAllowKidsRequests = "Seerr-Anfragen im Kindermodus erlauben"
        profileMaxRating = "Höchste erlaubte Altersfreigabe"
        ratingAll = "Alle Altersstufen (ohne Einschränkung)"
        ratingTeen = "Jugendliche (PG-13 / TV-14)"
        ratingFamily = "Familie (PG / TV-PG)"
        ratingKids = "Nur Kinder (G / TV-Y)"
        profilePinLocked = "Profil-PIN (gesperrt)"
        profileSetPin = "4-stellige Profil-PIN festlegen"
        profilePinNewPlaceholder = "•••• (neue 4 Ziffern)"
        profilePinPlaceholder = "4 Ziffern (optional)"
        profileRemovePin = "PIN entfernen"
        profilePinInvalid = "Die PIN muss genau 4 Ziffern haben."
        profileConfirmDelete = "Löschen bestätigen"
        profileDelete = "Profil löschen"
        profileEdit = "Profil bearbeiten"
        profilePinProtected = "PIN-geschützt"
        profileAdd = "Profil hinzufügen"

        // UI presets (0.8)
        presetPickerTitle = "Darstellung & UI-Stil"
        presetPickerSubtitle = "Layout und Look für %s anpassen"
        presetNetflixDesc = "Icon-Leiste links • Hochformat-Poster • Top-10-Ziffern • Karminroter Akzent"
        presetAppleTvDesc = "Schwebende Glasleiste • Hohes randloses Titelbild • 16:9-Karten"
        presetDisneyDesc = "Kopfleiste oben • Mitternachtsblauer Hintergrund • Elektrisch blaues Leuchten"

        // Requests screen (0.8)
        requestsTitle = "Anfragen & Downloads"
        requestsSubtitle = "Live-Status von Seerr v3+ • Aktualisiert alle 15 s"
        requestsSubtitleJellyfinOnly = "Nur-Jellyfin-Modus • Füge in den Servereinstellungen eine Seerr-URL hinzu, um Anfragen zu aktivieren"
        requestsSeerrNotConnected = "Seerr nicht verbunden (Nur-Jellyfin-Modus)"
        requestsSeerrNotConnectedHint = "Füge im Server-Setup eine Seerr-URL hinzu, um Anfragen zu durchsuchen, zu senden, zu genehmigen oder zu verfolgen."
        requestsEmpty = "Hier gibt es noch keine Anfragen."
        requestsRequestedBy = "Angefragt von %1\$s • %2\$s"
        requestsFilterAvailable = "Verfügbar"

        // Search screen (0.8)
        searchVoicePrompt = "Filme, Serien oder Schauspieler suchen"
        searchPlaceholderSeerr = "Filme, Serien oder Besetzung (Mediathek + Seerr)..."
        searchPlaceholderLibrary = "Filme & Serien in deiner Jellyfin-Mediathek suchen..."
        searchVoice = "Sprachsuche"
        searchFilmography = "Filmografie • %s"
        searchClear = "Löschen"
        searchTopSearches = "Top-Suchanfragen"
        searchTypeHint = "Gib einen Film, eine Serie oder einen Schauspieler ein."
        searchNoMatches = "Keine passenden Titel gefunden."
        searchNoResults = "Keine Ergebnisse für „%s“"
        searchPeople = "Personen"

        // Setup screen (0.8)
        setupTitle = "Server einrichten"
        setupUpdateReady = "Update %s bereit"
        setupIntro = "Füge deinen Jellyfin-Server hinzu (optional einen Seerr-v3+-Anfrageserver). Unterwegs wechseln lokale LAN-URLs automatisch zu den Remote-URLs."
        setupJellyfinUrl = "Jellyfin-Server-URL (primär / remote)"
        setupJellyfinUrlPlaceholder = "https://jellyfin.example.com oder http://192.168.1.100:8096"
        setupJellyfinLocalUrl = "Lokale Jellyfin-LAN-URL (optional, Ausfallsicherung)"
        setupJellyfinOnlyDesc = "Seerr/Jellyseerr überspringen und direkt die Jellyfin-Mediathek durchsuchen"
        setupSeerrUrl = "Seerr-v3+-/Jellyseerr-URL (primär / remote)"
        setupSeerrLocalUrl = "Lokale Seerr-LAN-URL (optional, Ausfallsicherung)"
        setupSeerrApiKey = "Seerr-API-Schlüssel (optional – aus den Seerr-Einstellungen)"
        setupSeerrApiKeyPlaceholder = "Leer lassen, um dich automatisch mit deinem Jellyfin-Konto anzumelden"
        setupSelfSigned = "Selbstsignierten HTTPS-Zertifikaten vertrauen"
        setupSelfSignedDesc = "Nützlich für Homelab-Reverse-Proxys mit lokaler CA"
        setupConnect = "Verbinden & weiter"
        setupDiscovery = "Automatische LAN-Suche"
        setupDiscoveryScanning = "Suche nach Jellyfin-Servern über UDP 7359…"
        setupDiscoveryNone = "Noch keine Server gefunden. Drücke „Suchen“, um dein LAN zu durchsuchen."
        setupCompanion = "Einrichtung per Handy"
        setupCompanionHint = "Keine Lust, URLs per Fernbedienung einzutippen? Scanne mit dem Handy im selben WLAN und füge sie direkt ein:"
        setupCompanionQr = "QR-Code zur Einrichtung"
        setupCompanionOffline = "Verbinde den Fernseher per WLAN / Ethernet, um die Einrichtung per Handy zu starten."
        setupScanNetwork = "Netzwerk durchsuchen"
        setupScanning = "Suche…"
        setupScan = "Suchen"

        // Hero banner & cards (0.8)
        heroFilm = "FILM"
        heroSeries = "SERIE"
        heroFeaturedFilm = "FILM-TIPP"
        heroFeaturedSeries = "SERIEN-TIPP"
        heroViewProgress = "Fortschritt"
        heroTogglePreviewAudio = "Vorschauton ein/aus"
        cardDownloading = "Wird geladen..."
        cardPartial = "Teilweise"
        cardBadgeRequest = "+ ANF"
        cardCollection = "SAMMLUNG"
        cardHoldOk = "OK halten"
        profileMenuSwitchProfile = "Profil wechseln"
        homeFilterAllTitles = "Alle Titel"
        cardTimeLeft = "noch %s"
        librarySortFilter = "Sortieren & Filtern"
        librarySortBy = "Sortieren nach"
        libraryShow = "Anzeigen"
        detailsMore = "Mehr"
        detailsTabEpisodes = "Episoden"
        detailsTabCollection = "Sammlung"
        profileParentPinName = "ein Elternprofil"
        homeNothingForProfileHint = "Hier ist nichts für dieses Profil freigegeben. Titel ohne Altersfreigabe werden in Kinder- und eingeschränkten Profilen ausgeblendet."
    }
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
        labelKidsLocked = "リクエスト制限中 (キッズモード)",

        // Settings (0.7)
        navSettings = "設定",
        settingsTitle = "設定",
        settingsPlaybackSection = "再生",
        settingsMatchCapsTitle = "このテレビの性能に合わせる",
        settingsMatchCapsDesc = "このデバイス・テレビ・AVアンプで再生できないもの（SDR画面でのHDR、アンプなしのDTS、未対応のコーデックなど）はサーバーが変換します。検出後に再生がおかしい場合のみオフにしてください。",
        settingsDetected = "検出結果",
        settingsPassthroughTitle = "オーディオパススルー",
        settingsPassthroughDesc = "Dolby・DTS音声を対応するAVアンプやサウンドバーへそのまま出力します。",
        settingsDownmixTitle = "ステレオダウンミックス",
        settingsDownmixDesc = "テレビのスピーカーや深夜の視聴向けに、サラウンドを2.0chにまとめます。",
        settingsFrameRateTitle = "フレームレートを合わせる",
        settingsAutoSkipIntroTitle = "オープニングを自動スキップ",
        settingsAutoSkipRecapTitle = "あらすじを自動スキップ",
        settingsAutoplayNextTitle = "次のエピソードを自動再生",
        settingsSubtitlesSection = "字幕",
        settingsSubtitleSize = "文字サイズ",
        settingsSubtitleColor = "文字色",
        settingsSubtitleBackground = "背景",
        settingsSubtitleEdge = "縁取り",
        settingsSubtitlePosition = "位置",
        settingsSubtitlePreview = "字幕はこのように表示されます。",
        settingsRemoteSection = "リモート操作",
        settingsRemoteTitle = "他のJellyfinアプリからの操作を許可",
        settingsRemoteDesc = "Web・スマートフォン・パソコンのJellyfinで「再生先」にこのテレビを表示し、一時停止・シーク・メッセージ送信をできるようにします。",
        settingsConnected = "サーバーに接続済み",
        settingsConnecting = "接続中...",
        settingsDisconnected = "未接続",
        settingsDiagnosticsSection = "ヘルプと診断",
        settingsSendLogsTitle = "ログをJellyfinサーバーに送信",
        settingsSendLogsDesc = "レポート（デバイス、再生対応状況、最近のアプリログ、直近のクラッシュ）をご自身のサーバーの「ダッシュボード > ログ」に保存します。サインイン用トークンは削除され、他の場所には送信されません。",
        settingsSendingLogs = "送信中...",
        settingsLogsSent = "サーバーに %s として保存しました",
        settingsLogsDisabled = "サーバーがアプリのログを受け付けていません。管理者が「ダッシュボード > 全般」で「クライアントログのアップロードを許可」をオンにできます。",
        settingsLogsFailed = "ログを送信できませんでした: %s",
        settingsCrashPending = "前回Lumenが予期せず終了しました。そのレポートも含まれます。",
        settingsServerSection = "サーバーとアカウント",
        settingsServerTitle = "サーバー接続",
        settingsServerDesc = "JellyfinとSeerrのアドレス、サインイン、Jellyfinのみモード。",
        settingsAboutSection = "このアプリについて",
        settingsVersion = "バージョン %s",
        optionOn = "オン",
        optionOff = "オフ",
        frameRateSeamless = "シームレスのみ",
        frameRateAlways = "常に",
        sizeSmall = "小",
        sizeMedium = "中",
        sizeLarge = "大",
        sizeExtraLarge = "特大",
        backgroundNone = "なし",
        backgroundLight = "薄め",
        backgroundMedium = "普通",
        backgroundSolid = "不透明",
        colorWhite = "白",
        colorYellow = "黄",
        colorCyan = "シアン",
        colorGreen = "緑",
        colorGrey = "ライトグレー",
        edgeOutline = "縁取り",
        edgeDropShadow = "ドロップシャドウ",
        edgeRaised = "浮き出し",
        edgeNone = "なし",
        positionBottom = "下",
        positionRaised = "やや上",
        positionHigh = "上",
        crashPromptTitle = "Lumenが予期せず終了しました",
        crashPromptBody = "問題を調べられるよう、Jellyfinサーバーにレポートを送信しますか？レポートはご自身のサーバーにのみ保存されます。",
        crashPromptSend = "レポートを送信",
        crashPromptDismiss = "今はしない",

        // Library browsing (0.7)
        libraryBrowseAll = "すべて見る",
        libraryAllMovies = "すべての映画",
        libraryAllShows = "すべてのTV番組",
        librarySort = "並べ替え",
        sortName = "名前順",
        sortDateAdded = "追加日",
        sortReleased = "公開日",
        sortRating = "評価",
        sortRandom = "ランダム",
        libraryFilterAll = "すべて",
        libraryFilterUnwatched = "未視聴",
        libraryFilterWatched = "視聴済み",
        libraryFilterFavorites = "お気に入り",
        libraryAllGenres = "すべてのジャンル",
        libraryGenre = "ジャンル",
        libraryTitleCount = "%d 作品",
        libraryEmpty = "条件に一致する作品はありません。",
        libraryJumpHint = "頭文字へ移動",

        // DVR (0.7)
        dvrRecord = "録画",
        dvrRecordSeries = "シリーズ録画",
        dvrCancelRecording = "録画をキャンセル",
        dvrCancelSeries = "シリーズ録画をキャンセル",
        dvrScheduled = "録画予約",
        dvrNoneScheduled = "録画予約はありません",
        dvrRecordingSet = "録画を予約しました",
        dvrSeriesSet = "シリーズ録画を予約しました",
        dvrCancelled = "録画をキャンセルしました",
        dvrFailed = "録画を変更できませんでした: %s",
        dvrRecordingBadge = "録画",
        dvrWatchLive = "ライブで視聴",
        dvrProgramOptions = "番組オプション"
    ).apply {
        // Common (0.8)
        commonRetry = "再試行"
        commonGoBack = "戻る"
        commonBack = "戻る"
        commonClose = "閉じる"
        commonRefresh = "更新"
        commonSave = "保存"
        commonSending = "送信中..."
        commonActive = "使用中"
        commonDefault = "既定"
        commonMovie = "映画"
        commonSeries = "シリーズ"
        commonTvSeries = "TVシリーズ"
        commonInLibrary = "ライブラリにあり"
        commonDownloading = "ダウンロード中"
        commonRequested = "リクエスト済み"
        commonMatchPercent = "マッチ度%d%%"
        commonMinutesShort = "%d分"

        // Details screen (0.8)
        detailsTitleNotFound = "作品が見つかりません"
        detailsSeasonsEpisodes = "シーズンとエピソード"
        detailsPartOf = "%s の一部"
        detailsRequestMissing = "未所持をリクエスト (%d)"
        detailsMoreLikeThis = "似た作品"
        detailsTrailersMore = "予告編など"
        detailsCast = "キャスト"
        detailsPlayTrailer = "予告編を再生"
        detailsPlayExtra = "特典映像を再生"
        detailsRequest4kMore = "4K・追加をリクエスト"
        detailsRequestHd4k = "HD・4Kをリクエスト"
        detailsInMyList = "マイリスト登録済み"
        detailsReminderSet = "リマインダー設定済み"
        detailsRemindMe = "リマインド"
        detailsRemoveProgress = "視聴位置を削除"
        detailsCancelRequest = "リクエストを取り消す"
        detailsTierAvailable = "%s 視聴可能"
        detailsTierPartial = "%s 一部のみ"
        detailsTierDownloading = "%s ダウンロード中"
        detailsTierRequested = "%s リクエスト済み"
        detailsTierNotInLibrary = "%s ライブラリになし"
        detailsNotOnServer = "サーバーになし"
        detailsRequestTitle = "「%s」をリクエスト"
        detailsRequestIntro = "画質、Radarr/Sonarrサーバー、プロファイル、ルートフォルダーを選択:"
        detailsAlreadyInLibrary = "ライブラリに追加済み"
        detailsAlreadyRequested = "リクエスト済み"
        detailsStandardProfile = "標準のRadarr / Sonarrプロファイル"
        details4kProfile = "4K用Radarr / Sonarrプロファイル"
        detailsLoadingProfiles = "Radarr / Sonarrのプロファイルを読み込み中..."
        detailsSelectSeasons = "シーズンを選択（%d件選択中）:"
        detailsSendRequest = "リクエストを送信"
        detailsServer = "サーバー"
        detailsQualityProfile = "画質プロファイル"
        detailsRootFolder = "ルートフォルダー"
        detailsIssueHint = "再生や字幕の問題を入力（任意）"
        detailsReporting = "報告中..."
        detailsSubmitIssue = "問題を送信"
        issueVideo = "映像の問題"
        issueAudio = "音ズレ・音声なし"
        issueSubtitle = "字幕"
        issueOther = "その他"

        // Home screen (0.8)
        homeProfileFallback = "プロフィール"
        homeLoadingHub = "%s スタイルのホームを読み込み中…"
        homeMyListEmpty = "マイリストは空です"
        homeMyListEmptyHint = "トップのバナーや詳細画面から映画・番組を追加すると、ここに表示されます。"
        homeNoTitlesMatch = "「%s」に一致する作品はありません"
        homeNoTitlesMatchHint = "「すべて」に戻すか、別のジャンルを選んでください。"
        homeNothingYet = "まだ表示する作品がありません"
        homeNothingYetHint = "ライブラリから作品を取得できませんでした。メニューの「更新」で再試行してください。"
        homeAppearance = "外観"
        homeUpdate = "アップデート"
        homeAppUpdate = "アプリを更新"
        homeServers = "サーバー"
        homeFilterWatchNow = "今すぐ視聴"
        homeFilterRequestable = "リクエスト可能"
        homeJellyfinOnlyMode = "Jellyfinのみモード"
        homeKidsModeActive = "キッズモード中"
        homeRequestedDownloading = "リクエスト済み・ダウンロード中"
        homeAvailableToRequest = "リクエスト可能"
        homeRemoveFromMyList = "マイリストから削除"
        homeAddToMyList = "マイリストに追加"
        homeDetailsCastTrailers = "詳細・キャスト・予告編"
        homeRemoveFromContinue = "「続きを観る」から削除"
        homeNoCollections = "Jellyfinのコレクションが見つかりません"
        homeNoCollectionsHint = "Jellyfinでコレクション（BoxSet）を作成すると、ここでシリーズ作品を閲覧し、未所持の続編を追加できます。"
        homeCollectionsSubtitle = "Jellyfinのコレクションを閲覧し、各シリーズの未所持作品を見つけましょう。"
        homeCollectionEmpty = "このコレクションに作品はありません。"
        homeMissingFromLibrary = "ライブラリに未追加"
        homeDetails = "詳細"
        homeJustArrived = "新着 • %s"
        homeJustArrivedBody = "Seerrでリクエストした作品のダウンロードが完了し、視聴できるようになりました。"
        homeDismiss = "閉じる"
        genreAll = "すべてのジャンル"
        genreAction = "アクション"
        genreComedy = "コメディ"
        genreSciFi = "SF"
        genreHorrorMystery = "ホラー・ミステリー"
        genreDrama = "ドラマ"
        genreCrime = "犯罪"
        genreThriller = "スリラー"
        genreDocumentary = "ドキュメンタリー"
        genreAnimation = "アニメーション"

        // Live TV guide (0.8)
        liveNoChannels = "ライブTVのチャンネルが見つかりません"
        liveNoChannelsHint = "Jellyfinのダッシュボード → ライブTVで、IPTV / HDHomeRunチューナーとXMLTV番組表を設定してください。"
        liveGuideTitle = "ライブTV・録画ガイド"
        liveChannelCount = "%d チャンネル視聴可能 • チャンネルを選ぶとライブで視聴できます。"
        liveStream = "ライブ配信"
        liveOnNow = "放送中 • %s"
        liveUpNext = "次の番組"
        liveTuneIn = "視聴する"
        liveDvrRecordings = "録画番組"

        // Customize Home dialog (0.8)
        customizeTitle = "ホームのカスタマイズ • %s"
        customizePinLibraries = "Jellyfinライブラリをホームの列に固定"
        customizeStartupTab = "起動時のタブ"
        customizeHideReorder = "ホームの列を非表示・並べ替え"
        customizeShow = "表示"
        customizeHide = "非表示"
        customizeUp = "上へ"
        customizeDown = "下へ"
        customizeSave = "レイアウトを保存"

        // SyncPlay dialog (0.8)
        syncPlayTitle = "SyncPlay • 一緒に視聴"
        syncPlayDefaultRoom = "映画ナイト"
        syncPlayActiveRoom = "参加中のルーム: %s"
        syncPlayViewers = "視聴者 %1\$d 人 • %2\$s"
        syncPlayLeave = "ルームを退出"
        syncPlayNewName = "新しいウォッチパーティー名"
        syncPlayCreate = "ウォッチパーティーを作成"
        syncPlayRooms = "Jellyfinの開催中SyncPlayルーム"
        syncPlayNoRooms = "開催中のルームはまだありません。上で作成して再生を始めましょう！"
        syncPlayParticipants = "参加者 %1\$d 人 • %2\$s"
        syncPlayJoin = "参加"

        // App update dialog (0.8)
        updateAvailable = "Lumen %s が利用可能です"
        updateReleaseLink = "GitHubリリース: %s"

        // Player (0.8)
        playerSubtitleSyncHud = "字幕のタイミング: %s"
        playerSkippedRecapUndo = "あらすじをスキップ • 元に戻す"
        playerSkippedIntroUndo = "オープニングをスキップ • 元に戻す"
        playerSkipRecap = "あらすじをスキップ"
        playerSkipIntro = "オープニングをスキップ"
        playerNextEpisode = "次のエピソード"
        playerPause = "一時停止"
        playerBack10 = "10秒戻す"
        playerForward10 = "10秒進む"
        playerEpisodes = "エピソード"
        playerAudioSubtitles = "音声と字幕"
        playerPlaybackSettings = "再生設定"
        playerVideoStream = "映像ストリーム"
        playerAudioInfo = "音声: %s"
        playerQualityInfo = "画質: %1\$s • %2\$s"
        playerNextIn = "%d秒後に次のエピソード"
        playerUpNext = "次に再生"
        playerPlayNow = "今すぐ再生"
        playerWatchCredits = "エンドロールを見る"
        playerLive = "ライブ"
        playerSeekPreview = "シークプレビュー"
        playerSelectEpisode = "再生するエピソードを選択"
        playerNotOnJellyfin = "Jellyfinにありません"
        playerNowPlaying = "再生中"
        playerChaptersCount = "チャプター (%d)"
        playerAudioTracks = "音声トラック"
        playerAudioTrackN = "音声 %d"
        playerSubtitleTrackN = "字幕 %d"
        playerAssEngine = "ASS / SSA字幕エンジン"
        playerAssTrackN = "ASSトラック #%s"
        playerSubtitleSync = "字幕のタイミング (%s)"
        playerSubtitleSyncBitmap = "テキスト字幕のみ対応 — 画像字幕（PGS/VobSub）はずらせません。"
        playerSearchSubtitles = "字幕を検索・ダウンロード"
        playerSearching = "検索中…"
        playerSpeed = "再生速度"
        playerVideoQuality = "画質"
        playerAudioRouting = "ホームシアターと音声出力"
        playerPassthrough = "オーディオパススルー (HDMI / Atmos / DTS)"
        playerDownmix = "ステレオ2.0ダウンミックス (夜間 / サウンドバー)"
        playerAutomation = "自動化と表示"
        playerSubtitleSize = "字幕サイズ"
        playerSubtitleBackground = "字幕の背景"
        playerStreamInfo = "ストリーム情報を表示"
        qualityAuto = "自動 (ダイレクト再生)"
        assStyled = "スタイル付き (ASS)"
        assBurnIn = "サーバーで焼き込み"
        assPlain = "プレーンテキスト"

        // Profiles (0.8)
        profileDone = "完了"
        profileManage = "プロフィールの管理"
        profileWhoIsWatching = "視聴するのは誰ですか？"
        profileSignInPassword = "パスワードでサインイン"
        profileQuickConnect = "クイックコネクト"
        profileQuickConnectHint = "スマートフォンかパソコンでJellyfinまたはSeerrを開き、クイックコネクトでこのコードを入力してください:"
        profileWaitingApproval = "承認を待っています…"
        profileDetectedAccounts = "検出されたJellyfinアカウント:"
        profileUsername = "Jellyfinのユーザー名"
        profilePassword = "パスワード"
        profileSigningIn = "サインイン中..."
        profileSignIn = "サインイン"
        profileUseQuickConnect = "クイックコネクトを使う"
        profileUsePassword = "パスワードを使う"
        profileEnterPin = "%s のPINを入力"
        profileSettingsTitle = "プロフィール設定 • %s"
        profileTabAppearance = "外観と言語"
        profileTabTheater = "ホームシアター"
        profileTabKids = "キッズとPIN"
        profileUiPreset = "UIプリセット"
        profileAppLanguage = "アプリの表示言語"
        profileMetadataLanguage = "メタデータ / Seerrの言語"
        profileAutoplayPreviews = "閲覧中にプレビューを自動再生"
        profilePassthrough = "HDMIオーディオパススルー (TrueHD / DTS-HD / EAC3)"
        profileDownmix = "ステレオダウンミックス (テレビのスピーカー / ナイトモード)"
        profileAssSubtitles = "ASS / SSAアニメ字幕"
        profilePreferredLanguage = "優先する音声・字幕の言語"
        profileAudioChip = "音声: %s"
        profileSubtitleChip = "字幕: %s"
        profileKidsMode = "キッズプロフィール (ペアレンタルフィルター)"
        profileAllowKidsRequests = "キッズモードでSeerrのリクエストを許可"
        profileMaxRating = "許可する最大レーティング"
        ratingAll = "全年齢 (制限なし)"
        ratingTeen = "ティーン (PG-13 / TV-14)"
        ratingFamily = "ファミリー (PG / TV-PG)"
        ratingKids = "子ども向けのみ (G / TV-Y)"
        profilePinLocked = "プロフィールPIN (ロック中)"
        profileSetPin = "4桁のプロフィールPINを設定"
        profilePinNewPlaceholder = "•••• (新しい4桁を入力)"
        profilePinPlaceholder = "4桁 (任意)"
        profileRemovePin = "PINを削除"
        profilePinInvalid = "PINは4桁の数字で入力してください。"
        profileConfirmDelete = "削除を確定"
        profileDelete = "プロフィールを削除"
        profileEdit = "プロフィールを編集"
        profilePinProtected = "PINで保護"
        profileAdd = "プロフィールを追加"

        // UI presets (0.8)
        presetPickerTitle = "外観とUIプリセット"
        presetPickerSubtitle = "%s のレイアウトと外観をカスタマイズ"
        presetNetflixDesc = "左のアイコンレール • 縦長ポスター • トップ10の数字 • クリムゾンのアクセント"
        presetAppleTvDesc = "フローティングのガラスバー • 全面表示の大きなヒーロー • 16:9ワイドカード"
        presetDisneyDesc = "上部ヘッダーバー • ミッドナイトネイビーの背景 • エレクトリックブルーの光"

        // Requests screen (0.8)
        requestsTitle = "リクエストとダウンロード"
        requestsSubtitle = "Seerr v3+と同期したライブ状況 • 15秒ごとに自動更新"
        requestsSubtitleJellyfinOnly = "Jellyfinのみモード • リクエストを使うにはサーバー設定でSeerrのURLを追加してください"
        requestsSeerrNotConnected = "Seerr未接続 (Jellyfinのみモード)"
        requestsSeerrNotConnectedHint = "サーバー設定でSeerrのURLを追加すると、リクエストの閲覧・送信・承認・追跡ができます。"
        requestsEmpty = "ここにはまだリクエストがありません。"
        requestsRequestedBy = "%1\$s がリクエスト • %2\$s"
        requestsFilterAvailable = "視聴可能"

        // Search screen (0.8)
        searchVoicePrompt = "映画、TV番組、俳優を検索"
        searchPlaceholderSeerr = "映画、TV番組、キャストを検索 (ライブラリ + Seerr)..."
        searchPlaceholderLibrary = "Jellyfinライブラリの映画・TV番組を検索..."
        searchVoice = "音声検索"
        searchFilmography = "出演作品 • %s"
        searchGenreHeader = "ジャンル • %s"
        searchClear = "クリア"
        searchTopSearches = "人気の検索"
        searchTypeHint = "映画、TV番組、俳優の名前を入力して検索します。"
        searchNoMatches = "一致する作品が見つかりません。"
        searchNoResults = "「%s」の検索結果はありません"
        searchPeople = "人物"

        // Setup screen (0.8)
        setupTitle = "サーバーの設定"
        setupUpdateReady = "アップデート %s の準備完了"
        setupIntro = "Jellyfinサーバー（任意でSeerr v3+のリクエストサーバー）を追加します。外出先ではローカルLANのURLからリモートURLに自動で切り替わります。"
        setupJellyfinUrl = "JellyfinサーバーのURL (メイン / リモート)"
        setupJellyfinUrlPlaceholder = "https://jellyfin.example.com または http://192.168.1.100:8096"
        setupJellyfinLocalUrl = "JellyfinのローカルLAN URL (任意・フェイルオーバー用)"
        setupJellyfinOnlyDesc = "Seerr/Jellyseerrを使わず、Jellyfinライブラリを直接閲覧します"
        setupSeerrUrl = "Seerr v3+ / JellyseerrのURL (メイン / リモート)"
        setupSeerrLocalUrl = "SeerrのローカルLAN URL (任意・フェイルオーバー用)"
        setupSeerrApiKey = "SeerrのAPIキー (任意 — Seerrの設定から取得)"
        setupSeerrApiKeyPlaceholder = "空欄にするとJellyfinアカウントで自動サインインします"
        setupSelfSigned = "自己署名のHTTPS証明書を信頼"
        setupSelfSignedDesc = "ローカルCA証明書を使う自宅サーバーのリバースプロキシ向け"
        setupConnect = "接続して続行"
        setupDiscovery = "LAN自動検出"
        setupDiscoveryScanning = "UDP 7359でJellyfinサーバーを検索中…"
        setupDiscoveryNone = "まだサーバーが見つかっていません。「スキャン」を押してLANを検索してください。"
        setupCompanion = "スマートフォンで設定"
        setupCompanionHint = "リモコンでURLを入力するのは面倒？同じWi-Fiのスマートフォンでスキャンすれば、URLを直接貼り付けられます:"
        setupCompanionQr = "設定用QRコード"
        setupCompanionOffline = "スマートフォンからの設定を始めるには、テレビをWi-Fiまたは有線LANに接続してください。"
        setupScanNetwork = "ネットワークをスキャン"
        setupScanning = "スキャン中…"
        setupScan = "スキャン"

        // Hero banner & cards (0.8)
        heroFilm = "映画"
        heroSeries = "シリーズ"
        heroFeaturedFilm = "注目の映画"
        heroFeaturedSeries = "注目のシリーズ"
        heroViewProgress = "進捗を見る"
        heroTogglePreviewAudio = "プレビュー音声のオン/オフ"
        cardDownloading = "ダウンロード中..."
        cardPartial = "一部のみ"
        cardBadgePlay = "再生"
        cardBadgeRequest = "+ リクエスト"
        cardCollection = "コレクション"
        cardTopRank = "トップ%d"
        cardHoldOk = "OK長押し"
        profileMenuSwitchProfile = "プロフィールを切り替え"
        homeFilterAllTitles = "すべての作品"
        cardTimeLeft = "残り%s"
        librarySortFilter = "並べ替えと絞り込み"
        librarySortBy = "並べ替え"
        libraryShow = "表示"
        detailsMore = "その他"
        detailsTabEpisodes = "エピソード"
        detailsTabCollection = "コレクション"
        profileParentPinName = "保護者のプロフィール"
        homeNothingForProfileHint = "このプロフィールで視聴できる作品はありません。年齢区分のない作品はキッズ・制限付きプロフィールでは表示されません。"
    }
    else -> UiStrings()
}

val LocalStrings = staticCompositionLocalOf { UiStrings() }

object AppStrings {
    val current: UiStrings
        @Composable
        @ReadOnlyComposable
        get() = LocalStrings.current
}
