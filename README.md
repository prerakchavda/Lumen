# Lumen — Customizable Google TV Client for Jellyfin + Seerr v3+

A native **Google TV / Android TV** app built with **Kotlin**, **Jetpack Compose for TV**, and **Jetpack Media3** that unifies your **Jellyfin** library and **Seerr v3+** (or Jellyseerr) into a living room streaming experience with **per-user UI Presets** (Netflix, Apple TV+, and Disney+ / Max) — with no extra middleware server required.

## Screenshots

### Per-User UI Presets

#### Netflix Preset (Default) — Collapsible Rail & Top 10 Numerals
![Home Screen (Netflix Preset)](screenshots/home.png)

#### Apple TV+ Preset — Floating Glass Pill Bar & 16:9 Widescreen Cards
![Apple TV+ Preset](screenshots/home-apple-tv.png)

#### Disney+ / Max Preset — Top Header Bar & Midnight Navy Gradient
![Disney+ / Max Preset](screenshots/home-disney-plus.png)

#### Appearance & UI Preset Picker (Per Profile)
![Appearance Picker](screenshots/appearance-picker.png)

### Top 10 Movies Row, Genre Filters & Live Availability Badges
![Top 10 Row](screenshots/home-top10.png)

### Collections Hub & Saga Completion
![Collections Hub](screenshots/collections.png)

### Title Details, Versions/Editions, TV Seasons, Extras & Live 4K Download ETA
![Details Screen](screenshots/details.png)

### Live TV & DVR Guide
![Live TV & DVR Guide](screenshots/live-tv.png)

### Search, People & Genre Discovery
![Search Screen](screenshots/search.png)

### Media3 Player Overlays (Episodes Drawer, Chapters, ASS/SSA & Stream Info)
![Player Overlays](screenshots/player-overlays.png)

### Requests & Downloads Hub (HD & 4K Queue, Live Progress & Admin Approvals)
![Requests Screen](screenshots/requests.png)

### "Who's Watching?" Profile Picker, PIN Lock, Kids Mode & Manage Profiles
![Profile Picker](screenshots/profiles.png)
![Profile PIN Lock](screenshots/profile-pin-card.png)
![Manage Profiles](screenshots/profiles-manage.png)

### Zero-Friction Setup (LAN Auto-Discovery, QR Companion, Dual Failover & Jellyfin-Only Mode)
![Server Setup](screenshots/setup.png)

> *Note: UI screenshots above are rendered from the real Jetpack Compose TV layouts via CashApp Paparazzi (`./gradlew testDebugUnitTest`) using synthetic poster/backdrop preview artwork.*

## Key Features
- **Per-User UI Presets & Customizable Home**: Each profile independently chooses and persists its own streaming UI layout (`Netflix`, `Apple TV+`, or `Disney+ / Max`), default startup tab, pinned Jellyfin library rows, and hidden/reordered Home rows.
- **Zero-Friction Onboarding & Smart Networking**:
  - **Jellyfin-Only Mode**: Skip Seerr/Jellyseerr entirely and browse/stream your Jellyfin libraries directly.
  - **LAN Auto-Discovery + QR Phone Companion**: Discovers Jellyfin servers via UDP `7359` broadcast and hosts a temporary local HTTP form reachable by scanning an on-screen QR code from your phone — no typing URLs with a TV remote.
  - **Dual Local/Remote Failover & Self-Signed SSL**: Configure separate LAN and remote URLs per server with automatic failover when roaming, plus an optional self-signed certificate toggle for homelab setups.
- **Plays Right on Every TV (new in 0.7)**:
  - **Device-Aware Playback**: Lumen reads the box's decoders (H.264 / HEVC 8- & 10-bit / VP9 / AV1, max resolution per codec), the screen's HDR support (HDR10, HDR10+, HLG, Dolby Vision) and what the TV or receiver accepts over HDMI (AC3, E-AC3, DTS, TrueHD), and sends Jellyfin a matching device profile. HDR on an SDR screen is tone-mapped by the server, Dolby Vision profile 5/7 only direct-plays on Dolby Vision hardware, DTS/TrueHD is converted when nothing downstream can play it, and missing codecs are transcoded instead of failing. For HDR content on SDR TVs, turn on tone mapping in Jellyfin (Dashboard → Playback → Transcoding), otherwise converted HDR looks washed out. A **Match this TV's capabilities** switch in Settings falls back to the old "allow everything" profile if detection is ever wrong.
  - **Media Session**: "Hey Google, pause", HDMI-CEC / Bluetooth remote media keys and the system playback controls work in the player.
- **Remote Control, SyncPlay & Live Updates (new in 0.7)**:
  - **"Play on" this TV**: Lumen keeps a live WebSocket connection to Jellyfin while it is on screen, so the TV shows up as a cast target in Jellyfin for web, iOS, Android and desktop — start playback, pause, seek, skip, stop or send an on-screen message from your phone. Can be switched off in Settings.
  - **Real Jellyfin SyncPlay**: Watch Together now follows the server's timed group commands (clock-synced to the server), reports buffering/ready like the official clients, and loads whatever the group switches to.
  - **Live Library Updates**: Home refreshes in place when titles are added or removed on the server and when watch progress changes on another device.
