package com.kdl.rfidinventory.presentation.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kdl.rfidinventory.presentation.ui.screens.main.MainScreen
import com.kdl.rfidinventory.presentation.ui.screens.production.ProductionScreen
import com.kdl.rfidinventory.presentation.ui.screens.warehouse.receiving.ReceivingScreen
import com.kdl.rfidinventory.presentation.ui.screens.warehouse.inventory.InventoryScreen
import com.kdl.rfidinventory.presentation.ui.screens.warehouse.transfer.TransferScreen
import com.kdl.rfidinventory.presentation.ui.screens.clear.ClearScreen
//import com.kdl.rfidinventory.presentation.ui.screens.sampling.SamplingScreen
import com.kdl.rfidinventory.presentation.ui.screens.admin.AdminScreen
import com.kdl.rfidinventory.presentation.ui.screens.admin.BasketManagementScreen
import com.kdl.rfidinventory.presentation.ui.screens.admin.BasketDetailScreen
import com.kdl.rfidinventory.presentation.ui.screens.auth.LoginScreen
import com.kdl.rfidinventory.presentation.ui.screens.loading.LoadingScreen
import com.kdl.rfidinventory.presentation.ui.screens.shipping.ShippingVerifyScreen

sealed class Screen(val route: String, val title: String) {
    data object Login : Screen("login", "登錄")
    data object Main : Screen("main", "RFID 庫存管理")

    data object Production : Screen("production", "生產模式")

    data object Receiving : Screen("receiving", "倉庫收貨")
    data object Inventory : Screen("inventory", "倉庫盤點")
    data object Transfer : Screen("transfer", "倉庫轉換")

    data object Loading : Screen("loading", "上貨模式")
    data object ShippingVerify : Screen("shipping_verify", "出貨模式")

    data object Clear : Screen("clear", "清除配置")
//    data object Sampling : Screen("sampling", "抽樣檢驗")

    data object Admin : Screen("admin", "管理員設定")
    data object BasketManagement : Screen("basket_management", "籃子管理")
    data object BasketDetail : Screen("basket_detail/{uid}", "籃子詳情") {
        fun createRoute(uid: String) = "basket_detail/$uid"
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Login.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Main.route) {
            MainScreen(
                navController = navController,
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

//        composable(Screen.Main.route) {
//            MainScreen(
//                navController = navController,
//                onLogout = TODO(),
//                viewModel = TODO()
//            )
//        }

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
        composable(Screen.Transfer.route) {
            TransferScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Loading.route) {
            LoadingScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.ShippingVerify.route) {
            ShippingVerifyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Clear.route) {
            ClearScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

//        composable(Screen.Sampling.route) {
//            SamplingScreen(
//                onNavigateBack = { navController.popBackStack() }
//            )
//        }

        composable(Screen.Admin.route) {
            AdminScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBasketManagement = {
                    navController.navigate(Screen.BasketManagement.route)
                }
            )
        }

        composable(Screen.BasketManagement.route) {
            BasketManagementScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { uid ->
                    navController.navigate(Screen.BasketDetail.createRoute(uid))
                }
            )
        }

        composable(
            route = Screen.BasketDetail.route,
            arguments = listOf(
                navArgument("uid") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("uid") ?: return@composable
            BasketDetailScreen(
                uid = uid,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

