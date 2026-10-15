package com.angmarx.bounceblade.game.level

import org.junit.Assert.assertEquals
import org.junit.Test

class CampaignWorldsTest {
    @Test fun campaignHasFiveTenLevelWorlds() {
        assertEquals(50,CampaignWorlds.TOTAL_LEVELS); assertEquals(0,CampaignWorlds.indexForLevel(1)); assertEquals(0,CampaignWorlds.indexForLevel(10)); assertEquals(1,CampaignWorlds.indexForLevel(11)); assertEquals(4,CampaignWorlds.indexForLevel(50)); assertEquals(41,CampaignWorlds.firstLevelForWorld(4))
    }
    @Test fun worldNamesMatchDesign() { assertEquals("Neon Grid",CampaignWorlds.worldName(0)); assertEquals("Golden Core",CampaignWorlds.worldName(4)) }
}
