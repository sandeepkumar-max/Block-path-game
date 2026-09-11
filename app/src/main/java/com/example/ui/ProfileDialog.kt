package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.AVAILABLE_AVATARS
import com.example.UserProfile
import com.example.ui.theme.Player1Color

@Composable
fun ProfileDialog(
    userProfile: UserProfile,
    onSaveProfile: (name: String, avatar: String) -> Unit,
    onResetStats: () -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by remember { mutableStateOf(userProfile.name) }
    var selectedAvatar by remember { mutableStateOf(userProfile.avatar) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(22.dp))
                .testTag("profile_dialog"),
            color = Color.White,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Player Profile",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp).testTag("profile_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Avatar Hero & Nickname Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Avatar Hero
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFF818CF8), Color(0xFF4F46E5))
                                ),
                                shape = CircleShape
                            )
                            .border(2.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectedAvatar,
                            fontSize = 28.sp
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { if (it.length <= 16) nameInput = it },
                            label = { Text("Player Name", fontSize = 11.sp) },
                            singleLine = true,
                            placeholder = { Text("Enter name") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_name_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF4F46E5),
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            ),
                            trailingIcon = {
                                if (nameInput.isNotBlank()) {
                                    IconButton(
                                        onClick = { nameInput = "" },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Rank / Level Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFFEEF2FF),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MilitaryTech,
                                contentDescription = null,
                                tint = Color(0xFF4F46E5),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Level ${userProfile.level} • ${getPlayerRankTitle(userProfile.wins)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4F46E5)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Avatar Selection (10 avatars in 2 rows of 5)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Choose Your Avatar",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569)
                    )
                    Text(
                        text = "10 Avatars",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val avatarChunks = AVAILABLE_AVATARS.chunked(5)
                        avatarChunks.forEach { rowAvatars ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                rowAvatars.forEach { av ->
                                    val isSelected = av == selectedAvatar
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) Color(0xFF4F46E5) else Color.White
                                            )
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) Color(0xFF4F46E5) else Color(0xFFE2E8F0),
                                                shape = CircleShape
                                            )
                                            .clickable { selectedAvatar = av }
                                            .testTag("avatar_$av"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = av,
                                            fontSize = 20.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Battle Statistics Header
                Text(
                    text = "Battle Statistics",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF475569),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Compact Single-Row Stats
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 7.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactStatItem(
                            title = "Matches",
                            value = "${userProfile.gamesPlayed}",
                            sub = "${userProfile.wins}W-${userProfile.losses}L",
                            icon = Icons.Default.SportsEsports,
                            color = Color(0xFF2563EB)
                        )
                        Box(modifier = Modifier.width(1.dp).height(28.dp).background(Color(0xFFE2E8F0)))
                        CompactStatItem(
                            title = "Win Rate",
                            value = "${userProfile.winRate}%",
                            sub = "Overall",
                            icon = Icons.Default.EmojiEvents,
                            color = Color(0xFF16A34A)
                        )
                        Box(modifier = Modifier.width(1.dp).height(28.dp).background(Color(0xFFE2E8F0)))
                        CompactStatItem(
                            title = "Streak",
                            value = "${userProfile.winStreak}",
                            sub = "Best: ${userProfile.bestStreak}",
                            icon = Icons.Default.LocalFireDepartment,
                            color = Color(0xFFEA580C)
                        )
                        Box(modifier = Modifier.width(1.dp).height(28.dp).background(Color(0xFFE2E8F0)))
                        CompactStatItem(
                            title = "Walls",
                            value = "${userProfile.wallsPlaced}",
                            sub = "Placed",
                            icon = Icons.Default.Shield,
                            color = Color(0xFF9333EA)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons
                Button(
                    onClick = {
                        onSaveProfile(nameInput, selectedAvatar)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("save_profile_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4F46E5),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Changes", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(4.dp))

                TextButton(
                    onClick = { showResetConfirm = true },
                    modifier = Modifier
                        .testTag("reset_stats_btn")
                        .height(28.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Reset Career Stats",
                        color = Color(0xFFEF4444),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Stats?") },
            text = { Text("Are you sure you want to reset all your battle matches, wins, streaks, and wall records? Your name and avatar will remain.") },
            confirmButton = {
                Button(
                    onClick = {
                        onResetStats()
                        showResetConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CompactStatItem(
    title: String,
    value: String,
    sub: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = title,
                fontSize = 10.sp,
                color = Color(0xFF64748B),
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
        Text(
            text = sub,
            fontSize = 9.sp,
            color = Color(0xFF94A3B8)
        )
    }
}

private fun getPlayerRankTitle(wins: Int): String {
    return when {
        wins >= 50 -> "Grandmaster"
        wins >= 25 -> "Master Tactician"
        wins >= 10 -> "Path Expert"
        wins >= 5 -> "Strategist"
        wins >= 2 -> "Apprentice"
        else -> "Novice"
    }
}
