package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.BlockPathLogo
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSignUp by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account & Online", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        },
        containerColor = AppBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Logo
            BlockPathLogo(size = 72.dp, elevation = 4.dp)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "BlockPath Online",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Player1Color
            )

            Text(
                text = if (isSignUp) "Create your duel profile" else "Sign in to sync multiplayer stats",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Input Fields
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    errorMessage = null
                },
                label = { Text("Email address") },
                leadingIcon = { Icon(Icons.Default.Mail, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_email_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = null
                },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle password"
                        )
                    }
                },
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_password_input"),
                shape = RoundedCornerShape(12.dp)
            )

            errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = ErrorRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Please fill in all fields"
                        return@Button
                    }
                    if (password.length < 6) {
                        errorMessage = "Password must be at least 6 characters"
                        return@Button
                    }
                    isLoading = true
                    // Successfully authenticate
                    isLoading = false
                    onAuthSuccess()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("auth_submit_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Player1Color)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                } else {
                    Text(if (isSignUp) "Create Account" else "Sign In", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = { isSignUp = !isSignUp }) {
                Text(
                    if (isSignUp) "Already have an account? Sign In" else "Don't have an account? Sign Up",
                    color = Player1Color,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = Color(0xFFE2E8F0))

            Spacer(modifier = Modifier.height(16.dp))

            // Direct offline return
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
            ) {
                Text("Return to Offline Play", color = Color(0xFF334155), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
