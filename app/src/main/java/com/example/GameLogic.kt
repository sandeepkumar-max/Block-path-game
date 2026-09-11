package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.theme.Player1Color
import com.example.ui.theme.Player2Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    VS_COMPUTER,
    ONLINE,
    OBSTACLE_RUSH,     // Alternative mode: fixed stone obstacles & dynamic gameplay
    QUICK_BLITZ        // Alternative mode: 5 walls each, frantic speed duel
}

enum class AIDifficulty {
    EASY,
    MEDIUM,
    HARD
}

sealed class GameAction {
    data class Move(val player: Int, val x: Int, val y: Int) : GameAction()
    data class PlaceWall(val player: Int, val wall: Wall) : GameAction()
    data class PassTurn(val player: Int) : GameAction()
}

data class GameState(
    val player1: Player = Player(1, 4, 8, 10, Player1Color), // Goal y=0
    val player2: Player = Player(2, 4, 0, 10, Player2Color), // Goal y=8
    val walls: List<Wall> = emptyList(),
    val obstacles: List<Pair<Int, Int>> = emptyList(), // Static rock obstacles on tiles (x, y)
    val currentPlayer: Int = 1,
    val winner: Int? = null,
    val errorMsg: String? = null,
    val gameMode: GameMode = GameMode.LOCAL_PASS_AND_PLAY,
    val aiDifficulty: AIDifficulty = AIDifficulty.MEDIUM,
    val isAiThinking: Boolean = false,
    val moveCount: Int = 0,
    val hasUsedRewardedWalls: Boolean = false, // Only 1 reward allowed per game in vs Computer mode
    val player1Name: String = "Player 1",
    val player1Avatar: String = "♟",
    val player2Name: String = "Player 2",
    val player2Avatar: String = "♜",
    val myPlayerNum: Int = 1, // 1 for Player 1 (Host/Local), 2 for Player 2 (Guest)
    val isTimerEnabled: Boolean = false, // Online game timer toggle (default false = relaxed)
    val lastActionNotification: String? = null, // Real-time notification e.g. "Opponent moved pawn"
    val lastPlacedWall: Wall? = null, // Highlight latest placed wall
    val lastMovedPlayerPos: Pair<Int, Int>? = null, // Highlight latest pawn destination
    val opponentName: String = "Opponent",
    val opponentAvatar: String = "🤖",
    val opponentPing: Int = 42,
    val isRealPeerConnected: Boolean = false,
    val roomCode: String? = null,
    val rematchRequestedByMe: Boolean = false,
    val rematchRequestedByOpponent: Boolean = false,
    val roundsPlayed: Int = 1,
    val myWins: Int = 0,
    val opponentWins: Int = 0
)

