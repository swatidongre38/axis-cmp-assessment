package com.axis.marketinsights.domain

import com.axis.marketinsights.domain.util.RetryPolicy
import com.axis.marketinsights.domain.util.TransientNetworkException
import com.axis.marketinsights.domain.util.retryWithBackoff
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RetryPolicyTest {

    private val fastPolicy = RetryPolicy(maxAttempts = 3, initialDelayMillis = 10L, maxDelayMillis = 50L)

    @Test
    fun succeedsOnFirstTryWithoutRetrying() = runTest {
        var calls = 0
        val result = retryWithBackoff(fastPolicy) { calls++; "ok" }
        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test
    fun retriesTransientFailuresAndEventuallySucceeds() = runTest {
        var calls = 0
        val result = retryWithBackoff(fastPolicy) { attempt ->
            calls++
            if (attempt < 3) throw TransientNetworkException("simulated timeout")
            "ok on attempt $attempt"
        }
        assertEquals("ok on attempt 3", result)
        assertEquals(3, calls)
    }

    @Test
    fun givesUpAfterMaxAttemptsAndRethrowsTheLastError() = runTest {
        var calls = 0
        assertFailsWith<TransientNetworkException> {
            retryWithBackoff(fastPolicy) { calls++; throw TransientNetworkException("still down") }
        }
        assertEquals(fastPolicy.maxAttempts, calls)
    }

    @Test
    fun nonRetryableErrorsFailImmediately_noRetryAtAll() = runTest {
        var calls = 0
        assertFailsWith<IllegalStateException> {
            retryWithBackoff(fastPolicy) { calls++; throw IllegalStateException("not a network problem") }
        }
        assertEquals(1, calls) // never retried - the default isRetryable only matches TransientNetworkException
    }

    @Test
    fun cancellationAlwaysPropagatesImmediately() = runTest {
        var calls = 0
        assertFailsWith<CancellationException> {
            retryWithBackoff(fastPolicy) { calls++; throw CancellationException("scope cancelled") }
        }
        assertEquals(1, calls)
    }
}
