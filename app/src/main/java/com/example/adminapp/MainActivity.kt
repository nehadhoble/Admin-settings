package com.example.adminapp

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.adminapp.model.FirebaseHelper
import com.example.adminapp.model.PasswordPolicy
import com.example.adminapp.receiver.DeviceAdminReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var compName: ComponentName
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var btnPin: Button
    private lateinit var btnUnpin: Button

    private lateinit var btnLock: Button
    private lateinit var btnUnlock: Button

    private var failedAttempts = 0
    private var policy: PasswordPolicy? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnPin = findViewById(R.id.btnPin)
        btnUnpin = findViewById(R.id.btnUnpin)

        btnPin.setOnClickListener {
            pinApp()
        }

        btnUnpin.setOnClickListener {
            unpinApp()
        }

        btnLock = findViewById(R.id.btnLock)
        btnUnlock = findViewById(R.id.btnUnlock)

        btnLock.setOnClickListener {
            lockDevice()
        }

        btnUnlock.setOnClickListener {
            unlockDevice()
        }

        sharedPreferences = getSharedPreferences("admin_prefs", MODE_PRIVATE)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        compName = ComponentName(this, DeviceAdminReceiver::class.java)

        if (devicePolicyManager.isDeviceOwnerApp(packageName)) {
            devicePolicyManager.setStatusBarDisabled(compName, true) // Disable the status bar
            Toast.makeText(this, "Device owner setup successful!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to set up as device owner", Toast.LENGTH_SHORT).show()
        }

        val btnActivate = findViewById<Button>(R.id.btnActivate)
        btnActivate.setOnClickListener {
            if (!devicePolicyManager.isAdminActive(compName)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Admin privileges required to enforce policies")
                startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN)
            } else {
                // Admin already active
                Toast.makeText(this, "Admin Already Active..", Toast.LENGTH_LONG).show()
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            val policy = FirebaseHelper.getPasswordPolicy()
            policy?.let {
                applyPasswordPolicy(it)
            }
        }
    }

    private fun applyPasswordPolicy(policy: PasswordPolicy) {
        if (devicePolicyManager.isAdminActive(compName)) {
            policy.min_length?.let { devicePolicyManager.setPasswordMinimumLength(compName, it) }
            devicePolicyManager.setPasswordQuality(compName, if (policy.require_special_chars == true) {
                DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC
            } else {
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC
            })

            // Check password expiration
            val lastChange = sharedPreferences.getLong("last_password_change", 0L)
            val currentTime = System.currentTimeMillis()
            val expirationMillis = (policy.expiration_days ?: 30) * 24 * 60 * 60 * 1000L

            if (lastChange != 0L && (currentTime - lastChange) > expirationMillis) {
                runOnUiThread {
                    Toast.makeText(this, "Password expired. Please change your password.", Toast.LENGTH_LONG).show()
                    devicePolicyManager.resetPassword("new_secure_password", 0)
                    sharedPreferences.edit().putLong("last_password_change", currentTime).apply()
                }
            } else {
                // Update last password change time if not set
                if (lastChange == 0L) {
                    sharedPreferences.edit().putLong("last_password_change", currentTime).apply()
                }
            }
        }
    }

    companion object {
        const val REQUEST_CODE_ENABLE_ADMIN = 1
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_ENABLE_ADMIN) {
            if (devicePolicyManager.isAdminActive(compName)) {
                // Admin enabled
                Toast.makeText(this, "Device admin enabled successfully.", Toast.LENGTH_SHORT).show()
                Log.d("Neha's Log-MainActivity", "Device admin enabled successfully.")

            } else {
                // Admin not enabled
                Toast.makeText(this, "Failed to enable device admin. Some features may not work.", Toast.LENGTH_SHORT).show()
                Log.e("Neha's Log-MainActivity", "Failed to enable device admin.")

            }
        }
    }

    private fun pinApp() {
        if (devicePolicyManager.isAdminActive(compName)) { // Check if admin is active
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                if (activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
                    startLockTask()
                    Toast.makeText(this, "App pinned", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "App is already pinned", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "App pinning not supported on this device", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Admin privileges required to pin the app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun unpinApp() {
        if (devicePolicyManager.isAdminActive(compName)) { // Check if admin is active
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                stopLockTask()
                Toast.makeText(this, "App unpinned", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "App pinning not supported on this device", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Admin privileges required to unpin the app", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) {
                // Disable back press while in lock task mode
                Toast.makeText(this, "Back button disabled in pinned mode", Toast.LENGTH_SHORT).show()
            } else {
                super.onBackPressed()
            }
        } else {
            super.onBackPressed()
        }
    }


    private fun lockDevice() {
        if (devicePolicyManager.isAdminActive(compName)) {
            devicePolicyManager.lockNow()
            Toast.makeText(this, "Device locked", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Admin not active", Toast.LENGTH_SHORT).show()
        }
    }

    private fun unlockDevice() {
        // Unlocking programmatically is restricted for security reasons.
        // Users must unlock the device manually.
        Toast.makeText(this, "Please unlock the device manually", Toast.LENGTH_SHORT).show()
    }
}