package com.angmarx.bounceblade.game.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRulesTest {
    @Test
    fun exitNeedsAllTargetsAndSwitch() {
        assertFalse(GameRules.exitIsUnlocked(1, 0, true))
        assertFalse(GameRules.exitIsUnlocked(0, 1, true))
        assertFalse(GameRules.exitIsUnlocked(0, 0, false))
        assertTrue(GameRules.exitIsUnlocked(0, 0, true))
    }

    @Test
    fun lastAllowedBounceDoesNotImmediatelyFail() {
        assertFalse(GameRules.bounceBudgetExceeded(1))
        assertFalse(GameRules.bounceBudgetExceeded(0))
        assertTrue(GameRules.bounceBudgetExceeded(-1))
    }

    @Test
    fun efficientClearAwardsThreeStars() {
        assertEquals(3, GameRules.starsEarned(10, 8, 2, 0))
    }

    @Test
    fun requiredTargetHitsAreAccountedForInThreeStarScore() {
        assertEquals(3, GameRules.starsEarned(8, 3, 2, 5))
    }

    @Test
    fun completedButLessEfficientClearAwardsTwoStars() {
        assertEquals(2, GameRules.starsEarned(10, 3, 4))
    }

    @Test
    fun exhaustedBounceBudgetAwardsOneStar() {
        assertEquals(1, GameRules.starsEarned(10, 0, 8))
    }

    @Test
    fun invalidMaximumDoesNotCrashScoring() {
        assertEquals(1, GameRules.starsEarned(0, 0, 1))
    }
}
