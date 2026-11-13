package com.christmasgsd.weather.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/** Read-only exported endpoint so Projectivy can load the generated wallpaper image. */
class WallpaperImageProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun getType(uri: Uri): String = "image/png"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Read only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read only")

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read-only provider")
        val appContext = requireNotNull(context)
        val file = when (uri.pathSegments.firstOrNull()) {
            "current.png" -> File(appContext.filesDir, "wallpaper/current.png")
            "backgrounds" -> {
                val name = uri.pathSegments.getOrNull(1) ?: throw FileNotFoundException("Missing background name")
                if (!name.matches(Regex("background_[0-9]+_[0-9]+\\.(jpg|png|webp)"))) throw FileNotFoundException("Invalid background name")
                File(appContext.filesDir, "restored-backgrounds/$name")
            }
            else -> throw FileNotFoundException("Unknown wallpaper")
        }
        if (!file.isFile) throw FileNotFoundException("Wallpaper file not found")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
}
