package com.marauder.mobile.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.marauder.mobile.data.AnalyzerKind
import com.marauder.mobile.data.Catalog
import com.marauder.mobile.data.ListType
import com.marauder.mobile.data.MenuAction
import com.marauder.mobile.data.MenuItem
import com.marauder.mobile.ui.screens.AboutScreen
import com.marauder.mobile.ui.screens.AnalyzerScreen
import com.marauder.mobile.ui.screens.ConnectScreen
import com.marauder.mobile.ui.screens.ConsoleScreen
import com.marauder.mobile.ui.screens.ListScreen
import com.marauder.mobile.ui.screens.LiveActivityScreen
import com.marauder.mobile.ui.screens.MenuScreen
import com.marauder.mobile.usb.UsbSerialManager
import com.marauder.mobile.vm.MarauderViewModel

private object Routes {
    const val CONNECT = "connect"
    const val MENU = "menu"
    const val LIST = "list"
    const val CONSOLE = "console"
    const val ANALYZER = "analyzer"
    const val LIVE = "live"
    const val ABOUT = "about"
    fun menu(id: String) = "$MENU/$id"
    fun list(type: ListType) = "$LIST/${type.name}"
    fun analyzer(kind: AnalyzerKind, command: String) = "$ANALYZER/${kind.name}/${Uri.encode(command)}"
    fun live(title: String, command: String) = "$LIVE/${Uri.encode(title)}/${Uri.encode(command)}"
}

@Composable
fun AppRoot() {
    val vm: MarauderViewModel = viewModel()
    val nav = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.snackbar.collect { snackbarHostState.showSnackbar(it) }
    }

    ConnectionGate(vm, nav)

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = Routes.CONNECT) {

            composable(Routes.CONNECT) { ConnectScreen(vm) }

            composable(
                route = "${Routes.MENU}/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: Catalog.ROOT
                MenuScreen(
                    screenId = id,
                    vm = vm,
                    onItemClick = { item -> handleAction(item, nav, vm) },
                    onBack = { nav.popBackStack() },
                    onOpenConsole = { nav.navigate(Routes.CONSOLE) },
                    onOpenAbout = { nav.navigate(Routes.ABOUT) },
                    onDisconnect = { vm.disconnect() },
                )
            }

            composable(
                route = "${Routes.LIST}/{type}",
                arguments = listOf(navArgument("type") { type = NavType.StringType }),
            ) { entry ->
                val type = runCatching { ListType.valueOf(entry.arguments?.getString("type").orEmpty()) }
                    .getOrDefault(ListType.ACCESS_POINTS)
                ListScreen(type = type, vm = vm, onBack = { nav.popBackStack() })
            }

            composable(Routes.CONSOLE) {
                ConsoleScreen(vm = vm, onBack = { nav.popBackStack() })
            }

            composable(Routes.ABOUT) {
                AboutScreen(onBack = { nav.popBackStack() })
            }

            composable(
                route = "${Routes.ANALYZER}/{kind}/{cmd}",
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType },
                    navArgument("cmd") { type = NavType.StringType },
                ),
            ) { entry ->
                val kind = runCatching { AnalyzerKind.valueOf(entry.arguments?.getString("kind").orEmpty()) }
                    .getOrDefault(AnalyzerKind.WIFI)
                val command = Uri.decode(entry.arguments?.getString("cmd").orEmpty())
                AnalyzerScreen(command = command, kind = kind, vm = vm, onBack = { nav.popBackStack() })
            }

            composable(
                route = "${Routes.LIVE}/{title}/{cmd}",
                arguments = listOf(
                    navArgument("title") { type = NavType.StringType },
                    navArgument("cmd") { type = NavType.StringType },
                ),
            ) { entry ->
                val title = Uri.decode(entry.arguments?.getString("title").orEmpty())
                val command = Uri.decode(entry.arguments?.getString("cmd").orEmpty())
                LiveActivityScreen(title = title, command = command, vm = vm, onBack = { nav.popBackStack() })
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Steers the user to the connect screen or the main menu as the link goes up/down. */
@Composable
private fun ConnectionGate(vm: MarauderViewModel, nav: NavHostController) {
    val status by vm.status.collectAsState()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(status, currentRoute) {
        when (status) {
            UsbSerialManager.Status.CONNECTED -> {
                if (currentRoute == Routes.CONNECT) {
                    nav.navigate(Routes.menu(Catalog.ROOT)) {
                        popUpTo(Routes.CONNECT) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            UsbSerialManager.Status.DISCONNECTED -> {
                if (currentRoute != null && currentRoute != Routes.CONNECT) {
                    nav.navigate(Routes.CONNECT) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            else -> {}
        }
    }
}

private fun handleAction(
    item: MenuItem,
    nav: NavHostController,
    vm: MarauderViewModel,
) {
    when (val action = item.action) {
        is MenuAction.Submenu -> nav.navigate(Routes.menu(action.id))
        is MenuAction.Send -> nav.navigate(Routes.live(item.title, action.command))
        is MenuAction.OpenList -> {
            vm.refreshList(action.type)
            nav.navigate(Routes.list(action.type))
        }
        is MenuAction.OpenAnalyzer -> nav.navigate(Routes.analyzer(action.kind, action.command))
        MenuAction.OpenConsole -> nav.navigate(Routes.CONSOLE)
    }
}
