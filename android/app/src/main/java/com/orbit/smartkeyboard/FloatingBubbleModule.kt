package com.orbit.smartkeyboard

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.BaseActivityEventListener
import android.app.Activity
import java.io.File
import java.io.FileOutputStream

class FloatingBubbleModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private val PICK_THEME_REQUEST = 9081
    private val PICK_OCR_REQUEST = 9082
    private var mPickPromise: Promise? = null
    private var mOcrPickPromise: Promise? = null

    private val mActivityEventListener = object : BaseActivityEventListener() {
        override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val imageUri = data.data ?: return
                try {
                    val contentResolver = reactApplicationContext.contentResolver
                    val inputStream = contentResolver.openInputStream(imageUri) ?: return
                    val prefs = reactApplicationContext.getSharedPreferences("glidetype_keyboard_prefs", Context.MODE_PRIVATE)
                    
                    if (requestCode == PICK_THEME_REQUEST) {
                        val destFolder = File(reactApplicationContext.cacheDir, "theme_images").apply { mkdirs() }
                        val destFile = File(destFolder, "custom_bg.jpg")
                        val outputStream = FileOutputStream(destFile)
                        inputStream.copyTo(outputStream)
                        inputStream.close()
                        outputStream.close()
                        prefs.edit().putString("theme_image_path", destFile.absolutePath).apply()
                        mPickPromise?.resolve(destFile.absolutePath)
                        mPickPromise = null
                    } else if (requestCode == PICK_OCR_REQUEST) {
                        val destFolder = File(reactApplicationContext.cacheDir, "ocr_images").apply { mkdirs() }
                        val destFile = File(destFolder, "ocr_${System.currentTimeMillis()}.jpg")
                        val outputStream = FileOutputStream(destFile)
                        inputStream.copyTo(outputStream)
                        inputStream.close()
                        outputStream.close()
                        prefs.edit().putString("ocr_image_path", destFile.absolutePath).apply()
                        mOcrPickPromise?.resolve(destFile.absolutePath)
                        mOcrPickPromise = null
                    }
                } catch (e: Exception) {
                    if (requestCode == PICK_THEME_REQUEST) {
                        mPickPromise?.reject("ERROR", e.message, e)
                        mPickPromise = null
                    } else {
                        mOcrPickPromise?.reject("ERROR", e.message, e)
                        mOcrPickPromise = null
                    }
                }
            } else {
                if (requestCode == PICK_THEME_REQUEST) {
                    mPickPromise?.reject("CANCELLED", "Image picking was cancelled")
                    mPickPromise = null
                } else {
                    mOcrPickPromise?.reject("CANCELLED", "Image picking was cancelled")
                    mOcrPickPromise = null
                }
            }
        }
    }

    init {
        reactApplicationContext.addActivityEventListener(mActivityEventListener)
    }

    override fun getName(): String {
        return "FloatingBubble"
    }

    @ReactMethod
    fun pickThemeImage(promise: Promise) {
        val activity = reactApplicationContext.currentActivity
        if (activity == null) {
            promise.reject("Activity not available")
            return
        }
        mPickPromise = promise
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        activity.startActivityForResult(Intent.createChooser(intent, "Select Background Image"), PICK_THEME_REQUEST)
    }

    @ReactMethod
    fun pickOcrImage(promise: Promise) {
        val activity = reactApplicationContext.currentActivity
        if (activity == null) {
            promise.reject("Activity not available")
            return
        }
        mOcrPickPromise = promise
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        activity.startActivityForResult(Intent.createChooser(intent, "Select Image for OCR"), PICK_OCR_REQUEST)
    }

    @ReactMethod
    fun clearThemeImage(promise: Promise) {
        val prefs = reactApplicationContext.getSharedPreferences("glidetype_keyboard_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("theme_image_path").apply()
        
        try {
            val destFolder = File(reactApplicationContext.cacheDir, "theme_images")
            val destFile = File(destFolder, "custom_bg.jpg")
            if (destFile.exists()) {
                destFile.delete()
            }
        } catch (e: Exception) {}
        
        promise.resolve(true)
    }

    @ReactMethod
    fun isKeyboardEnabled(promise: Promise) {
        try {
            val imm = reactApplicationContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val list = imm.enabledInputMethodList
            val packageName = reactApplicationContext.packageName
            val targetId = "$packageName/.GlideTypeKeyboardService"
            val isEnabled = list.any { it.id == targetId }
            promise.resolve(isEnabled)
        } catch (e: Exception) {
            promise.reject("Error checking if keyboard is enabled", e)
        }
    }

    @ReactMethod
    fun isKeyboardDefault(promise: Promise) {
        try {
            val currentIME = Settings.Secure.getString(
                reactApplicationContext.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
            val packageName = reactApplicationContext.packageName
            val targetId = "$packageName/.GlideTypeKeyboardService"
            promise.resolve(currentIME != null && currentIME == targetId)
        } catch (e: Exception) {
            promise.reject("Error checking if keyboard is default", e)
        }
    }

    @ReactMethod
    fun openKeyboardSettings() {
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        reactApplicationContext.startActivity(intent)
    }

    @ReactMethod
    fun showKeyboardPicker() {
        // Must run on main thread
        val imm = reactApplicationContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }

    @ReactMethod
    fun saveStringSetting(key: String, value: String) {
        val prefs = reactApplicationContext.getSharedPreferences("glidetype_keyboard_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(key, value).apply()
    }

    @ReactMethod
    fun getStringSetting(key: String, defaultValue: String, promise: Promise) {
        val prefs = reactApplicationContext.getSharedPreferences("glidetype_keyboard_prefs", Context.MODE_PRIVATE)
        promise.resolve(prefs.getString(key, defaultValue))
    }

    @ReactMethod
    fun saveIntSetting(key: String, value: Int) {
        val prefs = reactApplicationContext.getSharedPreferences("glidetype_keyboard_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt(key, value).apply()
    }

    @ReactMethod
    fun getIntSetting(key: String, defaultValue: Int, promise: Promise) {
        val prefs = reactApplicationContext.getSharedPreferences("glidetype_keyboard_prefs", Context.MODE_PRIVATE)
        promise.resolve(prefs.getInt(key, defaultValue))
    }

    @ReactMethod
    fun saveBooleanSetting(key: String, value: Boolean) {
        val prefs = reactApplicationContext.getSharedPreferences("glidetype_keyboard_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(key, value).apply()
    }

    @ReactMethod
    fun getBooleanSetting(key: String, defaultValue: Boolean, promise: Promise) {
        val prefs = reactApplicationContext.getSharedPreferences("glidetype_keyboard_prefs", Context.MODE_PRIVATE)
        promise.resolve(prefs.getBoolean(key, defaultValue))
    }
}
