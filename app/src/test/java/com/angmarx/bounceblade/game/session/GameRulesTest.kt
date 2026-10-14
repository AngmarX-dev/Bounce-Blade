package com.angmarx.bounceblade.game.session

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
}
