package tv.projectivy.plugin.wallpaperprovider.api

import android.os.Parcelable
import androidx.annotation.IntDef
import kotlinx.parcelize.Parcelize

@Target(AnnotationTarget.TYPE)
@Retention(AnnotationRetention.SOURCE)
@IntDef(0, 1, 2, 3, 4, 5)
annotation class WallpaperType {
    companion object { const val IMAGE=0; const val DRAWABLE=1; const val ANIMATED_DRAWABLE=2; const val LOTTIE=3; const val VIDEO=4; const val COLOR=5 }
}
@Target(AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
@IntDef(0, 1, 2, 3, 4, 5)
annotation class WallpaperDisplayMode {
    companion object { const val DEFAULT=0; const val CROP=1; const val STRETCH=2; const val BLUR=3; const val FIT_CENTER=4; const val FIT_CENTER_BLURRED_BG=5 }
}
@Parcelize
data class Wallpaper(val uri:String, val type:@WallpaperType Int=WallpaperType.IMAGE, val displayMode:@WallpaperDisplayMode Int=WallpaperDisplayMode.CROP, val title:String?=null, val source:String?=null, val author:String?=null, val actionUri:String?=null): Parcelable
