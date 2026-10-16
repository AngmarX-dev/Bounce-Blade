package com.angmarx.bounceblade.game.level

object CampaignWorlds {
    const val LEVELS_PER_WORLD = 10
    const val WORLD_COUNT = 10
    const val TOTAL_LEVELS = LEVELS_PER_WORLD * WORLD_COUNT
    private val names = listOf("Neon Grid", "Crimson Circuit", "Azure Vault", "Magenta Maze", "Golden Core", "Obsidian Rift", "Solar Flare", "Quantum Abyss", "Storm Citadel", "Celestial Forge")
    fun indexForLevel(levelId: Int): Int = ((levelId.coerceIn(1,TOTAL_LEVELS)-1)/LEVELS_PER_WORLD).coerceIn(0,WORLD_COUNT-1)
    fun firstLevelForWorld(worldIndex: Int): Int = worldIndex.coerceIn(0,WORLD_COUNT-1)*LEVELS_PER_WORLD+1
    fun worldName(worldIndex: Int): String = names[worldIndex.coerceIn(0,WORLD_COUNT-1)]
}
