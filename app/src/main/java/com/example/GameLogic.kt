package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.theme.Player1Color
import com.example.ui.theme.Player2Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedList
import java.util.Queue
import kotlin.random.Random

data class Player(val id: Int, var x: Int, var y: Int, var walls: Int, val color: Color)
data class Wall(val x: Int, val y: Int, val isHorizontal: Boolean)

enum class GameMode {
    LOCAL_PASS_AND_PLAY,
    VS_COMPUTER
}

enum class AIDifficulty {
    EASY,
    MEDIUM,
    HARD
}

sealed class GameAction {
    data class Move(val player: Int, val x: Int, val y: Int) : GameAction()
    data class PlaceWall(val player: Int, val wall: Wall) : GameAction()
}

data class GameState(
    val player1: Player = Player(1, 4, 8, 10, Player1Color), // Goal y=0
    val player2: Player = Player(2, 4, 0, 10, Player2Color), // Goal y=8
    val walls: List<Wall> = emptyList(),
    val currentPlayer: Int = 1,
    val winner: Int? = null,
    val errorMsg: String? = null,
    val gameMode: GameMode = GameMode.LOCAL_PASS_AND_PLAY,
    val aiDifficulty: AIDifficulty = AIDifficulty.MEDIUM,
    val isAiThinking: Boolean = false,
    val moveCount: Int = 0,
    val hasUsedRewardedWalls: Boolean = false // Only 1 reward allowed per game in vs Computer mode
)

