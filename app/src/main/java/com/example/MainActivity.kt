package com.example

import android.os.Build
import android.os.Bundle
import com.google.firebase.FirebaseApp
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.theme.Slate900
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.example.ui.components.DocumentTypeSelectionDialog
import com.example.ui.components.UniversalDocumentReviewDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Person
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.AttendanceScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DocumentsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RoutineScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.SyllabusScreen
import com.example.ui.theme.AcademicBlue
import com.example.ui.theme.DangerRed
import com.example.ui.theme.StudyMateTheme
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.AppScreenState
import com.example.ui.viewmodel.StudyMateViewModel
import kotlinx.coroutines.delay

sealed class NavTab(val id: String, val title: String, val icon: ImageVector) {
    object Home : NavTab("HOME", "Home", Icons.Default.Home)
    object Routine : NavTab("ROUTINE", "Routine", Icons.Default.DateRange)
    object Attendance : NavTab("ATTENDANCE", "Attendance", Icons.Default.CheckCircle)
    object Syllabus : NavTab("SYLLABUS", "Syllabus", Icons.AutoMirrored.Filled.FormatListBulleted)
    object Documents : NavTab("DOCUMENTS", "Docs", Icons.Default.Description)
    object AI : NavTab("AI", "AI", Icons.Default.AutoAwesome)
    object Profile : NavTab("PROFILE", "Profile", Icons.Default.Person)
}


class MainActivity : ComponentActivity() {

    private val viewModel: StudyMateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        } catch (_: Exception) {}
        enableEdgeToEdge()
        setContent {
            StudyMateTheme {
                val appScreenState by viewModel.appScreenState.collectAsStateWithLifecycle()
                val authError by viewModel.authError.collectAsStateWithLifecycle()
                val isAuthLoading by viewModel.isAuthLoading.collectAsStateWithLifecycle()
                val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

                when (appScreenState) {
                    AppScreenState.SPLASH -> {
                        SplashScreen()
                    }
                    AppScreenState.AUTH -> {
                        AuthScreen(
                            onLogin = { email, pass -> viewModel.login(email, pass) },
                            onRegister = { name, email, pass, confirm -> viewModel.register(name, email, pass, confirm) },
                            onGoogleSignIn = { viewModel.loginWithGoogle(this@MainActivity) },
                            onForgotPassword = { email, callback -> viewModel.sendPasswordResetEmail(email, callback) },
                            authError = authError,
                            isLoading = isAuthLoading,
                            onClearError = { viewModel.clearAuthError() }
                        )
                    }
                    AppScreenState.ONBOARDING -> {
                        OnboardingScreen(
                            userName = currentUser?.fullName ?: "Student",
                            onSaveProfile = { col, crs, sem, yr, grp ->
                                viewModel.saveAcademicProfile(col, crs, sem, yr, grp)
                            },
                            onSkip = { viewModel.skipOnboarding() }
                        )
                    }
                    AppScreenState.MAIN -> {
                        MainAppScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: StudyMateViewModel) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val universalResult by viewModel.universalDocumentResult.collectAsStateWithLifecycle()
    val showTypeSelect by viewModel.showTypeSelectionDialog.collectAsStateWithLifecycle()
    val isExtracting by viewModel.isExtracting.collectAsStateWithLifecycle()

    val tabs = listOf(
        NavTab.Home,
        NavTab.Routine,
        NavTab.Attendance,
        NavTab.Syllabus,
        NavTab.Documents,
        NavTab.AI
    )

    // Request POST_NOTIFICATIONS on Android 13+ gracefully
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { _ ->
            // Handled gracefully: even if denied, app functions normally
        }
        val context = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(Unit) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(permission)
            }
        }
    }

    // Auto dismiss user notification banner
    LaunchedEffect(userMessage) {
        if (userMessage != null) {
            delay(4000)
            viewModel.dismissUserMessage()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                tabs.forEach { tab ->
                    val isSelected = currentTab == tab.id
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.selectTab(tab.id) },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AcademicBlue,
                            selectedTextColor = AcademicBlue,
                            indicatorColor = AcademicBlue.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.id.lowercase()}")
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                NavTab.Home.id -> HomeScreen(
                    viewModel = viewModel,
                    onNavigateToTab = { tabId -> viewModel.selectTab(tabId) }
                )
                NavTab.Routine.id -> RoutineScreen(viewModel = viewModel)
                NavTab.Attendance.id -> AttendanceScreen(viewModel = viewModel)
                NavTab.Syllabus.id -> SyllabusScreen(viewModel = viewModel)
                NavTab.Documents.id -> DocumentsScreen(viewModel = viewModel)
                NavTab.AI.id -> AiAssistantScreen(viewModel = viewModel)
                NavTab.Profile.id, "PROFILE" -> ProfileScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.selectTab("HOME") }
                )
            }

            // Floating feedback banner
            AnimatedVisibility(
                visible = userMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (userMessage != null) {
                    val msg = userMessage!!
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (msg.isError) DangerRed else AcademicBlue,
                        shadowElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (msg.isError) Icons.Default.Warning else Icons.Default.Info,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = msg.message,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.dismissUserMessage() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Universal Document Review Dialog
            if (universalResult != null) {
                UniversalDocumentReviewDialog(
                    result = universalResult!!,
                    onConfirm = { confirmedType ->
                        viewModel.confirmUniversalDocument(universalResult!!, confirmedType)
                    },
                    onDismiss = { viewModel.dismissUniversalDocumentResult() },
                    onResolveConflict = { applyNew, conflict ->
                        viewModel.resolveRoutineConflict(applyNew, conflict)
                    },
                    onChangeTypeRequested = {
                        viewModel.dismissUniversalDocumentResult()
                    }
                )
            }

            // Universal Document Type Selection Dialog
            if (showTypeSelect) {
                DocumentTypeSelectionDialog(
                    onSelectType = { type ->
                        viewModel.overrideDocumentTypeAndReprocess(type)
                    },
                    onDismiss = { viewModel.dismissTypeSelectionDialog() }
                )
            }

            // Extracting / Analyzing Progress Overlay
            if (isExtracting) {
                Surface(
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            shadowElevation = 8.dp,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = AcademicBlue, modifier = Modifier.size(44.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Analyzing Document Structure...",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Processing tables, sections & academic cross-checks",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
