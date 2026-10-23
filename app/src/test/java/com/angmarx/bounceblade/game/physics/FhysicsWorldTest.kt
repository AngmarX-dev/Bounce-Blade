package com.angmarx.bounceblade.game.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class FhysicsWorldTest {

    @Test
    fun solidBoundaryReflectsBladeWithConfiguredRestitution() {
        val world = FhysicsWorld(linearDamping = 0f, angularDamping = 0f)
        val body = FhysicsBody(
            position = FhysicsVector2(9f, 5f),
            velocity = FhysicsVector2(10f, 0f),
            radius = 0.5f,
            material = FhysicsMaterial(restitution = 0.8f, staticFriction = 0f, dynamicFriction = 0f)
        )

        val result = world.stepBody(
            body, 0.2f,
            FhysicsBounds(0f, 0f, 10f, 10f, constrainLeft = false, constrainRight = true)
        )

        assertTrue(result.wallContacts > 0)
        assertEquals(10f, result.position.x, 0.0001f)
        assertEquals(-8f, result.velocity.x, 0.001f)
    }

    @Test
    fun openPlayerSideDoesNotBounceWhenTheBladeMisses() {
        val world = FhysicsWorld(linearDamping = 0f, angularDamping = 0f)
        val body = FhysicsBody(
            position = FhysicsVector2(9f, 5f),
            velocity = FhysicsVector2(10f, 0f),
            radius = 0.5f
        )

        val result = world.stepBody(
            body, 0.2f,
            FhysicsBounds(0f, 0f, 10f, 10f, constrainLeft = false, constrainRight = false)
        )

        assertEquals(11f, result.position.x, 0.0001f)
        assertEquals(0, result.wallContacts)
    }

    @Test
    fun circleContactResolvesOverlapAndReversesApproachingBodies() {
        val world = FhysicsWorld(linearDamping = 0f, angularDamping = 0f)
        val first = FhysicsBody(FhysicsVector2(0f, 0f), FhysicsVector2(2f, 0f), 1f)
        val second = FhysicsBody(FhysicsVector2(1.5f, 0f), FhysicsVector2(-2f, 0f), 1f)

        assertTrue(world.resolveCircleContact(first, second))
        assertTrue(first.position.x < 0f)
        assertTrue(second.position.x > 1.5f)
        assertTrue(first.velocity.x < 0f)
        assertTrue(second.velocity.x > 0f)
    }

    @Test
    fun separatedCirclesDoNotCreateContact() {
        val world = FhysicsWorld()
        val first = FhysicsBody(FhysicsVector2(0f, 0f), radius = 1f)
        val second = FhysicsBody(FhysicsVector2(3f, 0f), radius = 1f)
        assertFalse(world.resolveCircleContact(first, second))
    }

    @Test
    fun angularMotionAdvancesInRadians() {
        val world = FhysicsWorld(linearDamping = 0f, angularDamping = 0f)
        val body = FhysicsBody(FhysicsVector2(), radius = 1f)
        body.angularVelocity = PI.toFloat()

        val result = world.stepBody(body, 0.5f)

        assertEquals((PI / 2.0).toFloat(), result.angleRadians, 0.001f)
    }
}
