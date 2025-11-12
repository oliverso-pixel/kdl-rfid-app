package com.kdl.rfidinventory.presentation.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.kdl.rfidinventory.presentation.ui.screens.main.MainScreen
import com.kdl.rfidinventory.presentation.ui.screens.production.ProductionScreen
import com.kdl.rfidinventory.presentation.ui.screens.warehouse.receiving.ReceivingScreen
import com.kdl.rfidinventory.presentation.ui.screens.warehouse.inventory.InventoryScreen
import com.kdl.rfidinventory.presentation.ui.screens.shipping.ShippingScreen
import com.kdl.rfidinventory.presentation.ui.screens.clear.ClearScreen
import com.kdl.rfidinventory.presentation.ui.screens.sampling.SamplingScreen
import com.kdl.rfidinventory.presentation.ui.screens.admin.AdminScreen

sealed class Screen(val route: String, val title: String) {
    data object Main : Screen("main", "RFID 庫存管理")
    data object Production : Screen("production", "生產模式")
    data object Receiving : Screen("receiving", "倉庫收貨")
    data object Inventory : Screen("inventory", "倉庫盤點")
    data object Shipping : Screen("shipping", "出貨模式")
    data object Clear : Screen("clear", "清除配置")
    data object Sampling : Screen("sampling", "抽樣檢驗")
    data object Admin : Screen("admin", "管理員設定")
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Main.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Main.route) {
            MainScreen(navController = navController)
        }

        composable(Screen.Production.route) {
            ProductionScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Receiving.route) {
            ReceivingScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Inventory.route) {
            InventoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Shipping.route) {
            ShippingScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Clear.route) {
            ClearScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Sampling.route) {
            SamplingScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Admin.route) {
            AdminScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

