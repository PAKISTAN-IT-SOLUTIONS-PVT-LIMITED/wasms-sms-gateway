package net.wasms.smsgateway.domain.repository

import kotlinx.coroutines.flow.Flow
import net.wasms.smsgateway.domain.model.ConnectionState

interface ConnectionRepository {
    fun observeConnectionState(): Flow<ConnectionState>
    suspend fun connect()
    suspend fun disconnect()
    suspend fun reconnect()
    fun isConnected(): Boolean
}
