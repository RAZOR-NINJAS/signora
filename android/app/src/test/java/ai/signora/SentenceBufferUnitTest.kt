package ai.signora

import ai.signora.nlp.GlossToken
import ai.signora.nlp.SentenceBuffer
import org.junit.Assert.*
import org.junit.Test

class SentenceBufferUnitTest {

    @Test
    fun testPushAndPeekTokens() {
        val completed = mutableListOf<List<GlossToken>>()
        val buffer = SentenceBuffer(sentencePauseMs = 1500L) { completed.add(it) }

        buffer.push(GlossToken("HELLO", timestamp = 1000L))
        buffer.push(GlossToken("WORLD", timestamp = 1100L))

        assertEquals(2, buffer.peek().size)
        assertEquals("HELLO", buffer.peek()[0].label)
        assertEquals("WORLD", buffer.peek()[1].label)
        assertEquals(0, completed.size)
    }

    @Test
    fun testTimeBoundaryFiring() {
        val completed = mutableListOf<List<GlossToken>>()
        val buffer = SentenceBuffer(sentencePauseMs = 1500L) { completed.add(it) }

        buffer.push(GlossToken("I", timestamp = 1000L))
        buffer.push(GlossToken("GO", timestamp = 1200L))

        buffer.tick(2000L) // 800ms elapsed
        assertEquals(0, completed.size)
        assertFalse(buffer.isFinalizedState)

        buffer.tick(2750L) // 1550ms elapsed >= 1500ms
        assertEquals(1, completed.size)
        assertTrue(buffer.isFinalizedState)
        assertEquals(listOf("I", "GO"), completed[0].map { it.label })
    }

    @Test
    fun testExplicitTerminalMarkerFiresInstantly() {
        val completed = mutableListOf<List<GlossToken>>()
        val buffer = SentenceBuffer(sentencePauseMs = 1500L) { completed.add(it) }

        buffer.push(GlossToken("HELP", timestamp = 1000L))
        assertEquals(0, completed.size)

        buffer.push(GlossToken("PERIOD", timestamp = 1050L))
        assertEquals(1, completed.size)
        assertTrue(buffer.isFinalizedState)
        assertEquals(listOf("HELP", "PERIOD"), completed[0].map { it.label })
    }

    @Test
    fun testPopRemovesLastToken() {
        val completed = mutableListOf<List<GlossToken>>()
        val buffer = SentenceBuffer(sentencePauseMs = 1500L) { completed.add(it) }

        buffer.push(GlossToken("ONE", timestamp = 1000L))
        buffer.push(GlossToken("TWO", timestamp = 1100L))

        val popped = buffer.pop()
        assertNotNull(popped)
        assertEquals("TWO", popped?.label)
        assertEquals(1, buffer.peek().size)
        assertEquals("ONE", buffer.peek()[0].label)

        buffer.pop()
        assertNull(buffer.pop()) // Empty pop returns null
    }

    @Test
    fun testClearResetsBuffer() {
        val completed = mutableListOf<List<GlossToken>>()
        val buffer = SentenceBuffer(sentencePauseMs = 1500L) { completed.add(it) }

        buffer.push(GlossToken("HELLO", timestamp = 1000L))
        buffer.clear()
        assertTrue(buffer.peek().isEmpty())
        assertFalse(buffer.isFinalizedState)
        assertEquals(0, completed.size)
    }

    @Test
    fun testFlushManualFiresOnce() {
        val completed = mutableListOf<List<GlossToken>>()
        val buffer = SentenceBuffer(sentencePauseMs = 1500L) { completed.add(it) }

        buffer.push(GlossToken("TEST", timestamp = 1000L))
        buffer.flush()
        assertEquals(1, completed.size)

        buffer.flush() // Second flush must not re-trigger
        assertEquals(1, completed.size)
    }

    @Test
    fun testPushAfterFinalizedStartsNewSentence() {
        val completed = mutableListOf<List<GlossToken>>()
        val buffer = SentenceBuffer(sentencePauseMs = 1500L) { completed.add(it) }

        buffer.push(GlossToken("FIRST", timestamp = 1000L))
        buffer.flush()
        assertEquals(1, completed.size)

        // Pushing new token clears old sentence
        buffer.push(GlossToken("SECOND", timestamp = 3000L))
        assertEquals(1, buffer.peek().size)
        assertEquals("SECOND", buffer.peek()[0].label)
        assertFalse(buffer.isFinalizedState)
    }

    @Test
    fun testPopToEmptyResetsTimer() {
        val completed = mutableListOf<List<GlossToken>>()
        val buffer = SentenceBuffer(sentencePauseMs = 1500L) { completed.add(it) }

        buffer.push(GlossToken("TEMP", timestamp = 1000L))
        buffer.pop()
        assertTrue(buffer.peek().isEmpty())

        // Tick after pause duration: should NOT fire because buffer was popped empty
        buffer.tick(3000L)
        assertEquals(0, completed.size)
    }
}
