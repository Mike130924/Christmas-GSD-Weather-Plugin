# Project status

## Verified
- Independent Android application ID: `com.christmasgsd.weather`.
- Independent Projectivy provider UUID: `d9b06d63-17ab-4e4c-a4d6-4f0d3c4f81d2`.
- Projectivy wallpaper-provider AIDL contract and API models copied from the official public provider API source (integration contract only).
- Christmas GSD background asset recovered and copied into app resources and design references.
- Manifest declares Projectivy wallpaper provider action, API version 1, UUID, settings activity, cache duration and time-elapsed update event.

## Not yet verified
- Android build/compilation (Android SDK and Gradle are not installed in the current execution environment).
- Cross-app image URI access and wallpaper rendering on Projectivy/device.
- Weather API/data source, settings persistence/import/export, custom wallpaper picker, compositing renderer, update installer/signing.

## Design requirements
- Screenshot-faithful rounded dark translucent weather panel with red neon edge, clock/date, current conditions and six-day forecast.
- Custom background wallpaper with selectable crop/fit/stretch/centre modes.
- Clock/date visibility, 12/24-hour time, date formats, independent colour/opacity/size/weight.
- Custom palette controls and presets, panel opacity, neon colour/intensity.
- In-app update checker and user-confirmed Android package update; stable application ID and release signing key.

## Renderer milestone (source added; not build-verified)
- Added `app/src/main/java/com/christmasgsd/weather/render/WeatherWallpaperRenderer.kt`.
- Implements an independent Android Canvas renderer using the 1536x626 design coordinate system, scaled to the actual output dimensions.
- Supports a selected background bitmap with fill/crop, fit, stretch, and centre modes; a separate translucent rounded weather panel; neon border/glow; current conditions; six forecast slots; simple weather-code icons; festive trim; and configurable clock/date styling and palette values.
- Renderer inputs are typed Kotlin data classes (`WallpaperWeather`, `CurrentWeather`, `ForecastDay`, `WallpaperRenderConfig`, `WallpaperPalette`, `ClockStyle`). Weather retrieval and Projectivy image-URI delivery were subsequently connected in the service integration milestone below.
- This is source implementation only. It has not been compiled or run because the environment currently has no detected Gradle executable or Android SDK.

## Weather/service integration milestone (source added; not build/device verified)
- Added `data/OpenMeteoRepository.kt`, which requests current conditions and daily forecast data from the Open-Meteo forecast endpoint, parses weather codes, temperatures, wind speed/direction and six forecast days.
- Connected the Projectivy AIDL service to a background executor, the renderer, a persistent generated PNG and a read-only exported `WallpaperImageProvider` URI (`content://<applicationId>.wallpaperimages/current.png`).
- Added basic location configuration fields (decimal latitude/longitude and display label) to `SettingsActivity`; saving starts a refresh. Coordinates are deliberately user-entered rather than silently assuming a location.
- The service retains the last successful generated wallpaper if a weather request fails. With no coordinates configured, it supplies the bundled Christmas background as a fallback.
- Still unverified: Projectivy's ability to open the exported content URI, actual refresh-broadcast compatibility, compilation, live API execution, and TV rendering. The settings screen is currently only the location foundation; custom wallpaper, palette and clock controls remain outstanding.


## Save-and-refresh control (source added; not build/device verified)
- Settings now has a prominent **Save all changes & refresh overlay** button. It validates the location fields, synchronously commits them to the shared preferences store, then starts the wallpaper refresh service. This same action will persist/apply additional controls as they are added to the settings screen and stored in the same preference file.
- The renderer service now writes a refreshed PNG to a temporary file, syncs it, then publishes it as `current.png` before sending the Projectivy provider-update broadcast. This avoids Projectivy opening a half-written image during refresh.
- The button currently saves the settings that exist on the screen (location label and coordinates); the future wallpaper, palette, clock and date controls still need to be implemented and wired to this shared save/apply path. Android build and Projectivy refresh behaviour remain unverified.

## Saved theme profiles (source added; not build/device verified)
- Added `themes/ThemeProfiles.kt` to save named appearance snapshots independently in app preferences. Theme snapshots include appearance-related keys only (palette, clock/date, readout, background, icon, forecast and layout settings); location settings are excluded.
- Added settings controls to save the current appearance as a new named theme and load a selected saved theme, followed by an overlay refresh.
- Existing theme names are rejected rather than silently overwritten, protecting the original Christmas theme when creating another look. A user can create separate profiles such as `Christmas GSD` and `Winter Blue`; a future explicit edit/replace action can be added if desired.
- Future appearance controls must persist under the documented prefixes (`palette.`, `clock.`, `date.`, `readout.`, `background.`, `icon.`, `forecast.`, `layout.`) or the explicit background keys so they are captured by theme snapshots.
- Android compilation and on-device persistence/refresh remain unverified because Gradle/Android SDK are unavailable in this environment.


