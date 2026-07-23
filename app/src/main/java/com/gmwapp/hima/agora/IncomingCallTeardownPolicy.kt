package com.gmwapp.hima.agora

/**
 * Pure identity policy for abandoning an unaccepted incoming ring.
 *
 * A task-removal callback can race a newer ring or an Accept action. Teardown is
 * therefore allowed only when the still-pending app identity exactly matches the
 * service payload and that ring has not been accepted.
 */
internal object IncomingCallTeardownPolicy {

    fun identityMatches(
        currentSenderId: Int?,
        currentCallId: Int?,
        expectedSenderId: Int,
        expectedCallId: Int
    ): Boolean =
        expectedSenderId > 0 &&
            expectedCallId > 0 &&
            currentSenderId == expectedSenderId &&
            currentCallId == expectedCallId

    fun canClaim(
        incomingCallPending: Boolean,
        currentSenderId: Int?,
        currentCallId: Int?,
        expectedSenderId: Int,
        expectedCallId: Int,
        acceptedForExpectedSender: Boolean
    ): Boolean =
        incomingCallPending &&
            !acceptedForExpectedSender &&
            identityMatches(
                currentSenderId = currentSenderId,
                currentCallId = currentCallId,
                expectedSenderId = expectedSenderId,
                expectedCallId = expectedCallId
            )
}
