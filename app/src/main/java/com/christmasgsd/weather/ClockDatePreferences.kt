package com.christmasgsd.weather

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Persisted clock/date appearance settings, deliberately independent of weather readout sizing. */
 data class ClockDateStyle(
    val showTime: Boolean = true,
    val showDate: Boolean = true,
    val use24Hour: Boolean = true,
    val datePattern: String = "EEE d MMM",
    val timeColor: Int = 0xFFFFFFFF.toInt(),
    val dateColor: Int = 0xFFE8E8E8.toInt(),
    val timeAlpha: Int = 100,
    val dateAlpha: Int = 90,
    val timeSizeSp: Float = 34f,
    val dateSizeSp: Float = 18f,
    val timeBold: Boolean = true,
    val dateBold: Boolean = false,
    val datePosition: String = "below_time"
)

class ClockDatePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    private fun numericPreference(key: String, fallback: Float): Float =
        (prefs.all[key] as? Number)?.toFloat() ?: fallback

    fun load(): ClockDateStyle = ClockDateStyle(
        showTime = prefs.getBoolean("clock.visible", true),
        showDate = prefs.getBoolean("date.visible", true),
        use24Hour = prefs.getBoolean("clock.24_hour", true),
        datePattern = prefs.getString("date.pattern", "EEE d MMM") ?: "EEE d MMM",
        timeColor = prefs.getInt("clock.color", 0xFFFFFFFF.toInt()),
        dateColor = prefs.getInt("date.color", 0xFFE8E8E8.toInt()),
        timeAlpha = prefs.getInt("clock.opacity", 100).coerceIn(0, 100),
        dateAlpha = prefs.getInt("date.opacity", 90).coerceIn(0, 100),
        timeSizeSp = numericPreference("clock.size", 34f).coerceIn(10f, 120f),
        dateSizeSp = numericPreference("date.size", 18f).coerceIn(8f, 72f),
        timeBold = prefs.getBoolean("clock.bold", true),
        dateBold = prefs.getBoolean("date.bold", false),
        datePosition = prefs.getString("date.position", "below_time") ?: "below_time"
    )

    fun save(style: ClockDateStyle): Boolean = prefs.edit()
        .putBoolean("clock.visible", style.showTime)
        .putBoolean("date.visible", style.showDate)
        .putBoolean("clock.24_hour", style.use24Hour)
        .putString("date.pattern", style.datePattern)
        .putInt("clock.color", style.timeColor)
        .putInt("date.color", style.dateColor)
        .putInt("clock.opacity", style.timeAlpha.coerceIn(0, 100))
        .putInt("date.opacity", style.dateAlpha.coerceIn(0, 100))
        .putInt("clock.size", style.timeSizeSp.coerceIn(10f, 120f).roundToInt())
        .putInt("date.size", style.dateSizeSp.coerceIn(8f, 72f).roundToInt())
        .putBoolean("clock.bold", style.timeBold)
        .putBoolean("date.bold", style.dateBold)
        .putString("date.position", style.datePosition)
        .commit()

    fun formattedTime(epochMillis: Long = System.currentTimeMillis(), style: ClockDateStyle = load(), locale: Locale = Locale.getDefault()): String {
        val pattern = if (style.use24Hour) "HH:mm" else "h:mm a"
        return DateTimeFormatter.ofPattern(pattern, locale).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis))
    }

    fun formattedDate(epochMillis: Long = System.currentTimeMillis(), style: ClockDateStyle = load(), locale: Locale = Locale.getDefault()): String {
        val safePattern = DATE_PATTERNS.firstOrNull { it == style.datePattern } ?: "EEE d MMM"
        return DateTimeFormatter.ofPattern(safePattern, locale).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis))
    }

    companion object {
        val DATE_PATTERNS = listOf("EEE d MMM", "EEEE d MMMM", "dd/MM/yyyy", "d MMMM yyyy", "yyyy-MM-dd")
    }
}
