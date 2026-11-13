package com.christmasgsd.weather.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.min

/** Weather values supplied by the data layer. Temperatures are Celsius. */
data class CurrentWeather(
    val temperature: Int,
    val condition: String,
    val weatherCode: Int,
    val feelsLike: Int,
    val high: Int,
    val low: Int,
    val windKph: Int,
    val windDirection: String = "",
    val humidity: Int = -1,
    val pressureHpa: Int = Int.MIN_VALUE,
    val dewPoint: Int = Int.MIN_VALUE,
    val uvIndex: Float = Float.NaN,
    val visibilityKm: Float = Float.NaN,
)

data class ForecastDay(
    val dayLabel: String,
    val weatherCode: Int,
    val high: Int,
    val low: Int,
)

data class HourlyForecast(val hour: String, val temperature: Int, val rainChance: Int, val weatherCode: Int)

data class SunTimes(val sunrise: String, val sunset: String, val daylightMinutes: Long, val isDaylight: Boolean, val daylightRemainingMinutes: Long = 0L)

data class WallpaperWeather(
    val current: CurrentWeather,
    val forecast: List<ForecastDay>,
    val location: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val hourly: List<HourlyForecast> = emptyList(),
    val sunTimes: SunTimes? = null,
)

data class WallpaperPalette(
    val panel: Int = Color.argb(222, 15, 17, 23),
    val border: Int = Color.rgb(255, 42, 58),
    val glow: Int = Color.rgb(255, 25, 45),
    val primaryText: Int = Color.WHITE,
    val secondaryText: Int = Color.rgb(205, 211, 222),
    val temperature: Int = Color.WHITE,
    val weatherIcon: Int = Color.rgb(255, 255, 255),
    val divider: Int = Color.argb(95, 255, 255, 255),
    val festive: Int = Color.rgb(35, 160, 91),
    val snow: Int = Color.WHITE,
    val borderAlpha: Int = 255,
    val glowAlpha: Int = 180,
    val glowRadius: Float = 18f,
)

enum class BackgroundMode { FILL_CROP, FIT, STRETCH, CENTRE }

data class ClockStyle(
    val showTime: Boolean = true,
    val showDate: Boolean = true,
    val use24Hour: Boolean = true,
    val datePattern: String = "EEE d MMMM",
    val timeColor: Int = Color.WHITE,
    val dateColor: Int = Color.LTGRAY,
    val timeAlpha: Int = 255,
    val dateAlpha: Int = 230,
    val timeSize: Float = 42f,
    val dateSize: Float = 21f,
    val timeBold: Boolean = true,
    val dateBold: Boolean = false,
    val datePosition: String = "below_time",
)

data class WarningDisplay(
    val headline: String,
    val weatherType: String,
    val severity: String,
    val validFrom: String,
    val validTo: String,
    val description: String = "",
)

data class WallpaperRenderConfig(
    val palette: WallpaperPalette = WallpaperPalette(),
    val clock: ClockStyle = ClockStyle(),
    val backgroundMode: BackgroundMode = BackgroundMode.FILL_CROP,
    val showLocation: Boolean = true,
    val showWindDirection: Boolean = true,
    val burnInProtection: Boolean = true,
    val appRowStartPercent: Int = 70,
    val fullScreenWhenIdle: Boolean = true,
    val showUpdated: Boolean = false,
    val showHourly: Boolean = false,
    val showDaily: Boolean = true,
    val showExtraStats: Boolean = false,
    val showSunTimes: Boolean = false,
    /** Uniform scale for weather readouts; panel and clock remain unchanged. */
    val readoutScale: Float = 1.0f,
    val warningHeadline: String = "",
    val warningSeverity: String = "",
    val warnings: List<WarningDisplay> = emptyList(),
    /** inside_panel or separate_banner */
    val warningDisplayMode: String = "separate_banner",
    val showWarningDetails: Boolean = true,
)

/**
 * Draws the live weather layer over a user-selected wallpaper bitmap.
 * Layout coordinates use the supplied 1536x626 design reference and scale uniformly
 * to the actual Projectivy output dimensions. No generated preview is used as the renderer.
 */
