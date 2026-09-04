package com.example

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppSettingsDialog
import com.example.ui.BlockPathLogo
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    gameViewModel: GameViewModel,
    onStartPassAndPlay: () -> Unit,
    onStartVsAi: (AIDifficulty) -> Unit,
    onStartTutorial: () -> Unit,
    onOpenAuth: () -> Unit
) {
    val appSettings by gameViewModel.appSettings.collectAsState()
    var showRulesDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // First time user check
    var showFirstTimeDialog by remember {
        mutableStateOf(gameViewModel.isFirstTimeUser())
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (appSettings.darkTheme) Color(0xFF0F172A) else AppBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Top Bar Row with Settings button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier.testTag("home_settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = if (appSettings.darkTheme) Color.White else WallColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            BlockPathLogo(
                size = 110.dp,
                elevation = 8.dp,
                modifier = Modifier.testTag("app_game_logo")
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "BLOCKPATH",
                fontSize = 34.sp,
                fontWeight = FontWeight.Light,
                color = if (appSettings.darkTheme) Color.White else WallColor,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Quoridor-style Strategy Duel",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Button 1: Local Multiplayer (Pass & Play)
            Button(
                onClick = onStartPassAndPlay,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("mode_pass_and_play"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E293B), // Deep navy slate
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 2.dp
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Local Multiplayer",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Button 2: vs Computer (AI Mode)
            Button(
                onClick = { onStartVsAi(AIDifficulty.MEDIUM) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("mode_vs_computer"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0284C7), // Vibrant sky blue
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 2.dp
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "vs Computer",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Row with Tutorial & Rules buttons (Compact side-by-side)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Interactive Tutorial
                Button(
                    onClick = onStartTutorial,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("button_interactive_tutorial"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF059669), // Rich Emerald Green
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 3.dp,
                        pressedElevation = 1.dp
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tutorial",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                // Rules & Guide
                Button(
                    onClick = { showRulesDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("button_how_to_play"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF475569), // Rich Slate
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 3.dp,
                        pressedElevation = 1.dp
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Rules & Guide",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(onClick = { showPrivacyDialog = true }) {
                Text(
                    text = "Privacy Policy",
                    color = Color(0xFF64748B),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // First-Time User Welcome Dialog
        if (showFirstTimeDialog) {
            AlertDialog(
                onDismissRequest = {
                    showFirstTimeDialog = false
                    gameViewModel.setFirstLaunchCompleted()
                },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color(0xFFD1FAE5), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = "New to BlockPath?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Welcome! Learn how to move your pawn, trap opponents with walls, and customize your game settings in a quick 1-minute interactive practice game!",
                            fontSize = 14.sp,
                            color = if (appSettings.darkTheme) Color(0xFFCBD5E1) else Color(0xFF334155),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = if (appSettings.darkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "💡 Hands-on preview: tap tiles, place walls & test controls live!",
                                fontSize = 12.sp,
                                color = Color(0xFF059669),
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showFirstTimeDialog = false
                            gameViewModel.setFirstLaunchCompleted()
                            onStartTutorial()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Start Interactive Tutorial (1 min)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showFirstTimeDialog = false
                            gameViewModel.setFirstLaunchCompleted()
                        }
                    ) {
                        Text("I Already Know Rules", color = Color.Gray)
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }

        if (showSettingsDialog) {
            AppSettingsDialog(
                appSettings = appSettings,
                onSettingsChanged = { gameViewModel.updateSettings(it) },
                onDismiss = { showSettingsDialog = false },
                onReplayTutorial = {
                    showSettingsDialog = false
                    onStartTutorial()
                }
            )
        }

        if (showRulesDialog) {
            RulesDialog(onDismiss = { showRulesDialog = false })
        }
        if (showPrivacyDialog) {
            PrivacyPolicyDialog(onDismiss = { showPrivacyDialog = false })
        }
    }
}


@Composable
fun RulesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BlockPathLogo(size = 32.dp, elevation = 1.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Rules of BlockPath", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RuleItem(
                    number = "1",
                    title = "The Objective",
                    description = "Player 1 (Blue) starts at the bottom and must reach row 0. Player 2 (Red) starts at the top and must reach row 8."
                )
                RuleItem(
                    number = "2",
                    title = "Your Turn",
                    description = "On each turn, you can either MOVE your pawn 1 step (or jump an adjacent opponent) OR PLACE 1 wall."
                )
                RuleItem(
                    number = "3",
                    title = "Walls (10 per player)",
                    description = "Walls are 2-cells wide and block passage between grid cells. You start with 10 walls."
                )
                RuleItem(
                    number = "4",
                    title = "Pathfinding Rule (BFS)",
                    description = "A wall can NEVER completely block a player's path to their goal. There must always remain at least one valid path open!"
                )
                RuleItem(
                    number = "5",
                    title = "Jumping",
                    description = "When facing an opponent head-to-head, you can jump over them. If blocked behind them by a wall, you can jump diagonally!"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Player1Color)
            ) {
                Text("Got It!")
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White
    )
}

@Composable
fun RuleItem(number: String, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Player1Color.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(number, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Player1Color)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
            Text(description, fontSize = 12.sp, color = Color(0xFF64748B), lineHeight = 16.sp)
        }
    }
}
@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy Policy", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Last updated: September 02, 2026", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Welcome to BlockPath! This Privacy Policy explains how we collect, use, and share information about you when you use our mobile application.",
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("1. Information We Collect", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("BlockPath is a local board game. We do not collect any personal data, usage data, or telemetry. Your game state and settings remain on your device.", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("2. Contact Us", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("If you have any questions about this Privacy Policy, please contact us at sandeepkumar28wu@gmail.com.", fontSize = 14.sp)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Player1Color)) {
                Text("Close")
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White
    )
}