data class AppSettings(
    val classicControls: Boolean = true,
    val soundEnabled: Boolean = true,
    val darkTheme: Boolean = false
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("blockpath_prefs", Context.MODE_PRIVATE)

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _appSettings = MutableStateFlow(
        AppSettings(
            classicControls = prefs.getBoolean("classic_controls", true),
            soundEnabled = prefs.getBoolean("sound_enabled", true),
            darkTheme = prefs.getBoolean("dark_theme", false)
        )
    )
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    fun updateSettings(settings: AppSettings) {
        _appSettings.value = settings
        prefs.edit()
            .putBoolean("classic_controls", settings.classicControls)
            .putBoolean("sound_enabled", settings.soundEnabled)
            .putBoolean("dark_theme", settings.darkTheme)
            .apply()
    }

    fun isFirstTimeUser(): Boolean {
        return prefs.getBoolean("is_first_launch_prompt_needed", true)
    }

    fun setFirstLaunchCompleted() {
        prefs.edit().putBoolean("is_first_launch_prompt_needed", false).apply()
    }

    fun startGame(mode: GameMode, difficulty: AIDifficulty = AIDifficulty.MEDIUM) {
        _gameState.value = GameState(
            gameMode = mode,
            aiDifficulty = difficulty
        )
    }

    fun handleAction(action: GameAction) {
        val state = _gameState.value
        if (state.winner != null || state.isAiThinking) return

        when (action) {
            is GameAction.Move -> {
                if (action.player != state.currentPlayer) return
                if (isValidMove(state, action.player, action.x, action.y)) {
                    applyMove(action.player, action.x, action.y)
                } else {
                    showError("Tap a highlighted square to move")
                }
            }
            is GameAction.PlaceWall -> {
                if (action.player != state.currentPlayer) return
                val player = if (action.player == 1) state.player1 else state.player2
                if (player.walls <= 0) {
                    showError("No walls left!")
                    return
                }
                val validation = checkWallPlacement(state, action.wall)
                if (validation == null) {
                    applyWall(action.player, action.wall)
                } else {
                    showError(validation)
                }
            }
        }
    }

    fun getValidMovesForCurrentPlayer(): List<Pair<Int, Int>> {
        val state = _gameState.value
        if (state.winner != null) return emptyList()
        return getValidMoves(state, state.currentPlayer)
    }

    fun getValidMoves(state: GameState, playerId: Int): List<Pair<Int, Int>> {
        val validMoves = mutableListOf<Pair<Int, Int>>()
        val p = if (playerId == 1) state.player1 else state.player2

        // Check potential reachable cells within distance 2
        for (nx in 0..8) {
            for (ny in 0..8) {
                if (Math.abs(nx - p.x) <= 2 && Math.abs(ny - p.y) <= 2) {
                    if (isValidMove(state, playerId, nx, ny)) {
                        validMoves.add(Pair(nx, ny))
                    }
                }
            }
        }
        return validMoves
    }

    private fun showError(msg: String) {
        _gameState.update { it.copy(errorMsg = msg) }
    }

    fun clearError() {
        _gameState.update { it.copy(errorMsg = null) }
    }

    fun resetGame() {
        val currentMode = _gameState.value.gameMode
        val currentDiff = _gameState.value.aiDifficulty
        startGame(currentMode, currentDiff)
    }

    /**
     * Unlocks +2 extra walls for Player 1 after successfully completing a rewarded video ad.
     * Allowed only in VS_COMPUTER mode, and only once per game.
     */
    fun claimRewardedExtraWalls() {
        val state = _gameState.value
        if (state.gameMode != GameMode.VS_COMPUTER || state.hasUsedRewardedWalls) return
        val p1 = state.player1.copy(walls = state.player1.walls + 2)
        _gameState.update {
            it.copy(
                player1 = p1,
                hasUsedRewardedWalls = true,
                errorMsg = null
            )
        }
    }

    private fun applyMove(playerId: Int, nx: Int, ny: Int) {
        val state = _gameState.value
        val p1 = state.player1.copy()
        val p2 = state.player2.copy()
        var winner = state.winner

        if (playerId == 1) {
            p1.x = nx
            p1.y = ny
            if (ny == 0) winner = 1
        } else {
            p2.x = nx
            p2.y = ny
            if (ny == 8) winner = 2
        }

        val nextPlayer = if (winner == null) (if (playerId == 1) 2 else 1) else playerId

        _gameState.update {
            it.copy(
                player1 = p1,
                player2 = p2,
                currentPlayer = nextPlayer,
                winner = winner,
                errorMsg = null,
                moveCount = it.moveCount + 1
            )
        }

        checkTriggerAi(nextPlayer, winner)
    }

    private fun applyWall(playerId: Int, wall: Wall) {
        val state = _gameState.value
        val p1 = state.player1.copy()
        val p2 = state.player2.copy()
        if (playerId == 1) p1.walls-- else p2.walls--

        val nextPlayer = if (playerId == 1) 2 else 1

        _gameState.update {
            it.copy(
                player1 = p1,
                player2 = p2,
                walls = it.walls + wall,
                currentPlayer = nextPlayer,
                errorMsg = null,
                moveCount = it.moveCount + 1
            )
        }

        checkTriggerAi(nextPlayer, null)
    }

    private fun checkTriggerAi(nextPlayer: Int, winner: Int?) {
        val state = _gameState.value
        if (winner == null && state.gameMode == GameMode.VS_COMPUTER && nextPlayer == 2) {
            viewModelScope.launch {
                _gameState.update { it.copy(isAiThinking = true) }
                delay(600) // Natural thinking delay
                withContext(Dispatchers.Default) {
                    executeAiTurn()
                }
                _gameState.update { it.copy(isAiThinking = false) }
            }
        }
    }

    private fun executeAiTurn() {
        val state = _gameState.value
        if (state.winner != null || state.currentPlayer != 2) return

        when (state.aiDifficulty) {
            AIDifficulty.EASY -> executeAiEasy(state)
            AIDifficulty.MEDIUM -> executeAiMedium(state)
            AIDifficulty.HARD -> executeAiHard(state)
        }
    }

    private fun executeAiEasy(state: GameState) {
        val moves = getValidMoves(state, 2)
        // 20% chance to try placing a wall if walls remain
        if (state.player2.walls > 0 && Random.nextFloat() < 0.2f) {
            val validWall = findRandomValidWall(state)
            if (validWall != null) {
                applyWall(2, validWall)
                return
            }
        }
        // Otherwise pick move that reduces distance to row 8
        if (moves.isNotEmpty()) {
            val bestMove = moves.minByOrNull { (x, y) ->
                shortestPathDistance(x, y, 8, state.walls) + (8 - y)
            } ?: moves.random()
            applyMove(2, bestMove.first, bestMove.second)
        }
    }

    private fun executeAiMedium(state: GameState) {
        val p1Dist = shortestPathDistance(state.player1.x, state.player1.y, 0, state.walls)
        val p2Dist = shortestPathDistance(state.player2.x, state.player2.y, 8, state.walls)

        // If player 1 is close to winning or with 35% probability, try blocking player 1
        if (state.player2.walls > 0 && (p1Dist <= 3 || (p1Dist < p2Dist && Random.nextFloat() < 0.45f))) {
            val blockingWall = findBestBlockingWall(state)
            if (blockingWall != null) {
                applyWall(2, blockingWall)
                return
            }
        }

        // Greedy shortest path move
        val moves = getValidMoves(state, 2)
        if (moves.isNotEmpty()) {
            val bestMove = moves.minByOrNull { (x, y) ->
                shortestPathDistance(x, y, 8, state.walls)
            } ?: moves.first()
            applyMove(2, bestMove.first, bestMove.second)
        }
    }

    private fun executeAiHard(state: GameState) {
        // Minimax / heuristic evaluation: evaluate best moves and walls
        val p1Dist = shortestPathDistance(state.player1.x, state.player1.y, 0, state.walls)
        val p2Dist = shortestPathDistance(state.player2.x, state.player2.y, 8, state.walls)

        val safeP1Dist = if (p1Dist == Int.MAX_VALUE) 1000 else p1Dist

        var bestScore = Int.MIN_VALUE
        var bestAction: GameAction? = null

        // Evaluate moves
        val moves = getValidMoves(state, 2)
        for (m in moves) {
            val newP2Dist = shortestPathDistance(m.first, m.second, 8, state.walls)
            val safeNewP2Dist = if (newP2Dist == Int.MAX_VALUE) 1000 else newP2Dist
            val score = (safeP1Dist - safeNewP2Dist) * 10
            if (score > bestScore) {
                bestScore = score
                bestAction = GameAction.Move(2, m.first, m.second)
            }
        }

        // Evaluate candidate walls near player 1
        if (state.player2.walls > 0 && p1Dist <= p2Dist + 1) {
            val candidateWalls = getCandidateWalls(state)
            for (w in candidateWalls) {
                val newWalls = state.walls + w
                val newP1Dist = shortestPathDistance(state.player1.x, state.player1.y, 0, newWalls)
                val newP2Dist = shortestPathDistance(state.player2.x, state.player2.y, 8, newWalls)
                if (newP1Dist != Int.MAX_VALUE && newP2Dist != Int.MAX_VALUE) {
                    val score = (newP1Dist - newP2Dist) * 10 - 2
                    if (score > bestScore) {
                        bestScore = score
                        bestAction = GameAction.PlaceWall(2, w)
                    }
                }
            }
        }

        when (bestAction) {
            is GameAction.PlaceWall -> applyWall(2, bestAction.wall)
            is GameAction.Move -> applyMove(2, bestAction.x, bestAction.y)
            null -> {
                if (moves.isNotEmpty()) {
                    applyMove(2, moves.first().first, moves.first().second)
                }
            }
        }
    }

    private fun findRandomValidWall(state: GameState): Wall? {
        val attempts = 30
        for (i in 0 until attempts) {
            val wall = Wall(Random.nextInt(8), Random.nextInt(8), Random.nextBoolean())
            if (checkWallPlacement(state, wall) == null) {
                return wall
            }
        }
        return null
    }

    private fun findBestBlockingWall(state: GameState): Wall? {
        val baseP1Dist = shortestPathDistance(state.player1.x, state.player1.y, 0, state.walls)
        var bestWall: Wall? = null
        var maxP1Dist = baseP1Dist

        val candidates = getCandidateWalls(state)
        for (w in candidates) {
            val newWalls = state.walls + w
            val p1d = shortestPathDistance(state.player1.x, state.player1.y, 0, newWalls)
            val p2d = shortestPathDistance(state.player2.x, state.player2.y, 8, newWalls)
            if (p1d > maxP1Dist && p1d != Int.MAX_VALUE && p2d != Int.MAX_VALUE) {
                maxP1Dist = p1d
                bestWall = w
            }
        }
        return bestWall
    }

    private fun getCandidateWalls(state: GameState): List<Wall> {
        val list = mutableListOf<Wall>()
        val p1x = state.player1.x
        val p1y = state.player1.y
        for (dx in -2..2) {
            for (dy in -2..2) {
                val wx = p1x + dx
                val wy = p1y + dy
                if (wx in 0..7 && wy in 0..7) {
                    val wH = Wall(wx, wy, true)
                    if (checkWallPlacement(state, wH) == null) list.add(wH)
                    val wV = Wall(wx, wy, false)
                    if (checkWallPlacement(state, wV) == null) list.add(wV)
                }
            }
        }
        return list.shuffled().take(20)
    }

    fun checkWallPlacement(state: GameState, wall: Wall): String? {
        if (wall.x !in 0..7 || wall.y !in 0..7) {
            return "Wall is out of grid bounds!"
        }

        // Cannot intersect or overlap
        for (w in state.walls) {
            if (w.x == wall.x && w.y == wall.y) {
                return "Wall overlaps or intersects an existing wall!"
            }
            if (w.isHorizontal == wall.isHorizontal) {
                if (w.isHorizontal) {
                    if (w.y == wall.y && Math.abs(w.x - wall.x) < 2) {
                        return "Wall overlaps an existing horizontal wall!"
                    }
                } else {
                    if (w.x == wall.x && Math.abs(w.y - wall.y) < 2) {
                        return "Wall overlaps an existing vertical wall!"
                    }
                }
            }
        }

        // Check pathfinding
        val newWalls = state.walls + wall
        if (!hasPath(state.player1.x, state.player1.y, 0, newWalls) || !hasPath(state.player2.x, state.player2.y, 8, newWalls)) {
            return "Cannot block all paths to the goal!"
        }

        return null
    }

    private fun isValidMove(state: GameState, playerId: Int, nx: Int, ny: Int): Boolean {
        if (nx !in 0..8 || ny !in 0..8) return false
        val p = if (playerId == 1) state.player1 else state.player2
        val op = if (playerId == 1) state.player2 else state.player1

        if (nx == op.x && ny == op.y) return false

        val dx = Math.abs(nx - p.x)
        val dy = Math.abs(ny - p.y)

        if (dx + dy == 1) {
            return !isWallBetween(state.walls, p.x, p.y, nx, ny)
        } else if (dx == 0 && dy == 2) {
            val midY = (p.y + ny) / 2
            if (op.x == nx && op.y == midY) {
                return !isWallBetween(state.walls, p.x, p.y, nx, midY) &&
                       !isWallBetween(state.walls, nx, midY, nx, ny)
            }
        } else if (dx == 2 && dy == 0) {
            val midX = (p.x + nx) / 2
            if (op.x == midX && op.y == ny) {
                return !isWallBetween(state.walls, p.x, p.y, midX, ny) &&
                       !isWallBetween(state.walls, midX, ny, nx, ny)
            }
        } else if (dx == 1 && dy == 1) {
            val adj1X = p.x; val adj1Y = ny
            val adj2X = nx; val adj2Y = p.y

            var valid1 = false
            if (op.x == adj1X && op.y == adj1Y) {
                val blockedBehind = (adj1Y + (adj1Y - p.y) !in 0..8) || isWallBetween(state.walls, adj1X, adj1Y, adj1X, adj1Y + (adj1Y - p.y))
                if (blockedBehind) {
                    if (!isWallBetween(state.walls, p.x, p.y, adj1X, adj1Y) && !isWallBetween(state.walls, adj1X, adj1Y, nx, ny)) {
                        valid1 = true
                    }
                }
            }
            var valid2 = false
            if (op.x == adj2X && op.y == adj2Y) {
                val blockedBehind = (adj2X + (adj2X - p.x) !in 0..8) || isWallBetween(state.walls, adj2X, adj2Y, adj2X + (adj2X - p.x), adj2Y)
                if (blockedBehind) {
                    if (!isWallBetween(state.walls, p.x, p.y, adj2X, adj2Y) && !isWallBetween(state.walls, adj2X, adj2Y, nx, ny)) {
                        valid2 = true
                    }
                }
            }
            return valid1 || valid2
        }
        return false
    }

    private fun isWallBetween(walls: List<Wall>, x1: Int, y1: Int, x2: Int, y2: Int): Boolean {
        val wallCount = walls.size
        if (x1 == x2) {
            val miny = if (y1 < y2) y1 else y2
            for (i in 0 until wallCount) {
                val w = walls[i]
                if (w.isHorizontal && w.y == miny && (w.x == x1 || w.x == x1 - 1)) {
                    return true
                }
            }
        } else if (y1 == y2) {
            val minx = if (x1 < x2) x1 else x2
            for (i in 0 until wallCount) {
                val w = walls[i]
                if (!w.isHorizontal && w.x == minx && (w.y == y1 || w.y == y1 - 1)) {
                    return true
                }
            }
        }
        return false
    }

    private fun hasPath(startX: Int, startY: Int, goalY: Int, walls: List<Wall>): Boolean {
        return shortestPathDistance(startX, startY, goalY, walls) != Int.MAX_VALUE
    }

    fun shortestPathDistance(startX: Int, startY: Int, goalY: Int, walls: List<Wall>): Int {
        if (startY == goalY) return 0

        val dist = IntArray(81) { Int.MAX_VALUE }
        val queue = IntArray(81)
        var head = 0
        var tail = 0

        val startIdx = startX * 9 + startY
        dist[startIdx] = 0
        queue[tail++] = startIdx

        while (head < tail) {
            val curr = queue[head++]
            val x = curr / 9
            val y = curr % 9
            val d = dist[curr]
            if (y == goalY) return d

            // Down (y + 1)
            if (y < 8) {
                val next = curr + 1
                if (dist[next] == Int.MAX_VALUE && !isWallBetween(walls, x, y, x, y + 1)) {
                    dist[next] = d + 1
                    queue[tail++] = next
                }
            }
            // Up (y - 1)
            if (y > 0) {
                val next = curr - 1
                if (dist[next] == Int.MAX_VALUE && !isWallBetween(walls, x, y, x, y - 1)) {
                    dist[next] = d + 1
                    queue[tail++] = next
                }
            }
            // Right (x + 1)
            if (x < 8) {
                val next = curr + 9
                if (dist[next] == Int.MAX_VALUE && !isWallBetween(walls, x, y, x + 1, y)) {
                    dist[next] = d + 1
                    queue[tail++] = next
                }
            }
            // Left (x - 1)
            if (x > 0) {
                val next = curr - 9
                if (dist[next] == Int.MAX_VALUE && !isWallBetween(walls, x, y, x - 1, y)) {
                    dist[next] = d + 1
                    queue[tail++] = next
                }
            }
        }
        return Int.MAX_VALUE
    }
}
