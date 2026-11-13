package com.christmasgsd.weather.alerts

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.time.Instant
import java.security.MessageDigest

/** A UK Met Office National Severe Weather Warning matched to the configured map point. */
data class UkWeatherWarning(
    val id: String,
    val headline: String,
    val weatherType: String,
    val severity: String,
    val validFrom: String,
    val validTo: String,
    val description: String = "",
)

/**
 * Client for the Met Office NSWWS public API.
 * The feed URL and API key are deliberately supplied by the caller: Met Office requires
 * an issued API key and supplies the feed URL with the subscription. No fake public URL
 * or unauthenticated access is assumed here.
 */
class UkWeatherWarningsRepository(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 10_000,
) {
    fun fetchForLocation(
        feedUrl: String,
        apiKey: String,
        latitude: Double,
        longitude: Double,
    ): List<UkWeatherWarning> {
        require(feedUrl.startsWith("https://")) { "A secure Met Office feed URL is required" }
        require(apiKey.isNotBlank()) { "Met Office NSWWS API key is required" }
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "Invalid coordinates" }

        val feed = request(feedUrl, apiKey)
        val issuedUrls = parseIssuedLinks(feed)
        return issuedUrls.flatMap { issuedUrl ->
            val collection = JSONObject(request(issuedUrl, apiKey))
            val features = collection.optJSONArray("features") ?: return@flatMap emptyList()
            (0 until features.length()).mapNotNull { index ->
                val feature = features.optJSONObject(index) ?: return@mapNotNull null
                val geometry = feature.optJSONObject("geometry") ?: return@mapNotNull null
                if (!containsPoint(geometry, latitude, longitude)) return@mapNotNull null
                val properties = feature.optJSONObject("properties") ?: JSONObject()
                val id = feature.optString("id", properties.optString("id", "warning-$index"))
                UkWeatherWarning(
                    id = id,
                    headline = first(properties, "warningHeadline", "headline", "description", "title")
                        .ifBlank { "Met Office weather warning" },
                    weatherType = firstArrayOrString(properties, "weatherType", "weather_type", "phenomenon"),
                    severity = first(properties, "warningLevel", "severity", "warning_level"),
                    validFrom = first(properties, "validFromDate", "validFrom", "valid_from", "onset"),
                    validTo = first(properties, "validToDate", "validTo", "valid_to", "expires"),
                    description = firstArrayOrString(properties, "whatToExpect", "what_to_expect", "instruction", "details"),
                )
            }
        }.distinctBy { it.id }
    }

    private fun request(address: String, apiKey: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/atom+xml, application/geo+json, application/json")
            // DataHub's NSWWS API authenticates using the apikey header.
            setRequestProperty("apikey", apiKey)
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Met Office warnings returned HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Extract only issued-warning links advertised by the Atom feed. */
    private fun parseIssuedLinks(xml: String): List<String> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(xml.reader())
        val links = linkedSetOf<String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name.equals("link", true)) {
                val href = parser.getAttributeValue(null, "href")
                val rel = parser.getAttributeValue(null, "rel").orEmpty().lowercase(Locale.ROOT)
                val title = parser.getAttributeValue(null, "title").orEmpty().lowercase(Locale.ROOT)
                // The Atom feed's related link is the authoritative current issued collection.
                if (!href.isNullOrBlank() && (rel == "related" || title.contains("latest version of all issued"))) links += href
            }
            event = parser.next()
        }
        return links.toList()
    }

    private fun firstArrayOrString(json: JSONObject, vararg keys: String): String {
        keys.forEach { key ->
            if (!json.has(key) || json.isNull(key)) return@forEach
            val value = json.opt(key)
            when (value) {
                is JSONArray -> return (0 until value.length()).mapNotNull { i ->
                    value.optString(i).takeIf { it.isNotBlank() && it != "null" }
                }.joinToString(", ")
                is String -> if (value.isNotBlank() && value != "null") return value
            }
        }
        return ""
    }

    /**
     * Poll at most once per refresh interval. A previously cached warning collection is
     * used if the network is unavailable, but expired warnings are always filtered out.
     * Cache is scoped to feed + coordinates so changing location cannot reuse another area's alerts.
     */
    fun fetchCachedForLocation(
        feedUrl: String,
        apiKey: String,
        latitude: Double,
        longitude: Double,
        cache: SharedPreferences,
        nowMillis: Long = System.currentTimeMillis(),
        refreshIntervalMillis: Long = 15 * 60 * 1000L,
    ): List<UkWeatherWarning> {
        // Match the six-decimal location identity used by the wallpaper service.
        // Four decimals could incorrectly share warning caches for nearby map points.
        val scope = "$feedUrl|${"%.6f".format(Locale.ROOT, latitude)}|${"%.6f".format(Locale.ROOT, longitude)}"
        val scopeDigest = MessageDigest.getInstance("SHA-256")
            .digest(scope.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }
        val key = "nswws.cache.$scopeDigest"
        val savedAt = cache.getLong("$key.saved_at", 0L)
        val cachedJson = cache.getString("$key.items", null)
        fun decode(): List<UkWeatherWarning> = runCatching {
            val array = JSONArray(cachedJson ?: "[]")
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                UkWeatherWarning(o.optString("id"), o.optString("headline"), o.optString("weatherType"),
                    o.optString("severity"), o.optString("validFrom"), o.optString("validTo"), o.optString("description"))
            }
        }.getOrDefault(emptyList())
        fun active(items: List<UkWeatherWarning>): List<UkWeatherWarning> = items.filter { warning ->
            // Keep announced future warnings: they must be visible before onset so the
            // renderer can show its “Starts in” countdown. Reject malformed intervals.
            val start = parseWarningTime(warning.validFrom)
            val end = parseWarningTime(warning.validTo)
            start != null && end != null && end > start && end.toEpochMilli() > nowMillis
        }.sortedWith(
            compareBy<UkWeatherWarning> { severityOrder(it.severity) }
                .thenBy { parseWarningTime(it.validFrom) }
        )
        val cacheAge = nowMillis - savedAt
        if (savedAt > 0 && cacheAge in 0 until refreshIntervalMillis && cachedJson != null) {
            return active(decode())
        }
        return try {
            val fresh = fetchForLocation(feedUrl, apiKey, latitude, longitude)
            val array = JSONArray()
            fresh.forEach { w -> array.put(JSONObject().apply {
                put("id", w.id); put("headline", w.headline); put("weatherType", w.weatherType)
                put("severity", w.severity); put("validFrom", w.validFrom); put("validTo", w.validTo)
                put("description", w.description)
            }) }
            cache.edit().putString("$key.items", array.toString()).putLong("$key.saved_at", nowMillis).apply()
            active(fresh)
        } catch (error: Exception) {
            if (cachedJson != null) active(decode()) else throw error
        }
    }

    private fun parseWarningTime(value: String): Instant? =
        runCatching { Instant.parse(value.trim()) }.getOrNull()

    private fun severityOrder(value: String): Int = when (value.trim().uppercase(Locale.ROOT)) {
        "RED" -> 0
        "AMBER" -> 1
        "YELLOW" -> 2
        else -> 3
    }

    private fun first(json: JSONObject, vararg keys: String): String {
        keys.forEach { key ->
            val value = json.optString(key).takeUnless { it.isBlank() || it == "null" }
            if (value != null) return value
        }
        return ""
    }

    /** Supports Polygon and MultiPolygon coordinates in GeoJSON [longitude, latitude] order. */
    private fun containsPoint(geometry: JSONObject, latitude: Double, longitude: Double): Boolean {
        return when (geometry.optString("type")) {
            "Polygon" -> polygonContains(geometry.optJSONArray("coordinates")?.optJSONArray(0), latitude, longitude)
            "MultiPolygon" -> {
                val polygons = geometry.optJSONArray("coordinates") ?: return false
                (0 until polygons.length()).any { i ->
                    polygonContains(polygons.optJSONArray(i)?.optJSONArray(0), latitude, longitude)
                }
            }
            else -> false
        }
    }

    private fun polygonContains(ring: org.json.JSONArray?, latitude: Double, longitude: Double): Boolean {
        if (ring == null || ring.length() < 4) return false
        var inside = false
        var j = ring.length() - 1
        for (i in 0 until ring.length()) {
            val a = ring.optJSONArray(i) ?: continue
            val b = ring.optJSONArray(j) ?: continue
            val xi = a.optDouble(0, Double.NaN); val yi = a.optDouble(1, Double.NaN)
            val xj = b.optDouble(0, Double.NaN); val yj = b.optDouble(1, Double.NaN)
            if (xi.isFinite() && yi.isFinite() && xj.isFinite() && yj.isFinite()) {
                val crosses = (yi > latitude) != (yj > latitude) &&
                    longitude < (xj - xi) * (latitude - yi) / ((yj - yi).takeIf { it != 0.0 } ?: 1e-12) + xi
                if (crosses) inside = !inside
            }
            j = i
        }
        return inside
    }
}
