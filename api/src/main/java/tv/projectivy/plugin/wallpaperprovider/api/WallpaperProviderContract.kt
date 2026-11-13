package tv.projectivy.plugin.wallpaperprovider.api
object WallpaperProviderContract {
    const val ACTION_WALLPAPER_PROVIDER_UPDATED="tv.projectivy.plugin.action.WALLPAPER_PROVIDER_UPDATED"
    const val EXTRA_PROVIDER_ID="tv.projectivy.plugin.extra.PROVIDER_ID"
    const val EXTRA_UPDATE_REASON="tv.projectivy.plugin.extra.UPDATE_REASON"
    object UpdateReason { const val PREFS_CHANGED=1; const val DATA_CHANGED=2 }
}
