package com.angmarx.bounceblade.game.model

data class NormalizedPoint(val x: Float, val y: Float) {
    init {
        require(x in 0f..1f) { "x must be normalized to 0..1" }
        require(y in 0f..1f) { "y must be normalized to 0..1" }
    }
}

enum class HazardType { SPIKES, LASER, MOVING_HAZARD, STICKY_WALL, TELEPORTER, POWER_UP, MOVING_SPIKES, ROTATING_LASER, GRAVITY_WELL, CONVEYOR_BELT, BREAKABLE_WALL, SWITCH, KEY, DOOR }

data class HazardDefinition(
    val type: HazardType,
    val position: NormalizedPoint,
    val width: Float,
    val height: Float
)

data class LevelDefinition(
    val id: Int,
    val name: String,
    val maxBounces: Int,
    val playerSpawn: NormalizedPoint,
    val exit: NormalizedPoint,
    val switch: NormalizedPoint,
    val enemies: List<NormalizedPoint> = emptyList(),
    val crates: List<NormalizedPoint> = emptyList(),
    val hazards: List<HazardDefinition> = emptyList(),
    val requiresAllTargets: Boolean = true,
    val requiresSwitch: Boolean = true
) {
    init {
        require(id > 0) { "Level id must be positive" }
        require(maxBounces > 0) { "A level must permit at least one bounce" }
    }
}