## Projectivy display behaviour settings (source added; not build/device verified)
- Added a **Reduce screen burn-in** toggle, enabled by default. The renderer applies a small alternating pixel offset to the composition; it depends on Projectivy's wallpaper refresh cadence to move periodically.
- Added **Where your app row starts** percentage control (0–100%, default 70%). The saved value is included in the render configuration as a small vertical composition adjustment relative to the 70% baseline. This is an approximation of Projectivy's launcher layout setting, not a replacement for Projectivy's own system-level app-row positioning.
- Added **Use the full screen when idle** toggle, enabled by default. The service now listens for Projectivy's `LauncherIdleModeChanged` event and renders the tighter full-frame panel inset only while idle; otherwise it uses the normal inset.
- All three values are persisted under `layout.*`, so they are included in named theme snapshots and restored when a theme is loaded. The existing Save all changes & refresh overlay action commits them before requesting a refresh.
- Source-level implementation only; Android compilation, event delivery and physical OLED/TV behaviour have not been verified.


## Weather information preferences (settings source added; rendering/data feeds not yet verified)
- Added independent settings for last-updated timestamp, severe weather alerts, six-hour hourly forecast, five-day daily forecast, extra statistics, and sunrise/sunset/daylight information.
- Added a World Weather Watch selector: off/local only, nearby areas, national overview, or worldwide overview.
- All selections persist in SharedPreferences and are included in saved theme profiles under the `weather.*` prefix. The existing Save all changes & refresh overlay action commits them.
- UK severe-weather alerts are described as requiring a supported official UK warning feed; the US-only NWS source shown in the reference screenshot is not used.
- Important: these are settings controls and saved preferences at this stage. The additional data fields, warning feed, world-watch aggregation, and conditional renderer layouts still need implementation and testing.

## Weather detail data and conditional rendering (source updated; not build/device verified)
- Expanded the Open-Meteo request to include six upcoming hourly temperature/rain-probability/weather-code points, daily sunrise/sunset, UV maximum, current relative humidity, mean-sea-level pressure, apparent temperature, wind and precipitation data.
- Added typed hourly forecast and sun-time models, plus humidity, dew point, pressure and UV fields to the current-weather model. Dew point is calculated from temperature and relative humidity; visibility is left unavailable rather than fabricated because it is not currently requested from the source.
- Connected the saved `weather.show_updated`, `weather.show_hourly`, `weather.show_daily`, `weather.show_extra_stats`, and `weather.show_sun_times` preferences through the service into the renderer. The renderer conditionally draws the update time, hourly summary, extra statistics, sunrise/sunset/daylight and daily forecast (five days when enabled).
- The six-hour hourly forecast is currently represented as a compact text row, not a dedicated icon/graph layout. Severe-warning retrieval and World Weather Watch aggregation are not implemented; their existing settings are not evidence of live warning/global feeds. No US NWS feed is used.
- Source changes have not been compiled or exercised against the live API or Projectivy. The project still needs an Android SDK/Gradle build environment and on-device verification.


## Readout size control (source added; not build/device verified)
- Added a settings selector for Small, Medium, Large, Extra Large and a custom-labelled 110% option.
- The selected scale is saved under `readout.scale`, so it is included in saved theme snapshots.
- The renderer applies this scale to the weather information and forecast only; the outer panel and clock remain at their independent sizes.
- This is a discrete size selector rather than a continuous custom slider. Android compilation and visual fit checks remain outstanding.


## Colour palette controls (source added; not build/device verified)
- Added preset choices: Christmas Red, Arctic Blue, Winter Forest and Golden Christmas, plus Custom.
- Added individual editable hex colour fields for panel background (including alpha/transparency), neon border, glow, primary/secondary text, temperature, weather icon, forecast divider, greenery and snow.
- Preset selection populates editable fields; users can adjust the values before saving. The normal Save all changes & refresh overlay action validates and persists palette values.
- Service now parses saved colours into `WallpaperPalette` and passes that palette into the Canvas renderer. Theme profile snapshots already include the `palette.*` prefix, so separate saved themes retain separate colour schemes.
- Source-level implementation only. Android compilation, live preview UI, and actual TV appearance remain unverified. Invalid colour text is flagged at save time; visual swatch/live preview is not yet implemented.

## Clock and date preference foundation (source added; not build-verified)
- Added `ClockDatePreferences.kt` with independent time/date visibility, 12/24-hour formatting, five selectable date patterns, independent ARGB colours, opacity, size, bold weight and date placement preferences.
- Added safe formatting using the device's local timezone and locale; unsupported date patterns fall back to `EEE d MMM`.
- Added `themes/ThemeProfileStore.kt` with named profile snapshots, duplicate-name protection and restoration of appearance keys. Clock/date keys are included in snapshots.
- These are persistence/model components, not yet a complete settings UI or connected renderer controls.

## Verification clarification
- The initial shallow file listing did not descend far enough to show the nested Kotlin source files. A full recursive listing confirms that the renderer, weather repository, provider, settings activity and wallpaper service source files are present.
- The clock/date preference model and additional theme-profile store added above are present as source files. They are not yet confirmed to be wired into every settings control and renderer path.
- No Android compilation, APK, or installable ZIP has been produced. All future progress reports will distinguish source presence from compilation and device verification.

