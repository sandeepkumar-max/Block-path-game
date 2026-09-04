package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.AppSettings
import com.example.ui.theme.Player1Color
import com.example.ui.theme.WallColor

@Composable
fun AppSettingsDialog(
    appSettings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onDismiss: () -> Unit,
    onReplayTutorial: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = Player1Color,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Game Settings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Classic Button Controls vs Drag
                Surface(
                    color = if (appSettings.darkTheme) Color(0xFF1E293B) else Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (appSettings.darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = Player1Color,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Wall Button Controls",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = appSettings.classicControls,
                                onCheckedChange = { onSettingsChanged(appSettings.copy(classicControls = it)) }
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (appSettings.classicControls)
                                "✅ Active: Horizontal/Vertical tap buttons (Easy on phones)"
                            else
                                "⚡ Active: Drag & Drop wall directly on the board",
                            fontSize = 12.sp,
                            color = if (appSettings.darkTheme) Color(0xFF94A3B8) else Color(0xFF64748B),
                            lineHeight = 16.sp
                        )
                    }
                }

                // 2. Sound & Haptic Vibration
                Surface(
                    color = if (appSettings.darkTheme) Color(0xFF1E293B) else Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (appSettings.darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    ) {
                        Icon(
                            imageVector = if (appSettings.soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = null,
                            tint = if (appSettings.soundEnabled) Player1Color else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sound & Vibration",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Haptic feedback on moves and wins",
                                fontSize = 12.sp,
                                color = if (appSettings.darkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
                            )
                        }
                        Switch(
                            checked = appSettings.soundEnabled,
                            onCheckedChange = { onSettingsChanged(appSettings.copy(soundEnabled = it)) }
                        )
                    }
                }

                // 3. Dark Theme
                Surface(
                    color = if (appSettings.darkTheme) Color(0xFF1E293B) else Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (appSettings.darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    ) {
                        Icon(
                            imageVector = if (appSettings.darkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = if (appSettings.darkTheme) Color(0xFFFACC15) else Color(0xFFD97706),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dark Mode",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (appSettings.darkTheme) "Dark board theme active" else "Classic cream wood theme",
                                fontSize = 12.sp,
                                color = if (appSettings.darkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
                            )
                        }
                        Switch(
                            checked = appSettings.darkTheme,
                            onCheckedChange = { onSettingsChanged(appSettings.copy(darkTheme = it)) }
                        )
                    }
                }

                // 4. Replay Tutorial option if provided
                if (onReplayTutorial != null) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onReplayTutorial()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Player1Color)
                    ) {
                        Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Replay Interactive Tutorial", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Player1Color),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(18.dp)
    )
}
