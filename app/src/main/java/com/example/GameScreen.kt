package com.example
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.BlockPathLogo
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PlayerInteractionMode {
    MOVE_PAWN,
    PLACE_WALL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    gameViewModel: GameViewModel,
    onBack: () -> Unit
) {
    val gameState by gameViewModel.gameState.collectAsState()
    val appSettings by gameViewModel.appSettings.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity
    val app = context.applicationContext as? BlockPathApplication

    var interactionMode by remember { mutableStateOf(PlayerInteractionMode.MOVE_PAWN) }
    var isWallHorizontal by remember { mutableStateOf(true) }
    var pendingWall by remember { mutableStateOf<Wall?>(null) }
    var wallValidationMsg by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showRewardDialog by remember { mutableStateOf(false) }

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

    // Reset interaction mode when player turn changes
    LaunchedEffect(gameState.currentPlayer) {
        pendingWall = null
        wallValidationMsg = null
        interactionMode = PlayerInteractionMode.MOVE_PAWN
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BlockPath", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (appSettings.darkTheme) Color.White else WallColor) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                            pendingWall = null
                            gameViewModel.resetGame()
                        },
                        modifier = Modifier.testTag("reset_game_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Restart Game", tint = if (appSettings.darkTheme) Color.White else WallColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = if (appSettings.darkTheme) Color(0xFF0F172A) else AppBackground)
            )
        },
        containerColor = if (appSettings.darkTheme) Color(0xFF0F172A) else AppBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Player 2 Area (Top - Rotated 180 for Pass & Play)
            if (gameState.gameMode == GameMode.LOCAL_PASS_AND_PLAY) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { rotationZ = 180f },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PlayerActionControls(
                        gameState = gameState,
                        playerNum = 2,
                        interactionMode = interactionMode,
                        isWallHorizontal = isWallHorizontal,
                        pendingWall = pendingWall,
                        wallValidationMsg = wallValidationMsg,
                        appSettings = appSettings,
                        onInteractionModeChange = { 
                            interactionMode = it
                            pendingWall = null
                            wallValidationMsg = null
                        },
                        onOrientationChange = { horiz ->
                            isWallHorizontal = horiz
                            pendingWall?.let { w ->
                                val candidate = w.copy(isHorizontal = horiz)
                                pendingWall = candidate
                                wallValidationMsg = gameViewModel.checkWallPlacement(gameState, candidate)
                            }
                        },
                        onConfirmWall = { w ->
                            gameViewModel.handleAction(GameAction.PlaceWall(2, w))
                            pendingWall = null
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    PlayerInfoClassic(player = gameState.player2, name = "Player 2")
                }
            } else {
                PlayerInfoClassic(player = gameState.player2, name = "AI")
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Turn Indicator
            Text(
                text = if (gameState.winner != null) "Game Over" else if (gameState.isAiThinking) "AI Thinking..." else "Turn: ${if (gameState.currentPlayer == 1) "Player 1" else if (gameState.gameMode == GameMode.VS_COMPUTER) "AI" else "Player 2"}",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (gameState.winner != null) AccentGreen else if (gameState.currentPlayer == 1) Player1Color else Player2Color
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Board Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .graphicsLayer { 
                        shadowElevation = 12.dp.toPx()
                        shape = RoundedCornerShape(16.dp)
                        clip = true
                        translationX = shakeOffset.value
                    }
                    .background(if (appSettings.darkTheme) Color(0xFF1E293B) else BoardBackground, RoundedCornerShape(16.dp))
                    .border(2.dp, if (appSettings.darkTheme) Color(0xFF334155) else Color(0xFFD4C3A3), RoundedCornerShape(16.dp))
                    .padding(8.dp)
                    .testTag("game_board_canvas")
            ) {
                InteractiveBoard(
                    gameState = gameState,
                    validMoves = if (interactionMode == PlayerInteractionMode.MOVE_PAWN && !gameState.isAiThinking) validMoves else emptyList(),
                    invalidCell = invalidCell,
                    invalidCellAlpha = invalidCellAlpha.value,
                    interactionMode = interactionMode,
                    isWallHorizontal = isWallHorizontal,
                    pendingWall = pendingWall,
                    wallValidationMsg = wallValidationMsg,
                    appSettings = appSettings,
                    onCellClicked = { x, y ->
                        if (gameState.winner != null || gameState.isAiThinking) return@InteractiveBoard
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
                        if (gameState.winner != null || gameState.isAiThinking) return@InteractiveBoard
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
                    },
                    onDragWallPreview = { wx, wy, isHoriz ->
                        if (gameState.winner != null || gameState.isAiThinking) return@InteractiveBoard
                        val currentWalls = if (gameState.currentPlayer == 1) gameState.player1.walls else gameState.player2.walls
                        if (currentWalls <= 0) {
                            wallValidationMsg = "No walls left!"
                            return@InteractiveBoard
                        }
                        val candidate = Wall(wx, wy, isHoriz)
                        val err = gameViewModel.checkWallPlacement(gameState, candidate)
                        pendingWall = candidate
                        wallValidationMsg = err
                    },
                    onConfirmDragWall = {
                        if (gameState.winner != null || gameState.isAiThinking) return@InteractiveBoard
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

            Spacer(modifier = Modifier.height(6.dp))

            // Player 1 Area (Bottom - Normal)
            PlayerInfoClassic(
                player = gameState.player1,
                name = "Player 1",
                extraTag = if (gameState.hasUsedRewardedWalls) "• +2 Bonus Used" else null
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            PlayerActionControls(
                gameState = gameState,
                playerNum = 1,
                interactionMode = interactionMode,
                isWallHorizontal = isWallHorizontal,
                pendingWall = pendingWall,
                wallValidationMsg = wallValidationMsg,
                appSettings = appSettings,
                onRequestRewardAd = { showRewardDialog = true },
                onInteractionModeChange = { 
                    interactionMode = it
                    pendingWall = null
                    wallValidationMsg = null
                },
                onOrientationChange = { horiz ->
                    isWallHorizontal = horiz
                    pendingWall?.let { w ->
                        val candidate = w.copy(isHorizontal = horiz)
                        pendingWall = candidate
                        wallValidationMsg = gameViewModel.checkWallPlacement(gameState, candidate)
                    }
                },
                onConfirmWall = { w ->
                    gameViewModel.handleAction(GameAction.PlaceWall(1, w))
                    pendingWall = null
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
                            .height(42.dp)
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

            Spacer(modifier = Modifier.height(4.dp))
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
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text("Wall Button Controls", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Switch(
                                checked = appSettings.classicControls,
                                onCheckedChange = { gameViewModel.updateSettings(appSettings.copy(classicControls = it)) }
                            )
                        }
                        Text("Use Horizontal/Vertical buttons to place walls instead of dragging (Recommended for mobile screens).", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text("Sound & Haptics", modifier = Modifier.weight(1f))
                            Switch(
                                checked = appSettings.soundEnabled,
                                onCheckedChange = { gameViewModel.updateSettings(appSettings.copy(soundEnabled = it)) }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text("Dark Theme", modifier = Modifier.weight(1f))
                            Switch(
                                checked = appSettings.darkTheme,
                                onCheckedChange = { gameViewModel.updateSettings(appSettings.copy(darkTheme = it)) }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettings = false }) { Text("Close") }
                }
            )
        }

        if (gameState.winner != null) {
            VictoryCelebrationDialog(
                winner = gameState.winner!!,
                gameMode = gameState.gameMode,
                moveCount = gameState.moveCount,
                onPlayAgain = {
                    gameViewModel.resetGame()
                    interactionMode = PlayerInteractionMode.MOVE_PAWN
                    pendingWall = null
                    wallValidationMsg = null
                },
                onMenu = onBack
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
    gameMode: GameMode,
    moveCount: Int,
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

    val winnerColor = if (winner == 1) Player1Color else Player2Color
    val winnerName = if (winner == 1) {
        "Player 1 Wins!"
    } else if (gameMode == GameMode.VS_COMPUTER) {
        "Computer AI Wins!"
    } else {
        "Player 2 Wins!"
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

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action Buttons
                    Button(
                        onClick = onPlayAgain,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("play_again_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Play Again", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
fun PlayerActionControls(
    gameState: GameState,
    playerNum: Int,
    interactionMode: PlayerInteractionMode,
    isWallHorizontal: Boolean,
    pendingWall: Wall?,
    wallValidationMsg: String?,
    appSettings: AppSettings,
    onRequestRewardAd: (() -> Unit)? = null,
    onInteractionModeChange: (PlayerInteractionMode) -> Unit,
    onOrientationChange: (Boolean) -> Unit,
    onConfirmWall: (Wall) -> Unit
) {
    val isMyTurn = gameState.currentPlayer == playerNum
    val currentWalls = if (playerNum == 1) gameState.player1.walls else gameState.player2.walls
    val alpha = if (isMyTurn) 1f else 0.4f
    val canWatchAdForWalls = (playerNum == 1 &&
            gameState.gameMode == GameMode.VS_COMPUTER &&
            currentWalls <= 0 &&
            !gameState.hasUsedRewardedWalls)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onInteractionModeChange(PlayerInteractionMode.MOVE_PAWN) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (interactionMode == PlayerInteractionMode.MOVE_PAWN) WallColor else Color(0xFFE2E8F0),
                    contentColor = if (interactionMode == PlayerInteractionMode.MOVE_PAWN) Color.White else Color(0xFF334155)
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = if (interactionMode == PlayerInteractionMode.MOVE_PAWN) 3.dp else 0.dp),
                enabled = isMyTurn && !gameState.isAiThinking && gameState.winner == null
            ) {
                Text(
                    text = "Move Pawn",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Button(
                onClick = {
                    if (canWatchAdForWalls) {
                        onRequestRewardAd?.invoke()
                    } else {
                        onInteractionModeChange(PlayerInteractionMode.PLACE_WALL)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canWatchAdForWalls) Color(0xFFD97706)
                        else if (interactionMode == PlayerInteractionMode.PLACE_WALL) WallColor 
                        else Color(0xFFE2E8F0),
                    contentColor = if (canWatchAdForWalls || interactionMode == PlayerInteractionMode.PLACE_WALL) Color.White 
                        else Color(0xFF334155)
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = if (canWatchAdForWalls || interactionMode == PlayerInteractionMode.PLACE_WALL) 3.dp else 0.dp
                ),
                enabled = isMyTurn && !gameState.isAiThinking && (currentWalls > 0 || canWatchAdForWalls) && gameState.winner == null
            ) {
                if (canWatchAdForWalls) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "+2 Walls 🎬",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                } else {
                    Text(
                        text = "Place Wall ($currentWalls)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        if (interactionMode == PlayerInteractionMode.PLACE_WALL && isMyTurn) {
            if (appSettings.classicControls) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onOrientationChange(true) },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isWallHorizontal) WallColor else if (appSettings.darkTheme) Color(0xFF334155) else Color(0xFFF1F5F9),
                            contentColor = if (isWallHorizontal) Color.White else WallColor
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = if (!isWallHorizontal) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFCBD5E1)) else null,
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (isWallHorizontal) 2.dp else 0.dp),
                        enabled = isMyTurn
                    ) {
                        // Visual horizontal bar indicator
                        Box(
                            modifier = Modifier
                                .size(width = 14.dp, height = 4.dp)
                                .background(if (isWallHorizontal) Color.White else WallColor, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Horizontal",
                            fontWeight = if (isWallHorizontal) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = { onOrientationChange(false) },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isWallHorizontal) WallColor else if (appSettings.darkTheme) Color(0xFF334155) else Color(0xFFF1F5F9),
                            contentColor = if (!isWallHorizontal) Color.White else WallColor
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = if (isWallHorizontal) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFCBD5E1)) else null,
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (!isWallHorizontal) 2.dp else 0.dp),
                        enabled = isMyTurn
                    ) {
                        // Visual vertical bar indicator
                        Box(
                            modifier = Modifier
                                .size(width = 4.dp, height = 14.dp)
                                .background(if (!isWallHorizontal) Color.White else WallColor, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Vertical",
                            fontWeight = if (!isWallHorizontal) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (pendingWall == null) {
                    Surface(
                        color = if (appSettings.darkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (appSettings.darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "👉 Tap any grid intersection point to place wall",
                            fontSize = 11.sp,
                            color = if (appSettings.darkTheme) Color(0xFF94A3B8) else Color(0xFF64748B),
                            fontWeight = FontWeight.Medium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                } else {
                    if (wallValidationMsg == null) {
                        Button(
                            onClick = { onConfirmWall(pendingWall) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            shape = RoundedCornerShape(10.dp),
                            enabled = isMyTurn
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Confirm ${if (isWallHorizontal) "Horizontal" else "Vertical"} Wall",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        Surface(
                            color = Color(0x18EF4444),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33EF4444)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = ErrorRed,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = wallValidationMsg,
                                    color = ErrorRed,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Drag on the board to place a wall.", fontSize = 12.sp, color = WallColor, fontWeight = FontWeight.Medium)
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
    onCellClicked: (Int, Int) -> Unit,
    onWallIntersectionClicked: (Int, Int) -> Unit,
    onDragWallPreview: (Int, Int, Boolean) -> Unit,
    onConfirmDragWall: () -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(gameState, interactionMode, isWallHorizontal, appSettings) {
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
                                if (change != null) {
                                    finalUp = change
                                    if (!change.pressed) break
                                }
                            }
                            val cellX = (finalUp.position.x / cellSize).toInt().coerceIn(0, 8)
                            val cellY = (finalUp.position.y / cellSize).toInt().coerceIn(0, 8)
                            onCellClicked(cellX, cellY)
                        } else {
                            if (appSettings.classicControls) {
                                var finalUp = down
                                while (true) {
                                    val upEvent = awaitPointerEvent()
                                    val change = upEvent.changes.firstOrNull()
                                    if (change != null) {
                                        finalUp = change
                                        if (!change.pressed) break
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
                                onWallIntersectionClicked(closestWx, closestWy)
                            } else {
                                var currentWx = (startX / cellSize - 0.5f).roundToInt().coerceIn(0, 7)
                                var currentWy = (startY / cellSize - 0.5f).roundToInt().coerceIn(0, 7)
                                var isHoriz = true
                                onDragWallPreview(currentWx, currentWy, isHoriz)

                                var finalUp: androidx.compose.ui.input.pointer.PointerInputChange? = null
                                while (true) {
                                    val dragEvent = awaitPointerEvent()
                                    val change = dragEvent.changes.firstOrNull()
                                    if (change != null) {
                                        if (change.pressed) {
                                            val dx = change.position.x - startX
                                            val dy = change.position.y - startY
                                            if (abs(dx) > 15f || abs(dy) > 15f) {
                                                isHoriz = abs(dx) > abs(dy)
                                            }
                                            currentWx = (change.position.x / cellSize - 0.5f).roundToInt().coerceIn(0, 7)
                                            currentWy = (change.position.y / cellSize - 0.5f).roundToInt().coerceIn(0, 7)
                                            onDragWallPreview(currentWx, currentWy, isHoriz)
                                        } else {
                                            finalUp = change
                                            break
                                        }
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

        // 1. Draw 9x9 board cells and grid lines
        for (x in 0..8) {
            for (y in 0..8) {
                // Goal rows subtle highlight
                if (y == 0) {
                    drawRect(
                        color = Player1Color.copy(alpha = 0.08f),
                        topLeft = Offset(x * cellSize, y * cellSize),
                        size = Size(cellSize, cellSize)
                    )
                } else if (y == 8) {
                    drawRect(
                        color = Player2Color.copy(alpha = 0.08f),
                        topLeft = Offset(x * cellSize, y * cellSize),
                        size = Size(cellSize, cellSize)
                    )
                }
            }
        }

        // Grid lines
        for (i in 1..8) {
            // vertical line
            drawLine(
                color = if (appSettings.darkTheme) Color(0xFF334155) else Color(0xFFD4C3A3),
                start = Offset(i * cellSize, 0f),
                end = Offset(i * cellSize, size.height),
                strokeWidth = 2f
            )
            // horizontal line
            drawLine(
                color = if (appSettings.darkTheme) Color(0xFF334155) else Color(0xFFD4C3A3),
                start = Offset(0f, i * cellSize),
                end = Offset(size.width, i * cellSize),
                strokeWidth = 2f
            )
        }

        // 2. Highlight Valid Moves
        for ((vx, vy) in validMoves) {
            // Subtle glowing circle for valid move
            drawCircle(
                color = AccentGreen.copy(alpha = 0.22f),
                radius = cellSize * 0.36f,
                center = Offset(vx * cellSize + cellSize / 2f, vy * cellSize + cellSize / 2f)
            )
            drawCircle(
                color = AccentGreen,
                radius = cellSize * 0.16f,
                center = Offset(vx * cellSize + cellSize / 2f, vy * cellSize + cellSize / 2f)
            )
        }

        // 2b. Highlight Invalid tapped cell with subtle pulsing red wash and border
        invalidCell?.let { (ix, iy) ->
            if (invalidCellAlpha > 0.01f) {
                drawRoundRect(
                    color = Color(0xFFEF4444).copy(alpha = invalidCellAlpha * 0.35f),
                    topLeft = Offset(ix * cellSize + 2.dp.toPx(), iy * cellSize + 2.dp.toPx()),
                    size = Size(cellSize - 4.dp.toPx(), cellSize - 4.dp.toPx()),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFFEF4444).copy(alpha = invalidCellAlpha),
                    topLeft = Offset(ix * cellSize + 2.dp.toPx(), iy * cellSize + 2.dp.toPx()),
                    size = Size(cellSize - 4.dp.toPx(), cellSize - 4.dp.toPx()),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    style = Stroke(width = 2.5f.dp.toPx())
                )
            }
        }

        // 3. Draw Pawns
        // Player 1 (Blue)
        val p1Center = Offset(
            gameState.player1.x * cellSize + cellSize / 2f,
            gameState.player1.y * cellSize + cellSize / 2f
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.2f),
            radius = pawnRadius + 2.dp.toPx(),
            center = p1Center + Offset(0f, 2.dp.toPx())
        )
        drawCircle(
            color = gameState.player1.color,
            radius = pawnRadius,
            center = p1Center
        )
        drawCircle(
            color = Color(0xFF60A5FA),
            radius = pawnRadius * 0.45f,
            center = p1Center - Offset(pawnRadius * 0.25f, pawnRadius * 0.25f)
        )

        // Player 2 (Red)
        val p2Center = Offset(
            gameState.player2.x * cellSize + cellSize / 2f,
            gameState.player2.y * cellSize + cellSize / 2f
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.2f),
            radius = pawnRadius + 2.dp.toPx(),
            center = p2Center + Offset(0f, 2.dp.toPx())
        )
        drawCircle(
            color = gameState.player2.color,
            radius = pawnRadius,
            center = p2Center
        )
        drawCircle(
            color = Color(0xFFF87171),
            radius = pawnRadius * 0.45f,
            center = p2Center - Offset(pawnRadius * 0.25f, pawnRadius * 0.25f)
        )

        // 4. Draw placed walls
        for (w in gameState.walls) {
            if (w.isHorizontal) {
                val startX = w.x * cellSize
                val endX = (w.x + 2) * cellSize
                val lineY = (w.y + 1) * cellSize
                drawLine(
                    color = WallColor,
                    start = Offset(startX, lineY),
                    end = Offset(endX, lineY),
                    strokeWidth = wallThickness,
                    cap = StrokeCap.Round
                )
            } else {
                val lineX = (w.x + 1) * cellSize
                val startY = w.y * cellSize
                val endY = (w.y + 2) * cellSize
                drawLine(
                    color = WallColor,
                    start = Offset(lineX, startY),
                    end = Offset(lineX, endY),
                    strokeWidth = wallThickness,
                    cap = StrokeCap.Round
                )
            }
        }

        // 5. Draw pending wall preview
        pendingWall?.let { w ->
            val previewColor = if (wallValidationMsg != null) ErrorRed.copy(alpha = 0.85f) else AccentGreen.copy(alpha = 0.85f)
            if (w.isHorizontal) {
                val startX = w.x * cellSize
                val endX = (w.x + 2) * cellSize
                val lineY = (w.y + 1) * cellSize
                drawLine(
                    color = previewColor,
                    start = Offset(startX, lineY),
                    end = Offset(endX, lineY),
                    strokeWidth = wallThickness + 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            } else {
                val lineX = (w.x + 1) * cellSize
                val startY = w.y * cellSize
                val endY = (w.y + 2) * cellSize
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
                    val isCurrentPending = pendingWall?.x == wx && pendingWall?.y == wy
                    if (isCurrentPending) {
                        drawCircle(
                            color = if (wallValidationMsg != null) ErrorRed else AccentGreen,
                            radius = 6.dp.toPx(),
                            center = Offset(interX, interY)
                        )
                    } else {
                        drawCircle(
                            color = WallColor.copy(alpha = 0.35f),
                            radius = 3.5.dp.toPx(),
                            center = Offset(interX, interY)
                        )
                    }
                }
            }
        }
    }
}
