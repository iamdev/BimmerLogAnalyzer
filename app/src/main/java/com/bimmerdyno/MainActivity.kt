package com.bimmerdyno

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bimmerdyno.ui.screens.ChartScreen
import com.bimmerdyno.ui.screens.HomeScreen
import com.bimmerdyno.ui.screens.SettingsScreen
import com.bimmerdyno.ui.theme.BimmerTheme
import com.bimmerdyno.viewmodel.MainViewModel
import com.bimmerdyno.viewmodel.UiState

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle CSV file opened from external app
        intent?.data?.let { uri ->
            val name = contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                c.moveToFirst(); if (idx >= 0) c.getString(idx) else "log.csv"
            } ?: "log.csv"
            viewModel.loadLocalFile(uri, name)
        }

        setContent {
            BimmerTheme {
                val navController = rememberNavController()
                val uiState by viewModel.uiState.collectAsState()

                NavHost(navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            viewModel = viewModel,
                            onSessionLoaded = {
                                navController.navigate("chart") {
                                    launchSingleTop = true
                                }
                            },
                            onOpenSettings = {
                                navController.navigate("settings") {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                    composable("chart") {
                        val session = (uiState as? UiState.Success)?.session
                        // A mapping change re-parses the open log; if that parse
                        // now fails there is nothing to draw, so fall back home
                        // rather than leaving a blank chart screen behind.
                        LaunchedEffect(uiState) {
                            if (uiState is UiState.Error || uiState is UiState.Idle) {
                                navController.popBackStack("home", inclusive = false)
                            }
                        }
                        if (session != null) {
                            ChartScreen(
                                viewModel = viewModel,
                                session = session,
                                onBack = {
                                    viewModel.reset()
                                    navController.popBackStack()
                                },
                                onOpenSettings = {
                                    navController.navigate("settings") {
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
