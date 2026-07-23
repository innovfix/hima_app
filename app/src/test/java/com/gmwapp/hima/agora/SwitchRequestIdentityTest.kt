package com.gmwapp.hima.agora

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwitchRequestIdentityTest {

    @Test
    fun `no contract is emitted before a valid root is captured`() {
        val identity = SwitchRequestIdentity { "request-1" }

        assertNull(identity.begin("video"))
        identity.captureRootCallId(0)
        assertNull(identity.begin("video"))
    }

    @Test
    fun `root remains immutable after switch call ids replace the active leg`() {
        val identity = SwitchRequestIdentity { "request-1" }
        identity.captureRootCallId(100)
        identity.captureRootCallId(200)

        assertEquals(100, identity.begin("video")?.rootCallId)
    }

    @Test
    fun `same target retry reuses the idempotency key`() {
        var sequence = 0
        val identity = SwitchRequestIdentity { "request-${++sequence}" }
        identity.captureRootCallId(100)

        val first = identity.begin("video")
        val retry = identity.begin("video")

        assertEquals(first, retry)
        assertEquals(1, sequence)
    }

    @Test
    fun `opposite target creates a new switch intent`() {
        var sequence = 0
        val identity = SwitchRequestIdentity { "request-${++sequence}" }
        identity.captureRootCallId(100)

        val video = identity.begin("video")
        val audio = identity.begin("audio")

        assertNotEquals(video?.requestId, audio?.requestId)
        assertEquals(2, sequence)
    }

    @Test
    fun `terminal completion allows a later same-target request to use a new key`() {
        var sequence = 0
        val identity = SwitchRequestIdentity { "request-${++sequence}" }
        identity.captureRootCallId(100)

        val first = identity.begin("video")
        identity.complete("video")
        val later = identity.begin("video")

        assertNotEquals(first?.requestId, later?.requestId)
        assertEquals(2, sequence)
    }

    @Test
    fun `stale completion for another target does not clear current request`() {
        var sequence = 0
        val identity = SwitchRequestIdentity { "request-${++sequence}" }
        identity.captureRootCallId(100)

        val video = identity.begin("video")
        identity.complete("audio")
        val retry = identity.begin("video")

        assertEquals(video, retry)
        assertEquals(1, sequence)
    }

    @Test
    fun `unsupported target is rejected without consuming a key`() {
        var sequence = 0
        val identity = SwitchRequestIdentity { "request-${++sequence}" }
        identity.captureRootCallId(100)

        assertNull(identity.begin("screen"))
        assertEquals(0, sequence)
    }
}
