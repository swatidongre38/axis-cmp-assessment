package com.axis.marketinsights.data.repository

import com.axis.marketinsights.data.dto.RawSeedSource
import com.axis.marketinsights.data.mapper.toDomain
import com.axis.marketinsights.domain.model.ResearchCall
import com.axis.marketinsights.domain.repository.ResearchRepository
import com.axis.marketinsights.domain.util.RetryPolicy
import com.axis.marketinsights.domain.util.TransientNetworkException
import com.axis.marketinsights.domain.util.retryWithBackoff
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Stands in for a real "GET /research-calls" call. Reads are safe to retry blindly (there's
 * no idempotency concern - re-fetching the same data twice does nothing harmful), so unlike
 * MockOrderRepository, the retry here lives in the data layer itself rather than being the
 * caller's decision. [transientFailureRate] simulates a flaky connection so that behaviour
 * is actually exercised, not just theoretical.
 */
class MockResearchRepository(
    private val simulatedLatencyMillis: Long = 600L,
    private val transientFailureRate: Double = 0.2,
    private val random: Random = Random.Default,
    private val retryPolicy: RetryPolicy = RetryPolicy(maxAttempts = 3, initialDelayMillis = 250L),
) : ResearchRepository {

    override suspend fun getResearchCalls(): List<ResearchCall> =
        retryWithBackoff(retryPolicy) {
            delay(simulatedLatencyMillis)
            if (random.nextDouble() < transientFailureRate) {
                throw TransientNetworkException("simulated research-endpoint timeout")
            }
            RawSeedSource.researchCalls.mapNotNull { it.toDomain() }
        }
}
