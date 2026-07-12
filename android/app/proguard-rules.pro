# Keep the keyboard IME service, settings activity and helpers
-keep class com.orbit.smartkeyboard.** { *; }

# ML Kit and CameraX rely on reflection / native APIs
-keep class com.google.mlkit.** { *; }
-keep class androidx.camera.** { *; }

# Material Components
-keep class com.google.android.material.** { *; }
