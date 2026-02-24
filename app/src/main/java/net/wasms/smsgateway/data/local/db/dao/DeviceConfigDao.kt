package net.wasms.smsgateway.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import net.wasms.smsgateway.data.local.model.DeviceConfigEntity

@Dao
interface DeviceConfigDao {

    @Query("SELECT * FROM device_config WHERE id = 1")
    fun observe(): Flow<DeviceConfigEntity?>

    @Query("SELECT * FROM device_config WHERE id = 1")
    suspend fun get(): DeviceConfigEntity?

    @Upsert
    suspend fun upsert(config: DeviceConfigEntity)
}
