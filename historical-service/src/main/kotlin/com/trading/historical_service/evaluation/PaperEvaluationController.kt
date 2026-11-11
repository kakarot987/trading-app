package com.trading.historical_service.evaluation

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Read-only paper activity; wallet IDs are NOT authentication credentials.
 * Keep local/private until access control exists. No market marks or
 * hypothetical performance values are fabricated.
 */
@RestController
@RequestMapping("/api/v1/paper/wallets")
class PaperEvaluationController(
    private val activityRepository: PersistedPaperActivityRepository,
    private val inventoryRepository: PersistedPaperInventoryRepository
) {
    @GetMapping("/{walletId}/evaluation")
    fun evaluation(@PathVariable walletId: UUID): Mono<PaperEvaluationReport> =
        Mono.zip(activityRepository.summarize(walletId),
            inventoryRepository.summarize(walletId)).map { pair ->
            PaperEvaluationReports.assemble(pair.t1, realizedInventory = pair.t2)
        }
}
