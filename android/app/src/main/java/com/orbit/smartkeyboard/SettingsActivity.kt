package com.orbit.smartkeyboard

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.File
import java.io.FileOutputStream

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private val PREFS_NAME = "glidetype_keyboard_prefs"

    // Language options (code -> display)
    private val languageOptions = listOf(
        "en" to "English",
        "hi" to "Hindi",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German",
        "ja" to "Japanese",
        "ko" to "Korean",
        "zh" to "Chinese"
    )

    private val themeOptions = listOf(
        "dynamic" to "Dynamic (Material You)",
        "light" to "Light Theme",
        "dark" to "Dark Theme",
        "purple" to "Purple",
        "blue" to "Blue",
        "green" to "Green",
        "red" to "Red"
    )

    private val effectOptions = listOf(
        "none" to "None (Disabled)",
        "matrix" to "Matrix Rain Effect",
        "neo_glow" to "Neo Glow Effect",
        "water" to "Water Ripple Effect",
        "fire" to "Fire Flames Effect",
        "galaxy" to "Galaxy Particles",
        "mechanical_flash" to "Mechanical Flash"
    )



    // Image picker for custom background
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) copyThemeImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashLogger.install(this)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        showPreviousCrashIfAny()

        // Apply saved app theme (settings UI only)
        val appTheme = prefs.getString("app_theme", "dark") ?: "dark"
        AppCompatDelegate.setDefaultNightMode(
            if (appTheme == "light") AppCompatDelegate.MODE_NIGHT_NO
            else AppCompatDelegate.MODE_NIGHT_YES
        )

        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_settings)

        // Handle window insets for status bar and soft keyboard IME elevation
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsRoot)) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val sysBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val bottomMargin = Math.max(imeHeight, sysBottom)

            val rootLayout = findViewById<android.view.View>(R.id.rootLayout)
            rootLayout?.setPadding(rootLayout.paddingLeft, statusBarHeight + dpToPx(8), rootLayout.paddingRight, dpToPx(84) + bottomMargin)

            val bottomTest = findViewById<android.view.View>(R.id.bottomTestContainer)
            val params = bottomTest?.layoutParams as? android.widget.RelativeLayout.LayoutParams
            if (bottomTest != null && params != null) {
                params.setMargins(dpToPx(12), dpToPx(8), dpToPx(12), bottomMargin + dpToPx(8))
                bottomTest.layoutParams = params
            }
            insets
        }

        setupStatus()
        setupAppearance()
        setupLanguages()
        setupLayout()
        setupTyping()
        setupFeedback()
        setupAddons()
        setupClipboard()
        setupEmojiAndTheme()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    private fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    private fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()

    private fun showPreviousCrashIfAny() {
        val text = CrashLogger.readLastCrash() ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("crash", text))
        AlertDialog.Builder(this)
            .setTitle("Previous crash log (copied to clipboard)")
            .setMessage(text)
            .setPositiveButton("OK", null)
            .show()
    }

    // ---------- Status ----------
    private fun setupStatus() {
        findViewById<MaterialButton>(R.id.btnEnable).setOnClickListener { openKeyboardSettings() }
        findViewById<MaterialButton>(R.id.btnDefault).setOnClickListener { showKeyboardPicker() }
    }

    private fun refreshStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val targetId = "$packageName/.GlideTypeKeyboardService"
        val enabled = imm.enabledInputMethodList.any { it.id == targetId }
        val currentIME = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val isDefault = currentIME != null && currentIME == targetId
        
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val btnEnable = findViewById<MaterialButton>(R.id.btnEnable)
        val btnDefault = findViewById<MaterialButton>(R.id.btnDefault)

        tvStatus.text = when {
            isDefault -> "✓ Active and set as default keyboard"
            enabled -> "✓ Enabled (Tap 'Set as Default' to select Orbit Keyboard)"
            else -> "Not enabled — Tap 'Enable Keyboard' to get started"
        }

        if (enabled) {
            btnEnable.text = "✓ Enabled"
            btnEnable.isEnabled = false
            btnEnable.alpha = 0.6f
        } else {
            btnEnable.text = "Enable Keyboard"
            btnEnable.isEnabled = true
            btnEnable.alpha = 1.0f
        }

        if (isDefault) {
            btnDefault.text = "✓ Active Default"
            btnDefault.isEnabled = false
            btnDefault.alpha = 0.6f
        } else {
            btnDefault.text = "Set as Default"
            btnDefault.isEnabled = enabled
            btnDefault.alpha = if (enabled) 1.0f else 0.5f
        }
    }

    private fun openKeyboardSettings() {
        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun showKeyboardPicker() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }

    // ---------- Appearance ----------
    private fun setupAppearance() {
        val themeSpinner = findViewById<Spinner>(R.id.spinnerTheme)
        themeSpinner.adapter = spinnerAdapter(themeOptions.map { it.second })
        val savedTheme = prefs.getString("theme", "dynamic") ?: "dynamic"
        themeSpinner.setSelection(themeOptions.indexOfFirst { it.first == savedTheme }.coerceAtLeast(0))
        themeSpinner.onItemSelectedListener = simple { pos ->
            putString("theme", themeOptions[pos].first)
        }

        val switchAdaptive = findViewById<SwitchMaterial>(R.id.switchAdaptive)
        switchAdaptive.isChecked = prefs.getBoolean("adaptive_theme", true)
        switchAdaptive.setOnCheckedChangeListener { _, v -> putBoolean("adaptive_theme", v) }

        val effectSpinner = findViewById<Spinner>(R.id.spinnerEffect)
        effectSpinner.adapter = spinnerAdapter(effectOptions.map { it.second })
        val savedEffect = prefs.getString("keyboard_effect", "none") ?: "none"
        effectSpinner.setSelection(effectOptions.indexOfFirst { it.first == savedEffect }.coerceAtLeast(0))
        effectSpinner.onItemSelectedListener = simple { pos ->
            putString("keyboard_effect", effectOptions[pos].first)
        }

        findViewById<MaterialButton>(R.id.btnPickBg).setOnClickListener { pickImage.launch("image/*") }
        findViewById<MaterialButton>(R.id.btnClearBg).setOnClickListener { clearThemeImage() }
    }

    private fun copyThemeImage(uri: Uri) {
        try {
            val input = contentResolver.openInputStream(uri) ?: return
            val destFolder = File(cacheDir, "theme_images").apply { mkdirs() }
            val destFile = File(destFolder, "custom_bg.jpg")
            val output = FileOutputStream(destFile)
            input.copyTo(output)
            input.close()
            output.close()
            putString("theme_image_path", destFile.absolutePath)
            Toast.makeText(this, "Background image set", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to set image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearThemeImage() {
        prefs.edit().remove("theme_image_path").apply()
        try {
            File(File(cacheDir, "theme_images"), "custom_bg.jpg").delete()
        } catch (e: Exception) { }
        Toast.makeText(this, "Background image removed", Toast.LENGTH_SHORT).show()
    }

    // ---------- Languages ----------
    private fun setupLanguages() {
        val langSpinner = findViewById<Spinner>(R.id.spinnerLang)
        langSpinner.adapter = spinnerAdapter(languageOptions.map { it.second })
        val savedLang = prefs.getString("selected_language", "en") ?: "en"
        langSpinner.setSelection(languageOptions.indexOfFirst { it.first == savedLang }.coerceAtLeast(0))
        langSpinner.onItemSelectedListener = simple { pos ->
            putString("selected_language", languageOptions[pos].first)
        }

        val container = findViewById<LinearLayout>(R.id.langContainer)
        container.removeAllViews()
        val savedList = (prefs.getString("selected_languages", "en") ?: "en")
            .split(",").filter { it.isNotEmpty() }.toMutableSet()

        languageOptions.forEach { (code, label) ->
            val cb = CheckBox(this).apply {
                text = label
                textSize = 14f
                setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4))
                isChecked = savedList.contains(code)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) savedList.add(code) else savedList.remove(code)
                    if (savedList.isEmpty()) savedList.add("en")
                    putString("selected_languages", savedList.joinToString(","))
                }
            }
            container.addView(cb)
        }
    }

    // ---------- Layout ----------
    private fun setupLayout() {
        val sliderHeight = findViewById<SeekBar>(R.id.sliderHeight)
        val tvHeight = findViewById<TextView>(R.id.tvHeight)
        val h = prefs.getInt("keyboard_height_dp", 260)
        sliderHeight.progress = (h - 200).coerceIn(0, 250)
        tvHeight.text = "$h dp"
        sliderHeight.setOnSeekBarChangeListener(seek { v ->
            val value = v + 200
            tvHeight.text = "$value dp"
            putInt("keyboard_height_dp", value)
        })

        val sliderSpacing = findViewById<SeekBar>(R.id.sliderSpacing)
        val tvSpacing = findViewById<TextView>(R.id.tvSpacing)
        val sp = prefs.getInt("key_spacing_dp", 4)
        sliderSpacing.progress = sp.coerceIn(0, 12)
        tvSpacing.text = "$sp dp"
        sliderSpacing.setOnSeekBarChangeListener(seek { v ->
            tvSpacing.text = "$v dp"
            putInt("key_spacing_dp", v)
        })

        val sliderFont = findViewById<SeekBar>(R.id.sliderFont)
        val tvFont = findViewById<TextView>(R.id.tvFont)
        val fs = prefs.getInt("key_font_size_sp", 0)
        sliderFont.progress = fs.coerceIn(0, 28)
        tvFont.text = if (fs == 0) "Auto" else "$fs sp"
        sliderFont.setOnSeekBarChangeListener(seek { v ->
            tvFont.text = if (v == 0) "Auto" else "$v sp"
            putInt("key_font_size_sp", v)
        })

        val switchNumberRow = findViewById<SwitchMaterial>(R.id.switchNumberRow)
        switchNumberRow.isChecked = prefs.getBoolean("number_row_enabled", true)
        switchNumberRow.setOnCheckedChangeListener { _, v -> putBoolean("number_row_enabled", v) }
    }

    // ---------- Typing ----------
    private fun setupTyping() {
        bindSwitch(R.id.switchAutoCap, "auto_cap", true)
        bindSwitch(R.id.switchDoubleSpace, "double_space_period", true)
        bindSwitch(R.id.switchSuggestions, "suggestions_enabled", true)
        bindSwitch(R.id.switchAutoCorrect, "auto_correct_enabled", true)

        val slider = findViewById<SeekBar>(R.id.sliderLongPress)
        val tv = findViewById<TextView>(R.id.tvLongPress)
        val delay = prefs.getInt("long_press_delay_ms", 300)
        slider.progress = (delay - 100).coerceIn(0, 600)
        tv.text = "$delay ms"
        slider.setOnSeekBarChangeListener(seek { v ->
            val value = v + 100
            tv.text = "$value ms"
            putInt("long_press_delay_ms", value)
        })
    }

    // ---------- Feedback ----------
    private fun setupFeedback() {
        bindSwitch(R.id.switchSound, "sound_enabled", false)
        bindSwitch(R.id.switchVibration, "vibration_enabled", true)
    }

    // ---------- Add-ons ----------
    private fun setupAddons() {
        bindSwitch(R.id.switchVoice, "addon_voice_text", true)
        bindSwitch(R.id.switchTranslate, "addon_translate", true)
    }

    // ---------- Clipboard ----------
    private fun setupClipboard() {
        bindSwitch(R.id.switchClipboardTimeline, "clipboard_timeline", false)
        val clipLimit = findViewById<EditText>(R.id.etClipLimit)
        clipLimit.setText(prefs.getInt("clipboard_limit", 100).toString())
        clipLimit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) putInt("clipboard_limit", clipLimit.text.toString().toIntOrNull() ?: 100)
        }
        val pinLimit = findViewById<EditText>(R.id.etPinLimit)
        pinLimit.setText(prefs.getInt("pin_limit", 10).toString())
        pinLimit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) putInt("pin_limit", pinLimit.text.toString().toIntOrNull() ?: 10)
        }
    }

    // ---------- Interface Theme ----------
    private fun setupEmojiAndTheme() {
        val appThemeSpinner = findViewById<Spinner>(R.id.spinnerAppTheme)
        val appOptions = listOf("dark" to "Dark Mode", "light" to "Light Mode")
        appThemeSpinner.adapter = spinnerAdapter(appOptions.map { it.second })
        val savedApp = prefs.getString("app_theme", "dark") ?: "dark"
        appThemeSpinner.setSelection(appOptions.indexOfFirst { it.first == savedApp }.coerceAtLeast(0))
        var firstSelection = true
        appThemeSpinner.onItemSelectedListener = simple { pos ->
            if (firstSelection) { firstSelection = false; return@simple }
            val value = appOptions[pos].first
            putString("app_theme", value)
            AppCompatDelegate.setDefaultNightMode(
                if (value == "light") AppCompatDelegate.MODE_NIGHT_NO
                else AppCompatDelegate.MODE_NIGHT_YES
            )
            // Recreate to apply theme immediately
            recreate()
        }
    }

    // ---------- Helpers ----------
    private fun bindSwitch(id: Int, key: String, default: Boolean) {
        val sw = findViewById<SwitchMaterial>(id)
        sw.isChecked = prefs.getBoolean(key, default)
        sw.setOnCheckedChangeListener { _, v -> putBoolean(key, v) }
    }

    private fun spinnerAdapter(items: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun simple(onSelect: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) = onSelect(pos)
        override fun onNothingSelected(p: AdapterView<*>?) { }
    }

    private fun seek(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) onChange(progress) }
        override fun onStartTrackingTouch(s: SeekBar?) { }
        override fun onStopTrackingTouch(s: SeekBar?) { }
    }
}
