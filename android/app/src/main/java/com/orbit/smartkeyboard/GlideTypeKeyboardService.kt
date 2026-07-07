package com.orbit.smartkeyboard

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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.io.BufferedReader
import java.io.InputStreamReader

class GlideTypeKeyboardService : InputMethodService() {

    private lateinit var keyboardContainer: FrameLayout
    private var currentViewMode = ViewMode.QWERTY
    private var isShifted = false
    private var isCapsLock = false
    private var lastShiftPressTime: Long = 0
    private var translationInputField: EditText? = null
    private var longPressDelayMs = 400
    private var keyboardHeightDp = 270 // Default keyboard height

    // Settings fields (synced from SharedPrefs)
    private var vibrationEnabled = true
    private var soundEnabled = false
    private var numberRowEnabled = true
    private var gestureEnabled = true
    private var themeName = "purple"
    private var translationFeatureEnabled = true
    private var voiceDictationEnabled = true

    // Theme Colors
    private var themeBgColor = "#121212"
    private var themeAccentColor = "#8A2BE2"
    private var themeSpecialKeyBg = "#2E2E2E"
    private var themeRegularKeyBg = "#1F1F1F"
    private var themeToolbarBg = "#1A1A1A"

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
        QWERTY, SYMBOLS, EMOJIS, CLIPBOARD, PC_SHORTCUTS
    }

    data class ClipboardItem(
        val text: String,
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

    // All emoji categories sorted nicely
    private val emojiCategories = listOf(
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
        loadPreferences()
        setupClipboardListener()
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        super.onDestroy()
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
        currentViewMode = ViewMode.QWERTY
        isTranslationActive = false
        // Refresh preferences and clipboard history when IME is shown
        loadPreferences()
        fetchSystemClipboard()
        updateKeyboardLayout()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        keyboardHeightDp = prefs.getInt(PREF_KEY_HEIGHT, 270)
        
        vibrationEnabled = prefs.getBoolean("vibration_enabled", true)
        soundEnabled = prefs.getBoolean("sound_enabled", false)
        numberRowEnabled = prefs.getBoolean("number_row_enabled", true)
        gestureEnabled = prefs.getBoolean("gesture_enabled", true)
        themeName = prefs.getString("theme", "red") ?: "red"
        translationFeatureEnabled = prefs.getBoolean("addon_translate", true)
        voiceDictationEnabled = prefs.getBoolean("addon_voice_text", true)
        longPressDelayMs = prefs.getInt("long_press_delay", 400)

        // Set theme colors based on loaded settings
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
                            isPinned = obj.optBoolean("isPinned", false),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putInt(PREF_KEY_HEIGHT, keyboardHeightDp)

        val array = JSONArray()
        for (item in clipboardHistory) {
            val obj = JSONObject().apply {
                put("text", item.text)
                put("isPinned", item.isPinned)
                put("timestamp", item.timestamp)
            }
            array.put(obj)
        }
        editor.putString(PREF_KEY_CLIPBOARD, array.toString())
        editor.apply()
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

    private fun fetchSystemClipboard() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0).text?.toString()?.trim()
                    if (!text.isNullOrEmpty()) {
                        addClipboardItem(text)
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
        savePreferences()
        if (currentViewMode == ViewMode.CLIPBOARD) {
            updateKeyboardLayout()
        }
    }

    private fun clearClipboard(exceptPinned: Boolean) {
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

        val params = keyboardContainer.layoutParams
        params.height = dpToPx(keyboardHeightDp)
        keyboardContainer.layoutParams = params
        keyboardContainer.setBackgroundColor(Color.parseColor(themeBgColor))

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val dragHandle = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(10)
            )
            setBackgroundColor(Color.parseColor(themeBgColor))
            val line = View(this@GlideTypeKeyboardService).apply {
                background = createKeyDrawable(Color.parseColor("#444444"), dpToPx(2))
                layoutParams = FrameLayout.LayoutParams(dpToPx(40), dpToPx(3)).apply {
                    gravity = Gravity.CENTER
                }
            }
            addView(line)
        }
        var dragStartY = 0f
        var dragStartHeight = 0
        dragHandle.setOnTouchListener { _, event ->
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
                    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putInt(PREF_KEY_HEIGHT, keyboardHeightDp).apply()
                    vibrateClick()
                    updateKeyboardLayout()
                    true
                }
                else -> false
            }
        }
        mainLayout.addView(dragHandle)

        if (isTranslationActive) {
            mainLayout.addView(createTranslationToolbar())
        } else {
            mainLayout.addView(createToolbar())
        }

        val keyboardArea = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
        }

        val keysLayout = when (currentViewMode) {
            ViewMode.QWERTY -> createQwertyLayout()
            ViewMode.SYMBOLS -> createSymbolsLayout()
            ViewMode.EMOJIS -> createEmojisLayout()
            ViewMode.CLIPBOARD -> createClipboardLayout()
            ViewMode.PC_SHORTCUTS -> createPcShortcutsLayout()
        }
        keyboardArea.addView(keysLayout)

        val gestureView = if (currentViewMode == ViewMode.QWERTY && gestureEnabled) {
            GestureDrawingView(this, themeAccentColor).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        } else null

        if (gestureView != null) {
            keyboardArea.addView(gestureView)
            val swipedKeys = mutableListOf<String>()
            
            var startX = 0f
            var startY = 0f
            var startTime = 0L
            var isGesture = false
            var isLongPressed = false
            val longPressRunnable = Runnable {
                if (!isGesture) {
                    isLongPressed = true
                    vibrateClick()
                    swipedKeys.lastOrNull()?.let { key ->
                        getHintForKey(key)?.let { hint ->
                            showKeyPreview(pressedKeyView ?: keysLayout, hint)
                        }
                    }
                }
            }

            keyboardArea.setOnTouchListener { v, event ->
                val x = event.x
                val y = event.y
                
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = x
                        startY = y
                        startTime = System.currentTimeMillis()
                        isGesture = false
                        isLongPressed = false
                        spaceLongPressed = false
                        swipedKeys.clear()
                        gestureView.clearPath()
                        
                        findKeyViewAt(keysLayout as ViewGroup, x, y)?.let { keyView ->
                            pressedKeyView = keyView
                            keyView.isPressed = true
                            
                            val keyLabel = ((keyView as? ViewGroup)?.getChildAt(0) as? TextView)?.text?.toString() ?: ""
                            if (keyLabel.isNotEmpty()) {
                                swipedKeys.add(keyLabel)
                                showKeyPreview(keyView, keyLabel)
                                if (keyLabel == " " || keyLabel.lowercase() == "spacebar" || keyLabel == "␣") {
                                    handler.postDelayed(spaceLongPressRunnable, 3000)
                                }
                            }
                        }
                        
                        handler.postDelayed(longPressRunnable, longPressDelayMs.toLong())
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!isGesture) {
                            val dist = Math.hypot((x - startX).toDouble(), (y - startY).toDouble())
                            if (dist > dpToPx(12)) {
                                isGesture = true
                                handler.removeCallbacks(longPressRunnable)
                                handler.removeCallbacks(spaceLongPressRunnable)
                                isLongPressed = false
                            }
                        }
                        
                        if (isGesture) {
                            gestureView.addPoint(x, y)
                            findKeyViewAt(keysLayout as ViewGroup, x, y)?.let { keyView ->
                                if (pressedKeyView != keyView) {
                                    pressedKeyView?.isPressed = false
                                    pressedKeyView = keyView
                                    keyView.isPressed = true
                                    
                                    val keyLabel = ((keyView as? ViewGroup)?.getChildAt(0) as? TextView)?.text?.toString() ?: ""
                                    if (keyLabel.isNotEmpty()) {
                                        if (swipedKeys.isEmpty() || swipedKeys.last() != keyLabel) {
                                            swipedKeys.add(keyLabel)
                                            vibrateClick()
                                            showKeyPreview(keyView, keyLabel)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        handler.removeCallbacks(longPressRunnable)
                        handler.removeCallbacks(spaceLongPressRunnable)
                        pressedKeyView?.isPressed = false
                        pressedKeyView = null
                        gestureView.clearPath()
                        hideKeyPreview()
                        
                        if (isGesture) {
                            if (swipedKeys.isNotEmpty()) {
                                val word = matchGesturePath(swipedKeys)
                                if (word != null) {
                                    currentInputConnection?.commitText(word + " ", 1)
                                }
                            }
                        } else {
                            if (swipedKeys.isNotEmpty()) {
                                val key = swipedKeys[0]
                                if (spaceLongPressed && (key.lowercase() == "spacebar" || key == " " || key == "␣")) {
                                    // Triggered space bar picker already, do nothing
                                } else if (isLongPressed) {
                                    val hint = getHintForKey(key)
                                    if (hint != null) {
                                        currentInputConnection?.commitText(hint, 1)
                                    } else if (key.lowercase() == "spacebar" || key == " " || key == "␣") {
                                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                        imm.showInputMethodPicker()
                                    } else {
                                        handleKeyPress(key)
                                    }
                                } else {
                                    val keyCode = when (key.lowercase()) {
                                        "enter" -> android.view.KeyEvent.KEYCODE_ENTER
                                        "spacebar", " ", "␣" -> android.view.KeyEvent.KEYCODE_SPACE
                                        "back" -> android.view.KeyEvent.KEYCODE_DEL
                                        else -> 100
                                    }
                                    playClick(keyCode)
                                    handleKeyPress(key)
                                }
                            }
                        }
                        v.performClick()
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(longPressRunnable)
                        handler.removeCallbacks(spaceLongPressRunnable)
                        pressedKeyView?.isPressed = false
                        pressedKeyView = null
                        gestureView.clearPath()
                        hideKeyPreview()
                    }
                }
                true
            }
        }

        mainLayout.addView(keyboardArea)
        keyboardContainer.addView(mainLayout)
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

        if (isToolbarCollapsed) {
            val expandBtn = createToolbarIconButton(R.drawable.ic_expand) {
                isToolbarCollapsed = false
                updateKeyboardLayout()
            }
            toolbar.addView(expandBtn)
        } else {
            val collapseBtn = createToolbarIconButton(R.drawable.ic_collapse) {
                isToolbarCollapsed = true
                updateKeyboardLayout()
            }
            toolbar.addView(collapseBtn)

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
                toolbar.addView(transBtn)
            }

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
            toolbar.addView(clipBtn)

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
            toolbar.addView(pcBtn)

            val heightBtn = createToolbarIconButton(R.drawable.ic_height) {
                showSizeAdjustmentDialog()
            }
            toolbar.addView(heightBtn)

            val settingsBtn = createToolbarIconButton(R.drawable.ic_settings) {
                val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
            toolbar.addView(settingsBtn)

            if (voiceDictationEnabled) {
                val micBtn = createToolbarIconButton(R.drawable.ic_mic, isListening) {
                    if (isListening) {
                        stopVoiceInput()
                    } else {
                        startVoiceInput()
                    }
                }
                toolbar.addView(micBtn)
            }
        }

        return toolbar
    }

    private fun showSizeAdjustmentDialog() {
        val context = this
        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }

        val text = TextView(context).apply {
            text = "Keyboard Height: ${keyboardHeightDp}dp"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, 0, 0, dpToPx(12))
        }
        dialogView.addView(text)

        val seekBar = SeekBar(context).apply {
            max = 200
            progress = keyboardHeightDp - 200
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    keyboardHeightDp = progress + 200
                    text.text = "Keyboard Height: ${keyboardHeightDp}dp"
                    updateKeyboardLayout()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    savePreferences()
                }
            })
        }
        dialogView.addView(seekBar)

        val overlayParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        val frame = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#CC000000"))
            setOnClickListener {
                updateKeyboardLayout()
            }
        }

        val centerParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
            setMargins(dpToPx(24), 0, dpToPx(24), 0)
        }
        dialogView.layoutParams = centerParams
        frame.addView(dialogView)

        keyboardContainer.addView(frame, overlayParams)
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

        val row1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        val row2 = if (isShifted) listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
                   else listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        val row3 = if (isShifted) listOf("A", "S", "D", "F", "G", "H", "J", "K", "L")
                   else listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        val row4 = if (isShifted) listOf("Shift", "Z", "X", "C", "V", "B", "N", "M", "Back")
                   else listOf("shift", "z", "x", "c", "v", "b", "n", "m", "Back")
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
        val row2 = listOf("@", "#", "${'$'}", "%", "&", "-", "+", "(", ")", "/")
        val row3 = listOf("*", "\"", "'", ":", ";", "!", "?", "\\", "_")
        val row4 = listOf("ABC", "~", "`", "|", "<", ">", "=", "[", "]", "Back")
        val row5 = listOf("ABC", "😊", "Spacebar", ",", "Enter")

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
            val keyView = FrameLayout(this).apply {
                val weight = when (key.lowercase()) {
                    "spacebar", " ", "␣" -> 4.0f
                    "shift", "back", "enter", "?123", "abc", "📋", "☺", "⚙" -> 1.5f
                    else -> 1.0f
                }
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    weight
                ).apply {
                    setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
                }

                val keyLayout = RelativeLayout(context).apply {
                    val isSpecial = listOf("shift", "back", "enter", "?123", "abc", "📋", "☺", "⚙").contains(key.lowercase())
                    val bgColor = if (isSpecial) Color.parseColor(themeSpecialKeyBg) else Color.parseColor(themeRegularKeyBg)
                    background = createKeyDrawable(bgColor, dpToPx(6))
                    isClickable = true
                    isFocusable = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val isIconKey = listOf("shift", "back", "enter", "📋", "😊", "☺", "⚙").contains(key.lowercase())
                if (isIconKey) {
                    val imageView = ImageView(context).apply {
                        val iconRes = when (key.lowercase()) {
                            "shift" -> if (isShifted) R.drawable.ic_shift_active else R.drawable.ic_shift
                            "back" -> R.drawable.ic_backspace
                            "enter" -> R.drawable.ic_enter
                            "📋" -> R.drawable.ic_clipboard
                            "😊", "☺" -> R.drawable.ic_emoji
                            "⚙" -> R.drawable.ic_settings
                            else -> 0
                        }
                        if (iconRes != 0) {
                            setImageResource(iconRes)
                            setColorFilter(Color.WHITE)
                        }
                        layoutParams = RelativeLayout.LayoutParams(
                            dpToPx(20),
                            dpToPx(20)
                        ).apply {
                            addRule(RelativeLayout.CENTER_IN_PARENT)
                        }
                    }
                    keyLayout.addView(imageView)
                } else {
                    val mainTextView = TextView(context).apply {
                        text = when (key.lowercase()) {
                            "spacebar" -> " "
                            else -> key
                        }
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        gravity = Gravity.CENTER
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
                        setTextColor(Color.parseColor("#7AFFFFFF"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                        gravity = Gravity.CENTER
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
                                currentInputConnection?.deleteSurroundingText(1, 0)
                                
                                backspaceRunnable = object : Runnable {
                                    override fun run() {
                                        vibrateClick()
                                        playClick(android.view.KeyEvent.KEYCODE_DEL)
                                        currentInputConnection?.deleteSurroundingText(1, 0)
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
                } else if (key.lowercase() == "spacebar" || key == " " || key == "␣") {
                    var spaceLongPressedLocal = false
                    val spaceLongPressRunnableLocal = Runnable {
                        spaceLongPressedLocal = true
                        vibrateClick()
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showInputMethodPicker()
                    }

                    keyLayout.setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                spaceLongPressedLocal = false
                                v.isPressed = true
                                handler.postDelayed(spaceLongPressRunnableLocal, 3000)
                            }
                            MotionEvent.ACTION_UP -> {
                                handler.removeCallbacks(spaceLongPressRunnableLocal)
                                v.isPressed = false
                                if (!spaceLongPressedLocal) {
                                    playClick(android.view.KeyEvent.KEYCODE_SPACE)
                                    handleKeyPress(key)
                                }
                                v.performClick()
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                handler.removeCallbacks(spaceLongPressRunnableLocal)
                                v.isPressed = false
                            }
                        }
                        true
                    }
                } else if (currentViewMode == ViewMode.QWERTY && gestureEnabled) {
                    // Handled by keyboardArea gesture touch listener
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
                            }
                            MotionEvent.ACTION_UP -> {
                                handler.removeCallbacks(keyLongPressRunnable)
                                v.isPressed = false
                                hideKeyPreview()
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

    private fun handleKeyPress(key: String) {
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
                            et.text.delete(start - 1, start)
                        }
                    }
                }
                "enter", "↵" -> {
                    currentInputConnection?.finishComposingText()
                    isTranslationActive = false
                    updateKeyboardLayout()
                }
                "spacebar", " ", "␣" -> {
                    et.text.replace(Math.min(start, end), Math.max(start, end), " ")
                }
                "shift", "⇧", "⇪" -> {
                    val now = System.currentTimeMillis()
                    if (now - lastShiftPressTime < 300) {
                        isCapsLock = !isCapsLock
                        isShifted = isCapsLock
                    } else {
                        isCapsLock = false
                        isShifted = !isShifted
                    }
                    lastShiftPressTime = now
                    updateKeyboardLayout()
                }
                "?123" -> {
                    currentViewMode = ViewMode.SYMBOLS
                    updateKeyboardLayout()
                }
                "abc" -> {
                    currentViewMode = ViewMode.QWERTY
                    updateKeyboardLayout()
                }
                "😊", "☺", "😀" -> {
                    currentViewMode = ViewMode.EMOJIS
                    updateKeyboardLayout()
                }
                else -> {
                    et.text.replace(Math.min(start, end), Math.max(start, end), key)
                    if (isShifted && !isCapsLock) {
                        isShifted = false
                        updateKeyboardLayout()
                    }
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
                    isCapsLock = false
                    isShifted = !isShifted
                }
                lastShiftPressTime = now
                updateKeyboardLayout()
            }
            "back", "⌫" -> {
                ic.deleteSurroundingText(1, 0)
            }
            "enter", "↵" -> {
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER))
            }
            "?123" -> {
                currentViewMode = ViewMode.SYMBOLS
                updateKeyboardLayout()
            }
            "abc" -> {
                currentViewMode = ViewMode.QWERTY
                updateKeyboardLayout()
            }
            "😊", "☺", "😀" -> {
                currentViewMode = ViewMode.EMOJIS
                updateKeyboardLayout()
            }
            "spacebar", " ", "␣" -> {
                ic.commitText(" ", 1)
            }
            else -> {
                ic.commitText(key, 1)
                if (isShifted && !isCapsLock) {
                    isShifted = false
                    updateKeyboardLayout()
                }
            }
        }
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
                        currentInputConnection?.deleteSurroundingText(1, 0)
                        
                        backspaceRunnable = object : Runnable {
                            override fun run() {
                                vibrateClick()
                                playClick(android.view.KeyEvent.KEYCODE_DEL)
                                currentInputConnection?.deleteSurroundingText(1, 0)
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
            
            val grid = GridLayout(this@GlideTypeKeyboardService).apply {
                columnCount = 7
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
            }

            val gridScroll = ScrollView(this@GlideTypeKeyboardService).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                addView(grid)
            }

            val categoryEmojis = emojiCategories[categoryIndex].second
            val itemSize = dpToPx(42)

            for (emoji in categoryEmojis) {
                if (emoji.isEmpty()) continue
                val cell = FrameLayout(this@GlideTypeKeyboardService).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 0
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        setGravity(Gravity.CENTER)
                        setMargins(dpToPx(1), dpToPx(1), dpToPx(1), dpToPx(1))
                    }

                    addView(TextView(this@GlideTypeKeyboardService).apply {
                        text = emoji
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                        gravity = Gravity.CENTER
                        background = createKeyDrawable(Color.TRANSPARENT, dpToPx(4))
                        layoutParams = FrameLayout.LayoutParams(itemSize, itemSize)
                        setOnClickListener {
                            vibrateClick()
                            playClick(100)
                            currentInputConnection?.commitText(emoji, 1)
                        }
                    })
                }
                grid.addView(cell)
            }
            contentArea.addView(gridScroll)
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
                currentInputConnection?.deleteSurroundingText(1, 0)
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
                val itemRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.parseColor(if (item.isPinned) "#2E1A47" else "#1A1A1A"))
                        setStroke(2, Color.parseColor(if (item.isPinned) themeAccentColor else "#252525"))
                        cornerRadius = dpToPx(6).toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(dpToPx(6), dpToPx(3), dpToPx(6), dpToPx(3))
                    }
                }

                val clipTextView = TextView(this).apply {
                    text = item.text
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1.0f
                    ).apply {
                        setMargins(0, 0, dpToPx(12), 0)
                    }
                    setOnClickListener {
                        vibrateClick()
                        currentInputConnection?.commitText(item.text, 1)
                    }
                }
                itemRow.addView(clipTextView)

                val pinBtn = ImageView(this).apply {
                    setImageResource(if (item.isPinned) R.drawable.ic_pin else R.drawable.ic_unpin)
                    setColorFilter(if (item.isPinned) Color.parseColor(themeAccentColor) else Color.WHITE)
                    background = createKeyDrawable(Color.TRANSPARENT, dpToPx(4))
                    isClickable = true
                    isFocusable = true
                    layoutParams = LinearLayout.LayoutParams(dpToPx(35), dpToPx(35)).apply {
                        setMargins(0, 0, dpToPx(6), 0)
                    }
                    setOnClickListener {
                        vibrateClick()
                        togglePinItem(item)
                    }
                }
                itemRow.addView(pinBtn)

                val delBtn = ImageView(this).apply {
                    setImageResource(R.drawable.ic_delete)
                    setColorFilter(Color.parseColor("#FF1744"))
                    background = createKeyDrawable(Color.TRANSPARENT, dpToPx(4))
                    isClickable = true
                    isFocusable = true
                    layoutParams = LinearLayout.LayoutParams(dpToPx(35), dpToPx(35))
                    setOnClickListener {
                        vibrateClick()
                        deleteClipboardItem(item)
                    }
                }
                itemRow.addView(delBtn)

                listLayout.addView(itemRow)
            }
        }

        rootLayout.addView(scrollView)
        return rootLayout
    }

    private fun playClick(keyCode: Int) {
        if (!soundEnabled) return
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val volume = 0.5f
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_ENTER -> am.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN, volume)
            android.view.KeyEvent.KEYCODE_DEL -> am.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE, volume)
            android.view.KeyEvent.KEYCODE_SPACE -> am.playSoundEffect(AudioManager.FX_KEYPRESS_SPACEBAR, volume)
            else -> am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, volume)
        }
    }

    private fun vibrateClick() {
        if (!vibrationEnabled) return
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(18)
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

        val closeBtn = Button(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = createKeyDrawable(Color.TRANSPARENT, dpToPx(4))
            layoutParams = LinearLayout.LayoutParams(dpToPx(30), dpToPx(30))
            setOnClickListener {
                vibrateClick()
                translationInputField = null
                isTranslationActive = false
                currentInputConnection?.finishComposingText()
                updateKeyboardLayout()
            }
        }
        toolbar.addView(closeBtn)

        val sourceBtn = Button(this).apply {
            text = translationSourceLang.uppercase()
            setTextColor(Color.parseColor(themeAccentColor))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
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

        val targetBtn = Button(this).apply {
            text = translationTargetLang.uppercase()
            setTextColor(Color.parseColor(themeAccentColor))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = createKeyDrawable(Color.parseColor("#252525"), dpToPx(4))
            layoutParams = LinearLayout.LayoutParams(dpToPx(45), dpToPx(30))
            setOnClickListener {
                vibrateClick()
                showLanguageMenu(this, false)
            }
        }
        toolbar.addView(targetBtn)

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

        val sendBtn = Button(this).apply {
            text = "✓"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = createKeyDrawable(Color.parseColor(themeAccentColor), dpToPx(4))
            layoutParams = LinearLayout.LayoutParams(dpToPx(35), dpToPx(30)).apply {
                setMargins(0, 0, dpToPx(4), 0)
            }
            setOnClickListener {
                vibrateClick()
                currentInputConnection?.finishComposingText()
                inputField.setText("")
                translationInputField = null
                isTranslationActive = false
                updateKeyboardLayout()
            }
        }
        toolbar.addView(sendBtn)

        return toolbar
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
                    val second = first.getJSONArray(0)
                    val translatedText = second.getString(0)
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
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isListening = false
                        updateKeyboardLayout()
                    }
                    override fun onError(error: Int) {
                        isListening = false
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
                        updateKeyboardLayout()
                    }
                    override fun onPartialResults(partialResults: android.os.Bundle?) {}
                    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                })
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
        isListening = true
        updateKeyboardLayout()
    }

    private fun stopVoiceInput() {
        speechRecognizer?.stopListening()
        isListening = false
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

    private fun matchGesturePath(pathKeys: List<String>): String? {
        if (pathKeys.isEmpty()) return null
        val startChar = pathKeys.first().lowercase()
        val endChar = pathKeys.last().lowercase()

        var bestMatch: String? = null
        var bestScore = 0.0

        for (word in commonWords) {
            if (word.length < 2) continue
            if (word.first().toString() != startChar || word.last().toString() != endChar) continue

            var pathIndex = 0
            var matchedChars = 0
            for (char in word) {
                while (pathIndex < pathKeys.size) {
                    if (pathKeys[pathIndex].lowercase() == char.toString()) {
                        matchedChars++
                        pathIndex++
                        break
                    }
                    pathIndex++
                }
            }

            if (matchedChars == word.length) {
                val score = word.length.toDouble()
                if (score > bestScore) {
                    bestScore = score
                    bestMatch = word
                }
            }
        }
        return bestMatch
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
        val row2 = listOf("Cut", "Copy", "Paste", "Find", "Home")
        val row3 = listOf("PgUp", "Up", "PgDn", "End", "Del")
        val row4 = listOf("Left", "Down", "Right", "Enter", "ABC")

        layout.addView(createPcKeyRow(row1))
        layout.addView(createPcKeyRow(row2))
        layout.addView(createPcKeyRow(row3))
        layout.addView(createPcKeyRow(row4))

        return layout
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
            val keyView = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1.0f
                ).apply {
                    setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
                }

                val keyLayout = RelativeLayout(context).apply {
                    background = createKeyDrawable(Color.parseColor(themeSpecialKeyBg), dpToPx(6))
                    isClickable = true
                    isFocusable = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val mainTextView = TextView(context).apply {
                    text = key
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    gravity = Gravity.CENTER
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
                    handlePcShortcutPress(key)
                }

                addView(keyLayout)
            }
            row.addView(keyView)
        }

        return row
    }

    private fun handlePcShortcutPress(key: String) {
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
}

class GestureDrawingView(context: Context, val strokeColor: String) : View(context) {
    private val paint = Paint().apply {
        color = Color.parseColor(strokeColor)
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    private val path = Path()
    private val points = mutableListOf<Pair<Float, Float>>()

    fun addPoint(x: Float, y: Float) {
        points.add(Pair(x, y))
        if (points.size == 1) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
        invalidate()
    }

    fun clearPath() {
        path.reset()
        points.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(path, paint)
    }
}
