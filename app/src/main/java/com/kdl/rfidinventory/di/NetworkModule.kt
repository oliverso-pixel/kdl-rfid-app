package com.kdl.rfidinventory.di

import com.kdl.rfidinventory.data.remote.api.*
import com.kdl.rfidinventory.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
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

    // Mock API Services (後端未完成前使用)
    @Provides
    @Singleton
    fun provideProductionApi(retrofit: Retrofit): ProductionApi {
        return retrofit.create(ProductionApi::class.java)
    }

    @Provides
    @Singleton
    fun provideWarehouseApi(retrofit: Retrofit): WarehouseApi {
        return retrofit.create(WarehouseApi::class.java)
    }

    @Provides
    @Singleton
    fun provideShippingApi(retrofit: Retrofit): ShippingApi {
        return retrofit.create(ShippingApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAdminApi(retrofit: Retrofit): AdminApi {
        return retrofit.create(AdminApi::class.java)
    }
}