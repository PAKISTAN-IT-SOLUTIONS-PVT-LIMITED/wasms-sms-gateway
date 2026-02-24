package net.wasms.smsgateway.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.wasms.smsgateway.data.remote.websocket.ReverbWebSocketClient
import net.wasms.smsgateway.data.remote.websocket.ReverbWebSocketClientImpl
import net.wasms.smsgateway.data.repository.ConnectionRepositoryImpl
import net.wasms.smsgateway.data.repository.DeviceRepositoryImpl
import net.wasms.smsgateway.data.repository.MessageRepositoryImpl
import net.wasms.smsgateway.domain.repository.ConnectionRepository
import net.wasms.smsgateway.domain.repository.DeviceRepository
import net.wasms.smsgateway.domain.repository.MessageRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMessageRepository(
        impl: MessageRepositoryImpl
    ): MessageRepository

    @Binds
    @Singleton
    abstract fun bindDeviceRepository(
        impl: DeviceRepositoryImpl
    ): DeviceRepository

    @Binds
    @Singleton
    abstract fun bindConnectionRepository(
        impl: ConnectionRepositoryImpl
    ): ConnectionRepository

    @Binds
    @Singleton
    abstract fun bindReverbWebSocketClient(
        impl: ReverbWebSocketClientImpl
    ): ReverbWebSocketClient
}
