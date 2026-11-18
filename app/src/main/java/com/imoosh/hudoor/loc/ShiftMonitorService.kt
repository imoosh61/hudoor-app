package com.imoosh.hudoor.loc

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.imoosh.hudoor.MainActivity
import com.imoosh.hudoor.R
import com.imoosh.hudoor.data.AppConfig
import com.imoosh.hudoor.data.Fs
import com.imoosh.hudoor.data.Prefs
import com.imoosh.hudoor.data.Telegram
import com.imoosh.hudoor.data.Workplace
import com.imoosh.hudoor.sec.Integrity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * خدمة تعمل أثناء الدوام فقط.
 * 1) تراقب موقع الموظف — إذا غادر النطاق قبل نهاية الدوام يوصل إشعار للمدير فوراً.
 * 2) ترسل "نبضة" كل دقيقتين للسيرفر — إذا أقفل التطبيق أو أطفأ الموقع تنقطع النبضة
 *    وتظهر الحالة في لوحة المتابعة كـ"انقطع الاتصال" مع إشعار.
 */
class ShiftMonitorService : Service() {

    companion object {
        const val CH_ID = "hudoor_shift"
        const val CH_ALERT = "hudoor_alert"
        const val NOTIF_ID = 7701
        const val HEARTBEAT_MS = 120_000L     // نبضة كل دقيقتين
        const val LOC_INTERVAL_MS = 60_000L   // تحديث الموقع كل دقيقة

        fun start(ctx: Context) {
            val i = Intent(ctx, ShiftMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, ShiftMonitorService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefs: Prefs
    private var wp: Workplace? = null
    private var token: String = ""
    private var lastLoc: Location? = null
    private var heartbeatJob: Job? = null
    private var outsideStreak = 0
    private var insideStreak = 0

    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { lastLoc = it; evaluate(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        channels()
        startForeground(NOTIF_ID, ongoing("جاري متابعة الدوام"))
        scope.launch { boot() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    // ============ التهيئة ============

    private suspend fun boot() {
        try {
            if (!prefs.isLoggedIn || !prefs.isOnShift) { stopSelf(); return }
            val s = Fs.signIn(prefs.email, prefs.password)
            token = s.idToken
            val cfg = Fs.getDoc("config/workplace", token) ?: run { stopSelf(); return }
            wp = Workplace.from(cfg)
            requestUpdates()
            startHeartbeat()
        } catch (e: Exception) {
            // نعيد المحاولة بعد دقيقة بدل أن نتوقف
            scope.launch {
                kotlinx.coroutines.delay(60_000)
                boot()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOC_INTERVAL_MS)
            .setMinUpdateIntervalMillis(30_000L)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            client.requestLocationUpdates(req, callback, mainLooper)
        } catch (e: Exception) {
            // الصلاحية مسحوبة — نبلّغ المدير
            scope.launch { alert("⚠️ صلاحية الموقع مسحوبة من التطبيق — لا يمكن متابعة الموظف.") }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                try { beat() } catch (e: Exception) { /* نتجاهل ونعيد */ }
                kotlinx.coroutines.delay(HEARTBEAT_MS)
            }
        }
    }

    // ============ التقييم ============

    private fun evaluate(loc: Location) {
        val w = wp ?: return

        // انتهى الدوام؟ نوقف المراقبة بهدوء بدون إشعار مغادرة
        if (w.shiftEnded()) {
            scope.launch {
                beat()
                notifyLocal("انتهى وقت الدوام", "لا تنسَ تسجيل الانصراف")
            }
            stopMonitoring()
            return
        }

        val d = Loc.distance(loc.latitude, loc.longitude, w.lat, w.lng)
        val mock = Integrity.isMock(loc)

        if (mock) {
            scope.launch {
                logEvent("mock_during_shift", loc, d)
                alert(
                    "🚨 <b>تم كشف موقع وهمي</b>\n" +
                        "الموظف: ${prefs.name.ifBlank { prefs.email }}\n" +
                        "الوقت: ${nowStr()}\n" +
                        "التطبيق رفض الموقع وسجّل المحاولة."
                )
            }
            return
        }

        val outside = d > (w.radius + w.exitBuffer)

        if (outside) {
            outsideStreak++; insideStreak = 0
        } else {
            insideStreak++; outsideStreak = 0
        }

        // نطلب قراءتين متتاليتين قبل الإشعار لتفادي الإنذار الكاذب من تشويش GPS
        if (outside && outsideStreak >= 2 && prefs.wasInside) {
            prefs.wasInside = false
            scope.launch {
                logEvent("exit", loc, d)
                alert(
                    "🔴 <b>الموظف غادر موقع العمل</b>\n" +
                        "الاسم: ${prefs.name.ifBlank { prefs.email }}\n" +
                        "الوقت: ${nowStr()}\n" +
                        "المسافة عن المقر: ${d.toInt()} متر\n" +
                        "نهاية الدوام: ${w.fmt(w.shiftEnd)}\n" +
                        "📍 ${Telegram.mapLink(loc.latitude, loc.longitude)}"
                )
                notifyLocal("أنت خارج نطاق العمل", "تم إبلاغ المسؤول. ارجع للمقر.")
            }
        } else if (!outside && insideStreak >= 2 && !prefs.wasInside) {
            prefs.wasInside = true
            scope.launch {
                logEvent("return", loc, d)
                alert(
                    "🟢 <b>الموظف رجع لموقع العمل</b>\n" +
                        "الاسم: ${prefs.name.ifBlank { prefs.email }}\n" +
                        "الوقت: ${nowStr()}"
                )
            }
        }
    }

    // ============ النبضة ============

    private suspend fun beat() {
        if (token.isBlank()) return
        val w = wp ?: return
        val loc = lastLoc
        val d = if (loc != null) Loc.distance(loc.latitude, loc.longitude, w.lat, w.lng) else -1.0
        val fields = mutableMapOf<String, Any?>(
            "uid" to prefs.uid,
            "name" to prefs.name,
            "onShift" to true,
            "inside" to (d >= 0 && d <= w.radius + w.exitBuffer),
            "distance" to d,
            "accuracy" to (loc?.accuracy?.toDouble() ?: -1.0),
            "mock" to Integrity.isMock(loc),
            "device" to Integrity.deviceLabel(),
            "shiftEnd" to w.shiftEnd,
            "battery" to battery()
        )
        if (loc != null) {
            fields["lat"] = loc.latitude
            fields["lng"] = loc.longitude
        }
        Fs.setDoc("status/${prefs.uid}", fields, listOf("lastSeen"), token)
    }

    private suspend fun logEvent(type: String, loc: Location?, d: Double) {
        if (token.isBlank()) return
        try {
            Fs.createDoc(
                collection = "events",
                fields = mapOf(
                    "uid" to prefs.uid,
                    "name" to prefs.name,
                    "type" to type,
                    "lat" to (loc?.latitude ?: 0.0),
                    "lng" to (loc?.longitude ?: 0.0),
                    "distance" to d,
                    "accuracy" to (loc?.accuracy?.toDouble() ?: -1.0),
                    "sessionId" to prefs.openSessionId,
                    "device" to Integrity.deviceLabel()
                ),
                serverTimeFields = listOf("serverTs"),
                token = token
            )
        } catch (e: Exception) {
            // لا نفقد الإشعار بسبب فشل الكتابة
        }
    }

    private fun alert(text: String) {
        val w = wp ?: return
        Telegram.send(w.tgToken, w.tgChat, text)
    }

    private fun stopMonitoring() {
        try { client.removeLocationUpdates(callback) } catch (e: Exception) { }
        heartbeatJob?.cancel()
        stopSelf()
    }

    // ============ مساعدات ============

    private fun battery(): Long = try {
        val bm = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY).toLong()
    } catch (e: Exception) { -1L }

    private fun nowStr(): String =
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())

    private fun channels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ID, "متابعة الدوام", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "تنبيهات", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun ongoing(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CH_ID)
            .setContentTitle("الدوام جاري")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notify)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun notifyLocal(title: String, body: String) {
        val n = NotificationCompat.Builder(this, CH_ALERT)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_notify)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            getSystemService(NotificationManager::class.java).notify(7702, n)
        } catch (e: Exception) { }
    }

    override fun onDestroy() {
        try { client.removeLocationUpdates(callback) } catch (e: Exception) { }
        scope.cancel()
        super.onDestroy()
    }
}
