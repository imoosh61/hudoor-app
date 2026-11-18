package com.imoosh.hudoor.data

import android.content.Context
import com.imoosh.hudoor.loc.Loc
import com.imoosh.hudoor.sec.Integrity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** منطق البصمة — كل الفحوصات تمر من هنا */
class Punch(private val ctx: Context) {

    private val prefs = Prefs(ctx)

    sealed class Result {
        data class Ok(val message: String, val distance: Int) : Result()
        data class Blocked(val reason: String) : Result()
        data class Error(val reason: String) : Result()
    }

    data class Loaded(val wp: Workplace, val token: String, val allowEarlyOut: Boolean)

    /** تحميل الإعدادات من السيرفر */
    suspend fun load(): Loaded {
        val s = Fs.signIn(prefs.email, prefs.password)
        prefs.uid = s.uid
        val cfg = Fs.getDoc("config/workplace", s.idToken)
            ?: throw Fs.FsException("لم يتم تحديد موقع العمل بعد. راجع المسؤول.")
        val wp = Workplace.from(cfg)

        val emp = Fs.getDoc("employees/${s.uid}", s.idToken)
        if (emp != null) {
            val n = emp.optString("name", "")
            if (n.isNotBlank()) prefs.name = n
        }

        val perm = Fs.getDoc("perm/${s.uid}", s.idToken)
        val allow = perm?.optBoolean("allowEarlyOut", false) == true &&
            perm.optString("date", "") == today()

        return Loaded(wp, s.idToken, allow)
    }

    /** تسجيل حضور */
    suspend fun checkIn(): Result = punch("in")

    /** تسجيل انصراف */
    suspend fun checkOut(): Result = punch("out")

