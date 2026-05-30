package com.weishao.webdav

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weishao.webdav.ui.WebDavViewModel
import com.weishao.webdav.ui.screens.*
import com.weishao.webdav.ui.theme.MyApplicationTheme

enum class Screen {
    Login, Browser, Settings, Logs
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: WebDavViewModel = viewModel()
                val config by viewModel.config.collectAsState()
                var currentScreen by rememberSaveable { mutableStateOf(Screen.Login) }
                var hasAttemptedAutoLogin by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(config) {
                    if (!hasAttemptedAutoLogin && config.url.isNotEmpty()) {
                        currentScreen = Screen.Browser
                        hasAttemptedAutoLogin = true
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    when (currentScreen) {
                        Screen.Login -> LoginScreen(
                            config = config,
                            onLogin = {
                                viewModel.updateConfig(it)
                                currentScreen = Screen.Browser
                                hasAttemptedAutoLogin = true
                            }
                        )
                        Screen.Browser -> FileBrowserScreen(
                            viewModel = viewModel,
                            onLogout = {
                                viewModel.logout()
                                currentScreen = Screen.Login
                                hasAttemptedAutoLogin = false
                            },
                            onSettings = {
                                currentScreen = Screen.Settings
                            },
                            onViewLogs = {
                                currentScreen = Screen.Logs
                            }
                        )
                        Screen.Settings -> SettingsScreen(
                            config = config,
                            onConfigChange = { viewModel.updateConfig(it) },
                            onBack = { currentScreen = Screen.Browser }
                        )
                        Screen.Logs -> LogScreen(
                            onBack = { currentScreen = Screen.Browser }
                        )
                    }
                }
            }
        }
    }
}
