package com.angmarx.bounceblade.game.session

/** Small pure gameplay rules that can be tested without Android UI classes. */
object GameRules {
    fun exitIsUnlocked(enemiesRemaining: Int, cratesRemaining: Int, switchActivated: Boolean): Boolean =
        enemiesRemaining == 0 && cratesRemaining == 0 && switchActivated

    /** Zero means the final permitted bounce was used; failure begins on the next bounce. */
    fun bounceBudgetExceeded(bouncesLeft: Int): Boolean = bouncesLeft < 0

    /**
     * Awards three stars for a controlled, efficient clear; two for a successful
     * but less efficient run; and one for any other completed run.
     */
    fun starsEarned(maxBounces: Int, remainingBounces: Int, shotsUsed: Int): Int {
        if (maxBounces <= 0) return 1
        val safeRemaining = remainingBounces.coerceIn(0, maxBounces)
        val safeShots = shotsUsed.coerceAtLeast(0)
        return when {
            safeRemaining * 5 >= maxBounces * 3 && safeShots <= 3 -> 3
            safeRemaining > 0 && safeShots <= 6 -> 2
            else -> 1
        }
    }
}
