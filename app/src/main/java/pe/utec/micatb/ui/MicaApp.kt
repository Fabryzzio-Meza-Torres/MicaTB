package pe.utec.micatb.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pe.utec.micatb.AppContainer
import pe.utec.micatb.ui.capture.CaptureScreen
import pe.utec.micatb.ui.capture.CaptureViewModel
import pe.utec.micatb.ui.components.DisclaimerStrip
import pe.utec.micatb.ui.components.MicaIcon
import pe.utec.micatb.ui.home.HomeScreen
import pe.utec.micatb.ui.settings.SettingsScreen
import pe.utec.micatb.ui.results.FieldDetailScreen
import pe.utec.micatb.ui.results.HistoryScreen
import pe.utec.micatb.ui.results.SessionResultScreen
import pe.utec.micatb.ui.session.SessionScreen
import pe.utec.micatb.ui.theme.MicaIcons

private enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Inicio", MicaIcons.Slide),
    Capture("capture", "Captura", MicaIcons.Capture),
    Session("session", "Sesión", MicaIcons.Session),
    Results("results", "Resultados", MicaIcons.Results),
    Settings("settings", "Ajustes", MicaIcons.Settings),
}

@Composable
fun MicaApp(container: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route

    fun go(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { DisclaimerStrip(container.modelConfig.displayNotice) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Dest.entries.forEach { d ->
                    NavigationBarItem(
                        selected = current == d.route || (current?.startsWith(d.route + "/") == true),
                        onClick = { go(d.route) },
                        icon = { MicaIcon(d.icon, null, tint = androidx.compose.ui.graphics.Color.Unspecified.let { MaterialTheme.colorScheme.onSurface }) },
                        label = { Text(d.label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Dest.Home.route, modifier = Modifier.padding(padding)) {
            composable(Dest.Home.route) { HomeScreen(container, onNavigate = ::go) }
            composable(Dest.Capture.route) {
                val vm: CaptureViewModel = viewModel(factory = viewModelFactory { initializer { CaptureViewModel(container) } })
                CaptureScreen(vm, container)
            }
            composable(Dest.Session.route) { SessionScreen(container, onOpenResults = { nav.navigate("results/$it") }) }
            composable(Dest.Results.route) { HistoryScreen(container, onOpen = { nav.navigate("results/$it") }) }
            composable("results/{sid}", listOf(navArgument("sid") { type = NavType.StringType })) { e ->
                val sid = e.arguments!!.getString("sid")!!
                SessionResultScreen(container, sid, onBack = { nav.popBackStack() }, onOpenField = { nav.navigate("results/$sid/$it") })
            }
            composable("results/{sid}/{idx}", listOf(navArgument("sid") { type = NavType.StringType }, navArgument("idx") { type = NavType.IntType })) { e ->
                val sid = e.arguments!!.getString("sid")!!
                val idx = e.arguments!!.getInt("idx")
                FieldDetailScreen(container, sid, idx, onBack = { nav.popBackStack() }, onGo = { nav.navigate("results/$sid/$it") { popUpTo("results/$sid") } })
            }
            composable(Dest.Settings.route) { SettingsScreen(container) }
        }
    }
}
