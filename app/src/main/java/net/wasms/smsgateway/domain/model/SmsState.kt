package net.wasms.smsgateway.domain.model

/**
 * Message lifecycle states matching the server-side SmsState enum.
 * State machine: CREATED → QUEUED → ASSIGNED → DISPATCHED → SENT → DELIVERED/FAILED
 */
enum class SmsState(val value: String) {
    CREATED("created"),
    QUEUED("queued"),
    ASSIGNED_DEVICE("assigned_device"),
    DISPATCHED_TO_SIM("dispatched_to_sim"),
    SENT("sent"),
    DELIVERED("delivered"),
    FAILED_ATTEMPT("failed_attempt"),
    FAILED_PERMANENT("failed_permanent"),
    EXPIRED("expired"),
    REJECTED("rejected"),
    CANCELLED("cancelled");

    val isTerminal: Boolean
        get() = this in setOf(DELIVERED, FAILED_PERMANENT, EXPIRED, REJECTED, CANCELLED)

    val isPending: Boolean
        get() = this in setOf(CREATED, QUEUED, ASSIGNED_DEVICE)

    val isInFlight: Boolean
        get() = this in setOf(DISPATCHED_TO_SIM, SENT)

    companion object {
        fun fromValue(value: String): SmsState =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown SmsState: $value")
    }
}
