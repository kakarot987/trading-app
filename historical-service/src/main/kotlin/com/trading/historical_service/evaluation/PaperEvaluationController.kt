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
class PaperEvaluationController(private val activityRepository: PersistedPaperActivityRepository) {
    @GetMapping("/{walletId}/evaluation")
    fun evaluation(@PathVariable walletId: UUID): Mono<PaperEvaluationReport> =
        activityRepository.summarize(walletId).map(PaperEvaluationReports::assemble)
}
