package com.angmarx.bounceblade.game.physics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollisionTest {

    @Test
    fun circlesOverlap_whenCirclesTouch() {
        assertTrue(
            Collision.circlesOverlap(
                firstCenter = PhysicsPoint(0f, 0f),
                firstRadius = 5f,
                secondCenter = PhysicsPoint(10f, 0f),
                secondRadius = 5f
            )
        )
    }

    @Test
    fun circlesOverlap_returnsFalseWhenCirclesAreSeparated() {
        assertFalse(
            Collision.circlesOverlap(
                firstCenter = PhysicsPoint(0f, 0f),
                firstRadius = 4f,
                secondCenter = PhysicsPoint(10f, 0f),
                secondRadius = 4f
            )
        )
    }

    @Test
    fun segmentIntersectsCircle_whenPathCrossesCircle() {
        assertTrue(
            Collision.segmentIntersectsCircle(
                start = PhysicsPoint(0f, 0f),
                end = PhysicsPoint(20f, 0f),
                circleCenter = PhysicsPoint(10f, 0f),
                combinedRadius = 2f
            )
        )
    }

    @Test
    fun segmentIntersectsCircle_returnsFalseWhenPathMissesCircle() {
        assertFalse(
            Collision.segmentIntersectsCircle(
                start = PhysicsPoint(0f, 0f),
                end = PhysicsPoint(20f, 0f),
                circleCenter = PhysicsPoint(10f, 5f),
                combinedRadius = 2f
            )
        )
    }

    @Test
    fun segmentIntersectsCircle_handlesZeroLengthSegment() {
        assertTrue(
            Collision.segmentIntersectsCircle(
                start = PhysicsPoint(3f, 4f),
                end = PhysicsPoint(3f, 4f),
                circleCenter = PhysicsPoint(0f, 0f),
                combinedRadius = 5f
            )
        )
    }

    @Test
    fun bladeSweepHitsCircle_accountsForBladeRadius() {
        assertTrue(
            Collision.bladeSweepHitsCircle(
                previousPosition = PhysicsPoint(0f, 0f),
                currentPosition = PhysicsPoint(20f, 0f),
                bladeRadius = 2f,
                targetCenter = PhysicsPoint(10f, 4f),
                targetRadius = 2f
            )
        )
    }
}
