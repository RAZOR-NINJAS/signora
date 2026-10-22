package ai.signora

import ai.signora.tts.SpeechManager
import org.junit.Assert.*
import org.junit.Test

class SpeechManagerUnitTest {

    @Test
    fun testNormalizeFingerspellingAlphabets() {
        assertEquals("A", SpeechManager.normalizeText("A"))
        assertEquals("Bee", SpeechManager.normalizeText("B"))
        assertEquals("See", SpeechManager.normalizeText("C"))
        assertEquals("Zed", SpeechManager.normalizeText("Z"))
        assertEquals("Double you", SpeechManager.normalizeText("W"))
    }

    @Test
    fun testNormalizeDigitsAndOrdinals() {
        assertEquals("Zero", SpeechManager.normalizeText("0"))
        assertEquals("One", SpeechManager.normalizeText("1"))
        assertEquals("Nine", SpeechManager.normalizeText("9"))
        assertEquals("First", SpeechManager.normalizeText("1ST"))
        assertEquals("Second", SpeechManager.normalizeText("2ND"))
        assertEquals("Third", SpeechManager.normalizeText("3RD"))
    }

    @Test
    fun testNormalizeMedicalAndEmergencyTokens() {
        assertEquals("Doctor", SpeechManager.normalizeText("DOC"))
        assertEquals("Doctor", SpeechManager.normalizeText("DR"))
        assertEquals("Hospital", SpeechManager.normalizeText("HOSP"))
        assertEquals("Medicine", SpeechManager.normalizeText("MED"))
        assertEquals("Medicine", SpeechManager.normalizeText("MEDS"))
        assertEquals("Emergency", SpeechManager.normalizeText("EMRG"))
        assertEquals("Emergency", SpeechManager.normalizeText("EMERG"))
        assertEquals("Ambulance", SpeechManager.normalizeText("AMB"))
        assertEquals("Police", SpeechManager.normalizeText("POL"))
        assertEquals("S O S", SpeechManager.normalizeText("SOS"))
        assertEquals("Washroom", SpeechManager.normalizeText("BATH"))
        assertEquals("Washroom", SpeechManager.normalizeText("WASH"))
    }

    @Test
    fun testNormalizeSpecialTokens() {
        assertEquals("Please", SpeechManager.normalizeText("PLS"))
        assertEquals("Please", SpeechManager.normalizeText("PLZ"))
        assertEquals("Thank you", SpeechManager.normalizeText("THX"))
        assertEquals("Thank you", SpeechManager.normalizeText("TY"))
        assertEquals("Indian Sign Language", SpeechManager.normalizeText("ISL"))
        assertEquals("American Sign Language", SpeechManager.normalizeText("ASL"))
        assertEquals("Space", SpeechManager.normalizeText("SPACE"))
        assertEquals("Delete", SpeechManager.normalizeText("DEL"))
        assertEquals("Delete", SpeechManager.normalizeText("BACKSPACE"))
        assertEquals("Okay", SpeechManager.normalizeText("OK"))
    }

    @Test
    fun testNormalizeUnderscoreReplacementAndCaseInsensitive() {
        assertEquals("TRAIN TICKET", SpeechManager.normalizeText("TRAIN_TICKET"))
        assertEquals("good morning", SpeechManager.normalizeText("good_morning"))
    }

    @Test
    fun testNormalizeEmptyAndBlank() {
        assertEquals("", SpeechManager.normalizeText(""))
        assertEquals("", SpeechManager.normalizeText("   "))
    }
}