## Custom wallpaper selection (source added; not build/device verified)
- Added an Android document picker for JPEG, PNG and WebP images, including persistable read permission for selected document URIs (including supported USB/document-provider locations).
- Added a saved-background list in SharedPreferences, selection spinner, image preview, and remove-selected action. The active background URI is stored separately from the overlay composition.
- Added Fill/Crop, Fit, Stretch and Centre display-mode selection and passed the chosen mode into the Canvas renderer configuration.
- Saved backgrounds and the active background URI are included in theme snapshots through the existing `background.*` prefix and `background_uri` key.
- The main Save all changes & refresh overlay action persists the selected background URI and display mode before requesting a render.
- Limitations: the current image preview is a simple still preview; the fallback path when no weather coordinates are configured still displays the bundled background rather than rendering the selected custom image. Android compilation, USB-provider permission behaviour, image decoding limits, Projectivy reload and TV preview remain unverified.

## Settings navigation and reachability (source updated; not build/device verified)
- Wrapped the long settings form in a vertically scrollable Android `ScrollView`, with descendant focus traversal enabled for Android TV remote navigation.
- This ensures the lower sections (weather options, theme save/load and Save all changes & refresh overlay) are reachable on a TV-sized display instead of extending below a fixed viewport.
- Android manifest XML parsing was rechecked successfully after the change. Kotlin compilation and physical remote-navigation testing remain outstanding.

## Clock/date preferences wired into rendering (source updated; not build/device verified)
- The wallpaper service now reads saved clock/date visibility, 12/24-hour mode, date pattern, independent colours, opacity, size, boldness and date position into `ClockStyle`.
- The renderer now supports date placement above, below or in the secondary line beside the clock, while preserving independent show/hide switches.
- Android compilation and TV rendering remain unverified because the environment does not currently have a configured Android SDK/Gradle build toolchain.


## Theme profile consistency fix (source updated; not build/device verified)
- Found two theme persistence implementations using different SharedPreferences files and incompatible value encodings. The unused `ThemeProfileStore` previously converted all saved values to strings, which could cause type mismatches when renderer code reads booleans and integers.
- Reworked `ThemeProfileStore` into a facade over the same typed `weather` preferences and `ThemeProfiles` implementation used by the settings screen. Theme save/load/export now share one source of truth; location remains excluded and duplicate names remain protected.
- This is a source-level correction. Android compilation and persistence round-trip tests have not been run because a Gradle wrapper/runtime and Android SDK are not present in the project environment.

## Saved theme editing and deletion (source added; not build/device verified)
- Added `ThemeProfiles.update()` to replace an existing named profile with the current typed appearance snapshot. It refuses unknown/blank names and preserves all other profiles.
- Added `ThemeProfiles.delete()` to remove a named profile and clear `active_theme` only when it points to the deleted profile. It does not delete source wallpaper files or saved background URI entries.
- Added settings actions for updating and deleting the selected theme. Both actions require an explicit Android confirmation dialog; update warns that it replaces the profile, and delete clarifies that the wallpaper image itself is retained.
- Added success/failure toasts and refreshes the theme selector after either operation. Updating a theme requests an overlay refresh.
- Static checks confirmed the new methods and UI actions are present and `AndroidManifest.xml` remains valid XML. No Kotlin/Gradle build or device test has been run.


## Theme export and import (source added; not build/device verified)
- Added a versioned portable JSON theme format (`christmas-gsd-theme`, schema version 1) containing one named appearance snapshot. It excludes location and other device-specific settings.
- Added export through Android's Create Document picker and import through Open Document, supporting local storage and compatible USB/document providers.
- Import validates the format, schema version, allowed appearance-setting keys, value types, payload size and theme name. It refuses duplicate names rather than overwriting an existing Christmas or custom theme.
- Export/import controls are available alongside saved theme management. Imported themes are added to the list but are not automatically applied; the user chooses when to load and refresh them.
- Source-level only: Android compilation, file-picker behaviour on the target TV, and round-trip export/import testing remain outstanding.


## 2026-11-13 follow-up
- Inspected the actual SettingsActivity source and found two overrides of `onActivityResult`, which would prevent Kotlin compilation.
- Consolidated wallpaper selection, theme export, and theme import handling into one callback, preserving each request-code path.
- This is a source-level correction only; no successful Gradle build or Android TV runtime test is claimed.
- Whole-plugin backup/restore remains a separate next task. Theme-only JSON export/import remains available.


## Whole-plugin backup and restore (source added; not build/device verified)
- Added `backup/PluginBackup.kt` with a versioned JSON backup format. It captures typed SharedPreferences values (including all named theme profiles and plugin settings) and embeds the bytes of saved custom wallpaper images, subject to 12 MB per image and 32 MB total backup limits.
- Added Android document-picker actions to export a complete `.gsdbackup.json` file and restore one after an explicit confirmation. Restore validates the format/version and replaces the plugin preference set; restored image bytes are written into app-private storage so they do not rely on the original USB/document-provider URI.
- Added read-only provider routing for restored background assets under `content://<applicationId>.wallpaperimages/backgrounds/<filename>`.
- Important limitation: the restore path is source-level only. Need compile-time validation, SAF round-trip testing, and verification that every restored background URI is correctly loaded by settings preview and wallpaper renderer before calling this feature production-ready. Existing custom wallpaper URI sources that cannot be read during export are skipped and should be reported more clearly in a future refinement.
- No APK or installable ZIP has been produced.


