package com.angmarx.bounceblade.game.physics

import kotlin.math.max

data class MutableVector2(var x: Float, var y: Float)

data class PhysicsBounds(
    val minX: Float = 0.08f,
    val maxX: Float = 0.92f,
    val minY: Float = 0.085f,
    val maxY: Float = 0.87f
)

/** Small reusable physics helpers; connect these to the game loop when refactoring it. */
object BladePhysics {
    /**
     * Advances the point and reflects its velocity at the rectangular bounds.
     * Returns true if at least one boundary was hit.
     */
    fun advance(
        position: MutableVector2,
        velocity: MutableVector2,
        deltaSeconds: Float,
        bounds: PhysicsBounds = PhysicsBounds()
    ): Boolean {
        if (deltaSeconds <= 0f) return false

        position.x += velocity.x * deltaSeconds
        position.y += velocity.y * deltaSeconds
        var bounced = false

        if (position.x < bounds.minX) {
            position.x = bounds.minX
            velocity.x = max(0f, kotlin.math.abs(velocity.x))
            bounced = true
        } else if (position.x > bounds.maxX) {
            position.x = bounds.maxX
            velocity.x = -kotlin.math.abs(velocity.x)
            bounced = true
        }

        if (position.y < bounds.minY) {
            position.y = bounds.minY
            velocity.y = kotlin.math.abs(velocity.y)
            bounced = true
        } else if (position.y > bounds.maxY) {
            position.y = bounds.maxY
            velocity.y = -kotlin.math.abs(velocity.y)
            bounced = true
        }

        return bounced
    }

    fun distanceSquared(a: MutableVector2, b: MutableVector2): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return dx * dx + dy * dy
    }
}
