package com.translator.overlaykeyboard

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var overlayView: View? = null
    private var isExpanded = false

    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var overlayParams: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createBubble()
        createOverlay()
    }

    private fun createBubble() {
        val size = dpToPx(60)
        bubbleView = FrameLayout(this).apply {
            // Circle Background
            setBackgroundColor(Color.TRANSPARENT)
            val container = FrameLayout(context).apply {
                val layoutParams = FrameLayout.LayoutParams(dpToPx(52), dpToPx(52)).apply {
                    gravity = Gravity.CENTER
                }
                // Custom design: vibrant purple gradient-like circle
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(Color.parseColor("#8A2BE2")) // Violet/purple
                    setStroke(2, Color.WHITE)
                }
                layoutParams.setMargins(0, 0, 0, 0)
                this.layoutParams = layoutParams

                // Voice/Translate Icon
                addView(ImageView(context).apply {
                    val iconParams = FrameLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
                        gravity = Gravity.CENTER
                    }
                    this.layoutParams = iconParams
                    // Draw a white microphone or translate icon using a vector or a custom shape.
                    // For simplicity and since we don't have drawable assets, we draw a custom icon programmatically:
                    setImageDrawable(android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(Color.WHITE)
                    })
                    // Just put a small indicator inside:
                    addView(View(context).apply {
                        val innerParams = FrameLayout.LayoutParams(dpToPx(12), dpToPx(12)).apply {
                            gravity = Gravity.CENTER
                        }
                        this.layoutParams = innerParams
                        background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(Color.parseColor("#8A2BE2"))
                        }
                    })
                })
            }
            addView(container)
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        bubbleParams = WindowManager.LayoutParams(
            size,
            size,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        bubbleView?.let { view ->
            windowManager.addView(view, bubbleParams)
            setupBubbleTouchListener(view)
        }
    }

    private fun createOverlay() {
        val width = dpToPx(340)
        val height = dpToPx(280)

        val frame = FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#121212")) // Sleek Dark Mode Background
                cornerRadius = dpToPx(16).toFloat()
                setStroke(3, Color.parseColor("#2C2C2C"))
            }
        }

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false

            // Expose copy functionality and minimize triggers to JS
            addJavascriptInterface(object {
                @JavascriptInterface
                fun copyToClipboard(text: String) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Translated Text", text)
                    clipboard.setPrimaryClip(clip)
                    // Minimize overlay after copy
                    post {
                        collapseOverlay()
                    }
                }

                @JavascriptInterface
                fun closeOverlay() {
                    post {
                        collapseOverlay()
                    }
                }
            }, "AndroidBridge")

            // Automatically grant Mic permission for Speech recognition
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                }
            }

            // HTML Webpage Content for Translation and Speech recognition
            val htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body {
                            background-color: #121212;
                            color: #FFFFFF;
                            font-family: system-ui, -apple-system, sans-serif;
                            margin: 0;
                            padding: 12px;
                            box-sizing: border-box;
                        }
                        .header {
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                            margin-bottom: 8px;
                        }
                        .title {
                            font-size: 14px;
                            font-weight: bold;
                            color: #8A2BE2;
                        }
                        .close-btn {
                            background: transparent;
                            border: none;
                            color: #888;
                            font-size: 16px;
                            cursor: pointer;
                        }
                        .selectors {
                            display: flex;
                            gap: 8px;
                            margin-bottom: 8px;
                        }
                        select {
                            flex: 1;
                            background-color: #1E1E1E;
                            color: white;
                            border: 1px solid #333;
                            padding: 6px;
                            border-radius: 6px;
                            font-size: 12px;
                            outline: none;
                        }
                        textarea {
                            width: 100%;
                            height: 60px;
                            background-color: #1E1E1E;
                            color: white;
                            border: 1px solid #333;
                            border-radius: 6px;
                            padding: 8px;
                            box-sizing: border-box;
                            font-size: 13px;
                            resize: none;
                            margin-bottom: 8px;
                            outline: none;
                        }
                        .buttons {
                            display: flex;
                            gap: 8px;
                        }
                        button.action {
                            flex: 1;
                            background-color: #8A2BE2;
                            color: white;
                            border: none;
                            padding: 8px;
                            border-radius: 6px;
                            font-weight: bold;
                            font-size: 12px;
                            cursor: pointer;
                        }
                        button.mic {
                            background-color: #CF6679;
                        }
                        button.copy {
                            background-color: #03DAC6;
                            color: black;
                        }
                        .status {
                            font-size: 10px;
                            color: #888;
                            margin-bottom: 4px;
                            text-align: center;
                            height: 14px;
                        }
                    </style>
                </head>
                <body>
                    <div class="header">
                        <span class="title">Quick Translate</span>
                        <button class="close-btn" onclick="AndroidBridge.closeOverlay()">✕</button>
                    </div>
                    <div class="selectors">
                        <select id="srcLang">
                            <option value="hi-IN" selected>Hindi</option>
                            <option value="en-US">English</option>
                            <option value="es-ES">Spanish</option>
                            <option value="fr-FR">French</option>
                        </select>
                        <select id="destLang">
                            <option value="en" selected>English</option>
                            <option value="hi">Hindi</option>
                            <option value="es">Spanish</option>
                            <option value="fr">French</option>
                        </select>
                    </div>
                    <textarea id="inputText" placeholder="Type here or tap Voice Input..."></textarea>
                    <textarea id="outputText" placeholder="Translated text will appear here..." readonly></textarea>
                    <div class="status" id="statusText">Ready</div>
                    <div class="buttons">
                        <button class="action mic" id="micBtn" onclick="toggleVoice()">Voice Input</button>
                        <button class="action" onclick="translateText()">Translate</button>
                        <button class="action copy" onclick="copyResult()">Copy</button>
                    </div>

                    <script>
                        let recognition;
                        let isListening = false;

                        if ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window) {
                            const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
                            recognition = new SpeechRecognition();
                            recognition.continuous = false;
                            recognition.interimResults = false;

                            recognition.onstart = () => {
                                isListening = true;
                                document.getElementById('micBtn').innerText = 'Stop';
                                document.getElementById('statusText').innerText = 'Listening...';
                            };

                            recognition.onerror = (event) => {
                                console.error(event);
                                document.getElementById('statusText').innerText = 'Error: ' + event.error;
                                stopVoice();
                            };

                            recognition.onend = () => {
                                stopVoice();
                            };

                            recognition.onresult = (event) => {
                                const transcript = event.results[0][0].transcript;
                                document.getElementById('inputText').value = transcript;
                                translateText();
                            };
                        } else {
                            document.getElementById('micBtn').disabled = true;
                            document.getElementById('statusText').innerText = 'Speech recognition not supported';
                        }

                        function toggleVoice() {
                            if (isListening) {
                                recognition.stop();
                            } else {
                                const src = document.getElementById('srcLang').value;
                                recognition.lang = src;
                                recognition.start();
                            }
                        }

                        function stopVoice() {
                            isListening = false;
                            document.getElementById('micBtn').innerText = 'Voice Input';
                            if (document.getElementById('statusText').innerText === 'Listening...') {
                                document.getElementById('statusText').innerText = 'Ready';
                            }
                        }

                        async function translateText() {
                            const text = document.getElementById('inputText').value.trim();
                            if (!text) return;
                            
                            const sl = document.getElementById('srcLang').value.split('-')[0];
                            const tl = document.getElementById('destLang').value;
                            
                            document.getElementById('statusText').innerText = 'Translating...';
                            try {
                                const url = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=${'$'}{sl}&tl=${'$'}{tl}&dt=t&q=${'$'}{encodeURIComponent(text)}`;
                                const response = await fetch(url);
                                const data = await response.json();
                                const translated = data[0].map(x => x[0]).join('');
                                document.getElementById('outputText').value = translated;
                                document.getElementById('statusText').innerText = 'Translated!';
                            } catch (err) {
                                document.getElementById('statusText').innerText = 'Translation failed';
                            }
                        }

                        function copyResult() {
                            const translated = document.getElementById('outputText').value.trim();
                            if (translated) {
                                AndroidBridge.copyToClipboard(translated);
                            }
                        }
                    </script>
                </body>
                </html>
            """.trimIndent()

            loadDataWithBaseURL("https://localhost", htmlContent, "text/html", "UTF-8", null)
        }

        frame.addView(webView)
        overlayView = frame

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        overlayParams = WindowManager.LayoutParams(
            width,
            height,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
    }

    private fun setupBubbleTouchListener(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var clickStart: Long = 0

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    clickStart = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    bubbleParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    bubbleParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, bubbleParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val clickDuration = System.currentTimeMillis() - clickStart
                    val deltaX = abs(event.rawX - initialTouchX)
                    val deltaY = abs(event.rawY - initialTouchY)

                    // Threshold for click vs drag
                    if (clickDuration < 200 && deltaX < 10 && deltaY < 10) {
                        expandOverlay()
                    } else {
                        // Snap to left or right screen edge
                        val screenWidth = windowManager.defaultDisplay.width
                        if (bubbleParams.x + view.width / 2 < screenWidth / 2) {
                            bubbleParams.x = 0
                        } else {
                            bubbleParams.x = screenWidth - view.width
                        }
                        windowManager.updateViewLayout(view, bubbleParams)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun expandOverlay() {
        if (isExpanded) return
        isExpanded = true

        // Hide bubble temporarily or keep it
        bubbleView?.visibility = View.GONE

        // Add overlay view to window manager
        overlayView?.let { view ->
            windowManager.addView(view, overlayParams)
        }
    }

    private fun collapseOverlay() {
        if (!isExpanded) return
        isExpanded = false

        // Remove overlay view
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {}
        }

        // Show bubble again
        bubbleView?.visibility = View.VISIBLE
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {}
        }
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {}
        }
    }
}