## 2026-11-13 recursive project audit correction
- Re-ran a recursive file inventory of the working directory. The Kotlin source files previously described as missing are present: `SettingsActivity.kt`, `WeatherWallpaperService.kt`, `render/WeatherWallpaperRenderer.kt`, `data/OpenMeteoRepository.kt`, `provider/WallpaperImageProvider.kt`, `themes/ThemeProfiles.kt`, `themes/ThemeProfileStore.kt`, `backup/PluginBackup.kt`, and `ClockDatePreferences.kt`.
- The earlier progress message claiming these files were missing was incorrect. The actual issue is that the source has not been compiled or exercised, so file presence must not be confused with a working feature.
- Confirmed there is exactly one `onActivityResult()` override in `SettingsActivity.kt`; the source includes document-picker branches for wallpaper selection, theme export/import, and full backup export/restore.
- Confirmed `saveAndRefresh()` persists settings with synchronous `commit()` before starting the refresh service.
- Toolchain audit: `kotlinc` is installed, but no Gradle executable, Gradle wrapper scripts/JAR, or Android SDK `android.jar` was found in the checked environment. Therefore an Android build cannot currently be run here.
- Automatic updates have not yet been implemented. A real GitHub Releases updater requires the user's repository owner/name or release API URL, plus a stable release signing key; neither should be guessed or fabricated.


## Setup wallpaper fallback correction (source updated; not build/device verified)
- Corrected the no-location path: it now renders the selected (or bundled) background with a clear setup prompt instead of publishing the untouched background image.
- Added a shared atomic PNG publishing helper used by setup and weather renders. It writes a temporary file, syncs it, renames it into place, and recycles the bitmap.
- This prevents a blank-looking initial experience and honours the selected background display mode before coordinates are configured.
- Android compilation, Projectivy content-provider access, and TV refresh behaviour remain unverified.

## 2026-11-13 provider startup race fix
- Audited the actual Projectivy service and exported image provider. Found a startup race: `getWallpapers()` returned the `content://` image URI while the first wallpaper render ran asynchronously, so Projectivy could open the URI before `current.png` existed.
- Added a synchronous `onCreate()` initial composition. It loads the selected (or bundled) background, renders the setup overlay, and atomically publishes `current.png` before Projectivy can bind and request the wallpaper URI. The live weather refresh remains asynchronous and replaces the setup composition when ready.
- If initial composition fails, the service creates a bundled-image fallback so the exported URI has a file to open.
- Static source inspection only. Android compilation, Projectivy binding, URI access and TV refresh have not been tested.


## 2026-11-13 Projectivy refresh broadcast contract audit
- Compared the wallpaper service's refresh notification against the project's own `WallpaperProviderContract` constants.
- Fixed the service to use the contract's exact broadcast action and namespaced extra keys for provider UUID and update reason, rather than unqualified literal extra names.
- This aligns the notification with the contract declared in the API module; actual reception by Projectivy remains unverified until an Android build and TV test.
- Also confirmed the provider serves `current.png` from the authority declared in the manifest (`${applicationId}.wallpaperimages`).


## 2026-11-13 display-layout audit refinement
- Audited the actual service-to-renderer path for the three Projectivy-style display settings. The service reads the saved app-row percentage, burn-in toggle and idle-mode toggle, and receives `LauncherIdleModeChanged` events.
- Refined the app-row anchor so the percentage now produces a meaningful vertical composition adjustment instead of the previous near-imperceptible 0.35px-per-percent multiplier. The 70% reference remains unchanged.
- Refined panel insets: normal launcher mode uses a 34px safe inset, while configured full-screen idle mode draws to the canvas edges.
- Burn-in protection remains a small periodic offset based on render time; it only changes when Projectivy requests a refreshed wallpaper. This is not a continuously animated pixel shift, and its real cadence depends on Projectivy refresh events.
- Static source inspection only. These layout adjustments have not been compiled or visually verified on the target TV.

## 2026-11-13 readout/forecast layout audit
- Confirmed `readout.scale` is stored as a string by the settings spinner and read as a string by the service; the scale is applied around the weather content, including optional readouts and the daily forecast. The available preset values are 0.85, 1.0, 1.15, 1.25 and 1.10; the last is labelled custom but is currently a fixed 110% preset, not a free slider.
- Confirmed hourly data is requested and parsed from Open-Meteo, and the renderer receives the show-hourly/show-daily/show-extra-stats/show-sun-times switches.
- Found a genuine layout collision: optional updated/stats/sun/hourly lines could overlap the daily forecast separator and labels. Changed optional readouts to occupy sequential rows and moved the forecast down according to the number of visible optional rows. Forecast row coordinates now derive from its top position.
- Added visibility to the extra-statistics line when a valid visibility value is available. The current Open-Meteo repository still supplies `NaN` for visibility, so it correctly remains omitted until a source value is added.
- This is source-level only. No Android compile, emulator render, or Projectivy TV test has been run.


## 2026-11-13 visibility and daylight remaining implementation
- Added Open-Meteo hourly `visibility` to the request. The repository reads the nearest current forecast hour, converts metres to kilometres, and leaves the value unavailable (`NaN`) when the API omits it rather than inventing a reading.
- Added `daylightRemainingMinutes` to `SunTimes`, calculated from the configured location's sunrise/sunset instants and current instant. It is zero before sunrise/after sunset and counts down only during daylight.
- Extended the existing sun-times overlay line to show total daylight and remaining daylight (or “No daylight remaining”).
- Source-level change only. No live API request, Android compilation, or TV rendering has been performed.