class WeatherWallpaperRenderer {
    companion object {
        const val DESIGN_WIDTH = 1536f
        const val DESIGN_HEIGHT = 626f
        private const val WARNING_ROTATION_MILLIS = 20_000L
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun render(
        background: Bitmap,
        weather: WallpaperWeather,
        width: Int,
        height: Int,
        config: WallpaperRenderConfig = WallpaperRenderConfig(),
        now: Date = Date(),
    ): Bitmap {
        require(width > 0 && height > 0) { "Render dimensions must be positive" }
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
        val canvas = Canvas(output)
        val sx = width / DESIGN_WIDTH
        val sy = height / DESIGN_HEIGHT
        drawBackground(canvas, background, width, height, config.backgroundMode)
        canvas.save()
        canvas.scale(sx, sy)
        // Treat the configured app-row start as a vertical composition anchor.
        // 70% is the reference layout; moving the row changes the overlay's vertical
        // placement proportionally while keeping the artwork within the 626px canvas.
        val rowOffset = ((config.appRowStartPercent.coerceIn(0, 100) - 70) * 1.15f)
        val burnOffset = if (config.burnInProtection) {
            val phase = (now.time / 60_000L).toInt() % 4
            when (phase) { 0 -> -2.5f; 1 -> 2.5f; 2 -> 2.5f; else -> -2.5f }
        } else 0f
        canvas.translate(0f, rowOffset + burnOffset)
        drawPanel(canvas, config.palette, config.fullScreenWhenIdle)
        drawClock(canvas, config.clock, now, config.palette)
        // Draw decorative trim first: an active warning must remain legible above it.
        drawFestiveTrim(canvas, config.palette)
        drawWarningBanner(canvas, config, now)
        canvas.save()
        val readoutScale = config.readoutScale.coerceIn(0.75f, 1.35f)
        canvas.scale(readoutScale, readoutScale, DESIGN_WIDTH / 2f, 340f)
        drawCurrentWeather(canvas, weather, config)
        val optionalLines = drawOptionalWeather(canvas, weather, config)
        canvas.restore() // Keep the forecast legible and inside the frame at every readout scale.
        if (config.showDaily) {
            // Keep the forecast below optional readouts instead of allowing hourly,
            // astronomy and statistics lines to collide with the forecast heading.
            // Six forecast columns fit the reference width; the forecast itself remains
            // at design scale so enlarging the current readout cannot clip its lower row.
            val requestedForecastTop = 414f + optionalLines * 27f
            val maxForecastTop = 470f - (rowOffset + burnOffset).coerceAtLeast(0f)
            val forecastTop = requestedForecastTop.coerceAtMost(maxForecastTop.coerceAtLeast(360f))
            drawForecast(canvas, weather.forecast.take(6), config.palette, forecastTop)
        }
        canvas.restore()
        return output
        } catch (error: Throwable) {
            if (!output.isRecycled) output.recycle()
            throw error
        }
    }

