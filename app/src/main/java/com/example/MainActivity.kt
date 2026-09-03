package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val navController = rememberNavController()
        val gameViewModel: GameViewModel = viewModel()

        NavHost(navController = navController, startDestination = "splash") {
          composable("splash") {
            SplashScreen(
              onSplashComplete = {
                navController.navigate("home") {
                  popUpTo("splash") { inclusive = true }
                }
              }
            )
          }
          composable("home") {
            HomeScreen(
              onStartPassAndPlay = {
                gameViewModel.startGame(GameMode.LOCAL_PASS_AND_PLAY)
                navController.navigate("game")
              },
              onStartVsAi = { difficulty ->
                gameViewModel.startGame(GameMode.VS_COMPUTER, difficulty)
                navController.navigate("game")
              },
              onOpenAuth = {
                navController.navigate("auth")
              }
            )
          }
          composable("game") {
            GameScreen(
              gameViewModel = gameViewModel,
              onBack = {
                navController.popBackStack("home", inclusive = false)
              }
            )
          }
          composable("auth") {
            AuthScreen(
              onAuthSuccess = {
                navController.popBackStack("home", inclusive = false)
              },
              onBack = {
                navController.popBackStack("home", inclusive = false)
              }
            )
          }
        }
      }
    }
  }
}

