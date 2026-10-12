package ai.signora.models

data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float
)

data class HandLandmarks(
    val points: List<Point3D>,
    val isRightHand: Boolean
)

data class RecognitionResult(
    val label: String,
    val confidence: Float
)

data class DebounceState(
    val candidate: String = "",
    val confidence: Float = 0f,
    val dwellProgress: Float = 0f,
    val confirmed: Boolean = false,
    val lastConfirmed: String = "",
    val transcript: String = "",
    val fps: Float = 30f,
    val isSpeaking: Boolean = false
)

enum class RecognitionMode(val displayName: String, val description: String) {
    ASL("ASL Fingerspelling", "Single-hand static alphabet (A-Z + SPACE)"),
    ISL("Indian Sign Language", "Dual-handed 164D spatial geometry & words"),
    FINGERSPELLING("Fingerspelling", "Continuous fingerspelling stream")
}

enum class DwellProfile(val label: String, val subtitle: String, val dwellMs: Long) {
    QUICK("0.750s", "Quick (750ms) - Rapid fingerspelling", 750L),
    STANDARD("1.0s", "Standard (1000ms) - Balanced & reliable", 1000L),
    STEADY("2.0s", "Steady (2000ms) - Maximum precision", 2000L)
}
