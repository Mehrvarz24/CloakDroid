package com.cloakdroid.di

import android.content.Context
import androidx.room.Room
import com.cloakdroid.data.local.AppDatabase
import com.cloakdroid.data.local.ProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApplicationContext(
        @ApplicationContext context: Context
    ): Context = context

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "cloakdroid.db"
    ).build()

    @Provides
    @Singleton
    fun provideProfileDao(database: AppDatabase): ProfileDao = database.profileDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    @IoScope
    fun provideExternalScope(): CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )
}
