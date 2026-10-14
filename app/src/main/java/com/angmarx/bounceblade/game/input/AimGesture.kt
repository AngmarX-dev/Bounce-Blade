package com.angmarx.bounceblade.game.input

import kotlin.math.sqrt

/** Platform-independent aim gesture state; wire this to MotionEvent in the game view. */
class AimGesture {
    var isAiming: Boolean = false
        private set
    private var originX = 0f
    private var originY = 0f
    private var currentX = 0f
    private var currentY = 0f

    fun begin(x: Float, y: Float) {
        originX = x
        originY = y
        currentX = x
        currentY = y
        isAiming = true
    }

    fun moveTo(x: Float, y: Float) {
        if (!isAiming) return
        currentX = x
        currentY = y
    }

    /** Returns a normalized launch vector and ends the gesture. */
    fun release(): Pair<Float, Float>? {
        if (!isAiming) return null
        isAiming = false
        val dx = originX - currentX
        val dy = originY - currentY
        val length = sqrt(dx * dx + dy * dy)
        if (length <= 0.045f) return null
        return (dx / length) to (dy / length)
    }

    fun cancel() {
        isAiming = false
    }
}
