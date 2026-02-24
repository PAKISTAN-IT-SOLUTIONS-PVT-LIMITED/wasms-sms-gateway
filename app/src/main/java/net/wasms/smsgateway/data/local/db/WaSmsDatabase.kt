package net.wasms.smsgateway.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.wasms.smsgateway.data.local.db.dao.DeliveryReportDao
import net.wasms.smsgateway.data.local.db.dao.DeviceConfigDao
import net.wasms.smsgateway.data.local.db.dao.SimCardDao
import net.wasms.smsgateway.data.local.db.dao.SmsMessageDao
import net.wasms.smsgateway.data.local.model.DeliveryReportEntity
import net.wasms.smsgateway.data.local.model.DeviceConfigEntity
import net.wasms.smsgateway.data.local.model.SimCardEntity
import net.wasms.smsgateway.data.local.model.SmsMessageEntity

@Database(
    entities = [
        SmsMessageEntity::class,
        SimCardEntity::class,
        DeviceConfigEntity::class,
        DeliveryReportEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class WaSmsDatabase : RoomDatabase() {

    abstract fun smsMessageDao(): SmsMessageDao
    abstract fun simCardDao(): SimCardDao
    abstract fun deviceConfigDao(): DeviceConfigDao
    abstract fun deliveryReportDao(): DeliveryReportDao

    companion object {
        const val DATABASE_NAME = "wasms_gateway.db"
    }
}
