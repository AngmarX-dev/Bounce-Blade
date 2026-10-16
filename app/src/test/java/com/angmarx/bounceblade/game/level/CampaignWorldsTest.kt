package com.angmarx.bounceblade.game.level

import org.junit.Assert.assertEquals
import org.junit.Test

class CampaignWorldsTest {
    @Test fun campaignHasTenTenLevelWorlds() {
        assertEquals(100, CampaignWorlds.TOTAL_LEVELS)
        assertEquals(0, CampaignWorlds.indexForLevel(1))
        assertEquals(0, CampaignWorlds.indexForLevel(10))
        assertEquals(1, CampaignWorlds.indexForLevel(11))
        assertEquals(4, CampaignWorlds.indexForLevel(50))
        assertEquals(9, CampaignWorlds.indexForLevel(100))
        assertEquals(91, CampaignWorlds.firstLevelForWorld(9))
    }
    @Test fun worldNamesMatchDesign() {
        assertEquals("Neon Grid", CampaignWorlds.worldName(0))
        assertEquals("Golden Core", CampaignWorlds.worldName(4))
        assertEquals("Obsidian Rift", CampaignWorlds.worldName(5))
        assertEquals("Celestial Forge", CampaignWorlds.worldName(9))
    }
}
