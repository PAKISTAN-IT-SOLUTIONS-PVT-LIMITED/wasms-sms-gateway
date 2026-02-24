package net.wasms.smsgateway.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import java.util.UUID

@Entity(
    tableName = "delivery_reports",
    foreignKeys = [
        ForeignKey(
            entity = SmsMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["message_id"]),
        Index(value = ["reported_to_server"])
    ]
)
data class DeliveryReportEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "sim_slot")
    val simSlot: Int,

    @ColumnInfo(name = "result_code")
    val resultCode: Int,

    @ColumnInfo(name = "error_message")
    val errorMessage: String?,

    @ColumnInfo(name = "reported_to_server", defaultValue = "0")
    val reportedToServer: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant
)
