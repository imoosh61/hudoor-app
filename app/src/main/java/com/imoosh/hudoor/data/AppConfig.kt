package com.imoosh.hudoor.data

/**
 * ====== عدّل هذين السطرين فقط ======
 * خذ القيم من: Firebase Console > Project settings > General > Your apps > Web app
 */
object AppConfig {
    const val PROJECT_ID = "PUT_YOUR_PROJECT_ID_HERE"
    const val API_KEY = "PUT_YOUR_WEB_API_KEY_HERE"

    // لا تعدّل ما تحت هذا السطر
    const val FS_BASE = "https://firestore.googleapis.com/v1/projects"
    const val IDENTITY = "https://identitytoolkit.googleapis.com/v1/accounts"

    fun docUrl(path: String) =
        "$FS_BASE/$PROJECT_ID/databases/(default)/documents/$path?key=$API_KEY"

    fun commitUrl() =
        "$FS_BASE/$PROJECT_ID/databases/(default)/documents:commit?key=$API_KEY"

    fun docName(path: String) =
        "projects/$PROJECT_ID/databases/(default)/documents/$path"
}
