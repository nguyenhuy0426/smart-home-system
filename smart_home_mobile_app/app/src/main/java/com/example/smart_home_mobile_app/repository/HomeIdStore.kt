package com.example.smart_home_mobile_app.repository

import android.content.Context

class HomeIdStore(context: Context) {
    private val preferences = context.getSharedPreferences("mobile_home_ids", Context.MODE_PRIVATE)

    fun load(): List<String> = preferences.getStringSet(KEY_HOME_IDS, emptySet()).orEmpty().sorted()

    fun add(homeId: String): List<String> {
        require(homeId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,127}"))) { "Invalid home ID" }
        val ids = load().toMutableSet().apply { add(homeId) }
        preferences.edit().putStringSet(KEY_HOME_IDS, ids).apply()
        return ids.sorted()
    }

    fun remove(homeId: String): List<String> {
        val ids = load().toMutableSet().apply { remove(homeId) }
        preferences.edit().putStringSet(KEY_HOME_IDS, ids).apply()
        return ids.sorted()
    }

    private companion object {
        const val KEY_HOME_IDS = "home_ids"
    }
}

