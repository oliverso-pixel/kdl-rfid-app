package com.kdl.rfidinventory.di

import android.content.Context
import com.kdl.rfidinventory.data.device.DeviceInfoProvider
import com.kdl.rfidinventory.data.local.preferences.AuthPreferences
import com.kdl.rfidinventory.data.local.preferences.PreferencesManager
import com.kdl.rfidinventory.data.remote.api.ApiService
import com.kdl.rfidinventory.data.remote.api.AuthApiService
import com.kdl.rfidinventory.data.remote.api.DeviceApi
import com.kdl.rfidinventory.data.remote.api.LoadingApiService
import com.kdl.rfidinventory.data.remote.websocket.WebSocketManager
import com.kdl.rfidinventory.data.repository.DeviceRepository
import com.kdl.rfidinventory.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authPreferences: AuthPreferences): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val original = chain.request()
                val authHeader = authPreferences.getAuthorizationHeader()

                val request = if (authHeader != null) {
                    original.newBuilder()
                        .header("Authorization", authHeader)
                        .build()
                } else {
                    original
                }

                chain.proceed(request)
            }
            .connectTimeout(Constants.API_TIMEOUT, TimeUnit.MILLISECONDS)
            .readTimeout(Constants.API_TIMEOUT, TimeUnit.MILLISECONDS)
            .writeTimeout(Constants.API_TIMEOUT, TimeUnit.MILLISECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService {
        return retrofit.create(AuthApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideLoadingApiService(retrofit: Retrofit): LoadingApiService {
        return retrofit.create(LoadingApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideDeviceApi(retrofit: Retrofit): DeviceApi {
        return retrofit.create(DeviceApi::class.java)
    }

    @Provides
    @Singleton
    fun providePreferencesManager(
        @ApplicationContext context: Context
    ): PreferencesManager {
        return PreferencesManager(context)
    }

    @Provides
    @Singleton
    fun provideDeviceInfoProvider(
        @ApplicationContext context: Context
    ): DeviceInfoProvider {
        return DeviceInfoProvider(context)
    }

    @Provides
    @Singleton
    fun provideDeviceRepository(
        deviceApi: DeviceApi,
        deviceInfoProvider: DeviceInfoProvider
    ): DeviceRepository {
        return DeviceRepository(deviceApi, deviceInfoProvider)
    }

    @Provides
    @Singleton
    fun provideWebSocketManager(
        @ApplicationContext context: Context,
        deviceRepository: DeviceRepository,
        preferencesManager: PreferencesManager
    ): WebSocketManager {
        return WebSocketManager(context, deviceRepository, preferencesManager)
    }
}