    private suspend fun punch(type: String): Result {
        val l = try { load() } catch (e: Exception) {
            return Result.Error(e.message ?: "تعذّر الاتصال بالسيرفر")
        }
        val wp = l.wp
        val token = l.token

        // 1) يوم عمل؟
        if (!wp.isWorkDayToday()) return Result.Blocked("اليوم ليس يوم عمل.")

        // 2) الانصراف ممنوع قبل نهاية الدوام
        if (type == "out" && !wp.shiftEnded() && !l.allowEarlyOut) {
            val left = wp.shiftEnd - wp.nowMinutes()
            val h = left / 60
            val m = left % 60
            val rem = if (h > 0) "$h ساعة و $m دقيقة" else "$m دقيقة"
            return Result.Blocked(
                "لا يمكن تسجيل الانصراف قبل نهاية الدوام (${wp.fmt(wp.shiftEnd)}).\n" +
                    "المتبقي: $rem.\nإذا تحتاج تغادر مبكراً اطلب استئذان من المسؤول."
            )
        }

        // 3) الموقع
        val loc = Loc.fresh(ctx)
            ?: return Result.Error("ما قدرنا نقرأ موقعك. تأكد أن GPS مفعّل واطلع لمكان مكشوف.")

        // 4) فحوصات التلاعب
        val rep = Integrity.check(ctx, loc)
        rep.blockReason(wp.blockRoot)?.let { reason ->
            runCatching {
                Fs.createDoc(
                    "events",
                    mapOf(
                        "uid" to prefs.uid, "name" to prefs.name, "type" to "cheat_attempt",
                        "detail" to reason, "lat" to loc.latitude, "lng" to loc.longitude,
                        "device" to Integrity.deviceLabel()
                    ),
                    listOf("serverTs"), token
                )
                Telegram.send(
                    wp.tgToken, wp.tgChat,
                    "🚨 <b>محاولة تلاعب بالبصمة</b>\n" +
                        "الموظف: ${prefs.name.ifBlank { prefs.email }}\n" +
                        "السبب: $reason\nالوقت: ${nowStr()}"
                )
            }
            return Result.Blocked(reason)
        }

        // 5) دقة القراءة
        if (loc.accuracy > wp.maxAccuracy) {
            return Result.Error(
                "دقة الموقع ضعيفة (±${loc.accuracy.toInt()} متر). " +
                    "انتظر ثوانٍ أو اطلع لمكان مكشوف ثم أعد المحاولة."
            )
        }

        // 6) النطاق الجغرافي
        val d = Loc.distance(loc.latitude, loc.longitude, wp.lat, wp.lng)
        if (d > wp.radius) {
            return Result.Blocked(
                "أنت خارج نطاق العمل.\nالمسافة عن المقر: ${d.toInt()} متر " +
                    "(المسموح ${wp.radius.toInt()} متر)."
            )
        }

        // 7) شبكة المكتب (إن كانت مُلزمة)
        if (wp.requireWifi && wp.wifiBssid.isNotBlank()) {
            val b = Loc.currentBssid(ctx)
            if (!b.equals(wp.wifiBssid, ignoreCase = true)) {
                return Result.Blocked("لازم تكون متصل بشبكة واي فاي المكتب لتسجيل البصمة.")
            }
        }

        // 8) ربط الجهاز
        val devId = Integrity.deviceId(ctx)
        val emp = Fs.getDoc("employees/${prefs.uid}", token)
        val bound = emp?.optString("deviceId", "") ?: ""
        if (bound.isBlank()) {
            runCatching {
                Fs.setDoc(
                    "employees/${prefs.uid}",
                    mapOf(
                        "deviceId" to devId,
                        "deviceLabel" to Integrity.deviceLabel(),
                        "email" to prefs.email
                    ),
                    listOf("boundAt"), token
                )
            }
        } else if (bound != devId) {
            runCatching {
                Telegram.send(
                    wp.tgToken, wp.tgChat,
                    "🚨 <b>محاولة بصمة من جهاز غير مسجّل</b>\n" +
                        "الموظف: ${prefs.name.ifBlank { prefs.email }}\n" +
                        "الجهاز: ${Integrity.deviceLabel()}\nالوقت: ${nowStr()}"
                )
            }
            return Result.Blocked(
                "هذا الجهاز غير مسجّل لك. البصمة تعمل من جهازك المسجّل فقط.\n" +
                    "إذا بدّلت جوالك راجع المسؤول لإعادة الربط."
            )
        }

        // 9) كتابة البصمة — الوقت وقت السيرفر، والسجل لا يُعدّل ولا يُحذف
        val late = type == "in" && wp.isLate()
        val id = try {
            Fs.createDoc(
                collection = "attendance",
                fields = mapOf(
                    "uid" to prefs.uid,
                    "name" to prefs.name,
                    "type" to type,
                    "lat" to loc.latitude,
                    "lng" to loc.longitude,
                    "accuracy" to loc.accuracy.toDouble(),
                    "distance" to d,
                    "late" to late,
                    "day" to today(),
                    "clientTime" to System.currentTimeMillis(),
                    "device" to Integrity.deviceLabel(),
                    "deviceId" to devId,
                    "devOptions" to rep.devOptionsOn,
                    "sessionId" to if (type == "in") "" else prefs.openSessionId
                ),
                serverTimeFields = listOf("serverTs"),
                token = token
            )
        } catch (e: Exception) {
            return Result.Error(e.message ?: "فشلت الكتابة في السيرفر")
        }

        // 10) تشغيل/إيقاف المراقبة
        if (type == "in") {
            prefs.isOnShift = true
            prefs.openSessionId = id
            prefs.shiftEndMin = wp.shiftEnd
            prefs.wasInside = true
            com.imoosh.hudoor.loc.ShiftMonitorService.start(ctx)
        } else {
            prefs.isOnShift = false
            prefs.openSessionId = ""
            com.imoosh.hudoor.loc.ShiftMonitorService.stop(ctx)
        }

        runCatching {
            Fs.setDoc(
                "status/${prefs.uid}",
                mapOf(
                    "uid" to prefs.uid, "name" to prefs.name,
                    "onShift" to (type == "in"),
                    "lat" to loc.latitude, "lng" to loc.longitude,
                    "inside" to true, "distance" to d, "mock" to false,
                    "device" to Integrity.deviceLabel(), "shiftEnd" to wp.shiftEnd
                ),
                listOf("lastSeen"), token
            )
            val emoji = if (type == "in") "✅" else "🏁"
            val label = if (type == "in") "تسجيل حضور" else "تسجيل انصراف"
            Telegram.send(
                wp.tgToken, wp.tgChat,
                "$emoji <b>$label</b>\n" +
                    "الموظف: ${prefs.name.ifBlank { prefs.email }}\n" +
                    "الوقت: ${nowStr()}" + (if (late) "\n⏰ متأخر" else "")
            )
        }

        val msg = if (type == "in") {
            (if (late) "تم تسجيل الحضور — مسجّل تأخير" else "تم تسجيل الحضور") +
                "\nنهاية الدوام: ${wp.fmt(wp.shiftEnd)}"
        } else {
            "تم تسجيل الانصراف. يعطيك العافية."
        }
        return Result.Ok(msg, d.toInt())
    }

    /** طلب استئذان — يوصل المسؤول على تيليجرام */
    suspend fun requestEarlyLeave(reason: String): Boolean {
        val l = runCatching { load() }.getOrNull() ?: return false
        runCatching {
            Fs.createDoc(
                "requests",
                mapOf(
                    "uid" to prefs.uid, "name" to prefs.name,
                    "reason" to reason, "status" to "pending", "day" to today()
                ),
                listOf("serverTs"), l.token
            )
        }
        return Telegram.send(
            l.wp.tgToken, l.wp.tgChat,
            "🙋 <b>طلب استئذان</b>\n" +
                "الموظف: ${prefs.name.ifBlank { prefs.email }}\n" +
                "السبب: $reason\nالوقت: ${nowStr()}\n\n" +
                "للموافقة: افتح لوحة المتابعة واضغط «أذن بالانصراف المبكر»."
        )
    }

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun nowStr(): String =
        SimpleDateFormat("HH:mm", Locale.US).format(Date())
}
