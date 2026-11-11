package com.trading.historical_service.evaluation

import com.trading.historical_service.paper.PaperWalletReadGuard
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
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
    private val inventoryRepository: PersistedPaperInventoryRepository,
    private val readGuard: PaperWalletReadGuard
) {
    @GetMapping("/{walletId}/evaluation")
    fun evaluation(
        @PathVariable walletId: UUID,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
        authorization: String = ""
    ): Mono<PaperEvaluationReport> =
        readGuard.mono(walletId, authorization,
            Mono.zip(activityRepository.summarize(walletId),
                inventoryRepository.summarize(walletId)).map { pair ->
                PaperEvaluationReports.assemble(pair.t1, realizedInventory = pair.t2)
            }
        )
}
