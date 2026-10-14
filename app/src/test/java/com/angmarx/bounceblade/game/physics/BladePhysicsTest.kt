package com.angmarx.bounceblade.game.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BladePhysicsTest {
    @Test
    fun advanceReflectsAtRightBoundary() {
        val position = MutableVector2(0.91f, 0.5f)
        val velocity = MutableVector2(1f, 0f)

        val bounced = BladePhysics.advance(position, velocity, 0.2f)

        assertTrue(bounced)
        assertEquals(0.92f, position.x, 0.0001f)
        assertEquals(-1f, velocity.x, 0.0001f)
    }

    @Test
    fun nonPositiveDeltaDoesNotMoveBlade() {
        val position = MutableVector2(0.4f, 0.6f)
        val velocity = MutableVector2(1f, -1f)

        val bounced = BladePhysics.advance(position, velocity, 0f)

        assertFalse(bounced)
        assertEquals(0.4f, position.x, 0.0001f)
        assertEquals(0.6f, position.y, 0.0001f)
    }

    @Test
    fun distanceSquaredAvoidsSquareRoot() {
        val a = MutableVector2(0f, 0f)
        val b = MutableVector2(3f, 4f)

        assertEquals(25f, BladePhysics.distanceSquared(a, b), 0.0001f)
    }
}
