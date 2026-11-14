package com.christmasgsd.weather.themes

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Named, independent appearance snapshots. Location is intentionally excluded. */
object ThemeProfiles {
    private const val THEMES_KEY = "saved_themes_json"
    private val appearancePrefixes = listOf(
        "palette.", "clock.", "date.", "readout.", "background.", "icon.", "forecast.", "layout.", "weather.", "panel."
    )
    private val appearanceKeys = setOf(
        "background_uri", "background_mode", "active_theme",
        // Warning presentation is visual theme state; feed credentials remain device-specific.
        "alerts.display_mode", "alerts.show_details"
    )

    fun names(prefs: SharedPreferences): List<String> = runCatching {
        val root = JSONObject(prefs.getString(THEMES_KEY, "{}") ?: "{}")
        root.keys().asSequence().toList().sortedBy { it.lowercase() }
    }.getOrDefault(emptyList())

    enum class SaveResult { SUCCESS, DUPLICATE, INVALID_NAME, STORAGE_ERROR }

    /** Save under a new name; report duplicate names separately from persistence failures. */
    fun saveWithResult(prefs: SharedPreferences, name: String): SaveResult {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > 60) return SaveResult.INVALID_NAME
        return try {
            val root = JSONObject(prefs.getString(THEMES_KEY, "{}") ?: "{}")
            if (root.has(clean)) return SaveResult.DUPLICATE
            val snapshot = JSONObject()
            prefs.all.forEach { (key, value) ->
                if (isAppearanceKey(key) && value != null) snapshot.put(key, value)
            }
            // Seed the first Christmas profile with the renderer's current festive palette.
            if (snapshot.length() == 0 && clean.contains("christmas", ignoreCase = true)) {
                snapshot.put("palette.panel", "#DE0F1117")
                snapshot.put("palette.border", "#FFFF2A3A")
                snapshot.put("palette.glow", "#FFFF192D")
                snapshot.put("palette.festive", "#FF23A05B")
                snapshot.put("palette.snow", "#FFFFFFFF")
            }
            root.put(clean, snapshot)
            if (prefs.edit().putString(THEMES_KEY, root.toString()).putString("active_theme", clean).commit())
                SaveResult.SUCCESS else SaveResult.STORAGE_ERROR
        } catch (_: Throwable) {
            SaveResult.STORAGE_ERROR
        }
    }

    /** Backwards-compatible Boolean API for existing callers. */
    fun save(prefs: SharedPreferences, name: String): Boolean =
        saveWithResult(prefs, name) == SaveResult.SUCCESS


    /** Replace an existing profile only when the caller has obtained explicit confirmation. */
    fun update(prefs: SharedPreferences, name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty()) return false
        return runCatching {
            val root = JSONObject(prefs.getString(THEMES_KEY, "{}") ?: "{}")
            if (!root.has(clean)) return false
            val snapshot = JSONObject()
            prefs.all.forEach { (key, value) ->
                if (isAppearanceKey(key) && value != null) snapshot.put(key, value)
            }
            root.put(clean, snapshot)
            prefs.edit().putString(THEMES_KEY, root.toString())
                .putString("active_theme", clean).commit()
        }.getOrDefault(false)
    }

    /** Delete a named profile; active-theme marker is cleared if it points at this profile. */
    fun delete(prefs: SharedPreferences, name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty()) return false
        return runCatching {
            val root = JSONObject(prefs.getString(THEMES_KEY, "{}") ?: "{}")
            if (!root.has(clean)) return false
            root.remove(clean)
            val editor = prefs.edit().putString(THEMES_KEY, root.toString())
            if (prefs.getString("active_theme", null) == clean) editor.remove("active_theme")
            editor.commit()
        }.getOrDefault(false)
    }

    /** Restore only appearance keys, leaving location and other device settings untouched. */
    fun apply(prefs: SharedPreferences, name: String): Boolean = runCatching {
        val root = JSONObject(prefs.getString(THEMES_KEY, "{}") ?: "{}")
        val snapshot = root.optJSONObject(name) ?: return false
        val editor = prefs.edit()
        prefs.all.keys.filter(::isAppearanceKey).forEach(editor::remove)
        snapshot.keys().forEach { key ->
            when (val value = snapshot.get(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Double -> editor.putFloat(key, value.toFloat())
                is String -> editor.putString(key, value)
            }
        }
        editor.putString("active_theme", name).commit()
    }.getOrDefault(false)


    /** Build a portable, versioned JSON document for one saved theme. */
    fun exportJson(prefs: SharedPreferences, name: String): String? = runCatching {
        val root = JSONObject(prefs.getString(THEMES_KEY, "{}") ?: "{}")
        val snapshot = root.optJSONObject(name) ?: return null
        JSONObject().put("format", "christmas-gsd-theme")
            .put("schemaVersion", 1)
            .put("name", name)
            .put("settings", JSONObject(snapshot.toString()))
            .toString(2)
    }.getOrNull()

    /** Import a portable theme without replacing an existing profile of the same name. */
    fun importJson(prefs: SharedPreferences, json: String, preferredName: String? = null): String? = runCatching {
        require(json.length <= 1_000_000) { "Theme file is too large." }
        val document = JSONObject(json)
        require(document.optString("format") == "christmas-gsd-theme") { "This is not a Christmas GSD theme file." }
        require(document.optInt("schemaVersion", -1) == 1) { "Unsupported theme file version." }
        val originalName = document.optString("name").trim()
        val clean = (preferredName?.trim().takeUnless { it.isNullOrEmpty() } ?: originalName)
        require(clean.isNotBlank() && clean.length <= 60) { "Theme name must be 1–60 characters." }
        val incoming = document.optJSONObject("settings") ?: error("Theme settings are missing.")
        val safeSnapshot = JSONObject()
        val iterator = incoming.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            require(isAppearanceKey(key)) { "Theme contains an unsupported setting: $key" }
            val value = incoming.get(key)
            require(value == JSONObject.NULL || value is String || value is Boolean || value is Int || value is Long || value is Double) {
                "Theme contains an unsupported value for $key"
            }
            if (value != JSONObject.NULL) safeSnapshot.put(key, value)
        }
        require(safeSnapshot.length() > 0) { "Theme contains no appearance settings." }
        val root = JSONObject(prefs.getString(THEMES_KEY, "{}") ?: "{}")
        require(!root.has(clean)) { "A theme named '$clean' already exists. Rename the import to keep both." }
        root.put(clean, safeSnapshot)
        require(prefs.edit().putString(THEMES_KEY, root.toString()).commit()) { "Could not save imported theme." }
        clean
    }.getOrNull()

    private fun isAppearanceKey(key: String): Boolean =
        // The saved wallpaper library is device-wide user data, not part of a theme.
        // Themes may select one background, but must never snapshot or replace the
        // user's complete collection when they are saved, loaded, or imported.
        key != "background.saved_uris" &&
            (key in appearanceKeys || appearancePrefixes.any(key::startsWith))
}
