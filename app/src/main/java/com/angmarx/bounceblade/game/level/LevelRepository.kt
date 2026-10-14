package com.angmarx.bounceblade.game.level

import android.content.Context
import java.io.IOException

/** Loads raw level JSON from app/src/main/assets/levels/. JSON parsing can be added here. */
class LevelRepository {
    @Throws(IOException::class)
    fun readLevelJson(context: Context, fileName: String): String {
        val safeName = fileName.substringAfterLast('/')
        require(safeName.endsWith(".json", ignoreCase = true)) {
            "Level files must use the .json extension"
        }
        return context.assets.open("levels/$safeName")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }
}
