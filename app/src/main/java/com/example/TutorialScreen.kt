package com.example

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppSettingsDialog
import com.example.ui.BlockPathLogo
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TutorialStep(val stepNumber: Int, val title: String) {
    PAWN_MOVE(1, "Move Your Pawn"),
    PLACE_WALL(2, "Place a Wall"),
    BFS_RULE(3, "Path Rule"),
    JUMP_OPPONENT(4, "Jump Opponent"),
    SETTINGS_GUIDE(5, "Change Settings"),
    COMPLETED(6, "You're Ready!")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialScreen(
    gameViewModel: GameViewModel,
    onFinishTutorial: () -> Unit,
    onStartVsAi: () -> Unit,
    onStartPassAndPlay: () -> Unit
) {
    val appSettings by gameViewModel.appSettings.collectAsState()
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var currentStep by remember { mutableStateOf(TutorialStep.PAWN_MOVE) }
    var interactionMode by remember { mutableStateOf(PlayerInteractionMode.MOVE_PAWN) }
    var isWallHorizontal by remember { mutableStateOf(true) }
    var pendingWall by remember { mutableStateOf<Wall?>(null) }
    var wallValidationMsg by remember { mutableStateOf<String?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var hasOpenedSettingsInTutorial by remember { mutableStateOf(false) }
    var showConfirmExitTutorialDialog by remember { mutableStateOf(false) }

    // Step-specific GameStates
    var tutorialGameState by remember {
        mutableStateOf(
            GameState(
                player1 = Player(1, 4, 7, 10, Player1Color),
                player2 = Player(2, 4, 2, 10, Player2Color),
                walls = emptyList(),
                currentPlayer = 1,
                gameMode = GameMode.VS_COMPUTER
            )
        )
    }

    var stepSuccessMsg by remember { mutableStateOf<String?>(null) }

    // Setup initial conditions whenever step changes
    LaunchedEffect(currentStep) {
        stepSuccessMsg = null
        pendingWall = null
        wallValidationMsg = null
        when (currentStep) {
            TutorialStep.PAWN_MOVE -> {
                interactionMode = PlayerInteractionMode.MOVE_PAWN
                tutorialGameState = GameState(
                    player1 = Player(1, 4, 7, 10, Player1Color),
                    player2 = Player(2, 4, 2, 10, Player2Color),
                    walls = emptyList(),
                    currentPlayer = 1
                )
            }
            TutorialStep.PLACE_WALL -> {
                interactionMode = PlayerInteractionMode.PLACE_WALL
                isWallHorizontal = true
                tutorialGameState = GameState(
                    player1 = Player(1, 4, 6, 10, Player1Color),
                    player2 = Player(2, 4, 3, 10, Player2Color),
                    walls = emptyList(),
                    currentPlayer = 1
                )
            }
            TutorialStep.BFS_RULE -> {
                interactionMode = PlayerInteractionMode.PLACE_WALL
                isWallHorizontal = true
                // Create walls that leave only 1 narrow escape for red
                tutorialGameState = GameState(
                    player1 = Player(1, 4, 6, 8, Player1Color),
                    player2 = Player(2, 0, 0, 8, Player2Color),
                    walls = listOf(
                        Wall(0, 1, true),
                        Wall(2, 1, true)
                    ),
                    currentPlayer = 1
                )
            }
            TutorialStep.JUMP_OPPONENT -> {
                interactionMode = PlayerInteractionMode.MOVE_PAWN
                tutorialGameState = GameState(
                    player1 = Player(1, 4, 4, 6, Player1Color),
                    player2 = Player(2, 4, 3, 6, Player2Color),
                    walls = emptyList(),
                    currentPlayer = 1
                )
            }
            TutorialStep.SETTINGS_GUIDE -> {
                interactionMode = PlayerInteractionMode.MOVE_PAWN
            }
            TutorialStep.COMPLETED -> {
                gameViewModel.setFirstLaunchCompleted()
            }
        }
    }

    // Valid moves based on current tutorial step
    val validMoves = remember(tutorialGameState, currentStep) {
        when (currentStep) {
            TutorialStep.PAWN_MOVE -> listOf(Pair(4, 6)) // Guide them forward
            TutorialStep.JUMP_OPPONENT -> listOf(Pair(4, 2)) // Guide them to jump straight
            else -> gameViewModel.getValidMoves(tutorialGameState, 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Interactive Practice / Tutorial",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (appSettings.darkTheme) Color.White else WallColor
                        )
                        Text(
                            text = "Step ${currentStep.stepNumber} of 5: ${currentStep.title}",
                            fontSize = 12.sp,
                            color = Player1Color,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentStep != TutorialStep.COMPLETED) {
                                showConfirmExitTutorialDialog = true
                            } else {
                                onFinishTutorial()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Exit Tutorial",
                            tint = if (appSettings.darkTheme) Color.White else WallColor
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onFinishTutorial) {
                        Text(
                            text = "Skip",
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (appSettings.darkTheme) Color(0xFF0F172A) else Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (appSettings.darkTheme) Color(0xFF0B1120) else AppBackground)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Step Progress Indicator bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TutorialStep.values().take(5).forEach { step ->
                    val isPast = step.stepNumber < currentStep.stepNumber
                    val isCurrent = step == currentStep
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    isPast -> AccentGreen
                                    isCurrent -> Player1Color
                                    else -> if (appSettings.darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)
                                }
                            )
                    )
                }
            }

            // Instructions Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                color = if (appSettings.darkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (stepSuccessMsg != null) AccentGreen else (if (appSettings.darkTheme) Color(0xFF334155) else Color(0xFFCBD5E1))
                ),
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (currentStep) {
                                TutorialStep.PAWN_MOVE -> Icons.Default.DirectionsWalk
                                TutorialStep.PLACE_WALL -> Icons.Default.Block
                                TutorialStep.BFS_RULE -> Icons.Default.WarningAmber
                                TutorialStep.JUMP_OPPONENT -> Icons.Default.FastForward
                                TutorialStep.SETTINGS_GUIDE -> Icons.Default.Settings
                                TutorialStep.COMPLETED -> Icons.Default.EmojiEvents
                            },
                            contentDescription = null,
                            tint = if (stepSuccessMsg != null) AccentGreen else Player1Color,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (currentStep) {
                                TutorialStep.PAWN_MOVE -> "1. How Pawns Move (Goti Chalao)"
                                TutorialStep.PLACE_WALL -> "2. How Walls Work (Diwar Lagana)"
                                TutorialStep.BFS_RULE -> "3. Golden Rule: Never Trap Completely"
                                TutorialStep.JUMP_OPPONENT -> "4. Leap Over Opponent (Chhalang)"
                                TutorialStep.SETTINGS_GUIDE -> "5. How to Change Settings"
                                TutorialStep.COMPLETED -> "Ready to Play!"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (appSettings.darkTheme) Color.White else Color(0xFF0F172A)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = when (currentStep) {
                            TutorialStep.PAWN_MOVE ->
                                "Blue is your pawn! On each turn, you can move 1 step. Your goal is to reach the TOP row (opponent's base).\n👉 Tap the highlighted green tile ahead to move!"
                            TutorialStep.PLACE_WALL ->
                                "You have 10 walls. Walls are 2 tiles wide. Select 'Horizontal', tap the highlighted intersection dot below, and click Confirm to block Red's path!"
                            TutorialStep.BFS_RULE ->
                                "Golden Quoridor Rule: A wall can NEVER completely block an opponent from reaching their goal. At least one open path must always exist!"
                            TutorialStep.JUMP_OPPONENT ->
                                "When facing your opponent head-to-head, you can JUMP over them! Tap the green square directly behind the red pawn to leap over it!"
                            TutorialStep.SETTINGS_GUIDE ->
                                "Customize your controls (Button taps vs Drag), Sound effects, and Dark theme anytime! Tap 'Open Settings' below to try changing them now."
                            TutorialStep.COMPLETED ->
                                "Congratulations! You now know how pawns move, how to place walls strategically, and how to change settings. Choose an option below to begin!"
                        },
                        fontSize = 12.sp,
                        color = if (appSettings.darkTheme) Color(0xFFCBD5E1) else Color(0xFF334155),
                        lineHeight = 16.sp
                    )

                    if (stepSuccessMsg != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stepSuccessMsg!!,
                                color = AccentGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Board in the center (Fixed aspect ratio 1:1)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                InteractiveBoard(
                    gameState = tutorialGameState,
                    validMoves = if (stepSuccessMsg == null) validMoves else emptyList(),
                    invalidCell = null,
                    invalidCellAlpha = 0f,
                    interactionMode = interactionMode,
                    isWallHorizontal = isWallHorizontal,
                    pendingWall = pendingWall,
                    wallValidationMsg = wallValidationMsg,
                    appSettings = appSettings,
                    onCellClicked = { x, y ->
                        if (currentStep == TutorialStep.PAWN_MOVE && x == 4 && y == 6) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            tutorialGameState = tutorialGameState.copy(
                                player1 = tutorialGameState.player1.copy(x = 4, y = 6)
                            )
                            stepSuccessMsg = "Great move! Opponent now takes a turn."
                            coroutineScope.launch {
                                delay(600)
                                tutorialGameState = tutorialGameState.copy(
                                    player2 = tutorialGameState.player2.copy(x = 4, y = 3)
                                )
                                delay(400)
                                stepSuccessMsg = "Nice! You've learned pawn movement. Let's learn wall placement."
                            }
                        } else if (currentStep == TutorialStep.JUMP_OPPONENT && x == 4 && y == 2) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            tutorialGameState = tutorialGameState.copy(
                                player1 = tutorialGameState.player1.copy(x = 4, y = 2)
                            )
                            stepSuccessMsg = "Awesome leap! You jumped over the opponent."
                        }
                    },
                    onWallIntersectionClicked = { wx, wy ->
                        if (currentStep == TutorialStep.PLACE_WALL) {
                            // Guide to place near (3, 3)
                            pendingWall = Wall(wx, wy, isWallHorizontal)
                            val validation = gameViewModel.checkWallPlacement(tutorialGameState, pendingWall!!)
                            wallValidationMsg = validation
                        } else if (currentStep == TutorialStep.BFS_RULE) {
                            pendingWall = Wall(wx, wy, isWallHorizontal)
                            // If user tries to block (1, 0)
                            val validation = gameViewModel.checkWallPlacement(tutorialGameState, pendingWall!!)
                            wallValidationMsg = validation ?: "Notice how the system checks path connectivity!"
                        }
                    },
                    onDragWallPreview = { wx, wy, isHoriz ->
                        pendingWall = Wall(wx, wy, isHoriz)
                        val validation = gameViewModel.checkWallPlacement(tutorialGameState, pendingWall!!)
                        wallValidationMsg = validation
                    },
                    onConfirmDragWall = {
                        if (pendingWall != null && wallValidationMsg == null) {
                            tutorialGameState = tutorialGameState.copy(
                                walls = tutorialGameState.walls + pendingWall!!,
                                player1 = tutorialGameState.player1.copy(walls = tutorialGameState.player1.walls - 1)
                            )
                            pendingWall = null
                            stepSuccessMsg = "Wall locked in place! Opponent's path is blocked."
                        }
                    }
                )
            }

            // Controls & Next Step Bottom Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (currentStep) {
                    TutorialStep.PAWN_MOVE -> {
                        if (stepSuccessMsg != null) {
                            Button(
                                onClick = { currentStep = TutorialStep.PLACE_WALL },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Next: Learn How to Place Walls →", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        } else {
                            Text(
                                text = "👆 Tap the green tile on row 6 to move forward",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Player1Color
                            )
                        }
                    }

                    TutorialStep.PLACE_WALL -> {
                        if (stepSuccessMsg == null) {
                            // Wall Controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        isWallHorizontal = true
                                        if (pendingWall != null) pendingWall = pendingWall!!.copy(isHorizontal = true)
                                    },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isWallHorizontal) WallColor else Color(0xFFE2E8F0),
                                        contentColor = if (isWallHorizontal) Color.White else WallColor
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Horizontal", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        isWallHorizontal = false
                                        if (pendingWall != null) pendingWall = pendingWall!!.copy(isHorizontal = false)
                                    },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (!isWallHorizontal) WallColor else Color(0xFFE2E8F0),
                                        contentColor = if (!isWallHorizontal) Color.White else WallColor
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Vertical", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (pendingWall == null) {
                                Button(
                                    onClick = {
                                        // Auto suggest intersection (3, 3) to help new user
                                        pendingWall = Wall(3, 3, isWallHorizontal)
                                        wallValidationMsg = gameViewModel.checkWallPlacement(tutorialGameState, pendingWall!!)
                                    },
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.AdsClick, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Show Me Where to Place Wall", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (wallValidationMsg == null) {
                                            tutorialGameState = tutorialGameState.copy(
                                                walls = tutorialGameState.walls + pendingWall!!,
                                                player1 = tutorialGameState.player1.copy(walls = tutorialGameState.player1.walls - 1)
                                            )
                                            pendingWall = null
                                            stepSuccessMsg = "Wall placed! Opponent is now forced to take a longer path."
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(44.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (wallValidationMsg == null) AccentGreen else Color.Gray
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    enabled = wallValidationMsg == null
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Confirm Wall", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        } else {
                            Button(
                                onClick = { currentStep = TutorialStep.BFS_RULE },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Next: The Pathfinding Rule →", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    TutorialStep.BFS_RULE -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Notice: At least 1 path must remain open for both players at all times!",
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = if (appSettings.darkTheme) Color(0xFFE2E8F0) else Color(0xFF334155),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { currentStep = TutorialStep.JUMP_OPPONENT },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Player1Color),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Next: Learn How to Jump Opponent →", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    TutorialStep.JUMP_OPPONENT -> {
                        if (stepSuccessMsg != null) {
                            Button(
                                onClick = { currentStep = TutorialStep.SETTINGS_GUIDE },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Next: Learn How to Change Settings ⚙️ →", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        } else {
                            Text(
                                text = "👆 Pawns are face-to-face! Tap the square behind Red to leap over him.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Player1Color,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    TutorialStep.SETTINGS_GUIDE -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    showSettingsDialog = true
                                    hasOpenedSettingsInTutorial = true
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("⚙️ Open Settings (Test Controls & Themes)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (hasOpenedSettingsInTutorial) {
                                Button(
                                    onClick = { currentStep = TutorialStep.COMPLETED },
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Finish Tutorial & Start Playing! 🎉", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            } else {
                                Text(
                                    text = "Tap 'Open Settings' above to see how to switch between Tap & Drag modes and Dark Theme.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    TutorialStep.COMPLETED -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onStartVsAi,
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Play vs AI", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }

                                Button(
                                    onClick = onStartPassAndPlay,
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Local 2-Player", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            TextButton(onClick = onFinishTutorial) {
                                Text("Back to Home Menu", color = Player1Color, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // Confirmation Dialog: Exit Tutorial Early
        if (showConfirmExitTutorialDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmExitTutorialDialog = false },
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
                        text = "Exit Tutorial?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Text(
                        text = "You are currently on Step ${currentStep.stepNumber} of 5. Are you sure you want to leave the practice guide?",
                        fontSize = 14.sp,
                        color = if (appSettings.darkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showConfirmExitTutorialDialog = false
                            onFinishTutorial()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Exit Tutorial", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showConfirmExitTutorialDialog = false },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Continue Learning", color = if (appSettings.darkTheme) Color.White else Color(0xFF334155))
                    }
                },
                shape = RoundedCornerShape(18.dp)
            )
        }

        // Shared Settings Dialog
        if (showSettingsDialog) {
            AppSettingsDialog(
                appSettings = appSettings,
                onSettingsChanged = { gameViewModel.updateSettings(it) },
                onDismiss = { showSettingsDialog = false },
                onReplayTutorial = {
                    showSettingsDialog = false
                    currentStep = TutorialStep.PAWN_MOVE
                }
            )
        }
    }
}
