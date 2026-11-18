package com.imoosh.hudoor.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * عميل Firestore عبر REST API.
 * الوقت يُكتب دائماً بوقت السيرفر (REQUEST_TIME) ولا يُقبل وقت الجهاز نهائياً.
 */
object Fs {

    class FsException(message: String) : Exception(message)

    // ============ المصادقة ============

    data class Session(val idToken: String, val uid: String, val email: String)

    fun signIn(email: String, password: String): Session {
        val body = JSONObject().apply {
            put("email", email)
            put("password", password)
            put("returnSecureToken", true)
        }
        val res = post("${AppConfig.IDENTITY}:signInWithPassword?key=${AppConfig.API_KEY}", body, null)
        return Session(
            idToken = res.getString("idToken"),
            uid = res.getString("localId"),
            email = res.optString("email", email)
        )
    }

    // ============ قراءة مستند ============

    fun getDoc(path: String, token: String): JSONObject? {
        val conn = (URL(AppConfig.docUrl(path)).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 15000
            readTimeout = 15000
        }
        return try {
            if (conn.responseCode == 404) return null
            if (conn.responseCode >= 400) throw FsException(readErr(conn))
            val raw = JSONObject(read(conn))
            flatten(raw.optJSONObject("fields") ?: JSONObject())
        } finally {
            conn.disconnect()
        }
    }

    // ============ كتابة بصمة (لا يمكن تعديلها أو حذفها لاحقاً) ============

    /**
     * ينشئ مستنداً جديداً بمعرّف عشوائي. الحقل serverTs يُملأ بوقت سيرفر Firestore.
     */
    fun createDoc(
        collection: String,
        fields: Map<String, Any?>,
        serverTimeFields: List<String>,
        token: String
    ): String {
        val id = UUID.randomUUID().toString()
        val docPath = "$collection/$id"

        val writes = JSONArray()

        writes.put(JSONObject().apply {
            put("update", JSONObject().apply {
                put("name", AppConfig.docName(docPath))
                put("fields", toFsFields(fields))
            })
            // currentDocument.exists=false يمنع الكتابة فوق مستند قائم
            put("currentDocument", JSONObject().put("exists", false))
        })

        if (serverTimeFields.isNotEmpty()) {
            val transforms = JSONArray()
            serverTimeFields.forEach {
                transforms.put(
                    JSONObject().put("fieldPath", it).put("setToServerValue", "REQUEST_TIME")
                )
            }
            writes.put(JSONObject().apply {
                put("transform", JSONObject().apply {
                    put("document", AppConfig.docName(docPath))
                    put("fieldTransforms", transforms)
                })
            })
        }

        post(AppConfig.commitUrl(), JSONObject().put("writes", writes), token)
        return id
    }

    /**
     * يحدّث/ينشئ مستنداً بمعرّف ثابت (يُستخدم لحالة النبضة الحيّة فقط).
     */
    fun setDoc(
        path: String,
        fields: Map<String, Any?>,
        serverTimeFields: List<String>,
        token: String
    ) {
        val writes = JSONArray()
        writes.put(JSONObject().apply {
            put("update", JSONObject().apply {
                put("name", AppConfig.docName(path))
                put("fields", toFsFields(fields))
            })
            put("updateMask", JSONObject().put("fieldPaths", JSONArray(fields.keys.toList())))
        })
        if (serverTimeFields.isNotEmpty()) {
            val transforms = JSONArray()
            serverTimeFields.forEach {
                transforms.put(
                    JSONObject().put("fieldPath", it).put("setToServerValue", "REQUEST_TIME")
                )
            }
            writes.put(JSONObject().apply {
                put("transform", JSONObject().apply {
                    put("document", AppConfig.docName(path))
                    put("fieldTransforms", transforms)
                })
            })
        }
        post(AppConfig.commitUrl(), JSONObject().put("writes", writes), token)
    }

    // ============ تحويل الأنواع ============

    private fun toFsFields(map: Map<String, Any?>): JSONObject {
        val out = JSONObject()
        for ((k, v) in map) out.put(k, toFsValue(v))
        return out
    }

    private fun toFsValue(v: Any?): JSONObject = when (v) {
        null -> JSONObject().put("nullValue", JSONObject.NULL)
        is Boolean -> JSONObject().put("booleanValue", v)
        is Int -> JSONObject().put("integerValue", v.toString())
        is Long -> JSONObject().put("integerValue", v.toString())
        is Double -> JSONObject().put("doubleValue", v)
        is Float -> JSONObject().put("doubleValue", v.toDouble())
        else -> JSONObject().put("stringValue", v.toString())
    }

    private fun flatten(fields: JSONObject): JSONObject {
        val out = JSONObject()
        val keys = fields.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val holder = fields.getJSONObject(k)
            val type = holder.keys().next()
            val value: Any? = when (type) {
                "integerValue" -> holder.getString(type).toLongOrNull() ?: 0L
                "doubleValue" -> holder.getDouble(type)
                "booleanValue" -> holder.getBoolean(type)
                "nullValue" -> null
                else -> holder.get(type)
            }
            if (value == null) out.put(k, JSONObject.NULL) else out.put(k, value)
        }
        return out
    }

    // ============ HTTP ============

    private fun post(url: String, body: JSONObject, token: String?): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
            connectTimeout = 15000
            readTimeout = 20000
        }
        return try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode >= 400) throw FsException(readErr(conn))
            val txt = read(conn)
            if (txt.trim().startsWith("[")) JSONObject().put("array", JSONArray(txt))
            else JSONObject(txt)
        } finally {
            conn.disconnect()
        }
    }

    private fun read(conn: HttpURLConnection): String =
        BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }

    private fun readErr(conn: HttpURLConnection): String {
        val raw = try {
            BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream, Charsets.UTF_8))
                .use { it.readText() }
        } catch (e: Exception) {
            "HTTP ${conn.responseCode}"
        }
        return try {
            JSONObject(raw).optJSONObject("error")?.optString("message") ?: raw
        } catch (e: Exception) {
            raw
        }
    }
}
