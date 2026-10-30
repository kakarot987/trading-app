package com.trading.historical_service.paper

/**
 * Pure state machine for simulated spot orders. A fill may use only a strictly
 * future tick. No exchange, funds or database side effects are performed here.
 */
data class PaperOrderState(
    val request: PaperOrderRequest,
    val submittedAtMs: Long,
    val status: PaperOrderStatus = PaperOrderStatus.NEW,
    val fill: PaperFill? = null,
    val rejectionReason: String? = null
) {
    init {
        require(submittedAtMs >= 0)
        require((status == PaperOrderStatus.FILLED) == (fill != null))
        require((status == PaperOrderStatus.REJECTED) == (rejectionReason != null))
        require(fill == null || (fill.clientOrderId == request.clientOrderId &&
            fill.symbol == request.symbol && fill.side == request.side &&
            fill.quantity == request.quantity && fill.filledAtMs > submittedAtMs))
    }
}

/** Single-use state transitions: terminal states are immutable. */
object PaperOrderLifecycle {
    fun fill(state: PaperOrderState, tick: PaperMarketTick,
             costs: PaperExecutionCosts = PaperExecutionCosts()): PaperOrderState {
        require(state.status == PaperOrderStatus.NEW) { "Only NEW orders can fill" }
        return state.copy(status = PaperOrderStatus.FILLED,
            fill = PaperFillEngine.fill(state.request, state.submittedAtMs, tick, costs))
    }

    fun cancel(state: PaperOrderState): PaperOrderState {
        require(state.status == PaperOrderStatus.NEW) { "Only NEW orders can cancel" }
        return state.copy(status = PaperOrderStatus.CANCELLED)
    }

    fun reject(state: PaperOrderState, reason: String): PaperOrderState {
        require(state.status == PaperOrderStatus.NEW) { "Only NEW orders can reject" }
        require(reason.isNotBlank() && reason.length <= 256)
        return state.copy(status = PaperOrderStatus.REJECTED, rejectionReason = reason)
    }
}
