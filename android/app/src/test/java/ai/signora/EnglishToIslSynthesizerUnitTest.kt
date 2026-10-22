package ai.signora

import ai.signora.nlp.EnglishToIslSynthesizer
import org.junit.Assert.*
import org.junit.Test

class EnglishToIslSynthesizerUnitTest {

    @Test
    fun testSvoToSovReordering() {
        val result = EnglishToIslSynthesizer.translate("I want water")
        assertEquals(listOf("I", "WATER", "WANT"), result.islGlosses)
        assertEquals("CHEST_CENTER", result.signCues[0].bodyAnchor) // I
        assertEquals("CHIN", result.signCues[1].bodyAnchor)         // WATER
        assertEquals("OPEN_SPACE", result.signCues[2].bodyAnchor)   // WANT
    }

    @Test
    fun testWhQuestionMovementToEnd() {
        val result = EnglishToIslSynthesizer.translate("Where is the hospital?")
        assertEquals(listOf("HOSPITAL", "WHERE"), result.islGlosses)
        assertEquals("CHEST_LEFT", result.signCues[0].bodyAnchor) // HOSPITAL
        assertEquals("OPEN_SPACE", result.signCues[1].bodyAnchor) // WHERE
    }

    @Test
    fun testWhQuestionWhatIsYourName() {
        val result = EnglishToIslSynthesizer.translate("What is your name?")
        assertEquals(listOf("YOUR", "NAME", "WHAT"), result.islGlosses)
        assertEquals("OPEN_SPACE", result.signCues[0].bodyAnchor)   // YOUR
        assertEquals("CHEST_CENTER", result.signCues[1].bodyAnchor) // NAME
        assertEquals("OPEN_SPACE", result.signCues[2].bodyAnchor)   // WHAT
    }

    @Test
    fun testTemporalFronting() {
        val result = EnglishToIslSynthesizer.translate("I will go to hospital tomorrow")
        assertEquals(listOf("TOMORROW", "I", "HOSPITAL", "GO"), result.islGlosses)
        assertEquals("CHIN", result.signCues[0].bodyAnchor)         // TOMORROW
        assertEquals("CHEST_CENTER", result.signCues[1].bodyAnchor) // I
        assertEquals("CHEST_LEFT", result.signCues[2].bodyAnchor)   // HOSPITAL
        assertEquals("OPEN_SPACE", result.signCues[3].bodyAnchor)   // GO
    }

    @Test
    fun testNegationPlacement() {
        val result = EnglishToIslSynthesizer.translate("I do not understand")
        assertEquals(listOf("I", "UNDERSTAND", "NOT"), result.islGlosses)
        assertEquals("CHIN", result.signCues[2].bodyAnchor) // NOT
    }

    @Test
    fun testContractionExpansion() {
        val result = EnglishToIslSynthesizer.translate("I can't come")
        assertEquals(listOf("I", "COME", "NOT"), result.islGlosses)
    }

    @Test
    fun testOutOfVocabularyFingerspellingFallback() {
        val result = EnglishToIslSynthesizer.translate("Meet Vaibhav")
        // "VAIBHAV" is not in fixed lexicon, so it should fall back to fingerspelling
        val vaibhavCue = result.signCues.find { it.gloss == "VAIBHAV" }
        assertNotNull("Expected cue for VAIBHAV", vaibhavCue)
        assertTrue("VAIBHAV should be marked fingerspelled", vaibhavCue!!.isFingerspelled)
        assertEquals(listOf("V", "A", "I", "B", "H", "A", "V"), vaibhavCue.fingerspellingLetters)
    }

    @Test
    fun testCopulaAndArticleSuppression() {
        val result = EnglishToIslSynthesizer.translate("The doctor is here")
        assertEquals(listOf("DOCTOR", "HERE"), result.islGlosses)
    }

    @Test
    fun testEmptyInputHandling() {
        val result = EnglishToIslSynthesizer.translate("   ")
        assertTrue(result.islGlosses.isEmpty())
        assertTrue(result.signCues.isEmpty())
    }
}
