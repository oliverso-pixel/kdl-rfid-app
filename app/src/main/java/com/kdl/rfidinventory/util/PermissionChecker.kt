// util/PermissionChecker.kt
package com.kdl.rfidinventory.util

import com.kdl.rfidinventory.data.model.User

object PermissionChecker {

    /**
     * 檢查用戶是否有指定權限
     */
    fun hasPermission(user: User?, permission: String): Boolean {
        if (user == null) return false

        // "*" 表示所有權限
        if (user.permissions.contains("*")) return true

        return user.permissions.contains(permission)
    }

    /**
     * 檢查用戶是否為管理員
     */
    fun isAdmin(user: User?): Boolean {
        return user?.role == "Admin"
    }

    /**
     * 預定義權限
     */
    object Permissions {
        const val PRODUCTION = "production"
        const val WAREHOUSE = "warehouse"
        const val SHIPPING = "shipping"
        const val ADMIN = "admin"
        const val BASKET_MANAGEMENT = "basket_management"
    }
}