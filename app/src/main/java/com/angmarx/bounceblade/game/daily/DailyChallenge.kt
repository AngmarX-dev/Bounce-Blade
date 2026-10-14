package com.angmarx.bounceblade.game.daily

data class DailyChallenge(
    val id: String,
    val levelAsset: String,
    val expiresAtEpochSeconds: Long
) {
    fun hasExpired(nowEpochSeconds: Long): Boolean = nowEpochSeconds >= expiresAtEpochSeconds
}
