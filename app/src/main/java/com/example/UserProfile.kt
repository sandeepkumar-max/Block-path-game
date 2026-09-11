package com.example

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserProfile(
    val name: String = "Player 1",
    val avatar: String = "♟",
    val gamesPlayed: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val wallsPlaced: Int = 0,
    val winStreak: Int = 0,
    val bestStreak: Int = 0
) {
    val winRate: Int
        get() = if (gamesPlayed > 0) ((wins * 100) / gamesPlayed) else 0

    val level: Int
        get() = 1 + (wins / 3) // Level up every 3 wins
}

val AVAILABLE_AVATARS = listOf(
    "♟", "♞", "♚", "♛", "♜",
    "🦁", "⚡", "🔥", "👑", "🤖"
)

class UserProfileRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("blockpath_user_profile", Context.MODE_PRIVATE)

    private val _userProfile = MutableStateFlow(loadProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private fun loadProfile(): UserProfile {
        return UserProfile(
            name = prefs.getString(KEY_NAME, "Player 1") ?: "Player 1",
            avatar = prefs.getString(KEY_AVATAR, "♟") ?: "♟",
            gamesPlayed = prefs.getInt(KEY_GAMES_PLAYED, 0),
            wins = prefs.getInt(KEY_WINS, 0),
            losses = prefs.getInt(KEY_LOSSES, 0),
            wallsPlaced = prefs.getInt(KEY_WALLS_PLACED, 0),
            winStreak = prefs.getInt(KEY_WIN_STREAK, 0),
            bestStreak = prefs.getInt(KEY_BEST_STREAK, 0)
        )
    }

    fun updateProfile(name: String, avatar: String) {
        val cleanName = name.trim().ifBlank { "Player 1" }
        prefs.edit()
            .putString(KEY_NAME, cleanName)
            .putString(KEY_AVATAR, avatar)
            .apply()

        _userProfile.value = _userProfile.value.copy(
            name = cleanName,
            avatar = avatar
        )
    }

    fun recordGameFinished(won: Boolean) {
        val current = _userProfile.value
        val newPlayed = current.gamesPlayed + 1
        val newWins = if (won) current.wins + 1 else current.wins
        val newLosses = if (!won) current.losses + 1 else current.losses
        val newStreak = if (won) current.winStreak + 1 else 0
        val newBestStreak = maxOf(current.bestStreak, newStreak)

        prefs.edit()
            .putInt(KEY_GAMES_PLAYED, newPlayed)
            .putInt(KEY_WINS, newWins)
            .putInt(KEY_LOSSES, newLosses)
            .putInt(KEY_WIN_STREAK, newStreak)
            .putInt(KEY_BEST_STREAK, newBestStreak)
            .apply()

        _userProfile.value = current.copy(
            gamesPlayed = newPlayed,
            wins = newWins,
            losses = newLosses,
            winStreak = newStreak,
            bestStreak = newBestStreak
        )
    }

    fun recordWallPlaced() {
        val current = _userProfile.value
        val newWalls = current.wallsPlaced + 1
        prefs.edit().putInt(KEY_WALLS_PLACED, newWalls).apply()
        _userProfile.value = current.copy(wallsPlaced = newWalls)
    }

    fun resetStats() {
        prefs.edit()
            .putInt(KEY_GAMES_PLAYED, 0)
            .putInt(KEY_WINS, 0)
            .putInt(KEY_LOSSES, 0)
            .putInt(KEY_WALLS_PLACED, 0)
            .putInt(KEY_WIN_STREAK, 0)
            .putInt(KEY_BEST_STREAK, 0)
            .apply()

        _userProfile.value = _userProfile.value.copy(
            gamesPlayed = 0,
            wins = 0,
            losses = 0,
            wallsPlaced = 0,
            winStreak = 0,
            bestStreak = 0
        )
    }

    companion object {
        private const val KEY_NAME = "profile_display_name"
        private const val KEY_AVATAR = "profile_avatar_symbol"
        private const val KEY_GAMES_PLAYED = "profile_games_played"
        private const val KEY_WINS = "profile_wins"
        private const val KEY_LOSSES = "profile_losses"
        private const val KEY_WALLS_PLACED = "profile_walls_placed"
        private const val KEY_WIN_STREAK = "profile_win_streak"
        private const val KEY_BEST_STREAK = "profile_best_streak"
    }
}
