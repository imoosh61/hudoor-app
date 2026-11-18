package com.imoosh.hudoor.sec

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.provider.Settings
import java.io.File
import java.security.MessageDigest

/**
 * طبقات كشف التلاعب.
 * ملاحظة مهمة: لا يوجد كشف مضمون 100% على جهاز يملكه المستخدم،
 * لكن تجميع هذه الطبقات مع تسجيل كل محاولة في السيرفر يجعل التلاعب مكشوفاً.
 */
object Integrity {

    /** تطبيقات تزييف الموقع المعروفة */
    private val FAKE_GPS_PACKAGES = listOf(
        "com.lexa.fakegps",
        "com.incorporateapps.fakegps.fre",
        "com.incorporateapps.fakegps",
        "com.blogspot.newapphorizons.fakegps",
        "com.theappninjas.fakegpsjoystick",
        "com.rosteam.gpsemulator",
        "com.gsmartstudio.fakegps",
        "net.marlove.mockgps",
        "com.fakegps.mock",
        "ru.gavrikov.mocklocations",
        "com.evezzon.fakegps",
        "com.hola.fakegps",
        "appinventor.ai_zmaster.FakeGPS"
    )

    /** مؤشرات الروت */
    private val ROOT_PATHS = listOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/su/bin/su",
        "/system/xbin/daemonsu",
        "/system/bin/magisk",
        "/sbin/magisk"
    )

    private val ROOT_PACKAGES = listOf(
        "com.topjohnwu.magisk",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.noshufou.android.su",
        "com.thirdparty.superuser",
        "com.yellowes.su"
    )

    data class Report(
        val mockLocation: Boolean,
        val mockAppsInstalled: List<String>,
        val devOptionsOn: Boolean,
        val rooted: Boolean,
        val emulator: Boolean
    ) {
        val clean: Boolean
            get() = !mockLocation && mockAppsInstalled.isEmpty() && !rooted && !emulator

        /** سبب الرفض بالعربي، أو null إذا كان الجهاز سليماً */
        fun blockReason(blockRoot: Boolean): String? = when {
            mockLocation -> "تم كشف موقع وهمي (Fake GPS). البصمة مرفوضة وتم تسجيل المحاولة."
            mockAppsInstalled.isNotEmpty() ->
                "يوجد تطبيق تزييف موقع مثبّت على الجهاز: ${mockAppsInstalled.first()}. احذفه ثم أعد المحاولة."
            emulator -> "التطبيق لا يعمل على المحاكيات."
            rooted && blockRoot -> "الجهاز مروت (Root). البصمة مرفوضة."
            else -> null
        }
    }

    fun check(ctx: Context, loc: Location?): Report = Report(
        mockLocation = isMock(loc),
        mockAppsInstalled = installedFakeGps(ctx),
        devOptionsOn = devOptions(ctx),
        rooted = isRooted(),
        emulator = isEmulator()
    )

    @Suppress("DEPRECATION")
    fun isMock(loc: Location?): Boolean {
        if (loc == null) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) loc.isMock
        else loc.isFromMockProvider
    }

    private fun installedFakeGps(ctx: Context): List<String> {
        val pm = ctx.packageManager
        val found = mutableListOf<String>()
        for (p in FAKE_GPS_PACKAGES) {
            try {
                pm.getPackageInfo(p, 0)
                found.add(p)
            } catch (e: PackageManager.NameNotFoundException) {
                // غير مثبّت
            }
        }
        return found
    }

    @SuppressLint("HardwareIds")
    private fun devOptions(ctx: Context): Boolean = try {
        Settings.Secure.getInt(
            ctx.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
        ) != 0
    } catch (e: Exception) {
        false
    }

    private fun isRooted(): Boolean {
        if (ROOT_PATHS.any { File(it).exists() }) return true
        val tags = Build.TAGS
        if (tags != null && tags.contains("test-keys")) return true
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val out = p.inputStream.bufferedReader().readText()
            p.destroy()
            out.isNotBlank()
        } catch (e: Exception) {
            false
        }
    }

    fun rootApps(ctx: Context): List<String> {
        val pm = ctx.packageManager
        return ROOT_PACKAGES.filter {
            try { pm.getPackageInfo(it, 0); true } catch (e: Exception) { false }
        }
    }

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("vbox") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.PRODUCT == "sdk" ||
            Build.PRODUCT == "google_sdk" ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu")

    /** بصمة الجهاز — تُربط بالموظف فلا يقدر يبصم من جهاز ثاني */
    @SuppressLint("HardwareIds")
    fun deviceId(ctx: Context): String {
        val raw = buildString {
            append(Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "")
            append("|").append(Build.MANUFACTURER)
            append("|").append(Build.MODEL)
            append("|").append(Build.BOARD)
        }
        val md = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return md.joinToString("") { "%02x".format(it) }.take(32)
    }

    fun deviceLabel(): String = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
}
