package com.axis.marketinsights.domain.order

import kotlin.random.Random

/**
 * Generated ONCE per order attempt - meaning once per user tap on Submit, not once per HTTP
 * call. If PlaceOrderUseCase retries after a timeout, every retry reuses the SAME key, so
 * the server can recognize "this is the same order I already saw" instead of creating a
 * second position. Random 128 bits is enough that collision risk is a non-issue; there's no
 * need for this to be a formal UUID.
 */
fun newIdempotencyKey(): String =
    Random.nextLong().toULong().toString(16).padStart(16, '0') +
        Random.nextLong().toULong().toString(16).padStart(16, '0')
