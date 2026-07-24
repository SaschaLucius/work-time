package de.worktime.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.worktime.ui.calculator.RechnerScreen
import de.worktime.ui.theme.ArbeitsTheme
import de.worktime.ui.timer.TimerScreen
import de.worktime.ui.woche.WochensaldoScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArbeitsTheme {
                ArbeitsApp()
            }
        }
    }
}

private data class NavTab(val route: String, val label: String, val icon: @Composable () -> Unit)

@Composable
private fun ArbeitsApp() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()

    val tabs = listOf(
        NavTab("timer", "Timer") { Icon(Icons.Default.Timer, contentDescription = "Timer") },
        NavTab("rechner", "Rechner") { Icon(Icons.Default.Calculate, contentDescription = "Rechner") },
        NavTab("woche", "Woche") { Icon(Icons.Default.DateRange, contentDescription = "Woche") }
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = tab.icon,
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "timer",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable("timer") { TimerScreen(viewModel) }
            composable("rechner") { RechnerScreen() }
            composable("woche") { WochensaldoScreen() }
        }
    }
}
