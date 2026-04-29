package com.p2pvoice

import android.content.Context
import com.p2pvoice.data.AppDatabase
import com.p2pvoice.data.CallLogDao
import com.p2pvoice.signaling.SupabaseSignalingManager
import com.p2pvoice.utils.ProximitySensorManager
import com.p2pvoice.utils.UserIdManager
import com.p2pvoice.webrtc.WebRTCManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
object AppModule {

    @Provides
    @Singleton
    fun provideUserIdManager(
        @ApplicationContext context: Context
    ): UserIdManager = UserIdManager(context)

    @Provides
    @Singleton
    fun provideSupabaseSignalingManager(): SupabaseSignalingManager =
        SupabaseSignalingManager()

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = AppDatabase.getDatabase(context)

    @Provides
    @Singleton
    fun provideCallLogDao(
        database: AppDatabase
    ): CallLogDao = database.callLogDao()

    @Provides
    @Singleton
    fun provideWebRTCManager(
        @ApplicationContext context: Context,
        signalingManager: SupabaseSignalingManager,
        proximitySensorManager: ProximitySensorManager,
        callLogDao: CallLogDao
    ): WebRTCManager = WebRTCManager(context, signalingManager, proximitySensorManager, callLogDao)
}
