package com.imoosh.hudoor.loc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.imoosh.hudoor.data.Prefs

/** يعيد تشغيل المراقبة بعد إعادة تشغيل الجهاز — فإطفاء الجوال لا يفيد في التهرب */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = Prefs(context)
        if (prefs.isLoggedIn && prefs.isOnShift) {
            ShiftMonitorService.start(context)
        }
    }
}