    /** Renders a useful setup wallpaper when location/weather has not been configured yet. */
    fun renderSetup(
        background: Bitmap,
        width: Int,
        height: Int,
        mode: BackgroundMode = BackgroundMode.FILL_CROP,
    ): Bitmap {
        require(width > 0 && height > 0) { "Render dimensions must be positive" }
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
        val canvas = Canvas(output)
        drawBackground(canvas, background, width, height, mode)
        canvas.save()
        canvas.scale(width / DESIGN_WIDTH, height / DESIGN_HEIGHT)
        val panel = RectF(34f, 34f, DESIGN_WIDTH - 34f, DESIGN_HEIGHT - 34f)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(218, 12, 16, 24) }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 42, 58)
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            setShadowLayer(16f, 0f, 0f, Color.rgb(255, 25, 45))
        }
        canvas.drawRoundRect(panel, 30f, 30f, fill)
        canvas.drawRoundRect(panel, 30f, 30f, border)
        border.clearShadowLayer()
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val detail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            textSize = 25f
        }
        canvas.drawText("Christmas GSD Weather", 82f, 250f, heading)
        canvas.drawText("Set your location in plugin settings, then save and refresh", 82f, 305f, detail)
        canvas.restore()
        return output
        } catch (error: Throwable) {
            if (!output.isRecycled) output.recycle()
            throw error
        }
    }

    private fun drawBackground(canvas: Canvas, bitmap: Bitmap, width: Int, height: Int, mode: BackgroundMode) {
        val src = Rect(0, 0, bitmap.width, bitmap.height)
        val dst = RectF(0f, 0f, width.toFloat(), height.toFloat())
        when (mode) {
            BackgroundMode.STRETCH -> canvas.drawBitmap(bitmap, src, dst, p)
            BackgroundMode.CENTRE -> {
                val left = (width - bitmap.width) / 2f
                val top = (height - bitmap.height) / 2f
                canvas.drawBitmap(bitmap, left, top, p)
            }
            BackgroundMode.FIT -> {
                val scale = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
                val w = bitmap.width * scale
                val h = bitmap.height * scale
                val fit = RectF((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
                canvas.drawBitmap(bitmap, src, fit, p)
            }
            BackgroundMode.FILL_CROP -> {
                val scale = maxOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
                val w = width / scale
                val h = height / scale
                val crop = Rect(
                    ((bitmap.width - w) / 2f).toInt().coerceAtLeast(0),
                    ((bitmap.height - h) / 2f).toInt().coerceAtLeast(0),
                    ((bitmap.width + w) / 2f).toInt().coerceAtMost(bitmap.width),
                    ((bitmap.height + h) / 2f).toInt().coerceAtMost(bitmap.height),
                )
                canvas.drawBitmap(bitmap, crop, dst, p)
            }
        }
    }

    private fun drawPanel(canvas: Canvas, palette: WallpaperPalette, fullScreenWhenIdle: Boolean) {
        // Idle mode deliberately uses the full canvas. Normal launcher mode keeps
        // a wider safe inset so the wallpaper panel does not crowd launcher chrome.
        val inset = if (fullScreenWhenIdle) 0f else 34f
        val panel = RectF(inset, inset, DESIGN_WIDTH - inset, DESIGN_HEIGHT - inset)
        p.style = Paint.Style.FILL
        p.color = palette.panel
        p.clearShadowLayer()
        canvas.drawRoundRect(panel, 34f, 34f, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        p.color = withAlpha(palette.glow, palette.glowAlpha)
        p.setShadowLayer(palette.glowRadius, 0f, 0f, withAlpha(palette.glow, palette.glowAlpha))
        canvas.drawRoundRect(panel, 34f, 34f, p)
        p.clearShadowLayer()
        p.strokeWidth = 3f
        p.color = withAlpha(palette.border, palette.borderAlpha)
        canvas.drawRoundRect(panel, 34f, 34f, p)
        p.style = Paint.Style.FILL
    }

    private fun drawClock(canvas: Canvas, style: ClockStyle, now: Date, palette: WallpaperPalette) {
        val timeFormat = SimpleDateFormat(if (style.use24Hour) "HH:mm" else "h:mm a", Locale.getDefault())
        val dateFormat = runCatching { SimpleDateFormat(style.datePattern, Locale.getDefault()) }
            .getOrElse { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
        val timeText = timeFormat.format(now)
        val dateText = dateFormat.format(now)
        val right = 1468f
        val baseY = 91f
        when (style.datePosition) {
            "above_time" -> {
                if (style.showDate) text(canvas, dateText, right, 55f, style.dateSize, withAlpha(style.dateColor, style.dateAlpha), style.dateBold, Paint.Align.RIGHT)
                if (style.showTime) text(canvas, timeText, right, 105f, style.timeSize, withAlpha(style.timeColor, style.timeAlpha), style.timeBold, Paint.Align.RIGHT)
            }
            "beside_time" -> {
                if (style.showTime && style.showDate) {
                    // Measure the time with the same typeface/size used by text(), then
                    // place the date immediately to its left on the same baseline.
                    p.clearShadowLayer()
                    p.textSize = style.timeSize
                    p.typeface = if (style.timeBold) Typeface.create("sans-serif", Typeface.BOLD)
                        else Typeface.create("sans-serif", Typeface.NORMAL)
                    val timeWidth = p.measureText(timeText)
                    text(canvas, dateText, right - timeWidth - 22f, baseY,
                        style.dateSize, withAlpha(style.dateColor, style.dateAlpha), style.dateBold, Paint.Align.RIGHT)
                    text(canvas, timeText, right, baseY, style.timeSize,
                        withAlpha(style.timeColor, style.timeAlpha), style.timeBold, Paint.Align.RIGHT)
                } else {
                    if (style.showTime) text(canvas, timeText, right, baseY, style.timeSize, withAlpha(style.timeColor, style.timeAlpha), style.timeBold, Paint.Align.RIGHT)
                    if (style.showDate) text(canvas, dateText, right, 125f, style.dateSize, withAlpha(style.dateColor, style.dateAlpha), style.dateBold, Paint.Align.RIGHT)
                }
            }
            else -> {
                if (style.showTime) text(canvas, timeText, right, baseY, style.timeSize, withAlpha(style.timeColor, style.timeAlpha), style.timeBold, Paint.Align.RIGHT)
                if (style.showDate) text(canvas, dateText, right, 124f, style.dateSize, withAlpha(style.dateColor, style.dateAlpha), style.dateBold, Paint.Align.RIGHT)
            }
        }
    }

    private fun drawCurrentWeather(canvas: Canvas, data: WallpaperWeather, config: WallpaperRenderConfig) {
        val w = data.current
        val palette = config.palette
        if (config.showLocation && data.location.isNotBlank()) {
            text(canvas, data.location, 78f, 83f, 25f, palette.secondaryText, true)
        }
        drawWeatherIcon(canvas, 245f, 265f, 1.0f, w.weatherCode, palette.weatherIcon)
        text(canvas, "${w.temperature}°", 420f, 280f, 142f, palette.temperature, true)
        text(canvas, w.condition, 435f, 330f, 35f, palette.primaryText, true)

        val details = listOf(
            "FEELS LIKE" to "${w.feelsLike}°",
            "HIGH / LOW" to "${w.high}°  /  ${w.low}°",
            "WIND" to buildString {
                append(w.windKph).append(" km/h")
                if (config.showWindDirection && w.windDirection.isNotBlank()) append("  ").append(w.windDirection)
            },
        )
        val startX = 710f
        val colW = 225f
        details.forEachIndexed { index, item ->
            val x = startX + index * colW
            text(canvas, item.first, x, 230f, 18f, palette.secondaryText, true)
            text(canvas, item.second, x, 274f, 30f, palette.primaryText, true)
        }
        text(canvas, "CURRENT CONDITIONS", 78f, 155f, 18f, palette.secondaryText, true)
    }


    private fun drawOptionalWeather(canvas: Canvas, data: WallpaperWeather, config: WallpaperRenderConfig): Int {
        val palette = config.palette
        var y = 365f
        var lines = 0
        if (config.showUpdated) {
            val stamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(data.updatedAt))
            text(canvas, "Updated $stamp", 1450f, y, 15f, palette.secondaryText, false, Paint.Align.RIGHT)
            y += 27f
            lines++
        }
        if (config.showExtraStats) {
            val bits = mutableListOf<String>()
            if (data.current.humidity >= 0) bits += "Humidity ${data.current.humidity}%"
            if (data.current.dewPoint != Int.MIN_VALUE) bits += "Dew point ${data.current.dewPoint}°"
            if (data.current.pressureHpa != Int.MIN_VALUE) bits += "Pressure ${data.current.pressureHpa} hPa"
            if (data.current.uvIndex.isFinite()) bits += "UV ${"%.1f".format(Locale.getDefault(), data.current.uvIndex)}"
            if (data.current.visibilityKm.isFinite()) bits += "Visibility ${"%.1f".format(Locale.getDefault(), data.current.visibilityKm)} km"
            if (bits.isNotEmpty()) {
                text(canvas, bits.joinToString("   •   "), 78f, y, 18f, palette.secondaryText, false)
                y += 27f
                lines++
            }
        }
        if (config.showSunTimes) {
            data.sunTimes?.let { sun ->
                val rise = sun.sunrise.substringAfter('T', "").take(5).ifBlank { "--:--" }
                val set = sun.sunset.substringAfter('T', "").take(5).ifBlank { "--:--" }
                val hours = sun.daylightMinutes / 60
                val mins = sun.daylightMinutes % 60
                val remainingHours = sun.daylightRemainingMinutes.coerceAtLeast(0L) / 60
                val remainingMins = sun.daylightRemainingMinutes.coerceAtLeast(0L) % 60
                val remainingLabel = if (sun.isDaylight && sun.daylightRemainingMinutes > 0L)
                    "   •   ${remainingHours}h ${remainingMins}m daylight left" else "   •   No daylight remaining"
                text(canvas, "Sunrise $rise   Sunset $set   Daylight ${hours}h ${mins}m$remainingLabel", 78f, y, 18f, palette.secondaryText, false)
                y += 27f
                lines++
            }
        }
        if (config.showHourly && data.hourly.isNotEmpty()) {
            val summary = data.hourly.take(6).joinToString("     ") {
                "${it.hour}  ${it.temperature}°" + if (it.rainChance in 0..100) "  ${it.rainChance}% rain" else ""
            }
            text(canvas, summary, 78f, y, 17f, palette.primaryText, false)
            lines++
        }
        return lines
    }


    private fun drawWarningBanner(canvas: Canvas, config: WallpaperRenderConfig, now: Date) {
        val severityRank = mapOf("RED" to 0, "AMBER" to 1, "YELLOW" to 2)
        val warnings = config.warnings.sortedWith(
            compareBy<WarningDisplay> { severityRank[it.severity.uppercase(Locale.ROOT)] ?: 3 }
                .thenBy { it.validFrom }
        )
        val warning = if (warnings.isNotEmpty()) {
            // Rotate only after sorting, so the most severe warning is always first.
            // Use the render timestamp to keep the entire frame internally consistent.
            val slot = (now.time / WARNING_ROTATION_MILLIS).toInt()
            warnings[Math.floorMod(slot, warnings.size)]
        } else null

        val headline: String
        val severity: String
        val weatherType: String
        val validity: String
        val countdown: String
        val description: String
        if (warning != null) {
            headline = warning.headline
            severity = warning.severity
            weatherType = warning.weatherType
            validity = warningValidityLabel(warning.validFrom, warning.validTo)
            countdown = warningCountdownLabel(warning.validFrom, warning.validTo, now.toInstant())
            description = warning.description
        } else {
            headline = config.warningHeadline.trim()
            if (headline.isEmpty()) return
            severity = config.warningSeverity
            weatherType = ""
            validity = ""
            countdown = ""
            description = ""
        }

        val title = listOf(headline, weatherType).filter { it.isNotBlank() }.joinToString("  •  ")
        val details = if (config.showWarningDetails)
            listOf(validity, countdown, description).filter { it.isNotBlank() }.joinToString("  •  ")
        else ""

        // Separate mode occupies the narrow top safe area. Inside-panel mode uses
        // the open upper-right information lane, clear of the location and clock.
        val inside = config.warningDisplayMode == "inside_panel"
        val bounds = if (inside) RectF(710f, 145f, 1458f, 214f)
                     else RectF(78f, 12f, 1458f, 80f)
        drawWarningRow(canvas, severity, title, details, bounds)
    }

    private fun drawWarningRow(
        canvas: Canvas,
        severity: String,
        headline: String,
        details: String,
        bounds: RectF,
    ) {
        val level = severity.uppercase(Locale.ROOT)
        val accent = when (level) {
            "RED" -> Color.rgb(190, 20, 35)
            "AMBER" -> Color.rgb(205, 112, 20)
            else -> Color.rgb(160, 139, 25)
        }
        p.style = Paint.Style.FILL
        p.clearShadowLayer()
        p.color = Color.argb(242, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(bounds, 12f, 12f, p)

        val left = bounds.left + 16f
        val right = bounds.right - 16f
        val titleSize = if (bounds.width() < 800f) 15f else 17f
        val titleBaseline = bounds.top + 25f
        drawFittedText(canvas, "${level.ifBlank { "WEATHER" }} WARNING  •  $headline",
            left, titleBaseline, right - left, titleSize, Color.WHITE, true)
        if (details.isNotBlank()) {
            val detailSize = 12f
            drawFittedText(canvas, details, left, bounds.top + 48f,
                right - left, detailSize, Color.WHITE, false)
        }
        p.style = Paint.Style.FILL
    }

    /** Draws a single-line warning within its allocated width, truncating by pixels. */
    private fun drawFittedText(
        canvas: Canvas,
        value: String,
        x: Float,
        baseline: Float,
        maxWidth: Float,
        size: Float,
        color: Int,
        bold: Boolean,
    ) {
        p.clearShadowLayer()
        p.style = Paint.Style.FILL
        p.textSize = size
        p.typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD)
                     else Typeface.create("sans-serif", Typeface.NORMAL)
        p.textAlign = Paint.Align.LEFT
        p.color = color
        val ellipsis = "…"
        var end = value.length
        while (end > 0 && p.measureText(value.substring(0, end) + ellipsis) > maxWidth) end--
        val fitted = if (end < value.length) value.take(end).trimEnd() + ellipsis else value
        canvas.save()
        canvas.clipRect(x, baseline - size - 4f, x + maxWidth, baseline + 5f)
        canvas.drawText(fitted, x, baseline, p)
        canvas.restore()
    }

    private fun warningValidityLabel(from: String, to: String): String {
        val zone = ZoneId.of("Europe/London")
        val formatter = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.UK).withZone(zone)
        val start = parseWarningInstant(from)
        val end = parseWarningInstant(to)
        val startText = start?.let(formatter::format)
        val endText = end?.let(formatter::format)
        return when {
            startText != null && endText != null -> "Valid $startText – $endText UK time"
            endText != null -> "Until $endText UK time"
            startText != null -> "From $startText UK time"
            else -> ""
        }
    }

    private fun warningCountdownLabel(from: String, to: String, now: Instant): String {
        val start = parseWarningInstant(from)
        val end = parseWarningInstant(to)
        val target: Instant
        val prefix: String
        when {
            start != null && now.isBefore(start) -> { target = start; prefix = "Starts in" }
            end != null && now.isBefore(end) -> { target = end; prefix = "Ends in" }
            else -> return ""
        }
        // Round up so a warning with seconds remaining never misleadingly reads
        // “0m” while it is still valid or has not started yet.
        val remainingSeconds = Duration.between(now, target).seconds.coerceAtLeast(0)
        val minutes = ((remainingSeconds + 59L) / 60L)
        val days = minutes / 1440
        val hours = (minutes % 1440) / 60
        val remainder = minutes % 60
        val remaining = when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${remainder}m"
            else -> "${remainder}m"
        }
        return "$prefix $remaining"
    }

    private fun parseWarningInstant(value: String): Instant? =
        runCatching { Instant.parse(value) }.getOrNull()

    private fun drawForecast(canvas: Canvas, days: List<ForecastDay>, palette: WallpaperPalette, top: Float) {
        val left = 78f
        val width = 1380f
        p.color = palette.divider
        p.strokeWidth = 1.5f
        canvas.drawLine(left, top, left + width, top, p)
        val cell = width / days.size.coerceAtLeast(1).toFloat()
        days.forEachIndexed { index, day ->
            val cx = left + cell * (index + 0.5f)
            if (index > 0) {
                p.color = palette.divider
                canvas.drawLine(left + cell * index, top + 18f, left + cell * index, top + 156f, p)
            }
            text(canvas, day.dayLabel.uppercase(Locale.getDefault()), cx, top + 41f, 19f, palette.secondaryText, true, Paint.Align.CENTER)
            drawWeatherIcon(canvas, cx, top + 86f, 0.34f, day.weatherCode, palette.weatherIcon)
            text(canvas, "${day.high}°  /  ${day.low}°", cx, top + 148f, 22f, palette.primaryText, true, Paint.Align.CENTER)
        }
    }

    private fun drawWeatherIcon(canvas: Canvas, cx: Float, cy: Float, scale: Float, code: Int, color: Int) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(scale, scale)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 8f
        p.strokeCap = Paint.Cap.ROUND
        p.color = color
        p.clearShadowLayer()
        when (code) {
            0 -> { // clear
                canvas.drawCircle(0f, 0f, 34f, p)
                for (a in 0..7) {
                    val angle = Math.toRadians(a * 45.0)
                    val x1 = kotlin.math.cos(angle).toFloat() * 48f
                    val y1 = kotlin.math.sin(angle).toFloat() * 48f
                    val x2 = kotlin.math.cos(angle).toFloat() * 65f
                    val y2 = kotlin.math.sin(angle).toFloat() * 65f
                    canvas.drawLine(x1, y1, x2, y2, p)
                }
            }
            1, 2, 3 -> drawCloud(canvas, p)
            45, 48 -> { drawCloud(canvas, p); canvas.drawLine(-28f, 40f, -40f, 59f, p); canvas.drawLine(5f, 40f, -7f, 59f, p) }
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> {
                drawCloud(canvas, p)
                listOf(-30f, 0f, 30f).forEach { x -> canvas.drawLine(x, 35f, x - 12f, 58f, p) }
            }
            71, 73, 75, 77, 85, 86 -> {
                drawCloud(canvas, p)
                canvas.drawLine(-25f, 37f, -25f, 57f, p)
                canvas.drawLine(-35f, 47f, -15f, 47f, p)
                canvas.drawLine(22f, 37f, 22f, 57f, p)
                canvas.drawLine(12f, 47f, 32f, 47f, p)
            }
            95, 96, 99 -> {
                drawCloud(canvas, p)
                val bolt = Path().apply { moveTo(8f, 31f); lineTo(-10f, 57f); lineTo(5f, 57f); lineTo(-5f, 78f) }
                canvas.drawPath(bolt, p)
            }
            else -> drawCloud(canvas, p)
        }
        p.style = Paint.Style.FILL
        canvas.restore()
    }

    private fun drawCloud(canvas: Canvas, paint: Paint) {
        val cloud = Path().apply {
            moveTo(-54f, 25f)
            cubicTo(-75f, 25f, -76f, -5f, -55f, -12f)
            cubicTo(-51f, -43f, -12f, -53f, 1f, -28f)
            cubicTo(27f, -43f, 54f, -24f, 51f, 1f)
            cubicTo(82f, 3f, 82f, 35f, 53f, 35f)
            lineTo(-54f, 35f)
            close()
        }
        canvas.drawPath(cloud, paint)
    }

    private fun drawFestiveTrim(canvas: Canvas, palette: WallpaperPalette) {
        // Fine evergreen/snow accents stay on the inner top edge so they do not obscure content.
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = withAlpha(palette.festive, 210)
        val garland = Path().apply {
            moveTo(88f, 29f)
            cubicTo(250f, 65f, 360f, 5f, 520f, 29f)
            cubicTo(680f, 55f, 800f, 8f, 950f, 29f)
            cubicTo(1100f, 53f, 1240f, 8f, 1448f, 29f)
        }
        canvas.drawPath(garland, p)
        p.style = Paint.Style.FILL
        for (x in listOf(155f, 320f, 490f, 660f, 830f, 1000f, 1170f, 1340f)) {
            p.color = palette.snow
            canvas.drawCircle(x, 34f, 3.5f, p)
        }
    }

    private fun text(canvas: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean, align: Paint.Align = Paint.Align.LEFT) {
        p.clearShadowLayer()
        p.style = Paint.Style.FILL
        p.color = color
        p.textSize = size
        p.typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
        p.textAlign = align
        canvas.drawText(value, x, y, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}
