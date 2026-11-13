package com.christmasgsd.weather.themes

import android.content.Context

/**
 * Context-facing facade for the single theme store used by SettingsActivity.
 * All values remain typed in the `weather` SharedPreferences file so loading a
 * theme cannot turn booleans/integers into strings and cause ClassCastException.
 */
class ThemeProfileStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("weather", Context.MODE_PRIVATE)

    data class Theme(val name: String, val values: Map<String, String>)

    fun names(): List<String> = ThemeProfiles.names(prefs)

    fun saveAsNew(name: String): Result<Unit> =
        if (ThemeProfiles.save(prefs, name)) Result.success(Unit)
        else Result.failure(IllegalStateException("Theme name is empty or already exists."))

    fun load(name: String): Result<Unit> =
        if (ThemeProfiles.apply(prefs, name)) Result.success(Unit)
        else Result.failure(NoSuchElementException("Saved theme not found or could not be applied."))

    fun export(name: String): Theme? {
        val root = runCatching {
            org.json.JSONObject(prefs.getString("saved_themes_json", "{}") ?: "{}")
        }.getOrNull() ?: return null
        val snapshot = root.optJSONObject(name) ?: return null
        val values = buildMap {
            snapshot.keys().forEach { key -> put(key, snapshot.opt(key).toString()) }
        }
        return Theme(name, values)
    }
}
