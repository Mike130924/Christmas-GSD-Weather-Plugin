package com.christmasgsd.weather.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.christmasgsd.weather.render.CurrentWeather
import com.christmasgsd.weather.render.ForecastDay
import com.christmasgsd.weather.render.HourlyForecast
import com.christmasgsd.weather.render.SunTimes
import com.christmasgsd.weather.render.WallpaperWeather

/** Fetches current, hourly and daily forecast data from Open-Meteo. */
class OpenMeteoRepository {
    fun fetch(latitude: Double, longitude: Double, locationName: String = ""): WallpaperWeather {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "Invalid coordinates" }
        val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,pressure_msl,surface_pressure,wind_speed_10m,wind_direction_10m" +
            "&hourly=temperature_2m,precipitation_probability,weather_code,visibility" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max" +
            "&forecast_days=7&forecast_hours=24&timezone=auto&wind_speed_unit=kmh")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "ChristmasGSDWeather/0.1")
        }
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("Weather service returned HTTP ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return parse(JSONObject(body), locationName)
        } finally { connection.disconnect() }
    }

    private fun parse(root: JSONObject, locationName: String): WallpaperWeather {
        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        val hourly = root.getJSONObject("hourly")
        // Open-Meteo returns wall-clock timestamps in the requested location's
        // timezone (without an offset). Always parse/format them in that timezone,
        // not the TV's timezone, which may be different from the weather location.
        val locationTimeZone = TimeZone.getTimeZone(root.optString("timezone", "GMT"))
        val times = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val highs = daily.getJSONArray("temperature_2m_max")
        val lows = daily.getJSONArray("temperature_2m_min")
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = locationTimeZone }
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault()).apply { timeZone = locationTimeZone }
        val forecast = (0 until minOf(6, times.length())).mapNotNull { i ->
            val high = highs.optDouble(i, Double.NaN)
            val low = lows.optDouble(i, Double.NaN)
            val code = codes.optInt(i, -1)
            // Never turn a missing API value into a plausible-looking 0°C forecast.
            if (!high.isFinite() || !low.isFinite() || code < 0) return@mapNotNull null
            val date = inputFormat.parse(times.getString(i)) ?: return@mapNotNull null
            ForecastDay(dayFormat.format(date), code, high.toInt(), low.toInt())
        }
        val hourlyTimes = hourly.getJSONArray("time")
        val hourlyTemps = hourly.getJSONArray("temperature_2m")
        val hourlyRain = hourly.getJSONArray("precipitation_probability")
        val hourlyCodes = hourly.getJSONArray("weather_code")
        val hourlyVisibility = hourly.optJSONArray("visibility")
        val currentTime = current.optString("time")
        val startHour = (0 until hourlyTimes.length()).firstOrNull { hourlyTimes.optString(it) >= currentTime } ?: 0
        // Open-Meteo exposes visibility as an hourly field (metres). Use the
        // nearest forecast hour to the current local forecast timestamp.
        val visibilityKm = hourlyVisibility?.optDouble(startHour, Double.NaN)
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.div(1000.0)?.toFloat() ?: Float.NaN
        val hourLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = locationTimeZone }
        val hourlyForecast = (startHour until minOf(startHour + 6, hourlyTimes.length())).mapNotNull { i ->
            val temperature = hourlyTemps.optDouble(i, Double.NaN)
            val code = hourlyCodes.optInt(i, -1)
            if (!temperature.isFinite() || code < 0) return@mapNotNull null
            val rain = hourlyRain.optInt(i, -1).takeIf { it in 0..100 } ?: -1
            HourlyForecast(hourLabel.format(parseIsoHour(hourlyTimes.getString(i), locationTimeZone)), temperature.toInt(), rain, code)
        }
        val sunrise = daily.optJSONArray("sunrise")?.optString(0).orEmpty()
        val sunset = daily.optJSONArray("sunset")?.optString(0).orEmpty()
        val nowMillis = System.currentTimeMillis()
        val sunriseMillis = parseIsoDateTime(sunrise, locationTimeZone)
        val sunsetMillis = parseIsoDateTime(sunset, locationTimeZone)
        val daylight = if (sunriseMillis > 0 && sunsetMillis > sunriseMillis) (sunsetMillis - sunriseMillis) / 60000L else 0L
        val daylightRemaining = if (sunriseMillis > 0 && sunsetMillis > nowMillis && nowMillis >= sunriseMillis)
            (sunsetMillis - nowMillis) / 60000L else 0L
        val temperature = requiredFinite(current, "temperature_2m")
        val feelsLike = requiredFinite(current, "apparent_temperature")
        val windSpeed = requiredFinite(current, "wind_speed_10m")
        val windDegrees = requiredFinite(current, "wind_direction_10m").toInt().mod(360)
        val humidityValue = current.optDouble("relative_humidity_2m", Double.NaN)
        val humidity = humidityValue.takeIf { it.isFinite() && it in 0.0..100.0 }?.toInt() ?: -1
        val pressureValue = current.optDouble("pressure_msl", Double.NaN)
        val pressure = pressureValue.takeIf { it.isFinite() && it > 0.0 }?.toInt() ?: Int.MIN_VALUE
        val dewPoint = dewPointC(temperature, humidity.toDouble())
        val code = current.optInt("weather_code", -1)
        require(code >= 0) { "Weather service did not provide a valid current weather code" }
        require(forecast.isNotEmpty()) { "Weather service did not provide valid daily forecast values" }
        return WallpaperWeather(
            current = CurrentWeather(
                temperature = temperature.toInt(), condition = conditionFor(code), weatherCode = code,
                feelsLike = feelsLike.toInt(), high = forecast.first().high, low = forecast.first().low,
                windKph = windSpeed.toInt(), windDirection = direction(windDegrees),
                humidity = humidity, pressureHpa = pressure,
                dewPoint = dewPoint, uvIndex = daily.optJSONArray("uv_index_max")?.optDouble(0, Double.NaN)?.toFloat() ?: Float.NaN,
                visibilityKm = visibilityKm,
            ),
            forecast = forecast, location = locationName, updatedAt = nowMillis,
            hourly = hourlyForecast,
            // Open-Meteo's is_day is evaluated for the forecast location and is
            // more reliable than comparing naive local timestamps against the TV clock.
            sunTimes = SunTimes(sunrise, sunset, daylight, current.optInt("is_day", 0) == 1, daylightRemaining),
        )
    }

    private fun requiredFinite(json: JSONObject, key: String): Double {
        val value = json.optDouble(key, Double.NaN)
        require(value.isFinite()) { "Weather service did not provide a valid $key reading" }
        return value
    }

    private fun parseIsoHour(value: String, timeZone: TimeZone): Date = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply { this.timeZone = timeZone }.parse(value)
    }.getOrNull() ?: Date()
    private fun parseIsoDateTime(value: String, timeZone: TimeZone): Long = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply { this.timeZone = timeZone }.parse(value)?.time ?: 0L
    }.getOrDefault(0L)
    private fun dewPointC(temp: Double, humidity: Double): Int {
        if (!temp.isFinite() || !humidity.isFinite() || humidity <= 0.0) return Int.MIN_VALUE
        val a = 17.625; val b = 243.04
        val gamma = kotlin.math.ln(humidity / 100.0) + a * temp / (b + temp)
        return (b * gamma / (a - gamma)).toInt()
    }
    private fun direction(degrees: Int): String {
        val points = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return points[((degrees + 22) % 360) / 45]
    }
    private fun conditionFor(code: Int): String = when (code) {
        0 -> "Clear sky"; 1 -> "Mainly clear"; 2 -> "Partly cloudy"; 3 -> "Overcast"
        45, 48 -> "Fog"; 51, 53, 55 -> "Drizzle"; 56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"; 66, 67 -> "Freezing rain"; 71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Rain showers"; 85, 86 -> "Snow showers"; 95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"; else -> "Unknown conditions"
    }
}