## UK severe-weather warnings (source milestone; integration incomplete)
- Added `alerts/UkWeatherWarningsRepository.kt` as an independent NSWWS client.
- Requires the Met Office-issued NSWWS API key and feed URL; the Met Office documentation confirms these are required for authenticated access.
- Parses Atom feed links to issued warning GeoJSON, matches Polygon/MultiPolygon warning areas against configured coordinates, and extracts headline/type/severity/validity/details.
- Not yet connected to settings, service refresh, caching, or renderer. No warning display is active until credentials/feed URL are configured and the integration is completed.
- Not compiled or live-tested in this environment.

## 2026-11-13 UK severe-weather warning display integration (source added; not build/live verified)
- Added Met Office NSWWS feed URL and API-key fields to the settings screen. They are saved in the app's private `weather` preferences and included in full local backup; theme snapshots continue to exclude credentials.
- Connected `UkWeatherWarningsRepository` to the wallpaper refresh path. When severe alerts are enabled and both credentials are present, the service checks warnings covering the configured latitude/longitude and selects the highest severity (RED, then AMBER, then YELLOW).
- Added a severity-coloured warning banner to the renderer. If credentials are absent, the API fails, or no warning covers the configured point, the normal weather overlay continues without a warning banner.
- The NSWWS v1.0 documentation says authentication uses `x-api-key` and a supplied Atom feed URL; current Weather DataHub v1.1 documentation uses the `apikey` header. The current repository uses `apikey`, matching the newer DataHub API. API-version compatibility must be confirmed against the user's issued feed URL/credentials before live use.
- Met Office API credentials are stored in app-private preferences, not encrypted storage. Do not treat this as production-grade secret protection; avoid sharing/exporting backups containing the key. The full backup currently needs a credential-exclusion review before distribution.
- Static checks passed: manifest XML parses; settings contain feed URL/key controls; service calls the warning repository; renderer receives and draws warning headline/severity. No live API call, Kotlin compilation, APK build, or TV rendering test has been performed.


## 2026-11-13 UK severe-weather warning cache and expiry
- Added a persistent SharedPreferences cache scoped to the configured feed URL and rounded coordinates, preventing one location's warnings from being reused for another.
- The service now polls at most once per 15 minutes during wallpaper refreshes. A successful fetch replaces the cached collection; on network/API failure, the last cached collection is used.
- Cached warnings are filtered by `validToDate` on every read, so expired or unparseable-expiry entries are not displayed even while offline.
- Corrected NSWWS field mapping to prefer the documented `warningHeadline`, `warningLevel`, `validFromDate`, `validToDate` fields, and supports `weatherType` / `whatToExpect` arrays.
- Corrected Atom feed link selection to use the authoritative root `rel=related` latest-issued collection link, as specified by the Met Office.
- Met Office documentation says the Atom feed changes when warnings are issued, updated, cancelled or expire, and the related link supplies the current issued collection. The 15-minute interval is a conservative client-side poll interval, not a claim about the user's subscription quota.
- Static checks passed: manifest XML; cache persistence; 15-minute service integration; expiry filtering; stale-cache fallback; Atom related-link parsing; documented NSWWS field mapping. No Gradle/Android SDK was available, so Kotlin compilation, API authentication and live feed tests remain unverified.


## 2026-11-13 multiple UK warning presentation
- Extended the render configuration with a typed list of warning display records rather than passing only the single highest-severity headline.
- The service now sorts all active location-matched warnings by severity and passes the collection to the renderer.
- The renderer can display up to two simultaneous warning rows, each showing severity, headline, weather type and validity window converted to UK local time (`Europe/London`).
- Retained a single-warning fallback for callers still using the original headline/severity fields.
- Static source inspection only. No Kotlin/Android build, Met Office live feed test, or TV layout verification has been performed.

## 2026-11-13 warning rotation, countdown and details
- Changed the warning banner from showing two fixed rows to rotating through all location-matched warnings on successive wallpaper renders (20-second time slots). Because Projectivy consumes rendered wallpaper images, this is render-driven rotation, not a continuously animated view; visible rotation depends on Projectivy requesting a new render.
- Added countdown text: future warnings show `Starts in`; warnings already in force show `Ends in`. Countdown is calculated from the warning's ISO instant timestamps and formatted as days/hours/minutes.
- Added a shortened `whatToExpect`/description line to the warning banner and retained UK-local validity times (`Europe/London`).
- Static checks passed for rotation selection, start/end countdown branches, description rendering, UK timezone formatting and Android manifest XML parsing. No Kotlin compilation, live API request or Projectivy TV test has been run.
- Met Office documentation confirms the Atom feed is the source of truth and is updated when warnings are issued, updated, cancelled or expire; the NSWWS API supplies validity periods and warning text. See https://datahub.metoffice.gov.uk/docs/g/category/warnings/overview and https://metoffice.github.io/nswws-public-api/.

## 2026-11-13 warning display controls
- Added a settings selector for warning placement: separate wallpaper banner or inside the weather panel.
- Added an independent switch to show/hide warning validity, countdown and description details.
- Passed both preferences from the settings store through `WeatherWallpaperService` into `WallpaperRenderConfig`.
- Updated `WeatherWallpaperRenderer` to position the warning row according to the selected mode and suppress detail text when disabled.
- Static checks passed for settings controls, service-to-renderer preference wiring, both renderer options and Android manifest XML parsing. No Android compilation or visual TV test has been run.


