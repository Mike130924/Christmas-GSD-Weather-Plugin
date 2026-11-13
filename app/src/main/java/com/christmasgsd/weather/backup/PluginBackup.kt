package com.christmasgsd.weather.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/** Portable whole-plugin backup. Wallpaper bytes are embedded so backups do not depend on USB paths. */
object PluginBackup {
    private const val FORMAT = "christmas-gsd-plugin-backup"
    private const val VERSION = 2
    private const val MAX_BACKGROUND_COUNT = 100
    private const val MAX_SETTINGS_COUNT = 500
    private const val MAX_BACKUP_BYTES = 32 * 1024 * 1024
    private const val MAX_SINGLE_IMAGE_BYTES = 12 * 1024 * 1024
    private val SENSITIVE_KEYS = setOf("alerts.api_key")

    fun write(context: Context, output: OutputStream) {
        val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
        val settings = JSONObject()
        prefs.all.forEach { (key, value) ->
            if (key in SENSITIVE_KEYS) return@forEach
            when (value) {
                is Boolean, is Int, is Long, is Float, is String -> settings.put(key, value)
                is Set<*> -> settings.put(key, JSONArray(value.filterIsInstance<String>()))
            }
        }
        val backgrounds = JSONArray()
        val uriCandidates = linkedSetOf<String>()
        val uris = JSONArray(prefs.getString("background.saved_uris", "[]") ?: "[]")
        for (i in 0 until uris.length()) uris.optString(i).takeIf(String::isNotBlank)?.let(uriCandidates::add)
        prefs.getString("background_uri", null)?.takeIf(String::isNotBlank)?.let(uriCandidates::add)
        // Include backgrounds referenced only by saved theme profiles as well.
        runCatching {
            val themes = JSONObject(prefs.getString("saved_themes_json", "{}") ?: "{}")
            themes.keys().forEach { name ->
                themes.optJSONObject(name)?.optString("background_uri")
                    ?.takeIf(String::isNotBlank)?.let(uriCandidates::add)
            }
        }
        val seen = linkedSetOf<String>()
        for (uriText in uriCandidates) {
            if (!seen.add(uriText)) continue
            val parsed = Uri.parse(uriText)
            val bytes = context.contentResolver.openInputStream(parsed)?.use {
                it.readBounded(MAX_SINGLE_IMAGE_BYTES)
            } ?: throw IllegalStateException("Could not include wallpaper image in backup: $uriText")
            require(bytes.isNotEmpty()) { "Wallpaper image is empty: $uriText" }
            val mime = context.contentResolver.getType(parsed) ?: detectImageMime(bytes)
            require(mime in setOf("image/jpeg", "image/png", "image/webp")) {
                "Unsupported wallpaper image type: $mime"
            }
            backgrounds.put(JSONObject().put("originalUri", uriText)
                .put("mime", mime)
                .put("sha256", bytes.sha256Hex())
                .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)))
            require(backgrounds.length() <= MAX_BACKGROUND_COUNT) { "Too many wallpaper images in backup." }
        }
        val document = JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("settings", settings)
            .put("excludedSensitiveSettings", JSONArray(SENSITIVE_KEYS.toList()))
            .put("backgrounds", backgrounds)
        val bytes = document.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BACKUP_BYTES) { "Backup exceeds the 32 MB limit." }
        output.write(bytes)
        output.flush()
    }

    /** Restores preferences and writes embedded wallpaper assets into app-private storage. */
    fun restore(context: Context, input: InputStream) {
        val raw = input.readBounded(MAX_BACKUP_BYTES)
        val document = JSONObject(String(raw, Charsets.UTF_8))
        require(document.optString("format") == FORMAT) { "This is not a Christmas GSD plugin backup." }
        require(document.optInt("schemaVersion", -1) == VERSION) { "Unsupported backup version." }
        val incoming = document.optJSONObject("settings") ?: error("Backup settings are missing.")
        require(incoming.length() <= MAX_SETTINGS_COUNT) { "Backup contains too many settings." }
        require(document.optJSONArray("backgrounds")?.length()?.let { it <= MAX_BACKGROUND_COUNT } != false) {
            "Backup contains too many wallpaper images."
        }
        val restoredUris = JSONArray()
        val uriMap = linkedMapOf<String, String>()
        val backgrounds = document.optJSONArray("backgrounds") ?: JSONArray()
        val directory = java.io.File(context.filesDir, "restored-backgrounds").apply { mkdirs() }
        // Unique filenames ensure a failed restore can never overwrite assets used by
        // the currently active preferences from an earlier successful restore.
        val restoreToken = System.nanoTime().coerceAtLeast(0L)
        val newFiles = mutableListOf<java.io.File>()
        try {
            for (i in 0 until backgrounds.length()) {
                val item = backgrounds.getJSONObject(i)
                val mime = item.optString("mime", "image/jpeg")
                val extension = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
                require(mime in setOf("image/jpeg", "image/png", "image/webp")) { "Unsupported wallpaper image type." }
                val encoded = item.getString("data")
                require(encoded.length <= ((MAX_SINGLE_IMAGE_BYTES + 2) / 3) * 4 + 8) { "Encoded wallpaper is too large." }
                val data = Base64.decode(encoded, Base64.DEFAULT)
                require(data.isNotEmpty() && data.size <= MAX_SINGLE_IMAGE_BYTES) { "Invalid wallpaper image in backup." }
                require(data.sha256Hex().equals(item.getString("sha256"), ignoreCase = true)) {
                    "Wallpaper integrity check failed. The backup may be damaged or modified."
                }
                require(data.matchesImageMime(mime)) { "Wallpaper data does not match its declared image type." }
                val file = java.io.File(directory, "background_${restoreToken}_${i + 1}.$extension")
                file.writeBytes(data)
                newFiles += file
                val restoredUri = Uri.Builder().scheme("content")
                    .authority("${context.packageName}.wallpaperimages")
                    .appendPath("backgrounds").appendPath(file.name).build().toString()
                restoredUris.put(restoredUri)
                item.optString("originalUri").takeIf(String::isNotBlank)?.let { uriMap[it] = restoredUri }
            }
            val currentPrefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
            val retainedSecrets = SENSITIVE_KEYS.associateWith { currentPrefs.getString(it, null) }
            val safe = currentPrefs.edit().clear()
            val iterator = incoming.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                when (val value = incoming.get(key)) {
                    is Boolean -> safe.putBoolean(key, value)
                    is Int -> safe.putInt(key, value)
                    is Long -> safe.putLong(key, value)
                    is Double -> safe.putFloat(key, value.toFloat())
                    is String -> safe.putString(key, if (key == "background_uri") uriMap[value] ?: value else value)
                    is JSONArray -> {
                        if (key == "background.saved_uris") {
                            safe.putString(key, JSONArray().also { mapped ->
                                for (i in 0 until value.length()) {
                                    val old = value.optString(i)
                                    mapped.put(uriMap[old] ?: old)
                                }
                            }.toString())
                        } else safe.putStringSet(key, (0 until value.length()).map { value.getString(it) }.toSet())
                    }
                }
            }
            // Remap image references held inside named theme snapshots too.
            val themesJson = incoming.optString("saved_themes_json", "{}")
            val themes = JSONObject(themesJson) // Reject malformed profile data before committing preferences.
            themes.keys().forEach { name ->
                themes.optJSONObject(name)?.let { snapshot ->
                    val old = snapshot.optString("background_uri")
                    if (old.isNotBlank() && uriMap.containsKey(old)) snapshot.put("background_uri", uriMap.getValue(old))
                }
            }
            safe.putString("saved_themes_json", themes.toString())
            // Credentials are deliberately not portable in plaintext backups. Keep a
            // credential already configured on this device; otherwise require re-entry.
            retainedSecrets.forEach { (key, value) -> if (!value.isNullOrBlank()) safe.putString(key, value) }
            if (restoredUris.length() > 0 && incoming.optString("background_uri").let { uriMap.containsKey(it) }) {
                safe.putString("background_uri", uriMap.getValue(incoming.optString("background_uri")))
            }
            require(safe.commit()) { "Could not save restored plugin settings." }
            // Preferences now point only at the new set. Remove obsolete restored
            // assets after the commit; never delete the previous set before success.
            directory.listFiles()?.filter { it.isFile && it !in newFiles }?.forEach { it.delete() }
        } catch (failure: Exception) {
            newFiles.forEach { it.delete() }
            throw failure
        }
    }

    private fun ByteArray.sha256Hex(): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(this).joinToString("") { "%02x".format(it) }

    private fun detectImageMime(bytes: ByteArray): String = when {
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4e.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
        bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "image/jpeg"
        bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
        else -> "application/octet-stream"
    }

    private fun ByteArray.matchesImageMime(mime: String): Boolean = detectImageMime(this) == mime

    private fun InputStream.readBounded(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "File exceeds the permitted size." }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
}
