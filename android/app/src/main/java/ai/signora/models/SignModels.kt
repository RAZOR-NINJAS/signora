package ai.signora.models

data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float
)

data class HandLandmarks(
    val points: List<Point3D>,
    val isRightHand: Boolean,
    val isFrontCamera: Boolean = true
)

data class PoseLandmarks(
    val points: List<Point3D>,
    val isFrontCamera: Boolean = true
)

data class SignInfo(
    val name: String,
    val category: String,
    val isWord: Boolean,
    val expectedHands: Int,
    val targetAnchor: String?,
    val guidePosture: String,
    val guideDetail: String,
    val expectedMotion: String? = null,
    val motionDescription: String = ""
)

data class RecognitionResult(
    val label: String,
    val confidence: Float,
    val margin: Float = 1.0f,
    val top3: List<Pair<String, Float>> = emptyList()
)

enum class DwellStatus {
    IDLE,
    DETECTING,
    HOLDING,
    COMMITTED,
    NO_HAND,
    LOW_LIGHT,
    ERROR
}

data class DebounceState(
    val candidate: String = "",
    val confidence: Float = 0f,
    val margin: Float = 0f,
    val dwellProgress: Float = 0f,
    val confirmed: Boolean = false,
    val lastConfirmed: String = "",
    val transcript: String = "",
    val fps: Float = 30f,
    val inferenceMs: Float = 0.5f,
    val isSpeaking: Boolean = false,
    val top3: List<Pair<String, Float>> = emptyList(),
    val status: DwellStatus = DwellStatus.IDLE,
    val isApex: Boolean = false,
    val effectiveDwellMs: Long = 250L
)

enum class RecognitionMode(val displayName: String, val description: String) {
    ASL("ASL", "Single-hand static alphabet (A-Z + SPACE)"),
    ISL("ISL", "Indian Sign Language (15 signs: A-E + Namaste, Hello, Help, Doctor, Emergency, etc.)"),
    FINGERSPELLING("Fingerspelling", "Continuous fingerspelling stream")
}

/**
 * Holds the current state of the ISL Gloss-to-Natural-Sentence NLP pipeline.
 *
 * @param pendingGlosses      Gloss labels still being accumulated (not yet sentence-complete).
 * @param synthesizedSentence The latest naturally-reconstructed English sentence.
 * @param isProcessing        True while a Tier-2 LLM refinement pass is running (future use).
 */
data class NlpState(
    val pendingGlosses: List<String> = emptyList(),
    val synthesizedSentence: String = "",
    val isProcessing: Boolean = false
)

enum class DwellProfile(val label: String, val subtitle: String, val dwellMs: Long) {
    FLUID("0.50s", "Fluid (500ms) - Quick natural signing", 500L),
    STANDARD("1.00s", "Standard (1000ms) - Balanced & steady hold", 1000L),
    STEADY("1.50s", "Steady (1500ms) - Long deliberate hold", 1500L)
}
