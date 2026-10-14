package com.angmarx.bounceblade.game.level

import android.content.Context
import com.angmarx.bounceblade.game.model.HazardDefinition
import com.angmarx.bounceblade.game.model.HazardType
import com.angmarx.bounceblade.game.model.LevelDefinition
import com.angmarx.bounceblade.game.model.NormalizedPoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Reads and validates editable JSON campaign stages from app/src/main/assets/levels/. */
class LevelRepository {
    @Throws(IOException::class)
    fun readLevelJson(context: Context, fileName: String): String {
        val safeName = fileName.substringAfterLast('/')
        require(safeName.matches(Regex("level_[0-9]{3}\\.json"))) {
            "Level filename must look like level_001.json"
        }
        return context.assets.open("levels/$safeName")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }

    fun loadAll(context: Context): List<LevelDefinition> {
        val names = context.assets.list("levels").orEmpty()
            .filter { it.matches(Regex("level_[0-9]{3}\\.json")) }
            .sorted()
        return names.mapNotNull { name ->
            try {
                parse(readLevelJson(context, name))
            } catch (_: Exception) {
                null
            }
        }.distinctBy { it.id }.sortedBy { it.id }
    }

    fun parse(json: String): LevelDefinition {
        val root = JSONObject(json)
        val spawn = point(root.optJSONObject("playerSpawn"), NormalizedPoint(0.14f, 0.80f))
        val exit = point(root.optJSONObject("exit"), NormalizedPoint(0.87f, 0.18f))
        val switch = point(root.optJSONObject("switch"), NormalizedPoint(0.78f, 0.75f))
        return LevelDefinition(
            id = root.optInt("id", 1).coerceAtLeast(1),
            name = root.optString("name", "Arena"),
            maxBounces = root.optInt("maxBounces", 8).coerceAtLeast(1),
            playerSpawn = spawn,
            exit = exit,
            switch = switch,
            enemies = points(root.optJSONArray("enemies")),
            crates = points(root.optJSONArray("crates")),
            hazards = hazards(root.optJSONArray("hazards"))
        )
    }

    private fun point(value: JSONObject?, default: NormalizedPoint): NormalizedPoint {
        if (value == null) return default
        return NormalizedPoint(
            value.optDouble("x", default.x.toDouble()).toFloat().coerceIn(0f, 1f),
            value.optDouble("y", default.y.toDouble()).toFloat().coerceIn(0f, 1f)
        )
    }

    private fun points(values: JSONArray?): List<NormalizedPoint> {
        if (values == null) return emptyList()
        val result = mutableListOf<NormalizedPoint>()
        for (i in 0 until values.length()) {
            val item = values.optJSONObject(i) ?: continue
            result.add(NormalizedPoint(
                item.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
                item.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f)
            ))
        }
        return result
    }

    private fun hazards(values: JSONArray?): List<HazardDefinition> {
        if (values == null) return emptyList()
        val result = mutableListOf<HazardDefinition>()
        for (i in 0 until values.length()) {
            val item = values.optJSONObject(i) ?: continue
            val type = try {
                HazardType.valueOf(item.optString("type", "SPIKES").uppercase())
            } catch (_: IllegalArgumentException) {
                HazardType.SPIKES
            }
            result.add(HazardDefinition(
                type = type,
                position = NormalizedPoint(
                    item.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
                    item.optDouble("y", 0.9).toFloat().coerceIn(0f, 1f)
                ),
                width = item.optDouble("width", 0.56).toFloat().coerceIn(0.01f, 1f),
                height = item.optDouble("height", 0.025).toFloat().coerceIn(0.005f, 0.25f)
            ))
        }
        return result
    }
}
