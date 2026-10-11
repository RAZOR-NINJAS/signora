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
    ISL("Indian Sign Language", "Dual-handed 164D spatial geometry & words")
}

enum class DwellProfile(val label: String, val dwellMs: Long) {
    FAST("Fast (450ms)", 450L),
    STANDARD("Standard (600ms)", 600L),
    RELAXED("Relaxed (800ms)", 800L)
}
