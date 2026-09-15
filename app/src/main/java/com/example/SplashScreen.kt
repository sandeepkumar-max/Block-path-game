package com.example

import android.app.Activity
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.BlockPathLogo
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.BoardBackground
import com.example.ui.theme.Player1Color

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val app = context.applicationContext as? BlockPathApplication

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_scale"
    )
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_offset"
    )

    LaunchedEffect(Unit) {
        /* ========================================================================= */
        /* [LOCATION 2: SPLASH SCREEN AD TRIGGER]                                    */
        /* Emulator crash se bachne ke liye splash ad ko comment kiya gaya hai.      */
        /* Jab aapko launch par App Open Ad chalana ho toh neeche UNCOMMENT kar dein:*/
        /* ========================================================================= */
        /*
        if (activity != null && app != null) {
            app.appOpenAdManager.showSplashAd(
                activity = activity,
                timeoutMillis = 3200L,
                onProceedToGame = {
                    onSplashComplete()
                }
            )
            return@LaunchedEffect
        }
        */
        /* ========================================================================= */

        // Direct, smooth entry to game matching home screen transition:
        kotlinx.coroutines.delay(1000L)
        onSplashComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFDFBF7), // Warm classic board cream
                        Color(0xFFF5EFE6)  // Gentle wooden parchment
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Animated Wooden Logo Badge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset(y = floatOffset.dp)
                    .scale(scale)
                    .padding(bottom = 20.dp)
            ) {
                // Ambient glow ring
                Box(
                    modifier = Modifier
                        .size(126.dp)
                        .background(
                            Color(0xFFD4C3A3).copy(alpha = 0.35f),
                            shape = RoundedCornerShape(32.dp)
                        )
                )

                // Main 3x3 Board Logo
                BlockPathLogo(size = 108.dp, elevation = 8.dp)
            }

            Text(
                text = "BlockPath",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1E293B),
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Tactical Maze & Wall Duel",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF78716C),
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Badges / Feature Highlights
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFFE2E8F0),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "♟ Pass & Play",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "🤖 Smart AI",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF92400E),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    color = Color(0xFFDBEAFE),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "🎙 Voice P2P",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E40AF),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(42.dp))

            // Warm Modern Circular Loading Indicator
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Player1Color,
                trackColor = Color(0xFFE2E8F0),
                strokeWidth = 3.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Preparing board...",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF94A3B8)
            )
        }

        // Bottom version watermark
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = "Version 1.0.0 • Pure Strategy Board Game",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFA8A29E)
            )
        }
    }
}
