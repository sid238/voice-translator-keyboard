package com.orbit.smartkeyboard

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.os.Vibrator
import android.os.VibrationEffect
import android.media.AudioManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.io.BufferedReader
import java.io.InputStreamReader

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.core.content.ContextCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning

class GlideTypeKeyboardService : InputMethodService(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private lateinit var keyboardContainer: FrameLayout
    private var currentViewMode = ViewMode.QWERTY
    private var isShifted = false
    private var isCapsLock = false
    private var lastShiftPressTime: Long = 0
    private var translationInputField: EditText? = null
    // grammar now uses currentInputConnection directly
    private var longPressDelayMs = 300
    private var keyboardHeightDp = 220 // Default keyboard height
    private var isSizeAdjustActive = false
    private var keyboardWidthPercent = 100
    private var keyboardGravity = Gravity.CENTER_HORIZONTAL
    private var isSymbolsPage2 = false
    private var isHindiPage2 = false
    private var customBgDrawable: android.graphics.drawable.Drawable? = null
    private var lastProcessedSystemClip: String? = null
    private var isCtrlActive = false
    private var isAltActive = false
    private var autoCapEnabled = true
    private var doubleSpacePeriodEnabled = true
    private var suggestionsEnabled = true
    private var keySpacingDp = 3
    private var lastSpacePressTime: Long = 0
    private val selectedLanguages = mutableListOf<String>()
    private var activeLanguageIndex = 0

    private var isFontSelectorActive = false
    private var currentKeyboardFont = KeyboardFont.NORMAL
    private var wasLastKeySpace = true

    enum class KeyboardFont {
        NORMAL, BOLD_SERIF, ITALIC_SERIF, BOLD_ITALIC_SERIF, SCRIPT, BOLD_SCRIPT, GOTHIC, BOLD_GOTHIC, OUTLINE, TYPEWRITER, CIRCLED, NEGATIVE_CIRCLED, SQUARED, NEGATIVE_SQUARED, FULLWIDTH, SMALL_CAPS, PARENTHESIZED, SUBSCRIPT, SUPERSCRIPT, STRIKETHROUGH, SLASHED, UNDERLINE, DOUBLE_UNDERLINE, OVERLINE, DOTTED, WAVY, ZALGO, INVERTED, REVERSED, MORSE, FANCY_BRACKETS, FANCY_ARROWS, FANCY_HEARTS, FANCY_STARS, FANCY_SPARKLES, FANCY_SMILEY, MATH_BOLD_SANS, MATH_ITALIC_SANS, MATH_BOLD_ITALIC_SANS, CURSIVE, PARENTHESIZED_DIGITS, NEGATIVE_CIRCLED_DIGITS, DOUBLE_PARENTHESES, CYBER, STARLIGHT, DIAMOND, TSUNAMI, DOUBLE_SLASH, SLASH_BOX, SHARP, CROSSED
    }

    // Settings fields (synced from SharedPrefs)
    private var vibrationEnabled = true
    private var soundEnabled = false
    private var numberRowEnabled = true
    // gesture/glide typing removed
    private var themeName = "purple"
    private var translationFeatureEnabled = true
    private var voiceDictationEnabled = true
    // Theme Colors
    private var themeBgColor = "#121212"
    private var themeAccentColor = "#8A2BE2"
    private var themeSpecialKeyBg = "#2E2E2E"
    private var themeRegularKeyBg = "#1F1F1F"
    private var themeToolbarBg = "#1A1A1A"
    private var themeTextColor = Color.WHITE
    private var themeHintColor = "#7AFFFFFF"

    // Effects & Timeline & OCR settings
    private var keyboardEffect = "none"
    private var keyboardEffectsView: KeyboardEffectsView? = null
    private var clipboardTimelineEnabled = false
    private var activeKeyboardArea: FrameLayout? = null

    // Camera OCR fields
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: java.util.concurrent.ExecutorService? = null
    private var ocrPreviewView: androidx.camera.view.PreviewView? = null
    private var lastScannedText = ""

    private val handler = Handler(Looper.getMainLooper())

    // Clipboard History List (Max 100 items)
    private val clipboardHistory = mutableListOf<ClipboardItem>()
    private val PREFS_NAME = "glidetype_keyboard_prefs"
    private val PREF_KEY_CLIPBOARD = "clipboard_history"
    private val PREF_KEY_HEIGHT = "keyboard_height_dp"

    // Toolbar collapse/expand state
    private var isToolbarCollapsed = false
    private var spaceLongPressed = false
    private val spaceLongPressRunnable = Runnable {
        spaceLongPressed = true
        vibrateClick()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }

    // Translation fields
    private var isTranslationActive = false
    private var translationSourceLang = "en"
    private var translationTargetLang = "hi"
    private val translationDebounceHandler = Handler(Looper.getMainLooper())
    private var translationRunnable: Runnable? = null

    // Voice Dictation
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    // Key preview and touches
    private var activePreviewView: View? = null
    private val previewRemoveRunnable = Runnable {
        activePreviewView?.let {
            if (::keyboardContainer.isInitialized) {
                keyboardContainer.removeView(it)
            }
            activePreviewView = null
        }
    }
    private var pressedKeyView: View? = null

    private val layoutUpdateRunnable = Runnable {
        updateKeyboardLayoutInternal()
    }

    enum class ViewMode {
        QWERTY, SYMBOLS, EMOJIS, CLIPBOARD, PC_SHORTCUTS, HINDI, OCR
    }

    data class ClipboardItem(
        val text: String,
        val imageUri: String? = null,
        var isPinned: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val languages = listOf(
        Pair("English", "en"),
        Pair("Hindi", "hi"),
        Pair("Spanish", "es"),
        Pair("Bengali", "bn"),
        Pair("Telugu", "te"),
        Pair("Marathi", "mr"),
        Pair("Tamil", "ta"),
        Pair("Gujarati", "gu"),
        Pair("Kannada", "kn"),
        Pair("Malayalam", "ml")
    )

    private val commonWords = listOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
        "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
        "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
        "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
        "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
        "when", "make", "can", "like", "time", "no", "just", "him", "know", "take",
        "people", "into", "year", "your", "good", "some", "could", "them", "see", "other",
        "than", "then", "now", "look", "only", "come", "its", "over", "think", "also",
        "back", "after", "use", "two", "how", "our", "work", "first", "well", "way",
        "even", "new", "want", "because", "any", "these", "give", "day", "most", "us",
        "hello", "welcome", "love", "india", "chatgpt", "keyboard", "android", "smart",
        "great", "here", "there", "where", "please", "thanks", "thank", "sorry", "happy", "life",
        "world", "time", "home", "more", "write"
    )

    private var currentEmojiCategoryIndex = 0
    private val recentEmojis = mutableListOf<String>()

    // All emoji categories sorted nicely
    private val emojiCategories: List<Pair<Int, List<String>>>
        get() = listOf(
            Pair(R.drawable.ic_clock, recentEmojis.toList()),
            Pair(R.drawable.ic_emoji_people, listOf(
            "😀", "😁", "😂", "🤣", "😃", "😄", "😅", "😆", "😉", "😊", "😋", "😎",
            "😍", "😘", "😗", "😙", "😚", "🙂", "🤗", "🤩", "🤔", "🤨", "😐", "😑",
            "😶", "🙄", "😏", "😣", "😥", "😮", "🤐", "😯", "😪", "😫", "😴", "😌",
            "😛", "😜", "🤪", "😝", "🤤", "😒", "😓", "😔", "😕", "🙃", "🤑", "😲",
            "☹️", "🙁", "😖", "😞", "😟", "😤", "😢", "😭", "😦", "😧", "😨", "😩",
            "🤯", "😬", "😰", "😱", "🥵", "🥶", "😳", "😵", "😡", "😠", "🤬",
            "😷", "🤒", "🤕", "🤢", "🤮", "🤧", "😇", "🤠", "🥳", "🥴", "🥺",
            "🥱", "🧑‍💻", "🥷", "🫂", "🧏", "🙋", "💁", "🧘"
        )),
        Pair(R.drawable.ic_emoji_gestures, listOf(
            "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏", "✌️", "🤞", "🤟", "🤘",
            "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "👍", "👎", "✊", "👊", "🤛",
            "🤜", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳", "💪", "🦾",
            "🦵", "🦶", "👂", "👃", "🧠", "👀", "👅", "👄", "💋", "🩸"
        )),
        Pair(R.drawable.ic_emoji_nature, listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮",
            "🐷", "🐽", "🐸", "🐵", "🙈", "🙉", "🙊", "🐒", "🐔", "🐧", "🐦", "🐤",
            "🐣", "🐥", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🐛",
            "🦋", "🐌", "🐞", "🐜", "🦟", "🦗", "🕷️", "🕸️", "🦂", "🐢", "🐍", "🦎",
            "🐙", "🦑", "🦞", "🦀", "🐡", "🐠", "🐟", "🐬", "🐳", "🐋", "🦈", "🐊",
            "🐅", "🐆", "🦓", "🦍", "🐘", "🦛", "🦏", "🐪", "🐫", "🦒", "🦘", "🐎",
            "🐖", "🐏", "🐑", "🐐", "🦌", "🐕", "🐈", "🐓", "🦃", "🦢", "🦩", "🕊️",
            "🐇", "🦝", "🦡", "🦦", "🦥", "🐿️", "🦔", "🐾", "🐉", "🦖", "🦕", "🦧", "🦮", "🐕‍🦺", "🦫", "🦬", "🦣", "🦤", "🦚", "🦜", "🦠"
        )),
        Pair(R.drawable.ic_emoji_food, listOf(
            "🍏", "🍎", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🍒", "🍑", "🥭", "🍍",
            "🥥", "🥝", "🍅", "🍆", "🥑", "🥦", "🥬", "🥒", "🌶️", "🌽", "🥕",
            "🧅", "🧄", "🍄", "🥜", "🌰", "🍞", "🥐", "🥖",
            "🥨", "🥯", "🥞", "🧇", "🧀", "🍖", "🍗", "🥩", "🥓", "🍔", "🍟", "🍕",
            "🌭", "🥪", "🌮", "🌯", "🥙", "🧆", "🥚", "🍳", "🥘", "🍲", "🥣", "🥗",
            "🍿", "🧈", "🧂", "🍱", "🍘", "🍙", "🍚", "🍛", "🍜", "🍝", "🍢", "🍣",
            "🍤", "🍥", "🍡", "🥟", "🍦", "🍧", "🍨", "🍩", "🍪", "🎂", "🍰", "🧁",
            "🥧", "🍫", "🍬", "🍭", "🍮", "🍯", "🍼", "🥛", "☕️", "🍵", "🍶",
            "🍷", "🍸", "🍹", "🍺", "🍻", "🥂", "🥃", "🥤", "🧋", "🧃", "🧊"
        )),
        Pair(R.drawable.ic_emoji_activity, listOf(
            "⚽️", "🏀", "🏈", "⚾️", "🥎", "🎾", "🏐", "🏉", "🎱", "🪀", "🏹", "🎣", "🤿", "🥊", "🥋",
            "🥅", "⛳️", "⛸️", "🎿", "🛷", "🎯", "🎮", "🕹️", "🎰", "🎲",
            "🧩", "🧸", "🎨", "🧵", "🪡", "🎹", "🥁", "🎷",
            "🎺", "🎸", "🪕", "🎻", "🎬", "🎤", "🎧", "🎼"
        )),
        Pair(R.drawable.ic_emoji_travel, listOf(
            "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑", "🚒", "🚐", "🛻", "🚚",
            "🚛", "🚜", "🛵", "🏍️", "🛺", "🚲", "🛴", "skateboard", "🛼", "🚨", "🚔", "🚍",
            "🚘", "🚖", "🚡", "🚠", "🚟", "🚃", "🚋", "🚞", "🚝", "🚄", "🚅", "🚈",
            "🚂", "🚆", "🚇", "🚊", "🚉", "✈️", "🛫", "🛬", "🛩️", "💺", "🛰️", "🚀",
            "🛸", "🚁", "🛶", "⛵️", "🚤", "🛥️", "🛳️", "⛴️", "🚢", "⚓️", "🛟", "🚧",
            "⛽️", "🚏", "🗺️", "🗼", "🏰", "🏯", "🏟️", "🎡", "🎢",
            "🎠", "⛲️", "⛱️", "🏖️", "🏝️", "🏜️", "🌋", "⛰️", "🏔️", "🗻", "🏕️", "⛺️",
            "🛞", "🪨", "🛖", "🪧"
        )),
        Pair(R.drawable.ic_emoji_objects, listOf(
            "⌚️", "📱", "💻", "⌨️", "🖱️", "🖲️", "🗜️", "💾", "💿", "📀", "📼", "📷",
            "📸", "📹", "🎥", "📽️", "🎞️", "📞", "📟", "📠", "📺", "📻", "🎙️", "🎚️",
            "🎛️", "🧭", "⏱️", "⏰", "🕰️", "⏳", "⌛️", "📡", "🔋", "🔌", "💡", "🔦",
            "🕯️", "🪔", "🧯", "🛢️", "💸", "💵", "💴", "💶", "💷", "🪙", "💰", "💳",
            "💎", "⚖️", "🪜", "🔧", "🔨", "⚒️", "🛠️", "⛏️", "🪚", "⚙️",
            "🧱", "⛓️", "🧲", "🛡️", "🚬", "⚰️", "🏺", "🔮", "🪬", "💈", "🔬", "🔭",
            "💉", "🩸", "💊", "🩹", "🩺", "🚪", "🛗", "🪟", "🛏️", "🛋️", "🪑",
            "🪠", "🚿", "🛁", "🛀", "🪤", "🪒", "🧴", "🧷", "🧹", "🧺", "🧻", "🧼",
            "🪥", "🧽", "🪣", "🔑", "🗝️", "🔐", "🔏", "🔒", "🔓", "🪃", "🪗", "🪘", "🪞", "🪦"
        )),
        Pair(R.drawable.ic_emoji_symbols, listOf(
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕",
            "💞", "💓", "💗", "💖", "💘", "💝", "☮️", "✝️", "☪️", "🕉️", "☸️", "✡️",
            "🔯", "🕎", "☯️", "☦️", "🛐", "⛎", "🪯", "♈️", "♉️", "♊️", "♋️", "♌️",
            "♍️", "♎️", "♏️", "♐️", "♑️", "♒️", "♓️", "🔀", "🔁", "🔂", "▶️", "⏩",
            "⚧️", "🧿", "🪬"
        ))
    )

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        loadPreferences()
        setupClipboardListener()
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        stopOcrCamera()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        return false
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        handler.removeCallbacks(layoutUpdateRunnable)
        handler.postDelayed(layoutUpdateRunnable, 150)
    }

    override fun onCreateInputView(): View {
        keyboardContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(keyboardHeightDp)
            )
            setBackgroundColor(Color.parseColor(themeBgColor))
        }
        updateKeyboardLayout()
        return keyboardContainer
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        currentViewMode = ViewMode.QWERTY
        isTranslationActive = false
        isHindiPage2 = false
        isSymbolsPage2 = false
        // Refresh preferences and clipboard history when IME is shown
        loadPreferences()
        fetchSystemClipboard()
        updateKeyboardLayout()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        stopOcrCamera()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        keyboardHeightDp = prefs.getInt(PREF_KEY_HEIGHT, 220)
        keyboardWidthPercent = prefs.getInt("keyboard_width_percent", 100)
        keyboardGravity = prefs.getInt("keyboard_gravity", Gravity.CENTER_HORIZONTAL)
        
        vibrationEnabled = prefs.getBoolean("vibration_enabled", true)
        soundEnabled = prefs.getBoolean("sound_enabled", false)
        numberRowEnabled = prefs.getBoolean("number_row_enabled", true)
        // gesture disabled
        themeName = prefs.getString("theme", "red") ?: "red"
        translationFeatureEnabled = prefs.getBoolean("addon_translate", true)
        voiceDictationEnabled = prefs.getBoolean("addon_voice_text", true)
        longPressDelayMs = prefs.getInt("long_press_delay_ms", 300)

        autoCapEnabled = prefs.getBoolean("auto_cap", true)
        doubleSpacePeriodEnabled = prefs.getBoolean("double_space_period", true)
        suggestionsEnabled = prefs.getBoolean("suggestions_enabled", true)
        keySpacingDp = prefs.getInt("key_spacing_dp", 3)

        keyboardEffect = prefs.getString("keyboard_effect", "none") ?: "none"
        clipboardTimelineEnabled = prefs.getBoolean("clipboard_timeline", false)

        val ocrImagePath = prefs.getString("ocr_image_path", null)
        if (ocrImagePath != null) {
            prefs.edit().remove("ocr_image_path").apply()
            try {
                val file = java.io.File(ocrImagePath)
                if (file.exists()) {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(ocrImagePath)
                    if (bitmap != null) {
                        processImageForOcr(bitmap)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("OCR", "Failed to process gallery image", e)
            }
        }

        val langsStr = prefs.getString("selected_languages", "en") ?: "en"
        selectedLanguages.clear()
        selectedLanguages.addAll(langsStr.split(",").filter { it.isNotEmpty() })
        if (selectedLanguages.isEmpty()) {
            selectedLanguages.add("en")
        }
        if (activeLanguageIndex >= selectedLanguages.size) {
            activeLanguageIndex = 0
        }

        val themeImagePath = prefs.getString("theme_image_path", null)
        customBgDrawable = if (themeImagePath != null) {
            try {
                val file = java.io.File(themeImagePath)
                if (file.exists()) {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(themeImagePath)
                    if (bitmap != null) {
                        android.graphics.drawable.BitmapDrawable(resources, bitmap)
                    } else null
                } else null
            } catch (e: Exception) {
                null
            }
        } else null

        val recentStr = prefs.getString("recent_emojis", "") ?: ""
        recentEmojis.clear()
        if (recentStr.isNotEmpty()) {
            recentEmojis.addAll(recentStr.split(","))
        } else {
            recentEmojis.addAll(listOf("😊", "😂", "❤️", "👍", "🔥", "✨", "🎉"))
        }

        // Set theme colors based on loaded settings
        themeTextColor = Color.WHITE
        themeHintColor = "#7AFFFFFF"
        when (themeName) {
            "purple" -> {
                themeBgColor = "#121212"
                themeAccentColor = "#8A2BE2"
                themeSpecialKeyBg = "#2E2E2E"
                themeRegularKeyBg = "#1F1F1F"
                themeToolbarBg = "#1A1A1A"
            }
            "dark" -> {
                themeBgColor = "#000000"
                themeAccentColor = "#333333"
                themeSpecialKeyBg = "#1C1C1C"
                themeRegularKeyBg = "#0F0F0F"
                themeToolbarBg = "#0A0A0A"
            }
            "blue" -> {
                themeBgColor = "#0B0F19"
                themeAccentColor = "#0078FF"
                themeSpecialKeyBg = "#1E293B"
                themeRegularKeyBg = "#111827"
                themeToolbarBg = "#0F172A"
            }
            "red" -> {
                themeBgColor = "#0B0B0F"
                themeAccentColor = "#FF0055"
                themeSpecialKeyBg = "#1E1F29"
                themeRegularKeyBg = "#12131C"
                themeToolbarBg = "#0F1016"
            }
            "green" -> {
                themeBgColor = "#081C15"
                themeAccentColor = "#2D6A4F"
                themeSpecialKeyBg = "#1B4332"
                themeRegularKeyBg = "#0F2F20"
                themeToolbarBg = "#0B251A"
            }
            "dynamic" -> {
                val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                val dynamicAccent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    try {
                        val color = resources.getColor(android.R.color.system_accent1_500, theme)
                        String.format("#%06X", 0xFFFFFF and color)
                    } catch (e: Exception) {
                        "#00D68F"
                    }
                } else {
                    "#00D68F"
                }

                if (isDark) {
                    themeBgColor = "#121212"
                    themeAccentColor = dynamicAccent
                    themeSpecialKeyBg = "#2E2E2E"
                    themeRegularKeyBg = "#1F1F1F"
                    themeToolbarBg = "#1A1A1A"
                    themeTextColor = Color.WHITE
                    themeHintColor = "#7AFFFFFF"
                } else {
                    themeBgColor = "#F9F9F9"
                    themeAccentColor = dynamicAccent
                    themeSpecialKeyBg = "#D2D5DB"
                    themeRegularKeyBg = "#FFFFFF"
                    themeToolbarBg = "#E5E5EA"
                    themeTextColor = Color.BLACK
                    themeHintColor = "#7A000000"
                }
            }
        }

        val clipJson = prefs.getString(PREF_KEY_CLIPBOARD, null)
        if (clipJson != null) {
            try {
                clipboardHistory.clear()
                val array = JSONArray(clipJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    clipboardHistory.add(
                        ClipboardItem(
                            text = obj.getString("text"),
                            imageUri = obj.optString("imageUri", null),
                            isPinned = obj.optBoolean("isPinned", false),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            clipboardHistory.add(
                ClipboardItem(
                    text = "Welcome to Orbit Keyboard! Tap to paste, pin items to save them permanently.",
                    isPinned = true
                )
            )
            savePreferences()
        }
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putInt(PREF_KEY_HEIGHT, keyboardHeightDp)
        editor.putInt("keyboard_width_percent", keyboardWidthPercent)
        editor.putInt("keyboard_gravity", keyboardGravity)

        val array = JSONArray()
        for (item in clipboardHistory) {
            val obj = JSONObject().apply {
                put("text", item.text)
                if (item.imageUri != null) put("imageUri", item.imageUri)
                put("isPinned", item.isPinned)
                put("timestamp", item.timestamp)
            }
            array.put(obj)
        }
        editor.putString(PREF_KEY_CLIPBOARD, array.toString())
        editor.apply()
    }

    private fun switchToNextLanguage() {
        if (selectedLanguages.isEmpty()) return
        activeLanguageIndex = (activeLanguageIndex + 1) % selectedLanguages.size
        applyActiveLanguage()
    }

    private fun applyActiveLanguage() {
        val activeLang = if (activeLanguageIndex in selectedLanguages.indices) selectedLanguages[activeLanguageIndex] else "en"
        if (activeLang == "hi_phonetic") {
            currentViewMode = ViewMode.HINDI
            isHindiPage2 = false
            Toast.makeText(this, "Hindi Keyboard", Toast.LENGTH_SHORT).show()
        } else {
            currentViewMode = ViewMode.QWERTY
            val langName = when (activeLang) {
                "fr" -> "French AZERTY"
                "es" -> "Spanish QWERTY"
                else -> "English QWERTY"
            }
            Toast.makeText(this, langName, Toast.LENGTH_SHORT).show()
        }
        updateKeyboardLayout()
    }

    private fun addRecentEmoji(emoji: String) {
        recentEmojis.remove(emoji)
        recentEmojis.add(0, emoji)
        if (recentEmojis.size > 21) {
            recentEmojis.removeAt(recentEmojis.size - 1)
        }
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("recent_emojis", recentEmojis.joinToString(",")).apply()
    }

    private fun setupClipboardListener() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.addPrimaryClipChangedListener {
                fetchSystemClipboard()
            }
        } catch (e: Exception) {
            // Ignore security or initialization exceptions
        }
    }

    private fun saveClipboardImageLocally(uriStr: String): String? {
        try {
            val context = this
            val uri = android.net.Uri.parse(uriStr)
            val resolver = context.contentResolver
            resolver.openInputStream(uri)?.use { inputStream ->
                val folder = java.io.File(context.cacheDir, "clipboard_images").apply { mkdirs() }
                val destFile = java.io.File(folder, "img_${System.currentTimeMillis()}.jpg")
                destFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                return destFile.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun addClipboardImageItem(uriStr: String) {
        val localPath = saveClipboardImageLocally(uriStr) ?: return
        val existingIndex = clipboardHistory.indexOfFirst { it.imageUri == localPath }
        val item = if (existingIndex != -1) {
            clipboardHistory.removeAt(existingIndex)
        } else {
            ClipboardItem(text = "[Image]", imageUri = localPath)
        }
        insertAndLimitClipboardItem(item)
    }

    private fun insertAndLimitClipboardItem(item: ClipboardItem) {
        if (item.isPinned) {
            clipboardHistory.add(0, item)
        } else {
            val insertIndex = clipboardHistory.count { it.isPinned }
            clipboardHistory.add(insertIndex, item)
        }
        if (clipboardHistory.size > 100) {
            val lastUnpinnedIndex = clipboardHistory.indexOfLast { !it.isPinned }
            if (lastUnpinnedIndex != -1) {
                clipboardHistory.removeAt(lastUnpinnedIndex)
            }
        }
        savePreferences()
        if (currentViewMode == ViewMode.CLIPBOARD) {
            updateKeyboardLayout()
        }
    }

    private fun fetchSystemClipboard() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val item = clipData.getItemAt(0)
                    val text = item.text?.toString()?.trim()
                    val uri = item.uri
                    
                    if (uri != null && clipData.description.hasMimeType("image/*")) {
                        val uriStr = uri.toString()
                        if (uriStr != lastProcessedSystemClip) {
                            lastProcessedSystemClip = uriStr
                            addClipboardImageItem(uriStr)
                        }
                    } else if (!text.isNullOrEmpty() && text != lastProcessedSystemClip) {
                        lastProcessedSystemClip = text
                        val existingIndex = clipboardHistory.indexOfFirst { it.text == text && it.imageUri == null }
                        val clipItem = if (existingIndex != -1) {
                            clipboardHistory.removeAt(existingIndex)
                        } else {
                            ClipboardItem(text = text)
                        }
                        insertAndLimitClipboardItem(clipItem)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore background clipboard access security exceptions
        }
    }

    private fun addClipboardItem(text: String) {
        val existingIndex = clipboardHistory.indexOfFirst { it.text == text }
        val item = if (existingIndex != -1) {
            clipboardHistory.removeAt(existingIndex)
        } else {
            ClipboardItem(text)
        }

        // Pinned items must always be at the top.
        if (item.isPinned) {
            clipboardHistory.add(0, item)
        } else {
            val insertIndex = clipboardHistory.count { it.isPinned }
            clipboardHistory.add(insertIndex, item)
        }

        if (clipboardHistory.size > 100) {
            val lastUnpinnedIndex = clipboardHistory.indexOfLast { !it.isPinned }
            if (lastUnpinnedIndex != -1) {
                clipboardHistory.removeAt(lastUnpinnedIndex)
            }
        }
        savePreferences()
        if (currentViewMode == ViewMode.CLIPBOARD) {
            updateKeyboardLayout()
        }
    }

    private fun togglePinItem(item: ClipboardItem) {
        if (!item.isPinned) {
            val pinnedCount = clipboardHistory.count { it.isPinned }
            if (pinnedCount >= 10) {
                Toast.makeText(this, "Max 10 pinned items allowed", Toast.LENGTH_SHORT).show()
                return
            }
            item.isPinned = true
            clipboardHistory.remove(item)
            clipboardHistory.add(0, item)
        } else {
            item.isPinned = false
            clipboardHistory.remove(item)
            val insertIndex = clipboardHistory.count { it.isPinned }
            clipboardHistory.add(insertIndex, item)
        }
        savePreferences()
        if (currentViewMode == ViewMode.CLIPBOARD) {
            updateKeyboardLayout()
        }
    }

    private fun deleteClipboardItem(item: ClipboardItem) {
        clipboardHistory.remove(item)
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val systemText = clipData.getItemAt(0).text?.toString()?.trim()
                    if (systemText == item.text) {
                        lastProcessedSystemClip = systemText
                    }
                }
            }
        } catch (e: Exception) {}
        savePreferences()
        if (currentViewMode == ViewMode.CLIPBOARD) {
            updateKeyboardLayout()
        }
    }

    private fun clearClipboard(exceptPinned: Boolean) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    lastProcessedSystemClip = clipData.getItemAt(0).text?.toString()?.trim()
                }
            }
        } catch (e: Exception) {}

        if (exceptPinned) {
            clipboardHistory.removeAll { !it.isPinned }
        } else {
            clipboardHistory.clear()
        }
        savePreferences()
        updateKeyboardLayout()
    }

    private fun updateKeyboardLayout() {
        handler.removeCallbacks(layoutUpdateRunnable)
        handler.post(layoutUpdateRunnable)
    }

    private fun updateKeyboardLayoutInternal() {
        if (!::keyboardContainer.isInitialized) return
        keyboardContainer.removeAllViews()

        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val calculatedHeight = if (isLandscape) {
            (keyboardHeightDp * 0.75f).toInt().coerceIn(160, 240)
        } else {
            keyboardHeightDp
        }
        val params = keyboardContainer.layoutParams
        params.height = dpToPx(calculatedHeight)
        keyboardContainer.layoutParams = params
        if (customBgDrawable != null) {
            keyboardContainer.background = customBgDrawable
        } else {
            keyboardContainer.setBackgroundColor(Color.parseColor(themeBgColor))
        }
        keyboardContainer.clipChildren = false
        keyboardContainer.clipToPadding = false

        val screenWidth = resources.displayMetrics.widthPixels
        val keyboardWidthPx = (screenWidth * (keyboardWidthPercent / 100f)).toInt()
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, 0, 0, dpToPx(8))
            layoutParams = FrameLayout.LayoutParams(
                keyboardWidthPx,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = keyboardGravity or Gravity.BOTTOM
            }
        }

        if (isTranslationActive) {
            mainLayout.addView(createTranslationToolbar())
        } else if (currentViewMode == ViewMode.CLIPBOARD) {
            mainLayout.addView(createNavigationToolbar("Clipboard History"))
        } else if (currentViewMode == ViewMode.PC_SHORTCUTS) {
            mainLayout.addView(createNavigationToolbar("PC Shortcuts"))
        } else {
            if (suggestionsEnabled) {
                mainLayout.addView(createToolbar())
            }
        }

        val keyboardArea = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
        }
        activeKeyboardArea = keyboardArea

        val keysLayout = when (currentViewMode) {
            ViewMode.QWERTY -> createQwertyLayout()
            ViewMode.SYMBOLS -> createSymbolsLayout()
            ViewMode.EMOJIS -> createEmojisLayout()
            ViewMode.CLIPBOARD -> createClipboardLayout()
            ViewMode.PC_SHORTCUTS -> createPcShortcutsLayout()
            ViewMode.HINDI -> createHindiLayout()
            ViewMode.OCR -> createOcrLayout()
        }
        keyboardArea.addView(keysLayout)

        // Initialize and add KeyboardEffectsView
        if (keyboardEffect != "none") {
            val effectsView = KeyboardEffectsView(this).apply {
                setEffectType(keyboardEffect)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            keyboardArea.addView(effectsView)
            keyboardEffectsView = effectsView
        } else {
            keyboardEffectsView = null
        }

        mainLayout.addView(keyboardArea)
        keyboardContainer.addView(mainLayout)

        if (isSizeAdjustActive) {
            val resizeOverlay = FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#99000000"))
            }

            val borderView = View(this).apply {
                val strokeWidth = dpToPx(2)
                background = GradientDrawable().apply {
                    setStroke(strokeWidth, Color.parseColor(themeAccentColor))
                    setColor(Color.TRANSPARENT)
                }
                layoutParams = FrameLayout.LayoutParams(
                    keyboardWidthPx,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply {
                    gravity = keyboardGravity or Gravity.BOTTOM
                }
            }
            resizeOverlay.addView(borderView)

            // Top Drag Handle (Height)
            val topDragHandle = FrameLayout(this).apply {
                background = createKeyDrawable(Color.parseColor(themeAccentColor), dpToPx(4))
                layoutParams = FrameLayout.LayoutParams(
                    dpToPx(120),
                    dpToPx(16)
                ).apply {
                    gravity = keyboardGravity or Gravity.TOP
                }
                val label = TextView(context).apply {
                    text = "↕ Height"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
                addView(label)
            }
            var dragStartY = 0f
            var dragStartHeight = 0
            topDragHandle.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dragStartY = event.rawY
                        dragStartHeight = keyboardHeightDp
                        vibrateClick()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = event.rawY - dragStartY
                        val density = resources.displayMetrics.density
                        val deltaDp = (deltaY / density).toInt()
                        val newHeight = (dragStartHeight - deltaDp).coerceIn(180, 420)
                        if (newHeight != keyboardHeightDp) {
                            keyboardHeightDp = newHeight
                            val rootParams = keyboardContainer.layoutParams
                            rootParams.height = dpToPx(keyboardHeightDp)
                            keyboardContainer.layoutParams = rootParams
                            keyboardContainer.requestLayout()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        vibrateClick()
                        updateKeyboardLayout()
                        true
                    }
                    else -> false
                }
            }
            resizeOverlay.addView(topDragHandle)

            // Left Drag Handle (Width / Left edge)
            val leftDragHandle = FrameLayout(this).apply {
                background = createKeyDrawable(Color.parseColor(themeAccentColor), dpToPx(4))
                layoutParams = FrameLayout.LayoutParams(
                    dpToPx(16),
                    dpToPx(100)
                ).apply {
                    gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
                }
                val label = TextView(context).apply {
                    text = "↔\nW\ni\nd\nt\nh"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
                addView(label)
            }
            var dragStartX = 0f
            var dragStartWidth = 0
            leftDragHandle.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dragStartX = event.rawX
                        dragStartWidth = keyboardWidthPercent
                        vibrateClick()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - dragStartX
                        val density = resources.displayMetrics.density
                        val deltaDp = (deltaX / density).toInt()
                        val deltaPercent = (deltaDp / 5).toInt()
                        val newWidth = (dragStartWidth - deltaPercent).coerceIn(60, 100)
                        if (newWidth != keyboardWidthPercent) {
                            keyboardWidthPercent = newWidth
                            keyboardGravity = Gravity.RIGHT
                            updateKeyboardLayout()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        vibrateClick()
                        updateKeyboardLayout()
                        true
                    }
                    else -> false
                }
            }
            resizeOverlay.addView(leftDragHandle)

            // Right Drag Handle (Width / Right edge)
            val rightDragHandle = FrameLayout(this).apply {
                background = createKeyDrawable(Color.parseColor(themeAccentColor), dpToPx(4))
                layoutParams = FrameLayout.LayoutParams(
                    dpToPx(16),
                    dpToPx(100)
                ).apply {
                    gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
                }
                val label = TextView(context).apply {
                    text = "↔\nW\ni\nd\nt\nh"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
                addView(label)
            }
            rightDragHandle.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dragStartX = event.rawX
                        dragStartWidth = keyboardWidthPercent
                        vibrateClick()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = dragStartX - event.rawX
                        val density = resources.displayMetrics.density
                        val deltaDp = (deltaX / density).toInt()
                        val deltaPercent = (deltaDp / 5).toInt()
                        val newWidth = (dragStartWidth - deltaPercent).coerceIn(60, 100)
                        if (newWidth != keyboardWidthPercent) {
                            keyboardWidthPercent = newWidth
                            keyboardGravity = Gravity.LEFT
                            updateKeyboardLayout()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        vibrateClick()
                        updateKeyboardLayout()
                        true
                    }
                    else -> false
                }
            }
            resizeOverlay.addView(rightDragHandle)

            // Center Gravity Reset Toggle
            val centerToggleBtn = Button(this).apply {
                text = "Center"
                setTextColor(Color.WHITE)
                background = createKeyDrawable(Color.parseColor("#444444"), dpToPx(4))
                layoutParams = FrameLayout.LayoutParams(
                    dpToPx(80),
                    dpToPx(35)
                ).apply {
                    gravity = Gravity.CENTER
                    setMargins(0, 0, 0, dpToPx(50))
                }
                setOnClickListener {
                    vibrateClick()
                    keyboardGravity = Gravity.CENTER_HORIZONTAL
                    updateKeyboardLayout()
                }
            }
            resizeOverlay.addView(centerToggleBtn)

            // Done Button (Save)
            val doneBtn = Button(this).apply {
                text = "Done"
                setTextColor(Color.WHITE)
                background = createKeyDrawable(Color.parseColor(themeAccentColor), dpToPx(4))
                layoutParams = FrameLayout.LayoutParams(
                    dpToPx(80),
                    dpToPx(35)
                ).apply {
                    gravity = Gravity.CENTER
                    setMargins(0, dpToPx(50), 0, 0)
                }
                setOnClickListener {
                    vibrateClick()
                    isSizeAdjustActive = false
                    savePreferences()
                    updateKeyboardLayout()
                }
            }
            resizeOverlay.addView(doneBtn)

            keyboardContainer.addView(resizeOverlay)
        }
    }

    private fun createToolbarIconButton(resId: Int, isRedBackground: Boolean = false, onClick: () -> Unit): View {
        val container = FrameLayout(this).apply {
            val bgColor = if (isRedBackground) Color.parseColor("#D32F2F") else Color.TRANSPARENT
            background = createKeyDrawable(bgColor, dpToPx(6))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(38),
                dpToPx(30)
            ).apply {
                setMargins(dpToPx(4), 0, dpToPx(4), 0)
            }
            setOnClickListener {
                vibrateClick()
                onClick()
            }
        }

        val imageView = ImageView(this).apply {
            setImageResource(resId)
            setColorFilter(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(18),
                dpToPx(18)
            ).apply {
                gravity = Gravity.CENTER
            }
        }
        container.addView(imageView)
        return container
    }

    private fun createToolbar(): View {
        if (isFontSelectorActive) {
            return createFontSelectorView()
        }
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val toolbarHeight = if (isToolbarCollapsed) dpToPx(20) else dpToPx(40)
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor(themeToolbarBg))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                toolbarHeight
            )
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(6), 0, dpToPx(6), 0)
        }

        if (isToolbarCollapsed) {
            val line = View(this).apply {
                background = createKeyDrawable(Color.parseColor("#888888"), dpToPx(2))
                layoutParams = FrameLayout.LayoutParams(dpToPx(60), dpToPx(4)).apply {
                    gravity = Gravity.CENTER
                }
            }
            val container = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                addView(line)
            }
            toolbar.addView(container)
            toolbar.isClickable = true
            toolbar.isFocusable = true
            toolbar.setOnClickListener {
                vibrateClick()
                isToolbarCollapsed = false
                updateKeyboardLayout()
            }
        } else {
            val scrollView = HorizontalScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                isHorizontalScrollBarEnabled = false
            }

            val buttonsContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val collapseBtn = createToolbarIconButton(R.drawable.ic_collapse) {
                isToolbarCollapsed = true
                updateKeyboardLayout()
            }
            buttonsContainer.addView(collapseBtn)

            if (isListening) {
                val waveView = VoiceWaveView(this, themeAccentColor).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        dpToPx(30),
                        1f
                    ).apply {
                        gravity = Gravity.CENTER
                        setMargins(dpToPx(12), 0, dpToPx(12), 0)
                    }
                }
                buttonsContainer.addView(waveView)

                val stopBtn = createToolbarIconButton(R.drawable.ic_mic, true) {
                    stopVoiceInput()
                }
                buttonsContainer.addView(stopBtn)
            } else {
                if (translationFeatureEnabled) {
                    val active = isTranslationActive
                    val activeBgColor = if (active) Color.parseColor(themeAccentColor) else Color.TRANSPARENT
                    val transBtn = FrameLayout(this).apply {
                        background = createKeyDrawable(activeBgColor, dpToPx(6))
                        isClickable = true
                        isFocusable = true
                        layoutParams = LinearLayout.LayoutParams(dpToPx(38), dpToPx(30)).apply {
                            setMargins(dpToPx(4), 0, dpToPx(4), 0)
                        }
                        setOnClickListener {
                            vibrateClick()
                            isTranslationActive = !isTranslationActive
                            updateKeyboardLayout()
                        }
                    }
                    val transIcon = ImageView(this).apply {
                        setImageResource(R.drawable.ic_translate)
                        setColorFilter(Color.WHITE)
                        layoutParams = FrameLayout.LayoutParams(dpToPx(18), dpToPx(18)).apply {
                            gravity = Gravity.CENTER
                        }
                    }
                    transBtn.addView(transIcon)
                    buttonsContainer.addView(transBtn)
                }

                val aiAssistBtn = FrameLayout(this).apply {
                    background = createKeyDrawable(Color.TRANSPARENT, dpToPx(6))
                    isClickable = true
                    isFocusable = true
                    layoutParams = LinearLayout.LayoutParams(dpToPx(38), dpToPx(30)).apply {
                        setMargins(dpToPx(4), 0, dpToPx(4), 0)
                    }
                    setOnClickListener {
                        vibrateClick()
                        showAiAssistantMenu(it)
                    }
                }
                val aiAssistIcon = TextView(this).apply {
                    text = "AI"
                    setTextColor(Color.parseColor(themeAccentColor))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    gravity = Gravity.CENTER
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.CENTER
                    }
                }
                aiAssistBtn.addView(aiAssistIcon)
                buttonsContainer.addView(aiAssistBtn)

                val clipActive = currentViewMode == ViewMode.CLIPBOARD
                val clipBgColor = if (clipActive) Color.parseColor(themeAccentColor) else Color.TRANSPARENT
                val clipBtn = FrameLayout(this).apply {
                    background = createKeyDrawable(clipBgColor, dpToPx(6))
                    isClickable = true
                    isFocusable = true
                    layoutParams = LinearLayout.LayoutParams(dpToPx(38), dpToPx(30)).apply {
                        setMargins(dpToPx(4), 0, dpToPx(4), 0)
                    }
                    setOnClickListener {
                        vibrateClick()
                        currentViewMode = if (clipActive) ViewMode.QWERTY else ViewMode.CLIPBOARD
                        updateKeyboardLayout()
                    }
                }
                val clipIcon = ImageView(this).apply {
                    setImageResource(R.drawable.ic_clipboard)
                    setColorFilter(Color.WHITE)
                    layoutParams = FrameLayout.LayoutParams(dpToPx(18), dpToPx(18)).apply {
                        gravity = Gravity.CENTER
                    }
                }
                clipBtn.addView(clipIcon)
                buttonsContainer.addView(clipBtn)

                val pcActive = currentViewMode == ViewMode.PC_SHORTCUTS
                val pcBgColor = if (pcActive) Color.parseColor(themeAccentColor) else Color.TRANSPARENT
                val pcBtn = FrameLayout(this).apply {
                    background = createKeyDrawable(pcBgColor, dpToPx(6))
                    isClickable = true
                    isFocusable = true
                    layoutParams = LinearLayout.LayoutParams(dpToPx(38), dpToPx(30)).apply {
                        setMargins(dpToPx(4), 0, dpToPx(4), 0)
                    }
                    setOnClickListener {
                        vibrateClick()
                        currentViewMode = if (pcActive) ViewMode.QWERTY else ViewMode.PC_SHORTCUTS
                        updateKeyboardLayout()
                    }
                }
                val pcIcon = ImageView(this).apply {
                    setImageResource(R.drawable.ic_pc)
                    setColorFilter(Color.WHITE)
                    layoutParams = FrameLayout.LayoutParams(dpToPx(18), dpToPx(18)).apply {
                        gravity = Gravity.CENTER
                    }
                }
                pcBtn.addView(pcIcon)
                buttonsContainer.addView(pcBtn)

                val heightBtn = createToolbarIconButton(R.drawable.ic_height) {
                    showSizeAdjustmentDialog()
                }
                buttonsContainer.addView(heightBtn)

                val settingsBtn = createToolbarIconButton(R.drawable.ic_settings) {
                    val launchIntent = packageManager.getLaunchIntentForPackage("com.orbit.smartkeyboard")
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                    } else {
                        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    }
                }
                buttonsContainer.addView(settingsBtn)

                if (voiceDictationEnabled) {
                    val micBtn = createToolbarIconButton(R.drawable.ic_mic, isListening) {
                        if (isListening) {
                            stopVoiceInput()
                        } else {
                            startVoiceInput()
                        }
                    }
                    buttonsContainer.addView(micBtn)
                }

                // Camera OCR Button
                val cameraActive = currentViewMode == ViewMode.OCR
                val cameraBgColor = if (cameraActive) Color.parseColor(themeAccentColor) else Color.TRANSPARENT
                val cameraBtn = FrameLayout(this).apply {
                    background = createKeyDrawable(cameraBgColor, dpToPx(6))
                    isClickable = true
                    isFocusable = true
                    layoutParams = LinearLayout.LayoutParams(dpToPx(38), dpToPx(30)).apply {
                        setMargins(dpToPx(4), 0, dpToPx(4), 0)
                    }
                    setOnClickListener {
                        vibrateClick()
                        toggleOcrMode()
                    }
                }
                val cameraIcon = ImageView(this).apply {
                    setImageResource(R.drawable.ic_camera)
                    setColorFilter(themeTextColor)
                    layoutParams = FrameLayout.LayoutParams(dpToPx(18), dpToPx(18)).apply {
                        gravity = Gravity.CENTER
                    }
                }
                cameraBtn.addView(cameraIcon)
                buttonsContainer.addView(cameraBtn)

                // Font Changer Button immediately next to the mic button
                val fontActive = currentKeyboardFont != KeyboardFont.NORMAL
                val fontBgColor = if (fontActive) Color.parseColor(themeAccentColor) else Color.TRANSPARENT
                val fontBtn = FrameLayout(this).apply {
                    background = createKeyDrawable(fontBgColor, dpToPx(6))
                    isClickable = true
                    isFocusable = true
                    layoutParams = LinearLayout.LayoutParams(dpToPx(38), dpToPx(30)).apply {
                        setMargins(dpToPx(4), 0, dpToPx(4), 0)
                    }
                    setOnClickListener {
                        vibrateClick()
                        isFontSelectorActive = !isFontSelectorActive
                        updateKeyboardLayout()
                    }
                }
                val fontIcon = ImageView(this).apply {
                    setImageResource(R.drawable.ic_font)
                    setColorFilter(Color.WHITE)
                    layoutParams = FrameLayout.LayoutParams(dpToPx(18), dpToPx(18)).apply {
                        gravity = Gravity.CENTER
                    }
                }
                fontBtn.addView(fontIcon)
                buttonsContainer.addView(fontBtn)

                if (isLandscape) {
                    val pcButtons = listOf(
                        Pair(R.drawable.ic_select_all) { sendCtrlShortcut(android.view.KeyEvent.KEYCODE_A) },
                        Pair(R.drawable.ic_copy) { sendCtrlShortcut(android.view.KeyEvent.KEYCODE_C) },
                        Pair(R.drawable.ic_paste) { sendCtrlShortcut(android.view.KeyEvent.KEYCODE_V) },
                        Pair(R.drawable.ic_undo) { sendCtrlShortcut(android.view.KeyEvent.KEYCODE_Z) },
                        Pair(R.drawable.ic_redo) { sendCtrlShortcut(android.view.KeyEvent.KEYCODE_Y) },
                        Pair(R.drawable.ic_left) { 
                            currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_LEFT))
                            currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_LEFT))
                        },
                        Pair(R.drawable.ic_right) {
                            currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_RIGHT))
                            currentInputConnection?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_RIGHT))
                        }
                    )
                    for (btn in pcButtons) {
                        val iconBtn = createToolbarIconButton(btn.first) {
                            vibrateClick()
                            btn.second()
                        }
                        buttonsContainer.addView(iconBtn)
                    }
                }
            }
            scrollView.addView(buttonsContainer)
            toolbar.addView(scrollView)
        }

        return toolbar
    }

        private fun createNavigationToolbar(title: String): View {
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor(themeToolbarBg))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(40)
            )
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
        }

        val backBtn = TextView(this).apply {
            text = "← Back"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            background = createKeyDrawable(Color.parseColor(themeSpecialKeyBg), dpToPx(4))
            layoutParams = LinearLayout.LayoutParams(dpToPx(70), dpToPx(30))
            setOnClickListener {
                vibrateClick()
                currentViewMode = ViewMode.QWERTY
                updateKeyboardLayout()
            }
        }
        toolbar.addView(backBtn)

        val titleView = TextView(this).apply {
            text = title
            setTextColor(Color.parseColor(themeAccentColor))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1.0f
            ).apply {
                rightMargin = dpToPx(8)
            }
        }
        toolbar.addView(titleView)

        return toolbar
    }

    private fun showSizeAdjustmentDialog() {
        isSizeAdjustActive = true
        updateKeyboardLayout()
    }

    private fun createQwertyLayout(): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(4), 0, dpToPx(4))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val activeLang = if (activeLanguageIndex in selectedLanguages.indices) selectedLanguages[activeLanguageIndex] else "en"

        val row1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        
        val row2 = when (activeLang) {
            "fr" -> if (isShifted) listOf("A", "Z", "E", "R", "T", "Y", "U", "I", "O", "P")
                    else listOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "p")
            else -> if (isShifted) listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
                    else listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        }

        val row3 = when (activeLang) {
            "fr" -> if (isShifted) listOf("spacer_half", "Q", "S", "D", "F", "G", "H", "J", "K", "L", "M", "spacer_half")
                    else listOf("spacer_half", "q", "s", "d", "f", "g", "h", "j", "k", "l", "m", "spacer_half")
            "es" -> if (isShifted) listOf("spacer_half", "A", "S", "D", "F", "G", "H", "J", "K", "L", "Ñ", "spacer_half")
                    else listOf("spacer_half", "a", "s", "d", "f", "g", "h", "j", "k", "l", "ñ", "spacer_half")
            else -> if (isShifted) listOf("spacer_half", "A", "S", "D", "F", "G", "H", "J", "K", "L", "spacer_half")
                    else listOf("spacer_half", "a", "s", "d", "f", "g", "h", "j", "k", "l", "spacer_half")
        }

        val row4 = when (activeLang) {
            "fr" -> if (isShifted) listOf("Shift", "W", "X", "C", "V", "B", "N", "Back")
                    else listOf("shift", "w", "x", "c", "v", "b", "n", "Back")
            else -> if (isShifted) listOf("Shift", "Z", "X", "C", "V", "B", "N", "M", "Back")
                    else listOf("shift", "z", "x", "c", "v", "b", "n", "m", "Back")
        }

        val row5 = listOf("?123", ",", "Spacebar", ".", "Enter")

        if (numberRowEnabled) {
            layout.addView(createKeyRow(row1))
        }
        layout.addView(createKeyRow(row2))
        layout.addView(createKeyRow(row3))
        layout.addView(createKeyRow(row4))
        layout.addView(createKeyRow(row5))

        return layout
    }

    private fun createHindiLayout(): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(4), 0, dpToPx(4))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val row1 = listOf("१", "२", "३", "४", "५", "६", "७", "८", "९", "०")
        val row2 = if (isHindiPage2) listOf("अ", "आ", "इ", "ई", "उ", "ऊ", "ऋ", "ए", "ऐ", "ओ")
                   else listOf("क", "ख", "ग", "घ", "ङ", "च", "छ", "ज", "झ", "ञ")
        val row3 = if (isHindiPage2) listOf("spacer_half", "औ", "ा", "ि", "ी", "ु", "ू", "े", "ै", "ो", "spacer_half")
                   else listOf("spacer_half", "ट", "ठ", "ड", "ढ", "ण", "त", "थ", "द", "ध", "spacer_half")
        val row4 = if (isHindiPage2) listOf("1/2", "ौ", "ं", "ः", "्", "ँ", "श्र", "ज्ञ", "क्ष", "Back")
                   else listOf("2/2", "न", "प", "फ", "ब", "भ", "म", "य", "र", "ल", "Back")
        val row5 = listOf("?123", "व", "Spacebar", "स", "ह", "Enter")

        if (numberRowEnabled) {
            layout.addView(createKeyRow(row1))
        }
        layout.addView(createKeyRow(row2))
        layout.addView(createKeyRow(row3))
        layout.addView(createKeyRow(row4))
        layout.addView(createKeyRow(row5))

        return layout
    }

    private fun createSymbolsLayout(): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(4), 0, dpToPx(4))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val row1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        val row2 = if (isSymbolsPage2) {
            listOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "=")
        } else {
            listOf("@", "#", "${'$'}", "%", "&", "-", "+", "(", ")", "/")
        }
        val row3 = if (isSymbolsPage2) {
            listOf("_", "\\", "|", "~", "<", ">", "€", "£", "¥", "•")
        } else {
            listOf("*", "\"", "'", ":", ";", "!", "?", "\\", "_")
        }
        val row4 = if (isSymbolsPage2) {
            listOf("1/2", "~", "`", "|", "<", ">", "=", "[", "]", "Back")
        } else {
            listOf("2/2", "~", "`", "|", "<", ">", "=", "[", "]", "Back")
        }
        val row5 = listOf("ABC", "emoji", "Spacebar", ",", "Enter")

        // Number row is always visible in symbols mode
        layout.addView(createKeyRow(row1))
        layout.addView(createKeyRow(row2))
        layout.addView(createKeyRow(row3))
        layout.addView(createKeyRow(row4))
        layout.addView(createKeyRow(row5))

        return layout
    }

    private fun createKeyRow(keys: List<String>): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
        }

        for (key in keys) {
            if (key.lowercase() == "spacer_half") {
                val spacer = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0.5f
                    )
                }
                row.addView(spacer)
                continue
            }

            val keyView = FrameLayout(this).apply {
                val weight = when (key.lowercase()) {
                    "spacebar", " ", "␣" -> 4.0f
                    "shift", "back", "enter", "?123", "abc", "emoji" -> 1.5f
                    else -> 1.0f
                }
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    weight
                ).apply {
                    setMargins(dpToPx(keySpacingDp), dpToPx(3), dpToPx(keySpacingDp), dpToPx(3))
                }

                val keyLayout = RelativeLayout(context).apply {
                    val isActiveShift = isShifted || isCapsLock
                    val isSpecial = listOf("shift", "back", "enter", "?123", "abc", "emoji").contains(key.lowercase())
                    val bgColor = if (key.lowercase() == "shift" && isActiveShift) {
                        Color.parseColor(themeAccentColor)
                    } else if (isSpecial) {
                        Color.parseColor(themeSpecialKeyBg)
                    } else {
                        Color.parseColor(themeRegularKeyBg)
                    }
                    background = createKeyDrawable(bgColor, dpToPx(6))
                    isClickable = true
                    isFocusable = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val isIconKey = listOf("shift", "back", "enter", "emoji").contains(key.lowercase())
                if (isIconKey) {
                    val imageView = ImageView(context).apply {
                        val iconRes = when (key.lowercase()) {
                            "shift" -> if (isShifted) R.drawable.ic_shift_active else R.drawable.ic_shift
                            "back" -> R.drawable.ic_backspace
                            "enter" -> R.drawable.ic_enter
                            "emoji" -> R.drawable.ic_emoji
                            else -> 0
                        }
                        if (iconRes != 0) {
                            setImageResource(iconRes)
                            setColorFilter(themeTextColor)
                        }
                        val iconSize = if (key.lowercase() == "shift") {
                            if (isShifted || isCapsLock) {
                                if (isLandscape) 28 else 26
                            } else {
                                if (isLandscape) 20 else 18
                            }
                        } else {
                            if (isLandscape) 24 else 22
                        }
                        isClickable = false
                        isFocusable = false
                        layoutParams = RelativeLayout.LayoutParams(
                            dpToPx(iconSize),
                            dpToPx(iconSize)
                        ).apply {
                            addRule(RelativeLayout.CENTER_IN_PARENT)
                        }
                    }
                    keyLayout.addView(imageView)
                } else {
                    val mainTextView = TextView(context).apply {
                        text = when (key.lowercase()) {
                            "spacebar" -> if (currentViewMode == ViewMode.HINDI) "हिन्दी" else "English"
                            else -> key
                        }
                        setTextColor(themeTextColor)
                        val isSingleChar = text.length == 1
                        val textSize = if (isSingleChar) {
                            if (isLandscape) 22f else 20f
                        } else {
                            if (isLandscape) 16f else 15f
                        }
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        gravity = Gravity.CENTER
                        isClickable = false
                        isFocusable = false
                        layoutParams = RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            addRule(RelativeLayout.CENTER_IN_PARENT)
                        }
                    }
                    keyLayout.addView(mainTextView)
                }

                val hint = getHintForKey(key)
                if (hint != null) {
                    val hintTextView = TextView(context).apply {
                        text = hint
                        setTextColor(Color.parseColor(themeHintColor))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                        gravity = Gravity.CENTER
                        isClickable = false
                        isFocusable = false
                        layoutParams = RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            addRule(RelativeLayout.ALIGN_PARENT_TOP)
                            addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                            setMargins(0, dpToPx(2), dpToPx(4), 0)
                        }
                    }
                    keyLayout.addView(hintTextView)
                }

                if (key.lowercase() == "back") {
                    var backspaceRunnable: Runnable? = null
                    keyLayout.setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                vibrateClick()
                                playClick(android.view.KeyEvent.KEYCODE_DEL)
                                startPressEffect(v, event)
                                val targetField = if (isTranslationActive && translationInputField != null) translationInputField
                                    else null
                                if (targetField != null) {
                                    val et = targetField!!
                                    val start = et.selectionStart
                                    val end = et.selectionEnd
                                    if (start != end) {
                                        et.text.delete(Math.min(start, end), Math.max(start, end))
                                    } else {
                                        et.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DEL))
                                        et.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DEL))
                                    }
                                } else {
                                    val ic = currentInputConnection
                                    val selected = ic?.getSelectedText(0)
                                    if (!selected.isNullOrEmpty()) {
                                        ic.commitText("", 1)
                                    } else {
                                        ic?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DEL))
                                        ic?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DEL))
                                    }
                                }
                                
                                backspaceRunnable = object : Runnable {
                                    override fun run() {
                                        vibrateClick()
                                        playClick(android.view.KeyEvent.KEYCODE_DEL)
                                        val repTargetField = if (isTranslationActive && translationInputField != null) translationInputField
                                            else null
                                        if (repTargetField != null) {
                                            val et = repTargetField!!
                                            val start = et.selectionStart
                                            val end = et.selectionEnd
                                            if (start != end) {
                                                et.text.delete(Math.min(start, end), Math.max(start, end))
                                            } else {
                                                et.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DEL))
                                                et.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DEL))
                                            }
                                        } else {
                                            val ic = currentInputConnection
                                            val selected = ic?.getSelectedText(0)
                                            if (!selected.isNullOrEmpty()) {
                                                ic.commitText("", 1)
                                            } else {
                                                ic?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DEL))
                                                ic?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DEL))
                                            }
                                        }
                                        handler.postDelayed(this, 45)
                                    }
                                }
                                handler.postDelayed(backspaceRunnable!!, 380)
                                v.isPressed = true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                backspaceRunnable?.let { handler.removeCallbacks(it) }
                                backspaceRunnable = null
                                v.isPressed = false
                                endPressEffect()
                            }
                        }
                        true
                    }
                } else if (key.lowercase() == "spacebar" || key == " " || key == "␣") {
                    var spaceLongPressedLocal = false
                    val spaceLongPressRunnableLocal = Runnable {
                        spaceLongPressedLocal = true
                        vibrateClick()
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showInputMethodPicker()
                    }

                    var dragStartX = 0f
                    var dragStartY = 0f
                    var swipeDetected = false

                    keyLayout.setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                spaceLongPressedLocal = false
                                swipeDetected = false
                                dragStartX = event.rawX
                                dragStartY = event.rawY
                                v.isPressed = true
                                handler.postDelayed(spaceLongPressRunnableLocal, 1500)
                                startPressEffect(v, event)
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val deltaX = event.rawX - dragStartX
                                val deltaY = event.rawY - dragStartY
                                if (Math.abs(deltaX) > 100 && Math.abs(deltaY) < 50 && !swipeDetected) {
                                    swipeDetected = true
                                    handler.removeCallbacks(spaceLongPressRunnableLocal)
                                    vibrateClick()
                                    switchToNextLanguage()
                                }
                                val location = IntArray(2)
                                v.getLocationOnScreen(location)
                                val keyboardAreaLoc = IntArray(2)
                                activeKeyboardArea?.getLocationOnScreen(keyboardAreaLoc)
                                val relativeX = location[0] - keyboardAreaLoc[0] + event.x
                                val relativeY = location[1] - keyboardAreaLoc[1] + event.y
                                keyboardEffectsView?.addTrailPoint(relativeX, relativeY)
                            }
                            MotionEvent.ACTION_UP -> {
                                handler.removeCallbacks(spaceLongPressRunnableLocal)
                                v.isPressed = false
                                endPressEffect()
                                if (!spaceLongPressedLocal && !swipeDetected) {
                                    playClick(android.view.KeyEvent.KEYCODE_SPACE)
                                    handleKeyPress(key)
                                }
                                v.performClick()
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                handler.removeCallbacks(spaceLongPressRunnableLocal)
                                v.isPressed = false
                                endPressEffect()
                            }
                        }
                        true
                    }
                } else {
                    var startKeyTime = 0L
                    var keyLongPressed = false
                    val keyLongPressRunnable = Runnable {
                        keyLongPressed = true
                        vibrateClick()
                        getHintForKey(key)?.let { hintText ->
                            showKeyPreview(keyLayout, hintText)
                        }
                    }

                    keyLayout.setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                startKeyTime = System.currentTimeMillis()
                                keyLongPressed = false
                                v.isPressed = true
                                showKeyPreview(v, key)
                                handler.postDelayed(keyLongPressRunnable, longPressDelayMs.toLong())
                                startPressEffect(v, event)
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val location = IntArray(2)
                                v.getLocationOnScreen(location)
                                val keyboardAreaLoc = IntArray(2)
                                activeKeyboardArea?.getLocationOnScreen(keyboardAreaLoc)
                                val relativeX = location[0] - keyboardAreaLoc[0] + event.x
                                val relativeY = location[1] - keyboardAreaLoc[1] + event.y
                                keyboardEffectsView?.addTrailPoint(relativeX, relativeY)
                            }
                            MotionEvent.ACTION_UP -> {
                                handler.removeCallbacks(keyLongPressRunnable)
                                v.isPressed = false
                                hideKeyPreview()
                                endPressEffect()
                                if (keyLongPressed) {
                                    val hintText = getHintForKey(key)
                                    if (hintText != null) {
                                        currentInputConnection?.commitText(hintText, 1)
                                    } else {
                                        handleKeyPress(key)
                                    }
                                } else {
                                    val keyCode = when (key.lowercase()) {
                                        "enter" -> android.view.KeyEvent.KEYCODE_ENTER
                                        "spacebar", " ", "␣" -> android.view.KeyEvent.KEYCODE_SPACE
                                        else -> 100
                                    }
                                    playClick(keyCode)
                                    handleKeyPress(key)
                                }
                                v.performClick()
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                handler.removeCallbacks(keyLongPressRunnable)
                                v.isPressed = false
                                hideKeyPreview()
                                endPressEffect()
                            }
                        }
                        true
                    }
                }

                addView(keyLayout)
            }
            row.addView(keyView)
        }

        return row
    }

    private fun getKeyCodeForChar(c: Char): Int {
        val lower = c.lowercaseChar()
        if (lower in 'a'..'z') {
            return android.view.KeyEvent.KEYCODE_A + (lower - 'a')
        }
        if (lower in '0'..'9') {
            return android.view.KeyEvent.KEYCODE_0 + (lower - '0')
        }
        return when (lower) {
            '\n' -> android.view.KeyEvent.KEYCODE_ENTER
            ' ' -> android.view.KeyEvent.KEYCODE_SPACE
            else -> -1
        }
    }

    private fun updateShiftStateBasedOnContext() {
        if (!autoCapEnabled) return
        val oldShifted = isShifted
        val ic = currentInputConnection
        if (ic == null) {
            isShifted = wasLastKeySpace
        } else {
            val textBefore = ic.getTextBeforeCursor(2, 0)
            if (textBefore == null || textBefore.isEmpty()) {
                isShifted = wasLastKeySpace
            } else {
                val str = textBefore.toString()
                if (str.endsWith(" ") || str.endsWith("\n")) {
                    isShifted = true
                } else {
                    if (isShifted && !isCapsLock) {
                        isShifted = false
                    }
                }
            }
        }
        if (isShifted != oldShifted) {
            updateKeyboardLayout()
        }
    }

    private fun handleKeyPress(key: String) {
        if (isCtrlActive || isAltActive) {
            val ic = currentInputConnection
            if (ic != null && key.length == 1) {
                val c = key.first()
                val keyCode = getKeyCodeForChar(c)
                if (keyCode != -1) {
                    var metaState = 0
                    if (isCtrlActive) metaState = metaState or android.view.KeyEvent.META_CTRL_ON or android.view.KeyEvent.META_CTRL_MASK
                    if (isAltActive) metaState = metaState or android.view.KeyEvent.META_ALT_ON or android.view.KeyEvent.META_ALT_MASK
                    val time = android.os.SystemClock.uptimeMillis()
                    ic.sendKeyEvent(android.view.KeyEvent(time, time, android.view.KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
                    ic.sendKeyEvent(android.view.KeyEvent(time, time, android.view.KeyEvent.ACTION_UP, keyCode, 0, metaState))
                }
            }
            isCtrlActive = false
            isAltActive = false
            updateKeyboardLayout()
            return
        }

        if (isTranslationActive && translationInputField != null) {
            val et = translationInputField!!
            val start = et.selectionStart
            val end = et.selectionEnd
            when (key.lowercase()) {
                "back", "⌫" -> {
                    if (start > 0 || end > 0) {
                        if (start != end) {
                            et.text.delete(Math.min(start, end), Math.max(start, end))
                        } else {
                            et.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DEL))
                            et.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DEL))
                        }
                    }
                }
                "enter", "↵" -> {
                    val text = et.text.toString()
                    if (text.isNotEmpty()) {
                        translateText(text, translationSourceLang, translationTargetLang) { result ->
                            if (result != null) {
                                currentInputConnection?.commitText(result, 1)
                                et.text.clear()
                            }
                        }
                    }
                    wasLastKeySpace = true
                }
                "spacebar", " ", "␣" -> {
                    et.text.replace(Math.min(start, end), Math.max(start, end), " ")
                    wasLastKeySpace = true
                }
                "shift", "⇧", "⇪" -> {
                    // Ignored in translation field
                }
                "?123" -> {
                    currentViewMode = ViewMode.SYMBOLS
                    isSymbolsPage2 = false
                    updateKeyboardLayout()
                }
                "1/2", "2/2" -> {
                    if (currentViewMode == ViewMode.HINDI) {
                        isHindiPage2 = !isHindiPage2
                    } else {
                        isSymbolsPage2 = !isSymbolsPage2
                    }
                    updateKeyboardLayout()
                }
                "abc" -> {
                    currentViewMode = ViewMode.QWERTY
                    updateKeyboardLayout()
                }
                "emoji" -> {
                    currentViewMode = ViewMode.EMOJIS
                    updateKeyboardLayout()
                }
                else -> {
                    val transformed = if (key.length == 1) applyFontTransformation(key) else key
                    et.text.replace(Math.min(start, end), Math.max(start, end), transformed)
                    if (isShifted && !isCapsLock) {
                        isShifted = false
                    }
                    wasLastKeySpace = false
                }
            }
            return
        }

        val ic = currentInputConnection ?: return
        when (key.lowercase()) {
            "shift", "⇧", "⇪" -> {
                val now = System.currentTimeMillis()
                if (now - lastShiftPressTime < 300) {
                    isCapsLock = !isCapsLock
                    isShifted = isCapsLock
                } else {
                    if (isCapsLock) {
                        isCapsLock = false
                        isShifted = false
                    } else {
                        isShifted = !isShifted
                    }
                }
                lastShiftPressTime = now
                updateKeyboardLayout()
            }
            "back", "⌫" -> {
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DEL))
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DEL))
                updateShiftStateBasedOnContext()
            }
            "enter", "↵" -> {
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER))
                wasLastKeySpace = true
                updateShiftStateBasedOnContext()
            }
            "spacebar", " ", "␣" -> {
                val now = System.currentTimeMillis()
                if (doubleSpacePeriodEnabled && now - lastSpacePressTime < 300) {
                    val textBefore = ic.getTextBeforeCursor(1, 0)
                    if (textBefore != null && textBefore.toString() == " ") {
                        ic.deleteSurroundingText(1, 0)
                        ic.commitText(". ", 1)
                    } else {
                        ic.commitText(" ", 1)
                    }
                } else {
                    ic.commitText(" ", 1)
                }
                lastSpacePressTime = now
                wasLastKeySpace = true
                updateShiftStateBasedOnContext()
            }
            "?123" -> {
                currentViewMode = ViewMode.SYMBOLS
                isSymbolsPage2 = false
                updateKeyboardLayout()
            }
            "1/2", "2/2" -> {
                if (currentViewMode == ViewMode.HINDI) {
                    isHindiPage2 = !isHindiPage2
                } else {
                    isSymbolsPage2 = !isSymbolsPage2
                }
                updateKeyboardLayout()
            }
            "abc" -> {
                currentViewMode = ViewMode.QWERTY
                updateKeyboardLayout()
            }
            "emoji" -> {
                currentViewMode = ViewMode.EMOJIS
                updateKeyboardLayout()
            }
            else -> {
                val transformed = if (key.length == 1) applyFontTransformation(key) else key
                ic.commitText(transformed, 1)
                if (isShifted && !isCapsLock) {
                    isShifted = false
                }
                wasLastKeySpace = false
                updateShiftStateBasedOnContext()
                updateKeyboardLayout()
            }
        }
    }

    private fun transformChar(c: Char, font: KeyboardFont): String {
        if (font == KeyboardFont.NORMAL) return c.toString()
        if (font == KeyboardFont.CIRCLED) {
            return when (c) {
                in 'A'..'Z' -> (0x24B6 + (c - 'A')).toChar().toString()
                in 'a'..'z' -> (0x24D0 + (c - 'a')).toChar().toString()
                in '1'..'9' -> (0x2460 + (c - '1')).toChar().toString()
                '0' -> "\u24EA"
                else -> c.toString()
            }
        }
        if (font == KeyboardFont.NEGATIVE_CIRCLED) {
            return when (c) {
                in 'A'..'Z' -> String(Character.toChars(0x1F150 + (c - 'A')))
                in 'a'..'z' -> String(Character.toChars(0x1F150 + (c - 'a')))
                in '1'..'9' -> String(Character.toChars(0x2776 + (c - '1')))
                '0' -> "\u24FF"
                else -> c.toString()
            }
        }
        if (font == KeyboardFont.SQUARED) {
            return when (c) {
                in 'A'..'Z' -> String(Character.toChars(0x1F130 + (c - 'A')))
                in 'a'..'z' -> String(Character.toChars(0x1F130 + (c - 'a')))
                else -> c.toString()
            }
        }
        if (font == KeyboardFont.NEGATIVE_SQUARED) {
            return when (c) {
                in 'A'..'Z' -> String(Character.toChars(0x1F170 + (c - 'A')))
                in 'a'..'z' -> String(Character.toChars(0x1F170 + (c - 'a')))
                else -> c.toString()
            }
        }
        if (font == KeyboardFont.FULLWIDTH) {
            return when (c) {
                in 'A'..'Z' -> (0xFF21 + (c - 'A')).toChar().toString()
                in 'a'..'z' -> (0xFF41 + (c - 'a')).toChar().toString()
                in '0'..'9' -> (0xFF10 + (c - '0')).toChar().toString()
                else -> c.toString()
            }
        }
        if (font == KeyboardFont.SMALL_CAPS) {
            val map = mapOf(
                'a' to "ᴀ", 'b' to "ʙ", 'c' to "ᴄ", 'd' to "ᴅ", 'e' to "ᴇ", 'f' to "ғ", 'g' to "ɢ",
                'h' to "ₕ", 'i' to "ɪ", 'j' to "ᴊ", 'k' to "ᴋ", 'l' to "ʟ", 'm' to "ᴍ", 'n' to "ɴ",
                'o' to "ᴏ", 'p' to "ᴘ", 'q' to "ǫ", 'r' to "ʀ", 's' to "s", 't' to "ᴛ", 'u' to "ᴜ",
                'v' to "ᴠ", 'w' to "ᴡ", 'x' to "x", 'y' to "ʏ", 'z' to "ᴢ",
                'A' to "ᴀ", 'B' to "ʙ", 'C' to "ᴄ", 'D' to "ᴅ", 'E' to "ᴇ", 'F' to "ғ", 'G' to "ɢ",
                'H' to "ₕ", 'I' to "ɪ", 'J' to "ᴊ", 'K' to "ᴋ", 'L' to "ʟ", 'M' to "ᴍ", 'N' to "ɴ",
                'O' to "ᴏ", 'P' to "ᴘ", 'Q' to "ǫ", 'R' to "ʀ", 'S' to "s", 'T' to "ᴛ", 'U' to "ᴜ",
                'V' to "ᴠ", 'W' to "ᴡ", 'X' to "x", 'Y' to "ʏ", 'Z' to "ᴢ"
            )
            return map[c] ?: c.toString()
        }
        if (font == KeyboardFont.PARENTHESIZED) {
            return when (c) {
                in 'A'..'Z' -> String(Character.toChars(0x249C + (c - 'A')))
                in 'a'..'z' -> String(Character.toChars(0x249C + (c - 'a')))
                in '1'..'9' -> (0x2474 + (c - '1')).toChar().toString()
                else -> c.toString()
            }
        }
        if (font == KeyboardFont.SUBSCRIPT) {
            val map = mapOf(
                'a' to "ₐ", 'e' to "ₑ", 'h' to "ₕ", 'i' to "ᵢ", 'j' to "ⱼ", 'k' to "ₖ", 'l' to "ₗ",
                'm' to "ₘ", 'n' to "ₙ", 'o' to "ₒ", 'p' to "ₚ", 'r' to "ᵣ", 's' to "ₛ", 't' to "ₜ",
                'u' to "ᵤ", 'v' to "ᵥ", 'x' to "ₓ",
                '0' to "₀", '1' to "₁", '2' to "₂", '3' to "₃", '4' to "₄", '5' to "₅", '6' to "₆",
                '7' to "₇", '8' to "₈", '9' to "₉"
            )
            return map[c.lowercaseChar()] ?: c.toString()
        }
        if (font == KeyboardFont.SUPERSCRIPT) {
            val map = mapOf(
                'a' to "ᵃ", 'b' to "ᵇ", 'c' to "ᶜ", 'd' to "ᵈ", 'e' to "ᵉ", 'f' to "ᶠ", 'g' to "ᵍ",
                'h' to "ʰ", 'i' to "ⁱ", 'j' to "ʲ", 'k' to "ᵏ", 'l' to "ˡ", 'm' to "ᵐ", 'n' to "ⁿ",
                'o' to "ᵒ", 'p' to "ᵖ", 'r' to "ʳ", 's' to "ˢ", 't' to "ᵗ", 'u' to "ᵘ", 'v' to "ᵛ",
                'w' to "ʷ", 'x' to "ˣ", 'y' to "ʸ", 'z' to "ᶻ",
                '0' to "⁰", '1' to "¹", '2' to "²", '3' to "³", '4' to "⁴", '5' to "⁵", '6' to "⁶",
                '7' to "⁷", '8' to "⁸", '9' to "⁹"
            )
            return map[c.lowercaseChar()] ?: c.toString()
        }
        if (font == KeyboardFont.STRIKETHROUGH) return c.toString() + "\u0336"
        if (font == KeyboardFont.SLASHED) return c.toString() + "\u0338"
        if (font == KeyboardFont.UNDERLINE) return c.toString() + "\u0332"
        if (font == KeyboardFont.DOUBLE_UNDERLINE) return c.toString() + "\u0333"
        if (font == KeyboardFont.OVERLINE) return c.toString() + "\u0305"
        if (font == KeyboardFont.DOTTED) return c.toString() + "\u0323"
        if (font == KeyboardFont.WAVY) return c.toString() + "\u0330"
        if (font == KeyboardFont.ZALGO) return c.toString() + "\u030d\u030e\u030f\u0311"
        if (font == KeyboardFont.INVERTED) {
            val map = mapOf(
                'A' to "Ɐ", 'B' to "ᗺ", 'C' to "Ɔ", 'D' to "ᗡ", 'E' to "Ǝ", 'F' to "Ⅎ", 'G' to "⅁",
                'J' to "Ր", 'K' to "ʞ", 'L' to "˥", 'M' to "W", 'R' to "ᴚ", 'T' to "⊥", 'U' to "∩",
                'V' to "Ʌ", 'W' to "M", 'Y' to "⅄", 'a' to "ɐ", 'b' to "q", 'c' to "ɔ", 'd' to "p",
                'e' to "ǝ", 'f' to "ɟ", 'g' to "ƃ", 'h' to "ɥ", 'i' to "ᵹ", 'j' to "ɾ", 'k' to "ʞ",
                'm' to "ɯ", 'n' to "u", 'p' to "d", 'q' to "b", 'r' to "ɹ", 't' to "ʇ", 'u' to "n",
                'v' to "ʌ", 'w' to "ʍ", 'y' to "ʎ"
            )
            return map[c] ?: c.toString()
        }
        if (font == KeyboardFont.REVERSED) {
            val map = mapOf(
                'A' to "ᗄ", 'B' to "ᗷ", 'C' to "Ɔ", 'D' to "ᗡ", 'E' to "Ǝ", 'F' to "Ⅎ", 'G' to "⅁",
                'J' to "ᓀ", 'K' to "Ⱈ", 'L' to "⅃", 'N' to "ᴎ", 'P' to "Ԁ", 'Q' to "Ծ", 'R' to "Я",
                'S' to "Ƨ", 'Y' to "ʏ", 'a' to "ɒ", 'b' to "d", 'c' to "ɔ", 'd' to "b", 'e' to "ɘ",
                'f' to "ɟ", 'g' to "ɒ", 'h' to "ʜ", 'j' to "ᓀ", 'k' to "ʞ", 'p' to "q", 'q' to "p",
                'r' to "ɿ", 's' to "ƨ", 't' to "ʇ", 'y' to "ʏ"
            )
            return map[c] ?: c.toString()
        }
        if (font == KeyboardFont.MORSE) {
            val map = mapOf(
                'A' to ".- ", 'B' to "-... ", 'C' to "-.-. ", 'D' to "-.. ", 'E' to ". ", 'F' to "..-. ", 'G' to "--. ",
                'H' to ".... ", 'I' to ".. ", 'J' to ".--- ", 'K' to "-.- ", 'L' to ".-.. ", 'M' to "-- ", 'N' to "-. ",
                'O' to "--- ", 'P' to ".--. ", 'Q' to "--.- ", 'R' to ".-. ", 'S' to "... ", 'T' to "- ", 'U' to "..- ",
                'V' to "...- ", 'W' to ".-- ", 'X' to "-..- ", 'Y' to "-.-- ", 'Z' to "--.. ",
                '0' to "----- ", '1' to ".---- ", '2' to "..--- ", '3' to "...-- ", '4' to "....- ", '5' to "..... ",
                '6' to "-.... ", '7' to "--... ", '8' to "---.. ", '9' to "----. "
            )
            return map[c.uppercaseChar()] ?: c.toString()
        }
        if (font == KeyboardFont.FANCY_BRACKETS) return "【" + c + "】"
        if (font == KeyboardFont.FANCY_ARROWS) return c.toString() + "➔"
        if (font == KeyboardFont.FANCY_HEARTS) return c.toString() + "♥"
        if (font == KeyboardFont.FANCY_STARS) return c.toString() + "★"
        if (font == KeyboardFont.FANCY_SPARKLES) return c.toString() + "✨"
        if (font == KeyboardFont.FANCY_SMILEY) return c.toString() + "☺"
        if (font == KeyboardFont.DOUBLE_PARENTHESES) return "((" + c + "))"
        if (font == KeyboardFont.CYBER) return "[" + c + "]"
        if (font == KeyboardFont.STARLIGHT) return "★" + c + "★"
        if (font == KeyboardFont.DIAMOND) return "♦" + c + "♦"
        if (font == KeyboardFont.TSUNAMI) return "~" + c + "~"
        if (font == KeyboardFont.DOUBLE_SLASH) return "//" + c + "//"
        if (font == KeyboardFont.SLASH_BOX) return "/" + c + "/"
        if (font == KeyboardFont.SHARP) return "#" + c + "#"
        if (font == KeyboardFont.CROSSED) return c.toString() + "❌"

        return when (font) {
            KeyboardFont.BOLD_SERIF -> {
                when (c) {
                    in 'A'..'Z' -> String(Character.toChars(0x1D400 + (c - 'A')))
                    in 'a'..'z' -> String(Character.toChars(0x1D41A + (c - 'a')))
                    in '0'..'9' -> String(Character.toChars(0x1D7CE + (c - '0')))
                    else -> c.toString()
                }
            }
            KeyboardFont.ITALIC_SERIF -> {
                when (c) {
                    in 'A'..'Z' -> String(Character.toChars(0x1D434 + (c - 'A')))
                    in 'a'..'z' -> String(Character.toChars(0x1D44E + (c - 'a')))
                    else -> c.toString()
                }
            }
            KeyboardFont.BOLD_ITALIC_SERIF -> {
                when (c) {
                    in 'A'..'Z' -> String(Character.toChars(0x1D468 + (c - 'A')))
                    in 'a'..'z' -> String(Character.toChars(0x1D482 + (c - 'a')))
                    else -> c.toString()
                }
            }
            KeyboardFont.MATH_BOLD_SANS -> {
                when (c) {
                    in 'A'..'Z' -> String(Character.toChars(0x1D5A0 + (c - 'A')))
                    in 'a'..'z' -> String(Character.toChars(0x1D5BA + (c - 'a')))
                    in '0'..'9' -> String(Character.toChars(0x1D7EC + (c - '0')))
                    else -> c.toString()
                }
            }
            KeyboardFont.MATH_ITALIC_SANS -> {
                when (c) {
                    in 'A'..'Z' -> String(Character.toChars(0x1D608 + (c - 'A')))
                    in 'a'..'z' -> String(Character.toChars(0x1D622 + (c - 'a')))
                    else -> c.toString()
                }
            }
            KeyboardFont.MATH_BOLD_ITALIC_SANS -> {
                when (c) {
                    in 'A'..'Z' -> String(Character.toChars(0x1D63C + (c - 'A')))
                    in 'a'..'z' -> String(Character.toChars(0x1D656 + (c - 'a')))
                    else -> c.toString()
                }
            }
            KeyboardFont.SCRIPT -> {
                when (c) {
                    in 'A'..'Z' -> {
                        // script capitals have some anomalies in unicode standard
                        val map = mapOf(
                            'B' to "\u212C", 'E' to "\u2130", 'F' to "\u2131", 'H' to "\u210B",
                            'I' to "\u2110", 'L' to "\u2112", 'M' to "\u2133", 'R' to "\u211B"
                        )
                        map[c] ?: String(Character.toChars(0x1D49C + (c - 'A')))
                    }
                    in 'a'..'z' -> {
                        if (c == 'e') "\u212F" else if (c == 'g') "\u210A" else if (c == 'o') "\u2134"
                        else String(Character.toChars(0x1D4B6 + (c - 'a')))
                    }
                    else -> c.toString()
                }
            }
            KeyboardFont.BOLD_SCRIPT -> {
                when (c) {
                    in 'A'..'Z' -> String(Character.toChars(0x1D4D0 + (c - 'A')))
                    in 'a'..'z' -> String(Character.toChars(0x1D4EA + (c - 'a')))
                    else -> c.toString()
                }
            }
            KeyboardFont.GOTHIC -> {
                when (c) {
                    in 'A'..'Z' -> {
                        val map = mapOf('C' to "\u212D", 'H' to "\u210C", 'I' to "\u2111", 'R' to "\u211C", 'Z' to "\u2128")
                        map[c] ?: String(Character.toChars(0x1D504 + (c - 'A')))
                    }
                    in 'a'..'z' -> String(Character.toChars(0x1D51E + (c - 'a')))
                    else -> c.toString()
                }
            }
            KeyboardFont.BOLD_GOTHIC -> {
                when (c) {
                    in 'A'..'Z' -> String(Character.toChars(0x1D5D4 + (c - 'A')))
                    in 'a'..'z' -> String(Character.toChars(0x1D5EE + (c - 'a')))
                    else -> c.toString()
                }
            }
            KeyboardFont.OUTLINE -> {
                when (c) {
                    in 'A'..'Z' -> {
                        val map = mapOf(
                            'C' to "\u2102", 'H' to "\u210D", 'N' to "\u2115", 'P' to "\u2119",
                            'Q' to "\u211A", 'R' to "\u211D", 'Z' to "\u2124"
                        )
                        map[c] ?: String(Character.toChars(0x1D538 + (c - 'A')))
                    }
                    in 'a'..'z' -> String(Character.toChars(0x1D552 + (c - 'a')))
                    in '0'..'9' -> String(Character.toChars(0x1D7D8 + (c - '0')))
                    else -> c.toString()
                }
            }
            KeyboardFont.TYPEWRITER -> {
                when (c) {
                    in 'A'..'Z' -> String(Character.toChars(0x1D670 + (c - 'A')))
                    in 'a'..'z' -> String(Character.toChars(0x1D68A + (c - 'a')))
                    in '0'..'9' -> String(Character.toChars(0x1D7F6 + (c - '0')))
                    else -> c.toString()
                }
            }
            KeyboardFont.CURSIVE -> {
                when (c) {
                    in 'A'..'Z' -> String(Character.toChars(0x1D49C + (c - 'A')))
                    in 'a'..'z' -> String(Character.toChars(0x1D4B6 + (c - 'a')))
                    else -> c.toString()
                }
            }
            KeyboardFont.PARENTHESIZED_DIGITS -> {
                when (c) {
                    in '1'..'9' -> (0x2474 + (c - '1')).toChar().toString()
                    else -> c.toString()
                }
            }
            KeyboardFont.NEGATIVE_CIRCLED_DIGITS -> {
                when (c) {
                    in '1'..'9' -> String(Character.toChars(0x2776 + (c - '1')))
                    '0' -> "\u24FF"
                    else -> c.toString()
                }
            }
            else -> c.toString()
        }
    }

    private fun applyFontTransformation(text: String): String {
        if (currentKeyboardFont == KeyboardFont.NORMAL) return text
        val sb = StringBuilder()
        for (char in text) {
            sb.append(transformChar(char, currentKeyboardFont))
        }
        return sb.toString()
    }

    private fun pasteClipboardImage(item: ClipboardItem) {
        val uriStr = item.imageUri ?: return
        try {
            val file = java.io.File(uriStr)
            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "com.orbit.smartkeyboard.fileprovider",
                file
            )
            val editorInfo = currentInputEditorInfo
            val ic = currentInputConnection
            if (ic != null && editorInfo != null) {
                val mimeTypes = androidx.core.view.inputmethod.EditorInfoCompat.getContentMimeTypes(editorInfo)
                var isSupported = false
                for (mime in mimeTypes) {
                    if (mime.startsWith("image/")) {
                        isSupported = true
                        break
                    }
                }
                if (isSupported) {
                    val description = android.content.ClipDescription("Image", arrayOf("image/jpeg", "image/png", "image/webp"))
                    val inputContentInfo = androidx.core.view.inputmethod.InputContentInfoCompat(
                        contentUri,
                        description,
                        null
                    )
                    var flags = 0
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
                        flags = flags or androidx.core.view.inputmethod.InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                    }
                    androidx.core.view.inputmethod.InputConnectionCompat.commitContent(
                        ic,
                        editorInfo,
                        inputContentInfo,
                        flags,
                        null
                    )
                    return
                }
            }
            // Fallback to Clipboard Manager
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newUri(contentResolver, "Image", contentUri)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied image to clipboard (App doesn't support direct paste)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to paste image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createFontSelectorView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor(themeToolbarBg))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val backBtn = createToolbarIconButton(R.drawable.ic_collapse) {
            isFontSelectorActive = false
            updateKeyboardLayout()
        }
        root.addView(backBtn)

        val scrollView = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            isHorizontalScrollBarEnabled = false
        }

        val itemsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
        }

        val fonts = listOf(
            Pair(KeyboardFont.NORMAL, "Normal"),
            Pair(KeyboardFont.BOLD_SERIF, "𝐁𝐨𝐥𝐝 𝐒𝐞𝐫𝐢𝐟"),
            Pair(KeyboardFont.ITALIC_SERIF, "𝘐𝘵𝘢𝘭𝘪𝘤 𝘚𝘦𝘳𝘪𝘧"),
            Pair(KeyboardFont.BOLD_ITALIC_SERIF, "𝑩𝒐𝒍𝒅 𝑰𝒕𝒂𝒍𝒊𝒄"),
            Pair(KeyboardFont.SCRIPT, "𝒮𝒸𝓇𝒾𝓅𝓉"),
            Pair(KeyboardFont.BOLD_SCRIPT, "𝓢𝓬𝓻𝓲𝓹𝓽 𝓑𝓸𝓵𝓭"),
            Pair(KeyboardFont.GOTHIC, "𝔊𝔬𝔱𝔥𝔦𝔠"),
            Pair(KeyboardFont.BOLD_GOTHIC, "𝕲𝖔𝖙𝖍𝖎𝖈 𝕭𝖔𝖑𝖉"),
            Pair(KeyboardFont.OUTLINE, "𝕆𝕦𝕥𝕝𝕚𝕟𝕖"),
            Pair(KeyboardFont.TYPEWRITER, "𝚃𝚢𝚙𝚎𝚠𝚛𝚒𝚝𝚎𝚛"),
            Pair(KeyboardFont.CIRCLED, "Ⓒⓘⓡⓒⓛⓔⓓ"),
            Pair(KeyboardFont.NEGATIVE_CIRCLED, "🅝🅔🅖🅐🅣🅘🅥🅔"),
            Pair(KeyboardFont.SQUARED, "🅂🅄🅄🄰🅁🄴🄳"),
            Pair(KeyboardFont.NEGATIVE_SQUARED, "🅽🅴🅶🆂🆀🆄"),
            Pair(KeyboardFont.FULLWIDTH, "Ｆｕｌｌｗｉｄｔｈ"),
            Pair(KeyboardFont.SMALL_CAPS, "sᴍᴀʟʟ ᴄᴀᴘs"),
            Pair(KeyboardFont.PARENTHESIZED, "⒫⒜⒭⒠⒩⒯⒣"),
            Pair(KeyboardFont.SUBSCRIPT, "ₛᵤ₆ₛ꜀ᵣᵢₚₜ"),
            Pair(KeyboardFont.SUPERSCRIPT, "ˢᵘᵖᵉʳˢᶜʳⁱᵖᵗ"),
            Pair(KeyboardFont.STRIKETHROUGH, "S̶t̶r̶i̶k̶e̶"),
            Pair(KeyboardFont.SLASHED, "S̷l̷a̷s̷h̷e̷d̷"),
            Pair(KeyboardFont.UNDERLINE, "U̲n̲d̲e̲r̲l̲i̲n̲e̲"),
            Pair(KeyboardFont.DOUBLE_UNDERLINE, "D̳o̳u̳b̳l̳e̳"),
            Pair(KeyboardFont.OVERLINE, "O̅v̅e̅r̅l̅i̅n̅e̅"),
            Pair(KeyboardFont.DOTTED, "Ḍọṭṭẹḍ"),
            Pair(KeyboardFont.WAVY, "W̰a̰v̰y̰"),
            Pair(KeyboardFont.ZALGO, "Z̶A̶L̶G̶O̶"),
            Pair(KeyboardFont.INVERTED, "ʇɹǝʌuI"),
            Pair(KeyboardFont.REVERSED, "bɘꙅɿɘvɘᴙ"),
            Pair(KeyboardFont.MORSE, "-- --- .-. ... ."),
            Pair(KeyboardFont.FANCY_BRACKETS, "【B】【r】【a】"),
            Pair(KeyboardFont.FANCY_ARROWS, "A➔r➔r➔"),
            Pair(KeyboardFont.FANCY_HEARTS, "H♥e♥a♥"),
            Pair(KeyboardFont.FANCY_STARS, "S★t★a★"),
            Pair(KeyboardFont.FANCY_SPARKLES, "S✨p✨a✨"),
            Pair(KeyboardFont.FANCY_SMILEY, "S☺m☺i☺"),
            Pair(KeyboardFont.MATH_BOLD_SANS, "𝗕𝗼𝗹𝗱 𝗦𝗮𝗻𝘀"),
            Pair(KeyboardFont.MATH_ITALIC_SANS, "𝘐𝘵𝘢𝘭𝘪𝘤 𝘚𝘢𝘯𝘴"),
            Pair(KeyboardFont.MATH_BOLD_ITALIC_SANS, "𝘽𝒐𝒍𝒅 𝙄𝒕𝒂𝒍𝘪𝙘 𝙎𝒂𝒏𝒔"),
            Pair(KeyboardFont.CURSIVE, "𝒸𝓊𝓇𝓈𝒾𝓋𝑒"),
            Pair(KeyboardFont.PARENTHESIZED_DIGITS, "⑴⑵⑶"),
            Pair(KeyboardFont.NEGATIVE_CIRCLED_DIGITS, "❶❷❸"),
            Pair(KeyboardFont.DOUBLE_PARENTHESES, "((D))((P))"),
            Pair(KeyboardFont.CYBER, "[C][y][b]"),
            Pair(KeyboardFont.STARLIGHT, "★S★T★"),
            Pair(KeyboardFont.DIAMOND, "♦D♦I♦"),
            Pair(KeyboardFont.TSUNAMI, "~T~S~"),
            Pair(KeyboardFont.DOUBLE_SLASH, "//S//L//"),
            Pair(KeyboardFont.SLASH_BOX, "/S/B/"),
            Pair(KeyboardFont.SHARP, "#S#H#"),
            Pair(KeyboardFont.CROSSED, "C❌r❌")
        )

        for (f in fonts) {
            val isCurrent = currentKeyboardFont == f.first
            val bg = if (isCurrent) Color.parseColor(themeAccentColor) else Color.TRANSPARENT
            val textBtn = TextView(this).apply {
                text = f.second
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4))
                background = createKeyDrawable(bg, dpToPx(4))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(dpToPx(4), 0, dpToPx(4), 0)
                }
                setOnClickListener {
                    vibrateClick()
                    currentKeyboardFont = f.first
                    isFontSelectorActive = false
                    updateKeyboardLayout()
                }
            }
            itemsLayout.addView(textBtn)
        }

        scrollView.addView(itemsLayout)
        root.addView(scrollView)
        return root
    }

    private fun getHintForKey(key: String): String? {
        if (key.length != 1) return null
        val c = key.first().lowercaseChar()
        return when (c) {
            'q' -> "1"
            'w' -> "2"
            'e' -> "3"
            'r' -> "4"
            't' -> "5"
            'y' -> "6"
            'u' -> "7"
            'i' -> "8"
            'o' -> "9"
            'p' -> "0"
            'a' -> "@"
            's' -> "#"
            'd' -> "$"
            'f' -> "%"
            'g' -> "&"
            'h' -> "-"
            'j' -> "+"
            'k' -> "("
            'l' -> ")"
            'z' -> "*"
            'x' -> "\""
            'c' -> "'"
            'v' -> ":"
            'b' -> ";"
            'n' -> "!"
            'm' -> "?"
            '1' -> "!"
            '2' -> "@"
            '3' -> "#"
            '4' -> "$"
            '5' -> "%"
            '6' -> "^"
            '7' -> "&"
            '8' -> "*"
            '9' -> "("
            '0' -> ")"
            else -> null
        }
    }

    private fun handleLongPress(key: String) {
        vibrateClick()
        val ic = currentInputConnection ?: return
        val hint = getHintForKey(key)
        if (hint != null) {
            ic.commitText(hint, 1)
        } else if (key.lowercase() == "spacebar" || key == " " || key == "␣") {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
    }

    private fun createEmojisLayout(): View {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val tabLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor(themeToolbarBg))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(38)
            )
            gravity = Gravity.CENTER_VERTICAL
        }

        val abcBtn = TextView(this).apply {
            text = "ABC"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            background = createKeyDrawable(Color.parseColor(themeSpecialKeyBg), dpToPx(4))
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(45),
                dpToPx(30)
            ).apply {
                setMargins(dpToPx(6), 0, dpToPx(4), 0)
            }
            setOnClickListener {
                vibrateClick()
                currentViewMode = ViewMode.QWERTY
                updateKeyboardLayout()
            }
        }
        tabLayout.addView(abcBtn)

        val tabContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(4), 0, dpToPx(4), 0)
        }

        val scrollTabs = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
            )
            isHorizontalScrollBarEnabled = false
            addView(tabContainer)
        }
        tabLayout.addView(scrollTabs)

        val emojiBackspace = ImageView(this).apply {
            setImageResource(R.drawable.ic_backspace)
            setColorFilter(Color.WHITE)
            background = createKeyDrawable(Color.parseColor(themeSpecialKeyBg), dpToPx(4))
            val pad = dpToPx(5)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(50),
                dpToPx(30)
            ).apply {
                setMargins(dpToPx(4), 0, dpToPx(6), 0)
            }
            
            var backspaceRunnable: Runnable? = null
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        vibrateClick()
                        playClick(android.view.KeyEvent.KEYCODE_DEL)
                                val targetField = if (isTranslationActive && translationInputField != null) translationInputField
                                    else null
                        if (targetField != null) {
                            val et = targetField!!
                            val start = et.selectionStart
                            val end = et.selectionEnd
                            if (start > 0 || end > 0) {
                                if (start != end) {
                                    et.text.delete(Math.min(start, end), Math.max(start, end))
                                } else {
                                    et.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DEL))
                                    et.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DEL))
                                }
                            }
                        } else {
                            currentInputConnection?.let { ic ->
                                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DEL))
                                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DEL))
                            }
                        }
                        
                        backspaceRunnable = object : Runnable {
                            override fun run() {
                                vibrateClick()
                                playClick(android.view.KeyEvent.KEYCODE_DEL)
                                val repTargetField = if (isTranslationActive && translationInputField != null) translationInputField
                                    else null
                                if (repTargetField != null) {
                                    val et = repTargetField!!
                                    val start = et.selectionStart
                                    val end = et.selectionEnd
                                    if (start > 0 || end > 0) {
                                        if (start != end) {
                                            et.text.delete(Math.min(start, end), Math.max(start, end))
                                        } else {
                                            et.text.delete(start - 1, start)
                                        }
                                    }
                                } else {
                                    currentInputConnection?.let { ic ->
                                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DEL))
                                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DEL))
                            }
                                }
                                handler.postDelayed(this, 55)
                            }
                        }
                        handler.postDelayed(backspaceRunnable!!, 380)
                        v.isPressed = true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        backspaceRunnable?.let { handler.removeCallbacks(it) }
                        backspaceRunnable = null
                        v.isPressed = false
                    }
                }
                true
            }
        }
        tabLayout.addView(emojiBackspace)
        rootLayout.addView(tabLayout)

        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(1)
            )
            setBackgroundColor(Color.parseColor("#252525"))
        }
        rootLayout.addView(divider)

        val contentArea = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        rootLayout.addView(contentArea)

        fun loadEmojiGrid(categoryIndex: Int) {
            contentArea.removeAllViews()
            
            val categoryEmojis = emojiCategories[categoryIndex].second
            val gridView = GridView(this@GlideTypeKeyboardService).apply {
                numColumns = 7
                verticalSpacing = dpToPx(4)
                horizontalSpacing = dpToPx(4)
                stretchMode = GridView.STRETCH_COLUMN_WIDTH
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
                
                adapter = object : android.widget.BaseAdapter() {
                    override fun getCount(): Int = categoryEmojis.size
                    override fun getItem(position: Int): Any = categoryEmojis[position]
                    override fun getItemId(position: Int): Long = position.toLong()
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                        val textView = (convertView as? TextView) ?: TextView(this@GlideTypeKeyboardService).apply {
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                            gravity = Gravity.CENTER
                            val itemSize = dpToPx(42)
                            layoutParams = AbsListView.LayoutParams(itemSize, itemSize)
                            background = createKeyDrawable(Color.TRANSPARENT, dpToPx(4))
                        }
                        textView.text = categoryEmojis[position]
                        return textView
                    }
                }
            }
            gridView.onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, position, _ ->
                val selectedEmoji = categoryEmojis[position]
                vibrateClick()
                playClick(100)
                addRecentEmoji(selectedEmoji)
                        val targetField = if (isTranslationActive && translationInputField != null) translationInputField
                            else null
                    if (targetField != null) {
                        val et = targetField!!
                        val start = et.selectionStart
                        val end = et.selectionEnd
                        et.text.replace(Math.min(start, end), Math.max(start, end), selectedEmoji)
                    } else {
                    currentInputConnection?.commitText(selectedEmoji, 1)
                }
                if (categoryIndex == 0) {
                    loadEmojiGrid(categoryIndex)
                }
            }
            contentArea.addView(gridView)
        }

        for (i in emojiCategories.indices) {
            val category = emojiCategories[i]
            val resId = category.first
            val isActive = i == currentEmojiCategoryIndex

            val tabView = ImageView(this).apply {
                setImageResource(resId)
                setColorFilter(Color.WHITE)
                val pad = dpToPx(6)
                setPadding(pad, pad, pad, pad)
                background = createKeyDrawable(
                    if (isActive) Color.parseColor(themeAccentColor) else Color.TRANSPARENT,
                    dpToPx(4)
                )

                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(40),
                    dpToPx(30)
                ).apply {
                    setMargins(dpToPx(4), 0, dpToPx(4), 0)
                }

                setOnClickListener {
                    vibrateClick()
                    currentEmojiCategoryIndex = i
                    for (childIdx in 0 until tabContainer.childCount) {
                        val child = tabContainer.getChildAt(childIdx) as? ImageView
                        if (child != null) {
                            val active = childIdx == currentEmojiCategoryIndex
                            child.background = createKeyDrawable(
                                if (active) Color.parseColor(themeAccentColor) else Color.TRANSPARENT,
                                dpToPx(4)
                            )
                        }
                    }
                    loadEmojiGrid(i)
                }
            }
            tabContainer.addView(tabView)
        }

        loadEmojiGrid(currentEmojiCategoryIndex)
        return rootLayout
    }

    private fun createClipboardLayout(): View {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val clipToolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            setBackgroundColor(Color.parseColor(themeToolbarBg))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(35)
            )
        }

        val clearAllBtn = Button(this).apply {
            text = "Clear All"
            setTextColor(Color.parseColor("#FF1744"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            background = createKeyDrawable(Color.parseColor("#2C1E21"), dpToPx(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(0, 0, dpToPx(8), 0)
            }
            setOnClickListener {
                vibrateClick()
                clearClipboard(false)
            }
        }
        clipToolbar.addView(clearAllBtn)

        val clearUnpinnedBtn = Button(this).apply {
            text = "Clear Unpinned"
            setTextColor(Color.parseColor("#FF8F00"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            background = createKeyDrawable(Color.parseColor("#2C261E"), dpToPx(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setOnClickListener {
                vibrateClick()
                clearClipboard(true)
            }
        }
        clipToolbar.addView(clearUnpinnedBtn)

        val backspaceBtn = FrameLayout(this).apply {
            background = createKeyDrawable(Color.parseColor("#2E2E2E"), dpToPx(4))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(dpToPx(40), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(dpToPx(8), 0, 0, 0)
            }
            setOnClickListener {
                vibrateClick()
                playClick(android.view.KeyEvent.KEYCODE_DEL)
                currentInputConnection?.let { ic ->
                    ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DEL))
                    ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DEL))
                }
            }
        }
        val backspaceIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_backspace)
            setColorFilter(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(dpToPx(16), dpToPx(16)).apply {
                gravity = Gravity.CENTER
            }
        }
        backspaceBtn.addView(backspaceIcon)
        clipToolbar.addView(backspaceBtn)

        rootLayout.addView(clipToolbar)

        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
            addView(listLayout)
        }

        if (clipboardHistory.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "Clipboard is empty"
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpToPx(100)
                )
            }
            listLayout.addView(emptyText)
        } else {
            for (item in clipboardHistory) {
                val container = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                if (clipboardTimelineEnabled) {
                    val timelineIndicator = FrameLayout(this).apply {
                        layoutParams = LinearLayout.LayoutParams(dpToPx(32), ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                    val line = View(this).apply {
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor(if (item.isPinned) themeAccentColor else if (themeTextColor == Color.BLACK) "#D2D5DB" else "#33FFFFFF"))
                        }
                        layoutParams = FrameLayout.LayoutParams(dpToPx(2), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                            gravity = Gravity.CENTER_HORIZONTAL
                        }
                    }
                    val dot = View(this).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.parseColor(if (item.isPinned) themeAccentColor else if (themeTextColor == Color.BLACK) "#8E8E93" else "#88FFFFFF"))
                        }
                        layoutParams = FrameLayout.LayoutParams(dpToPx(10), dpToPx(10)).apply {
                            gravity = Gravity.CENTER
                        }
                    }
                    timelineIndicator.addView(line)
                    timelineIndicator.addView(dot)
                    container.addView(timelineIndicator)
                }

                val itemRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.parseColor(
                            if (item.isPinned) {
                                if (themeTextColor == Color.BLACK) "#F2E6FF" else "#2E1A47"
                            } else {
                                if (themeTextColor == Color.BLACK) "#FFFFFF" else "#1A1A1A"
                            }
                        ))
                        setStroke(2, Color.parseColor(
                            if (item.isPinned) {
                                themeAccentColor
                            } else {
                                if (themeTextColor == Color.BLACK) "#E5E5EA" else "#252525"
                            }
                        ))
                        cornerRadius = dpToPx(6).toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        if (clipboardTimelineEnabled) 0 else ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        if (clipboardTimelineEnabled) 1.0f else 0.0f
                    ).apply {
                        setMargins(dpToPx(6), dpToPx(3), dpToPx(6), dpToPx(3))
                    }
                }

                val isImage = item.imageUri != null
                
                // Vertical container for content & relative time meta
                val textAndMetaLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1.0f
                    ).apply {
                        setMargins(0, 0, dpToPx(12), 0)
                    }
                }

                val bodyLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                if (isImage) {
                    val imageView = ImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(dpToPx(60), dpToPx(40)).apply {
                            setMargins(0, 0, dpToPx(8), 0)
                        }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        try {
                            setImageURI(android.net.Uri.parse(item.imageUri))
                        } catch (e: Exception) {
                            setImageResource(R.drawable.ic_clipboard)
                        }
                        setOnClickListener {
                            vibrateClick()
                            pasteClipboardImage(item)
                        }
                    }
                    bodyLayout.addView(imageView)
                }

                val textToCheck = item.text
                val isUrl = textToCheck.startsWith("http://") || textToCheck.startsWith("https://") || android.util.Patterns.WEB_URL.matcher(textToCheck).matches()

                val clipTextView = TextView(this).apply {
                    text = if (isImage) "Copied Image" else item.text
                    setTextColor(if (isUrl) Color.parseColor("#3498db") else themeTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1.0f
                    )
                    setOnClickListener {
                        vibrateClick()
                        if (isImage) {
                            pasteClipboardImage(item)
                        } else {
                            currentInputConnection?.commitText(item.text, 1)
                        }
                    }
                }
                bodyLayout.addView(clipTextView)
                textAndMetaLayout.addView(bodyLayout)

                val timeStr = android.text.format.DateUtils.getRelativeTimeSpanString(
                    item.timestamp,
                    System.currentTimeMillis(),
                    android.text.format.DateUtils.MINUTE_IN_MILLIS
                ).toString()

                val timeTextView = TextView(this).apply {
                    text = timeStr
                    setTextColor(Color.GRAY)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, dpToPx(2), 0, 0)
                    }
                }
                textAndMetaLayout.addView(timeTextView)

                itemRow.addView(textAndMetaLayout)

                if (isUrl) {
                    val browserIcon = ImageView(this).apply {
                        setImageResource(R.drawable.ic_browser)
                        setColorFilter(Color.parseColor("#3498db"))
                        background = createKeyDrawable(Color.TRANSPARENT, dpToPx(4))
                        isClickable = true
                        isFocusable = true
                        layoutParams = LinearLayout.LayoutParams(dpToPx(35), dpToPx(35)).apply {
                            setMargins(0, 0, dpToPx(6), 0)
                        }
                        setOnClickListener {
                            vibrateClick()
                            try {
                                val urlStr = if (!textToCheck.startsWith("http://") && !textToCheck.startsWith("https://")) {
                                    "https://" + textToCheck
                                } else {
                                    textToCheck
                                }
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(urlStr)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(this@GlideTypeKeyboardService, "Unable to open link", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    itemRow.addView(browserIcon)
                }

                val pinBtn = ImageView(this).apply {
                    setImageResource(if (item.isPinned) R.drawable.ic_pin else R.drawable.ic_unpin)
                    setColorFilter(if (item.isPinned) Color.parseColor(themeAccentColor) else themeTextColor)
                    background = createKeyDrawable(Color.TRANSPARENT, dpToPx(4))
                    isClickable = true
                    isFocusable = true
                    layoutParams = LinearLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
                        setMargins(dpToPx(1), 0, dpToPx(7), 0)
                    }
                    setOnClickListener {
                        vibrateClick()
                        handler.postDelayed({ togglePinItem(item) }, 100)
                    }
                }
                itemRow.addView(pinBtn)

                val delBtn = ImageView(this).apply {
                    setImageResource(R.drawable.ic_delete)
                    setColorFilter(Color.parseColor("#FF1744"))
                    background = createKeyDrawable(Color.TRANSPARENT, dpToPx(4))
                    isClickable = true
                    isFocusable = true
                    layoutParams = LinearLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
                        setMargins(0, 0, 0, 0)
                    }
                    setOnClickListener {
                        vibrateClick()
                        deleteClipboardItem(item)
                    }
                }
                itemRow.addView(delBtn)

                container.addView(itemRow)
                listLayout.addView(container)
            }
        }

        rootLayout.addView(scrollView)
        return rootLayout
    }

    private fun playClick(keyCode: Int) {
        if (!soundEnabled) return
        val soundConstant = when (keyCode) {
            android.view.KeyEvent.KEYCODE_ENTER -> android.view.SoundEffectConstants.NAVIGATION_DOWN
            android.view.KeyEvent.KEYCODE_DEL -> android.view.SoundEffectConstants.CLICK
            android.view.KeyEvent.KEYCODE_SPACE -> android.view.SoundEffectConstants.CLICK
            else -> android.view.SoundEffectConstants.CLICK
        }
        if (::keyboardContainer.isInitialized) {
            keyboardContainer.playSoundEffect(soundConstant)
        }
    }

    private fun vibrateClick() {
        if (!vibrationEnabled) return
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(25)
        }
    }

    private fun createKeyDrawable(color: Int, radius: Int): RippleDrawable {
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius.toFloat()
            setColor(color)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius.toFloat()
            setColor(Color.WHITE)
        }
        return RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#44FFFFFF")),
            content,
            mask
        )
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun createTranslationToolbar(): View {
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor(themeToolbarBg))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(40)
            )
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(6), 0, dpToPx(6), 0)
        }

        val closeBtn = FrameLayout(this).apply {
            background = createKeyDrawable(Color.TRANSPARENT, dpToPx(4))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(dpToPx(30), dpToPx(30))
            setOnClickListener {
                vibrateClick()
                translationInputField = null
                isTranslationActive = false
                currentInputConnection?.finishComposingText()
                updateKeyboardLayout()
            }
        }
        val closeIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(dpToPx(16), dpToPx(16)).apply {
                gravity = Gravity.CENTER
            }
        }
        closeBtn.addView(closeIcon)
        toolbar.addView(closeBtn)

        val sourceBtn = TextView(this).apply {
            text = translationSourceLang.uppercase()
            setTextColor(Color.parseColor(themeAccentColor))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            background = createKeyDrawable(Color.parseColor("#252525"), dpToPx(4))
            layoutParams = LinearLayout.LayoutParams(dpToPx(45), dpToPx(30)).apply {
                setMargins(dpToPx(4), 0, 0, 0)
            }
            setOnClickListener {
                vibrateClick()
                showLanguageMenu(this, true)
            }
        }
        toolbar.addView(sourceBtn)

        val arrowText = TextView(this).apply {
            text = "➔"
            setTextColor(Color.GRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dpToPx(20), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        toolbar.addView(arrowText)

        val targetBtn = TextView(this).apply {
            text = translationTargetLang.uppercase()
            setTextColor(Color.parseColor(themeAccentColor))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            background = createKeyDrawable(Color.parseColor("#252525"), dpToPx(4))
            layoutParams = LinearLayout.LayoutParams(dpToPx(45), dpToPx(30))
            setOnClickListener {
                vibrateClick()
                showLanguageMenu(this, false)
            }
        }
        toolbar.addView(targetBtn)

        if (isListening) {
            val waveView = VoiceWaveView(this, themeAccentColor).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    dpToPx(30),
                    1f
                ).apply {
                    gravity = Gravity.CENTER
                    setMargins(dpToPx(8), 0, dpToPx(8), 0)
                }
            }
            toolbar.addView(waveView)
        } else {
            val inputField = EditText(this).apply {
                translationInputField = this
                showSoftInputOnFocus = false
                hint = "Type here..."
                setHintTextColor(Color.GRAY)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                background = null
                maxLines = 1
                isSingleLine = true
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1.0f
                ).apply {
                    setMargins(dpToPx(8), 0, dpToPx(8), 0)
                }
                post {
                    requestFocus()
                }
                setOnTouchListener { v, event ->
                    v.requestFocus()
                    false
                }

                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        translationRunnable?.let { translationDebounceHandler.removeCallbacks(it) }
                        val query = s?.toString() ?: ""
                        if (query.isEmpty()) {
                            currentInputConnection?.setComposingText("", 1)
                            return
                        }
                        translationRunnable = Runnable {
                            translateText(query, translationSourceLang, translationTargetLang) { result ->
                                if (result != null) {
                                    currentInputConnection?.setComposingText(result, 1)
                                }
                            }
                        }
                        translationDebounceHandler.postDelayed(translationRunnable!!, 400)
                    }
                    override fun afterTextChanged(s: android.text.Editable?) {}
                })
            }
            toolbar.addView(inputField)
        }

        val voiceTranslateBtn = FrameLayout(this).apply {
            background = createKeyDrawable(Color.TRANSPARENT, dpToPx(4))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(dpToPx(35), dpToPx(30)).apply {
                setMargins(0, 0, dpToPx(4), 0)
            }
            setOnClickListener {
                vibrateClick()
                if (isListening) {
                    stopVoiceInput()
                } else {
                    startVoiceInput()
                }
            }
        }
        val voiceTranslateIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_mic)
            setColorFilter(if (isListening) Color.parseColor(themeAccentColor) else Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(dpToPx(16), dpToPx(16)).apply {
                gravity = Gravity.CENTER
            }
        }
        voiceTranslateBtn.addView(voiceTranslateIcon)
        toolbar.addView(voiceTranslateBtn)

        val sendBtn = TextView(this).apply {
            text = "✓"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            background = createKeyDrawable(Color.parseColor(themeAccentColor), dpToPx(4))
            layoutParams = LinearLayout.LayoutParams(dpToPx(30), dpToPx(30)).apply {
                setMargins(0, 0, dpToPx(4), 0)
            }
            setOnClickListener {
                vibrateClick()
                currentInputConnection?.finishComposingText()
                translationInputField = null
                isTranslationActive = false
                updateKeyboardLayout()
            }
        }
        toolbar.addView(sendBtn)

        return toolbar
    }



    private fun showAiAssistantMenu(anchorView: View) {
        val popup = PopupMenu(this, anchorView)
        val options = listOf(
            "Write Email" to "Write a professional email based on context",
            "Write Message" to "Write a friendly message",
            "Rewrite" to "Rewrite the selected text",
            "Summarize" to "Summarize the text",
            "Make Professional" to "Make text more professional",
            "Make Casual" to "Make text more casual",
            "Custom Prompt" to "Write your own custom prompt"
        )
        for ((index, pair) in options.withIndex()) {
            popup.menu.add(0, index, 0, pair.first)
        }
        popup.setOnMenuItemClickListener { item ->
            val action = options[item.itemId].first
            if (action == "Custom Prompt") {
                showAiPromptDialog()
                return@setOnMenuItemClickListener true
            }
            val ic = currentInputConnection ?: return@setOnMenuItemClickListener true
            val textBefore = ic.getTextBeforeCursor(5000, 0) ?: ""
            val textAfter = ic.getTextAfterCursor(5000, 0) ?: ""
            val selectedText = ic.getSelectedText(0)
            val text = if (!selectedText.isNullOrEmpty()) selectedText.toString()
                else textBefore.toString() + textAfter.toString()
            if (text.isNotEmpty()) {
                Toast.makeText(this, "AI processing...", Toast.LENGTH_SHORT).show()
                aiAssist(action, text) { result ->
                    if (result != null) {
                        if (!selectedText.isNullOrEmpty()) {
                            ic.commitText(result, 1)
                        } else {
                            ic.deleteSurroundingText(textBefore.length, textAfter.length)
                            ic.commitText(result, 1)
                        }
                        Toast.makeText(this, "Done!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "AI request failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "No text found. Type something first", Toast.LENGTH_SHORT).show()
            }
            true
        }
        popup.show()
    }

    private fun showAiPromptDialog() {
        val inputEditText = EditText(this).apply {
            hint = "Enter your custom prompt..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            maxLines = 6
            setBackgroundColor(Color.parseColor("#333333"))
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }

        val ic = currentInputConnection
        val textBefore = ic?.getTextBeforeCursor(5000, 0) ?: ""
        val textAfter = ic?.getTextAfterCursor(5000, 0) ?: ""
        val selectedText = ic?.getSelectedText(0)
        val contextText = if (!selectedText.isNullOrEmpty()) selectedText.toString()
            else textBefore.toString() + textAfter.toString()

        if (contextText.isEmpty()) {
            inputEditText.hint = "Enter your prompt (no text context available)"
        } else {
            inputEditText.hint = "Prompt will use: \"$contextText\""
        }

        try {
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Custom AI Prompt")
                .setView(inputEditText)
                .setPositiveButton("Send") { _: android.content.DialogInterface, _: Int ->
                    val prompt = inputEditText.text.toString()
                    if (prompt.isNotEmpty()) {
                        Toast.makeText(this, "AI processing...", Toast.LENGTH_SHORT).show()
                        val fullText = if (contextText.isNotEmpty()) "$prompt\n\nContext: $contextText" else prompt
                        aiAssist("Custom", fullText) { result ->
                            if (result != null) {
                                if (!selectedText.isNullOrEmpty()) {
                                    ic?.commitText(result, 1)
                                } else {
                                    ic?.deleteSurroundingText(textBefore.length, textAfter.length)
                                    ic?.commitText(result, 1)
                                }
                                Toast.makeText(this, "Done!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "AI request failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Please enter a prompt", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Dialog error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun aiAssist(action: String, text: String, callback: (String?) -> Unit) {
        Thread {
            var conn: HttpURLConnection? = null
            try {
                val prompt = when (action) {
                    "Write Email" -> "Write a professional email. Context: $text"
                    "Write Message" -> "Write a friendly message. Context: $text"
                    "Rewrite" -> "Rewrite the following text to be clearer and more engaging: $text"
                    "Summarize" -> "Summarize the following text concisely: $text"
                    "Make Professional" -> "Rewrite the following text in a professional tone: $text"
                    "Make Casual" -> "Rewrite the following text in a casual, friendly tone: $text"
                    else -> text
                }
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=AIzaSyATBXajvzQLTDHEQbcpq0Ihe0vWDHmO520")
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.doInput = true

                val body = org.json.JSONObject().apply {
                    put("contents", org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply {
                            put("parts", org.json.JSONArray().apply {
                                put(org.json.JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                }
                conn.outputStream.use { os ->
                    os.write(body.toString().toByteArray())
                    os.flush()
                }

                val responseCode = conn.responseCode
                val response = if (responseCode == 200) {
                    conn.inputStream.use { inputStream ->
                        java.io.BufferedReader(java.io.InputStreamReader(inputStream, "UTF-8")).use { reader ->
                            reader.readText()
                        }
                    }
                } else {
                    val errorStream = conn.errorStream?.let { errorStream ->
                        java.io.BufferedReader(java.io.InputStreamReader(errorStream, "UTF-8")).use { reader ->
                            reader.readText()
                        }
                    } ?: "Unknown error"
                    android.util.Log.e("AI_Assist", "API error $responseCode: $errorStream")
                    handler.post { callback(null) }
                    return@Thread
                }

                try {
                    val json = org.json.JSONObject(response)
                    val candidates = json.getJSONArray("candidates")
                    if (candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).getJSONObject("content")
                        val parts = content.getJSONArray("parts")
                        if (parts.length() > 0) {
                            val result = parts.getJSONObject(0).getString("text")
                            handler.post { callback(result) }
                        } else {
                            handler.post { callback(null) }
                        }
                    } else {
                        // Try alternative response format (Gemini sometimes returns via promptFeedback)
                        val resultText = json.optJSONObject("promptFeedback")?.optString("blockReason")
                        handler.post { callback("Blocked: ${resultText ?: "No result"}") }
                    }
                } catch (e: Exception) {
                    handler.post { callback("") }
                }
            } catch (e: Exception) {
                android.util.Log.e("AI_Assist", "Error: ${e.message}", e)
                handler.post { callback(null) }
            } finally {
                conn?.disconnect()
            }
        }.start()
    }

    private fun showLanguageMenu(anchorView: View, isSource: Boolean) {
        val popup = PopupMenu(this, anchorView)
        for (i in languages.indices) {
            popup.menu.add(0, i, 0, languages[i].first)
        }
        popup.setOnMenuItemClickListener { item ->
            val selectedLang = languages[item.itemId].second
            if (isSource) {
                translationSourceLang = selectedLang
            } else {
                translationTargetLang = selectedLang
            }
            updateKeyboardLayout()
            true
        }
        popup.show()
    }

    private fun translateText(text: String, sourceLang: String, targetLang: String, callback: (String?) -> Unit) {
        Thread {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("https://translate-pa.googleapis.com/v1/translateHtml")
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("accept", "*/*")
                conn.setRequestProperty("content-type", "application/json+protobuf")
                conn.setRequestProperty("x-goog-api-key", "AIzaSyATBXajvzQLTDHEQbcpq0Ihe0vWDHmO520")
                conn.doOutput = true
                conn.doInput = true

                val payload = JSONArray().apply {
                    put(JSONArray().apply {
                        put(JSONArray().apply {
                            put(text)
                        })
                        put(sourceLang)
                        put(targetLang)
                    })
                    put("te_lib")
                }

                conn.outputStream.use { os ->
                    OutputStreamWriter(os, "UTF-8").use { writer ->
                        writer.write(payload.toString())
                        writer.flush()
                    }
                }

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                            reader.readText()
                        }
                    }
                    val responseArray = JSONArray(response)
                    val first = responseArray.getJSONArray(0)
                    val translatedText = first.getString(0)
                    handler.post { callback(translatedText) }
                } else {
                    handler.post { callback(null) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                handler.post { callback(null) }
            } finally {
                conn?.disconnect()
            }
        }.start()
    }

    private fun playVoiceBeep(start: Boolean) {
        try {
            val toneGenerator = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100)
            if (start) {
                toneGenerator.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 150)
            } else {
                toneGenerator.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 150)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startVoiceInput() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Please grant microphone permission in the app", Toast.LENGTH_LONG).show()
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("request_mic", true)
                }
                startActivity(intent)
                return
            }
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) {
                        Toast.makeText(this@GlideTypeKeyboardService, "Listening...", Toast.LENGTH_SHORT).show()
                        playVoiceBeep(true)
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isListening = false
                        playVoiceBeep(false)
                        updateKeyboardLayout()
                    }
                    override fun onError(error: Int) {
                        isListening = false
                        playVoiceBeep(false)
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission denied"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                            else -> "Voice recognition error"
                        }
                        Toast.makeText(this@GlideTypeKeyboardService, message, Toast.LENGTH_SHORT).show()
                        updateKeyboardLayout()
                    }
                    override fun onResults(results: android.os.Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0]
                            if (isTranslationActive && translationInputField != null) {
                                val et = translationInputField!!
                                val start = et.selectionStart
                                val end = et.selectionEnd
                                et.text.replace(Math.min(start, end), Math.max(start, end), text)
                            } else {
                                currentInputConnection?.commitText(text + " ", 1)
                            }
                        }
                        isListening = false
                        playVoiceBeep(false)
                        updateKeyboardLayout()
                    }
                    override fun onPartialResults(partialResults: android.os.Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0]
                            if (isTranslationActive && translationInputField != null) {
                                val et = translationInputField!!
                                val start = et.selectionStart
                                val end = et.selectionEnd
                                et.text.replace(Math.min(start, end), Math.max(start, end), text)
                            } else {
                                currentInputConnection?.setComposingText(text, 1)
                            }
                        }
                    }
                    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                })
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (isTranslationActive) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, translationSourceLang)
            }
        }
        speechRecognizer?.startListening(intent)
        isListening = true
        updateKeyboardLayout()
    }

    private fun stopVoiceInput() {
        speechRecognizer?.stopListening()
        isListening = false
        playVoiceBeep(false)
        updateKeyboardLayout()
    }

    private fun showKeyPreview(keyView: View, textToDisplay: String) {
        handler.removeCallbacks(previewRemoveRunnable)
        activePreviewView?.let {
            if (::keyboardContainer.isInitialized) {
                keyboardContainer.removeView(it)
            }
            activePreviewView = null
        }

        if (textToDisplay.length > 3 || textToDisplay.lowercase() == "spacebar") return

        val parentCoords = IntArray(2)
        keyboardContainer.getLocationInWindow(parentCoords)
        val keyCoords = IntArray(2)
        keyView.getLocationInWindow(keyCoords)
        val relativeX = keyCoords[0] - parentCoords[0]
        val relativeY = keyCoords[1] - parentCoords[1]

        val previewWidth = keyView.width
        val previewHeight = (keyView.height * 1.4).toInt()

        val previewTextView = TextView(this).apply {
            text = textToDisplay
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(themeAccentColor))
                cornerRadius = dpToPx(8).toFloat()
            }
        }

        val params = FrameLayout.LayoutParams(previewWidth, previewHeight).apply {
            leftMargin = relativeX
            topMargin = relativeY - previewHeight + dpToPx(10)
        }

        activePreviewView = previewTextView
        keyboardContainer.addView(previewTextView, params)
    }

    private fun hideKeyPreview() {
        handler.removeCallbacks(previewRemoveRunnable)
        handler.postDelayed(previewRemoveRunnable, 120)
    }

    private fun findKeyViewAt(container: ViewGroup, x: Float, y: Float): View? {
        val location = IntArray(2)
        container.getLocationOnScreen(location)
        val containerX = location[0]
        val containerY = location[1]

        for (i in 0 until container.childCount) {
            val row = container.getChildAt(i) as? ViewGroup ?: continue
            val rowLoc = IntArray(2)
            row.getLocationOnScreen(rowLoc)
            
            val rowTop = rowLoc[1] - containerY
            val rowBottom = rowTop + row.height
            if (y >= rowTop && y <= rowBottom) {
                for (j in 0 until row.childCount) {
                    val keyView = row.getChildAt(j) as? ViewGroup ?: continue
                    val keyLoc = IntArray(2)
                    keyView.getLocationOnScreen(keyLoc)
                    
                    val keyLeft = keyLoc[0] - containerX
                    val keyRight = keyLeft + keyView.width
                    if (x >= keyLeft && x <= keyRight) {
                        return keyView.getChildAt(0)
                    }
                }
            }
        }
        return null
    }

    private fun createPcShortcutsLayout(): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(4), 0, dpToPx(4))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val row1 = listOf("Esc", "Tab", "Undo", "Redo", "Select All")
        val row2 = listOf("Ctrl", "Alt", "Cut", "Copy", "Paste", "Find", "Home")
        val row3 = listOf("PgUp", "Up", "PgDn", "End", "Del")
        val row4 = listOf("Left", "Down", "Right", "Enter", "ABC")

        layout.addView(createPcKeyRow(row1))
        layout.addView(createPcKeyRow(row2))
        layout.addView(createPcKeyRow(row3))
        layout.addView(createPcKeyRow(row4))

        return layout
    }

    private fun isPcKeyWorkable(key: String): Boolean {
        if (isTranslationActive) {
            val unworkableKeys = listOf("Esc", "Tab", "Find", "Home", "End", "PgUp", "PgDn", "Up", "Down")
            return !unworkableKeys.contains(key)
        }
        return true
    }

    private fun createPcKeyRow(keys: List<String>): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
        }

        for (key in keys) {
            val workable = isPcKeyWorkable(key)
            val keyView = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1.0f
                ).apply {
                    setMargins(dpToPx(keySpacingDp), dpToPx(3), dpToPx(keySpacingDp), dpToPx(3))
                }

                val keyLayout = RelativeLayout(context).apply {
                    val isActiveModifier = (key == "Ctrl" && isCtrlActive) || (key == "Alt" && isAltActive)
                    val bgColor = if (isActiveModifier) Color.parseColor(themeAccentColor) else Color.parseColor(themeSpecialKeyBg)
                    background = createKeyDrawable(bgColor, dpToPx(6))
                    isClickable = workable
                    isFocusable = workable
                    alpha = if (workable) 1.0f else 0.4f
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val mainTextView = TextView(context).apply {
                    text = key
                    setTextColor(themeTextColor)
                    val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    val textSize = if (isLandscape) 15f else 14f
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
                    gravity = Gravity.CENTER
                    isClickable = false
                    isFocusable = false
                    layoutParams = RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        addRule(RelativeLayout.CENTER_IN_PARENT)
                    }
                }
                keyLayout.addView(mainTextView)

                keyLayout.setOnClickListener {
                    vibrateClick()
                    if (keyboardEffect != "none" && keyboardEffectsView != null) {
                        val location = IntArray(2)
                        it.getLocationOnScreen(location)
                        val keyboardAreaLoc = IntArray(2)
                        activeKeyboardArea?.getLocationOnScreen(keyboardAreaLoc)
                        val relativeX = location[0] - keyboardAreaLoc[0] + it.width / 2f
                        val relativeY = location[1] - keyboardAreaLoc[1] + it.height / 2f
                        keyboardEffectsView?.triggerEffect(relativeX, relativeY, it.width, it.height)
                    }
                    handlePcShortcutPress(key)
                }

                addView(keyLayout)
            }
            row.addView(keyView)
        }

        return row
    }

    private fun handlePcShortcutPress(key: String) {
        if (key == "Ctrl") {
            isCtrlActive = !isCtrlActive
            updateKeyboardLayout()
            return
        }
        if (key == "Alt") {
            isAltActive = !isAltActive
            updateKeyboardLayout()
            return
        }
        if (!isPcKeyWorkable(key)) return
        if (isTranslationActive && translationInputField != null) {
            val et = translationInputField!!
            val start = et.selectionStart
            val end = et.selectionEnd
            when (key) {
                "Undo" -> et.onTextContextMenuItem(android.R.id.undo)
                "Redo" -> et.onTextContextMenuItem(android.R.id.redo)
                "Select All" -> et.selectAll()
                "Cut" -> {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val selectedText = et.text.substring(Math.min(start, end), Math.max(start, end))
                    if (selectedText.isNotEmpty()) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("clipboard", selectedText))
                        et.text.delete(Math.min(start, end), Math.max(start, end))
                    }
                }
                "Copy" -> {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val selectedText = et.text.substring(Math.min(start, end), Math.max(start, end))
                    if (selectedText.isNotEmpty()) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("clipboard", selectedText))
                    }
                }
                "Paste" -> {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = clipboard.primaryClip
                    if (clipData != null && clipData.itemCount > 0) {
                        val text = clipData.getItemAt(0).text ?: ""
                        et.text.replace(Math.min(start, end), Math.max(start, end), text)
                    }
                }
                "Ctrl+Alt+V" -> {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = clipboard.primaryClip
                    if (clipData != null && clipData.itemCount > 0) {
                        val text = clipData.getItemAt(0).text ?: ""
                        et.text.replace(Math.min(start, end), Math.max(start, end), text)
                    }
                }
                "Left" -> {
                    if (start > 0) et.setSelection(start - 1)
                }
                "Right" -> {
                    if (start < et.length()) et.setSelection(start + 1)
                }
                "Del" -> {
                    if (start < et.length()) {
                        if (start != end) {
                            et.text.delete(Math.min(start, end), Math.max(start, end))
                        } else {
                            et.text.delete(start, start + 1)
                        }
                    }
                }
                "Enter" -> {
                    handleKeyPress("enter")
                }
                "ABC" -> {
                    handleKeyPress("abc")
                }
            }
            return
        }

        val ic = currentInputConnection ?: return
        when (key) {
            "Esc" -> {
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ESCAPE))
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ESCAPE))
            }
            "Tab" -> {
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_TAB))
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_TAB))
            }
            "Undo" -> sendCtrlShortcut(android.view.KeyEvent.KEYCODE_Z)
            "Redo" -> sendCtrlShortcut(android.view.KeyEvent.KEYCODE_Y)
            "Select All" -> sendCtrlShortcut(android.view.KeyEvent.KEYCODE_A)
            "Cut" -> sendCtrlShortcut(android.view.KeyEvent.KEYCODE_X)
            "Copy" -> sendCtrlShortcut(android.view.KeyEvent.KEYCODE_C)
            "Paste" -> sendCtrlShortcut(android.view.KeyEvent.KEYCODE_V)
            "Ctrl+Alt+V" -> sendCtrlAltShortcut(android.view.KeyEvent.KEYCODE_V)
            "Find" -> sendCtrlShortcut(android.view.KeyEvent.KEYCODE_F)
            "Home" -> {
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MOVE_HOME))
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MOVE_HOME))
            }
            "End" -> {
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MOVE_END))
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MOVE_END))
            }
            "PgUp" -> {
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_PAGE_UP))
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_PAGE_UP))
            }
            "PgDn" -> {
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_PAGE_DOWN))
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_PAGE_DOWN))
            }
            "Up" -> {
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_UP))
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_UP))
            }
            "Down" -> {
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_DOWN))
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN))
            }
            "Left" -> {
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_LEFT))
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_LEFT))
            }
            "Right" -> {
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_RIGHT))
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_RIGHT))
            }
            "Del" -> {
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_FORWARD_DEL))
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_FORWARD_DEL))
            }
            "Enter" -> {
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER))
            }
            "ABC" -> {
                currentViewMode = ViewMode.QWERTY
                updateKeyboardLayout()
            }
        }
    }

    private fun sendCtrlShortcut(keyCode: Int) {
        val ic = currentInputConnection ?: return
        val time = android.os.SystemClock.uptimeMillis()
        ic.sendKeyEvent(android.view.KeyEvent(time, time, android.view.KeyEvent.ACTION_DOWN, keyCode, 0, android.view.KeyEvent.META_CTRL_MASK or android.view.KeyEvent.META_CTRL_ON))
        ic.sendKeyEvent(android.view.KeyEvent(time, time, android.view.KeyEvent.ACTION_UP, keyCode, 0, android.view.KeyEvent.META_CTRL_MASK or android.view.KeyEvent.META_CTRL_ON))
    }

    private fun sendCtrlAltShortcut(keyCode: Int) {
        val ic = currentInputConnection ?: return
        val time = android.os.SystemClock.uptimeMillis()
        val metaState = android.view.KeyEvent.META_CTRL_MASK or android.view.KeyEvent.META_CTRL_ON or android.view.KeyEvent.META_ALT_MASK or android.view.KeyEvent.META_ALT_ON
        ic.sendKeyEvent(android.view.KeyEvent(time, time, android.view.KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
        ic.sendKeyEvent(android.view.KeyEvent(time, time, android.view.KeyEvent.ACTION_UP, keyCode, 0, metaState))
    }

    private fun triggerKeyEffect(v: View, event: MotionEvent) {
        if (keyboardEffect == "none" || keyboardEffectsView == null) return
        val area = activeKeyboardArea ?: return
        val location = IntArray(2)
        v.getLocationOnScreen(location)
        val keyboardAreaLoc = IntArray(2)
        area.getLocationOnScreen(keyboardAreaLoc)
        val relativeX = location[0] - keyboardAreaLoc[0] + event.x
        val relativeY = location[1] - keyboardAreaLoc[1] + event.y
        keyboardEffectsView?.triggerEffect(relativeX, relativeY, v.width, v.height)
    }

    private fun startPressEffect(v: View, event: MotionEvent) {
        if (keyboardEffect == "none" || keyboardEffectsView == null) return
        val area = activeKeyboardArea ?: return
        val location = IntArray(2)
        v.getLocationOnScreen(location)
        val keyboardAreaLoc = IntArray(2)
        area.getLocationOnScreen(keyboardAreaLoc)
        val relativeX = location[0] - keyboardAreaLoc[0] + event.x
        val relativeY = location[1] - keyboardAreaLoc[1] + event.y
        keyboardEffectsView?.setPressed(relativeX, relativeY, v.width, v.height)
    }

    private fun endPressEffect() {
        keyboardEffectsView?.setReleased()
    }

    private fun toggleOcrMode() {
        if (currentViewMode == ViewMode.OCR) {
            stopOcrCamera()
            currentViewMode = ViewMode.QWERTY
        } else {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Please grant camera permission to use OCR scanning", Toast.LENGTH_LONG).show()
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("request_camera", true)
                }
                startActivity(intent)
            } else {
                currentViewMode = ViewMode.OCR
            }
        }
        updateKeyboardLayout()
    }

    private fun createOcrLayout(): View {
        pendingGalleryImageHandler = { bitmap ->
            resumeOcrModeForGallery(bitmap)
        }

        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val previewView = androidx.camera.view.PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(previewView)
        ocrPreviewView = previewView

        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#DD000000"))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(230)
            ).apply {
                gravity = Gravity.BOTTOM
            }
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(12))
        }

        val scanStatusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val scanIndicator = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(8), dpToPx(8)).apply {
                setMargins(0, 0, dpToPx(6), 0)
            }
            background = createKeyDrawable(Color.parseColor("#00D68F"), dpToPx(4))
        }
        scanStatusRow.addView(scanIndicator)

        val scanTextLabel = TextView(this).apply {
            text = "Live scanning..."
            setTextColor(Color.parseColor("#00D68F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.START
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        scanStatusRow.addView(scanTextLabel)
        overlay.addView(scanStatusRow)

        val qrResultLabel = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#FFD600"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.START
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpToPx(14), dpToPx(2), 0, 0)
            }
        }
        overlay.addView(qrResultLabel)

        val previewImage = ImageView(this).apply {
            ocrPreviewImage = this
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(80)
            ).apply {
                setMargins(0, dpToPx(2), 0, dpToPx(2))
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
        }
        overlay.addView(previewImage)

        overlay.addView(createOcrTextEdit())

        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dpToPx(6), 0, 0)
            }
        }

        val galleryBtn = TextView(this).apply {
            text = "Gallery"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = createKeyDrawable(Color.parseColor("#4F8CFF"), dpToPx(6))
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(34),
                1f
            ).apply {
                setMargins(0, 0, dpToPx(4), 0)
            }
            setOnClickListener {
                vibrateClick()
                openGalleryForOcr()
            }
        }
        buttonsRow.addView(galleryBtn)

        val captureBtn = TextView(this).apply {
            text = "Capture"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = createKeyDrawable(Color.parseColor("#FF6B00"), dpToPx(6))
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(34),
                1f
            ).apply {
                setMargins(0, 0, dpToPx(4), 0)
            }
            setOnClickListener {
                vibrateClick()
                captureAndScan()
            }
        }
        buttonsRow.addView(captureBtn)

        val insertBtn = TextView(this).apply {
            text = "Insert"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = createKeyDrawable(Color.parseColor(themeAccentColor), dpToPx(6))
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(34),
                1f
            ).apply {
                setMargins(0, 0, dpToPx(4), 0)
            }
            setOnClickListener {
                vibrateClick()
                commitOcrText()
            }
        }
        buttonsRow.addView(insertBtn)

        val doneBtn = TextView(this).apply {
            text = "Done"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = createKeyDrawable(Color.parseColor("#33FFFFFF"), dpToPx(6))
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(34),
                1f
            )
            setOnClickListener {
                vibrateClick()
                stopOcrCamera()
                currentViewMode = ViewMode.QWERTY
                updateKeyboardLayout()
            }
        }
        buttonsRow.addView(doneBtn)
        overlay.addView(buttonsRow)

        container.addView(overlay)

        val closeBtn = FrameLayout(this).apply {
            background = createKeyDrawable(Color.parseColor("#80000000"), dpToPx(16))
            isClickable = true
            isFocusable = true
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(32),
                dpToPx(32)
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, dpToPx(10), dpToPx(10), 0)
            }
            setOnClickListener {
                vibrateClick()
                stopOcrCamera()
                currentViewMode = ViewMode.QWERTY
                updateKeyboardLayout()
            }
        }
        val closeIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_collapse)
            setColorFilter(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(dpToPx(16), dpToPx(16)).apply {
                gravity = Gravity.CENTER
            }
        }
        closeBtn.addView(closeIcon)
        container.addView(closeBtn)

        startOcrCamera(previewView, scanTextLabel, qrResultLabel)

        return container
    }

    private var qrDetectedText = ""
    private var ocrStatusLabel: TextView? = null
    private var ocrTextEdit: EditText? = null
    private var ocrPreviewImage: ImageView? = null
    // removed unused scan debounce vars
    private var imageCaptureUseCase: androidx.camera.core.ImageCapture? = null

    private fun createOcrTextEdit(): EditText {
        return EditText(this).apply {
            ocrTextEdit = this
            hint = "Extracted text will appear here..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = createKeyDrawable(Color.parseColor("#33FFFFFF"), dpToPx(4))
            maxLines = 3
            isSingleLine = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(50)
            ).apply {
                setMargins(0, dpToPx(4), 0, dpToPx(4))
            }
        }
    }

    private fun startOcrCamera(previewView: androidx.camera.view.PreviewView, statusLabel: TextView, qrLabel: TextView) {
        ocrStatusLabel = statusLabel
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            statusLabel.text = "Camera permission not granted"
            return
        }

        cameraExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }

                imageCaptureUseCase = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this@GlideTypeKeyboardService,
                    cameraSelector,
                    preview,
                    imageCaptureUseCase
                )
                handler.post {
                    statusLabel.text = "Point camera and tap Capture"
                    ocrCaptureReady = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                handler.post {
                    statusLabel.text = "Error: ${e.message}"
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private var ocrCaptureReady = false

    private fun captureAndScan() {
        val imageCapture = imageCaptureUseCase ?: return
        val photoFile = java.io.File.createTempFile("ocr_", ".jpg", cacheDir)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    ocrStatusLabel?.text = "Scanning image..."
                    processImageFile(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    ocrStatusLabel?.text = "Capture failed: ${exception.message}"
                }
            }
        )
    }

    private fun processImageFile(file: java.io.File) {
        Thread {
            try {
                val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, 480, (480 * bitmap.height / bitmap.width).coerceAtMost(720), true)
                    handler.post {
                        ocrPreviewImage?.setImageBitmap(scaledBitmap)
                        ocrPreviewImage?.visibility = View.VISIBLE
                    }

                    val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    val barcodeScanner = BarcodeScanning.getClient()
                    val image = InputImage.fromBitmap(bitmap, 0)

                    textRecognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            val text = visionText.text.trim()
                            handler.post {
                                if (text.isNotEmpty()) {
                                    ocrTextEdit?.setText(text)
                                    ocrTextEdit?.setSelection(text.length)
                                    ocrStatusLabel?.text = "Text extracted (${text.length} chars)"
                                } else {
                                    ocrStatusLabel?.text = "No text found in image"
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            handler.post { ocrStatusLabel?.text = "OCR failed: ${e.message}" }
                        }

                    barcodeScanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            for (barcode in barcodes) {
                                val rawValue = barcode.rawValue ?: continue
                                handler.post {
                                    ocrTextEdit?.setText(rawValue)
                                    ocrTextEdit?.setSelection(rawValue.length)
                                    ocrStatusLabel?.text = "QR detected: $rawValue"
                                }
                            }
                        }

                    file.delete()
                } else {
                    handler.post { ocrStatusLabel?.text = "Failed to decode image" }
                }
            } catch (e: Exception) {
                handler.post { ocrStatusLabel?.text = "Error: ${e.message}" }
            }
        }.start()
    }

    private fun openGalleryForOcr() {
        try {
            val intent = Intent(this, OcrGalleryActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            ocrStatusLabel?.text = "Gallery: ${e.message}"
        }
    }

    private fun resumeOcrModeForGallery(bitmap: Bitmap) {
        if (currentViewMode != ViewMode.OCR) {
            currentViewMode = ViewMode.OCR
            updateKeyboardLayout()
            handler.postDelayed({ processImageForOcr(bitmap) }, 100)
        } else {
            processImageForOcr(bitmap)
        }
    }

    private fun processImageForOcr(bitmap: android.graphics.Bitmap) {
        Thread {
            try {
                // Scale down the bitmap for faster processing
                val maxDim = 1024f
                val scale = minOf(maxDim / maxOf(bitmap.width, bitmap.height).toFloat(), 1f)
                val scaledBitmap = if (scale < 1f) {
                    android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                } else bitmap

                val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val barcodeScanner = BarcodeScanning.getClient()
                val image = InputImage.fromBitmap(scaledBitmap, 0)

                textRecognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val text = visionText.text.trim()
                        handler.post {
                            if (text.isNotEmpty()) {
                                if (ocrTextEdit != null) {
                                    ocrTextEdit?.setText(text)
                                    ocrTextEdit?.setSelection(text.length)
                                    ocrStatusLabel?.text = "Text extracted (${text.length} chars)"
                                    ocrStatusLabel?.setTextColor(Color.parseColor("#00D68F"))
                                } else {
                                    ocrStatusLabel?.text = "Text: ${text.take(50)}..."
                                }
                                Toast.makeText(this@GlideTypeKeyboardService, "Text extracted! (${text.length} chars)", Toast.LENGTH_SHORT).show()
                            } else {
                                ocrStatusLabel?.text = "No text found in image"
                                Toast.makeText(this@GlideTypeKeyboardService, "No text found in image", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        handler.post {
                            ocrStatusLabel?.text = "OCR failed: ${e.message}"
                            Toast.makeText(this@GlideTypeKeyboardService, "OCR failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }

                barcodeScanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            val rawValue = barcode.rawValue ?: continue
                            if (rawValue.isNotEmpty()) {
                                handler.post {
                                    if (ocrTextEdit != null) {
                                        ocrTextEdit?.setText(rawValue)
                                        ocrTextEdit?.setSelection(rawValue.length)
                                        ocrStatusLabel?.text = "QR: $rawValue"
                                    } else {
                                        ocrStatusLabel?.text = "QR: $rawValue"
                                    }
                                    Toast.makeText(this@GlideTypeKeyboardService, "QR: ${rawValue.take(30)}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                handler.post {
                    ocrStatusLabel?.text = "Error: ${e.message}"
                    Toast.makeText(this@GlideTypeKeyboardService, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    companion object {
        private var pendingGalleryImageHandler: ((Bitmap) -> Unit)? = null

        fun handleGalleryImage(bitmap: Bitmap) {
            val handler = pendingGalleryImageHandler
            if (handler != null) {
                handler(bitmap)
            }
        }
    }

    private fun stopOcrCamera() {
        try {
            cameraProvider?.unbindAll()
            cameraProvider = null
            cameraExecutor?.shutdown()
            cameraExecutor = null
            ocrPreviewView = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun commitOcrText() {
        val text = ocrTextEdit?.text?.toString() ?: return
        if (text.isNotEmpty()) {
            val targetField = if (isTranslationActive && translationInputField != null) translationInputField
                else null
            if (targetField != null) {
                val et = targetField!!
                val start = et.selectionStart
                val end = et.selectionEnd
                et.text.replace(Math.min(start, end), Math.max(start, end), text)
            } else {
                currentInputConnection?.commitText(text, 1)
            }
            ocrTextEdit?.setText("")
        } else {
            Toast.makeText(this, "No text to insert", Toast.LENGTH_SHORT).show()
        }
    }
}

class VoiceWaveView(context: Context, val accentColor: String) : View(context) {
    private val paint = Paint().apply {
        color = Color.parseColor(accentColor)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val handlerRef = Handler(Looper.getMainLooper())
    private val barHeights = floatArrayOf(0.2f, 0.5f, 0.8f, 0.4f, 0.6f)
    private val runnable = object : Runnable {
        override fun run() {
            for (i in barHeights.indices) {
                barHeights[i] = (0.2f + Math.random() * 0.8f).toFloat()
            }
            invalidate()
            handlerRef.postDelayed(this, 100)
        }
    }
    init {
        handlerRef.post(runnable)
    }
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handlerRef.removeCallbacks(runnable)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val barCount = 5
        val spacing = w / (barCount + 1)
        val barWidth = spacing * 0.4f
        for (i in 0 until barCount) {
            val cx = spacing * (i + 1)
            val barHeight = h * barHeights[i]
            val top = (h - barHeight) / 2
            val bottom = top + barHeight
            val rect = android.graphics.RectF(cx - barWidth/2, top, cx + barWidth/2, bottom)
            canvas.drawRoundRect(rect, barWidth/2, barWidth/2, paint)
        }
    }
}
