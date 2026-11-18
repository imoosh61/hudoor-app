package com.imoosh.hudoor.data

import android.content.Context

/**
 * بيانات ربط Firebase.
 * تُدخل مرة واحدة من شاشة الإعداد عند أول تشغيل، وتُحفظ في الجهاز.
 * لا حاجة لتعديل أي شيء في الكود.
 */
object AppConfig {

    var projectId: String = ""
        private set
    var apiKey: String = ""
        private set

    fun load(ctx: Context) {
        val p = Prefs(ctx)
        projectId = p.projectId
        apiKey = p.apiKey
    }

    fun save(ctx: Context, pid: String, key: String) {
        val p = Prefs(ctx)
        p.projectId = pid
        p.apiKey = key
        projectId = pid
        apiKey = key
    }

    val isReady: Boolean get() = projectId.isNotBlank() && apiKey.isNotBlank()

    private const val FS = "https://firestore.googleapis.com/v1/projects"
    const val IDENTITY = "https://identitytoolkit.googleapis.com/v1/accounts"

    fun docUrl(path: String) =
        "$FS/$projectId/databases/(default)/documents/$path?key=$apiKey"

    fun commitUrl() =
        "$FS/$projectId/databases/(default)/documents:commit?key=$apiKey"

    fun docName(path: String) =
        "projects/$projectId/databases/(default)/documents/$path"

    fun signInUrl() = "$IDENTITY:signInWithPassword?key=$apiKey"
}
