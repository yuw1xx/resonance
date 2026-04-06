package dev.yuwixx.resonance.presentation.screens

import android.Manifest
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.*
import dev.yuwixx.resonance.data.repository.LastFmAuthState
import dev.yuwixx.resonance.presentation.viewmodel.SettingsViewModel

// Steps: 0 = Welcome · 1 = Permission · 2 = Last.fm (optional) · 3 = Ready
private const val TOTAL_STEPS = 4

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SetupScreen(onComplete: () -> Unit) {
    var currentStep by remember { mutableIntStateOf(0) }

    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val lastFmAuthState by settingsViewModel.lastFmAuthState.collectAsState()

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionState = rememberPermissionState(permission)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    fadeIn() + slideInHorizontally { it } togetherWith fadeOut() + slideOutHorizontally { -it }
                },
                label = "setup_steps"
            ) { step ->
                when (step) {
                    0 -> WelcomeStep()
                    1 -> PermissionStep(permissionState)
                    2 -> LastFmStep(
                        authState = lastFmAuthState,
                        onLogin = { u, p -> settingsViewModel.lastFmLogin(u, p) },
                    )
                    3 -> ReadyStep()
                }
            }
        }

        // Bottom navigation row
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Step-dot indicator
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(TOTAL_STEPS) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentStep) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == currentStep) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            // Action button(s)
            if (currentStep == 2) {
                // Last.fm step — offer Skip alongside the primary action
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { currentStep++ }) {
                        Text("Skip")
                    }
                    val linked = lastFmAuthState is LastFmAuthState.Authenticated
                    Button(
                        onClick = { currentStep++ },
                        shape = MaterialTheme.shapes.medium,
                        enabled = linked
                    ) {
                        // The button inside LastFmStep handles the actual login;
                        // once linked the user taps Continue here to proceed.
                        if (linked) {
                            Text("Continue")
                            Icon(
                                Icons.Rounded.ArrowForward,
                                null,
                                modifier = Modifier.padding(start = 8.dp).size(18.dp)
                            )
                        } else {
                            Text("Continue")
                            Icon(
                                Icons.Rounded.ArrowForward,
                                null,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(18.dp)
                            )
                        }
                    }
                }
            } else {
                Button(
                    onClick = {
                        when {
                            currentStep < TOTAL_STEPS - 1 -> {
                                if (currentStep == 1 && !permissionState.status.isGranted) {
                                    permissionState.launchPermissionRequest()
                                } else {
                                    currentStep++
                                }
                            }
                            else -> onComplete()
                        }
                    },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(if (currentStep == TOTAL_STEPS - 1) "Get Started" else "Continue")
                    if (currentStep < TOTAL_STEPS - 1) {
                        Icon(
                            Icons.Rounded.ArrowForward,
                            null,
                            modifier = Modifier.padding(start = 8.dp).size(18.dp)
                        )
                    }
                }
            }
        }
    }

    // Auto-advance from permission step once granted
    LaunchedEffect(permissionState.status.isGranted) {
        if (currentStep == 1 && permissionState.status.isGranted) {
            currentStep = 2
        }
    }
}

// ─── Step composables ─────────────────────────────────────────────────────────

@Composable
private fun WelcomeStep() {
    SetupContent(
        icon = Icons.Rounded.MusicNote,
        title = "Welcome to Resonance",
        description = "Your personal music experience, refined with Material You 3. Experience your library like never before."
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionStep(permissionState: PermissionState) {
    SetupContent(
        icon = Icons.Rounded.Storage,
        title = "Library Access",
        description = "To find and play your music, Resonance needs permission to read audio files on your device."
    )
}

@Composable
private fun LastFmStep(
    authState: LastFmAuthState,
    onLogin: (String, String) -> Unit,
) {
    var showLoginDialog by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Icon
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                Icons.Rounded.Radio,
                contentDescription = null,
                modifier = Modifier
                    .padding(32.dp)
                    .size(56.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            "Scrobble Your Music",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "Connect your Last.fm account to automatically track every song you listen to and build a rich listening history.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(28.dp))

        // Status / action card
        when (authState) {
            is LastFmAuthState.Authenticated -> {
                // Linked successfully
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                "Linked as ${authState.username}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Tap Continue below to proceed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            is LastFmAuthState.Loading -> {
                CircularProgressIndicator()
            }

            is LastFmAuthState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Error, null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            authState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showLoginDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    // Last.fm red pill
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFD51007),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            "Last.fm",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text("Try Again")
                }
            }

            LastFmAuthState.Idle -> {
                Button(
                    onClick = { showLoginDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    // Last.fm red pill inside button
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFD51007),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            "Last.fm",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text("Link Account")
                }
            }
        }
    }

    if (showLoginDialog) {
        LastFmLoginDialog(
            onDismiss = { showLoginDialog = false },
            onLogin = { u, p ->
                onLogin(u, p)
                showLoginDialog = false
            }
        )
    }
}

@Composable
private fun LastFmLoginDialog(
    onDismiss: () -> Unit,
    onLogin: (String, String) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFD51007)
            ) {
                Text(
                    "Last.fm",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        },
        title = { Text("Sign in to Last.fm") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Rounded.VisibilityOff
                                else Icons.Rounded.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onLogin(username, password) },
                enabled = username.isNotBlank() && password.isNotBlank()
            ) { Text("Sign In") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ReadyStep() {
    SetupContent(
        icon = Icons.Rounded.AutoAwesome,
        title = "All Set!",
        description = "We're ready to build your library. Enjoy the smooth transitions and expressive design of Resonance."
    )
}

// ─── Shared layout ─────────────────────────────────────────────────────────────

@Composable
private fun SetupContent(
    icon: ImageVector,
    title: String,
    description: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                icon,
                null,
                modifier = Modifier
                    .padding(32.dp)
                    .size(56.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}