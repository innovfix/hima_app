package com.gmwapp.hima.agora.telecom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class IncomingCallRegistrationGateTest {

    @Test
    fun sameCallCanBeClaimedOnlyOnceInsideTtl() {
        val gate = IncomingCallRegistrationGate(claimTtlMs = 60_000L)

        assertTrue(gate.tryClaim("call:42", nowElapsedMs = 1_000L))
        assertFalse(gate.tryClaim("call:42", nowElapsedMs = 1_001L))
    }

    @Test
    fun differentCallsDoNotBlockEachOther() {
        val gate = IncomingCallRegistrationGate(claimTtlMs = 60_000L)

        assertTrue(gate.tryClaim("call:42", nowElapsedMs = 1_000L))
        assertTrue(gate.tryClaim("call:43", nowElapsedMs = 1_001L))
    }

    @Test
    fun expiredClaimAllowsARepeatRegistration() {
        val gate = IncomingCallRegistrationGate(claimTtlMs = 60_000L)

        assertTrue(gate.tryClaim("call:42", nowElapsedMs = 1_000L))
        assertTrue(gate.tryClaim("call:42", nowElapsedMs = 61_000L))
    }

    @Test
    fun failedDispatchCanReleaseItsClaimForRetry() {
        val gate = IncomingCallRegistrationGate(claimTtlMs = 60_000L)

        assertTrue(gate.tryClaim("call:42", nowElapsedMs = 1_000L))
        gate.release("call:42")
        assertTrue(gate.tryClaim("call:42", nowElapsedMs = 1_001L))
    }

    @Test
    fun concurrentDeliveriesProduceExactlyOneWinner() {
        val gate = IncomingCallRegistrationGate(claimTtlMs = 60_000L)
        val ready = CountDownLatch(32)
        val start = CountDownLatch(1)
        val winners = AtomicInteger(0)
        val threads = List(32) {
            thread(start = false) {
                ready.countDown()
                start.await()
                if (gate.tryClaim("call:42", nowElapsedMs = 1_000L)) {
                    winners.incrementAndGet()
                }
            }
        }

        threads.forEach(Thread::start)
        ready.await()
        start.countDown()
        threads.forEach(Thread::join)

        assertEquals(1, winners.get())
    }
}