## 2026-11-13 warning banner collision and text-fit audit
- Reworked warning placement: separate-banner mode uses the top safe strip; inside-panel mode uses the upper-right lane, away from the location label and main current-condition values.
- Draws festive garland before the warning so decoration cannot paint over warning text.
- Replaced character-count truncation with pixel-width measurement and clipping, so long headlines/details cannot spill beyond the coloured warning panel.
- Details remain optional and use the same bounded width.
- Static source checks and Android manifest XML parsing passed. No Android compilation or TV visual verification has been performed.

## Renderer audit – 13 November 2026

- Inspected the actual Kotlin renderer source and project tree.
- Fixed warning ordering: simultaneous warnings are now sorted RED, AMBER, YELLOW before rotation.
- Warning rotation now uses the render timestamp passed into `render()` rather than reading the system clock independently, keeping a frame internally consistent and making the selection deterministic for a given render time.
- Rotation index uses `Math.floorMod` to safely wrap for negative timestamps as well.
- Static source checks passed for timestamp propagation, severity order, rotation and single companion object.
- No Gradle executable, Gradle wrapper scripts, or Android SDK `android.jar` were found in this environment. Android compilation remains unverified.

## Renderer audit – 13 November 2026 (forecast bounds)

- Expanded the daily forecast from five to six days, matching the requested six-day display and using six equal-width columns.
- Capped the forecast's vertical position so its lowest text baseline remains within the 626px design canvas, accounting for positive app-row and burn-in offsets. This also prevents the optional weather rows from pushing the forecast off-screen.
- Made warning countdown calculations use the same render timestamp as the rest of the frame, rather than independently sampling the system clock.
- Static source assertions passed for six-day selection, offset-aware forecast positioning, and consistent warning timestamp use. Android/Kotlin compilation and actual Projectivy TV rendering remain unverified because the environment has no Gradle executable/wrapper or Android SDK.

### 13 November 2026 — settings/renderer integration audit
- Replaced the misleading fixed `Custom — 110%` readout-size spinner entry with a genuine 75–135% slider in 1% increments. The selected value is persisted as `readout.scale`, matching the service's renderer configuration.
- Corrected the `Beside time` date-position option: date and time now share a baseline, with the date placed immediately to the left of the time. If only one is enabled, it remains readable in the established clock area.
- Separated forecast drawing from the scaled current/optional readout canvas. This prevents a large readout scale from magnifying the six-day forecast beyond the lower edge of the 626px composition.
- Source-level checks performed for slider range/value persistence, horizontal date placement, and forecast canvas restore order. Android build/runtime verification remains blocked by missing Gradle and Android SDK tooling.

## 13 November 2026 — backup/theme reference audit
- Whole-plugin backup now gathers unique wallpaper URIs from the saved background list, the currently active background, and each named theme snapshot.
- Backup creation now fails visibly if a referenced wallpaper cannot be read or is empty, instead of silently producing an incomplete backup.
- Restore builds an original-URI to app-private restored-URI map and applies it to the active background, saved background list, and saved theme snapshots. This prevents restored profiles from retaining references to removable USB/document-provider locations.
- Static source checks performed for candidate collection, fail-fast image reads, URI remapping in settings and theme snapshots. Android compilation/runtime restore test remains unavailable in this environment (no Android SDK/Gradle installation).

## 13 November 2026 — restore failure-safety audit
- Changed restored wallpaper filenames to include a unique restore token, so a new restore never overwrites files referenced by the currently active preferences before the new settings have committed.
- Updated the read-only wallpaper content provider's filename validation to accept the new unique filename format while continuing to reject arbitrary paths.
- Made malformed saved-theme JSON fail validation before preferences are committed; it is no longer silently ignored while the rest of a backup is applied.
- Restore now commits the new preferences first, then removes obsolete restored wallpaper assets. If staging or the preferences commit fails, only files created by that attempted restore are deleted; the previous working wallpaper files are preserved.
- Six targeted source assertions passed. This is source-level verification only; Android compilation, SharedPreferences failure injection, and on-device restore testing remain unperformed because this environment lacks the Android SDK and Gradle tooling.


## 13 November 2026 — Theme/backup preference isolation audit

- Found that the broad `background.` theme-prefix rule also captured `background.saved_uris`. Loading a theme could therefore replace the device-wide saved wallpaper library with an older snapshot.
- Updated the theme appearance-key filter to exclude `background.saved_uris`. Themes still retain their selected `background_uri` and `background_mode`, while the user's full saved wallpaper collection remains global and untouched.
- This also prevents theme import from injecting a saved-wallpaper collection into global preferences.
- Static source checks verified the exclusion and that both save/update and apply continue to use the shared appearance-key filter. Android runtime testing remains outstanding.


## 13 November 2026 — backup credential protection
- Excluded `alerts.api_key` from portable JSON backups and recorded the excluded sensitive setting in backup metadata. This prevents the Met Office credential being exported as readable plaintext.
- Restore now retains an already-configured API key on the destination device; if none exists, the user must re-enter it after restoring. Other settings and wallpaper/theme restoration remain unchanged.
- Updated the backup/restore settings explanation so the credential behaviour is clear before export or restore.
- Static checks verified the sensitive-key exclusion, metadata declaration, preservation of an existing local key, and user-facing explanation. This is not encryption: the key is intentionally omitted rather than transported. Android compilation and device restore testing remain outstanding.


