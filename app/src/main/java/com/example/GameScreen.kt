package com.example
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.example.ui.AppSettingsDialog
import com.example.ui.BlockPathLogo
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PlayerInteractionMode {
    MOVE_PAWN,
    PLACE_WALL
}

enum class SelectedAction {
    MOVE_PAWN,
    HORIZONTAL_WALL,
    VERTICAL_WALL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    gameViewModel: GameViewModel,
    onBack: () -> Unit
) {
    val gameState by gameViewModel.gameState.collectAsState()
    val appSettings by gameViewModel.appSettings.collectAsState()
    val voiceChatState by gameViewModel.voiceChatState.collectAsState()
    val liveFloatingEmojis by gameViewModel.liveFloatingEmojis.collectAsState()
    val emojiCooldownSeconds by gameViewModel.emojiCooldownSeconds.collectAsState()
    var micPermissionNotice by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val activity = context as? Activity
    val app = context.applicationContext as? BlockPathApplication

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            micPermissionNotice = null
            gameViewModel.startVoiceChat()
        } else {
            micPermissionNotice = "Microphone permission is required for live voice chat."
        }
    }

    LaunchedEffect(micPermissionNotice) {
        if (micPermissionNotice != null) {
            delay(3200)
            micPermissionNotice = null
        }
    }

    var interactionMode by remember { mutableStateOf(PlayerInteractionMode.MOVE_PAWN) }
    var activeAction by remember { mutableStateOf(SelectedAction.MOVE_PAWN) }
    var isWallHorizontal by remember { mutableStateOf(true) }
    var pendingWall by remember { mutableStateOf<Wall?>(null) }
    var wallValidationMsg by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showRewardDialog by remember { mutableStateOf(false) }
    var showConfirmBackDialog by remember { mutableStateOf(false) }
    var showConfirmResetDialog by remember { mutableStateOf(false) }
    var showConfirmForfeitDialog by remember { mutableStateOf(false) }

    var invalidCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val shakeOffset = remember { Animatable(0f) }
    val invalidCellAlpha = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val haptic = LocalHapticFeedback.current

    // Auto-clear error message after 1.8 seconds
    LaunchedEffect(gameState.errorMsg) {
        if (gameState.errorMsg != null) {
            delay(1800)
            gameViewModel.clearError()
        }
    }

    // Reset interaction mode & action selection when player turn changes
    LaunchedEffect(gameState.currentPlayer) {
        pendingWall = null
        wallValidationMsg = null
        activeAction = SelectedAction.MOVE_PAWN
        interactionMode = PlayerInteractionMode.MOVE_PAWN
        isWallHorizontal = true
    }

    // 10-Second Turn Countdown Timer (managed centrally by ViewModel, auto-alternates between users)
    val timerSecondsRemaining by gameViewModel.turnSecondsRemaining.collectAsState()

    LaunchedEffect(Unit) {
        gameViewModel.startTurnTimer()
    }

    // Haptic feedback when time expires or turn passes
    LaunchedEffect(timerSecondsRemaining) {
        if (timerSecondsRemaining <= 0.05f && appSettings.timingEnabled && gameState.winner == null) {
            if (appSettings.soundEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    // Celebratory haptic on win
    LaunchedEffect(gameState.winner) {
        if (gameState.winner != null && appSettings.soundEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val validMoves = remember(gameState) {
        gameViewModel.getValidMovesForCurrentPlayer()
    }

    // Handle device/system back button
    BackHandler(enabled = true) {
        if (gameState.gameMode == GameMode.ONLINE && gameState.winner == null) {
            // In online matches, always confirm before quitting to prevent accidental exits
            showConfirmBackDialog = true
        } else if (gameState.winner == null && (gameState.moveCount > 0 || gameState.walls.isNotEmpty())) {
            showConfirmBackDialog = true
        } else {
            gameViewModel.quitCurrentGame()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BlockPath", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (appSettings.darkTheme) Color.White else WallColor) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (gameState.gameMode == GameMode.ONLINE && gameState.winner == null) {
                                // In online matches, always confirm before quitting to prevent accidental exits
                                showConfirmBackDialog = true
                            } else if (gameState.winner == null && (gameState.moveCount > 0 || gameState.walls.isNotEmpty())) {
                                showConfirmBackDialog = true
                            } else {
                                gameViewModel.quitCurrentGame()
                                onBack()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Menu", tint = if (appSettings.darkTheme) Color.White else WallColor)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.testTag("settings_btn")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = if (appSettings.darkTheme) Color.White else WallColor)
                    }
                    IconButton(
                        onClick = {
                            if (gameState.gameMode == GameMode.ONLINE) {
                                if (gameState.winner == null) {
                                    showConfirmForfeitDialog = true
                                } else {
                                    gameViewModel.requestOnlineRematch()
                                }
                            } else {
                                // If moves made or walls placed, ask confirmation before reset
                                if (gameState.winner == null && (gameState.moveCount > 0 || gameState.walls.isNotEmpty())) {
                                    showConfirmResetDialog = true
                                } else {
                                    pendingWall = null
                                    gameViewModel.resetGame()
                                }
                            }
                        },
                        modifier = Modifier.testTag("reset_game_btn")
                    ) {
                        if (gameState.gameMode == GameMode.ONLINE && gameState.winner == null) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = "Surrender Match",
                                tint = if (appSettings.darkTheme) Color.White else WallColor
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Restart Game",
                                tint = if (appSettings.darkTheme) Color.White else WallColor
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = if (appSettings.darkTheme) Color(0xFF0F172A) else AppBackground)
            )
        },
        containerColor = if (appSettings.darkTheme) Color(0xFF0F172A) else AppBackground
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val screenWidth = maxWidth
            val screenHeight = maxHeight
            val isPassAndPlay = gameState.gameMode == GameMode.LOCAL_PASS_AND_PLAY
            val isOnline = gameState.gameMode == GameMode.ONLINE

            // Dynamically calculate board size so all UI elements comfortably fit without squishing or overlapping
            val reservedVerticalHeight = when {
                isPassAndPlay -> 240.dp
                isOnline -> 220.dp
                else -> 190.dp
            }
            val availableBoardHeight = (screenHeight - reservedVerticalHeight).coerceAtLeast(180.dp)
            val maxBoardWidth = (screenWidth - 24.dp).coerceAtLeast(180.dp)
            val boardSize = minOf(maxBoardWidth, availableBoardHeight).coerceAtMost(430.dp)
            val needsScroll = screenHeight < 520.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (needsScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (needsScroll) Arrangement.spacedBy(6.dp) else Arrangement.SpaceBetween
            ) {
            // Player 2 Area (Top - Rotated 180 for Pass & Play)
            if (gameState.gameMode == GameMode.LOCAL_PASS_AND_PLAY) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { rotationZ = 180f },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PlayerInfoBar(
                        player = gameState.player2,
                        name = "Player 2",
                        avatar = "♜",
                        isCurrentTurn = gameState.currentPlayer == 2,
                        darkTheme = appSettings.darkTheme
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    PlayerActionControls(
                        gameState = gameState,
                        playerNum = 2,
                        activeAction = activeAction,
                        darkTheme = appSettings.darkTheme,
                        onSelectAction = { action ->
                            activeAction = action
                            when (action) {
                                SelectedAction.MOVE_PAWN -> {
                                    interactionMode = PlayerInteractionMode.MOVE_PAWN
                                    pendingWall = null
                                    wallValidationMsg = null
                                }
                                SelectedAction.HORIZONTAL_WALL -> {
                                    interactionMode = PlayerInteractionMode.PLACE_WALL
                                    isWallHorizontal = true
                                    pendingWall?.let { w ->
                                        val candidate = w.copy(isHorizontal = true)
                                        pendingWall = candidate
                                        wallValidationMsg = gameViewModel.checkWallPlacement(gameState, candidate)
                                    }
                                }
                                SelectedAction.VERTICAL_WALL -> {
                                    interactionMode = PlayerInteractionMode.PLACE_WALL
                                    isWallHorizontal = false
                                    pendingWall?.let { w ->
                                        val candidate = w.copy(isHorizontal = false)
                                        pendingWall = candidate
                                        wallValidationMsg = gameViewModel.checkWallPlacement(gameState, candidate)
                                    }
                                }
                            }
                        }
                    )
                }
            } else {
                val topPlayerNum = if (gameState.gameMode == GameMode.ONLINE && gameState.myPlayerNum == 2) 1 else 2
                val topPlayer = if (topPlayerNum == 1) gameState.player1 else gameState.player2
                val opponentDisplayName = if (gameState.gameMode == GameMode.ONLINE) gameState.opponentName else "AI"
                val opponentTag = if (gameState.gameMode == GameMode.ONLINE) "🟢 Online (${gameState.opponentPing}ms)" else null
                val topColorBadge = if (topPlayerNum == 1) "Blue" else "Red"
                PlayerInfoBar(
                    player = topPlayer,
                    name = opponentDisplayName,
                    avatar = gameState.opponentAvatar,
                    isCurrentTurn = gameState.currentPlayer == topPlayerNum,
                    colorBadge = topColorBadge,
                    extraTag = opponentTag,
                    darkTheme = appSettings.darkTheme
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Turn Indicator & Timing Countdown Bar
            TurnAndTimerBar(
                gameState = gameState,
                timingEnabled = if (gameState.gameMode == GameMode.ONLINE) gameState.isTimerEnabled else appSettings.timingEnabled,
                timerSecondsRemaining = timerSecondsRemaining,
                darkTheme = appSettings.darkTheme
            )

            // Real-time Action Notification Chip (when opponent moves or places a wall)
            if (gameState.lastActionNotification != null && gameState.winner == null) {
                Spacer(modifier = Modifier.height(3.dp))
                Surface(
                    color = if (appSettings.darkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (appSettings.darkTheme) Color(0xFF334155) else Color(0xFFCBD5E1))
                ) {
                    Text(
                        text = gameState.lastActionNotification ?: "",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (appSettings.darkTheme) Color(0xFFE2E8F0) else Color(0xFF334155),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Direction & Goal indicator (Clear player identity & orientation)
            val isGuestPlayer = (gameState.gameMode == GameMode.ONLINE && gameState.myPlayerNum == 2)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val myPawnText = when (gameState.gameMode) {
                    GameMode.ONLINE -> if (gameState.myPlayerNum == 1) "Your Pawn: Blue 🔵" else "Your Pawn: Red 🔴"
                    GameMode.VS_COMPUTER -> "Your Pawn: Blue 🔵"
                    else -> null
                }
                if (myPawnText != null) {
                    Text(
                        text = myPawnText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isGuestPlayer) Player2Color else Player1Color
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Text(
                    text = "🎯 GOAL: Reach Top Row ⬆️",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (appSettings.darkTheme) Color(0xFF34D399) else Color(0xFF059669)
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Board Container with dynamic responsive sizing
            BoxWithConstraints(
                modifier = Modifier
                    .size(boardSize)
                    .graphicsLayer { 
                        shadowElevation = 10.dp.toPx()
                        shape = RoundedCornerShape(16.dp)
                        clip = true
                        translationX = shakeOffset.value
                    }
                    .background(BoardBackground, RoundedCornerShape(16.dp))
                    .border(2.dp, Color(0xFFD4C3A3), RoundedCornerShape(16.dp))
                    .padding(6.dp)
                    .testTag("game_board_canvas")
            ) {
                val boardInnerWidth = maxWidth
                val boardInnerHeight = maxHeight
                val cellSize = boardInnerWidth / 9f

                val isMyTurnToAct = when (gameState.gameMode) {
                    GameMode.ONLINE -> gameState.currentPlayer == gameState.myPlayerNum
                    GameMode.VS_COMPUTER -> gameState.currentPlayer == 1 && !gameState.isAiThinking
                    else -> !gameState.isAiThinking
                }

                InteractiveBoard(
                    gameState = gameState,
                    validMoves = if (interactionMode == PlayerInteractionMode.MOVE_PAWN && isMyTurnToAct) validMoves else emptyList(),
                    invalidCell = invalidCell,
                    invalidCellAlpha = invalidCellAlpha.value,
                    interactionMode = interactionMode,
                    isWallHorizontal = isWallHorizontal,
                    pendingWall = pendingWall,
                    wallValidationMsg = wallValidationMsg,
                    appSettings = appSettings,
                    flipBoard = isGuestPlayer,
                    onCellClicked = { x, y ->
                        if (gameState.winner != null || gameState.isAiThinking || (gameState.gameMode == GameMode.ONLINE && gameState.currentPlayer != gameState.myPlayerNum)) return@InteractiveBoard
                        if (interactionMode == PlayerInteractionMode.MOVE_PAWN) {
                            val isLegal = validMoves.any { it.first == x && it.second == y }
                            if (isLegal) {
                                invalidCell = null
                                if (appSettings.soundEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                gameViewModel.handleAction(GameAction.Move(gameState.currentPlayer, x, y))
                            } else {
                                // Invalid cell tapped: trigger elegant cell flash, tactile rejection & micro-shake
                                invalidCell = Pair(x, y)
                                if (appSettings.soundEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coroutineScope.launch {
                                    invalidCellAlpha.snapTo(0.7f)
                                    invalidCellAlpha.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
                                }
                                coroutineScope.launch {
                                    shakeOffset.snapTo(0f)
                                    shakeOffset.animateTo(-6f, tween(40))
                                    shakeOffset.animateTo(6f, tween(40))
                                    shakeOffset.animateTo(-4f, tween(40))
                                    shakeOffset.animateTo(4f, tween(40))
                                    shakeOffset.animateTo(0f, tween(40))
                                }
                                gameViewModel.handleAction(GameAction.Move(gameState.currentPlayer, x, y))
                            }
                        }
                    },
                    onWallIntersectionClicked = { wx, wy ->
                        if (gameState.winner != null || gameState.isAiThinking || (gameState.gameMode == GameMode.ONLINE && gameState.currentPlayer != gameState.myPlayerNum)) return@InteractiveBoard
                        val currentWalls = if (gameState.currentPlayer == 1) gameState.player1.walls else gameState.player2.walls
                        if (currentWalls <= 0) {
                            wallValidationMsg = "No walls left!"
                            if (appSettings.soundEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            return@InteractiveBoard
                        }
                        val candidate = Wall(wx, wy, isWallHorizontal)
                        val err = gameViewModel.checkWallPlacement(gameState, candidate)
                        if (appSettings.soundEnabled) {
                            if (err != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            else haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        pendingWall = candidate
                        wallValidationMsg = err
                        interactionMode = PlayerInteractionMode.PLACE_WALL
                        activeAction = if (isWallHorizontal) SelectedAction.HORIZONTAL_WALL else SelectedAction.VERTICAL_WALL
                    },
                    onDragWallPreview = { wx, wy, isHoriz ->
                        if (gameState.winner != null || gameState.isAiThinking || (gameState.gameMode == GameMode.ONLINE && gameState.currentPlayer != gameState.myPlayerNum)) return@InteractiveBoard
                        val currentWalls = if (gameState.currentPlayer == 1) gameState.player1.walls else gameState.player2.walls
                        if (currentWalls <= 0) {
                            wallValidationMsg = "No walls left!"
                            return@InteractiveBoard
                        }
                        val candidate = Wall(wx, wy, isHoriz)
                        val err = gameViewModel.checkWallPlacement(gameState, candidate)
                        pendingWall = candidate
                        wallValidationMsg = err
                        isWallHorizontal = isHoriz
                        interactionMode = PlayerInteractionMode.PLACE_WALL
                        activeAction = if (isHoriz) SelectedAction.HORIZONTAL_WALL else SelectedAction.VERTICAL_WALL
                    },
                    onConfirmDragWall = {
                        if (gameState.winner != null || gameState.isAiThinking || (gameState.gameMode == GameMode.ONLINE && gameState.currentPlayer != gameState.myPlayerNum)) return@InteractiveBoard
                        val w = pendingWall
                        if (w != null && wallValidationMsg == null) {
                            if (appSettings.soundEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            gameViewModel.handleAction(GameAction.PlaceWall(gameState.currentPlayer, w))
                            pendingWall = null
                        } else {
                            if (appSettings.soundEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            pendingWall = null
                            wallValidationMsg = null
                        }
                    }
                )

                // Sleek Floating Warning Pill Overlay (Zero layout shift!)
                androidx.compose.animation.AnimatedVisibility(
                    visible = gameState.errorMsg != null,
                    enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.88f, animationSpec = tween(140)),
                    exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.88f, animationSpec = tween(180)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xF20F172A),
                        shadowElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x55EF4444))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(Color(0xFFEF4444), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = gameState.errorMsg ?: "",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Online Live Voice & Quick Emojis Bar (Online & VS Computer matches)
            if (gameState.gameMode == GameMode.ONLINE || gameState.gameMode == GameMode.VS_COMPUTER) {
                OnlineLiveVoiceEmojiBar(
                    voiceState = voiceChatState,
                    isRealPeerConnected = gameState.isRealPeerConnected,
                    emojiCooldownSeconds = emojiCooldownSeconds,
                    isVsComputer = gameState.gameMode == GameMode.VS_COMPUTER,
                    onToggleMic = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            if (!voiceChatState.isMicConnected) {
                                gameViewModel.startVoiceChat()
                            } else {
                                gameViewModel.toggleMicMute()
                            }
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onToggleSpeaker = {
                        gameViewModel.toggleSpeakerMute()
                    },
                    onSendEmoji = { emoji ->
                        if (appSettings.soundEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        gameViewModel.sendLiveEmoji(emoji)
                    },
                    darkTheme = appSettings.darkTheme
                )

                if (micPermissionNotice != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = micPermissionNotice ?: "",
                        color = Color(0xFFEF4444),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            // User / Bottom Area
            val bottomPlayerNum = if (gameState.gameMode == GameMode.ONLINE) gameState.myPlayerNum else 1
            val bottomPlayer = if (bottomPlayerNum == 1) gameState.player1 else gameState.player2
            val bottomLabel = if (gameState.gameMode == GameMode.ONLINE) {
                "${if (bottomPlayerNum == 1) gameState.player1Name else gameState.player2Name} (You)"
            } else if (gameState.gameMode == GameMode.VS_COMPUTER) {
                "${gameState.player1Name} (You)"
            } else {
                gameState.player1Name
            }
            val bottomAvatar = if (bottomPlayerNum == 1) gameState.player1Avatar else gameState.player2Avatar
            val bottomColorBadge = if (bottomPlayerNum == 1) "Blue (You)" else "Red (You)"

            PlayerInfoBar(
                player = bottomPlayer,
                name = bottomLabel,
                avatar = bottomAvatar,
                isCurrentTurn = gameState.currentPlayer == bottomPlayerNum,
                colorBadge = bottomColorBadge,
                extraTag = if (gameState.hasUsedRewardedWalls) "• +2 Bonus Used" else null,
                darkTheme = appSettings.darkTheme
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            PlayerActionControls(
                gameState = gameState,
                playerNum = bottomPlayerNum,
                activeAction = activeAction,
                onRequestRewardAd = { showRewardDialog = true },
                darkTheme = appSettings.darkTheme,
                onSelectAction = { action ->
                    activeAction = action
                    when (action) {
                        SelectedAction.MOVE_PAWN -> {
                            interactionMode = PlayerInteractionMode.MOVE_PAWN
                            pendingWall = null
                            wallValidationMsg = null
                        }
                        SelectedAction.HORIZONTAL_WALL -> {
                            interactionMode = PlayerInteractionMode.PLACE_WALL
                            isWallHorizontal = true
                            pendingWall?.let { w ->
                                val candidate = w.copy(isHorizontal = true)
                                pendingWall = candidate
                                wallValidationMsg = gameViewModel.checkWallPlacement(gameState, candidate)
                            }
                        }
                        SelectedAction.VERTICAL_WALL -> {
                            interactionMode = PlayerInteractionMode.PLACE_WALL
                            isWallHorizontal = false
                            pendingWall?.let { w ->
                                val candidate = w.copy(isHorizontal = false)
                                pendingWall = candidate
                                wallValidationMsg = gameViewModel.checkWallPlacement(gameState, candidate)
                            }
                        }
                    }
                }
            )

            // Rewarded Ad Option: When Player 1 runs out of walls against Computer
            if (gameState.gameMode == GameMode.VS_COMPUTER && gameState.player1.walls <= 0 && gameState.winner == null) {
                Spacer(modifier = Modifier.height(4.dp))
                if (!gameState.hasUsedRewardedWalls) {
                    Button(
                        onClick = { showRewardDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("rewarded_extra_walls_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD97706), // Warm Amber
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Watch Ad for +2 Extra Walls (1x Only)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Surface(
                        color = if (appSettings.darkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "⚡ Extra wall bonus already claimed for this game",
                            color = if (appSettings.darkTheme) Color(0xFF94A3B8) else Color(0xFF64748B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
        }

        // Active Player Wall Confirmation Overlay (Fixed, Absolute, Non-pushing)
        val isCurrentPlayerHuman = when (gameState.gameMode) {
            GameMode.LOCAL_PASS_AND_PLAY -> true
            GameMode.ONLINE -> gameState.currentPlayer == gameState.myPlayerNum
            else -> gameState.currentPlayer == 1
        }
        val isWallModeActive = !gameState.isAiThinking &&
                gameState.winner == null &&
                isCurrentPlayerHuman &&
                (activeAction == SelectedAction.HORIZONTAL_WALL || activeAction == SelectedAction.VERTICAL_WALL)

        val activeBottomPlayer = if (gameState.gameMode == GameMode.ONLINE) gameState.myPlayerNum else 1
        val isBottomWallVisible = isWallModeActive && (
            (gameState.gameMode == GameMode.ONLINE && gameState.currentPlayer == gameState.myPlayerNum) ||
            (gameState.gameMode != GameMode.ONLINE && gameState.currentPlayer == 1)
        )

        // Overlay for Bottom Player
        AnimatedVisibility(
            visible = isBottomWallVisible,
            enter = fadeIn(tween(140)) + slideInVertically(tween(140)) { it / 2 },
            exit = fadeOut(tween(100)) + slideOutVertically(tween(100)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
        ) {
            WallConfirmationOverlay(
                isWallHorizontal = isWallHorizontal,
                pendingWall = pendingWall,
                wallValidationMsg = wallValidationMsg,
                playerNum = activeBottomPlayer,
                isRotated = false,
                darkTheme = appSettings.darkTheme,
                onToggleOrientation = { isHoriz ->
                    isWallHorizontal = isHoriz
                    activeAction = if (isHoriz) SelectedAction.HORIZONTAL_WALL else SelectedAction.VERTICAL_WALL
                    pendingWall?.let { w ->
                        val candidate = w.copy(isHorizontal = isHoriz)
                        pendingWall = candidate
                        wallValidationMsg = gameViewModel.checkWallPlacement(gameState, candidate)
                    }
                },
                onConfirm = { w ->
                    if (appSettings.soundEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    gameViewModel.handleAction(GameAction.PlaceWall(activeBottomPlayer, w))
                    pendingWall = null
                    wallValidationMsg = null
                    activeAction = SelectedAction.MOVE_PAWN
                    interactionMode = PlayerInteractionMode.MOVE_PAWN
                },
                onCancel = {
                    pendingWall = null
                    wallValidationMsg = null
                    activeAction = SelectedAction.MOVE_PAWN
                    interactionMode = PlayerInteractionMode.MOVE_PAWN
                }
            )
        }

        // Overlay for Player 2 (Top, Local Pass & Play only)
        AnimatedVisibility(
            visible = isWallModeActive && gameState.currentPlayer == 2 && gameState.gameMode == GameMode.LOCAL_PASS_AND_PLAY,
            enter = fadeIn(tween(140)) + slideInVertically(tween(140)) { -it / 2 },
            exit = fadeOut(tween(100)) + slideOutVertically(tween(100)) { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(start = 14.dp, end = 14.dp, top = 6.dp)
        ) {
            WallConfirmationOverlay(
                isWallHorizontal = isWallHorizontal,
                pendingWall = pendingWall,
                wallValidationMsg = wallValidationMsg,
                playerNum = 2,
                isRotated = true,
                darkTheme = appSettings.darkTheme,
                onToggleOrientation = { isHoriz ->
                    isWallHorizontal = isHoriz
                    activeAction = if (isHoriz) SelectedAction.HORIZONTAL_WALL else SelectedAction.VERTICAL_WALL
                    pendingWall?.let { w ->
                        val candidate = w.copy(isHorizontal = isHoriz)
                        pendingWall = candidate
                        wallValidationMsg = gameViewModel.checkWallPlacement(gameState, candidate)
                    }
                },
                onConfirm = { w ->
                    if (appSettings.soundEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    gameViewModel.handleAction(GameAction.PlaceWall(2, w))
                    pendingWall = null
                    wallValidationMsg = null
                    activeAction = SelectedAction.MOVE_PAWN
                    interactionMode = PlayerInteractionMode.MOVE_PAWN
                },
                onCancel = {
                    pendingWall = null
                    wallValidationMsg = null
                    activeAction = SelectedAction.MOVE_PAWN
                    interactionMode = PlayerInteractionMode.MOVE_PAWN
                }
            )
        }

        // Floating Live Emojis Overlay (Local & Remote WebRTC reactions)
        FloatingEmojisOverlay(
            emojis = liveFloatingEmojis,
            darkTheme = appSettings.darkTheme
        )
    }

        if (showRewardDialog) {
            AlertDialog(
                onDismissRequest = { showRewardDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = "Need More Walls?",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "You have 0 walls left! Watch a short video ad to unlock +2 extra walls for this match.",
                            fontSize = 14.sp,
                            color = if (appSettings.darkTheme) Color(0xFFE2E8F0) else Color(0xFF334155),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "⚠️ Notice: Can only be used 1 time per game",
                                color = Color(0xFF92400E),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showRewardDialog = false
                            if (activity != null && app != null) {
                                app.rewardedAdManager.showRewardedAd(
                                    activity = activity,
                                    onRewardEarned = {
                                        gameViewModel.claimRewardedExtraWalls()
                                    }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Watch Video (+2 Walls)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRewardDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }

        if (showSettings) {
            AppSettingsDialog(
                appSettings = appSettings,
                onSettingsChanged = { gameViewModel.updateSettings(it) },
                onDismiss = { showSettings = false }
            )
        }

        // Confirmation Dialog: Leave Game / Back to Menu
        if (showConfirmBackDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmBackDialog = false },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(Color(0xFFFEF2F2), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = if (gameState.gameMode == GameMode.ONLINE) "Quit Online Match?" else "Leave Current Game?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Text(
                        text = if (gameState.gameMode == GameMode.ONLINE) {
                            "You are currently in an active online match. If you quit, you will disconnect from the opponent and forfeit the match.\n\nAre you sure you want to quit?"
                        } else {
                            "A match is currently in progress. If you go back to the menu, your current game progress will be lost.\n\nAre you sure you want to quit?"
                        },
                        fontSize = 14.sp,
                        color = if (appSettings.darkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showConfirmBackDialog = false
                            gameViewModel.quitCurrentGame()
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Quit Match", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showConfirmBackDialog = false },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Continue Playing", color = if (appSettings.darkTheme) Color.White else Color(0xFF334155))
                    }
                },
                shape = RoundedCornerShape(18.dp)
            )
        }

        // Confirmation Dialog: Surrender Online Match
        if (showConfirmForfeitDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmForfeitDialog = false },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(Color(0xFFFEE2E2), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = "Surrender Match?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Text(
                        text = "In an online match, you cannot restart the board while your opponent is actively playing.\n\nSurrendering will award the victory to your opponent and exit this match. Do you want to forfeit and leave?",
                        fontSize = 14.sp,
                        color = if (appSettings.darkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showConfirmForfeitDialog = false
                            gameViewModel.quitCurrentGame()
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Surrender & Exit", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showConfirmForfeitDialog = false },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Keep Playing", color = if (appSettings.darkTheme) Color.White else Color(0xFF334155))
                    }
                },
                shape = RoundedCornerShape(18.dp)
            )
        }

        // Confirmation Dialog: Reset / Restart Match
        if (showConfirmResetDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmResetDialog = false },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(Color(0xFFFEF3C7), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = "Restart Game?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Text(
                        text = "Restarting will clear all placed walls and return all pawns to their starting positions.\n\nDo you want to reset the board?",
                        fontSize = 14.sp,
                        color = if (appSettings.darkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showConfirmResetDialog = false
                            pendingWall = null
                            wallValidationMsg = null
                            interactionMode = PlayerInteractionMode.MOVE_PAWN
                            gameViewModel.resetGame()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Restart", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showConfirmResetDialog = false },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = if (appSettings.darkTheme) Color.White else Color(0xFF334155))
                    }
                },
                shape = RoundedCornerShape(18.dp)
            )
        }

        if (gameState.winner != null) {
            VictoryCelebrationDialog(
                winner = gameState.winner!!,
                myPlayerNum = gameState.myPlayerNum,
                gameMode = gameState.gameMode,
                moveCount = gameState.moveCount,
                opponentName = gameState.opponentName,
                rematchRequestedByMe = gameState.rematchRequestedByMe,
                rematchRequestedByOpponent = gameState.rematchRequestedByOpponent,
                isRealPeerConnected = gameState.isRealPeerConnected,
                roundsPlayed = gameState.roundsPlayed,
                myWins = gameState.myWins,
                opponentWins = gameState.opponentWins,
                roomCode = gameState.roomCode,
                onPlayAgain = {
                    if (gameState.gameMode == GameMode.ONLINE) {
                        gameViewModel.requestOnlineRematch()
                    } else {
                        gameViewModel.resetGame()
                        interactionMode = PlayerInteractionMode.MOVE_PAWN
                        pendingWall = null
                        wallValidationMsg = null
                    }
                },
                onMenu = {
                    gameViewModel.quitCurrentGame()
                    onBack()
                }
            )
        }
    }
}

@Composable
fun PlayerInfoClassic(
    player: Player,
    name: String,
    extraTag: String? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = name, fontWeight = FontWeight.Bold, color = player.color, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Walls: ${player.walls}", fontSize = 12.sp, color = Color.Gray)
            if (extraTag != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = extraTag, fontSize = 11.sp, color = Color(0xFFD97706), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun PlayerInfoBar(
    player: Player,
    name: String,
    avatar: String? = null,
    isCurrentTurn: Boolean,
    colorBadge: String? = null,
    extraTag: String? = null,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = false
) {
    val containerBg = if (isCurrentTurn) {
        if (darkTheme) player.color.copy(alpha = 0.22f) else player.color.copy(alpha = 0.10f)
    } else {
        if (darkTheme) Color(0xFF1E293B) else Color(0xFFF8FAFC)
    }
    val borderColor = if (isCurrentTurn) {
        player.color
    } else {
        if (darkTheme) Color(0xFF334155) else Color(0xFFCBD5E1)
    }

    Surface(
        color = containerBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isCurrentTurn) 1.5.dp else 1.dp,
            color = borderColor
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Player avatar + Name + Color Badge + Active Turn Dot
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(player.color, CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatar != null) {
                        Text(
                            text = avatar,
                            fontSize = 13.sp
                        )
                    } else {
                        Text(
                            text = "P${player.id}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }

                Text(
                    text = name,
                    fontWeight = if (isCurrentTurn) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = if (isCurrentTurn) player.color else if (darkTheme) Color(0xFFF1F5F9) else Color(0xFF1E293B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (colorBadge != null) {
                    Surface(
                        color = player.color.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, player.color.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = colorBadge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = player.color,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }

                if (isCurrentTurn) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(player.color, CircleShape)
                    )
                }

                if (extraTag != null) {
                    Text(
                        text = extraTag,
                        fontSize = 10.sp,
                        color = Color(0xFFD97706),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Right: Wall Count Badge
            val wallBadgeBg = when {
                player.walls > 2 -> if (darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)
                player.walls in 1..2 -> if (darkTheme) Color(0xFF78350F).copy(alpha = 0.6f) else Color(0xFFFEF3C7)
                else -> if (darkTheme) Color(0xFF7F1D1D).copy(alpha = 0.6f) else Color(0xFFFEE2E2)
            }
            val wallBadgeTextColor = when {
                player.walls > 2 -> if (darkTheme) Color(0xFFE2E8F0) else Color(0xFF334155)
                player.walls in 1..2 -> if (darkTheme) Color(0xFFFDE68A) else Color(0xFF92400E)
                else -> if (darkTheme) Color(0xFFFCA5A5) else Color(0xFFDC2626)
            }

            Surface(
                color = wallBadgeBg,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, wallBadgeTextColor.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "🧱", fontSize = 11.sp)
                    Text(
                        text = "${player.walls} walls",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = wallBadgeTextColor
                    )
                }
            }
        }
    }
}

@Composable
fun TurnAndTimerBar(
    gameState: GameState,
    timingEnabled: Boolean,
    timerSecondsRemaining: Float,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = false
) {
    Surface(
        color = if (darkTheme) Color(0xFF1E293B) else Color(0xFFF8FAFC),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val isMyTurnInOnline = (gameState.gameMode == GameMode.ONLINE && gameState.currentPlayer == gameState.myPlayerNum)
                val myColorName = if (gameState.myPlayerNum == 1) "Blue 🔵" else "Red 🔴"
                val oppColorName = if (gameState.myPlayerNum == 1) "Red 🔴" else "Blue 🔵"

                val turnText = if (gameState.winner != null) {
                    "Game Over"
                } else if (gameState.isAiThinking) {
                    if (gameState.gameMode == GameMode.ONLINE) "${gameState.opponentName} is thinking..." else "AI Thinking..."
                } else {
                    if (gameState.gameMode == GameMode.ONLINE) {
                        if (isMyTurnInOnline) "Your Turn ($myColorName)" else "${gameState.opponentName}'s Turn ($oppColorName)"
                    } else if (gameState.gameMode == GameMode.VS_COMPUTER) {
                        if (gameState.currentPlayer == 1) "Your Turn (Blue 🔵)" else "AI's Turn (Red 🔴)"
                    } else {
                        if (gameState.currentPlayer == 1) "Player 1's Turn (Blue 🔵)" else "Player 2's Turn (Red 🔴)"
                    }
                }

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (gameState.winner != null) AccentGreen 
                                        else if (gameState.currentPlayer == 1) Player1Color 
                                        else Player2Color,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = turnText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (gameState.winner != null) AccentGreen 
                                else if (gameState.currentPlayer == 1) Player1Color 
                                else Player2Color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (timingEnabled && gameState.winner == null) {
                    val timerColor = when {
                        timerSecondsRemaining > 5f -> Color(0xFF10B981)
                        timerSecondsRemaining > 2.5f -> Color(0xFFF59E0B)
                        else -> Color(0xFFEF4444)
                    }
                    val roundedSec = kotlin.math.ceil(timerSecondsRemaining).toInt().coerceIn(0, 10)
                    Surface(
                        color = timerColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, timerColor.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⏱ ${roundedSec}s",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = timerColor
                            )
                        }
                    }
                } else if (!timingEnabled && gameState.gameMode == GameMode.ONLINE && gameState.winner == null) {
                    Surface(
                        color = if (darkTheme) Color(0xFF334155) else Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (darkTheme) Color(0xFF475569) else Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Relaxed",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (darkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            if (timingEnabled && gameState.winner == null) {
                Spacer(modifier = Modifier.height(4.dp))
                val timerFraction = (timerSecondsRemaining / 10f).coerceIn(0f, 1f)
                val barColor = when {
                    timerSecondsRemaining > 5f -> Color(0xFF10B981)
                    timerSecondsRemaining > 2.5f -> Color(0xFFF59E0B)
                    else -> Color(0xFFEF4444)
                }
                LinearProgressIndicator(
                    progress = { timerFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = barColor,
                    trackColor = if (darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0),
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

private data class ConfettiParticle(
    val xRatio: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val phase: Float
)

@Composable
fun VictoryCelebrationDialog(
    winner: Int,
    myPlayerNum: Int = 1,
    gameMode: GameMode,
    moveCount: Int,
    opponentName: String = "Opponent",
    rematchRequestedByMe: Boolean = false,
    rematchRequestedByOpponent: Boolean = false,
    isRealPeerConnected: Boolean = true,
    roundsPlayed: Int = 1,
    myWins: Int = 0,
    opponentWins: Int = 0,
    roomCode: String? = null,
    onPlayAgain: () -> Unit,
    onMenu: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60)
        isVisible = true
    }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "victory_scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(250),
        label = "victory_alpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "celebration")
    val trophyPulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trophy_pulse"
    )

    val confettiProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "confetti"
    )

    val isMe = if (gameMode == GameMode.ONLINE) {
        winner == myPlayerNum
    } else {
        winner == 1
    }
    val winnerColor = if (winner == 1) Player1Color else Player2Color
    val winnerName = if (gameMode == GameMode.ONLINE) {
        if (isMe) "You Win! 🏆" else "$opponentName Wins!"
    } else if (gameMode == GameMode.VS_COMPUTER) {
        if (winner == 1) "You Win! 🏆" else "Computer AI Wins!"
    } else {
        if (winner == 1) "Player 1 Wins!" else "Player 2 Wins!"
    }

    val particles = remember {
        List(40) { index ->
            val initialX = (index * 0.025f)
            val speed = 0.65f + (index % 5) * 0.15f
            val size = 6f + (index % 4) * 3f
            val color = when (index % 5) {
                0 -> Color(0xFFFFD700)
                1 -> Player1Color
                2 -> Player2Color
                3 -> AccentGreen
                else -> Color(0xFFFF006E)
            }
            val phase = (index * 0.35f)
            ConfettiParticle(initialX, speed, size, color, phase)
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center
        ) {
            // Animated Falling Confetti Canvas across whole screen
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                particles.forEach { p ->
                    val y = ((confettiProgress * p.speed + p.phase) % 1f) * (h + 100f) - 50f
                    val x = (p.xRatio * w) + sin(confettiProgress * 6.28f + p.phase) * 40f
                    drawCircle(
                        color = p.color,
                        radius = p.size,
                        center = Offset(x, y)
                    )
                }
            }

            // Central Animated Victory Card
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Pulsing Golden Trophy Illustration
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .scale(trophyPulse),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(84.dp)) {
                            val w = size.width
                            val h = size.height

                            // Golden Glow Aura
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFFFE082).copy(alpha = 0.55f),
                                        Color.Transparent
                                    )
                                ),
                                radius = w * 0.5f,
                                center = Offset(w / 2f, h / 2f)
                            )

                            // Trophy Base
                            drawRoundRect(
                                color = Color(0xFFD97706),
                                topLeft = Offset(w * 0.28f, h * 0.82f),
                                size = Size(w * 0.44f, h * 0.12f),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                            // Stem
                            drawRect(
                                color = Color(0xFFF59E0B),
                                topLeft = Offset(w * 0.44f, h * 0.65f),
                                size = Size(w * 0.12f, h * 0.18f)
                            )
                            // Cup Body
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xFFFCD34D), Color(0xFFF59E0B))
                                ),
                                topLeft = Offset(w * 0.22f, h * 0.15f),
                                size = Size(w * 0.56f, h * 0.52f),
                                cornerRadius = CornerRadius(w * 0.28f, w * 0.28f)
                            )
                            // Left Handle
                            drawArc(
                                color = Color(0xFFD97706),
                                startAngle = 90f,
                                sweepAngle = 180f,
                                useCenter = false,
                                topLeft = Offset(w * 0.08f, h * 0.22f),
                                size = Size(w * 0.24f, h * 0.32f),
                                style = Stroke(width = 6f)
                            )
                            // Right Handle
                            drawArc(
                                color = Color(0xFFD97706),
                                startAngle = 270f,
                                sweepAngle = 180f,
                                useCenter = false,
                                topLeft = Offset(w * 0.68f, h * 0.22f),
                                size = Size(w * 0.24f, h * 0.32f),
                                style = Stroke(width = 6f)
                            )
                            // Star in center
                            drawCircle(
                                color = Color.White,
                                radius = w * 0.075f,
                                center = Offset(w / 2f, h * 0.38f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Celebration Tag
                    Surface(
                        color = winnerColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "🎉 VICTORY! 🎉",
                            color = winnerColor,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = winnerName,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = winnerColor
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Reached the opposing side in $moveCount moves!",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )

                    if (gameMode == GameMode.ONLINE) {
                        Spacer(modifier = Modifier.height(10.dp))

                        // Match Round and Series Score Chip
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Round $roundsPlayed",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF475569)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "•",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Score: You $myWins - $opponentWins $opponentName",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                                if (roomCode != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "• Room: $roomCode",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        }

                        // Status notification card for Rematch
                        if (!isRealPeerConnected) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = Color(0xFFFEF2F2),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA))
                            ) {
                                Text(
                                    text = "🚪 $opponentName left the room",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFB91C1C),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        } else if (rematchRequestedByOpponent && !rematchRequestedByMe) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = Color(0xFFF0FDF4),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0))
                            ) {
                                Text(
                                    text = "🔥 $opponentName wants a rematch! Tap to accept!",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF15803D),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        } else if (rematchRequestedByMe && !rematchRequestedByOpponent) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = Color(0xFFEFF6FF),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF2563EB)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Rematch requested! Waiting for $opponentName...",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF1D4ED8)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Buttons
                    val isPlayAgainEnabled = if (gameMode == GameMode.ONLINE) {
                        isRealPeerConnected && !rematchRequestedByMe
                    } else {
                        true
                    }

                    val playAgainButtonText = when {
                        gameMode == GameMode.ONLINE && !isRealPeerConnected -> "Opponent Left Match"
                        gameMode == GameMode.ONLINE && rematchRequestedByMe && !rematchRequestedByOpponent -> "Waiting for $opponentName..."
                        gameMode == GameMode.ONLINE && rematchRequestedByOpponent -> "Accept Rematch! 🔥"
                        gameMode == GameMode.ONLINE -> "Play Again (Rematch) 🔄"
                        else -> "Play Again"
                    }

                    Button(
                        onClick = onPlayAgain,
                        enabled = isPlayAgainEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("play_again_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGreen,
                            disabledContainerColor = if (rematchRequestedByMe && isRealPeerConnected) Color(0xFF3B82F6) else Color(0xFFCBD5E1),
                            disabledContentColor = if (rematchRequestedByMe && isRealPeerConnected) Color.White else Color(0xFF94A3B8)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (rematchRequestedByMe && isRealPeerConnected && !rematchRequestedByOpponent) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(playAgainButtonText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onMenu,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("menu_btn"),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFCBD5E1))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = WallColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Main Menu", fontSize = 16.sp, color = WallColor, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun WallConfirmationOverlay(
    isWallHorizontal: Boolean,
    pendingWall: Wall?,
    wallValidationMsg: String?,
    playerNum: Int,
    isRotated: Boolean,
    onToggleOrientation: (Boolean) -> Unit,
    onConfirm: (Wall) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = false
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(16.dp))
            .graphicsLayer {
                if (isRotated) {
                    rotationZ = 180f
                }
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (darkTheme) Color(0xFF1E293B) else Color.White),
        border = BorderStroke(1.dp, if (darkTheme) Color(0xFF334155) else Color(0xFFCBD5E1))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Line orientation toggles (Equal weights, clean segmented look)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Horizontal toggle button
                Surface(
                    onClick = { onToggleOrientation(true) },
                    color = if (isWallHorizontal) Color(0xFFEA580C) else if (darkTheme) Color(0xFF334155) else Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isWallHorizontal) Color(0xFFEA580C) else if (darkTheme) Color(0xFF475569) else Color(0xFFCBD5E1)),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .testTag(if (playerNum == 1) "horizontal_wall_btn_p1" else "horizontal_wall_btn_p2")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 14.dp, height = 4.dp)
                                .background(
                                    color = if (isWallHorizontal) Color.White else Color(0xFFEA580C),
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Horizontal",
                            fontSize = 12.sp,
                            fontWeight = if (isWallHorizontal) FontWeight.Bold else FontWeight.Medium,
                            color = if (isWallHorizontal) Color.White else if (darkTheme) Color(0xFFF1F5F9) else Color(0xFF334155)
                        )
                    }
                }

                // Vertical toggle button
                Surface(
                    onClick = { onToggleOrientation(false) },
                    color = if (!isWallHorizontal) Color(0xFFEA580C) else if (darkTheme) Color(0xFF334155) else Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (!isWallHorizontal) Color(0xFFEA580C) else if (darkTheme) Color(0xFF475569) else Color(0xFFCBD5E1)),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .testTag(if (playerNum == 1) "vertical_wall_btn_p1" else "vertical_wall_btn_p2")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 4.dp, height = 14.dp)
                                .background(
                                    color = if (!isWallHorizontal) Color.White else Color(0xFFEA580C),
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Vertical",
                            fontSize = 12.sp,
                            fontWeight = if (!isWallHorizontal) FontWeight.Bold else FontWeight.Medium,
                            color = if (!isWallHorizontal) Color.White else if (darkTheme) Color(0xFFF1F5F9) else Color(0xFF334155)
                        )
                    }
                }
            }

            // Row 2: Status or Error validation banner
            val statusBg = when {
                pendingWall != null && wallValidationMsg != null -> if (darkTheme) Color(0xFF450A0A) else Color(0xFFFEE2E2)
                pendingWall != null -> if (darkTheme) Color(0xFF064E3B) else Color(0xFFD1FAE5)
                else -> if (darkTheme) Color(0xFF334155) else Color(0xFFF1F5F9)
            }
            val statusBorder = when {
                pendingWall != null && wallValidationMsg != null -> ErrorRed
                pendingWall != null -> AccentGreen
                else -> if (darkTheme) Color(0xFF475569) else Color(0xFFCBD5E1)
            }
            val statusTextColor = when {
                pendingWall != null && wallValidationMsg != null -> ErrorRed
                pendingWall != null -> AccentGreen
                else -> if (darkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
            }
            val statusText = when {
                pendingWall != null && wallValidationMsg != null -> "⚠️ $wallValidationMsg"
                pendingWall != null -> "✓ Wall placed — Tap Confirm below"
                else -> "👉 Tap board intersection to position wall"
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = statusBg,
                border = BorderStroke(1.dp, statusBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Row 3: Action buttons (Confirm Wall & Cancel) - Both 42dp height, weight 1f, 13sp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Confirm Wall button
                val canConfirm = pendingWall != null && wallValidationMsg == null
                Button(
                    onClick = { pendingWall?.let { onConfirm(it) } },
                    enabled = canConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGreen,
                        contentColor = Color.White,
                        disabledContainerColor = if (darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0),
                        disabledContentColor = if (darkTheme) Color(0xFF64748B) else Color(0xFF94A3B8)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = if (canConfirm) 3.dp else 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("confirm_wall_btn"),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Confirm",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Confirm Wall",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // Cancel button
                OutlinedButton(
                    onClick = onCancel,
                    border = BorderStroke(1.2.dp, ErrorRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("cancel_wall_btn"),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = ErrorRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Cancel",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ErrorRed
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerActionControls(
    gameState: GameState,
    playerNum: Int,
    activeAction: SelectedAction,
    onSelectAction: (SelectedAction) -> Unit,
    onRequestRewardAd: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = false
) {
    val isMyTurn = gameState.currentPlayer == playerNum
    val currentWalls = if (playerNum == 1) gameState.player1.walls else gameState.player2.walls
    val alpha = if (isMyTurn && !gameState.isAiThinking && gameState.winner == null) 1f else 0.45f
    val canWatchAdForWalls = (playerNum == 1 &&
            gameState.gameMode == GameMode.VS_COMPUTER &&
            currentWalls <= 0 &&
            !gameState.hasUsedRewardedWalls)

    val isMoveActive = isMyTurn && !gameState.isAiThinking && gameState.winner == null && (activeAction == SelectedAction.MOVE_PAWN)
    val isWallActive = isMyTurn && !gameState.isAiThinking && gameState.winner == null &&
            (activeAction == SelectedAction.HORIZONTAL_WALL || activeAction == SelectedAction.VERTICAL_WALL)
    val wallColorActive = Color(0xFFEA580C)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .graphicsLayer { this.alpha = alpha },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Move Pawn Button (Weight 1f, identical height & styling)
        Button(
            onClick = { onSelectAction(SelectedAction.MOVE_PAWN) },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag(if (playerNum == 1) "move_pawn_btn" else "move_pawn_btn_p2"),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isMoveActive) WallColor else if (darkTheme) Color(0xFF1E293B) else Color(0xFFF8FAFC),
                contentColor = if (isMoveActive) Color.White else if (darkTheme) Color(0xFFE2E8F0) else Color(0xFF334155),
                disabledContainerColor = if (darkTheme) Color(0xFF1E293B).copy(alpha = 0.5f) else Color(0xFFF1F5F9),
                disabledContentColor = if (darkTheme) Color(0xFF64748B) else Color(0xFF94A3B8)
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = if (isMoveActive) 3.dp else 0.dp),
            border = if (!isMoveActive) BorderStroke(1.dp, if (darkTheme) Color(0xFF334155) else Color(0xFFCBD5E1)) else null,
            contentPadding = PaddingValues(horizontal = 8.dp),
            enabled = isMyTurn && !gameState.isAiThinking && gameState.winner == null
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "♟",
                    fontSize = 17.sp,
                    color = if (isMoveActive) Color.White else if (darkTheme) Color(0xFF93C5FD) else WallColor
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Move Pawn",
                    fontWeight = if (isMoveActive) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
        }

        // 2. Walls Button (Weight 1f, identical height & styling)
        Button(
            onClick = {
                if (canWatchAdForWalls) {
                    onRequestRewardAd?.invoke()
                } else if (currentWalls > 0) {
                    if (activeAction == SelectedAction.HORIZONTAL_WALL || activeAction == SelectedAction.VERTICAL_WALL) {
                        onSelectAction(activeAction)
                    } else {
                        onSelectAction(SelectedAction.HORIZONTAL_WALL)
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag(if (playerNum == 1) "place_wall_btn" else "place_wall_btn_p2"),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isWallActive) wallColorActive else if (darkTheme) Color(0xFF1E293B) else Color(0xFFF8FAFC),
                contentColor = if (isWallActive) Color.White else if (darkTheme) Color(0xFFE2E8F0) else Color(0xFF334155),
                disabledContainerColor = if (darkTheme) Color(0xFF1E293B).copy(alpha = 0.5f) else Color(0xFFF1F5F9),
                disabledContentColor = if (darkTheme) Color(0xFF64748B) else Color(0xFF94A3B8)
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = if (isWallActive) 3.dp else 0.dp),
            border = if (!isWallActive) BorderStroke(1.dp, if (darkTheme) Color(0xFF334155) else Color(0xFFCBD5E1)) else null,
            contentPadding = PaddingValues(horizontal = 8.dp),
            enabled = isMyTurn && !gameState.isAiThinking && (currentWalls > 0 || canWatchAdForWalls) && gameState.winner == null
        ) {
            if (canWatchAdForWalls) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(text = "🎬", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "+2 Walls",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFFD97706),
                        maxLines = 1
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "🧱",
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Walls",
                        fontWeight = if (isWallActive) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isWallActive) Color.White.copy(alpha = 0.25f)
                                else if (darkTheme) Color(0xFF334155)
                                else Color(0xFFE2E8F0)
                    ) {
                        Text(
                            text = "$currentWalls",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isWallActive) Color.White
                                    else if (currentWalls > 0) (if (darkTheme) Color(0xFFF1F5F9) else Color(0xFF334155))
                                    else Color(0xFFEF4444),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InteractiveBoard(
    gameState: GameState,
    validMoves: List<Pair<Int, Int>>,
    invalidCell: Pair<Int, Int>? = null,
    invalidCellAlpha: Float = 0f,
    interactionMode: PlayerInteractionMode,
    isWallHorizontal: Boolean,
    pendingWall: Wall?,
    wallValidationMsg: String? = null,
    appSettings: AppSettings,
    flipBoard: Boolean = false,
    onCellClicked: (Int, Int) -> Unit,
    onWallIntersectionClicked: (Int, Int) -> Unit,
    onDragWallPreview: (Int, Int, Boolean) -> Unit,
    onConfirmDragWall: () -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(gameState, interactionMode, isWallHorizontal, appSettings, flipBoard) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val down = event.changes.firstOrNull { it.pressed } ?: continue
                        val startX = down.position.x
                        val startY = down.position.y
                        val cellSize = size.width / 9f
                        if (cellSize <= 0f) continue

                        if (interactionMode == PlayerInteractionMode.MOVE_PAWN) {
                            var finalUp = down
                            while (true) {
                                val upEvent = awaitPointerEvent()
                                val change = upEvent.changes.firstOrNull()
                                if (change == null || !change.pressed) {
                                    if (change != null) finalUp = change
                                    break
                                } else {
                                    finalUp = change
                                }
                            }
                            val rawCellX = (finalUp.position.x / cellSize).toInt().coerceIn(0, 8)
                            val rawCellY = (finalUp.position.y / cellSize).toInt().coerceIn(0, 8)
                            val cellX = if (flipBoard) 8 - rawCellX else rawCellX
                            val cellY = if (flipBoard) 8 - rawCellY else rawCellY
                            onCellClicked(cellX, cellY)
                        } else {
                            if (appSettings.classicControls) {
                                var finalUp = down
                                while (true) {
                                    val upEvent = awaitPointerEvent()
                                    val change = upEvent.changes.firstOrNull()
                                    if (change == null || !change.pressed) {
                                        if (change != null) finalUp = change
                                        break
                                    } else {
                                        finalUp = change
                                    }
                                }
                                var closestWx = 0
                                var closestWy = 0
                                var minDistanceSq = Float.MAX_VALUE
                                for (wx in 0..7) {
                                    for (wy in 0..7) {
                                        val interX = (wx + 1) * cellSize
                                        val interY = (wy + 1) * cellSize
                                        val dSq = (finalUp.position.x - interX) * (finalUp.position.x - interX) + (finalUp.position.y - interY) * (finalUp.position.y - interY)
                                        if (dSq < minDistanceSq) {
                                            minDistanceSq = dSq
                                            closestWx = wx
                                            closestWy = wy
                                        }
                                    }
                                }
                                val targetWx = if (flipBoard) 7 - closestWx else closestWx
                                val targetWy = if (flipBoard) 7 - closestWy else closestWy
                                onWallIntersectionClicked(targetWx, targetWy)
                            } else {
                                val rawWx0 = (startX / cellSize - 0.5f).roundToInt().coerceIn(0, 7)
                                val rawWy0 = (startY / cellSize - 0.5f).roundToInt().coerceIn(0, 7)
                                var isHoriz = true
                                val curWx0 = if (flipBoard) 7 - rawWx0 else rawWx0
                                val curWy0 = if (flipBoard) 7 - rawWy0 else rawWy0
                                onDragWallPreview(curWx0, curWy0, isHoriz)

                                var finalUp: androidx.compose.ui.input.pointer.PointerInputChange? = null
                                while (true) {
                                    val dragEvent = awaitPointerEvent()
                                    val change = dragEvent.changes.firstOrNull()
                                    if (change == null || !change.pressed) {
                                        finalUp = change
                                        break
                                    } else {
                                        val dx = change.position.x - startX
                                        val dy = change.position.y - startY
                                        if (abs(dx) > 15f || abs(dy) > 15f) {
                                            isHoriz = abs(dx) > abs(dy)
                                        }
                                        val rawDragWx = (change.position.x / cellSize - 0.5f).roundToInt().coerceIn(0, 7)
                                        val rawDragWy = (change.position.y / cellSize - 0.5f).roundToInt().coerceIn(0, 7)
                                        val curWx = if (flipBoard) 7 - rawDragWx else rawDragWx
                                        val curWy = if (flipBoard) 7 - rawDragWy else rawDragWy
                                        onDragWallPreview(curWx, curWy, isHoriz)
                                    }
                                }
                                if (finalUp != null) {
                                    onConfirmDragWall()
                                }
                            }
                        }
                    }
                }
            }
    ) {
        val cellSize = size.width / 9f
        val wallThickness = 7.dp.toPx()
        val pawnRadius = cellSize * 0.36f

        val p1GoalColor = Player1Color.copy(alpha = 0.08f)
        val p2GoalColor = Player2Color.copy(alpha = 0.08f)
        val gridLineColor = Color(0xFFD4C3A3)
        val moveGlowColor = AccentGreen.copy(alpha = 0.22f)
        val pawnShadowColor = Color.Black.copy(alpha = 0.2f)
        val p1PawnHighlight = Color(0xFF60A5FA)
        val p2PawnHighlight = Color(0xFFF87171)
        val wallDotColor = WallColor.copy(alpha = 0.35f)

        // 1. Draw goal rows: Top row is always the local player's goal row!
        val topRowGoalColor = if (flipBoard) p2GoalColor else p1GoalColor
        val bottomRowGoalColor = if (flipBoard) p1GoalColor else p2GoalColor
        for (x in 0..8) {
            drawRect(
                color = topRowGoalColor,
                topLeft = Offset(x * cellSize, 0f),
                size = Size(cellSize, cellSize)
            )
            drawRect(
                color = bottomRowGoalColor,
                topLeft = Offset(x * cellSize, 8 * cellSize),
                size = Size(cellSize, cellSize)
            )
        }

        // Grid lines
        for (i in 1..8) {
            drawLine(
                color = gridLineColor,
                start = Offset(i * cellSize, 0f),
                end = Offset(i * cellSize, size.height),
                strokeWidth = 2f
            )
            drawLine(
                color = gridLineColor,
                start = Offset(0f, i * cellSize),
                end = Offset(size.width, i * cellSize),
                strokeWidth = 2f
            )
        }

        // 2. Highlight Valid Moves
        for ((vx, vy) in validMoves) {
            val drawVx = if (flipBoard) 8 - vx else vx
            val drawVy = if (flipBoard) 8 - vy else vy
            drawCircle(
                color = moveGlowColor,
                radius = cellSize * 0.36f,
                center = Offset(drawVx * cellSize + cellSize / 2f, drawVy * cellSize + cellSize / 2f)
            )
            drawCircle(
                color = AccentGreen,
                radius = cellSize * 0.16f,
                center = Offset(drawVx * cellSize + cellSize / 2f, drawVy * cellSize + cellSize / 2f)
            )
        }

        // 2b. Highlight Invalid tapped cell with subtle pulsing red wash and border
        invalidCell?.let { (ix, iy) ->
            val drawIx = if (flipBoard) 8 - ix else ix
            val drawIy = if (flipBoard) 8 - iy else iy
            if (invalidCellAlpha > 0.01f) {
                drawRoundRect(
                    color = Color(0xFFEF4444).copy(alpha = invalidCellAlpha * 0.35f),
                    topLeft = Offset(drawIx * cellSize + 2.dp.toPx(), drawIy * cellSize + 2.dp.toPx()),
                    size = Size(cellSize - 4.dp.toPx(), cellSize - 4.dp.toPx()),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFFEF4444).copy(alpha = invalidCellAlpha),
                    topLeft = Offset(drawIx * cellSize + 2.dp.toPx(), drawIy * cellSize + 2.dp.toPx()),
                    size = Size(cellSize - 4.dp.toPx(), cellSize - 4.dp.toPx()),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    style = Stroke(width = 2.5f.dp.toPx())
                )
            }
        }

        // 2c. Real-time visual indicator for latest pawn move
        gameState.lastMovedPlayerPos?.let { (mx, my) ->
            val drawMx = if (flipBoard) 8 - mx else mx
            val drawMy = if (flipBoard) 8 - my else my
            drawRoundRect(
                color = Color(0xFFFBBF24).copy(alpha = 0.28f),
                topLeft = Offset(drawMx * cellSize + 2.dp.toPx(), drawMy * cellSize + 2.dp.toPx()),
                size = Size(cellSize - 4.dp.toPx(), cellSize - 4.dp.toPx()),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFFF59E0B).copy(alpha = 0.65f),
                topLeft = Offset(drawMx * cellSize + 2.dp.toPx(), drawMy * cellSize + 2.dp.toPx()),
                size = Size(cellSize - 4.dp.toPx(), cellSize - 4.dp.toPx()),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // 3. Draw Pawns
        // Player 1 (Blue)
        val p1DrawX = if (flipBoard) 8 - gameState.player1.x else gameState.player1.x
        val p1DrawY = if (flipBoard) 8 - gameState.player1.y else gameState.player1.y
        val p1Center = Offset(
            p1DrawX * cellSize + cellSize / 2f,
            p1DrawY * cellSize + cellSize / 2f
        )
        // White halo ring around local player's pawn
        val isP1Local = (gameState.gameMode != GameMode.ONLINE || gameState.myPlayerNum == 1)
        if (isP1Local) {
            drawCircle(
                color = Color.White,
                radius = pawnRadius + 3.dp.toPx(),
                center = p1Center,
                style = Stroke(width = 2.5.dp.toPx())
            )
        }
        drawCircle(
            color = pawnShadowColor,
            radius = pawnRadius + 2.dp.toPx(),
            center = p1Center + Offset(0f, 2.dp.toPx())
        )
        drawCircle(
            color = gameState.player1.color,
            radius = pawnRadius,
            center = p1Center
        )
        drawCircle(
            color = p1PawnHighlight,
            radius = pawnRadius * 0.45f,
            center = p1Center - Offset(pawnRadius * 0.25f, pawnRadius * 0.25f)
        )

        // Player 2 (Red)
        val p2DrawX = if (flipBoard) 8 - gameState.player2.x else gameState.player2.x
        val p2DrawY = if (flipBoard) 8 - gameState.player2.y else gameState.player2.y
        val p2Center = Offset(
            p2DrawX * cellSize + cellSize / 2f,
            p2DrawY * cellSize + cellSize / 2f
        )
        val isP2Local = (gameState.gameMode == GameMode.ONLINE && gameState.myPlayerNum == 2)
        if (isP2Local) {
            drawCircle(
                color = Color.White,
                radius = pawnRadius + 3.dp.toPx(),
                center = p2Center,
                style = Stroke(width = 2.5.dp.toPx())
            )
        }
        drawCircle(
            color = pawnShadowColor,
            radius = pawnRadius + 2.dp.toPx(),
            center = p2Center + Offset(0f, 2.dp.toPx())
        )
        drawCircle(
            color = gameState.player2.color,
            radius = pawnRadius,
            center = p2Center
        )
        drawCircle(
            color = p2PawnHighlight,
            radius = pawnRadius * 0.45f,
            center = p2Center - Offset(pawnRadius * 0.25f, pawnRadius * 0.25f)
        )

        // 4. Draw placed walls
        for (w in gameState.walls) {
            val drawWx = if (flipBoard) 7 - w.x else w.x
            val drawWy = if (flipBoard) 7 - w.y else w.y
            val isLatest = (w == gameState.lastPlacedWall)
            val stroke = if (isLatest) wallThickness + 2.dp.toPx() else wallThickness
            val wallClr = if (isLatest) Color(0xFFE11D48) else WallColor
            if (w.isHorizontal) {
                val startX = drawWx * cellSize
                val endX = (drawWx + 2) * cellSize
                val lineY = (drawWy + 1) * cellSize
                if (isLatest) {
                    drawLine(
                        color = Color(0xFFFDA4AF).copy(alpha = 0.55f),
                        start = Offset(startX, lineY),
                        end = Offset(endX, lineY),
                        strokeWidth = stroke + 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                drawLine(
                    color = wallClr,
                    start = Offset(startX, lineY),
                    end = Offset(endX, lineY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            } else {
                val lineX = (drawWx + 1) * cellSize
                val startY = drawWy * cellSize
                val endY = (drawWy + 2) * cellSize
                if (isLatest) {
                    drawLine(
                        color = Color(0xFFFDA4AF).copy(alpha = 0.55f),
                        start = Offset(lineX, startY),
                        end = Offset(lineX, endY),
                        strokeWidth = stroke + 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                drawLine(
                    color = wallClr,
                    start = Offset(lineX, startY),
                    end = Offset(lineX, endY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }

        // 5. Draw pending wall preview
        pendingWall?.let { w ->
            val drawWx = if (flipBoard) 7 - w.x else w.x
            val drawWy = if (flipBoard) 7 - w.y else w.y
            val previewColor = if (wallValidationMsg != null) ErrorRed.copy(alpha = 0.85f) else AccentGreen.copy(alpha = 0.85f)
            if (w.isHorizontal) {
                val startX = drawWx * cellSize
                val endX = (drawWx + 2) * cellSize
                val lineY = (drawWy + 1) * cellSize
                drawLine(
                    color = previewColor,
                    start = Offset(startX, lineY),
                    end = Offset(endX, lineY),
                    strokeWidth = wallThickness + 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            } else {
                val lineX = (drawWx + 1) * cellSize
                val startY = drawWy * cellSize
                val endY = (drawWy + 2) * cellSize
                drawLine(
                    color = previewColor,
                    start = Offset(lineX, startY),
                    end = Offset(lineX, endY),
                    strokeWidth = wallThickness + 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // 6. When in Wall Mode, draw intersection indicator dots
        if (interactionMode == PlayerInteractionMode.PLACE_WALL) {
            for (wx in 0..7) {
                for (wy in 0..7) {
                    val interX = (wx + 1) * cellSize
                    val interY = (wy + 1) * cellSize
                    val actualWx = if (flipBoard) 7 - wx else wx
                    val actualWy = if (flipBoard) 7 - wy else wy
                    val isCurrentPending = pendingWall?.x == actualWx && pendingWall?.y == actualWy
                    if (isCurrentPending) {
                        drawCircle(
                            color = if (wallValidationMsg != null) ErrorRed else AccentGreen,
                            radius = 6.dp.toPx(),
                            center = Offset(interX, interY)
                        )
                    } else {
                        drawCircle(
                            color = wallDotColor,
                            radius = 3.5.dp.toPx(),
                            center = Offset(interX, interY)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnlineLiveVoiceEmojiBar(
    voiceState: VoiceChatState,
    isRealPeerConnected: Boolean,
    emojiCooldownSeconds: Int = 0,
    isVsComputer: Boolean = false,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSendEmoji: (String) -> Unit,
    darkTheme: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micPulse"
    )

    val isCooldownActive = emojiCooldownSeconds > 0
    var wasCooldownActive by remember { mutableStateOf(false) }
    var showHighlightPulse by remember { mutableStateOf(false) }

    LaunchedEffect(emojiCooldownSeconds) {
        if (emojiCooldownSeconds > 0) {
            wasCooldownActive = true
            showHighlightPulse = false
        } else if (wasCooldownActive && emojiCooldownSeconds == 0) {
            wasCooldownActive = false
            showHighlightPulse = true
            delay(1500)
            showHighlightPulse = false
        }
    }

    Surface(
        color = if (darkTheme) Color(0xFF1E293B) else Color(0xFFF8FAFC),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (showHighlightPulse) Color(0xFF10B981) else if (darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)
        ),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("online_voice_emoji_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Voice Chat Controls or VS AI Badge
            if (isVsComputer) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0),
                    modifier = Modifier.height(28.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🤖 VS AI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (darkTheme) Color.White else Color(0xFF334155)
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isMicActive = voiceState.isMicConnected && !voiceState.isMicMuted
                    val micBg = when {
                        !isRealPeerConnected -> if (darkTheme) Color(0xFF334155) else Color(0xFFCBD5E1)
                        isMicActive -> Color(0xFF10B981)
                        voiceState.isMicConnected && voiceState.isMicMuted -> Color(0xFFEF4444)
                        else -> Color(0xFF2563EB)
                    }

                    Surface(
                        onClick = onToggleMic,
                        enabled = isRealPeerConnected,
                        shape = RoundedCornerShape(20.dp),
                        color = micBg,
                        modifier = Modifier
                            .height(32.dp)
                            .scale(if (isMicActive) pulseScale else 1f)
                            .testTag("mic_toggle_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when {
                                    !isRealPeerConnected -> Icons.Default.MicOff
                                    isMicActive -> Icons.Default.Mic
                                    voiceState.isMicConnected && voiceState.isMicMuted -> Icons.Default.MicOff
                                    else -> Icons.Default.Mic
                                },
                                contentDescription = "Microphone",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when {
                                    !isRealPeerConnected -> "Mic Off"
                                    isMicActive -> "Live"
                                    voiceState.isMicConnected && voiceState.isMicMuted -> "Muted"
                                    else -> "Talk"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Speaker toggle
                    IconButton(
                        onClick = onToggleSpeaker,
                        enabled = isRealPeerConnected,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("speaker_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (voiceState.isSpeakerMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Speaker",
                            tint = if (voiceState.isSpeakerMuted) Color(0xFF94A3B8) else Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (voiceState.isRemoteVoiceActive) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(Color(0xFF10B981), CircleShape)
                        )
                    }
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .height(20.dp)
                    .width(1.dp)
                    .background(if (darkTheme) Color(0xFF334155) else Color(0xFFCBD5E1))
            )

            // Right: Quick Live Emoji Reactions with 5s Delay & Highlight Restoration
            val quickEmojis = listOf("😂", "🔥", "👍", "👋", "🤯", "😎", "🎯", "💀")
            Row(
                modifier = Modifier.padding(start = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Animated Cooldown Badge: ⏳ 5s, 4s, 3s, 2s, 1s
                AnimatedVisibility(
                    visible = isCooldownActive,
                    enter = fadeIn(tween(150)) + expandHorizontally(tween(150)),
                    exit = fadeOut(tween(150)) + shrinkHorizontally(tween(150))
                ) {
                    Surface(
                        color = if (darkTheme) Color(0xFF334155) else Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (darkTheme) Color(0xFFF59E0B) else Color(0xFFFDE68A)),
                        modifier = Modifier
                            .height(26.dp)
                            .padding(end = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⏳ ${emojiCooldownSeconds}s",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (darkTheme) Color(0xFFFBBF24) else Color(0xFFB45309)
                            )
                        }
                    }
                }

                // Emojis: Dimmed during 5s cooldown, brightly highlighted when cooldown finishes
                quickEmojis.forEach { emoji ->
                    val emojiAlpha = if (isCooldownActive) 0.35f else 1f
                    val isClickable = !isCooldownActive && (isRealPeerConnected || isVsComputer)
                    val emojiBg = when {
                        isCooldownActive -> Color.Transparent
                        showHighlightPulse -> if (darkTheme) Color(0xFF10B981).copy(alpha = 0.3f) else Color(0xFFD1FAE5)
                        else -> if (darkTheme) Color(0xFF334155).copy(alpha = 0.5f) else Color(0xFFE2E8F0).copy(alpha = 0.6f)
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(emojiBg)
                            .then(
                                if (showHighlightPulse && !isCooldownActive) {
                                    Modifier.border(1.dp, Color(0xFF10B981), CircleShape)
                                } else Modifier
                            )
                            .alpha(emojiAlpha)
                            .clickable(enabled = isClickable) {
                                onSendEmoji(emoji)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emoji,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingEmojisOverlay(
    emojis: List<FloatingEmoji>,
    darkTheme: Boolean
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        emojis.forEach { floating ->
            key(floating.id) {
                FloatingEmojiItem(
                    emoji = floating.emoji,
                    isFromOpponent = floating.isFromOpponent,
                    senderName = floating.senderName,
                    darkTheme = darkTheme
                )
            }
        }
    }
}

@Composable
fun BoxScope.FloatingEmojiItem(
    emoji: String,
    isFromOpponent: Boolean,
    senderName: String,
    darkTheme: Boolean
) {
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2000, easing = LinearEasing)
        )
    }

    val progress = animProgress.value
    val scale = when {
        progress < 0.2f -> (progress / 0.2f) * 1.15f
        progress < 0.35f -> 1.15f - ((progress - 0.2f) / 0.15f) * 0.15f
        progress > 0.8f -> (1f - (progress - 0.8f) / 0.2f).coerceAtLeast(0f)
        else -> 1f
    }
    val alpha = when {
        progress < 0.15f -> progress / 0.15f
        progress > 0.75f -> (1f - (progress - 0.75f) / 0.25f).coerceIn(0f, 1f)
        else -> 1f
    }

    // Position in screen margins completely outside the board, so the bot, pawn, and tiles are NEVER blocked
    val alignment = if (isFromOpponent) Alignment.TopEnd else Alignment.BottomEnd
    val yOffset = (-30.dp * progress)

    Box(
        modifier = Modifier
            .align(alignment)
            .padding(
                top = if (isFromOpponent) 70.dp else 0.dp,
                bottom = if (!isFromOpponent) 80.dp else 0.dp,
                end = 16.dp
            )
            .offset(y = yOffset)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = if (darkTheme) Color(0xF01E293B) else Color(0xF0FFFFFF),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(
                1.5.dp,
                if (isFromOpponent) Color(0xFFEF4444) else Color(0xFF10B981)
            ),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = emoji,
                    fontSize = 24.sp
                )
                if (senderName.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = senderName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (darkTheme) Color.White else Color(0xFF0F172A)
                    )
                }
            }
        }
    }
}
