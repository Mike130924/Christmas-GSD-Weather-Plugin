package com.christmasgsd.weather

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.IBinder
import android.util.Log
import org.json.JSONObject
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperProviderContract
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType
import com.christmasgsd.weather.data.OpenMeteoRepository
import com.christmasgsd.weather.alerts.UkWeatherWarningsRepository
import com.christmasgsd.weather.render.WallpaperRenderConfig
import com.christmasgsd.weather.render.WarningDisplay
import com.christmasgsd.weather.render.WeatherWallpaperRenderer
import com.christmasgsd.weather.render.WallpaperPalette
import com.christmasgsd.weather.render.BackgroundMode
import com.christmasgsd.weather.render.ClockStyle
import com.christmasgsd.weather.render.CurrentWeather
import com.christmasgsd.weather.render.ForecastDay
import com.christmasgsd.weather.render.HourlyForecast
import com.christmasgsd.weather.render.SunTimes
import com.christmasgsd.weather.render.WallpaperWeather
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class WeatherWallpaperService : Service() {
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var cachedWeather: com.christmasgsd.weather.render.WallpaperWeather? = null
    @Volatile private var cachedWeatherLocationKey: String? = null
    private val renderer by lazy { WeatherWallpaperRenderer() }
    private val repository by lazy { OpenMeteoRepository() }
    private val warningsRepository by lazy { UkWeatherWarningsRepository() }
    @Volatile private var launcherIsIdle: Boolean = false

    override fun onCreate() {
        super.onCreate()
        // Projectivy may open the provider URI immediately after getWallpapers().
        // Publish a valid setup composition synchronously before exposing that URI;
        // the asynchronous live-weather refresh can replace it afterwards.
        runCatching {
            val background = loadSelectedBackground()
            val initial = try {
                val mode = when (getSharedPreferences(PREFS, MODE_PRIVATE).getString("background_mode", "FILL_CROP")) {
                    "FIT" -> BackgroundMode.FIT
                    "STRETCH" -> BackgroundMode.STRETCH
                    "CENTRE" -> BackgroundMode.CENTRE
                    else -> BackgroundMode.FILL_CROP
                }
                renderer.renderSetup(background, OUTPUT_WIDTH, OUTPUT_HEIGHT, mode)
            } finally {
                if (!background.isRecycled) background.recycle()
            }
            publishWallpaper(initial)
        }.onFailure { error ->
            Log.e(TAG, "Could not prepare initial wallpaper; creating bundled fallback", error)
            ensureFallbackWallpaper()
        }
        // Recover the last good snapshot after a process/service restart. The refresh
        // timestamp remains persisted separately, so recovery does not force a download.
        restoreWeatherSnapshot()
        // Re-render cached weather periodically so warning rotation and countdowns
        // advance without forcing a new Open-Meteo request. If no snapshot survived,
        // perform a normal refresh instead of repeatedly attempting display-only work.
        refreshWallpaper()
        executor.scheduleWithFixedDelay({
            refreshWallpaper(displayOnly = cachedWeather != null)
        }, DISPLAY_REFRESH_INTERVAL_MILLIS, DISPLAY_REFRESH_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH) refreshWallpaper(force = true)
        return START_NOT_STICKY
    }

    private val binder = object : IWallpaperProviderService.Stub() {
        override fun getWallpapers(event: Event?): List<Wallpaper> {
            when (event) {
                is Event.LauncherIdleModeChanged -> {
                    launcherIsIdle = event.isIdle
                    if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("layout.full_screen_when_idle", true)) refreshWallpaper(force = true)
                }
                is Event.TimeElapsed, null -> refreshWallpaper()
                else -> Unit
            }
            val uri = Uri.parse("content://$packageName.wallpaperimages/current.png").toString()
            return listOf(Wallpaper(uri, WallpaperType.IMAGE, title = "Christmas GSD Weather"))
        }

        override fun getPreferences(): String {
            val json = JSONObject()
            getSharedPreferences(PREFS, MODE_PRIVATE).all.forEach { (key, value) -> json.put(key, value) }
            return json.toString()
        }

        override fun setPreferences(params: String) {
            runCatching {
                val json = JSONObject(params)
                val editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                json.keys().forEach { key ->
                    when (val value = json.get(key)) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Double -> editor.putFloat(key, value.toFloat())
                        is String -> editor.putString(key, value)
                        JSONObject.NULL -> editor.remove(key)
                    }
                }
                editor.apply()
                refreshWallpaper(force = true)
            }.onFailure { Log.e(TAG, "Could not apply Projectivy preferences", it) }
        }
    }

    private fun refreshWallpaper(force: Boolean = false, displayOnly: Boolean = false) {
        executor.execute {
            try {
                val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                val lastSuccessfulRefresh = prefs.getLong("internal.last_successful_refresh", 0L)
                val wallpaperExists = File(filesDir, "wallpaper/current.png").isFile
                val lat = prefs.getString("latitude", null)?.toDoubleOrNull()
                val lon = prefs.getString("longitude", null)?.toDoubleOrNull()
                val locationName = prefs.getString("location_name", "") ?: ""
                val requestedLocationKey = if (lat != null && lon != null)
                    "${"%.6f".format(java.util.Locale.ROOT, lat)}|${"%.6f".format(java.util.Locale.ROOT, lon)}|$locationName"
                else "location-not-configured"
                val lastRenderedLocationKey = prefs.getString("internal.last_rendered_location_key", null)
                val locationChanged = lastRenderedLocationKey != requestedLocationKey
                val hasUsableSnapshot = cachedWeather != null && cachedWeatherLocationKey == requestedLocationKey
                if (!displayOnly && !force && !locationChanged && wallpaperExists && hasUsableSnapshot &&
                    lastSuccessfulRefresh > 0L && System.currentTimeMillis() - lastSuccessfulRefresh < MIN_REFRESH_INTERVAL_MILLIS) {
                    return@execute
                }
                if (lat == null || lon == null) {
                    // Keep the selected custom/bundled background visible and explain
                    // how to finish setup; do not silently replace it with a bare image.
                    val background = loadSelectedBackground()
                    val mode = when (prefs.getString("background_mode", "FILL_CROP")) {
                        "FIT" -> BackgroundMode.FIT
                        "STRETCH" -> BackgroundMode.STRETCH
                        "CENTRE" -> BackgroundMode.CENTRE
                        else -> BackgroundMode.FILL_CROP
                    }
                    val setupImage = try {
                        renderer.renderSetup(background, OUTPUT_WIDTH, OUTPUT_HEIGHT, mode)
                    } finally {
                        if (!background.isRecycled) background.recycle()
                    }
                    publishWallpaper(setupImage)
                    prefs.edit()
                        .putLong("internal.last_successful_refresh", System.currentTimeMillis())
                        .putString("internal.last_rendered_location_key", requestedLocationKey)
                        .apply()
                    sendUpdateBroadcast()
                    return@execute
                }
                val weather = if (displayOnly && cachedWeather != null && cachedWeatherLocationKey == requestedLocationKey) {
                    cachedWeather!!
                } else {
                    repository.fetch(lat, lon, locationName).also {
                        cachedWeather = it
                        cachedWeatherLocationKey = requestedLocationKey
                        persistWeatherSnapshot(it, requestedLocationKey)
                    }
                }
                val background = loadSelectedBackground()
                fun color(key: String, fallback: Int): Int = runCatching {
                    Color.parseColor(prefs.getString(key, null) ?: return@runCatching fallback)
                }.getOrDefault(fallback)
                val palette = WallpaperPalette(
                    panel = color("palette.panel", Color.argb(222, 15, 17, 23)),
                    border = color("palette.border", Color.rgb(255, 42, 58)),
                    glow = color("palette.glow", Color.rgb(255, 25, 45)),
                    primaryText = color("palette.primary_text", Color.WHITE),
                    secondaryText = color("palette.secondary_text", Color.rgb(205, 211, 222)),
                    temperature = color("palette.temperature", Color.WHITE),
                    weatherIcon = color("palette.weather_icon", Color.WHITE),
                    divider = color("palette.divider", Color.argb(95, 255, 255, 255)),
                    festive = color("palette.festive", Color.rgb(35, 160, 91)),
                    snow = color("palette.snow", Color.WHITE),
                )
                val clockStyle = ClockStyle(
                    showTime = prefs.getBoolean("clock.visible", true),
                    showDate = prefs.getBoolean("date.visible", true),
                    use24Hour = prefs.getBoolean("clock.24_hour", true),
                    datePattern = prefs.getString("date.pattern", "EEE d MMM") ?: "EEE d MMM",
                    timeColor = prefs.getInt("clock.color", Color.WHITE),
                    dateColor = prefs.getInt("date.color", Color.LTGRAY),
                    timeAlpha = (prefs.getInt("clock.opacity", 100).coerceIn(0, 100) * 255 / 100),
                    dateAlpha = (prefs.getInt("date.opacity", 90).coerceIn(0, 100) * 255 / 100),
                    timeSize = prefs.getInt("clock.size", 34).coerceIn(10, 120).toFloat(),
                    dateSize = prefs.getInt("date.size", 18).coerceIn(8, 72).toFloat(),
                    timeBold = prefs.getBoolean("clock.bold", true),
                    dateBold = prefs.getBoolean("date.bold", false),
                    datePosition = prefs.getString("date.position", "below_time") ?: "below_time",
                )
                val activeWarnings = if (prefs.getBoolean("weather.show_severe_alerts", true)) {
                    val feedUrl = prefs.getString("alerts.feed_url", "").orEmpty()
                    val apiKey = prefs.getString("alerts.api_key", "").orEmpty()
                    if (feedUrl.isNotBlank() && apiKey.isNotBlank()) runCatching {
                        warningsRepository.fetchCachedForLocation(
                            feedUrl, apiKey, lat, lon, prefs,
                            refreshIntervalMillis = 15 * 60 * 1000L,
                        ).sortedByDescending { when (it.severity.uppercase()) { "RED" -> 3; "AMBER" -> 2; else -> 1 } }
                    }.onFailure { Log.w(TAG, "UK warning check failed; continuing without alert", it) }.getOrDefault(emptyList()) else emptyList()
                } else emptyList()
                val renderConfig = WallpaperRenderConfig(
                    palette = palette,
                    clock = clockStyle,
                    backgroundMode = when (prefs.getString("background_mode", "FILL_CROP")) {
                        "FIT" -> BackgroundMode.FIT
                        "STRETCH" -> BackgroundMode.STRETCH
                        "CENTRE" -> BackgroundMode.CENTRE
                        else -> BackgroundMode.FILL_CROP
                    },
                    burnInProtection = prefs.getBoolean("layout.burn_in_protection", true),
                    appRowStartPercent = prefs.getInt("layout.app_row_start_percent", 70),
                    fullScreenWhenIdle = prefs.getBoolean("layout.full_screen_when_idle", true) && launcherIsIdle,
                    showUpdated = prefs.getBoolean("weather.show_updated", false),
                    showHourly = prefs.getBoolean("weather.show_hourly", false),
                    showDaily = prefs.getBoolean("weather.show_daily", true),
                    showExtraStats = prefs.getBoolean("weather.show_extra_stats", false),
                    showSunTimes = prefs.getBoolean("weather.show_sun_times", false),
                    readoutScale = prefs.getString("readout.scale", "1.0")?.toFloatOrNull() ?: 1.0f,
                    warningHeadline = activeWarnings.firstOrNull()?.headline.orEmpty(),
                    warningSeverity = activeWarnings.firstOrNull()?.severity.orEmpty(),
                    warnings = activeWarnings.map { warning ->
                        WarningDisplay(warning.headline, warning.weatherType, warning.severity,
                            warning.validFrom, warning.validTo, warning.description)
                    },
                    warningDisplayMode = prefs.getString("alerts.display_mode", "separate_banner") ?: "separate_banner",
                    showWarningDetails = prefs.getBoolean("alerts.show_details", true),
                )
                val rendered = try {
                    renderer.render(background, weather, OUTPUT_WIDTH, OUTPUT_HEIGHT, renderConfig)
                } finally {
                    if (!background.isRecycled) background.recycle()
                }
                publishWallpaper(rendered)
                if (!displayOnly) {
                    prefs.edit()
                        .putLong("internal.last_successful_refresh", System.currentTimeMillis())
                        .putString("internal.last_rendered_location_key", requestedLocationKey)
                        .apply()
                }
                sendUpdateBroadcast()
            } catch (error: Exception) {
                Log.e(TAG, "Wallpaper refresh failed; retaining last successful image", error)
                ensureFallbackWallpaper()
            }
        }
    }


    /** Store only the last successful weather payload in app-private preferences. */
    private fun persistWeatherSnapshot(weather: WallpaperWeather, locationKey: String) {
        runCatching {
            val current = weather.current
            val json = JSONObject().apply {
                put("temperature", current.temperature); put("condition", current.condition)
                put("weatherCode", current.weatherCode); put("feelsLike", current.feelsLike)
                put("high", current.high); put("low", current.low); put("windKph", current.windKph)
                put("windDirection", current.windDirection); put("humidity", current.humidity)
                put("pressureHpa", current.pressureHpa); put("dewPoint", current.dewPoint)
                put("uvIndex", current.uvIndex.takeIf { it.isFinite() } ?: JSONObject.NULL)
                put("visibilityKm", current.visibilityKm.takeIf { it.isFinite() } ?: JSONObject.NULL)
                put("location", weather.location); put("updatedAt", weather.updatedAt)
                put("forecast", JSONArray().apply { weather.forecast.forEach { day ->
                    put(JSONObject().put("day", day.dayLabel).put("code", day.weatherCode).put("high", day.high).put("low", day.low))
                } })
                put("hourly", JSONArray().apply { weather.hourly.forEach { hour ->
                    put(JSONObject().put("hour", hour.hour).put("temperature", hour.temperature).put("rain", hour.rainChance).put("code", hour.weatherCode))
                } })
                weather.sunTimes?.let { sun -> put("sun", JSONObject().put("sunrise", sun.sunrise).put("sunset", sun.sunset)
                    .put("daylight", sun.daylightMinutes).put("isDaylight", sun.isDaylight).put("remaining", sun.daylightRemainingMinutes)) }
            }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("internal.weather_snapshot", json.toString())
                .putString("internal.weather_snapshot_location", locationKey).apply()
        }.onFailure { Log.w(TAG, "Could not persist weather snapshot", it) }
    }

    /** Recover a snapshot only for the exact location it was fetched for. */
    private fun restoreWeatherSnapshot() {
        runCatching {
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            val json = prefs.getString("internal.weather_snapshot", null)?.let(::JSONObject) ?: return
            val locationKey = prefs.getString("internal.weather_snapshot_location", null) ?: return
            val forecastJson = json.getJSONArray("forecast")
            val hourlyJson = json.optJSONArray("hourly") ?: JSONArray()
            val forecast = (0 until forecastJson.length()).map { i -> forecastJson.getJSONObject(i).let {
                ForecastDay(it.getString("day"), it.getInt("code"), it.getInt("high"), it.getInt("low"))
            } }
            val hourly = (0 until hourlyJson.length()).map { i -> hourlyJson.getJSONObject(i).let {
                HourlyForecast(it.getString("hour"), it.getInt("temperature"), it.getInt("rain"), it.getInt("code"))
            } }
            val sun = json.optJSONObject("sun")?.let {
                SunTimes(it.getString("sunrise"), it.getString("sunset"), it.getLong("daylight"),
                    it.getBoolean("isDaylight"), it.getLong("remaining"))
            }
            val current = CurrentWeather(json.getInt("temperature"), json.getString("condition"),
                json.getInt("weatherCode"), json.getInt("feelsLike"), json.getInt("high"), json.getInt("low"),
                json.getInt("windKph"), json.optString("windDirection"), json.optInt("humidity", -1),
                json.optInt("pressureHpa", Int.MIN_VALUE), json.optInt("dewPoint", Int.MIN_VALUE),
                json.optDouble("uvIndex", Double.NaN).toFloat(), json.optDouble("visibilityKm", Double.NaN).toFloat())
            cachedWeather = WallpaperWeather(current, forecast, json.optString("location"),
                json.optLong("updatedAt"), hourly, sun)
            cachedWeatherLocationKey = locationKey
        }.onFailure {
            cachedWeather = null
            cachedWeatherLocationKey = null
            Log.w(TAG, "Discarding invalid persisted weather snapshot", it)
        }
    }

    private fun publishWallpaper(bitmap: Bitmap) {
        val output = File(filesDir, "wallpaper/current.png")
        output.parentFile?.mkdirs()
        val temporary = File(output.parentFile, "current.png.tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "PNG encoding failed" }
                stream.fd.sync()
            }
            if (!temporary.renameTo(output)) throw IllegalStateException("Could not publish refreshed wallpaper")
        } finally {
            bitmap.recycle()
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun loadSelectedBackground(): Bitmap {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val uriText = prefs.getString("background_uri", null)
        if (!uriText.isNullOrBlank()) {
            runCatching { contentResolver.openInputStream(Uri.parse(uriText))?.use { stream -> BitmapFactory.decodeStream(stream) } }
                .getOrNull()?.let { return it }
        }
        return BitmapFactory.decodeResource(resources, R.drawable.christmas_gsd_background)
            ?: throw IllegalStateException("Bundled background could not be decoded")
    }

    private fun ensureFallbackWallpaper() {
        val output = File(filesDir, "wallpaper/current.png")
        if (output.isFile) return
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.christmas_gsd_background) ?: return
        output.parentFile?.mkdirs()
        FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun sendUpdateBroadcast() {
        sendBroadcast(
            Intent(WallpaperProviderContract.ACTION_WALLPAPER_PROVIDER_UPDATED)
                .setPackage("com.spocky.projengmenu")
                .putExtra(WallpaperProviderContract.EXTRA_PROVIDER_ID, PROVIDER_UUID)
                .putExtra(
                    WallpaperProviderContract.EXTRA_UPDATE_REASON,
                    WallpaperProviderContract.UpdateReason.DATA_CHANGED,
                ),
        )
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val ACTION_REFRESH = "com.christmasgsd.weather.action.REFRESH"
        private const val TAG = "ChristmasGSDWeather"
        private const val PREFS = "weather"
        private const val OUTPUT_WIDTH = 1536
        private const val OUTPUT_HEIGHT = 626
        private const val MIN_REFRESH_INTERVAL_MILLIS = 10 * 60 * 1000L
        private const val DISPLAY_REFRESH_INTERVAL_MILLIS = 20 * 1000L
        private const val PROVIDER_UUID = "d9b06d63-17ab-4e4c-a4d6-4f0d3c4f81d2"
    }
}
