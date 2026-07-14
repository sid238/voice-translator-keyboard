package com.orbit.smartkeyboard

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionRequestActivity : Activity() {

    companion object {
        const val EXTRA_PERMISSION = "extra_permission"
        const val REQUEST_CODE = 1001
        var onPermissionGranted: (() -> Unit)? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        
        val perm = intent.getStringExtra(EXTRA_PERMISSION)
        if (perm.isNullOrEmpty()) {
            finish()
            overridePendingTransition(0, 0)
            return
        }

        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            onPermissionGranted?.invoke()
            finish()
            overridePendingTransition(0, 0)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(perm), REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onPermissionGranted?.invoke()
            }
        }
        finish()
        overridePendingTransition(0, 0)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
