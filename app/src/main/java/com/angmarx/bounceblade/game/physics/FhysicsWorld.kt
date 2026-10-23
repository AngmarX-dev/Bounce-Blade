package com.angmarx.bounceblade.game.physics

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Android-safe gameplay adapter for the rigid-body ideas in Fhysics
 * (https://github.com/OfficerYoda/Fhysics), adapted for Bounce Blade.
 *
 * The upstream desktop project depends on JavaFX/AWT rendering and UI classes,
 * so its simulation concepts are implemented here without those desktop-only
 * dependencies. Coordinates use Android canvas pixels and seconds.
 */
data class FhysicsVector2(var x: Float = 0f, var y: Float = 0f) {
    operator fun plus(other: FhysicsVector2) = FhysicsVector2(x + other.x, y + other.y)
    operator fun minus(other: FhysicsVector2) = FhysicsVector2(x - other.x, y - other.y)
    operator fun times(scale: Float) = FhysicsVector2(x * scale, y * scale)
    operator fun div(scale: Float) = FhysicsVector2(x / scale, y / scale)
    fun dot(other: FhysicsVector2): Float = x * other.x + y * other.y
    fun cross(other: FhysicsVector2): Float = x * other.y - y * other.x
    fun lengthSquared(): Float = x * x + y * y
    fun length(): Float = sqrt(lengthSquared())
    fun normalized(): FhysicsVector2 {
        val len = length()
        return if (len > 1e-6f) this / len else FhysicsVector2()
    }
}

data class FhysicsMaterial(
    val restitution: Float = 0.96f,
    val staticFriction: Float = 0.35f,
    val dynamicFriction: Float = 0.22f
) {
    init {
        require(restitution in 0f..1f)
        require(staticFriction >= 0f && dynamicFriction >= 0f)
    }
}

/** Circle-shaped rigid body used by the blade and available to other game objects. */
class FhysicsBody(
    position: FhysicsVector2,
    velocity: FhysicsVector2 = FhysicsVector2(),
    var radius: Float,
    var mass: Float = 1f,
    val material: FhysicsMaterial = FhysicsMaterial(),
    var isStatic: Boolean = false
) {
    val position: FhysicsVector2 = position.copy()
    val velocity: FhysicsVector2 = velocity.copy()
    val force: FhysicsVector2 = FhysicsVector2()
    var angleRadians: Float = 0f
    var angularVelocity: Float = 0f

    init {
        require(radius >= 0f) { "radius must not be negative" }
        require(mass > 0f) { "mass must be positive" }
    }

    val inverseMass: Float get() = if (isStatic) 0f else 1f / mass
    val momentOfInertia: Float get() = max(0.5f * mass * radius * radius, 1e-5f)
    val inverseInertia: Float get() = if (isStatic) 0f else 1f / momentOfInertia

    fun applyForce(value: FhysicsVector2) {
        if (isStatic) return
        force.x += value.x
        force.y += value.y
    }

    fun applyImpulse(value: FhysicsVector2, contactOffset: FhysicsVector2 = FhysicsVector2()) {
        if (isStatic) return
        velocity.x += value.x * inverseMass
        velocity.y += value.y * inverseMass
        angularVelocity += contactOffset.cross(value) * inverseInertia
    }
}

/**
 * Bounds refer to permitted body-centre coordinates, so callers can account
 * for a body's radius when defining an arena. Edges may be left open for games
 * where crossing the player's side is a loss rather than a bounce.
 */
data class FhysicsBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val constrainLeft: Boolean = true,
    val constrainTop: Boolean = true,
    val constrainRight: Boolean = true,
    val constrainBottom: Boolean = true
) {
    init {
        require(right >= left && bottom >= top) { "invalid physics bounds" }
    }
}

data class FhysicsStepResult(
    val position: FhysicsVector2,
    val velocity: FhysicsVector2,
    val angleRadians: Float,
    val angularVelocity: Float,
    val wallContacts: Int
)

/**
 * Small deterministic rigid-body runtime. It uses semi-implicit Euler
 * integration, bounded substeps, restitution, Coulomb friction, angular motion,
 * gravity and a circle-circle impulse solver. The caller controls game rules
 * and damage/sensor events; physics does not turn piercing blade hits into
 * solid collisions.
 */
