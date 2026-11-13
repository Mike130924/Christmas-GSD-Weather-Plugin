
package tv.projectivy.plugin.wallpaperprovider.api

import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.IntDef

@Target(AnnotationTarget.TYPE)
@Retention(AnnotationRetention.SOURCE)
@IntDef(1, 2, 4, 8, 16)
annotation class WallpaperUpdateEventType {
    companion object {
        const val TIME_ELAPSED = 1
        const val NOW_PLAYING_CHANGED = 2
        const val CARD_FOCUSED = 4
        const val PROGRAM_CARD_FOCUSED = 8
        const val LAUNCHER_IDLE_MODE_CHANGED = 16
    }
}

sealed class Event(open val eventType: Int) : Parcelable {

    final override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<Event> =
            object : Parcelable.Creator<Event> {

                override fun createFromParcel(parcel: Parcel): Event {
                    return when (val type = parcel.readInt()) {
                        WallpaperUpdateEventType.TIME_ELAPSED ->
                            TimeElapsed

                        WallpaperUpdateEventType.NOW_PLAYING_CHANGED ->
                            NowPlayingChanged(
                                isPlaying = parcel.readInt() != 0,
                                title = parcel.readString(),
                                artist = parcel.readString(),
                                album = parcel.readString(),
                                iconUri = parcel.readString()
                            )

                        WallpaperUpdateEventType.CARD_FOCUSED ->
                            CardFocused(
                                lightColor = parcel.readInt(),
                                darkColor = parcel.readInt(),
                                title = parcel.readString(),
                                packageName = parcel.readString()
                            )

                        WallpaperUpdateEventType.PROGRAM_CARD_FOCUSED -> {
                            val title = parcel.readString()
                            val typeValue = parcel.readInt()
                            val type = if (typeValue == -1) null else typeValue

                            ProgramCardFocused(
                                title = title,
                                type = type,
                                iconUri = parcel.readString(),
                                iconAspectRatio = parcel.readInt()
                            )
                        }

                        WallpaperUpdateEventType.LAUNCHER_IDLE_MODE_CHANGED ->
                            LauncherIdleModeChanged(
                                isIdle = parcel.readInt() != 0
                            )

                        else -> throw IllegalArgumentException(
                            "Unknown wallpaper update event type: $type"
                        )
                    }
                }

                override fun newArray(size: Int): Array<Event?> =
                    arrayOfNulls(size)
            }
    }

    data object TimeElapsed : Event(
        WallpaperUpdateEventType.TIME_ELAPSED
    ) {
        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeInt(eventType)
        }
    }

    data class NowPlayingChanged(
        override val eventType: Int = WallpaperUpdateEventType.NOW_PLAYING_CHANGED,
        val isPlaying: Boolean = true,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val iconUri: String? = null
    ) : Event(eventType) {

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeInt(eventType)
            dest.writeInt(if (isPlaying) 1 else 0)
            dest.writeString(title)
            dest.writeString(artist)
            dest.writeString(album)
            dest.writeString(iconUri)
        }
    }

    data class CardFocused(
        override val eventType: Int = WallpaperUpdateEventType.CARD_FOCUSED,
        val lightColor: Int = 0,
        val darkColor: Int = 0,
        val title: String? = null,
        val packageName: String? = null
    ) : Event(eventType) {

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeInt(eventType)
            dest.writeInt(lightColor)
            dest.writeInt(darkColor)
            dest.writeString(title)
            dest.writeString(packageName)
        }
    }

    data class ProgramCardFocused(
        override val eventType: Int = WallpaperUpdateEventType.PROGRAM_CARD_FOCUSED,
        val title: String? = null,
        val type: Int? = null,
        val iconUri: String? = null,
        val iconAspectRatio: Int = 0
    ) : Event(eventType) {

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeInt(eventType)
            dest.writeString(title)
            dest.writeInt(type ?: -1)
            dest.writeString(iconUri)
            dest.writeInt(iconAspectRatio)
        }
    }

    data class LauncherIdleModeChanged(
        override val eventType: Int = WallpaperUpdateEventType.LAUNCHER_IDLE_MODE_CHANGED,
        val isIdle: Boolean = true
    ) : Event(eventType) {

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeInt(eventType)
            dest.writeInt(if (isIdle) 1 else 0)
        }
    }
}
