package com.angmarx.bounceblade.game.progression

import android.content.Context

/** Local progress storage. Cloud backup/sync can be added later. */
class ProgressRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "bounce_blade_save",
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

    fun unlockThrough(level: Int, campaignSize: Int) {
        val safeLevel = level.coerceIn(1, campaignSize.coerceAtLeast(1))
        saveHighestUnlockedLevel(safeLevel)
    }

    fun reset() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val KEY_HIGHEST_LEVEL = "unlocked_level"
    }
}
