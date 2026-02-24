package net.wasms.smsgateway.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.datetime.Instant
import net.wasms.smsgateway.data.local.model.DeliveryReportEntity

@Dao
interface DeliveryReportDao {

    @Query(
        """
        SELECT * FROM delivery_reports
        WHERE reported_to_server = 0
        ORDER BY created_at ASC
        LIMIT :limit
        """
    )
    suspend fun getUnreported(limit: Int): List<DeliveryReportEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: DeliveryReportEntity)

    @Query("UPDATE delivery_reports SET reported_to_server = 1 WHERE id IN (:ids)")
    suspend fun markReported(ids: List<String>)

    @Query("DELETE FROM delivery_reports WHERE created_at < :before")
    suspend fun deleteOlderThan(before: Instant)
}
