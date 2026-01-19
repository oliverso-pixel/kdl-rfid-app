// data/local/preferences/AuthPreferences.kt
package com.kdl.rfidinventory.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.kdl.rfidinventory.data.model.User
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_TOKEN_TYPE = "token_type"
        private const val KEY_USER_DATA = "user_data"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }

    fun saveLoginData(
        accessToken: String,
        tokenType: String,
        user: User
    ) {
        try {
            sharedPreferences.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_TOKEN_TYPE, tokenType)
                .putString(KEY_USER_DATA, gson.toJson(user))
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .apply()
            Timber.d("✅ Login data saved")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save login data")
        }
    }

    fun getAccessToken(): String? {
        return sharedPreferences.getString(KEY_ACCESS_TOKEN, null)
    }

    fun getTokenType(): String {
        return sharedPreferences.getString(KEY_TOKEN_TYPE, "Bearer") ?: "Bearer"
    }

    fun getAuthorizationHeader(): String? {
        val token = getAccessToken()
        val tokenType = getTokenType()
        return if (token != null) "$tokenType $token" else null
    }

    fun getCurrentUser(): User? {
        val userData = sharedPreferences.getString(KEY_USER_DATA, null) ?: return null
        return try {
            gson.fromJson(userData, User::class.java)
        } catch (e: Exception) {
            Timber.e(e, "Failed to decode user data")
            null
        }
    }

    fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false) &&
                getAccessToken() != null
    }

    fun clearLoginData() {
        try {
            sharedPreferences.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_TOKEN_TYPE)
                .remove(KEY_USER_DATA)
                .putBoolean(KEY_IS_LOGGED_IN, false)
                .apply()
            Timber.d("🗑️ Login data cleared")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear login data")
        }
    }
}