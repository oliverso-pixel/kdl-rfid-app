package com.kdl.rfidinventory.data.repository

//import com.kdl.rfidinventory.data.local.preferences.AuthPreferences
import com.kdl.rfidinventory.data.local.datastore.AuthDataStore
import com.kdl.rfidinventory.data.model.User
import com.kdl.rfidinventory.data.remote.api.AuthApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApiService: AuthApiService,
    private val authDataStore: AuthDataStore
) {

    val isLoggedInFlow: Flow<Boolean> = authDataStore.isLoggedInFlow
    val currentUserFlow: Flow<User?> = authDataStore.currentUserFlow

    suspend fun login(username: String, password: String): Result<User> =
        withContext(Dispatchers.IO) {
            try {
                val response = authApiService.login(username, password)
                val token = response.accessToken
                    ?: return@withContext Result.failure(Exception("Empty token"))

                val authHeader = "${response.tokenType} $token"
                val user = authApiService.getCurrentUser(authHeader)

                authDataStore.saveLoginData(token, response.tokenType, user)
                Timber.d("✅ Login success: ${user.username}")
                Result.success(user)
            } catch (e: Exception) {
                Timber.e(e, "❌ Login failed")
                Result.failure(e)
            }
        }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            authDataStore.clearLoginData()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 🔧 改為 suspend
    suspend fun isLoggedIn(): Boolean = authDataStore.isLoggedIn()

    suspend fun getCurrentUser(): User? = authDataStore.getCurrentUser()

    suspend fun refreshUser(): Result<User> = withContext(Dispatchers.IO) {
        try {
            val header = authDataStore.getAuthHeader()
                ?: return@withContext Result.failure(Exception("Not logged in"))
            val user = authApiService.getCurrentUser(header)
            authDataStore.updateUser(user)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

//    suspend fun login(username: String, password: String): Result<User> = withContext(Dispatchers.IO) {
//        try {
//            Timber.d("🔐 Attempting login for user: $username")
//
//            val loginResponse = authApiService.login(username, password)
//
//            val token = loginResponse.accessToken
//            if (token.isNullOrBlank()) {
//                throw IllegalStateException("Access token is null or empty")
//            }
//
//            Timber.d("✅ Login successful, token: ${token.take(20)}...")
//
//            val authHeader = "${loginResponse.tokenType} $token"
//            val user = authApiService.getCurrentUser(authHeader)
//
//            Timber.d("✅ User info fetched: ${user.username} (${user.role})")
//
//            authPreferences.saveLoginData(
//                accessToken = token,
//                tokenType = loginResponse.tokenType,
//                user = user
//            )
//
//            Result.success(user)
//        } catch (e: Exception) {
//            Timber.e(e, "❌ Login failed")
//            Result.failure(e)
//        }
//    }
//
//    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
//        try {
//            val authHeader = authPreferences.getAuthorizationHeader()
//
//            if (authHeader != null) {
//                try {
//                    val response = authApiService.logout(authHeader)
//                    Timber.d("✅ Logout API response: ${response.message}")
//                } catch (e: Exception) {
//                    Timber.w(e, "⚠️ Logout API call failed, clearing local data anyway")
//                }
//            }
//
//            authPreferences.clearLoginData()
//            Timber.d("🗑️ Logged out successfully")
//
//            Result.success(Unit)
//        } catch (e: Exception) {
//            Timber.e(e, "❌ Logout failed")
//            Result.failure(e)
//        }
//    }
//
//    fun isLoggedIn(): Boolean {
//        return authPreferences.isLoggedIn()
//    }
//
//    fun getCurrentUser(): User? {
//        return authPreferences.getCurrentUser()
//    }
//
//    suspend fun refreshUserInfo(): Result<User> = withContext(Dispatchers.IO) {
//        try {
//            val authHeader = authPreferences.getAuthorizationHeader()
//                ?: return@withContext Result.failure(Exception("Not logged in"))
//
//            val user = authApiService.getCurrentUser(authHeader)
//
//            val token = authPreferences.getAccessToken()!!
//            val tokenType = authPreferences.getTokenType()
//            authPreferences.saveLoginData(token, tokenType, user)
//
//            Result.success(user)
//        } catch (e: Exception) {
//            Timber.e(e, "Failed to refresh user info")
//            Result.failure(e)
//        }
//    }
}