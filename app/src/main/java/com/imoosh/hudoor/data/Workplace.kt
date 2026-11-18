package com.imoosh.hudoor.data

import org.json.JSONObject
import java.util.Calendar

/** إعدادات موقع العمل — تُقرأ من Firestore: config/workplace */
data class Workplace(
    val lat: Double,
    val lng: Double,
    val radius: Double,          // نصف قطر النطاق بالأمتار
    val shiftStart: Int,         // دقائق من منتصف الليل (مثال 480 = 8:00)
    val shiftEnd: Int,           // مثال 960 = 16:00
    val workDays: String,        // أرقام أيام مفصولة بفاصلة، الأحد=1 ... السبت=7
    val graceMinutes: Int,       // سماح التأخير بالدقائق
    val maxAccuracy: Double,     // أقصى هامش خطأ مقبول للـ GPS بالأمتار
    val exitBuffer: Double,      // مسافة إضافية قبل اعتباره خارج النطاق
    val tgToken: String,         // توكن بوت تيليجرام
    val tgChat: String,          // معرّف محادثة المدير
    val requireWifi: Boolean,    // إلزام شبكة المكتب
    val wifiBssid: String,       // MAC راوتر المكتب
    val blockRoot: Boolean       // منع الأجهزة المروتة
) {
    companion object {
        fun from(j: JSONObject) = Workplace(
            lat = j.optDouble("lat", 0.0),
            lng = j.optDouble("lng", 0.0),
            radius = j.optDouble("radius", 100.0),
            shiftStart = j.optLong("shiftStart", 480L).toInt(),
            shiftEnd = j.optLong("shiftEnd", 960L).toInt(),
            workDays = j.optString("workDays", "1,2,3,4,5"),
            graceMinutes = j.optLong("graceMinutes", 10L).toInt(),
            maxAccuracy = j.optDouble("maxAccuracy", 50.0),
            exitBuffer = j.optDouble("exitBuffer", 40.0),
            tgToken = j.optString("tgToken", ""),
            tgChat = j.optString("tgChat", ""),
            requireWifi = j.optBoolean("requireWifi", false),
            wifiBssid = j.optString("wifiBssid", ""),
            blockRoot = j.optBoolean("blockRoot", true)
        )
    }

    fun isWorkDayToday(): Boolean {
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) // الأحد = 1
        return workDays.split(",").map { it.trim() }.contains(dow.toString())
    }

    fun nowMinutes(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    fun isLate(): Boolean = nowMinutes() > shiftStart + graceMinutes

    fun shiftEnded(): Boolean = nowMinutes() >= shiftEnd

    fun fmt(min: Int): String =
        String.format("%02d:%02d", min / 60, min % 60)
}
