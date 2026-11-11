package com.trading.historical_service.paper

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Single-wallet, bearer capability for a private paper-trading demo.
 * No external identity or account ownership is asserted.
 * Only the SHA-256 fingerprint is persisted. A newly minted token is shown once.
 */
object PaperWalletTokens {
    private val random = SecureRandom()
    private val pattern = Regex("^pw_[A-Za-z0-9_-]{43}$")

    fun generate(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return "pw_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun isWellFormed(token: String): Boolean = pattern.matches(token)

    fun sha256(token: String): String {
        require(isWellFormed(token)) { "Invalid paper wallet credential" }
        return MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(StandardCharsets.US_ASCII))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
