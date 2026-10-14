package com.angmarx.bounceblade.game.input

import com.angmarx.bounceblade.game.physics.PhysicsPoint
import kotlin.math.sqrt

data class AimResult(
    val launchVelocity: PhysicsPoint,
    val dragDistance: Float,
    val canLaunch: Boolean
)

class AimController(
    private val pixelsPerVelocityUnit: Float = 8f,
    private val minimumDragDistance: Float = 20f,
    private val maximumDragDistance: Float = 180f,
    private val maximumLaunchSpeed: Float = 900f
) {
    init {
        require(pixelsPerVelocityUnit > 0f)
        require(minimumDragDistance >= 0f)
        require(maximumDragDistance >= minimumDragDistance)
        require(maximumLaunchSpeed > 0f)
    }

    /**
     * The player drags backward from the blade. The launch direction points
     * from the current finger position toward the original blade position.
     */
    fun calculate(
        bladePosition: PhysicsPoint,
        fingerPosition: PhysicsPoint
    ): AimResult {
        val dx = bladePosition.x - fingerPosition.x
        val dy = bladePosition.y - fingerPosition.y
        val distance = sqrt(dx * dx + dy * dy)

        if (distance < minimumDragDistance || distance == 0f) {
            return AimResult(
                launchVelocity = PhysicsPoint(0f, 0f),
                dragDistance = distance,
                canLaunch = false
            )
        }

        val cappedDistance = distance.coerceAtMost(maximumDragDistance)
        val speed = (cappedDistance / pixelsPerVelocityUnit)
            .coerceAtMost(maximumLaunchSpeed)

        val scale = speed / distance

        return AimResult(
            launchVelocity = PhysicsPoint(dx * scale, dy * scale),
            dragDistance = distance,
            canLaunch = true
        )
    }
}
