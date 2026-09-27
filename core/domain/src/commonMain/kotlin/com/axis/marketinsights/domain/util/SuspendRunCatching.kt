package com.axis.marketinsights.domain.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like the stdlib runCatching, but for suspend code, and it rethrows CancellationException
 * instead of swallowing it. Catching cancellation by accident breaks structured concurrency -
 * a cancelled coroutine ends up looking like a failure instead of a cancellation.
 */
inline fun <T> suspendRunCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
