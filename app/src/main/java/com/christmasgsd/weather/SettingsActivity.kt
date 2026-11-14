package com.christmasgsd.weather

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.EditorInfo
import android.text.InputType
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import android.widget.GridLayout
import org.json.JSONArray
import com.christmasgsd.weather.themes.ThemeProfiles
import com.christmasgsd.weather.backup.PluginBackup

/** Basic TV-remote-friendly location setup; visual customisation controls are added separately. */
class SettingsActivity : Activity() {
    private lateinit var latitude: EditText
    private lateinit var longitude: EditText
    private lateinit var locationName: EditText
    private lateinit var themeName: TextView
    private lateinit var themePicker: Spinner
    private lateinit var burnInProtection: CheckBox
    private lateinit var fullScreenWhenIdle: CheckBox
    private lateinit var appRowPosition: SeekBar
    private lateinit var appRowPositionLabel: TextView
    private val weatherOptions = linkedMapOf<String, CheckBox>()
    private lateinit var worldWeatherWatch: Spinner
    private lateinit var alertFeedUrl: EditText
    private lateinit var alertApiKey: EditText
    private lateinit var warningDisplayMode: Spinner
    private lateinit var showWarningDetails: CheckBox
    private lateinit var readoutSize: SeekBar
    private lateinit var readoutSizeLabel: TextView
    private lateinit var overlayTx: SeekBar
    private lateinit var overlayTy: SeekBar
    private lateinit var overlaySx: SeekBar
    private lateinit var overlaySy: SeekBar
    private lateinit var overlayOx: SeekBar
    private lateinit var overlayOy: SeekBar
    private lateinit var panelScaleX: SeekBar
    private lateinit var panelScaleY: SeekBar
    private lateinit var palettePreset: Spinner
    private val paletteFields = linkedMapOf<String, EditText>()
    private lateinit var showClock: CheckBox
    private lateinit var showDate: CheckBox
    private lateinit var clockFormat: Spinner
    private lateinit var dateFormat: Spinner
    private lateinit var clockColor: EditText
    private lateinit var dateColor: EditText
    private lateinit var clockOpacity: SeekBar
    private lateinit var dateOpacity: SeekBar
    private lateinit var clockSize: SeekBar
    private lateinit var dateSize: SeekBar
    private lateinit var clockBold: CheckBox
    private lateinit var dateBold: CheckBox
    private lateinit var datePosition: Spinner
    private lateinit var backgroundPicker: Spinner
    private lateinit var backgroundMode: Spinner
    private lateinit var backgroundPreview: ImageView
    private lateinit var overlayPreview: ImageView
    private val savedBackgroundUris = mutableListOf<String>()
    private var selectedBackgroundUri: String? = null
    private val pickBackgroundRequest = 4107
    private val exportThemeRequest = 4108
    private val importThemeRequest = 4109
    private val exportBackupRequest = 4110
    private val importBackupRequest = 4111
    private var pendingExportTheme: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("weather", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 32, 40, 32)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        root.addView(TextView(this).apply { text = "Christmas GSD Weather"; textSize = 28f })
        root.addView(TextView(this).apply {
            text = "Set the location used for live weather. Coordinates are decimal degrees."
            textSize = 16f
            setPadding(0, 12, 0, 20)
        })
        locationName = field(root, "Location label (e.g. Peterborough)", prefs.getString("location_name", "") ?: "")
        latitude = field(root, "Latitude", prefs.getString("latitude", "") ?: "")
        longitude = field(root, "Longitude", prefs.getString("longitude", "") ?: "")
        root.addView(TextView(this).apply {
            text = "Wallpaper backgrounds"
            textSize = 22f
            setPadding(0, 26, 0, 8)
        })
        backgroundPreview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setBackgroundColor(Color.DKGRAY)
            minimumHeight = 150
        }

        root.addView(TextView(this).apply {
            text = "App updates"
            textSize = 21f
            setPadding(0, 24, 0, 6)
        })
        val updateStatus = TextView(this).apply {
            text = "Check GitHub for the latest Christmas GSD Weather release."
            textSize = 14f
            setPadding(0, 4, 0, 8)
            contentDescription = "App update status"
        }
        root.addView(Button(this).apply {
            text = "Check for updates"
            textSize = 18f
            setPadding(18, 18, 18, 18)
            setOnClickListener {
                isEnabled = false
                updateStatus.text = "Checking for updates…"
                InAppUpdater.check(this@SettingsActivity) { message ->
                    updateStatus.text = message
                    this.isEnabled = true
                }
            }
        })
        root.addView(updateStatus)

        root.addView(backgroundPreview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 220))
        backgroundPicker = Spinner(this)
        root.addView(backgroundPicker)
        loadSavedBackgrounds(prefs)
        root.addView(Button(this).apply {
            text = "Add wallpaper from device / USB"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png", "image/webp"))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                startActivityForResult(intent, pickBackgroundRequest)
            }
        })
        backgroundPicker.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                selectedBackgroundUri = savedBackgroundUris.getOrNull(position)
                val uri = selectedBackgroundUri?.let(Uri::parse)
                if (uri != null) runCatching {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                        backgroundPreview.setImageBitmap(bitmap)
                    }
                }.onFailure { Toast.makeText(this@SettingsActivity, "Could not preview this image", Toast.LENGTH_SHORT).show() }
                else backgroundPreview.setImageResource(R.drawable.christmas_gsd_background)
            }
        }
        backgroundMode = spinner(root, "Wallpaper display mode", listOf("Fill / crop", "Fit", "Stretch", "Centre"),
            prefs.getString("background_mode", "FILL_CROP")?.let { listOf("FILL_CROP", "FIT", "STRETCH", "CENTRE").indexOf(it).coerceAtLeast(0) } ?: 0)
        root.addView(Button(this).apply {
            text = "Remove selected saved wallpaper"
            setOnClickListener {
                val index = backgroundPicker.selectedItemPosition
                if (index in savedBackgroundUris.indices) {
                    savedBackgroundUris.removeAt(index)
                    persistBackgroundList()
                    rebuildBackgroundPicker()
                    Toast.makeText(this@SettingsActivity, "Saved wallpaper removed", Toast.LENGTH_SHORT).show()
                }
            }
        })

        root.addView(TextView(this).apply {
            text = "Rendered overlay preview"
            textSize = 22f
            setPadding(0, 26, 0, 8)
        })
        root.addView(TextView(this).apply {
            text = "This displays the last successfully generated wallpaper image used by the provider. Save and refresh first to see your latest changes."
            textSize = 14f
            setPadding(0, 0, 0, 8)
        })
        overlayPreview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            contentDescription = "Last rendered weather wallpaper preview"
            minimumHeight = 140
        }
        root.addView(overlayPreview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 260))
        root.addView(Button(this).apply {
            text = "Load last rendered overlay preview"
            setOnClickListener { loadLastRenderedPreview() }
        })
        loadLastRenderedPreview()

        root.addView(TextView(this).apply {
            text = "Saved themes"
            textSize = 22f
            setPadding(0, 26, 0, 8)
        })
        root.addView(TextView(this).apply {
            text = "Save the current overlay appearance as a named theme. Each theme is stored separately, so creating a new wallpaper and icon style will not replace your Christmas theme. Location settings are kept separate."
            textSize = 14f
            setPadding(0, 0, 0, 8)
        })
        root.addView(TextView(this).apply {
            text = "Display & screen protection"
            textSize = 22f
            setPadding(0, 26, 0, 8)
        })
        burnInProtection = CheckBox(this).apply {
            text = "Reduce screen burn-in"
            textSize = 17f
            isChecked = prefs.getBoolean("layout.burn_in_protection", true)
        }
        root.addView(burnInProtection)
        root.addView(TextView(this).apply {
            text = "Gently nudges the weather panel and clock by a few pixels at intervals. Recommended for OLED and plasma displays."
            textSize = 13f
            setPadding(8, 0, 0, 8)
        })
        appRowPositionLabel = TextView(this).apply { textSize = 16f; setPadding(0, 12, 0, 4) }
        root.addView(appRowPositionLabel)
        appRowPosition = SeekBar(this).apply {
            max = 100
            progress = prefs.getInt("layout.app_row_start_percent", 70).coerceIn(0, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    appRowPositionLabel.text = "Where your app row starts: $progress% down the screen"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        appRowPositionLabel.text = "Where your app row starts: ${appRowPosition.progress}% down the screen"
        root.addView(appRowPosition)
        fullScreenWhenIdle = CheckBox(this).apply {
            text = "Use the full screen when idle"
            textSize = 17f
            isChecked = prefs.getBoolean("layout.full_screen_when_idle", true)
        }
        root.addView(fullScreenWhenIdle)
        root.addView(TextView(this).apply {
            text = "Allows the wallpaper forecast to use the whole frame when Projectivy enters idle mode."
            textSize = 13f
            setPadding(8, 0, 0, 8)
        })

        root.addView(TextView(this).apply {
            text = "Weather readout size"
            textSize = 22f
            setPadding(0, 26, 0, 8)
        })
        readoutSizeLabel = TextView(this).apply { textSize = 16f }
        root.addView(readoutSizeLabel)
        readoutSize = SeekBar(this).apply {
            max = 60 // 75% through 135%, in one-percent steps
            val scale = (prefs.getString("readout.scale", "1.0")?.toFloatOrNull() ?: 1.0f).coerceIn(0.75f, 1.35f)
            progress = ((scale * 100f).toInt() - 75).coerceIn(0, max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    readoutSizeLabel.text = "Weather readout size: ${progress + 75}%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        readoutSizeLabel.text = "Weather readout size: ${readoutSize.progress + 75}%"
        root.addView(readoutSize)
        root.addView(TextView(this).apply {
            text = "Changes the size of the current conditions, weather details and forecast without enlarging the panel or clock."
            textSize = 13f
            setPadding(8, 0, 0, 8)
        })

        root.addView(TextView(this).apply {
            text = "Weather overlay layout"
            textSize = 22f
            setPadding(0, 28, 0, 8)
        })
        root.addView(TextView(this).apply {
            text = "Fine-tune the weather overlay independently. The drawing uses a fixed logical design space; these controls only change its position, scale and reference origin."
            textSize = 13f
            setPadding(8, 0, 0, 8)
        })
        overlayTx = seek(root, "Overlay translate X", ((prefs.getString("overlay.translate_x", "875")?.toFloatOrNull() ?: 875f) + 1000).toInt(), 3000)
        overlayTy = seek(root, "Overlay translate Y", ((prefs.getString("overlay.translate_y", "12.5")?.toFloatOrNull() ?: 12.5f) + 500).toInt(), 1500)
        overlaySx = seek(root, "Overlay horizontal scale", ((prefs.getString("overlay.scale_x", "0.5506")?.toFloatOrNull() ?: 0.5506f) * 1000).toInt(), 1500)
        overlaySy = seek(root, "Overlay vertical scale", ((prefs.getString("overlay.scale_y", "0.4402")?.toFloatOrNull() ?: 0.4402f) * 1000).toInt(), 1500)
        overlayOx = seek(root, "Overlay origin X", ((prefs.getString("overlay.origin_x", "-540")?.toFloatOrNull() ?: -540f) + 1000).toInt(), 2000)
        overlayOy = seek(root, "Overlay origin Y", ((prefs.getString("overlay.origin_y", "-80")?.toFloatOrNull() ?: -80f) + 1000).toInt(), 2000)

        root.addView(TextView(this).apply {
            text = "Weather panel size"
            textSize = 22f
            setPadding(0, 28, 0, 8)
        })
        root.addView(TextView(this).apply {
            text = "These controls resize the panel itself. They do not change the weather text or the six overlay layout controls above."
            textSize = 13f
            setPadding(8, 0, 0, 8)
        })
        panelScaleX = seek(root, "Panel horizontal scale (%)", prefs.getInt("panel.scale_x", 100).coerceIn(50, 150), 150).apply { if (progress < 50) progress = 50 }
        panelScaleY = seek(root, "Panel vertical scale (%)", prefs.getInt("panel.scale_y", 100).coerceIn(50, 150), 150).apply { if (progress < 50) progress = 50 }

        root.addView(TextView(this).apply {
            text = "Colour palette & appearance"
            textSize = 22f
            setPadding(0, 28, 0, 8)
        })
        palettePreset = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_item,
                listOf("Custom", "Christmas Red", "Arctic Blue", "Winter Forest", "Golden Christmas")).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(0)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    val preset = paletteValues(position)
                    if (preset != null) preset.forEach { (key, value) -> paletteFields[key]?.setText(value) }
                }
            }
        }
        root.addView(palettePreset)
        val paletteDefaults = mapOf(
            "palette.panel" to "#DE0F1117", "palette.border" to "#FFFF2A3A", "palette.glow" to "#FFFF192D",
            "palette.primary_text" to "#FFFFFFFF", "palette.secondary_text" to "#FFCDD3DE",
            "palette.temperature" to "#FFFFFFFF", "palette.weather_icon" to "#FFFFFFFF",
            "palette.divider" to "#5FFFFFFF", "palette.festive" to "#FF23A05B", "palette.snow" to "#FFFFFFFF"
        )
        val paletteLabels = mapOf(
            "palette.panel" to "Panel background (8-digit #AARRGGBB)", "palette.border" to "Neon border colour",
            "palette.glow" to "Neon glow colour", "palette.primary_text" to "Main text colour",
            "palette.secondary_text" to "Secondary text colour", "palette.temperature" to "Temperature colour",
            "palette.weather_icon" to "Weather icon colour", "palette.divider" to "Forecast divider colour",
            "palette.festive" to "Festive greenery colour", "palette.snow" to "Snow decoration colour"
        )
        // Explicit TV-friendly rows: each row contains three or four equally
        // weighted cells. Avoid GridLayout auto-placement, which can collapse
        // into a single column on some Android TV density/configuration combos.
        val paletteColumns = if (resources.configuration.screenWidthDp >= 1100) 4 else 3
        paletteLabels.entries.toList().chunked(paletteColumns).forEach { rowItems ->
            val paletteRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            rowItems.forEach { (key, label) ->
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(8, 4, 8, 8)
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }
                val compactLabel = when (key) {
                    "palette.panel" -> "Panel background"
                    "palette.border" -> "Neon border"
                    "palette.glow" -> "Neon glow"
                    "palette.primary_text" -> "Main text"
                    "palette.secondary_text" -> "Secondary text"
                    "palette.temperature" -> "Temperature"
                    "palette.weather_icon" -> "Weather icon"
                    "palette.divider" -> "Forecast divider"
                    "palette.festive" -> "Festive greenery"
                    "palette.snow" -> "Snow decoration"
                    else -> label
                }
                paletteFields[key] = field(
                    cell,
                    compactLabel,
                    prefs.getString(key, paletteDefaults.getValue(key)) ?: paletteDefaults.getValue(key)
                )
                // Keep each cell compact and prevent long labels expanding the row.
                (cell.getChildAt(0) as? TextView)?.apply {
                    textSize = 13f
                    maxLines = 2
                }
                paletteRow.addView(cell)
            }
            // Fill the unused final cells so the last row remains aligned.
            repeat(paletteColumns - rowItems.size) {
                paletteRow.addView(LinearLayout(this), LinearLayout.LayoutParams(0, 1, 1f))
            }
            root.addView(paletteRow)
        }
        root.addView(TextView(this).apply {
            text = "Use #RRGGBB or #AARRGGBB. Panel transparency is controlled by the alpha value in its 8-digit colour. Presets can be customised before saving a theme."
            textSize = 13f
            setPadding(8, 4, 0, 8)
        })

        root.addView(TextView(this).apply {
            text = "Clock & date"
            textSize = 22f
            setPadding(0, 28, 0, 8)
        })
        showClock = CheckBox(this).apply { text = "Show time"; isChecked = prefs.getBoolean("clock.visible", true) }
        showDate = CheckBox(this).apply { text = "Show date"; isChecked = prefs.getBoolean("date.visible", true) }
        root.addView(showClock)
        root.addView(showDate)
        clockFormat = spinner(root, "Time format", listOf("24-hour (14:38)", "12-hour (2:38 PM)"), if (prefs.getBoolean("clock.24_hour", true)) 0 else 1)
        dateFormat = spinner(root, "Date format", listOf("Thu 12 Nov", "Thursday 12 November", "12/11/2026", "12 November 2026", "2026-11-12"),
            ClockDatePreferences.DATE_PATTERNS.indexOf(prefs.getString("date.pattern", "EEE d MMM")).coerceAtLeast(0))
        clockColor = field(root, "Clock colour (#RRGGBB)", String.format("#%06X", 0xFFFFFF and prefs.getInt("clock.color", Color.WHITE)))
        dateColor = field(root, "Date colour (#RRGGBB)", String.format("#%06X", 0xFFFFFF and prefs.getInt("date.color", Color.LTGRAY)))
        clockOpacity = seek(root, "Clock opacity", prefs.getInt("clock.opacity", 100), 100)
        dateOpacity = seek(root, "Date opacity", prefs.getInt("date.opacity", 90), 100)
        clockSize = seek(root, "Clock size", prefs.getInt("clock.size", 34), 110)
        dateSize = seek(root, "Date size", prefs.getInt("date.size", 18), 64)
        clockBold = CheckBox(this).apply { text = "Bold clock"; isChecked = prefs.getBoolean("clock.bold", true) }
        dateBold = CheckBox(this).apply { text = "Bold date"; isChecked = prefs.getBoolean("date.bold", false) }
        root.addView(clockBold)
        root.addView(dateBold)
        datePosition = spinner(root, "Date position", listOf("Below time", "Beside time", "Above time"),
            listOf("below_time", "beside_time", "above_time").indexOf(prefs.getString("date.position", "below_time")).coerceAtLeast(0))

        root.addView(TextView(this).apply {
            text = "Weather information"
            textSize = 22f
            setPadding(0, 28, 0, 8)
        })
        root.addView(TextView(this).apply {
            text = "Choose which information appears on the wallpaper. These options are saved with the selected theme."
            textSize = 14f
            setPadding(0, 0, 0, 8)
        })
        addWeatherOption(root, prefs, "weather.show_updated", "Show last updated time",
            "Small line showing when the weather data was last refreshed.", false)
        addWeatherOption(root, prefs, "weather.show_severe_alerts", "Severe weather alerts",
            "Show relevant official UK weather warnings when a supported warning feed is connected.", true)
        alertFeedUrl = field(root, "Met Office warning feed URL", prefs.getString("alerts.feed_url", "") ?: "")
        alertApiKey = field(root, "Met Office NSWWS API key", prefs.getString("alerts.api_key", "") ?: "")
        alertApiKey.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        warningDisplayMode = spinner(root, "Warning display position", listOf("Separate banner", "Inside weather panel"),
            if (prefs.getString("alerts.display_mode", "separate_banner") == "inside_panel") 1 else 0)
        showWarningDetails = CheckBox(this).apply {
            text = "Show warning details and countdown"
            isChecked = prefs.getBoolean("alerts.show_details", true)
        }
        root.addView(showWarningDetails)
        root.addView(TextView(this).apply {
            text = "The Met Office supplies the feed URL and API key after NSWWS API access is approved. Leave these blank until you have credentials."
            textSize = 13f
            setPadding(8, 0, 0, 8)
        })
        addWeatherOption(root, prefs, "weather.show_hourly", "Show hourly forecast",
            "Next 6 hours with temperature and chance of rain.", false)
        addWeatherOption(root, prefs, "weather.show_daily", "Show daily forecast",
            "Next 6 days with highs and lows.", true)
        addWeatherOption(root, prefs, "weather.show_extra_stats", "Show extra stats",
            "Humidity, UV, dew point, visibility and pressure.", false)
        addWeatherOption(root, prefs, "weather.show_sun_times", "Show sun times",
            "Sunrise, sunset and daylight remaining.", false)
        root.addView(TextView(this).apply {
            text = "World weather watch"
            textSize = 17f
            setPadding(0, 18, 0, 4)
        })
        worldWeatherWatch = Spinner(this)
        worldWeatherWatch.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            listOf("Off — local weather only", "Nearby areas", "National overview", "Worldwide overview")).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        worldWeatherWatch.setSelection(prefs.getInt("weather.world_watch_mode", 0).coerceIn(0, 3))
        root.addView(worldWeatherWatch)

        root.addView(TextView(this).apply {
            text = "Theme name"
            textSize = 15f
            setPadding(0, 10, 0, 4)
        })
        themeName = TextView(this).apply {
            text = "No theme name entered"
            textSize = 18f
            setPadding(12, 14, 12, 14)
            setBackgroundColor(Color.DKGRAY)
            contentDescription = "Entered theme name"
        }
        root.addView(themeName)
        root.addView(Button(this).apply {
            text = "Enter theme name"
            setOnClickListener { showThemeNameDialog() }
        })
        root.addView(Button(this).apply {
            text = "Save current layout as a new theme"
            setOnClickListener { confirmSaveTheme() }
        })
        themePicker = Spinner(this)
        root.addView(themePicker)
        root.addView(Button(this).apply {
            text = "Update selected theme with current settings"
            setOnClickListener {
                val selected = themePicker.selectedItem?.toString().orEmpty()
                if (selected.isBlank()) {
                    Toast.makeText(this@SettingsActivity, "Choose a saved theme first", Toast.LENGTH_SHORT).show()
                } else {
                    android.app.AlertDialog.Builder(this@SettingsActivity)
                        .setTitle("Replace saved theme?")
                        .setMessage("This will replace '$selected' with the current settings and wallpaper selection. This cannot be undone.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Replace theme") { _, _ ->
                            val ok = ThemeProfiles.update(getSharedPreferences("weather", MODE_PRIVATE), selected)
                            Toast.makeText(this@SettingsActivity, if (ok) "Updated theme: $selected" else "Could not update theme", Toast.LENGTH_LONG).show()
                            if (ok) refreshOverlayOnly()
                            refreshThemePicker()
                        }.show()
                }
            }
        })
        root.addView(Button(this).apply {
            text = "Delete selected theme"
            setOnClickListener {
                val selected = themePicker.selectedItem?.toString().orEmpty()
                if (selected.isBlank()) {
                    Toast.makeText(this@SettingsActivity, "Choose a saved theme first", Toast.LENGTH_SHORT).show()
                } else {
                    android.app.AlertDialog.Builder(this@SettingsActivity)
                        .setTitle("Delete saved theme?")
                        .setMessage("Delete '$selected'? This only removes the saved profile; it does not delete your wallpaper image.")
                        .setNegativeButton("Keep theme", null)
                        .setPositiveButton("Delete") { _, _ ->
                            val ok = ThemeProfiles.delete(getSharedPreferences("weather", MODE_PRIVATE), selected)
                            Toast.makeText(this@SettingsActivity, if (ok) "Deleted theme: $selected" else "Could not delete theme", Toast.LENGTH_LONG).show()
                            refreshThemePicker()
                        }.show()
                }
            }
        })
        root.addView(Button(this).apply {
            text = "Export selected theme to a file"
            setOnClickListener {
                val selected = themePicker.selectedItem?.toString().orEmpty()
                if (selected.isBlank()) {
                    Toast.makeText(this@SettingsActivity, "Choose a saved theme first", Toast.LENGTH_SHORT).show()
                } else {
                    pendingExportTheme = selected
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                        putExtra(Intent.EXTRA_TITLE, selected.replace(Regex("[^A-Za-z0-9 _-]"), "_") + ".gsdtheme.json")
                    }
                    startActivityForResult(intent, exportThemeRequest)
                }
            }
        })
        root.addView(Button(this).apply {
            text = "Import theme from a file"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivityForResult(intent, importThemeRequest)
            }
        })
        root.addView(TextView(this).apply {
            text = "Full plugin backup & restore"
            textSize = 22f
            setPadding(0, 26, 0, 8)
        })
        root.addView(TextView(this).apply {
            text = "Backs up plugin settings, saved themes and the actual image files for saved wallpapers. The Met Office API key is deliberately excluded from the file to protect your credentials. Restore keeps a key already configured on this device; on a new device, enter the key again. Restoring replaces the current plugin configuration, so keep a copy of your existing setup first."
            textSize = 14f
            setPadding(0, 0, 0, 8)
        })
        root.addView(Button(this).apply {
            text = "Export complete plugin backup"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "Christmas GSD Plugin Backup.gsdbackup.json")
                }
                startActivityForResult(intent, exportBackupRequest)
            }
        })
        root.addView(Button(this).apply {
            text = "Restore complete plugin backup"
            setOnClickListener {
                android.app.AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Replace current plugin setup?")
                    .setMessage("Restoring replaces current plugin settings and saved themes with the backup. Saved wallpaper images in the backup are copied into this app so they do not depend on the original USB drive.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Choose backup") { _, _ ->
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/json"
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivityForResult(intent, importBackupRequest)
                    }.show()
            }
        })
        root.addView(Button(this).apply {
            text = "Load selected theme & refresh overlay"
            setOnClickListener {
                val selected = themePicker.selectedItem?.toString().orEmpty()
                val prefsNow = getSharedPreferences("weather", MODE_PRIVATE)
                if (selected.isNotBlank() && ThemeProfiles.apply(prefsNow, selected)) {
                    refreshOverlayOnly()
                    Toast.makeText(this@SettingsActivity, "Loaded theme: $selected", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@SettingsActivity, "Choose a saved theme first", Toast.LENGTH_LONG).show()
                }
            }
        })
        refreshThemePicker()
        root.addView(Button(this).apply {
            text = "Save all changes & refresh overlay"
            textSize = 18f
            setPadding(18, 18, 18, 18)
            setOnClickListener { saveAndRefresh() }
        })
        root.addView(TextView(this).apply {
            text = "Use this after changing settings. It saves the current settings first, then rebuilds the wallpaper overlay and asks Projectivy to reload it."
            textSize = 14f
            setPadding(0, 12, 0, 4)
        })
        root.addView(TextView(this).apply {
            text = "Weather data: Open-Meteo. Leave coordinates blank to use the bundled background until a location is configured."
            textSize = 13f
            setPadding(0, 18, 0, 0)
        })
        // The settings screen is substantially longer than a TV viewport. Keep every
        // control reachable with a remote by placing the vertical form in a ScrollView.
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            clipToPadding = false
            setPadding(0, 0, 0, 24)
            addView(root, android.widget.FrameLayout.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,
    ViewGroup.LayoutParams.WRAP_CONTENT,
))
        }
        setContentView(scroll)
    }

    private fun spinner(root: LinearLayout, label: String, options: List<String>, selected: Int): Spinner {
        root.addView(TextView(this).apply { text = label; textSize = 15f; setPadding(0, 10, 0, 4) })
        return Spinner(this).also { control ->
            control.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            control.setSelection(selected.coerceIn(options.indices))
            root.addView(control)
        }
    }

    private fun seek(root: LinearLayout, label: String, value: Int, maxValue: Int): SeekBar {
        root.addView(TextView(this).apply { text = "$label: $value"; textSize = 15f; setPadding(0, 10, 0, 2) })
        return SeekBar(this).also { control ->
            control.max = maxValue
            control.progress = value.coerceIn(0, maxValue)
            control.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    (root.getChildAt(root.indexOfChild(control) - 1) as? TextView)?.text = "$label: $progress"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
            root.addView(control)
        }
    }

    private fun parseUiColor(input: EditText): Int {
        val raw = input.text.toString().trim()
        return try {
            Color.parseColor(if (raw.length == 7) "#FF${raw.substring(1)}" else raw)
        } catch (_: IllegalArgumentException) {
            input.error = "Use #RRGGBB or #AARRGGBB"
            throw IllegalArgumentException("Invalid clock/date colour")
        }
    }

    private fun paletteValues(position: Int): Map<String, String>? {
        val common = mapOf("palette.primary_text" to "#FFFFFFFF", "palette.secondary_text" to "#FFCDD3DE",
            "palette.temperature" to "#FFFFFFFF", "palette.weather_icon" to "#FFFFFFFF", "palette.divider" to "#5FFFFFFF", "palette.snow" to "#FFFFFFFF")
        return when (position) {
            1 -> common + mapOf("palette.panel" to "#DE0F1117", "palette.border" to "#FFFF2A3A", "palette.glow" to "#FFFF192D", "palette.festive" to "#FF23A05B")
            2 -> common + mapOf("palette.panel" to "#DE071827", "palette.border" to "#FF48C8FF", "palette.glow" to "#FF168BFF", "palette.festive" to "#FF6EC6A5")
            3 -> common + mapOf("palette.panel" to "#DE0B1B16", "palette.border" to "#FF38D996", "palette.glow" to "#FF159B69", "palette.festive" to "#FF238B50")
            4 -> common + mapOf("palette.panel" to "#DE21180B", "palette.border" to "#FFFFC857", "palette.glow" to "#FFFF9F1C", "palette.festive" to "#FF3E9B62")
            else -> null
        }
    }

    private fun addWeatherOption(
        root: LinearLayout,
        prefs: android.content.SharedPreferences,
        key: String,
        title: String,
        description: String,
        defaultValue: Boolean,
    ) {
        val check = CheckBox(this).apply {
            text = title
            textSize = 17f
            isChecked = prefs.getBoolean(key, defaultValue)
        }
        weatherOptions[key] = check
        root.addView(check)
        root.addView(TextView(this).apply {
            text = description
            textSize = 13f
            setPadding(8, 0, 0, 8)
        })
    }

    private fun confirmSaveTheme() {
        val name = themeName.text.toString().trim()
        if (name.isBlank() || name == "No theme name entered") {
            Toast.makeText(this, "Enter a theme name first", Toast.LENGTH_SHORT).show()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Confirm new theme")
            .setMessage("Save the current appearance as '$name'? Existing themes will not be overwritten.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm save") { _, _ ->
                when (ThemeProfiles.saveWithResult(getSharedPreferences("weather", MODE_PRIVATE), name)) {
                    ThemeProfiles.SaveResult.SUCCESS -> {
                        themeName.text = "No theme name entered"
                        refreshThemePicker()
                        Toast.makeText(this, "Theme saved separately: $name", Toast.LENGTH_LONG).show()
                    }
                    ThemeProfiles.SaveResult.DUPLICATE -> Toast.makeText(this,
                        "A theme named '$name' already exists. Choose a different name.", Toast.LENGTH_LONG).show()
                    ThemeProfiles.SaveResult.INVALID_NAME -> Toast.makeText(this,
                        "Enter a valid theme name (1–60 characters).", Toast.LENGTH_LONG).show()
                    ThemeProfiles.SaveResult.STORAGE_ERROR -> Toast.makeText(this,
                        "The theme could not be saved because storage failed. Your entered name has been kept.", Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun showThemeNameDialog() {
        val editor = EditText(this).apply {
            setSingleLine(true)
            textSize = 20f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_DONE
            setText(themeName.text)
            setSelection(text.length)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("New theme name")
            .setMessage("Enter a name using the TV keyboard.")
            .setView(editor)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Use name", null)
            .create()
        dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        dialog.setOnShowListener {
            editor.requestFocus()
            editor.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(editor, InputMethodManager.SHOW_FORCED)
            }, 300)
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val entered = editor.text.toString().trim()
                if (entered.isBlank()) {
                    editor.error = "Enter a theme name"
                    editor.requestFocus()
                } else {
                    themeName.text = entered
                    dialog.dismiss()
                }
            }
            editor.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
                    true
                } else false
            }
        }
        dialog.show()
    }

    private fun field(root: LinearLayout, label: String, value: String): EditText {
        root.addView(TextView(this).apply { text = label; textSize = 15f; setPadding(0, 10, 0, 4) })
        return EditText(this).also { input ->
            input.setSingleLine(true)
            input.textSize = 18f
            input.setText(value)
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            input.imeOptions = EditorInfo.IME_ACTION_DONE
            input.isFocusable = true
            input.isFocusableInTouchMode = true
            input.isClickable = true
            input.isLongClickable = true
            // Android TV: make the field selectable with the D-pad and explicitly
            // request the on-screen keyboard when the user activates it.
            // D-pad focus alone must never summon the TV keyboard. Activation
            // (OK/click) is the deliberate request to edit this field.
            input.setOnClickListener {
                input.requestFocus()
                input.post {
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
                }
            }
            input.setOnKeyListener { _, keyCode, event ->
                if ((keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == android.view.KeyEvent.KEYCODE_ENTER) &&
                    event.action == android.view.KeyEvent.ACTION_UP) {
                    input.performClick()
                    true
                } else false
            }
            input.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(input.windowToken, 0)
                    input.clearFocus()
                    true
                } else false
            }
            root.addView(input)
        }
    }

    private fun loadSavedBackgrounds(prefs: android.content.SharedPreferences) {
        savedBackgroundUris.clear()
        runCatching {
            val array = JSONArray(prefs.getString("background.saved_uris", "[]") ?: "[]")
            for (i in 0 until array.length()) savedBackgroundUris.add(array.getString(i))
        }
        selectedBackgroundUri = prefs.getString("background_uri", null)
        if (!selectedBackgroundUri.isNullOrBlank() && selectedBackgroundUri !in savedBackgroundUris) {
            savedBackgroundUris.add(0, selectedBackgroundUri!!)
        }
        rebuildBackgroundPicker()
        val index = savedBackgroundUris.indexOf(selectedBackgroundUri)
        if (index >= 0) backgroundPicker.setSelection(index)
    }

    private fun persistBackgroundList() {
        val array = JSONArray()
        savedBackgroundUris.forEach(array::put)
        getSharedPreferences("weather", MODE_PRIVATE).edit()
            .putString("background.saved_uris", array.toString()).apply()
    }

    private fun rebuildBackgroundPicker() {
        val labels = if (savedBackgroundUris.isEmpty()) listOf("Bundled Christmas GSD wallpaper") else
            savedBackgroundUris.mapIndexed { index, value -> "Wallpaper ${index + 1} · ${Uri.parse(value).lastPathSegment ?: "Saved image"}" }
        backgroundPicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    @Deprecated("Uses the document picker result contract for broad Android TV compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            pickBackgroundRequest -> {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                    Toast.makeText(this, "Android did not grant persistent access to this image", Toast.LENGTH_LONG).show()
                    return
                }
                if (uri.toString() !in savedBackgroundUris) savedBackgroundUris.add(uri.toString())
                selectedBackgroundUri = uri.toString()
                persistBackgroundList()
                rebuildBackgroundPicker()
                backgroundPicker.setSelection(savedBackgroundUris.indexOf(uri.toString()))
                Toast.makeText(this, "Wallpaper added. Save changes to apply it.", Toast.LENGTH_LONG).show()
            }
            exportThemeRequest -> {
                val name = pendingExportTheme ?: return
                val json = ThemeProfiles.exportJson(getSharedPreferences("weather", MODE_PRIVATE), name)
                if (json == null) {
                    Toast.makeText(this, "Could not export '$name'", Toast.LENGTH_LONG).show()
                    return
                }
                runCatching {
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: error("Could not open the selected destination.")
                }.onSuccess {
                    Toast.makeText(this, "Exported theme: $name", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(this, "Export failed: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                }
                pendingExportTheme = null
            }
            exportBackupRequest -> {
                runCatching {
                    contentResolver.openOutputStream(uri, "wt")?.use { PluginBackup.write(this, it) }
                        ?: error("Could not open the selected backup destination.")
                }.onSuccess {
                    Toast.makeText(this, "Complete plugin backup exported.", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(this, "Backup failed: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
            importBackupRequest -> {
                runCatching {
                    contentResolver.openInputStream(uri)?.use { PluginBackup.restore(this, it) }
                        ?: error("Could not read the selected backup.")
                }.onSuccess {
                    loadSavedBackgrounds(getSharedPreferences("weather", MODE_PRIVATE))
                    refreshThemePicker()
                    refreshOverlayOnly()
                    Toast.makeText(this, "Plugin backup restored. Wallpaper refresh requested.", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(this, "Restore failed: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
            importThemeRequest -> {
                val imported = runCatching {
                    val json = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: error("Could not read the selected file.")
                    ThemeProfiles.importJson(getSharedPreferences("weather", MODE_PRIVATE), json)
                        ?: error("Import failed. Check that this is a valid theme file and that its name is not already saved.")
                }
                imported.onSuccess { name ->
                    refreshThemePicker()
                    Toast.makeText(this, "Imported theme: $name. Select it and load to apply.", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(this, "Import failed: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadLastRenderedPreview() {
        val image = java.io.File(filesDir, "wallpaper/current.png")
        if (!image.isFile) {
            overlayPreview.setImageDrawable(null)
            overlayPreview.setBackgroundColor(Color.DKGRAY)
            Toast.makeText(this, "No rendered wallpaper yet. Configure a location and save & refresh first.", Toast.LENGTH_LONG).show()
            return
        }
        val bitmap = android.graphics.BitmapFactory.decodeFile(image.absolutePath)
        if (bitmap == null) {
            Toast.makeText(this, "The last rendered preview could not be opened.", Toast.LENGTH_LONG).show()
            return
        }
        overlayPreview.setImageBitmap(bitmap)
        overlayPreview.setBackgroundColor(Color.BLACK)
    }

    private fun refreshThemePicker() {
        val names = ThemeProfiles.names(getSharedPreferences("weather", MODE_PRIVATE))
        themePicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun refreshOverlayOnly() {
        runCatching {
            startService(android.content.Intent(this, WeatherWallpaperService::class.java)
                .setAction(WeatherWallpaperService.ACTION_REFRESH))
        }.onFailure {
            Toast.makeText(this, "Theme loaded, but refresh could not start: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    /** Persist the edited fields before asking the wallpaper service to rebuild the overlay. */
    private fun saveAndRefresh() {
        val lat = latitude.text.toString().trim()
        val lon = longitude.text.toString().trim()
        val latitudeValue = lat.toDoubleOrNull()
        val longitudeValue = lon.toDoubleOrNull()
        if (lat.isNotEmpty() && (latitudeValue == null || latitudeValue !in -90.0..90.0)) {
            latitude.error = "Enter a latitude from -90 to 90"
            return
        }
        if (lon.isNotEmpty() && (longitudeValue == null || longitudeValue !in -180.0..180.0)) {
            longitude.error = "Enter a longitude from -180 to 180"
            return
        }
        if (lat.isEmpty() != lon.isEmpty()) {
            Toast.makeText(this, "Enter both coordinates, or leave both blank", Toast.LENGTH_LONG).show()
            return
        }
        var invalidPalette = false
        paletteFields.forEach { (_, input) ->
            val value = input.text.toString().trim()
            try {
                Color.parseColor(value)
                if (value.length != 7 && value.length != 9) throw IllegalArgumentException("Expected RGB or ARGB hex")
                input.error = null
            } catch (_: IllegalArgumentException) {
                input.error = "Use #RRGGBB or #AARRGGBB"
                invalidPalette = true
            }
        }
        if (invalidPalette) return
        try {
            parseUiColor(clockColor)
            parseUiColor(dateColor)
        } catch (_: IllegalArgumentException) {
            return
        }
        val saved = getSharedPreferences("weather", MODE_PRIVATE).edit()
            .putString("latitude", lat.ifEmpty { null })
            .putString("longitude", lon.ifEmpty { null })
            .putString("location_name", locationName.text.toString().trim())
            .putString("background_uri", selectedBackgroundUri)
            .putString("background_mode", listOf("FILL_CROP", "FIT", "STRETCH", "CENTRE")[backgroundMode.selectedItemPosition])
            .putBoolean("layout.burn_in_protection", burnInProtection.isChecked)
            .putInt("layout.app_row_start_percent", appRowPosition.progress)
            .putBoolean("layout.full_screen_when_idle", fullScreenWhenIdle.isChecked)
            .putInt("weather.world_watch_mode", worldWeatherWatch.selectedItemPosition)
            .putString("alerts.feed_url", alertFeedUrl.text.toString().trim())
            .putString("alerts.api_key", alertApiKey.text.toString().trim())
            .putString("alerts.display_mode", if (warningDisplayMode.selectedItemPosition == 1) "inside_panel" else "separate_banner")
            .putBoolean("alerts.show_details", showWarningDetails.isChecked)
            .putString("readout.scale", String.format(java.util.Locale.US, "%.2f", (readoutSize.progress + 75) / 100f))
            .putString("overlay.translate_x", (overlayTx.progress - 1000).toString())
            .putString("overlay.translate_y", String.format(java.util.Locale.US, "%.1f", overlayTy.progress - 500f))
            .putString("overlay.scale_x", String.format(java.util.Locale.US, "%.4f", overlaySx.progress / 1000f))
            .putString("overlay.scale_y", String.format(java.util.Locale.US, "%.4f", overlaySy.progress / 1000f))
            .putString("overlay.origin_x", (overlayOx.progress - 1000).toString())
            .putString("overlay.origin_y", (overlayOy.progress - 1000).toString())
            .putInt("panel.scale_x", panelScaleX.progress.coerceIn(50, 150))
            .putInt("panel.scale_y", panelScaleY.progress.coerceIn(50, 150))
            .putBoolean("clock.visible", showClock.isChecked)
            .putBoolean("date.visible", showDate.isChecked)
            .putBoolean("clock.24_hour", clockFormat.selectedItemPosition == 0)
            .putString("date.pattern", ClockDatePreferences.DATE_PATTERNS[dateFormat.selectedItemPosition])
            .putInt("clock.color", parseUiColor(clockColor))
            .putInt("date.color", parseUiColor(dateColor))
            .putInt("clock.opacity", clockOpacity.progress)
            .putInt("date.opacity", dateOpacity.progress)
            .putInt("clock.size", clockSize.progress + 10)
            .putInt("date.size", dateSize.progress + 8)
            .putBoolean("clock.bold", clockBold.isChecked)
            .putBoolean("date.bold", dateBold.isChecked)
            .putString("date.position", listOf("below_time", "beside_time", "above_time")[datePosition.selectedItemPosition])
            .apply {
                weatherOptions.forEach { (key, checkBox) -> putBoolean(key, checkBox.isChecked) }
                paletteFields.forEach { (key, input) ->
                    val value = input.text.toString().trim()
                    putString(key, if (value.length == 7) "#FF" + value.substring(1) else value)
                }
            }
            .commit()
        if (!saved) {
            Toast.makeText(this, "Could not save settings. Please try again.", Toast.LENGTH_LONG).show()
            return
        }
        runCatching {
            startService(android.content.Intent(this, WeatherWallpaperService::class.java)
                .setAction(WeatherWallpaperService.ACTION_REFRESH))
        }.onSuccess {
            Toast.makeText(this, "Settings saved. Refreshing wallpaper overlay…", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(this, "Settings saved, but refresh could not be started: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
