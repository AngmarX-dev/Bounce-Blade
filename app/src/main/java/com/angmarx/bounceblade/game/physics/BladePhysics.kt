package com.angmarx.bounceblade.game.physics

import kotlin.math.abs

data class PhysicsPoint(
    val x: Float,
    val y: Float
)

data class ArenaBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init {
        require(right > left) {
            "Arena right edge must be greater than left edge"
        }
        require(bottom > top) {
            "Arena bottom edge must be greater than top edge"
        }
    }
}

data class BladeMotion(
    val position: PhysicsPoint,
    val velocity: PhysicsPoint,
    val radius: Float
)

data class PhysicsStep(
    val motion: BladeMotion,
    val wallBounces: Int
)

object BladePhysics {

    /**
     * Advances the blade by deltaSeconds and reflects it from the arena walls.
     *
     * Position is measured in the same coordinate units as ArenaBounds.
     * Velocity is measured in coordinate units per second.
     */
    fun step(
        blade: BladeMotion,
        bounds: ArenaBounds,
        deltaSeconds: Float
    ): PhysicsStep {
        require(deltaSeconds >= 0f) {
            "deltaSeconds must not be negative"
        }
        require(blade.radius >= 0f) {
            "Blade radius must not be negative"
        }

        val minX = bounds.left + blade.radius
        val maxX = bounds.right - blade.radius
        val minY = bounds.top + blade.radius
        val maxY = bounds.bottom - blade.radius

        require(minX <= maxX && minY <= maxY) {
            "Blade does not fit inside the arena"
        }

        var x = blade.position.x + blade.velocity.x * deltaSeconds
        var y = blade.position.y + blade.velocity.y * deltaSeconds
        var velocityX = blade.velocity.x
        var velocityY = blade.velocity.y
        var bounces = 0

        if (x < minX) {
            x = minX + (minX - x)
            velocityX = abs(velocityX)
            bounces++
        } else if (x > maxX) {
            x = maxX - (x - maxX)
            velocityX = -abs(velocityX)
            bounces++
        }

        if (y < minY) {
            y = minY + (minY - y)
            velocityY = abs(velocityY)
            bounces++
        } else if (y > maxY) {
            y = maxY - (y - maxY)
            velocityY = -abs(velocityY)
            bounces++
        }

        return PhysicsStep(
            motion = BladeMotion(
                position = PhysicsPoint(x, y),
                velocity = PhysicsPoint(velocityX, velocityY),
                radius = blade.radius
            ),
            wallBounces = bounces
        )
    }
}
