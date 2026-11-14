package com.christmasgsd.weather

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Checks the project's public GitHub Releases and installs a same-signed APK via Android's installer. */
object InAppUpdater {
    private const val RELEASE_API = "https://api.github.com/repos/Mike130924/Christmas-GSD-Weather-Plugin/releases?per_page=1"
    private const val CONNECT_TIMEOUT_MS = 15000
    private const val READ_TIMEOUT_MS = 30000

    data class Release(val tag: String, val version: String, val apkUrl: String)

    fun check(activity: Activity, status: (String) -> Unit) {
        Thread {
            try {
                statusOnUi(activity, status, "Checking GitHub for updates…")
                val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    setRequestProperty("User-Agent", "Christmas-GSD-Weather-Plugin")
                }
                val json = try {
                    if (connection.responseCode !in 200..299) throw IllegalStateException("GitHub returned HTTP ${connection.responseCode}")
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally { connection.disconnect() }
                val releases = JSONArray(json)
                val release = if (releases.length() > 0) parseRelease(releases.getJSONObject(0)) else null
                if (release == null && releases.length() == 0) throw IllegalStateException("No GitHub release has been published yet")
                if (release == null) throw IllegalStateException("The latest GitHub release does not contain an APK asset")
                val current = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "0.0.0"
                if (compareVersions(release.version, current) <= 0) {
                    statusOnUi(activity, status, "You're up to date (version $current).")
                } else {
                    statusOnUi(activity, status, "Update ${release.version} is available. Downloading…")
                    downloadAndInstall(activity, release)
                    statusOnUi(activity, status, "Download complete. Android's installer will ask you to confirm the update.")
                }
            } catch (e: Exception) {
                statusOnUi(activity, status, "Update check failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }.start()
    }

    private fun parseRelease(json: JSONObject): Release? {
        if (json.optBoolean("draft", false)) return null
        val tag = json.optString("tag_name", "")
        val version = tag.removePrefix("v").removePrefix("V")
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "")
            val url = asset.optString("browser_download_url", "")
            if (name.endsWith(".apk", ignoreCase = true) && url.startsWith("https://")) return Release(tag, version, url)
        }
        return null
    }

    private fun compareVersions(a: String, b: String): Int {
        val left = a.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val right = b.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = (left.getOrElse(i) { 0 }).compareTo(right.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    private fun downloadAndInstall(activity: Activity, release: Release) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            activity.startActivity(intent)
            throw IllegalStateException("Allow installs from this app in Android settings, return here, then check for updates again")
        }

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(activity.packageName)
        }
        val installer = activity.packageManager.packageInstaller
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Christmas-GSD-Weather-Plugin")
                    setRequestProperty("Accept", "application/vnd.android.package-archive")
                }
                try {
                    if (connection.responseCode !in 200..299) throw IllegalStateException("APK download returned HTTP ${connection.responseCode}")
                    connection.inputStream.use { input ->
                        session.openWrite("base.apk", 0, connection.contentLength.toLong()).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                            session.fsync(output)
                        }
                    }
                } finally { connection.disconnect() }
                val callback = Intent(activity, UpdateInstallReceiver::class.java)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                val pending = PendingIntent.getBroadcast(activity, sessionId, callback, flags)
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }

    private fun statusOnUi(activity: Activity, callback: (String) -> Unit, message: String) {
        activity.runOnUiThread { if (!activity.isFinishing) callback(message) }
    }
}
