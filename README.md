# Orbit Smart Keyboard 🚀 (v2.1.0 Pro Edition)

**Orbit Smart Keyboard** is an advanced, high-performance Android Input Method Service (IME) featuring built-in AI assistant capabilities, real-time live translation, gesture-based spacebar trackpad cursor control, customizable themes, matrix visual effects, OCR camera text scanning, clipboard history management, and low-latency voice dictation.

---

## 🌟 Key Features

### 🧠 Orbit AI Assistant
- **In-Keyboard Chat**: Ask AI questions, fix grammar, rewrite text, summarize content, or translate without switching apps.
- **Voice Dictation in AI**: Integrated voice typing directly inside the AI prompt bar with real-time waveform visualization.
- **Context-Aware Prompts**: AI action chips for quick text processing.

### 🌐 Live Real-Time Translation
- **On-the-Fly Input Translation**: Translate your typed text instantly between English, Hindi, Spanish, French, German, and 100+ languages.
- **Bi-Directional Support**: Live translation output directly into any active input box.

### 🎯 Trackpad Cursor & Typing Control
- **Spacebar Cursor Control**: Drag horizontally on the spacebar to navigate the cursor smoothly across text fields (Trackball/Trackpad mode).
- **Selection Backspace**: Highlight text and press backspace to delete the entire selection instantly. Single tap deletes 1 character accurately without text field wipeout.
- **Sentence Auto-Capitalization**: Smart capitalization at sentence boundaries (`. `, `? `, `! `, newline).

### 🎙 Advanced Voice Input & Audio-Visuals
- **Raw Voice Capture**: High-fidelity Android `SpeechRecognizer` integration without aggressive word censorship.
- **Dynamic Waveform Visualizer**: Live canvas-rendered `VoiceWaveView` with real-time RMS audio amplitude animations.

### 🎨 Visual Customization & Themes
- **Dynamic Matrix Effect**: Animated digital rain matrix canvas overlay on keypress with high-contrast software rendering.
- **Theme Engine**: Sleek Dark, Glassmorphism, and Light modes with dynamic contrast stroke rendering.
- **Custom Wallpaper Backgrounds**: Support for custom user background images with transparency and key radii control.

---

## 📱 Release Notes — Version 2.1.0

### Added & Enhanced:
1. **Spacebar Cursor Control**: Added horizontal drag gesture on spacebar for trackpad-style cursor positioning.
2. **Selected Text Deletion**: Fixed backspace behavior so highlighting text deletes the active selection across all external apps and in-app input fields.
3. **Raw Speech Recognition**: Removed restrictive word filters from Voice Typing to preserve accurate raw spoken phrases.
4. **AI Panel Voice Dictation**: Added voice input button with animated waveform directly inside Orbit AI chat prompt bar.
5. **UI & In-App Keyboard Tester**: Added bottom-anchored 1-line keyboard test card in Settings with smooth window inset elevation above the soft keyboard.
6. **Languages Support Notice**: Added notice card regarding upcoming language dictionary packs for future releases.

---

## 🛠 Tech Stack & Requirements

- **Platform**: Android 7.0+ (API Level 24+)
- **Target SDK**: Android 15 (API Level 35)
- **Language**: Kotlin 1.9+, Java 17
- **Architecture**: Android `InputMethodService`, Custom Canvas Rendering (`KeyboardEffectsView`, `VoiceWaveView`), Software Layer Blurs.

---

## 🚀 Building & Installation

### Build Debug APK via Command Line
```bash
cd android
./gradlew assembleDebug
```

The compiled APK will be available at:
`android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`

---

## 📄 License
Licensed under the Apache License 2.0.
