package net.wasms.smsgateway.data.local.db

import androidx.room.TypeConverter
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.wasms.smsgateway.domain.model.MessagePriority
import net.wasms.smsgateway.domain.model.SmsState

/**
 * Room type converters for non-primitive types used in the database.
 *
 * Handles conversion between:
 * - kotlinx.datetime.Instant <-> Long (epoch milliseconds)
 * - List<Int> <-> String (JSON array)
 * - SmsState <-> String (enum value)
 * - MessagePriority <-> Int (enum ordinal value)
 */
class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    // --- Instant <-> Long ---

    @TypeConverter
    fun fromInstant(value: Instant?): Long? {
        return value?.toEpochMilliseconds()
    }

    @TypeConverter
    fun toInstant(value: Long?): Instant? {
        return value?.let { Instant.fromEpochMilliseconds(it) }
    }

    // --- List<Int> <-> String (JSON) ---

    @TypeConverter
    fun fromIntList(value: List<Int>?): String? {
        return value?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toIntList(value: String?): List<Int>? {
        return value?.let { json.decodeFromString<List<Int>>(it) }
    }

    // --- SmsState <-> String ---

    @TypeConverter
    fun fromSmsState(value: SmsState?): String? {
        return value?.value
    }

    @TypeConverter
    fun toSmsState(value: String?): SmsState? {
        return value?.let { SmsState.fromValue(it) }
    }

    // --- MessagePriority <-> Int ---

    @TypeConverter
    fun fromMessagePriority(value: MessagePriority?): Int? {
        return value?.value
    }

    @TypeConverter
    fun toMessagePriority(value: Int?): MessagePriority? {
        return value?.let { MessagePriority.fromValue(it) }
    }
}
