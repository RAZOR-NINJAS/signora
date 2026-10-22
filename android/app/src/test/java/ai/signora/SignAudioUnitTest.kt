package ai.signora

import ai.signora.audio.SignAudio
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignAudioUnitTest {

    @Test
    fun bundledCuesAreRegistered() {
        assertTrue(SignAudio.hasCue("SUII"))
        assertTrue(SignAudio.hasCue("HALA_MADRID"))
        assertTrue(SignAudio.hasCue("suii"))
    }

    @Test
    fun signsWithoutCuesFallBackToTts() {
        assertFalse(SignAudio.hasCue("HELLO"))
        assertFalse(SignAudio.hasCue(""))
    }
}
