package com.gmwapp.hima.agora

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingCallTeardownPolicyTest {

    @Test
    fun exactUnacceptedPendingRingCanBeClaimed() {
        assertTrue(
            IncomingCallTeardownPolicy.canClaim(
                incomingCallPending = true,
                currentSenderId = 11,
                currentCallId = 42,
                expectedSenderId = 11,
                expectedCallId = 42,
                acceptedForExpectedSender = false
            )
        )
    }

    @Test
    fun acceptedRingCannotBeClaimed() {
        assertFalse(
            IncomingCallTeardownPolicy.canClaim(
                incomingCallPending = true,
                currentSenderId = 11,
                currentCallId = 42,
                expectedSenderId = 11,
                expectedCallId = 42,
                acceptedForExpectedSender = true
            )
        )
    }

    @Test
    fun newerCallCannotBeClearedByOlderServiceCallback() {
        assertFalse(
            IncomingCallTeardownPolicy.canClaim(
                incomingCallPending = true,
                currentSenderId = 11,
                currentCallId = 43,
                expectedSenderId = 11,
                expectedCallId = 42,
                acceptedForExpectedSender = false
            )
        )
    }

    @Test
    fun differentSenderCannotBeCleared() {
        assertFalse(
            IncomingCallTeardownPolicy.canClaim(
                incomingCallPending = true,
                currentSenderId = 12,
                currentCallId = 42,
                expectedSenderId = 11,
                expectedCallId = 42,
                acceptedForExpectedSender = false
            )
        )
    }

    @Test
    fun alreadyClearedRingCannotBeClaimedAgain() {
        assertFalse(
            IncomingCallTeardownPolicy.canClaim(
                incomingCallPending = false,
                currentSenderId = 11,
                currentCallId = 42,
                expectedSenderId = 11,
                expectedCallId = 42,
                acceptedForExpectedSender = false
            )
        )
    }
}
