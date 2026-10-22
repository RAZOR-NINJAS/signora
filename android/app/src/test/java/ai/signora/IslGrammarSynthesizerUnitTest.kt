package ai.signora

import ai.signora.nlp.GlossToken
import ai.signora.nlp.IslGrammarSynthesizer
import ai.signora.nlp.SentenceBuffer
import org.junit.Assert.*
import org.junit.Test

class IslGrammarSynthesizerUnitTest {

    @Test
    fun testTomorrowOffAndTemporalStates() {
        // User request: "TOMORROW OFF" -> "Tomorrow is an off."
        assertEquals("Tomorrow is an off.", IslGrammarSynthesizer.synthesize(listOf("TOMORROW", "OFF")))
        assertEquals("Tomorrow is an off.", IslGrammarSynthesizer.synthesize(listOf("OFF", "TOMORROW")))
        assertEquals("Today is a holiday.", IslGrammarSynthesizer.synthesize(listOf("TODAY", "HOLIDAY")))
        assertEquals("Yesterday was an off.", IslGrammarSynthesizer.synthesize(listOf("YESTERDAY", "OFF")))
        assertEquals("Sunday is an off.", IslGrammarSynthesizer.synthesize(listOf("SUNDAY", "OFF")))
    }

    @Test
    fun testLocationStateAndMedicine() {
        assertEquals("The office is closed.", IslGrammarSynthesizer.synthesize(listOf("OFFICE", "CLOSED")))
        assertEquals("The store is open.", IslGrammarSynthesizer.synthesize(listOf("STORE", "OPEN")))
        assertEquals("I need medicine for fever.", IslGrammarSynthesizer.synthesize(listOf("FEVER", "MEDICINE")))
        assertEquals("I need medicine for headache.", IslGrammarSynthesizer.synthesize(listOf("HEADACHE", "MEDICINE")))
        assertEquals("I am at home.", IslGrammarSynthesizer.synthesize(listOf("I", "HOME")))
        assertEquals("He is at the office.", IslGrammarSynthesizer.synthesize(listOf("HE", "OFFICE")))
    }

    @Test
    fun testNegation() {
        assertEquals("I do not go.", IslGrammarSynthesizer.synthesize(listOf("I", "NOT", "GO")))
        assertEquals("I will not go tomorrow.", IslGrammarSynthesizer.synthesize(listOf("I", "NOT", "GO", "TOMORROW")))
    }

    @Test
    fun testWhQuestions() {
        assertEquals("Where is the train station?", IslGrammarSynthesizer.synthesize(listOf("TRAIN", "STATION", "WHERE")))
        assertEquals("What is your name?", IslGrammarSynthesizer.synthesize(listOf("YOU", "NAME", "WHAT")))
        assertEquals("How are you?", IslGrammarSynthesizer.synthesize(listOf("YOU", "HOW")))
    }

    @Test
    fun testSentenceBufferRestPoseAndPause() {
        val completed = mutableListOf<List<GlossToken>>()
        val buffer = SentenceBuffer(sentencePauseMs = 1800L) { completed.add(it) }

        buffer.push(GlossToken("TOMORROW", timestamp = 1000L))
        buffer.push(GlossToken("OFF", timestamp = 1200L))

        assertEquals(0, completed.size)
        buffer.tick(2000L) // 800ms elapsed, not yet fired
        assertEquals(0, completed.size)

        buffer.tick(3100L) // 1900ms elapsed >= 1800ms -> fires!
        assertEquals(1, completed.size)
        assertEquals(listOf("TOMORROW", "OFF"), completed[0].map { it.label })
    }

    @Test
    fun testThirdPersonWhQuestions() {
        assertEquals("Where does he go?", IslGrammarSynthesizer.synthesize(listOf("WHERE", "HE", "GO")))
        assertEquals("Where does she work?", IslGrammarSynthesizer.synthesize(listOf("WHERE", "SHE", "WORK")))
        assertEquals("What does he want?", IslGrammarSynthesizer.synthesize(listOf("WHAT", "HE", "WANT")))
        assertEquals("How does it work?", IslGrammarSynthesizer.synthesize(listOf("HOW", "IT", "WORK")))
        assertEquals("How is he?", IslGrammarSynthesizer.synthesize(listOf("HOW", "HE")))
        assertEquals("How are they?", IslGrammarSynthesizer.synthesize(listOf("HOW", "THEY")))
        assertEquals("Where is he?", IslGrammarSynthesizer.synthesize(listOf("WHERE", "HE")))
    }

