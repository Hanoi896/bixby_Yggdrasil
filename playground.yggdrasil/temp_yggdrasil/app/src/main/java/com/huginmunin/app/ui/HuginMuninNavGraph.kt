package com.huginmunin.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.huginmunin.app.ui.dashboard.DashboardScreen
import com.huginmunin.app.ui.permissions.PermissionScreen
import com.huginmunin.app.ui.privacy.LearningControlScreen
import com.huginmunin.app.ui.trustee.TrusteeManagementScreen
import com.huginmunin.app.ui.chat.ChatScreen
import com.huginmunin.app.ui.security.BreakGlassScreen
import com.huginmunin.app.ui.onboarding.OnboardingScreen

@Composable
fun HuginMuninNavGraph(
    navController: NavHostController,
    startDestination: String = "dashboard"
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("onboarding") {
            OnboardingScreen(
                onCompleted = {
                    navController.navigate("dashboard") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }
        
        composable("dashboard") {
            DashboardScreen(
                onNavigateToPermissions = { navController.navigate("permissions") },
                onNavigateToChat = { navController.navigate("chat") },
                onNavigateToPrivacy = { navController.navigate("privacy") },
                onNavigateToTrustees = { navController.navigate("trustees") },
                onNavigateToBreakGlass = { navController.navigate("break_glass") }
            )
        }
        
        composable("permissions") {
            PermissionScreen(onBack = { navController.popBackStack() })
        }
        
        composable("chat") {
            ChatScreen(onBack = { navController.popBackStack() })
        }
        
        composable("privacy") {
            LearningControlScreen(onBack = { navController.popBackStack() })
        }
        
        composable("trustees") {
            TrusteeManagementScreen(onBack = { navController.popBackStack() })
        }

        composable("break_glass") {
            BreakGlassScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
