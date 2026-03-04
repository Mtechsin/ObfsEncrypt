package com.obfs.encrypt.di

import android.app.NotificationManager
import android.content.Context
import com.obfs.encrypt.crypto.EncryptionHelper
import com.obfs.encrypt.data.AppDirectoryManager
import com.obfs.encrypt.data.SettingsRepository
import com.obfs.encrypt.security.BiometricAuthManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * AppModule for defining application-level dependencies.
 *
 * Why this approach:
 * Centralizes dependency provisioning so components like EncryptionHelper
 * can be easily mocked during testing or swapped with different implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideEncryptionHelper(): EncryptionHelper {
        return EncryptionHelper()
    }

    @Provides
    @Singleton
    fun provideAppDirectoryManager(
        @ApplicationContext context: Context
    ): AppDirectoryManager {
        return AppDirectoryManager(context)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context
    ): SettingsRepository {
        return SettingsRepository(context)
    }

    @Provides
    @Singleton
    fun provideNotificationManager(
        @ApplicationContext context: Context
    ): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @Provides
    @Singleton
    fun provideBiometricAuthManager(
        @ApplicationContext context: Context
    ): BiometricAuthManager {
        return BiometricAuthManager(context)
    }
}
