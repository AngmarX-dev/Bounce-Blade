package com.angmarx.bounceblade.game.leaderboard

data class LeaderboardEntry(
    val playerId: String,
    val displayName: String,
    val score: Long
)

/**
 * Implement with a backend when online leaderboards are configured.
 * Do not store authentication tokens in source control.
 */
interface LeaderboardRepository {
    fun submitScore(entry: LeaderboardEntry): Result<Unit>
    fun loadTopScores(limit: Int = 100): Result<List<LeaderboardEntry>>
}
