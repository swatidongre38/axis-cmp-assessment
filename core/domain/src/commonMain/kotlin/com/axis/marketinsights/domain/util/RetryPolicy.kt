package com.axis.marketinsights.domain.util

import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlin.random.Random

/**
 * A network failure that's worth retrying - a timeout, a 5xx, a dropped connection. NOT for
 * validation errors or anything the server rejected on purpose; those should fail immediately,
 * retrying them just delays telling the user something they need to fix themselves.
 */
class TransientNetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMillis: Long = 200L,
    val maxDelayMillis: Long = 2_000L,
    val backoffFactor: Double = 2.0,
    /** +/- this fraction of the delay, randomized, so a batch of clients retrying after the
     * same outage don't all hammer the server on the exact same schedule. */
    val jitterFraction: Double = 0.2,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
        require(initialDelayMillis >= 0) { "initialDelayMillis can't be negative" }
    }
}

/**
 * Retries [block] with exponential backoff + jitter. [isRetryable] decides which failures are
 * worth retrying at all - defaults to "only TransientNetworkException", since blindly retrying
 * everything (including validation failures) wastes time telling the user nothing new.
 *
 * CancellationException always propagates immediately and is never retried - swallowing it
 * here would break structured concurrency the same way it would anywhere else.
 */
suspend fun <T> retryWithBackoff(
    policy: RetryPolicy = RetryPolicy(),
    isRetryable: (Throwable) -> Boolean = { it is TransientNetworkException },
    block: suspend (attempt: Int) -> T,
): T {
    var attempt = 0
    var delayMillis = policy.initialDelayMillis
    while (true) {
        attempt++
        try {
            return block(attempt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (attempt >= policy.maxAttempts || !isRetryable(e)) throw e
            val jitterRange = delayMillis * policy.jitterFraction
            val jittered = (delayMillis + Random.nextDouble(-jitterRange, jitterRange))
                .toLong()
                .coerceAtLeast(0)
            delay(jittered)
            delayMillis = min((delayMillis * policy.backoffFactor).toLong(), policy.maxDelayMillis)
        }
    }
}
