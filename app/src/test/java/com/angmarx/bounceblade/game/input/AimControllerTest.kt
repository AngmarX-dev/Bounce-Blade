package com.angmarx.bounceblade.game.input

import com.angmarx.bounceblade.game.physics.PhysicsPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AimControllerTest {

    private val controller = AimController(
        pixelsPerVelocityUnit = 10f,
        minimumDragDistance = 20f,
        maximumDragDistance = 100f,
        maximumLaunchSpeed = 10f
    )

    @Test
    fun calculate_rejectsDragShorterThanMinimum() {
        val result = controller.calculate(
            bladePosition = PhysicsPoint(100f, 100f),
            fingerPosition = PhysicsPoint(90f, 100f)
        )

        assertFalse(result.canLaunch)
        assertEquals(0f, result.launchVelocity.x, 0.001f)
        assertEquals(0f, result.launchVelocity.y, 0.001f)
    }

    @Test
    fun calculate_aimsOppositeTheBackwardDrag() {
        val result = controller.calculate(
            bladePosition = PhysicsPoint(100f, 100f),
            fingerPosition = PhysicsPoint(50f, 100f)
        )

        assertTrue(result.canLaunch)
        assertEquals(5f, result.launchVelocity.x, 0.001f)
        assertEquals(0f, result.launchVelocity.y, 0.001f)
    }

    @Test
    fun calculate_capsLaunchSpeedForLongDrag() {
        val result = controller.calculate(
            bladePosition = PhysicsPoint(200f, 100f),
            fingerPosition = PhysicsPoint(0f, 100f)
        )

        assertTrue(result.canLaunch)
        assertEquals(10f, result.launchVelocity.x, 0.001f)
        assertEquals(0f, result.launchVelocity.y, 0.001f)
    }

    @Test
    fun calculate_supportsDiagonalAim() {
        val result = controller.calculate(
            bladePosition = PhysicsPoint(100f, 100f),
            fingerPosition = PhysicsPoint(70f, 60f)
        )

        assertTrue(result.canLaunch)
        assertTrue(result.launchVelocity.x > 0f)
        assertTrue(result.launchVelocity.y > 0f)

        val speed = kotlin.math.sqrt(
            result.launchVelocity.x * result.launchVelocity.x +
                result.launchVelocity.y * result.launchVelocity.y
        )
        assertEquals(5f, speed, 0.001f)
    }
}
