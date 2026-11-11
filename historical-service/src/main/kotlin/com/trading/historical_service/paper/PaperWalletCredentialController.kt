package com.trading.historical_service.paper

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.util.UUID

data class RotatedPaperWalletCredentialResponse(val walletToken: String)

/**
 * Credential lifecycle is intentionally independent of paper-order mutation
 * flags. HTTP filter checks wallet-scoped possession first; SQL rechecks the
 * SAME current secret at the moment of update to avoid rotation/revoke races.
 *
 * No password reset, identity-based recovery or bootstrap reassignment.
 */
@RestController
@RequestMapping("/api/v1/paper/wallets")
class PaperWalletCredentialController(private val lifecycle: PaperWalletCredentialLifecycle) {
    private fun bearer(header: String): String {
        val token = if (header.startsWith("Bearer ")) header.removePrefix("Bearer ") else ""
        if (!PaperWalletTokens.isWellFormed(token)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid wallet credential")
        }
        return token
    }

    @PostMapping("/{walletId}/credential/rotate")
    fun rotate(
        @PathVariable walletId: UUID,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String
    ): Mono<RotatedPaperWalletCredentialResponse> =
        lifecycle.rotate(walletId, bearer(authorization))
            .map(::RotatedPaperWalletCredentialResponse)
            .switchIfEmpty(Mono.error(ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Wallet credential is no longer active"
            )))

    @PostMapping("/{walletId}/credential/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(
        @PathVariable walletId: UUID,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String
    ): Mono<Void> =
        lifecycle.revoke(walletId, bearer(authorization)).flatMap { changed ->
            if (changed) Mono.empty<Void>() else Mono.error(ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Wallet credential is no longer active"
            ))
        }
}
