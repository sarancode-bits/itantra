package com.itantra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.itantra.data.repository.SessionRepository
import com.itantra.ui.home.HomeScreen
import com.itantra.ui.home.HomeViewModel
import com.itantra.ui.permissions.PermissionsScreen
import com.itantra.ui.settings.SettingsScreen
import com.itantra.ui.settings.SettingsViewModel
import com.itantra.ui.sos.SosActiveScreen
import com.itantra.ui.sos.SosConfirmScreen
import com.itantra.ui.talk.TalkScreen
import com.itantra.ui.talk.TalkViewModel
import com.itantra.ui.theme.DarkBackground
import com.itantra.ui.theme.ITantraTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionRepository: SessionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ITantraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    ItantraAppNavigation(sessionRepository = sessionRepository)
                }
            }
        }
    }
}

@Composable
fun ItantraAppNavigation(sessionRepository: SessionRepository) {
    val navController = rememberNavController()
    val sosSenderName by sessionRepository.sosSenderName.collectAsState()

    // Global SOS override: if SOS arrives from peer, navigate immediately to active SOS screen
    LaunchedEffect(sosSenderName) {
        if (sosSenderName != null) {
            navController.navigate("sos_active") {
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            val viewModel: HomeViewModel = hiltViewModel()
            HomeScreen(
                viewModel = viewModel,
                onNavigateToTalk = { navController.navigate("talk") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToPermissions = { navController.navigate("permissions") }
            )
        }

        composable("talk") {
            val viewModel: TalkViewModel = hiltViewModel()
            TalkScreen(
                viewModel = viewModel,
                onNavigateToHome = { navController.navigate("home") },
                onNavigateToSosConfirm = { navController.navigate("sos_confirm") }
            )
        }

        composable("sos_confirm") {
            SosConfirmScreen(
                onConfirmSos = {
                    sessionRepository.triggerSos()
                    navController.navigate("sos_active") {
                        popUpTo("home")
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable("sos_active") {
            SosActiveScreen(
                senderName = sosSenderName,
                onCancelSos = {
                    sessionRepository.cancelSos()
                    navController.navigate("talk") {
                        popUpTo("home")
                    }
                }
            )
        }

        composable("settings") {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPermissions = { navController.navigate("permissions") }
            )
        }

        composable("permissions") {
            PermissionsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