## 13 November 2026 — backup integrity validation
- Increased the portable backup schema to version 2 and added SHA-256 checksums for each embedded wallpaper image. Restore verifies each checksum before writing the asset, detecting accidental corruption or image-data tampering.
- Restricted embedded image types to JPEG, PNG and WebP, checks the image signature against the declared MIME type, and bounds encoded image size before Base64 decoding.
- Added limits for the number of embedded backgrounds and settings to reject oversized or abusive backup structures before preferences are changed.
- Older schema-version-1 backups are rejected explicitly rather than being silently accepted without the new integrity guarantees.
- Static source assertions passed for checksum generation/verification, image signature validation, bounded Base64 input, count limits and schema version. No Android build or device restore test has been performed.


## 13 November 2026 — settings-to-renderer audit

- Corrected the daily forecast setting description from five days to six, matching the renderer's six-column output.
- Added warning banner placement and warning-detail visibility to the explicit theme appearance key allowlist. Met Office feed URL and API key remain device-specific and are not copied into theme profiles.
- Static source assertions verified both changes and verified that the credential keys are not in the theme appearance allowlist.
- This is source-level verification only; Android build and on-device Projectivy behaviour remain unverified.

## 13 November 2026 — weather-data validity audit
- Current temperature, apparent temperature, wind speed and wind direction are now required finite numeric readings; missing/invalid mandatory current values produce a clear fetch/parse failure rather than a plausible-looking zero.
- Humidity is accepted only in the physical 0–100% range; unavailable humidity remains the existing unavailable sentinel. Pressure is now marked unavailable when missing, non-finite or non-positive instead of converting NaN to 0 hPa.
- Daily forecast entries with missing/non-finite highs or lows, invalid weather codes, or invalid dates are omitted. A fetch with no valid daily forecast fails explicitly.
- Hourly entries with invalid temperature/weather code are omitted. Missing or out-of-range precipitation probability is represented as unavailable and no longer rendered as “0% rain”.
- Eight targeted source assertions passed. These are static checks only; no live API response, Android compilation, or Projectivy rendering has been tested in this environment.

## 13 November 2026 — refresh and offline resilience audit
- Added a ten-minute minimum interval for routine Projectivy time-elapsed refresh events, avoiding unnecessary repeated Open-Meteo requests while the launcher polls the provider.
- Explicit refresh actions, settings changes, and launcher idle-mode transitions bypass the throttle so user-requested changes are applied immediately.
- The successful-refresh timestamp is only persisted after a new wallpaper image is successfully published. A failed weather request leaves the last successfully published wallpaper in place; the throttle does not prevent the first refresh when no wallpaper exists.
- Seven targeted source assertions passed for refresh throttling, forced refresh paths, success timestamp persistence and last-image retention. No live network test, Android build, or Projectivy runtime test has been performed.

## Location-change refresh correctness (source updated; not build/device verified)
- The weather refresh throttle now compares the requested latitude, longitude and location label with the last successfully rendered location.
- A changed location bypasses the normal ten-minute interval even when the refresh arrives through Projectivy's routine provider polling path. This complements the existing forced refresh from the settings save action and Projectivy preference updates.
- The location key is recorded only after a setup or weather wallpaper has been successfully published, so a failed fetch does not mark the new location as rendered.
- Coordinates are normalised to six decimal places for comparison. The display label is included so a label-only change is reflected on the wallpaper too.
- Six targeted source assertions passed. This has not been compiled or exercised on an Android TV; live weather and Projectivy event behaviour remain unverified.


## 13 November 2026 — severe-weather warning cache isolation
- Aligned the warning-cache coordinate precision with the wallpaper service's six-decimal location identity. The previous four-decimal cache scope could reuse alerts for a nearby but different configured point.
- Replaced the short 32-bit string hash cache suffix with a SHA-256 digest of the feed URL and exact normalised coordinates, avoiding practical key collisions between different warning-feed/location scopes.
- Existing cached entries remain isolated under their old keys and naturally age out; they are not read by the new location-scoped key.
- Static checks verified six-decimal scope construction, SHA-256 key generation and absence of the previous `scope.hashCode()` implementation. This has not been exercised against live Met Office data or on Android.


## Warning validity and future-onset handling (source updated; not build/device verified)
- Warning filtering now requires parseable start and end timestamps and rejects invalid intervals where the end is not after the start.
- Announced warnings whose start is still in the future remain visible until their end time, allowing the renderer to show its “Starts in” countdown rather than hiding them prematurely.
- Cached and freshly fetched warnings use the same validity filter and are sorted RED, AMBER, YELLOW, then by start time.
- Cache freshness now requires a non-negative age within the refresh interval; a device clock moving backwards no longer makes a future-dated cache entry appear fresh indefinitely.
- Static source checks only; actual Met Office payload timestamp formats, compilation and Projectivy rendering remain unverified.


