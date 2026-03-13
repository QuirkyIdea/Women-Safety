package com.example.suraksha

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.suraksha.ui.screens.*
import com.example.suraksha.ui.theme.SurakshaTheme
import com.example.suraksha.ui.viewmodels.MainViewModel
import com.example.suraksha.utils.PermissionManager

class MainActivity : AppCompatActivity() {
    
    private val mainViewModel: MainViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SurakshaTheme {
                SurakshaApp(mainViewModel)
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Only redirect to permission screens if critical permissions are missing
        // and this is the first time the app is being used
        if (!PermissionManager.hasCriticalPermissions(this)) {
            // Check if we should show onboarding or permission request
            if (shouldShowOnboarding()) {
                startActivity(Intent(this, OnboardingActivity::class.java))
                finish()
            } else if (shouldShowPermissionRequest()) {
                startActivity(Intent(this, PermissionRequestActivity::class.java))
                finish()
            }
        }
    }
    
    private fun shouldShowOnboarding(): Boolean {
        // Check if this is the first time the app is being used
        val sharedPrefs = getSharedPreferences("suraksha_prefs", MODE_PRIVATE)
        return !sharedPrefs.getBoolean("has_seen_onboarding", false)
    }
    
    private fun shouldShowPermissionRequest(): Boolean {
        // Only show permission request if critical permissions are missing
        // and user hasn't explicitly skipped
        val sharedPrefs = getSharedPreferences("suraksha_prefs", MODE_PRIVATE)
        val hasSkippedPermissions = sharedPrefs.getBoolean("has_skipped_permissions", false)
        return !hasSkippedPermissions && !PermissionManager.hasCriticalPermissions(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurakshaApp(mainViewModel: MainViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    
    // Check if onboarding is needed
    LaunchedEffect(uiState.hasCompletedOnboarding) {
        if (!uiState.hasCompletedOnboarding) {
            // This will be handled by onResume in MainActivity
        }
    }
    

    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                listOf(
                    NavigationItem.Home,
                    NavigationItem.Recordings,
                    NavigationItem.Contacts,
                    NavigationItem.Settings
                ).forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavigationItem.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavigationItem.Home.route) {
                HomeScreen(
                    mainViewModel = mainViewModel,
                    onNavigateToFakeCall = {
                        val intent = Intent(context, FakeCallActivity::class.java)
                        context.startActivity(intent)
                    },
                    onNavigateToSupport = {
                        navController.navigate("support_resources")
                    },
                    onNavigateToLogs = {
                        navController.navigate("incident_logs")
                    }
                )
            }
            composable(NavigationItem.Contacts.route) {
                ContactsScreen(
                    viewModel = mainViewModel.contactsViewModel
                )
            }
            composable(NavigationItem.Recordings.route) {
                RecordingsScreen()
            }
            composable(NavigationItem.Settings.route) {
                SettingsScreen(
                    mainViewModel = mainViewModel,
                    onNavigateToVault = {
                        navController.navigate("vault")
                    }
                )
            }
            composable("vault") {
                SecureVaultScreen()
            }
            composable("support_resources") {
                SupportResourcesScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("incident_logs") {
                IncidentLogScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

sealed class NavigationItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Home : NavigationItem(
        route = "home",
        title = "Home",
        icon = Icons.Default.Home
    )
    
    object Contacts : NavigationItem(
        route = "contacts",
        title = "Contacts",
        icon = Icons.Default.Person
    )
    
    object Recordings : NavigationItem(
        route = "recordings",
        title = "Recordings",
        icon = Icons.Default.Mic
    )
    
    object Settings : NavigationItem(
        route = "settings",
        title = "Settings",
        icon = Icons.Default.Settings
    )

}
