package net.wasms.smsgateway.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import net.wasms.smsgateway.domain.model.ConnectionState
import net.wasms.smsgateway.domain.model.DeviceStatus
import net.wasms.smsgateway.domain.model.QueueStats
import net.wasms.smsgateway.domain.model.SimCard
import net.wasms.smsgateway.domain.repository.ConnectionRepository
import net.wasms.smsgateway.domain.repository.DeviceRepository
import net.wasms.smsgateway.domain.repository.MessageRepository
import javax.inject.Inject

/**
 * Combines all dashboard data streams into a single Flow for the Home screen.
 * Follows Agent 15's 3-layer hierarchy: glanceable data first.
 */
class ObserveDashboardUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val deviceRepository: DeviceRepository,
    private val connectionRepository: ConnectionRepository
) {
    data class DashboardState(
        val deviceStatus: DeviceStatus,
        val queueStats: QueueStats,
        val simCards: List<SimCard>,
        val connectionState: ConnectionState
    ) {
        val activeSim: SimCard? get() = simCards.firstOrNull { it.isActive && !it.isThrottled }
    }

    operator fun invoke(): Flow<DashboardState> {
        return combine(
            deviceRepository.observeDeviceStatus(),
            messageRepository.observeQueueStats(),
            deviceRepository.observeSimCards(),
            connectionRepository.observeConnectionState()
        ) { status, stats, sims, connection ->
            DashboardState(status, stats, sims, connection)
        }
    }
}