- **Home Theater & Codec Compatibility**:
  - **ASS / SSA Anime Subtitles**: Renders styled ASS/SSA dialogue overlays natively via Media3, or switches cleanly between Server Burn-In and Plain Text fallback.
  - **Audio Passthrough & Stereo Downmix**: Toggle optical/HDMI bitstream passthrough or 2.0 stereo downmix in-player.
  - **Multi-Version & Edition Picker**: Switch between 4K Remux, 1080p Web-DL, Director's Cut, or Extended editions right on Details or inside the player.
  - **Chapters, External Player & Auto-Skip**: Visual chapter tick marks on the seekbar with chapter jump list, `ACTION_VIEW` external player fallback (Kodi / Nova / MX / VLC), **24fps frame-rate matching**, **Auto-Skip Intro/Recap** (with Undo pill), **Subtitle Sync Offset (±ms)**, and **On-Demand Jellyfin Remote Subtitle Search/Download**.
- **Complete Seerr v3+ Loop**:
  - Request **HD (1080p)**, **4K (2160p)**, or **Both**, choose **Radarr / Sonarr Server, Quality Profile & Root Folder**, and select individual TV seasons.
  - **Admin Queue & Issue Reporting**: Seerr admins can **Approve** or **Decline** pending requests straight from the TV UI, and any user can **Report an Issue** (Video, Audio, Subtitle, Other) from Details.
  - **Live ETA & Background Ready Alerts**: Polls `downloadStatus` / `downloadStatus4k` every 15s, plus a **WorkManager** background check that promotes newly fulfilled requests into **"Ready to Watch"** and syncs to the Android TV Home **Watch Next** channel.
- **Household, Kids Mode & Social**:
  - **Kids Mode & Parental Filtering**: Restricts Home, Search, People, Genres, Live TV, and Requests by MPAA/TV maturity rating (`G`, `PG`, `TV-Y`, `TV-G`, `TV-PG`) with optional **4-digit Profile PIN lock** on adult profiles.
  - **Jellyfin SyncPlay**: Create, join, and sync **Watch Together** rooms directly from the top bar or player.
  - **Mark Watched / Unwatched** and Quick Connect 6-digit TV login.
- **Library Browsing (new in 0.7)**: **All Movies / All TV Shows** grids paged from the server with sort (A-Z, date added, release date, rating, random), Unwatched / Watched / Favorites filters, genre filter and A-Z jump.
- **Settings, Subtitles & Diagnostics (new in 0.7)**:
  - A dedicated **Settings** screen for playback (capability matching, passthrough, downmix, frame-rate matching, autoplay, auto-skip), remote control and server connection.
  - **Subtitle Styling**: text size, color (white / yellow / cyan / green / grey), background opacity, edge style (outline, drop shadow, raised, none) and position, with a live preview.
  - **Crash Reports & Log Upload**: after a crash Lumen offers to store a report on *your own* Jellyfin server (Dashboard → Logs); **Send logs** in Settings uploads device info, detected playback support and the recent app log with sign-in tokens removed. Nothing is sent anywhere else.
- **Live TV & DVR, Collections, Coming Soon & Extras**:
  - Dedicated **Live TV** tab with EPG Now/Next timeline bars, live channel tuning, DVR recordings, and **DVR scheduling** (new in 0.7): hold OK on a channel to record the current or next programme or the whole series, see REC badges in the guide, and cancel from the **Scheduled recordings** list.
  - **Collections & Saga Completion** (`Part of Saga` + 1-click `Request Missing`), **Coming Soon** countdown reminders, **Extras & Special Features** row on Details, **On-Device YouTube Trailers** (`NewPipeExtractor`), **In-App GitHub Release Updater**, and localized UI (`English`, `Español`, `Français`, `Deutsch`, `日本語`) + metadata language support.

> Lumen is video-focused: music, photo and audiobook libraries are not shown.

## Build & Sideload to Google TV
The easiest option is the APK attached to the latest [GitHub release](../../releases/latest). To build it yourself:

1. Build the optimized release APK (R8-shrunk, with a baseline profile so ART precompiles the app at install time):
   ```bash
   ./gradlew assembleRelease
   ```
   Output: `app/build/outputs/apk/release/app-release.apk`

   > Use the release build on your TV. Debug builds (`assembleDebug`) run unoptimized Compose code and feel noticeably laggy on low-end sticks like the onn. 4K.
2. Enable **Developer Options → USB/Network Debugging** on your Google TV and install via ADB:
   ```bash
   adb connect <GOOGLE_TV_IP>:5555
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```

### Top 10 charts (optional Trakt key)
"Top 10 Movies Out Right Now" and "Top 10 TV Shows Airing Now" rank what Trakt members watched this week. Create a free API app at [trakt.tv/oauth/applications](https://trakt.tv/oauth/applications/new) (any redirect URI, e.g. `urn:ietf:wg:oauth:2.0:oob`) and put its **Client ID** in `local.properties`:
```properties
trakt.clientId=YOUR_CLIENT_ID
```
For GitHub release builds, add it as the `TRAKT_CLIENT_ID` repository secret. Without a key those two rows fall back to TMDB popularity through Seerr. Either way, titles that aren't released yet never appear in a Top 10 row; they're in the Coming Soon rows instead.

## Tests & Screenshot Verification
```bash
./gradlew testDebugUnitTest
```