    @Test
    fun testContinuousWhQuestions() {
        assertEquals("Where are you going?", IslGrammarSynthesizer.synthesize(listOf("WHERE", "GO")))
        assertEquals("Where is he?", IslGrammarSynthesizer.synthesize(listOf("WHERE", "HE")))
    }

    @Test
    fun testThirdPersonAndPluralStatements() {
        assertEquals("He has pain in his chest.", IslGrammarSynthesizer.synthesize(listOf("HE", "CHEST", "PAIN")))
        assertEquals("She has pain in her head.", IslGrammarSynthesizer.synthesize(listOf("SHE", "HEAD", "PAIN")))
        assertEquals("They have pain in their stomach.", IslGrammarSynthesizer.synthesize(listOf("THEY", "STOMACH", "PAIN")))
        assertEquals("He has fever.", IslGrammarSynthesizer.synthesize(listOf("HE", "FEVER")))
        assertEquals("She is sick.", IslGrammarSynthesizer.synthesize(listOf("SHE", "SICK")))
        assertEquals("They are doctors.", IslGrammarSynthesizer.synthesize(listOf("THEY", "DOCTOR")))
        assertEquals("We are doctors.", IslGrammarSynthesizer.synthesize(listOf("WE", "DOCTOR")))
    }

    @Test
    fun testPoliteRequestsAndCompounds() {
        assertEquals("Please give water.", IslGrammarSynthesizer.synthesize(listOf("WATER", "PLEASE")))
        assertEquals("Please open the door.", IslGrammarSynthesizer.synthesize(listOf("DOOR", "OPEN", "PLEASE")))
        assertEquals("Police station.", IslGrammarSynthesizer.synthesize(listOf("POLICE", "STATION")))
        assertEquals("Train ticket.", IslGrammarSynthesizer.synthesize(listOf("TRAIN", "TICKET")))
    }

    @Test
    fun testFingerspelling() {
        assertEquals("Cat.", IslGrammarSynthesizer.synthesize(listOf("C", "A", "T")))
        assertEquals("Dog.", IslGrammarSynthesizer.synthesize(listOf("D", "O", "G")))
    }

    @Test
    fun testNounSubjectWhQuestions() {
        assertEquals("When will the train arrive?", IslGrammarSynthesizer.synthesize(listOf("WHEN", "TRAIN", "ARRIVE")))
        assertEquals("Where does the bus go?", IslGrammarSynthesizer.synthesize(listOf("WHERE", "BUS", "GO")))
        assertEquals("Why did the train stop?", IslGrammarSynthesizer.synthesize(listOf("WHY", "TRAIN", "STOP")))
        assertEquals("What does the doctor say?", IslGrammarSynthesizer.synthesize(listOf("WHAT", "DOCTOR", "SAY")))
        assertEquals("How does the machine work?", IslGrammarSynthesizer.synthesize(listOf("HOW", "MACHINE", "WORK")))
        assertEquals("Who do you see?", IslGrammarSynthesizer.synthesize(listOf("WHO", "YOU", "SEE")))
        assertEquals("What do you do today?", IslGrammarSynthesizer.synthesize(listOf("WHAT", "YOU", "DO", "TODAY")))
    }

    @Test
    fun testMyNameIsIntroduction() {
        assertEquals("My name is Rahul.", IslGrammarSynthesizer.synthesize(listOf("MY_NAME_IS", "RAHUL")))
        assertEquals("My name is John.", IslGrammarSynthesizer.synthesize(listOf("MY", "NAME", "JOHN")))
        assertEquals("My name is.", IslGrammarSynthesizer.synthesize(listOf("MY_NAME_IS")))
    }
}

