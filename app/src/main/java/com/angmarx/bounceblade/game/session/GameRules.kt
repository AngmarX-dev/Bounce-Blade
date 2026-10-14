package com.angmarx.bounceblade.game.session

/** Small pure rules that can be tested without Android UI classes. */
object GameRules {
    fun exitIsUnlocked(enemiesRemaining: Int, cratesRemaining: Int, switchActivated: Boolean): Boolean =
        enemiesRemaining == 0 && cratesRemaining == 0 && switchActivated

    /** Zero means the final permitted bounce was used; failure begins on the next bounce. */
    fun bounceBudgetExceeded(bouncesLeft: Int): Boolean = bouncesLeft < 0
}
