package com.angmarx.bounceblade.core

/** Shared defaults. Keep gameplay positions normalized to the 0..1 range. */
object GameConfig {
    const val INITIAL_LEVEL = 1
    const val DEFAULT_BOUNCES = 5
    const val SLOW_TIME_MULTIPLIER = 0.28f
    const val SLOW_METER_DRAIN_PER_SECOND = 0.18f
    const val SLOW_METER_RECHARGE_PER_SECOND = 0.04f
    const val MIN_X = 0.08f
    const val MAX_X = 0.92f
    const val MIN_Y = 0.085f
    const val MAX_Y = 0.87f
}
