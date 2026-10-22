package com.angmarx.bounceblade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class GameRulesTest {
    @Test fun allCampaignWorldsAndStagesAreMapped() {
        assertEquals(0, Campaign.world(1)); assertEquals(0, Campaign.world(10))
        assertEquals(1, Campaign.world(11)); assertEquals(4, Campaign.world(50))
        assertEquals(50, Campaign.COUNT)
    }
    @Test fun enemyCountScalesWithCampaign() {
        assertTrue(Campaign.enemyCount(1) >= 3)
        assertTrue(Campaign.enemyCount(50) <= 18)
        assertTrue(Campaign.enemyCount(50) >= Campaign.enemyCount(1))
    }
    @Test fun starAwardUsesLivesAndCombo() {
        assertEquals(3, Campaign.stars(3, 3))
        assertEquals(2, Campaign.stars(2, 1))
        assertEquals(1, Campaign.stars(1, 9))
    }
}
