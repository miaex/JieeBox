package com.jiee.box.data

import android.content.Context

data class BoxSettings(
    val boxName: String = "JIEE BOX",
    /** Null/blank = no password required (open access, V1 default behaviour). */
    val password: String? = null
)

/**
 * Small persisted settings store, separate from [FileRepository] since it
 * changes independently and is read once at server-start time (spec V1.2:
 * "nom personnalisé de la BOX", "mot de passe").
 */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("jiee_box_settings", Context.MODE_PRIVATE)

    fun get(): BoxSettings = BoxSettings(
        boxName = prefs.getString("box_name", null)?.takeIf { it.isNotBlank() } ?: "JIEE BOX",
        password = prefs.getString("password", null)?.takeIf { it.isNotBlank() }
    )

    fun save(settings: BoxSettings) {
        prefs.edit()
            .putString("box_name", settings.boxName.ifBlank { "JIEE BOX" })
            .putString("password", settings.password?.takeIf { it.isNotBlank() })
            .apply()
    }
}
