package com.axis.marketinsights.data

import com.axis.marketinsights.data.dto.RawSeedSource
import com.axis.marketinsights.data.repository.MockResearchRepository
import com.axis.marketinsights.domain.util.RetryPolicy
import com.axis.marketinsights.domain.util.TransientNetworkException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MockResearchRepositoryTest {

    private val fastPolicy = RetryPolicy(maxAttempts = 3, initialDelayMillis = 1L, maxDelayMillis = 5L)

    @Test
    fun succeedsAndMapsEveryDtoToADomainModel_whenNothingFails() = runTest {
        val repo = MockResearchRepository(simulatedLatencyMillis = 0L, transientFailureRate = 0.0, retryPolicy = fastPolicy)
        val calls = repo.getResearchCalls()
        assertEquals(RawSeedSource.researchCalls.size, calls.size)
    }

    @Test
    fun exhaustsRetriesAndThrows_whenTheEndpointNeverRecovers() = runTest {
        val repo = MockResearchRepository(simulatedLatencyMillis = 0L, transientFailureRate = 1.0, retryPolicy = fastPolicy)
        assertFailsWith<TransientNetworkException> { repo.getResearchCalls() }
    }
}
