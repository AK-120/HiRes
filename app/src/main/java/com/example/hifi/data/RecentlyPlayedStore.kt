package com.example.hifi.data

import android.content.Context

/** Tiny SharedPreferences-backed list of recently played track ids (most recent first). */
class RecentlyPlayedStore(context: Context) {
    private val prefs = context.getSharedPreferences("recent", Context.MODE_PRIVATE)

    fun ids(): List<String> =
        prefs.getString(KEY, "")?.split(',')?.filter { it.isNotBlank() }.orEmpty()

    fun record(id: String) {
        val updated = (listOf(id) + ids().filter { it != id }).take(MAX)
        prefs.edit().putString(KEY, updated.joinToString(",")).apply()
    }

    private companion object {
        const val KEY = "ids"
        const val MAX = 50
    }
}
