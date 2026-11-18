package com.imoosh.hudoor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.imoosh.hudoor.ui.AppRoot
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.imoosh.hudoor.data.AppConfig.load(this)
        askPermissions()
        setContent { AppRoot(this) }
    }

    // ============ الصلاحيات ============

    fun askPermissions() {
        val need = mutableListOf<String>()
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION))
            need += Manifest.permission.ACCESS_FINE_LOCATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !has(Manifest.permission.POST_NOTIFICATIONS)
        ) need += Manifest.permission.POST_NOTIFICATIONS
        if (need.isNotEmpty()) permLauncher.launch(need.toTypedArray())
    }

    /** صلاحية الموقع في الخلفية تُطلب منفصلة بعد الموافقة على الأساسية */
    fun askBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            has(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            permLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        }
    }

    fun hasLocation() = has(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasBackgroundLocation() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun has(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }

    /** طلب استثناء من تحسين البطارية — ضروري لاستمرار المراقبة */
    fun askIgnoreBattery() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            openAppSettings()
        }
    }

    // ============ البصمة الحيوية ============

    fun biometricAvailable(): Boolean {
        val m = BiometricManager.from(this)
        return m.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(title: String, onOk: () -> Unit, onFail: (String) -> Unit) {
        if (!biometricAvailable()) { onOk(); return }
        val exec: Executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, exec, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onOk()
            }

            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                onFail(msg.toString())
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("تأكيد هويتك قبل تسجيل البصمة")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
        )
    }

    companion object {
        fun forceArabic() {
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags("ar")
            )
        }
    }
}
