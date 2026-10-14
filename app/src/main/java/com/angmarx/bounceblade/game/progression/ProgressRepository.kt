package com.angmarx.bounceblade.game.progression

import android.content.Context

/** Local progress storage. Cloud backup/sync can be added later. */
class ProgressRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "bounce_blade_progress",
        Context.MODE_PRIVATE
    )

    fun highestUnlockedLevel(): Int = preferences.getInt(KEY_HIGHEST_LEVEL, 1)

    fun saveHighestUnlockedLevel(level: Int) {
        val safeLevel = level.coerceAtLeast(1)
        val current = highestUnlockedLevel()
        if (safeLevel > current) {
            preferences.edit().putInt(KEY_HIGHEST_LEVEL, safeLevel).apply()
        }
    }

    fun reset() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val KEY_HIGHEST_LEVEL = "highest_unlocked_level"
    }
}
