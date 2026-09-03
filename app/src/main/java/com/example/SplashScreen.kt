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
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_scale"
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

        // Direct, smooth entry to game (no waiting for ad, zero crash risk):
        kotlinx.coroutines.delay(800L)
        onSplashComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Dark slate navy
                        Color(0xFF1E293B)  // Rich deep slate
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
            // Animated Logo
            Box(
                modifier = Modifier
                    .scale(scale)
                    .padding(bottom = 24.dp)
            ) {
                BlockPathLogo(size = 100.dp, elevation = 12.dp)
            }

            Text(
                text = "BlockPath",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Strategy Maze & Block Duel",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Loading indicator
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = Color(0xFF38BDF8),
                strokeWidth = 3.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Starting game...",
                fontSize = 13.sp,
                color = Color(0xFF64748B)
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
                text = "Version 1.0.0 • Offline & Local Multiplayer",
                fontSize = 11.sp,
                color = Color(0xFF475569)
            )
        }
    }
}