## 13 November 2026 — warning countdown boundary accuracy
- Changed warning countdown rounding from floor to ceiling minutes. A warning with less than one minute remaining (or until onset) now displays `1m` rather than `0m` while the boundary is still in the future.
- Countdown calculation continues to use the renderer's supplied timestamp, keeping the frame internally consistent.
- Static source assertions verified ceiling-minute arithmetic and removal of the floor-based `toMinutes()` calculation.
- Important limitation found during this audit: warning rotation is calculated in 20-second slots when a frame is rendered, but the service's ordinary weather refresh is throttled to ten minutes. Unless Projectivy independently requests a new frame, multiple warnings will not visibly rotate every 20 seconds and countdown text will not tick live. A separate cached-weather redraw/event mechanism is still needed; do not describe rotation or countdown as live until that is implemented and tested.

## 13 November 2026 — Separate display refresh from weather downloads
- Converted the service worker to a single-thread scheduled executor and added a 20-second display refresh tick.
- Added an in-memory `WallpaperWeather` cache scoped to the exact requested location key. Display-only refreshes reuse this weather snapshot and redraw the composition, allowing warning rotation/countdowns to advance without a routine Open-Meteo fetch.
- Display-only redraws do not advance `internal.last_successful_refresh`; the ten-minute weather-fetch throttle therefore remains meaningful.
- The warning repository retains its independent 15-minute cache, so the 20-second redraw tick does not imply a 20-second Met Office network poll.
- The executor is stopped when the service is destroyed.
- Static source assertions passed for scheduling, location-scoped cache reuse, bypassing the weather throttle only for display redraw, preserving the weather-fetch timestamp, warning cache interval, and executor shutdown.
- Not yet verified: Android compilation, Projectivy event/broadcast behaviour, background service lifetime on TV firmware, and actual on-screen 20-second warning rotation. The in-memory weather snapshot is lost if Android kills the service; the first display tick after recreation may need to fetch weather again.

## 13 November 2026 — service restart and weather snapshot recovery
- Added app-private persistence for the last successfully fetched weather snapshot, including current readings, six-day forecast, hourly values and sun times. Non-finite optional floating-point values are represented as JSON null rather than invalid JSON numbers.
- Snapshot is stored with the exact six-decimal location identity and restored only with that identity; malformed snapshots are discarded safely.
- Service creation now attempts a normal refresh and schedules the 20-second redraw path. When a recovered snapshot is available, the redraw reuses it; the weather-fetch throttle now requires a usable same-location snapshot, so a missing/corrupt snapshot triggers a recovery fetch rather than leaving the service without weather indefinitely.
- The existing successful weather-fetch timestamp remains separate and is not advanced by display-only redraws. The last published wallpaper file remains in app-private storage across ordinary service recreation.
- Nine targeted source assertions passed. JSON round-trip, Android compilation, service lifecycle behaviour and Projectivy TV runtime remain untested because Android SDK/Gradle tooling is unavailable in this environment.

## 2026-11-13 baseline rendering memory-safety pass
- Reprioritised work around the screenshot-faithful core wallpaper: background, red neon panel, current weather, six-day forecast and clock/date. Deferred additional feature expansion until the basic plugin is buildable and visually verified.
- Audited service bitmap ownership. The selected background bitmap is now recycled in `finally` blocks in both the setup-render path and live-weather render path, including when Canvas rendering throws. This prevents decoded background bitmaps being retained after a failed render.
- `publishWallpaper` already recycles the generated output bitmap in its own `finally` block after PNG encoding/rename handling.
- This is a source-level memory ownership fix only. It has not been compiled, profiled on Android TV, or tested with Projectivy. The fixed 1536x626 render target and screenshot fidelity still require an actual device/emulator comparison.

## 2026-11-13 — reproducible Android build route
- Confirmed this working environment contains Java 21 but no Gradle executable/distribution, no Gradle wrapper JAR/scripts, and no Android SDK platform `android.jar`; a local Android build cannot be run here.
- Added `.github/workflows/android-build.yml` to build `:app:assembleDebug` on GitHub Actions using Java 17, Android platform 35/build-tools 35.0.0 and Gradle 8.11.1 (compatible with Android Gradle Plugin 8.9.2). The workflow uploads the debug APK as an artifact only after a successful build.
- This establishes a hosted build path but is not evidence that the project compiles. The workflow must run in the actual GitHub repository before any build-success claim can be made. A successful debug APK is not a signed release APK.


## 2026-11-13 — baseline service bitmap ownership follow-up
- A fresh lifecycle audit found the synchronous setup-wallpaper path recycled its decoded background only after a successful render. If `renderSetup` threw, that bitmap could remain allocated while the fallback path ran.
- Moved background recycling into a `finally` block, matching the live-weather rendering path. The output bitmap remains owned by `publishWallpaper`, which recycles it after PNG publication.
- Static source check verified the setup bitmap is released in `finally` and is not recycled twice on the success path. This is not an Android memory-profile or runtime test.


## 2026-11-13 renderer output-bitmap failure cleanup
- Audited both renderer entry points (`render` and `renderSetup`). Their output bitmaps were returned on success but could remain allocated if drawing threw before return.
- Added exception-safe cleanup around both drawing paths: failed renders recycle their partially composed output bitmap before rethrowing; successful renders still transfer ownership to the service for PNG publication.
- This is a source-level resource-lifecycle correction. No Android compilation, allocation profiling, or Projectivy TV test has been performed.
