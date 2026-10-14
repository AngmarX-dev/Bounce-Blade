package com.angmarx.bounceblade.game.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BladePhysicsTest {

    private val bounds = ArenaBounds(
        left = 0f,
        top = 0f,
        right = 100f,
        bottom = 100f
    )

    @Test
    fun step_movesBladeWhenItDoesNotHitAWall() {
        val blade = BladeMotion(
            position = PhysicsPoint(50f, 50f),
            velocity = PhysicsPoint(10f, -5f),
            radius = 5f
        )

        val result = BladePhysics.step(blade, bounds, 1f)

        assertEquals(60f, result.motion.position.x, 0.001f)
        assertEquals(45f, result.motion.position.y, 0.001f)
        assertEquals(0, result.wallBounces)
    }

    @Test
    fun step_reflectsBladeFromRightWall() {
        val blade = BladeMotion(
            position = PhysicsPoint(90f, 50f),
            velocity = PhysicsPoint(10f, 0f),
            radius = 5f
        )

        val result = BladePhysics.step(blade, bounds, 1f)

        assertEquals(90f, result.motion.position.x, 0.001f)
        assertEquals(-10f, result.motion.velocity.x, 0.001f)
        assertEquals(1, result.wallBounces)
    }

    @Test
    fun step_countsTwoBouncesAtCorner() {
        val blade = BladeMotion(
            position = PhysicsPoint(90f, 90f),
            velocity = PhysicsPoint(10f, 10f),
            radius = 5f
        )

        val result = BladePhysics.step(blade, bounds, 1f)

        assertEquals(90f, result.motion.position.x, 0.001f)
        assertEquals(90f, result.motion.position.y, 0.001f)
        assertEquals(-10f, result.motion.velocity.x, 0.001f)
        assertEquals(-10f, result.motion.velocity.y, 0.001f)
        assertEquals(2, result.wallBounces)
    }

    @Test
    fun step_rejectsNegativeDeltaTime() {
        val blade = BladeMotion(
            position = PhysicsPoint(50f, 50f),
            velocity = PhysicsPoint(1f, 1f),
            radius = 5f
        )

        assertThrows(IllegalArgumentException::class.java) {
            BladePhysics.step(blade, bounds, -0.1f)
        }
    }
}
