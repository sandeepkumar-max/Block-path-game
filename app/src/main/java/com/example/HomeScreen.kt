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
import com.example.ui.BlockPathLogo
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    onStartPassAndPlay: () -> Unit,
    onStartVsAi: (AIDifficulty) -> Unit,
    onOpenAuth: () -> Unit
) {
    var showRulesDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AppBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            BlockPathLogo(
                size = 120.dp,
                elevation = 8.dp,
                modifier = Modifier.testTag("app_game_logo")
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "BLOCKPATH",
                fontSize = 36.sp,
                fontWeight = FontWeight.Light,
                color = WallColor,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            // Button 1: Local Multiplayer (Pass & Play)
            Button(
                onClick = onStartPassAndPlay,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
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
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Button 2: vs Computer (AI Mode)
            Button(
                onClick = { onStartVsAi(AIDifficulty.MEDIUM) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
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
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Button 3: How to Play (Rules)
            Button(
                onClick = { showRulesDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("button_how_to_play"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF475569), // Rich Slate
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 2.dp
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "How to Play",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = { showPrivacyDialog = true }) {
                Text(
                    text = "Privacy Policy",
                    color = Color(0xFF64748B),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
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