data class AppSettings(
    val classicControls: Boolean = true,
    val soundEnabled: Boolean = true,
    val memeSoundEnabled: Boolean = true,
    val darkTheme: Boolean = false,
    val timingEnabled: Boolean = false
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("blockpath_prefs", Context.MODE_PRIVATE)
    val userProfileRepository: UserProfileRepository = UserProfileRepository(application)
    val userProfile: StateFlow<UserProfile> = userProfileRepository.userProfile
    val soundManager: SoundManager = SoundManager.getInstance(application)
    val peerJsWebRtcManager: PeerJsWebRtcManager = PeerJsWebRtcManager(application)

    init {
        peerJsWebRtcManager.onOpponentProfileUpdated = { name, avatar ->
            updateOpponentProfile(name, avatar)
        }
        peerJsWebRtcManager.onPingUpdated = { ping ->
            _gameState.update { cur ->
                if (cur.gameMode == GameMode.ONLINE) {
                    cur.copy(opponentPing = ping)
                } else cur
            }
        }
        peerJsWebRtcManager.onConnectionNotice = { notice ->
            _gameState.update { cur ->
                if (cur.gameMode == GameMode.ONLINE && cur.winner == null) {
                    cur.copy(lastActionNotification = notice)
                } else cur
            }
        }
        peerJsWebRtcManager.onReconnected = {
            _gameState.update { cur ->
                if (cur.gameMode == GameMode.ONLINE && cur.winner == null) {
                    cur.copy(lastActionNotification = "🟢 Reconnected! Match continues.", errorMsg = null)
                } else cur
            }
        }
    }

    fun updateOpponentProfile(name: String, avatar: String) {
        _gameState.update { cur ->
            if (cur.gameMode == GameMode.ONLINE) {
                val p1Name = if (cur.myPlayerNum == 1) cur.player1Name else name
                val p1Avatar = if (cur.myPlayerNum == 1) cur.player1Avatar else avatar
                val p2Name = if (cur.myPlayerNum == 1) name else cur.player2Name
                val p2Avatar = if (cur.myPlayerNum == 1) avatar else cur.player2Avatar
                cur.copy(
                    opponentName = name,
                    opponentAvatar = avatar,
                    player1Name = p1Name,
                    player1Avatar = p1Avatar,
                    player2Name = p2Name,
                    player2Avatar = p2Avatar
                )
            } else {
                cur
            }
        }
    }

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _appSettings = MutableStateFlow(
        AppSettings(
            classicControls = prefs.getBoolean("classic_controls", true),
            soundEnabled = prefs.getBoolean("sound_enabled", true),
            memeSoundEnabled = prefs.getBoolean("meme_sound_enabled", true),
            darkTheme = false,
            timingEnabled = prefs.getBoolean("timing_enabled", false)
        )
    )
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    private var turnTimerJob: Job? = null
    private val _turnSecondsRemaining = MutableStateFlow(10f)
    val turnSecondsRemaining: StateFlow<Float> = _turnSecondsRemaining.asStateFlow()

    fun startTurnTimer() {
        turnTimerJob?.cancel()
        val settings = _appSettings.value
        val state = _gameState.value

        val isTimingActive = if (state.gameMode == GameMode.ONLINE) {
            state.isTimerEnabled
        } else {
            settings.timingEnabled
        }

        if (!isTimingActive || state.winner != null) {
            _turnSecondsRemaining.value = 10f
            return
        }

        // In VS_COMPUTER, when AI takes its turn, pause timer for AI thinking
        if (state.gameMode == GameMode.VS_COMPUTER && state.currentPlayer == 2) {
            _turnSecondsRemaining.value = 10f
            return
        }

        // In Online mode with real peer, if remote player's turn, wait for their action
        if (state.gameMode == GameMode.ONLINE && state.currentPlayer != state.myPlayerNum && state.isRealPeerConnected) {
            _turnSecondsRemaining.value = 10f
            return
        }

        turnTimerJob = viewModelScope.launch {
            _turnSecondsRemaining.value = 10f
            val totalTicks = 100 // 100 * 100ms = 10.0 seconds
            for (tick in 1..totalTicks) {
                delay(100)
                val curState = _gameState.value
                val curSettings = _appSettings.value
                val curTimingActive = if (curState.gameMode == GameMode.ONLINE) {
                    curState.isTimerEnabled
                } else {
                    curSettings.timingEnabled
                }
                if (curState.winner != null || !curTimingActive) {
                    _turnSecondsRemaining.value = 10f
                    return@launch
                }
                if (curState.isAiThinking) {
                    continue
                }
                _turnSecondsRemaining.value = ((totalTicks - tick) / 10f).coerceAtLeast(0f)
            }
            _turnSecondsRemaining.value = 0f
            val curPlayer = _gameState.value.currentPlayer
            applyPassTurn(curPlayer)
        }
    }

    fun updateSettings(settings: AppSettings) {
        val cleanSettings = settings.copy(darkTheme = false)
        _appSettings.value = cleanSettings
        prefs.edit()
            .putBoolean("classic_controls", cleanSettings.classicControls)
            .putBoolean("sound_enabled", cleanSettings.soundEnabled)
            .putBoolean("meme_sound_enabled", cleanSettings.memeSoundEnabled)
            .putBoolean("timing_enabled", cleanSettings.timingEnabled)
            .putBoolean("dark_theme", false)
            .remove("board_theme")
            .apply()
        startTurnTimer()
    }

    fun isFirstTimeUser(): Boolean {
        return prefs.getBoolean("is_first_launch_prompt_needed", true)
    }

    fun setFirstLaunchCompleted() {
        prefs.edit().putBoolean("is_first_launch_prompt_needed", false).apply()
    }

    fun updateUserProfile(name: String, avatar: String) {
        userProfileRepository.updateProfile(name, avatar)
    }

    fun resetUserStats() {
        userProfileRepository.resetStats()
    }

    fun startGame(mode: GameMode, difficulty: AIDifficulty = AIDifficulty.MEDIUM) {
        val settings = _appSettings.value
        val profile = userProfile.value
        soundManager.onNewGameStarted(settings.soundEnabled, settings.memeSoundEnabled)
        val initialWalls = if (mode == GameMode.QUICK_BLITZ) 5 else 10
        val generatedObstacles = if (mode == GameMode.OBSTACLE_RUSH) {
            // Carefully placed obstacles in the neutral zone (rows 3, 4, 5) ensuring open lanes
            listOf(Pair(2, 3), Pair(6, 3), Pair(4, 4), Pair(2, 5), Pair(6, 5))
        } else {
            emptyList()
        }

        _gameState.value = GameState(
            player1 = Player(1, 4, 8, initialWalls, Player1Color),
            player2 = Player(2, 4, 0, initialWalls, Player2Color),
            gameMode = mode,
            aiDifficulty = difficulty,
            obstacles = generatedObstacles,
            player1Name = profile.name,
            player1Avatar = profile.avatar,
            opponentName = if (mode == GameMode.VS_COMPUTER) "BlockBot AI" else "Player 2",
            opponentAvatar = if (mode == GameMode.VS_COMPUTER) "🤖" else "♜"
        )
        startTurnTimer()
    }

    fun startOnlineMatchmaking(timerEnabled: Boolean = false, onActionReceived: ((String) -> Unit)? = null) {
        val profile = userProfile.value
        peerJsWebRtcManager.startMatchmaking(profile, timerEnabled) { actionJson ->
            handleRemoteAction(actionJson)
            onActionReceived?.invoke(actionJson)
        }
    }

    fun createCustomRoom(roomCode: String, timerEnabled: Boolean = false, onActionReceived: ((String) -> Unit)? = null) {
        val profile = userProfile.value
        peerJsWebRtcManager.createCustomRoom(roomCode, profile, timerEnabled) { actionJson ->
            handleRemoteAction(actionJson)
            onActionReceived?.invoke(actionJson)
        }
    }

    fun joinCustomRoom(roomCode: String, onActionReceived: ((String) -> Unit)? = null) {
        val profile = userProfile.value
        peerJsWebRtcManager.joinCustomRoom(roomCode, profile) { actionJson ->
            handleRemoteAction(actionJson)
            onActionReceived?.invoke(actionJson)
        }
    }

    fun startOnlineGame(
        opponentName: String,
        opponentAvatar: String = "♟",
        isRealPeer: Boolean,
        ping: Int,
        roomCode: String? = null,
        isHost: Boolean = true,
        timerEnabled: Boolean = false
    ) {
        val settings = _appSettings.value
        val profile = userProfile.value
        soundManager.onNewGameStarted(settings.soundEnabled, settings.memeSoundEnabled)

        val myPlayerNum = if (isHost) 1 else 2
        val p1Name = if (isHost) profile.name else opponentName
        val p1Avatar = if (isHost) profile.avatar else opponentAvatar
        val p2Name = if (isHost) opponentName else profile.name
        val p2Avatar = if (isHost) opponentAvatar else profile.avatar

        _gameState.value = GameState(
            gameMode = GameMode.ONLINE,
            aiDifficulty = AIDifficulty.HARD,
            player1Name = p1Name,
            player1Avatar = p1Avatar,
            player2Name = p2Name,
            player2Avatar = p2Avatar,
            myPlayerNum = myPlayerNum,
            isTimerEnabled = timerEnabled,
            opponentName = opponentName,
            opponentAvatar = opponentAvatar,
            opponentPing = ping,
            isRealPeerConnected = isRealPeer,
            roomCode = roomCode
        )
        startTurnTimer()
    }

    private fun broadcastOnlineSyncAction(
        actionType: String,
        actingPlayer: Int,
        moveX: Int? = null,
        moveY: Int? = null,
        wall: Wall? = null,
        state: GameState
    ) {
        if (state.gameMode != GameMode.ONLINE || !state.isRealPeerConnected) return
        try {
            val json = org.json.JSONObject()
            json.put("type", "SYNC_STATE")
            json.put("actionType", actionType)
            json.put("actingPlayer", actingPlayer)
            if (moveX != null) json.put("moveX", moveX)
            if (moveY != null) json.put("moveY", moveY)
            if (wall != null) {
                json.put("wallX", wall.x)
                json.put("wallY", wall.y)
                json.put("wallIsH", wall.isHorizontal)
            }
            json.put("p1x", state.player1.x)
            json.put("p1y", state.player1.y)
            json.put("p1w", state.player1.walls)
            json.put("p2x", state.player2.x)
            json.put("p2y", state.player2.y)
            json.put("p2w", state.player2.walls)
            json.put("currentPlayer", state.currentPlayer)
            json.put("winner", state.winner ?: 0)
            json.put("moveCount", state.moveCount)

            val wallsArr = org.json.JSONArray()
            for (w in state.walls) {
                val wObj = org.json.JSONObject()
                wObj.put("x", w.x)
                wObj.put("y", w.y)
                wObj.put("h", w.isHorizontal)
                wallsArr.put(wObj)
            }
            json.put("walls", wallsArr)

            peerJsWebRtcManager.sendGameAction(json.toString())
        } catch (e: Exception) {
            android.util.Log.e("GameLogic", "Failed to broadcast sync action", e)
        }
    }

    private fun handleRemoteAction(actionJson: String) {
        try {
            val json = org.json.JSONObject(actionJson)
            val type = json.optString("type")

            if (type == "OPPONENT_QUIT") {
                val oppName = _gameState.value.opponentName
                val isTimeout = json.optString("reason") == "TIMEOUT"
                val notification = if (isTimeout) {
                    "📡 $oppName lost connection."
                } else {
                    "🏳️ $oppName left the match."
                }
                val errorMsg = if (isTimeout) {
                    "$oppName disconnected due to network timeout."
                } else {
                    "$oppName left the room."
                }
                val wasGameActive = _gameState.value.winner == null
                _gameState.update { cur ->
                    val isFirstQuitWin = cur.winner == null
                    val updatedWins = if (isFirstQuitWin) cur.myWins + 1 else cur.myWins
                    cur.copy(
                        winner = cur.winner ?: cur.myPlayerNum,
                        myWins = updatedWins,
                        isRealPeerConnected = false,
                        rematchRequestedByOpponent = false,
                        lastActionNotification = notification,
                        errorMsg = errorMsg
                    )
                }
                turnTimerJob?.cancel()
                if (wasGameActive) {
                    val settings = _appSettings.value
                    soundManager.onVictoryClimax(settings.soundEnabled, settings.memeSoundEnabled)
                    userProfileRepository.recordGameFinished(won = true)
                }
                return
            }

            if (type == "REMATCH_REQUEST") {
                peerJsWebRtcManager.notifyActionReceived()
                val cur = _gameState.value
                if (cur.rematchRequestedByMe) {
                    // Both players have requested rematch! Confirm and start immediately.
                    try {
                        val resp = org.json.JSONObject()
                        resp.put("type", "REMATCH_CONFIRM")
                        peerJsWebRtcManager.sendGameAction(resp.toString())
                    } catch (e: Exception) {
                        android.util.Log.e("GameLogic", "Failed to broadcast REMATCH_CONFIRM", e)
                    }
                    startRematchGame()
                } else {
                    _gameState.update {
                        it.copy(
                            rematchRequestedByOpponent = true,
                            lastActionNotification = "🔥 ${it.opponentName} wants a rematch!"
                        )
                    }
                    val settings = _appSettings.value
                    soundManager.onStrategicLeap(settings.soundEnabled, settings.memeSoundEnabled)
                }
                return
            }

            if (type == "REMATCH_CONFIRM") {
                peerJsWebRtcManager.notifyActionReceived()
                startRematchGame()
                return
            }

            if (type == "SYNC_STATE") {
                peerJsWebRtcManager.notifyActionReceived()
                val actionType = json.optString("actionType")
                val actingPlayer = json.optInt("actingPlayer", 1)
                val p1x = json.getInt("p1x")
                val p1y = json.getInt("p1y")
                val p1w = json.getInt("p1w")
                val p2x = json.getInt("p2x")
                val p2y = json.getInt("p2y")
                val p2w = json.getInt("p2w")
                val nextCurPlayer = json.getInt("currentPlayer")
                val rawWinner = json.getInt("winner")
                val winner = if (rawWinner in 1..2) rawWinner else null
                val moveCount = json.optInt("moveCount", _gameState.value.moveCount + 1)

                val wallsArr = json.getJSONArray("walls")
                val newWalls = mutableListOf<Wall>()
                for (i in 0 until wallsArr.length()) {
                    val wObj = wallsArr.getJSONObject(i)
                    newWalls.add(Wall(wObj.getInt("x"), wObj.getInt("y"), wObj.getBoolean("h")))
                }

                var lastWall: Wall? = null
                if (json.has("wallX") && json.has("wallY")) {
                    lastWall = Wall(json.getInt("wallX"), json.getInt("wallY"), json.getBoolean("wallIsH"))
                }

                var lastMovePos: Pair<Int, Int>? = null
                if (json.has("moveX") && json.has("moveY")) {
                    lastMovePos = Pair(json.getInt("moveX"), json.getInt("moveY"))
                }

                val actingName = if (actingPlayer == 1) _gameState.value.player1Name else _gameState.value.player2Name
                val actionDesc = when (actionType) {
                    "MOVE" -> "♟ $actingName moved pawn"
                    "WALL" -> "🧱 $actingName placed a wall"
                    "TIMEOUT_PASS" -> "⏰ $actingName ran out of time"
                    else -> "Turn changed"
                }

                val isWonByMe = (winner != null && winner == _gameState.value.myPlayerNum)
                val isWonByOpp = (winner != null && winner != _gameState.value.myPlayerNum)

                _gameState.update { cur ->
                    val p1 = cur.player1.copy(x = p1x, y = p1y, walls = p1w)
                    val p2 = cur.player2.copy(x = p2x, y = p2y, walls = p2w)
                    val newMyWins = if (isWonByMe && cur.winner == null) cur.myWins + 1 else cur.myWins
                    val newOppWins = if (isWonByOpp && cur.winner == null) cur.opponentWins + 1 else cur.opponentWins
                    cur.copy(
                        player1 = p1,
                        player2 = p2,
                        walls = newWalls,
                        currentPlayer = nextCurPlayer,
                        winner = winner,
                        myWins = newMyWins,
                        opponentWins = newOppWins,
                        moveCount = moveCount,
                        lastActionNotification = actionDesc,
                        lastPlacedWall = lastWall ?: cur.lastPlacedWall,
                        lastMovedPlayerPos = lastMovePos ?: cur.lastMovedPlayerPos,
                        errorMsg = null
                    )
                }

                val settings = _appSettings.value
                if (winner != null) {
                    soundManager.onVictoryClimax(settings.soundEnabled, settings.memeSoundEnabled)
                    val didIWin = (winner == _gameState.value.myPlayerNum)
                    userProfileRepository.recordGameFinished(won = didIWin)
                    turnTimerJob?.cancel()
                } else {
                    if (actionType == "WALL") {
                        soundManager.onWallPlacedImpact(settings.soundEnabled, settings.memeSoundEnabled)
                    } else {
                        soundManager.onStrategicLeap(settings.soundEnabled, settings.memeSoundEnabled)
                    }
                    startTurnTimer()
                }
            } else if (type == "MOVE") {
                val x = json.getInt("x")
                val y = json.getInt("y")
                val opponentPlayerId = if (_gameState.value.myPlayerNum == 1) 2 else 1
                applyMove(opponentPlayerId, x, y)
            } else if (type == "WALL") {
                val x = json.getInt("x")
                val y = json.getInt("y")
                val isHoriz = json.getBoolean("isHorizontal")
                val opponentPlayerId = if (_gameState.value.myPlayerNum == 1) 2 else 1
                applyWall(opponentPlayerId, Wall(x, y, isHoriz))
            } else if (type == "TIMEOUT_PASS") {
                val opponentPlayerId = if (_gameState.value.myPlayerNum == 1) 2 else 1
                applyPassTurn(opponentPlayerId)
            }
        } catch (e: Exception) {
            android.util.Log.e("GameViewModel", "Failed to parse remote action", e)
        }
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
            is GameAction.PassTurn -> {
                if (action.player != state.currentPlayer) return
                applyPassTurn(action.player)
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
     * Handles instant Rematch / Play Again in online matches (both Custom Room and Quick Match).
     * If opponent already requested a rematch, immediately starts round 2.
     * Otherwise broadcasts REMATCH_REQUEST to the opponent so they can accept.
     */
    fun requestOnlineRematch() {
        val current = _gameState.value
        if (current.gameMode != GameMode.ONLINE) {
            resetGame()
            return
        }
        if (!current.isRealPeerConnected) {
            // If playing with offline AI fallback or peer disconnected
            startRematchGame()
            return
        }

        if (current.rematchRequestedByOpponent) {
            // Opponent already requested a rematch! Confirm and start immediately.
            try {
                val json = org.json.JSONObject()
                json.put("type", "REMATCH_CONFIRM")
                peerJsWebRtcManager.sendGameAction(json.toString())
            } catch (e: Exception) {
                android.util.Log.e("GameLogic", "Failed to broadcast REMATCH_CONFIRM", e)
            }
            startRematchGame()
        } else {
            // Send rematch proposal to opponent
            _gameState.update { it.copy(rematchRequestedByMe = true) }
            try {
                val json = org.json.JSONObject()
                json.put("type", "REMATCH_REQUEST")
                peerJsWebRtcManager.sendGameAction(json.toString())
            } catch (e: Exception) {
                android.util.Log.e("GameLogic", "Failed to broadcast REMATCH_REQUEST", e)
            }
        }
    }

    /**
     * Resets the board for the next round without disconnecting WebRTC peer connection.
     * Both players stay seamlessly in the custom room.
     */
    fun startRematchGame() {
        turnTimerJob?.cancel()
        val settings = _appSettings.value
        soundManager.onNewGameStarted(settings.soundEnabled, settings.memeSoundEnabled)

        _gameState.update { cur ->
            cur.copy(
                player1 = Player(1, 4, 8, 10, Player1Color),
                player2 = Player(2, 4, 0, 10, Player2Color),
                walls = emptyList(),
                currentPlayer = 1,
                winner = null,
                errorMsg = null,
                moveCount = 0,
                lastActionNotification = "🎮 Round ${cur.roundsPlayed + 1} Started! Good luck!",
                lastPlacedWall = null,
                lastMovedPlayerPos = null,
                rematchRequestedByMe = false,
                rematchRequestedByOpponent = false,
                roundsPlayed = cur.roundsPlayed + 1
            )
        }
        startTurnTimer()
    }

    /**
     * Completely quits the current game, stops any active turn timers,
     * fully terminates and disconnects WebRTC background sockets if in online mode,
     * and resets the game state so that new matches start with a completely fresh slate.
     */
    fun quitCurrentGame() {
        turnTimerJob?.cancel()
        val isOnline = _gameState.value.gameMode == GameMode.ONLINE
        if (isOnline) {
            peerJsWebRtcManager.disconnectAndResetAll(notifyOpponent = true)
        }
        val profile = userProfile.value
        _gameState.value = GameState(
            player1 = Player(1, 4, 8, 10, Player1Color),
            player2 = Player(2, 4, 0, 10, Player2Color),
            gameMode = GameMode.LOCAL_PASS_AND_PLAY,
            player1Name = profile.name,
            player1Avatar = profile.avatar,
            opponentName = "Player 2",
            opponentAvatar = "♜",
            winner = null,
            lastActionNotification = null,
            errorMsg = null
        )
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

    private fun applyPassTurn(playerId: Int) {
        val state = _gameState.value
        if (state.winner != null || state.isAiThinking) return
        if (playerId != state.currentPlayer) return

        val nextPlayer = if (playerId == 1) 2 else 1
        val actingName = if (playerId == 1) state.player1Name else state.player2Name
        val newState = state.copy(
            currentPlayer = nextPlayer,
            errorMsg = "⏰ Time's up! Turn passed to Player $nextPlayer",
            lastActionNotification = "⏰ $actingName ran out of time",
            moveCount = state.moveCount + 1
        )
        _gameState.value = newState

        if (state.gameMode == GameMode.ONLINE && playerId == state.myPlayerNum && state.isRealPeerConnected) {
            broadcastOnlineSyncAction("TIMEOUT_PASS", playerId, state = newState)
        }

        checkTriggerAi(nextPlayer, null)
        startTurnTimer()
    }

    private fun applyMove(playerId: Int, nx: Int, ny: Int) {
        val state = _gameState.value
        val p1 = state.player1.copy()
        val p2 = state.player2.copy()
        var winner = state.winner

        val oldX = if (playerId == 1) p1.x else p2.x
        val oldY = if (playerId == 1) p1.y else p2.y
        val opponent = if (playerId == 1) p2 else p1

        // Check if this move was a strategic leap over opponent
        val isLeapJump = (Math.abs(nx - oldX) == 2 && ny == oldY) ||
                         (Math.abs(ny - oldY) == 2 && nx == oldX) ||
                         (Math.abs(nx - oldX) == 1 && Math.abs(ny - oldY) == 1 &&
                          (opponent.x == nx && opponent.y == oldY || opponent.x == oldX && opponent.y == ny))

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
        val actingName = if (playerId == 1) state.player1Name else state.player2Name

        val isWonByMe = (winner != null && winner == state.myPlayerNum)
        val isWonByOpp = (winner != null && winner != state.myPlayerNum)
        val newMyWins = if (isWonByMe && state.winner == null) state.myWins + 1 else state.myWins
        val newOppWins = if (isWonByOpp && state.winner == null) state.opponentWins + 1 else state.opponentWins

        val newState = state.copy(
            player1 = p1,
            player2 = p2,
            currentPlayer = nextPlayer,
            winner = winner,
            myWins = newMyWins,
            opponentWins = newOppWins,
            errorMsg = null,
            lastMovedPlayerPos = Pair(nx, ny),
            lastActionNotification = "♟ $actingName moved",
            moveCount = state.moveCount + 1
        )
        _gameState.value = newState

        val currentSettings = _appSettings.value
        if (winner != null) {
            soundManager.onVictoryClimax(currentSettings.soundEnabled, currentSettings.memeSoundEnabled)
            if (state.gameMode != GameMode.LOCAL_PASS_AND_PLAY) {
                userProfileRepository.recordGameFinished(won = (winner == state.myPlayerNum))
            }
        } else if (isLeapJump) {
            soundManager.onStrategicLeap(currentSettings.soundEnabled, currentSettings.memeSoundEnabled)
        }

        if (state.gameMode == GameMode.ONLINE && playerId == state.myPlayerNum && state.isRealPeerConnected) {
            broadcastOnlineSyncAction("MOVE", playerId, moveX = nx, moveY = ny, state = newState)
        }

        checkTriggerAi(nextPlayer, winner)
        if (winner == null) {
            startTurnTimer()
        } else {
            turnTimerJob?.cancel()
            _turnSecondsRemaining.value = 10f
        }
    }

    private fun applyWall(playerId: Int, wall: Wall) {
        val state = _gameState.value
        val p1 = state.player1.copy()
        val p2 = state.player2.copy()

        // Measure opponent's path before wall
        val opponent = if (playerId == 1) p2 else p1
        val opponentGoal = if (playerId == 1) 8 else 0
        val distBefore = shortestPathDistance(opponent.x, opponent.y, opponentGoal, state.walls)

        if (playerId == 1) {
            p1.walls--
        } else {
            p2.walls--
        }
        if (playerId == state.myPlayerNum) {
            userProfileRepository.recordWallPlaced()
        }

        val nextPlayer = if (playerId == 1) 2 else 1
        val newWalls = state.walls + wall

        // Measure opponent's path after wall
        val distAfter = shortestPathDistance(opponent.x, opponent.y, opponentGoal, newWalls)
        val pathIncrease = distAfter - distBefore
        val actingName = if (playerId == 1) state.player1Name else state.player2Name

        val newState = state.copy(
            player1 = p1,
            player2 = p2,
            walls = newWalls,
            currentPlayer = nextPlayer,
            errorMsg = null,
            lastPlacedWall = wall,
            lastActionNotification = "🧱 $actingName placed a wall",
            moveCount = state.moveCount + 1
        )
        _gameState.value = newState

        // Trigger intelligent sound effect based on actual board impact
        val currentSettings = _appSettings.value
        if (distBefore in 1..2 && pathIncrease >= 2) {
            // Emergency panic block when opponent was right at goal line!
            soundManager.onCloseCallPanic(currentSettings.soundEnabled, currentSettings.memeSoundEnabled)
        } else if (pathIncrease >= 3) {
            // Brutal unexpected block (+3 to +5 detour)
            soundManager.onBrutalBlock(currentSettings.soundEnabled, currentSettings.memeSoundEnabled)
        } else if (distAfter >= 12 && pathIncrease >= 2) {
            // Huge tortuous labyrinth detour
            soundManager.onLongDetour(currentSettings.soundEnabled, currentSettings.memeSoundEnabled)
        } else {
            soundManager.onWallPlacedImpact(currentSettings.soundEnabled, currentSettings.memeSoundEnabled)
        }

        if (state.gameMode == GameMode.ONLINE && playerId == state.myPlayerNum && state.isRealPeerConnected) {
            broadcastOnlineSyncAction("WALL", playerId, wall = wall, state = newState)
        }

        checkTriggerAi(nextPlayer, null)
        startTurnTimer()
    }

    private fun checkTriggerAi(nextPlayer: Int, winner: Int?) {
        val state = _gameState.value
        if (winner == null && nextPlayer == 2) {
            if (state.gameMode == GameMode.VS_COMPUTER) {
                viewModelScope.launch {
                    _gameState.update { it.copy(isAiThinking = true) }
                    delay(600) // Natural thinking delay
                    withContext(Dispatchers.Default) {
                        executeAiTurn()
                    }
                    _gameState.update { it.copy(isAiThinking = false) }
                }
            } else if (state.gameMode == GameMode.ONLINE && !state.isRealPeerConnected) {
                // Seamless fallback to AI opponent when no peer was found!
                // Natural human thinking variance: 1200ms - 2200ms
                viewModelScope.launch {
                    _gameState.update { it.copy(isAiThinking = true) }
                    val humanDelay = Random.nextLong(1200, 2200)
                    delay(humanDelay)
                    withContext(Dispatchers.Default) {
                        executeAiTurn()
                    }
                    _gameState.update { it.copy(isAiThinking = false) }
                }
            }
        }
    }

    private fun executeAiTurn() {
        val state = _gameState.value
        if (state.winner != null || state.currentPlayer != 2) return

        if (state.gameMode == GameMode.ONLINE) {
            executeAiGenius(state)
            return
        }

        when (state.aiDifficulty) {
            AIDifficulty.EASY -> executeAiEasy(state)
            AIDifficulty.MEDIUM -> executeAiMedium(state)
            AIDifficulty.HARD -> executeAiHard(state)
        }
    }

    private fun executeAiGenius(state: GameState) {
        val p1Dist = shortestPathDistance(state.player1.x, state.player1.y, 0, state.walls)
        val p2Dist = shortestPathDistance(state.player2.x, state.player2.y, 8, state.walls)
        val moves = getValidMoves(state, 2)

        // 1. Immediate Win: If any move reaches row 8, play it instantly!
        val winningMove = moves.firstOrNull { it.second == 8 }
        if (winningMove != null) {
            applyMove(2, winningMove.first, winningMove.second)
            return
        }

        // 2. Critical Goal Defense: If Player 1 is about to win (within 1 or 2 steps from row 0)
        // and Player 2 has walls, find a wall that maximizes Player 1's detour
        if (state.player2.walls > 0 && p1Dist <= 2) {
            val candidateWalls = getGeniusCandidateWalls(state)
            var bestEmergencyWall: Wall? = null
            var maxDetour = p1Dist
            for (w in candidateWalls) {
                val newWalls = state.walls + w
                val newP1 = shortestPathDistance(state.player1.x, state.player1.y, 0, newWalls)
                val newP2 = shortestPathDistance(state.player2.x, state.player2.y, 8, newWalls)
                if (newP1 != Int.MAX_VALUE && newP2 != Int.MAX_VALUE && newP1 > maxDetour) {
                    maxDetour = newP1
                    bestEmergencyWall = w
                }
            }
            if (bestEmergencyWall != null) {
                applyWall(2, bestEmergencyWall)
                return
            }
        }

        // 3. High-IQ Strategic Wall Trap Evaluation
        var bestWallAction: Wall? = null
        var bestWallAdvantage = 1 // only place wall if it yields net positive advantage (>= 2)

        if (state.player2.walls > 0) {
            val candidateWalls = getGeniusCandidateWalls(state)
            for (w in candidateWalls) {
                val newWalls = state.walls + w
                val newP1 = shortestPathDistance(state.player1.x, state.player1.y, 0, newWalls)
                val newP2 = shortestPathDistance(state.player2.x, state.player2.y, 8, newWalls)
                if (newP1 != Int.MAX_VALUE && newP2 != Int.MAX_VALUE) {
                    val p1Increase = newP1 - p1Dist
                    val p2Increase = newP2 - p2Dist
                    val netAdvantage = p1Increase - p2Increase
                    // Reward blocking walls that increase opponent's path without blocking self
                    if (netAdvantage > bestWallAdvantage) {
                        bestWallAdvantage = netAdvantage
                        bestWallAction = w
                    }
                }
            }
        }

        // If a high-advantage wall was found (adv >= 2, meaning opponent is slowed down significantly)
        // AND Player 2 isn't already far ahead in a clear sprint
        val shouldPlaceWall = bestWallAction != null && bestWallAdvantage >= 2 && (p1Dist <= p2Dist || bestWallAdvantage >= 3)
        if (shouldPlaceWall && bestWallAction != null) {
            applyWall(2, bestWallAction)
            return
        }

        // 4. Optimal Pawn Move (Shortest Path + Forward Momentum + Leap Attacks)
        if (moves.isNotEmpty()) {
            val bestMove = moves.minWithOrNull(
                compareBy<Pair<Int, Int>> { (mx, my) ->
                    shortestPathDistance(mx, my, 8, state.walls)
                }.thenByDescending { (mx, my) ->
                    // Favor forward vertical progress toward row 8
                    my - state.player2.y
                }.thenByDescending { (mx, my) ->
                    // Favor leap jumps over opponent
                    if (Math.abs(my - state.player2.y) == 2 || (Math.abs(my - state.player2.y) == 1 && Math.abs(mx - state.player2.x) == 1)) 1 else 0
                }
            ) ?: moves.first()

            applyMove(2, bestMove.first, bestMove.second)
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
            is GameAction.PassTurn -> applyPassTurn(2)
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

    private fun getGeniusCandidateWalls(state: GameState): List<Wall> {
        val list = mutableListOf<Wall>()
        val p1x = state.player1.x
        val p1y = state.player1.y

        // Scan smart zones around Player 1, especially in front (toward row 0) and goal line
        for (dy in -3..1) {
            for (dx in -2..2) {
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

        // Also evaluate goal row blocks (row 0)
        for (x in 0..7) {
            val wH = Wall(x, 0, true)
            if (checkWallPlacement(state, wH) == null) list.add(wH)
        }

        return list.distinct()
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
        if (!hasPath(state.player1.x, state.player1.y, 0, newWalls, state.obstacles) || !hasPath(state.player2.x, state.player2.y, 8, newWalls, state.obstacles)) {
            return "Cannot block all paths to the goal!"
        }

        return null
    }

    private fun isValidMove(state: GameState, playerId: Int, nx: Int, ny: Int): Boolean {
        if (nx !in 0..8 || ny !in 0..8) return false
        // Tiles with rock obstacles cannot be stepped onto
        if (state.obstacles.contains(Pair(nx, ny))) return false

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

    private fun hasPath(startX: Int, startY: Int, goalY: Int, walls: List<Wall>, obstacles: List<Pair<Int, Int>> = emptyList()): Boolean {
        return shortestPathDistance(startX, startY, goalY, walls, obstacles) != Int.MAX_VALUE
    }

    fun shortestPathDistance(startX: Int, startY: Int, goalY: Int, walls: List<Wall>, obstacles: List<Pair<Int, Int>> = emptyList()): Int {
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
                val nx = x
                val ny = y + 1
                if (!obstacles.contains(Pair(nx, ny)) && dist[next] == Int.MAX_VALUE && !isWallBetween(walls, x, y, nx, ny)) {
                    dist[next] = d + 1
                    queue[tail++] = next
                }
            }
            // Up (y - 1)
            if (y > 0) {
                val next = curr - 1
                val nx = x
                val ny = y - 1
                if (!obstacles.contains(Pair(nx, ny)) && dist[next] == Int.MAX_VALUE && !isWallBetween(walls, x, y, nx, ny)) {
                    dist[next] = d + 1
                    queue[tail++] = next
                }
            }
            // Right (x + 1)
            if (x < 8) {
                val next = curr + 9
                val nx = x + 1
                val ny = y
                if (!obstacles.contains(Pair(nx, ny)) && dist[next] == Int.MAX_VALUE && !isWallBetween(walls, x, y, nx, ny)) {
                    dist[next] = d + 1
                    queue[tail++] = next
                }
            }
            // Left (x - 1)
            if (x > 0) {
                val next = curr - 9
                val nx = x - 1
                val ny = y
                if (!obstacles.contains(Pair(nx, ny)) && dist[next] == Int.MAX_VALUE && !isWallBetween(walls, x, y, nx, ny)) {
                    dist[next] = d + 1
                    queue[tail++] = next
                }
            }
        }
        return Int.MAX_VALUE
    }

    override fun onCleared() {
        super.onCleared()
        soundManager.release()
        peerJsWebRtcManager.cleanup()
    }
}
