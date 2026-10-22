package com.angmarx.bounceblade.game.session

/** Small pure gameplay rules that can be tested without Android UI classes. */
object GameRules {
    fun exitIsUnlocked(
        enemiesRemaining: Int,
        cratesRemaining: Int,
        switchActivated: Boolean,
        requiresAllTargets: Boolean = true,
        requiresSwitch: Boolean = true
    ): Boolean =
        (!requiresAllTargets || (enemiesRemaining == 0 && cratesRemaining == 0)) &&
            (!requiresSwitch || switchActivated)

    /** Zero means the final permitted bounce was used; failure begins on the next bounce. */
    fun bounceBudgetExceeded(bouncesLeft: Int): Boolean = bouncesLeft < 0

    /** Adjusts the bounce budget for the one-to-three-star difficulty modes. */
    fun bouncesForDifficulty(baseBounces: Int, difficultyStars: Int): Int {
        val safeBase = baseBounces.coerceAtLeast(1)
        return when (difficultyStars.coerceIn(1, 3)) {
            1 -> (safeBase * 3 + 1) / 2 // Easy: 50% more bounces, rounded up.
            2 -> safeBase
            else -> (safeBase * 7 / 10).coerceAtLeast(1) // Hard: 70% of the normal budget.
        }
    }

    /** Star score: 1 for a clear, 2 for a fast clear, 3 for a fast full-clear/combo. */
    fun starsEarnedByPerformance(elapsedSeconds: Float, targetSeconds: Float, allTargetsDestroyed: Boolean, bestCombo: Int): Int {
        val elapsed = if (elapsedSeconds.isFinite()) elapsedSeconds.coerceAtLeast(0f) else Float.MAX_VALUE
        val target = if (targetSeconds.isFinite()) targetSeconds.coerceAtLeast(0f) else 0f
        if (elapsed > target) return 1
        return if (allTargetsDestroyed || bestCombo >= 3) 3 else 2
    }

    fun targetTimeSeconds(levelId: Int, difficultyStars: Int): Float {
        val id = levelId.coerceAtLeast(1)
        val world = (id - 1) / 10
        val local = (id - 1) % 10
        return (32f - world * 1.4f - local * 0.25f - (difficultyStars.coerceIn(1, 3) - 1) * 2f).coerceAtLeast(16f)
    }

    fun timeLimitSeconds(levelId: Int, difficultyStars: Int): Float = targetTimeSeconds(levelId, difficultyStars) + 24f

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
