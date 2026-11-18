package com.imoosh.hudoor.data

import android.content.Context
import android.content.SharedPreferences

class Prefs(ctx: Context) {
    private val sp: SharedPreferences =
        ctx.getSharedPreferences("hudoor", Context.MODE_PRIVATE)

    var email: String
        get() = sp.getString("email", "") ?: ""
        set(v) = sp.edit().putString("email", v).apply()

    var password: String
        get() = sp.getString("pw", "") ?: ""
        set(v) = sp.edit().putString("pw", v).apply()

    var uid: String
        get() = sp.getString("uid", "") ?: ""
        set(v) = sp.edit().putString("uid", v).apply()

    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) = sp.edit().putString("name", v).apply()

    /** هل الموظف مسجّل حضور حالياً؟ */
    var isOnShift: Boolean
        get() = sp.getBoolean("onShift", false)
        set(v) = sp.edit().putBoolean("onShift", v).apply()

    /** معرّف سجل الحضور الحالي */
    var openSessionId: String
        get() = sp.getString("sessionId", "") ?: ""
        set(v) = sp.edit().putString("sessionId", v).apply()

    /** دقيقة نهاية الدوام لليوم (من منتصف الليل) */
    var shiftEndMin: Int
        get() = sp.getInt("shiftEndMin", 0)
        set(v) = sp.edit().putInt("shiftEndMin", v).apply()

    /** آخر حالة: داخل النطاق أو خارجه — لمنع تكرار الإشعار */
    var wasInside: Boolean
        get() = sp.getBoolean("wasInside", true)
        set(v) = sp.edit().putBoolean("wasInside", v).apply()

    val isLoggedIn: Boolean get() = email.isNotEmpty() && password.isNotEmpty()

    fun logout() = sp.edit().clear().apply()
}
