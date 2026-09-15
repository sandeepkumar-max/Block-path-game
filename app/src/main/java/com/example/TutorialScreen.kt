package com.example

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppSettingsDialog
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TutorialStep(val stepNumber: Int) {
    PAWN_MOVE(1),
    PLACE_WALL(2),
    BFS_RULE(3),
    JUMP_OPPONENT(4),
    ONLINE_MULTIPLAYER(5),
    SETTINGS_GUIDE(6),
    COMPLETED(7)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialScreen(
    gameViewModel: GameViewModel,
    onFinishTutorial: () -> Unit,
    onStartVsAi: () -> Unit,
    onStartPassAndPlay: () -> Unit,
    onStartOnline: () -> Unit = onFinishTutorial
) {
    val appSettings by gameViewModel.appSettings.collectAsState()
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Language selection: Default to Hindi if device locale starts with 'hi', else English
    val initialLang = remember {
        if (java.util.Locale.getDefault().language.lowercase().startsWith("hi")) {
            TutorialLanguage.HINDI
        } else {
            TutorialLanguage.ENGLISH
        }
    }
    var selectedLanguage by remember { mutableStateOf(initialLang) }

    var currentStep by remember { mutableStateOf(TutorialStep.PAWN_MOVE) }
    var interactionMode by remember { mutableStateOf(PlayerInteractionMode.MOVE_PAWN) }
    var isWallHorizontal by remember { mutableStateOf(true) }
    var pendingWall by remember { mutableStateOf<Wall?>(null) }
    var wallValidationMsg by remember { mutableStateOf<String?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var hasOpenedSettingsInTutorial by remember { mutableStateOf(false) }
    var showConfirmExitTutorialDialog by remember { mutableStateOf(false) }

    // Step-specific GameState
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

    // Online Sandbox preview interactive state
    var onlineSimMicMuted by remember { mutableStateOf(false) }
    var onlineSimSpeakerMuted by remember { mutableStateOf(false) }
    var floatingReactionEmoji by remember { mutableStateOf<String?>(null) }
    var roomCodeCopiedNotice by remember { mutableStateOf(false) }

    // Setup conditions whenever currentStep changes
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
            TutorialStep.ONLINE_MULTIPLAYER -> {
                interactionMode = PlayerInteractionMode.MOVE_PAWN
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
            TutorialStep.PAWN_MOVE -> listOf(Pair(4, 6))
            TutorialStep.JUMP_OPPONENT -> listOf(Pair(4, 2))
            else -> gameViewModel.getValidMoves(tutorialGameState, 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (selectedLanguage == TutorialLanguage.HINDI) "गेम गाइड / ट्यूटोरियल" else "How to Play / Tutorial",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (appSettings.darkTheme) Color.White else WallColor
                        )
                        Text(
                            text = "${if (selectedLanguage == TutorialLanguage.HINDI) "कदम" else "Step"} ${currentStep.stepNumber}/7: ${TutorialContent.getStepTitle(currentStep, selectedLanguage)}",
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
                    // Language Toggle: English / Hindi
                    Surface(
                        color = if (appSettings.darkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (appSettings.darkTheme) Color(0xFF334155) else Color(0xFFCBD5E1)
                        ),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            // English Chip
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (selectedLanguage == TutorialLanguage.ENGLISH) Player1Color else Color.Transparent
                                    )
                                    .clickable {
                                        selectedLanguage = TutorialLanguage.ENGLISH
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🇬🇧 EN",
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedLanguage == TutorialLanguage.ENGLISH) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedLanguage == TutorialLanguage.ENGLISH) Color.White else if (appSettings.darkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
                                )
                            }

                            // Hindi Chip
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (selectedLanguage == TutorialLanguage.HINDI) Player1Color else Color.Transparent
                                    )
                                    .clickable {
                                        selectedLanguage = TutorialLanguage.HINDI
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🇮🇳 हिंदी",
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedLanguage == TutorialLanguage.HINDI) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedLanguage == TutorialLanguage.HINDI) Color.White else if (appSettings.darkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
                                )
                            }
                        }
                    }

                    TextButton(onClick = onFinishTutorial) {
                        Text(
                            text = if (selectedLanguage == TutorialLanguage.HINDI) "छोड़ें" else "Skip",
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
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
            // Step Progress Indicator Bar (7 Steps)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TutorialStep.values().take(6).forEach { step ->
                    val isPast = step.stepNumber < currentStep.stepNumber
                    val isCurrent = step == currentStep
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
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

            // Top Instructions Card
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
                                TutorialStep.ONLINE_MULTIPLAYER -> Icons.Default.Public
                                TutorialStep.SETTINGS_GUIDE -> Icons.Default.Settings
                                TutorialStep.COMPLETED -> Icons.Default.EmojiEvents
                            },
                            contentDescription = null,
                            tint = if (stepSuccessMsg != null) AccentGreen else Player1Color,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = TutorialContent.getStepTitle(currentStep, selectedLanguage),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (appSettings.darkTheme) Color.White else Color(0xFF0F172A)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = TutorialContent.getStepDescription(currentStep, selectedLanguage),
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

            // Central Area: Board for Steps 1-4, Online Simulator for Step 5, Settings/Finish for 6-7
            if (currentStep == TutorialStep.ONLINE_MULTIPLAYER) {
                // Interactive Online Multiplayer & Voice Chat Sandbox
                OnlineTutorialSection(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    selectedLanguage = selectedLanguage,
                    isDarkTheme = appSettings.darkTheme,
                    micMuted = onlineSimMicMuted,
                    speakerMuted = onlineSimSpeakerMuted,
                    floatingEmoji = floatingReactionEmoji,
                    roomCopiedNotice = roomCodeCopiedNotice,
                    onToggleMic = {
                        onlineSimMicMuted = !onlineSimMicMuted
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onToggleSpeaker = {
                        onlineSimSpeakerMuted = !onlineSimSpeakerMuted
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onSendEmoji = { emoji ->
                        floatingReactionEmoji = emoji
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        coroutineScope.launch {
                            delay(1400)
                            floatingReactionEmoji = null
                        }
                    },
                    onCopyRoom = {
                        roomCodeCopiedNotice = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        coroutineScope.launch {
                            delay(1600)
                            roomCodeCopiedNotice = false
                        }
                    }
                )
            } else if (currentStep == TutorialStep.SETTINGS_GUIDE || currentStep == TutorialStep.COMPLETED) {
                // Completion / Settings Summary Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (appSettings.darkTheme) Color(0xFF1E293B) else Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (currentStep == TutorialStep.SETTINGS_GUIDE) "⚙️" else "🏆",
                                fontSize = 46.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (currentStep == TutorialStep.SETTINGS_GUIDE) {
                                    if (selectedLanguage == TutorialLanguage.HINDI) "कंट्रोल्स और सेटिंग्स" else "Controls & Settings"
                                } else {
                                    if (selectedLanguage == TutorialLanguage.HINDI) "जीत के लिए तैयार हैं!" else "Ready to Conquer!"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = if (appSettings.darkTheme) Color.White else Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (currentStep == TutorialStep.SETTINGS_GUIDE) {
                                    if (selectedLanguage == TutorialLanguage.HINDI)
                                        "आप गेम में दो तरह से दीवार लगा सकते हैं:\n• बटन टैप मोड (सरल और सुरक्षित)\n• ड्रैग-एंड-ड्रॉप मोड (तेज और मजेदार)\n\nसाथ ही साउंड, वाइब्रेशन और डार्क मोड भी बदल सकते हैं!"
                                    else
                                        "Customize how you play:\n• Button Mode (Safe, precise positioning)\n• Drag & Drop Mode (Fast, tactile placing)\n\nPlus haptic feedback, subtle audio cues, and Dark Theme!"
                                } else {
                                    if (selectedLanguage == TutorialLanguage.HINDI)
                                        "आपने गोटी चलना, दीवार लगाना, छलांग लगाना और नया ऑनलाइन लाइव वॉइस मुकाबला सीख लिया है!"
                                    else
                                        "You've mastered all core tactics: pawn strides, wall detours, jumping opponents, and online real-time voice duels!"
                                },
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                color = if (appSettings.darkTheme) Color(0xFF94A3B8) else Color(0xFF475569),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            } else {
                // Interactive Board for Steps 1-4
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
                                gameViewModel.soundManager.onPawnStep(appSettings.soundEnabled)
                                tutorialGameState = tutorialGameState.copy(
                                    player1 = tutorialGameState.player1.copy(x = 4, y = 6)
                                )
                                stepSuccessMsg = if (selectedLanguage == TutorialLanguage.HINDI)
                                    "शानदार चाल! अब प्रतिद्वंद्वी की बारी है..."
                                else
                                    "Great move! Opponent takes a step now..."

                                coroutineScope.launch {
                                    delay(600)
                                    tutorialGameState = tutorialGameState.copy(
                                        player2 = tutorialGameState.player2.copy(x = 4, y = 3)
                                    )
                                    gameViewModel.soundManager.onPawnStep(appSettings.soundEnabled)
                                    delay(400)
                                    stepSuccessMsg = if (selectedLanguage == TutorialLanguage.HINDI)
                                        "बहुत बढ़िया! आपने गोटी चलाना सीख लिया। अब दीवार लगाना सीखें।"
                                    else
                                        "Nice! Pawn movement complete. Now let's learn wall placement."
                                }
                            } else if (currentStep == TutorialStep.JUMP_OPPONENT && x == 4 && y == 2) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                gameViewModel.soundManager.onPawnStep(appSettings.soundEnabled)
                                tutorialGameState = tutorialGameState.copy(
                                    player1 = tutorialGameState.player1.copy(x = 4, y = 2)
                                )
                                stepSuccessMsg = if (selectedLanguage == TutorialLanguage.HINDI)
                                    "ज़बरदस्त छलांग! आपने विरोधी को पार कर लिया।"
                                else
                                    "Awesome leap! You jumped over the opponent."
                            }
                        },
                        onWallIntersectionClicked = { wx, wy ->
                            if (currentStep == TutorialStep.PLACE_WALL) {
                                pendingWall = Wall(wx, wy, isWallHorizontal)
                                val validation = gameViewModel.checkWallPlacement(tutorialGameState, pendingWall!!)
                                wallValidationMsg = validation
                            } else if (currentStep == TutorialStep.BFS_RULE) {
                                pendingWall = Wall(wx, wy, isWallHorizontal)
                                val validation = gameViewModel.checkWallPlacement(tutorialGameState, pendingWall!!)
                                wallValidationMsg = validation ?: if (selectedLanguage == TutorialLanguage.HINDI)
                                    "गेम हमेशा यह जांचता है कि रास्ता पूरी तरह बंद न हो!"
                                else
                                    "Notice how the system checks path connectivity!"
                            }
                        },
                        onDragWallPreview = { wx, wy, isHoriz ->
                            pendingWall = Wall(wx, wy, isHoriz)
                            val validation = gameViewModel.checkWallPlacement(tutorialGameState, pendingWall!!)
                            wallValidationMsg = validation
                        },
                        onConfirmDragWall = {
                            if (pendingWall != null && wallValidationMsg == null) {
                                gameViewModel.soundManager.onWallPlaced(appSettings.soundEnabled)
                                tutorialGameState = tutorialGameState.copy(
                                    walls = tutorialGameState.walls + pendingWall!!,
                                    player1 = tutorialGameState.player1.copy(walls = tutorialGameState.player1.walls - 1)
                                )
                                pendingWall = null
                                stepSuccessMsg = if (selectedLanguage == TutorialLanguage.HINDI)
                                    "दीवार लग गई! विरोधी का सीधा रास्ता बंद हो गया।"
                                else
                                    "Wall locked in place! Opponent's path is blocked."
                            }
                        }
                    )
                }
            }

            // Bottom Actions and Navigation Area
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
                                Text(
                                    text = if (selectedLanguage == TutorialLanguage.HINDI) "आगे: दीवार लगाना सीखें →" else "Next: Learn How to Place Walls →",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            Text(
                                text = TutorialContent.getStepPrompt(TutorialStep.PAWN_MOVE, selectedLanguage),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Player1Color
                            )
                        }
                    }

                    TutorialStep.PLACE_WALL -> {
                        if (stepSuccessMsg == null) {
                            // Wall Orientation Controls
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
                                    Text(
                                        text = if (selectedLanguage == TutorialLanguage.HINDI) "आड़ी (Horizontal)" else "Horizontal",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
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
                                    Text(
                                        text = if (selectedLanguage == TutorialLanguage.HINDI) "खड़ी (Vertical)" else "Vertical",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (pendingWall == null) {
                                Button(
                                    onClick = {
                                        pendingWall = Wall(3, 3, isWallHorizontal)
                                        wallValidationMsg = gameViewModel.checkWallPlacement(tutorialGameState, pendingWall!!)
                                    },
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.AdsClick, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (selectedLanguage == TutorialLanguage.HINDI) "दीवार की सही जगह दिखाएं" else "Show Me Where to Place Wall",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (wallValidationMsg == null) {
                                            gameViewModel.soundManager.onWallPlaced(appSettings.soundEnabled)
                                            tutorialGameState = tutorialGameState.copy(
                                                walls = tutorialGameState.walls + pendingWall!!,
                                                player1 = tutorialGameState.player1.copy(walls = tutorialGameState.player1.walls - 1)
                                            )
                                            pendingWall = null
                                            stepSuccessMsg = if (selectedLanguage == TutorialLanguage.HINDI)
                                                "दीवार लग गई! विरोधी को अब चक्कर लगाकर आना होगा।"
                                            else
                                                "Wall placed! Opponent is now forced to take a detour."
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
                                    Text(
                                        text = if (selectedLanguage == TutorialLanguage.HINDI) "दीवार पक्की करें (Confirm)" else "Confirm Wall",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = { currentStep = TutorialStep.BFS_RULE },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (selectedLanguage == TutorialLanguage.HINDI) "आगे: रास्ता खुला रखने का नियम →" else "Next: The Golden Path Rule →",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    TutorialStep.BFS_RULE -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (selectedLanguage == TutorialLanguage.HINDI)
                                    "याद रखें: दोनों खिलाड़ियों के लिए कम से कम 1 रास्ता हमेशा खुला रहना चाहिए!"
                                else
                                    "Notice: At least 1 path must remain open for both players at all times!",
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
                                Text(
                                    text = if (selectedLanguage == TutorialLanguage.HINDI) "आगे: विरोधी पर छलांग लगाना →" else "Next: Learn How to Jump Opponent →",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    TutorialStep.JUMP_OPPONENT -> {
                        if (stepSuccessMsg != null) {
                            Button(
                                onClick = { currentStep = TutorialStep.ONLINE_MULTIPLAYER },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (selectedLanguage == TutorialLanguage.HINDI) "आगे: नया ऑनलाइन व वॉइस चैट मोड 🌐 →" else "Next: New Online & Live Voice Chat 🌐 →",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            Text(
                                text = TutorialContent.getStepPrompt(TutorialStep.JUMP_OPPONENT, selectedLanguage),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Player1Color,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    TutorialStep.ONLINE_MULTIPLAYER -> {
                        Button(
                            onClick = { currentStep = TutorialStep.SETTINGS_GUIDE },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Player1Color),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (selectedLanguage == TutorialLanguage.HINDI) "आगे: सेटिंग्स और थीम्स ⚙️ →" else "Next: Settings & Themes ⚙️ →",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
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
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (selectedLanguage == TutorialLanguage.HINDI) "⚙️ सेटिंग्स खोलें (कंट्रोल्स और थीम्स)" else "⚙️ Open Settings (Test Controls & Themes)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (hasOpenedSettingsInTutorial) {
                                Button(
                                    onClick = { currentStep = TutorialStep.COMPLETED },
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = if (selectedLanguage == TutorialLanguage.HINDI) "ट्यूटोरियल पूरा करें 🎉" else "Finish Tutorial & Start Playing! 🎉",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                Text(
                                    text = if (selectedLanguage == TutorialLanguage.HINDI)
                                        "ऊपर 'सेटिंग्स खोलें' दबाकर देखें कि बटन या ड्रैग मोड कैसे बदला जाता है।"
                                    else
                                        "Tap 'Open Settings' above to see how to switch between Tap & Drag modes and Dark Theme.",
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
                            // Online Duel Action
                            Button(
                                onClick = onStartOnline,
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (selectedLanguage == TutorialLanguage.HINDI) "ऑनलाइन मुकाबला खेलें 🌐" else "Play Online Now 🌐",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onStartVsAi,
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (selectedLanguage == TutorialLanguage.HINDI) "AI से खेलें" else "Play vs AI",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                Button(
                                    onClick = onStartPassAndPlay,
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (selectedLanguage == TutorialLanguage.HINDI) "2 खिलाड़ी" else "Pass & Play",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            TextButton(onClick = onFinishTutorial) {
                                Text(
                                    text = if (selectedLanguage == TutorialLanguage.HINDI) "होम मेन्यू पर जाएँ" else "Back to Home Menu",
                                    color = Player1Color,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Exit Confirmation Dialog
        if (showConfirmExitTutorialDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmExitTutorialDialog = false },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color(0xFFFEF2F2), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = if (selectedLanguage == TutorialLanguage.HINDI) "ट्यूटोरियल से बाहर निकलें?" else "Exit Tutorial?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Text(
                        text = if (selectedLanguage == TutorialLanguage.HINDI)
                            "आप अभी कदम ${currentStep.stepNumber}/7 पर हैं। क्या आप वाकई प्रैक्टिस छोड़कर बाहर जाना चाहते हैं?"
                        else
                            "You are currently on Step ${currentStep.stepNumber} of 7. Are you sure you want to exit the practice guide?",
                        fontSize = 13.sp,
                        color = if (appSettings.darkTheme) Color(0xFFCBD5E1) else Color(0xFF475569),
                        textAlign = TextAlign.Center
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
                        Text(
                            text = if (selectedLanguage == TutorialLanguage.HINDI) "हाँ, बाहर निकलें" else "Exit Tutorial",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showConfirmExitTutorialDialog = false },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (selectedLanguage == TutorialLanguage.HINDI) "सीखना जारी रखें" else "Continue Learning",
                            color = if (appSettings.darkTheme) Color.White else Color(0xFF334155)
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Settings Dialog
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

/**
 * Interactive Online Multiplayer & Voice Chat Simulation Section
 */
@Composable
private fun OnlineTutorialSection(
    modifier: Modifier = Modifier,
    selectedLanguage: TutorialLanguage,
    isDarkTheme: Boolean,
    micMuted: Boolean,
    speakerMuted: Boolean,
    floatingEmoji: String?,
    roomCopiedNotice: Boolean,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSendEmoji: (String) -> Unit,
    onCopyRoom: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Interactive Online Cockpit Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (isDarkTheme) Color(0xFF3B82F6) else Color(0xFF60A5FA)
            ),
            shadowElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Opponent Status Bar & Turn Timer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFF22C55E), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedLanguage == TutorialLanguage.HINDI) "विरोधी: अमित (24ms)" else "Opponent: Rohit (24ms)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isDarkTheme) Color.White else Color(0xFF0F172A)
                        )
                    }

                    // Turn Timer Badge
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⏱️", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "28s",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFFB45309)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Interactive Voice Chat & Audio Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Mic Button
                    Button(
                        onClick = onToggleMic,
                        modifier = Modifier.weight(1f).height(42.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!micMuted) Color(0xFF10B981) else Color(0xFFEF4444)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (!micMuted) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (!micMuted) {
                                if (selectedLanguage == TutorialLanguage.HINDI) "माइक: चालू 🎙" else "Mic: Live 🎙"
                            } else {
                                if (selectedLanguage == TutorialLanguage.HINDI) "माइक: म्यूट 🔇" else "Mic: Muted 🔇"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Speaker Button
                    OutlinedButton(
                        onClick = onToggleSpeaker,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (!speakerMuted) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isDarkTheme) Color.White else Color(0xFF334155)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (!speakerMuted) {
                                if (selectedLanguage == TutorialLanguage.HINDI) "स्पीकर: ऑन" else "Audio: On"
                            } else {
                                if (selectedLanguage == TutorialLanguage.HINDI) "स्पीकर: बंद" else "Audio: Off"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDarkTheme) Color.White else Color(0xFF334155)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Interactive Live Reactions Bar with Floating Animation
                Text(
                    text = if (selectedLanguage == TutorialLanguage.HINDI)
                        "👇 लाइव इमोजी दबाकर टेस्ट करें (Test Live Emojis):"
                    else
                        "👇 Tap any emoji to test in-game reaction:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val emojis = listOf("😂", "🔥", "👏", "🧠", "🤯")
                        emojis.forEach { emoji ->
                            Surface(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .clickable { onSendEmoji(emoji) },
                                color = if (isDarkTheme) Color(0xFF334155) else Color(0xFFF1F5F9),
                                shape = CircleShape,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isDarkTheme) Color(0xFF475569) else Color(0xFFCBD5E1)
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = emoji, fontSize = 20.sp)
                                }
                            }
                        }
                    }

                    // Floating Reaction Animation
                    if (floatingEmoji != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-20).dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = floatingEmoji,
                                fontSize = 42.sp,
                                modifier = Modifier.scale(1.2f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Room Code Preview
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedLanguage == TutorialLanguage.HINDI) "रूम कोड: B7K2" else "Room Code: B7K2",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Player1Color
                        )

                        TextButton(
                            onClick = onCopyRoom,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (roomCopiedNotice) {
                                    if (selectedLanguage == TutorialLanguage.HINDI) "कॉपी हो गया! ✓" else "Copied! ✓"
                                } else {
                                    if (selectedLanguage == TutorialLanguage.HINDI) "कोड कॉपी करें" else "Copy Code"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (roomCopiedNotice) AccentGreen else Color(0xFF0284C7)
                            )
                        }
                    }
                }
            }
        }

        // Online Feature Highlight Points (Bilingual)
        TutorialContent.getOnlineHighlights(selectedLanguage).forEach { highlight ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF8FAFC),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)
                )
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = when (highlight.iconName) {
                            "Bolt" -> "⚡"
                            "Key" -> "🔑"
                            "Mic" -> "🎙️"
                            "Mood" -> "💬"
                            else -> "⏱️"
                        },
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = highlight.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isDarkTheme) Color.White else Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = highlight.desc,
                            fontSize = 11.sp,
                            color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF475569),
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}
