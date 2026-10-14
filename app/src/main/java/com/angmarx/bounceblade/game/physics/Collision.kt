package com.angmarx.bounceblade.game.physics

import kotlin.math.sqrt

object Collision {

    /**
     * Checks whether two circles overlap.
     * The circles touch when the distance between their centers equals
     * the sum of their radii.
     */
    fun circlesOverlap(
        firstCenter: PhysicsPoint,
        firstRadius: Float,
        secondCenter: PhysicsPoint,
        secondRadius: Float
    ): Boolean {
        require(firstRadius >= 0f) { "firstRadius must not be negative" }
        require(secondRadius >= 0f) { "secondRadius must not be negative" }

        val dx = firstCenter.x - secondCenter.x
        val dy = firstCenter.y - secondCenter.y
        val combinedRadius = firstRadius + secondRadius

        return dx * dx + dy * dy <= combinedRadius * combinedRadius
    }

    /**
     * Checks whether the blade's movement from start to end passes within
     * the target's radius. Include the blade radius in targetRadius to account
     * for the blade's size.
     */
    fun segmentIntersectsCircle(
        start: PhysicsPoint,
        end: PhysicsPoint,
        circleCenter: PhysicsPoint,
        combinedRadius: Float
    ): Boolean {
        require(combinedRadius >= 0f) {
            "combinedRadius must not be negative"
        }

        val segmentX = end.x - start.x
        val segmentY = end.y - start.y
        val segmentLengthSquared = segmentX * segmentX + segmentY * segmentY

        if (segmentLengthSquared == 0f) {
            val dx = start.x - circleCenter.x
            val dy = start.y - circleCenter.y
            return dx * dx + dy * dy <= combinedRadius * combinedRadius
        }

        val centerFromStartX = circleCenter.x - start.x
        val centerFromStartY = circleCenter.y - start.y

        val projection = (
            centerFromStartX * segmentX +
                centerFromStartY * segmentY
            ) / segmentLengthSquared

        val clampedProjection = projection.coerceIn(0f, 1f)
        val closestX = start.x + clampedProjection * segmentX
        val closestY = start.y + clampedProjection * segmentY

        val dx = closestX - circleCenter.x
        val dy = closestY - circleCenter.y

        return dx * dx + dy * dy <= combinedRadius * combinedRadius
    }

    /**
     * Convenience function for testing the blade's movement against a
     * circular target.
     */
    fun bladeSweepHitsCircle(
        previousPosition: PhysicsPoint,
        currentPosition: PhysicsPoint,
        bladeRadius: Float,
        targetCenter: PhysicsPoint,
        targetRadius: Float
    ): Boolean {
        require(bladeRadius >= 0f) { "bladeRadius must not be negative" }
        require(targetRadius >= 0f) { "targetRadius must not be negative" }

        return segmentIntersectsCircle(
            start = previousPosition,
            end = currentPosition,
            circleCenter = targetCenter,
            combinedRadius = bladeRadius + targetRadius
        )
    }
}
