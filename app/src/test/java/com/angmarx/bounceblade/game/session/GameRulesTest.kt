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
    fun performanceStarsRewardFastClearAndCombo() {
        assertEquals(3,GameRules.starsEarnedByPerformance(18f,24f,true,0))
        assertEquals(3,GameRules.starsEarnedByPerformance(18f,24f,false,3))
        assertEquals(2,GameRules.starsEarnedByPerformance(18f,24f,false,1))
        assertEquals(1,GameRules.starsEarnedByPerformance(30f,24f,true,5))
    }
    @Test fun deadlineIsLongerThanStarTarget(){assertTrue(GameRules.timeLimitSeconds(25,2)>GameRules.targetTimeSeconds(25,2))}

    @Test
    fun invalidMaximumDoesNotCrashScoring() {
        assertEquals(1, GameRules.starsEarned(0, 0, 1))
    }

    @Test
    fun difficultyChangesBounceBudget() {
        assertEquals(15, GameRules.bouncesForDifficulty(10, 1))
        assertEquals(10, GameRules.bouncesForDifficulty(10, 2))
        assertEquals(7, GameRules.bouncesForDifficulty(10, 3))
    }

    @Test
    fun difficultyInputIsClampedAndBudgetStaysPositive() {
        assertEquals(1, GameRules.bouncesForDifficulty(0, 2))
        assertEquals(15, GameRules.bouncesForDifficulty(10, 0))
        assertEquals(7, GameRules.bouncesForDifficulty(10, 99))
    }
}
