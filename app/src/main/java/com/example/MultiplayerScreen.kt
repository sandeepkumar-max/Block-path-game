package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiplayerScreen(
    gameViewModel: GameViewModel,
    onBack: () -> Unit,
    onStartGame: (opponentName: String, opponentAvatar: String, isRealPeer: Boolean, ping: Int, isHost: Boolean, timerEnabled: Boolean) -> Unit
) {
    val context = LocalContext.current
    val matchmakingState by gameViewModel.peerJsWebRtcManager.matchmakingState.collectAsState()
    val userProfile by gameViewModel.userProfile.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Quick Match, 1: Play with Friend
    var customRoomCode by remember { mutableStateOf("") }
    var myHostedCode by remember { mutableStateOf((1000 + Random.nextInt(9000)).toString()) }
    var timerEnabled by remember { mutableStateOf(false) } // Default OFF (relaxed) as requested
    var hasNavigatedToGame by remember { mutableStateOf(false) }

    BackHandler {
        gameViewModel.peerJsWebRtcManager.cancelMatchmaking()
        onBack()
    }

    LaunchedEffect(Unit) {
        hasNavigatedToGame = false
        // Ensure that entering MultiplayerScreen always resets any previous leftover matchmaking session
        if (matchmakingState !is MatchmakingState.Searching &&
            matchmakingState !is MatchmakingState.HostingRoom &&
            matchmakingState !is MatchmakingState.JoiningRoom) {
            gameViewModel.peerJsWebRtcManager.cancelMatchmaking()
        }
    }

    // Auto navigate when matched
    LaunchedEffect(matchmakingState) {
        val state = matchmakingState
        if (state is MatchmakingState.Matched && !hasNavigatedToGame) {
            hasNavigatedToGame = true
            onStartGame(
                state.opponentName,
                state.opponentAvatar,
                state.isRealPeer,
                state.ping,
                state.isHost,
                state.timerEnabled
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!hasNavigatedToGame && matchmakingState !is MatchmakingState.Matched) {
                gameViewModel.peerJsWebRtcManager.cancelMatchmaking()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Multiplayer Arena",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = Color(0xFF1E293B)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            gameViewModel.peerJsWebRtcManager.cancelMatchmaking()
                            onBack()
                        },
                        modifier = Modifier.testTag("multiplayer_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF1E293B)
                        )
                    }
                },
                actions = {
                    // Profile Chip (View-only identity display)
                    Surface(
                        color = Color(0xFFEEF2FF),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7D2FE)),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("multiplayer_profile_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(userProfile.avatar, fontSize = 16.sp)
                            Text(
                                text = userProfile.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF4F46E5)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF8FAFC)
                )
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode Tabs: Quick Duel vs Private Room
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFFE2E8F0),
                contentColor = Color(0xFF4F46E5),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp)),
                indicator = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        gameViewModel.peerJsWebRtcManager.cancelMatchmaking()
                        selectedTab = 0
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selectedTab == 0) Color.White else Color.Transparent)
                        .padding(vertical = 12.dp)
                        .testTag("tab_quick_match"),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Quick Duel", fontWeight = FontWeight.Bold)
                        }
                    }
                )

                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        gameViewModel.peerJsWebRtcManager.cancelMatchmaking()
                        selectedTab = 1
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selectedTab == 1) Color.White else Color.Transparent)
                        .padding(vertical = 12.dp)
                        .testTag("tab_private_room"),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Play with Friend", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Tab Content
            if (selectedTab == 0) {
                QuickMatchTab(
                    userProfile = userProfile,
                    matchmakingState = matchmakingState,
                    timerEnabled = timerEnabled,
                    onToggleTimer = { timerEnabled = it },
                    onStartMatchmaking = {
                        gameViewModel.startOnlineMatchmaking(timerEnabled = timerEnabled)
                    },
                    onCancelMatchmaking = {
                        gameViewModel.peerJsWebRtcManager.cancelMatchmaking()
                    }
                )
            } else {
                PlayWithFriendTab(
                    userProfile = userProfile,
                    matchmakingState = matchmakingState,
                    hostedRoomCode = myHostedCode,
                    timerEnabled = timerEnabled,
                    onToggleTimer = { timerEnabled = it },
                    onGenerateNewCode = {
                        myHostedCode = (1000 + Random.nextInt(9000)).toString()
                    },
                    onHostRoom = { code ->
                        gameViewModel.createCustomRoom(code, timerEnabled = timerEnabled)
                    },
                    onJoinRoom = { code ->
                        gameViewModel.joinCustomRoom(code)
                    },
                    onCancel = {
                        gameViewModel.peerJsWebRtcManager.cancelMatchmaking()
                        myHostedCode = (1000 + Random.nextInt(9000)).toString()
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickMatchTab(
    userProfile: UserProfile,
    matchmakingState: MatchmakingState,
    timerEnabled: Boolean,
    onToggleTimer: (Boolean) -> Unit,
    onStartMatchmaking: () -> Unit,
    onCancelMatchmaking: () -> Unit
) {
    val isSearching = matchmakingState is MatchmakingState.Searching

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Instant 1v1 Quoridor Duel",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Get matched instantly with an active opponent worldwide.",
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Player VS Opponent Arena Preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User Card (View-only display)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    listOf(Color(0xFF6366F1), Color(0xFF4338CA))
                                ),
                                shape = CircleShape
                            )
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(userProfile.avatar, fontSize = 32.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = userProfile.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF1E293B)
                    )
                    Text(
                        text = "${userProfile.winRate}% Win Rate",
                        fontSize = 11.sp,
                        color = Color(0xFF16A34A),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // VS Badge with Pulse
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFFF1F5F9), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "VS",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = Color(0xFF64748B)
                    )
                }

                // Opponent Card
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .background(
                                if (isSearching) Color(0xFFE0E7FF) else Color(0xFFF1F5F9),
                                CircleShape
                            )
                            .border(
                                width = 2.dp,
                                color = if (isSearching) Color(0xFF818CF8) else Color(0xFFE2E8F0),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = Color(0xFF4F46E5),
                                strokeWidth = 3.dp
                            )
                        } else {
                            Text("?", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isSearching) "Searching..." else "Opponent",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isSearching) Color(0xFF4F46E5) else Color(0xFF94A3B8)
                    )
                    Text(
                        text = if (isSearching) "Searching..." else "Online Match",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Configurable Turn Timer Setting
            if (!isSearching) {
                OnlineTimerSettingCard(
                    timerEnabled = timerEnabled,
                    onToggleTimer = onToggleTimer
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Status Text
            if (isSearching) {
                val searching = matchmakingState as MatchmakingState.Searching
                Surface(
                    color = Color(0xFFF0FDF4),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF22C55E), CircleShape)
                        )
                        Text(
                            text = searching.statusText,
                            fontSize = 12.sp,
                            color = Color(0xFF15803D),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Error Text
            if (matchmakingState is MatchmakingState.Error) {
                val err = matchmakingState as MatchmakingState.Error
                Surface(
                    color = Color(0xFFFEF2F2),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA))
                ) {
                    Text(
                        text = err.message,
                        fontSize = 12.sp,
                        color = Color(0xFFDC2626),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Action Button
            if (!isSearching) {
                Button(
                    onClick = onStartMatchmaking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_quick_match_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4F46E5),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Find Match Now",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onCancelMatchmaking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("cancel_quick_match_btn"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFEF4444)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFCA5A5)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel Search", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PlayWithFriendTab(
    userProfile: UserProfile,
    matchmakingState: MatchmakingState,
    hostedRoomCode: String,
    timerEnabled: Boolean,
    onToggleTimer: (Boolean) -> Unit,
    onGenerateNewCode: () -> Unit,
    onHostRoom: (code: String) -> Unit,
    onJoinRoom: (code: String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    var joinInputCode by remember { mutableStateOf("") }
    val isHosting = matchmakingState is MatchmakingState.HostingRoom
    val isJoining = matchmakingState is MatchmakingState.JoiningRoom

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Player Profile Identity Card (View-only identity display)
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFEEF2FF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(userProfile.avatar, fontSize = 22.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Playing as",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = userProfile.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                    }
                }
                Surface(
                    color = Color(0xFFF0FDF4),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0))
                ) {
                    Text(
                        text = "Level ${userProfile.level}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF16A34A),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Section 1: Host a Game
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Host a Private Match",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = "Share this 4-digit code with your friend",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    if (!isHosting) {
                        IconButton(onClick = onGenerateNewCode) {
                            Icon(Icons.Default.Refresh, contentDescription = "New Code", tint = Color(0xFF4F46E5))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Big Code Display Card
                Surface(
                    color = Color(0xFFEEF2FF),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFC7D2FE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "ROOM CODE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6366F1),
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = hostedRoomCode,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1E1B4B),
                                letterSpacing = 4.sp
                            )
                        }

                        // Copy Button
                        IconButton(
                            onClick = {
                                val clip = ClipData.newPlainText("BlockPath Room Code", hostedRoomCode)
                                clipboardManager.setPrimaryClip(clip)
                                Toast.makeText(context, "Room Code $hostedRoomCode copied!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("copy_room_code_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Code",
                                tint = Color(0xFF4F46E5)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Host Configurable Turn Timer Setting
                if (!isHosting) {
                    OnlineTimerSettingCard(
                        timerEnabled = timerEnabled,
                        onToggleTimer = onToggleTimer
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                if (!isHosting) {
                    Button(
                        onClick = { onHostRoom(hostedRoomCode) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("host_room_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Podcasts, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Room & Wait for Friend", fontWeight = FontWeight.Bold)
                    }
                } else {
                    val hostState = matchmakingState as MatchmakingState.HostingRoom
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color(0xFF4F46E5),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = hostState.statusText,
                                fontSize = 12.sp,
                                color = Color(0xFF4F46E5),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onCancel,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text("Cancel Hosting", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section 2: Join a Game
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Join a Friend's Match",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                Text(
                    text = "Enter the 4-digit code provided by your friend",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "ℹ️ Turn timer settings are automatically synchronized from the room host.",
                        fontSize = 11.sp,
                        color = Color(0xFF475569),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = joinInputCode,
                        onValueChange = { if (it.length <= 4) joinInputCode = it.filter { ch -> ch.isDigit() } },
                        placeholder = { Text("e.g. 7429") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done,
                            capitalization = KeyboardCapitalization.None
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("join_room_code_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = {
                            if (joinInputCode.length == 4) {
                                onJoinRoom(joinInputCode)
                            } else {
                                Toast.makeText(context, "Please enter a 4-digit room code", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = joinInputCode.length == 4 && !isJoining,
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("join_room_submit_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isJoining) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Join Game", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (isJoining) {
                    val joinState = matchmakingState as MatchmakingState.JoiningRoom
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = joinState.statusText,
                        fontSize = 12.sp,
                        color = Color(0xFF059669),
                        fontWeight = FontWeight.Medium
                    )
                }

                if (matchmakingState is MatchmakingState.Error) {
                    val err = matchmakingState as MatchmakingState.Error
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = err.message,
                        fontSize = 12.sp,
                        color = Color(0xFFEF4444),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun OnlineTimerSettingCard(
    timerEnabled: Boolean,
    onToggleTimer: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (timerEnabled) Color(0xFF818CF8) else Color(0xFFE2E8F0)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (timerEnabled) Color(0xFFEEF2FF) else Color(0xFFF1F5F9),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (timerEnabled) Icons.Default.Timer else Icons.Default.TimerOff,
                        contentDescription = null,
                        tint = if (timerEnabled) Color(0xFF4F46E5) else Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Turn Timer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF0F172A)
                        )
                        Surface(
                            color = if (timerEnabled) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (timerEnabled) "10s Speed Duel" else "Off (Relaxed)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (timerEnabled) Color(0xFF15803D) else Color(0xFF64748B),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (timerEnabled) "10s per turn limit" else "Unlimited time per turn (No rush)",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Switch(
                checked = timerEnabled,
                onCheckedChange = onToggleTimer,
                modifier = Modifier.testTag("online_timer_toggle"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF4F46E5),
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFCBD5E1)
                )
            )
        }
    }
}