class FhysicsWorld(
    var gravity: FhysicsVector2 = FhysicsVector2(),
    var linearDamping: Float = 0.004f,
    var angularDamping: Float = 0.018f,
    private val maxSubstepSeconds: Float = 1f / 240f
) {
    init {
        require(linearDamping >= 0f && angularDamping >= 0f)
        require(maxSubstepSeconds > 0f)
    }

    fun stepBody(
        body: FhysicsBody,
        deltaSeconds: Float,
        bounds: FhysicsBounds? = null
    ): FhysicsStepResult {
        require(deltaSeconds >= 0f) { "deltaSeconds must not be negative" }
        if (deltaSeconds == 0f || body.isStatic) {
            return result(body, 0)
        }

        val substeps = ceil(deltaSeconds / maxSubstepSeconds).toInt().coerceIn(1, 24)
        val dt = deltaSeconds / substeps
        var wallContacts = 0
        repeat(substeps) {
            // Semi-implicit Euler: update velocity before position.
            body.velocity.x += (gravity.x + body.force.x * body.inverseMass) * dt
            body.velocity.y += (gravity.y + body.force.y * body.inverseMass) * dt
            val damping = exp(-linearDamping * dt * 60f)
            body.velocity.x *= damping
            body.velocity.y *= damping
            body.angularVelocity *= exp(-angularDamping * dt * 60f)
            body.position.x += body.velocity.x * dt
            body.position.y += body.velocity.y * dt
            body.angleRadians = (body.angleRadians + body.angularVelocity * dt) % TWO_PI
            if (bounds != null) wallContacts += solveBoundaryContacts(body, bounds)
        }
        body.force.x = 0f
        body.force.y = 0f
        return result(body, wallContacts)
    }

    /** Resolves an overlap between two circular bodies using impulses and friction. */
    fun resolveCircleContact(first: FhysicsBody, second: FhysicsBody): Boolean {
        val invMassSum = first.inverseMass + second.inverseMass
        if (invMassSum <= 0f) return false

        val delta = second.position - first.position
        val distanceSquared = delta.lengthSquared()
        val combinedRadius = first.radius + second.radius
        if (distanceSquared >= combinedRadius * combinedRadius) return false

        val distance = sqrt(max(distanceSquared, 1e-10f))
        val normal = if (distance > 1e-5f) delta / distance else FhysicsVector2(1f, 0f)
        val penetration = combinedRadius - distance
        val correctionMagnitude = max(penetration - POSITION_SLOP, 0f) * POSITION_CORRECTION / invMassSum
        val correction = normal * correctionMagnitude
        if (!first.isStatic) {
            first.position.x -= correction.x * first.inverseMass
            first.position.y -= correction.y * first.inverseMass
        }
        if (!second.isStatic) {
            second.position.x += correction.x * second.inverseMass
            second.position.y += correction.y * second.inverseMass
        }

        val relative = second.velocity - first.velocity
        val normalSpeed = relative.dot(normal)
        if (normalSpeed > 0f) return true

        val restitution = min(first.material.restitution, second.material.restitution)
        val impulseMagnitude = -(1f + restitution) * normalSpeed / invMassSum
        val impulse = normal * impulseMagnitude
        first.applyImpulse(impulse * -1f, normal * first.radius)
        second.applyImpulse(impulse, normal * -second.radius)

        val afterNormal = second.velocity - first.velocity
        val tangentRaw = afterNormal - normal * afterNormal.dot(normal)
        val tangentLength = tangentRaw.length()
        if (tangentLength > 1e-5f) {
            val tangent = tangentRaw / tangentLength
            var frictionMagnitude = -afterNormal.dot(tangent) / invMassSum
            val staticMu = sqrt(first.material.staticFriction * second.material.staticFriction)
            val dynamicMu = sqrt(first.material.dynamicFriction * second.material.dynamicFriction)
            val staticLimit = impulseMagnitude * staticMu
            frictionMagnitude = if (abs(frictionMagnitude) <= staticLimit) {
                frictionMagnitude
            } else {
                -impulseMagnitude * dynamicMu
            }
            val frictionImpulse = tangent * frictionMagnitude
            first.applyImpulse(frictionImpulse * -1f, normal * first.radius)
            second.applyImpulse(frictionImpulse, normal * -second.radius)
        }
        return true
    }

    private fun solveBoundaryContacts(body: FhysicsBody, bounds: FhysicsBounds): Int {
        var contacts = 0
        if (bounds.constrainLeft && body.position.x < bounds.left) {
            body.position.x = bounds.left
            if (body.velocity.x < 0f) contacts += bounceFromBoundary(body, FhysicsVector2(1f, 0f), FhysicsVector2(-body.radius, 0f))
        }
        if (bounds.constrainRight && body.position.x > bounds.right) {
            body.position.x = bounds.right
            if (body.velocity.x > 0f) contacts += bounceFromBoundary(body, FhysicsVector2(-1f, 0f), FhysicsVector2(body.radius, 0f))
        }
        if (bounds.constrainTop && body.position.y < bounds.top) {
            body.position.y = bounds.top
            if (body.velocity.y < 0f) contacts += bounceFromBoundary(body, FhysicsVector2(0f, 1f), FhysicsVector2(0f, -body.radius))
        }
        if (bounds.constrainBottom && body.position.y > bounds.bottom) {
            body.position.y = bounds.bottom
            if (body.velocity.y > 0f) contacts += bounceFromBoundary(body, FhysicsVector2(0f, -1f), FhysicsVector2(0f, body.radius))
        }
        return contacts
    }

    private fun bounceFromBoundary(body: FhysicsBody, normal: FhysicsVector2, contactOffset: FhysicsVector2): Int {
        val normalSpeed = body.velocity.dot(normal)
        if (normalSpeed >= 0f) return 0
        val normalImpulseMagnitude = -(1f + body.material.restitution) * normalSpeed * body.mass
        body.velocity.x += normal.x * normalImpulseMagnitude / body.mass
        body.velocity.y += normal.y * normalImpulseMagnitude / body.mass

        val tangent = FhysicsVector2(-normal.y, normal.x)
        val tangentSpeed = body.velocity.dot(tangent)
        if (abs(tangentSpeed) > 1e-4f) {
            val frictionLimit = body.material.dynamicFriction * normalImpulseMagnitude
            val frictionImpulseMagnitude = min(abs(tangentSpeed) * body.mass, frictionLimit)
            val frictionImpulse = tangent * (-kotlin.math.sign(tangentSpeed) * frictionImpulseMagnitude)
            body.velocity.x += frictionImpulse.x / body.mass
            body.velocity.y += frictionImpulse.y / body.mass
            body.angularVelocity += contactOffset.cross(frictionImpulse) * body.inverseInertia
        }
        return 1
    }

    private fun result(body: FhysicsBody, contacts: Int) = FhysicsStepResult(
        position = body.position.copy(),
        velocity = body.velocity.copy(),
        angleRadians = body.angleRadians,
        angularVelocity = body.angularVelocity,
        wallContacts = contacts
    )

    private companion object {
        const val POSITION_SLOP = 0.01f
        const val POSITION_CORRECTION = 0.82f
        const val TWO_PI = 6.283185307179586f
    }
}
