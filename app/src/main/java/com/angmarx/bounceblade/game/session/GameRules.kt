package com.angmarx.bounceblade.game.session

/** Small pure gameplay rules that can be tested without Android UI classes. */
object GameRules {
    fun exitIsUnlocked(enemiesRemaining: Int, cratesRemaining: Int, switchActivated: Boolean): Boolean =
        enemiesRemaining == 0 && cratesRemaining == 0 && switchActivated

    /** Zero means the final permitted bounce was used; failure begins on the next bounce. */
    fun bounceBudgetExceeded(bouncesLeft: Int): Boolean = bouncesLeft < 0

    /**
     * Rates a successful run while accounting for the unavoidable collisions needed
     * to clear the targets. Extra wall rebounds and repeated launches reduce the rating.
     */
    fun starsEarned(
        maxBounces: Int,
        remainingBounces: Int,
        shotsUsed: Int,
        requiredTargetHits: Int = 0
    ): Int {
        if (maxBounces <= 0) return 1
        val safeRemaining = remainingBounces.coerceIn(0, maxBounces)
        val safeShots = shotsUsed.coerceAtLeast(0)
        val unavoidableBudget = (maxBounces - requiredTargetHits.coerceAtLeast(0)).coerceAtLeast(0)
        val threeStarFloor = ((unavoidableBudget + 1) / 2).coerceAtLeast(1)
        return when {
            safeRemaining >= threeStarFloor && safeShots <= 3 -> 3
            safeRemaining > 0 && safeShots <= 7 -> 2
            else -> 1
        }
    }
}
