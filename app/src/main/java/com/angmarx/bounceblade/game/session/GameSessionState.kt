package com.angmarx.bounceblade.game.session

enum class GameSessionState {
    READY,
    AIMING,
    IN_FLIGHT,
    LEVEL_COMPLETE,
    FAILED,
    PAUSED
}

data class GameSessionSnapshot(
    val levelId: Int,
    val state: GameSessionState = GameSessionState.READY,
    val remainingBounces: Int = 5,
    val slowMeter: Float = 1f
) {
    init {
        require(levelId > 0)
        require(remainingBounces >= 0)
        require(slowMeter in 0f..1f)
    }
}
