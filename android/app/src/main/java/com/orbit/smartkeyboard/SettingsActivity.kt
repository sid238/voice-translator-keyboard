package com.orbit.smartkeyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
        "purple" to "Purple",
        "blue" to "Blue",
        "green" to "Green",
        "red" to "Red",
        "dark" to "Dark"
    )

    private val effectOptions = listOf(
        "none" to "None",
        "glow" to "Glow",
        "ripple" to "Ripple",
        "particles" to "Particles"
    )

    private val emojiScaleOptions = listOf(
        "small" to "Small",
        "medium" to "Medium",
        "large" to "Large"
    )

    // Image picker for custom background
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) copyThemeImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Apply saved app theme (settings UI only)
        val appTheme = prefs.getString("app_theme", "dark") ?: "dark"
        AppCompatDelegate.setDefaultNightMode(
            if (appTheme == "light") AppCompatDelegate.MODE_NIGHT_NO
            else AppCompatDelegate.MODE_NIGHT_YES
        )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

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

    private fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    private fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    private fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()

    // ---------- Status ----------
    private fun setupStatus() {
        findViewById<Button>(R.id.btnEnable).setOnClickListener { openKeyboardSettings() }
        findViewById<Button>(R.id.btnDefault).setOnClickListener { showKeyboardPicker() }
    }

    private fun refreshStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val targetId = "$packageName/.GlideTypeKeyboardService"
        val enabled = imm.enabledInputMethodList.any { it.id == targetId }
        val currentIME = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val isDefault = currentIME != null && currentIME == targetId
        val text = when {
            isDefault -> "Enabled and set as default"
            enabled -> "Enabled (not default) — tap Set as Default"
            else -> "Not enabled — tap Enable Keyboard"
        }
        findViewById<TextView>(R.id.tvStatus).text = text
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

        findViewById<Button>(R.id.btnPickBg).setOnClickListener { pickImage.launch("image/*") }
        findViewById<Button>(R.id.btnClearBg).setOnClickListener { clearThemeImage() }
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
        val h = prefs.getInt("keyboard_height_dp", 360)
        sliderHeight.progress = (h - 200).coerceIn(0, 250)
        tvHeight.text = "Height: $h dp"
        sliderHeight.setOnSeekBarChangeListener(seek { v ->
            val value = v + 200
            tvHeight.text = "Height: $value dp"
            putInt("keyboard_height_dp", value)
        })

        val sliderSpacing = findViewById<SeekBar>(R.id.sliderSpacing)
        val tvSpacing = findViewById<TextView>(R.id.tvSpacing)
        val sp = prefs.getInt("key_spacing_dp", 4)
        sliderSpacing.progress = sp.coerceIn(0, 12)
        tvSpacing.text = "Key spacing: $sp dp"
        sliderSpacing.setOnSeekBarChangeListener(seek { v ->
            tvSpacing.text = "Key spacing: $v dp"
            putInt("key_spacing_dp", v)
        })

        val sliderFont = findViewById<SeekBar>(R.id.sliderFont)
        val tvFont = findViewById<TextView>(R.id.tvFont)
        val fs = prefs.getInt("key_font_size_sp", 0)
        sliderFont.progress = fs.coerceIn(0, 28)
        tvFont.text = if (fs == 0) "Key font size: auto" else "Key font size: $fs sp"
        sliderFont.setOnSeekBarChangeListener(seek { v ->
            tvFont.text = if (v == 0) "Key font size: auto" else "Key font size: $v sp"
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
        tv.text = "Long-press delay: $delay ms"
        slider.setOnSeekBarChangeListener(seek { v ->
            val value = v + 100
            tv.text = "Long-press delay: $value ms"
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

    // ---------- Emoji & App theme ----------
    private fun setupEmojiAndTheme() {
        val emojiSpinner = findViewById<Spinner>(R.id.spinnerEmoji)
        emojiSpinner.adapter = spinnerAdapter(emojiScaleOptions.map { it.second })
        val savedEmoji = prefs.getString("emoji_scale", "medium") ?: "medium"
        emojiSpinner.setSelection(emojiScaleOptions.indexOfFirst { it.first == savedEmoji }.coerceAtLeast(0))
        emojiSpinner.onItemSelectedListener = simple { pos ->
            putString("emoji_scale", emojiScaleOptions[pos].first)
        }

        val appThemeSpinner = findViewById<Spinner>(R.id.spinnerAppTheme)
        val appOptions = listOf("dark" to "Dark", "light" to "Light")
        appThemeSpinner.adapter = spinnerAdapter(appOptions.map { it.second })
        val savedApp = prefs.getString("app_theme", "dark") ?: "dark"
        appThemeSpinner.setSelection(appOptions.indexOfFirst { it.first == savedApp }.coerceAtLeast(0))
        appThemeSpinner.onItemSelectedListener = simple { pos ->
            val value = appOptions[pos].first
            putString("app_theme", value)
            AppCompatDelegate.setDefaultNightMode(
                if (value == "light") AppCompatDelegate.MODE_NIGHT_NO
                else AppCompatDelegate.MODE_NIGHT_YES
            )
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
