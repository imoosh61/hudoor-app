package com.imoosh.hudoor.loc

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.net.wifi.WifiManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.location.CurrentLocationRequest
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Loc {

    /** يطلب موقعاً جديداً (لا يقبل موقعاً مخزّناً قديماً) */
    @SuppressLint("MissingPermission")
    suspend fun fresh(ctx: Context): Location? = suspendCoroutine { cont ->
        val client = LocationServices.getFusedLocationProviderClient(ctx)
        val req = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(20000)
            .setMaxUpdateAgeMillis(0)       // مهم: يمنع استخدام موقع قديم مخزّن
            .build()
        try {
            client.getCurrentLocation(req, null)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        } catch (e: Exception) {
            cont.resume(null)
        }
    }

    /** المسافة بالأمتار — صيغة هافرساين */
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    @SuppressLint("MissingPermission", "HardwareIds")
    fun currentBssid(ctx: Context): String {
        return try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wm.connectionInfo?.bssid ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
