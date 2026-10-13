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
    val status: DwellStatus = DwellStatus.IDLE
)

enum class RecognitionMode(val displayName: String, val description: String) {
    ASL("ASL", "Single-hand static alphabet (A-Z + SPACE)"),
    ISL("ISL", "ISL model currently recognizes A, B, C, D and Space only (5 of 26)"),
    FINGERSPELLING("Fingerspelling", "Continuous fingerspelling stream")
}

enum class DwellProfile(val label: String, val subtitle: String, val dwellMs: Long) {
    QUICK("0.750s", "Quick (750ms) - Rapid fingerspelling", 750L),
    STANDARD("1.0s", "Standard (1000ms) - Balanced & reliable", 1000L),
    STEADY("2.0s", "Steady (2000ms) - Maximum precision", 2000L)
}
