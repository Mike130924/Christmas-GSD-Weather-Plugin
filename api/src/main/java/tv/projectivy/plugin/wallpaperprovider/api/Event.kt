package tv.projectivy.plugin.wallpaperprovider.api

import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.IntDef
import kotlinx.parcelize.Parcelize

@Target(AnnotationTarget.TYPE)
@Retention(AnnotationRetention.SOURCE)
@IntDef(1, 2, 4, 8, 16)
annotation class WallpaperUpdateEventType {
    companion object { const val TIME_ELAPSED=1; const val NOW_PLAYING_CHANGED=2; const val CARD_FOCUSED=4; const val PROGRAM_CARD_FOCUSED=8; const val LAUNCHER_IDLE_MODE_CHANGED=16 }
}
sealed class Event(open val eventType:Int):Parcelable {
    companion object CREATOR:Parcelable.Creator<Event> {
        override fun createFromParcel(parcel:Parcel):Event? {
            val pos=parcel.dataPosition(); val type=parcel.readInt(); parcel.setDataPosition(pos)
            return when(type) {
                1 -> TimeElapsed.CREATOR.createFromParcel(parcel)
                2 -> NowPlayingChanged.CREATOR.createFromParcel(parcel)
                4 -> CardFocused.CREATOR.createFromParcel(parcel)
                8 -> ProgramCardFocused.CREATOR.createFromParcel(parcel)
                16 -> LauncherIdleModeChanged.CREATOR.createFromParcel(parcel)
                else -> null
            }
        }
        override fun newArray(size:Int)=arrayOfNulls<Event>(size)
    }
    @Parcelize data object TimeElapsed:Event(1)
    @Parcelize data class NowPlayingChanged(override val eventType:Int=2,val isPlaying:Boolean=true,val title:String?=null,val artist:String?=null,val album:String?=null,val iconUri:String?=null):Event(2)
    @Parcelize data class CardFocused(override val eventType:Int=4,val lightColor:Int=0,val darkColor:Int=0,val title:String?=null,val packageName:String?=null):Event(4)
    @Parcelize data class ProgramCardFocused(override val eventType:Int=8,val title:String?=null,val type:Int?=null,val iconUri:String?=null,val iconAspectRatio:Int=0):Event(8)
    @Parcelize data class LauncherIdleModeChanged(override val eventType:Int=16,val isIdle:Boolean=true):Event(16)
}
