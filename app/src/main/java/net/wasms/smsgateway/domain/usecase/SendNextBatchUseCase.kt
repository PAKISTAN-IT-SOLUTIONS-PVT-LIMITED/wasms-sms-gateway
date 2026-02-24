package net.wasms.smsgateway.domain.usecase

import kotlinx.coroutines.flow.first
import net.wasms.smsgateway.domain.model.DeviceConfig
import net.wasms.smsgateway.domain.model.SmsMessage
import net.wasms.smsgateway.domain.repository.DeviceRepository
import net.wasms.smsgateway.domain.repository.MessageRepository
import javax.inject.Inject

/**
 * Pulls the next batch of messages from the local queue and prepares them for sending.
 * Called by SmsSenderService in its main loop.
 */
class SendNextBatchUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val deviceRepository: DeviceRepository
) {
    data class BatchResult(
        val messages: List<SmsMessage>,
        val batchSize: Int
    )

    suspend operator fun invoke(): BatchResult {
        val config = deviceRepository.observeDeviceConfig().first()

        val messages = messageRepository.getNextBatch(config.batchSize)
        return BatchResult(messages, config.batchSize)
    }
}
