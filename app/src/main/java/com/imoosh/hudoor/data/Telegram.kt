package com.imoosh.hudoor.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** إشعارات فورية للمدير عبر بوت تيليجرام */
object Telegram {

    fun send(token: String, chatId: String, text: String): Boolean {
        if (token.isBlank() || chatId.isBlank()) return false
        return try {
            val url = URL("https://api.telegram.org/bot$token/sendMessage")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connectTimeout = 12000
                readTimeout = 12000
            }
            val body = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
                put("parse_mode", "HTML")
                put("disable_web_page_preview", true)
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val ok = conn.responseCode in 200..299
            conn.disconnect()
            ok
        } catch (e: Exception) {
            false
        }
    }

    fun mapLink(lat: Double, lng: Double) = "https://maps.google.com/?q=$lat,$lng"
}
