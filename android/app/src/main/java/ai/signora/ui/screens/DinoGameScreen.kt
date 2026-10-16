package ai.signora.ui.screens

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ai.signora.game.audio.GameAudioTrack
import ai.signora.models.DebounceState
import ai.signora.models.HandLandmarks
import ai.signora.models.RecognitionMode
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

// --- Canonical 21-point hand skeleton connections ---
private val HAND_CONNECTIONS = listOf(
    0 to 1, 0 to 5, 5 to 9, 9 to 13, 13 to 17, 0 to 17,
    1 to 2, 2 to 3, 3 to 4,
    5 to 6, 6 to 7, 7 to 8,
    9 to 10, 10 to 11, 11 to 12,
    13 to 14, 14 to 15, 15 to 16,
    17 to 18, 18 to 19, 19 to 20
)

// --- Pixel Art Sprite Definitions ---
private val DINO_BODY = listOf(
    "         XXXXXXXX",
    "        XXXXXXXXXX",
    "        XXXXXXXXXX",
    "        XXXXXXXXXX",
    "        XXXX",
    "        XXXXXXXXXX",
    "        XXXXXXXXXX",
    "XX      XXXXXXXX",
    "XX     XXXXXXXXX",
    "XXX   XXXXXXXXXX",
    "XXXX XXXXXXXXXXX",
    "XXXXXXXXXXXXXXXX",
    " XXXXXXXXXXXXXXX",
    "  XXXXXXXXXXXXX",
    "   XXXXXXXXXXX",
    "    XXXXXXXXX",
    "     XXXXXXX"
)
private val DINO_LEGS_A = listOf(
    "     XX XXX",
    "     XX  XX",
    "     XX  XX",
    "     XX",
    "    XXX"
)
private val DINO_LEGS_B = listOf(
    "     XX XXX",
    "     XX  XX",
    "     XX  XX",
    "         XX",
    "        XXX"
)
private val DINO_LEGS_DEAD = listOf(
    "     XX XXX",
    "     XX  XX",
    "    XXX  XX",
    "   XXX    XX",
    "  XXX      XX"
)

// Flattened horizontal sprint posture while ducking.
private val DINO_DUCK_BODY = listOf(
    "          XXXXXXXX",
    "         XXXXXXXXXX",
    "         XXXXXXXXX",
    "XX       XXXXXXXXX",
    "XX      XXXXXXXXXX",
    "XXX    XXXXXXXXXXX",
    "XXXXXXXXXXXXXXXXXX",
    " XXXXXXXXXXXXXXXX",
    "  XXXXXXXXXXXXXX",
    "   XXXXXXXXXXXX"
)
private val DINO_DUCK_LEGS_A = listOf(
    "     XX  XX",
    "    XXX  XX"
)
private val DINO_DUCK_LEGS_B = listOf(
    "     XX XXX",
    "     XXX XX"
)

// Flying pterodactyl / space drone (two wing-flap frames).
private val BIRD_WINGS_UP = listOf(
    "......XXXXX......",
    "...XXXXXXXXXXX...",
    ".XXXXXXXXXXXXXXX.",
    "XXXXXXXXXXXXXXXXX",
    "...XXXXXXXXXXX...",
    "......XXXXX......",
    ".....XXXXXXX.....",
    "......XXXXX......",
    ".......XXX......."
)
private val BIRD_WINGS_DOWN = listOf(
    "......XXXXX......",
    "......XXXXX......",
    ".....XXXXXXX.....",
    "...XXXXXXXXXXX...",
    "XXXXXXXXXXXXXXXXX",
    ".XXXXXXXXXXXXXXX.",
    "...XXXXXXXXXXX...",
    "......XXXXX......",
    ".......XXX......."
)

// Biome Obstacle Sprites
private val CACTUS_DESERT = listOf(
    "....XXX....",
    "....XXX....",
    "....XXX....",
    ".X..XXX..X.",
    ".X..XXX..X.",
    ".X..XXX..X.",
    ".XXXXXX..X.",
    "....XXX..X.",
    "....XXXXXX.",
    "....XXX....",
    "....XXX....",
    "....XXX....",
    "....XXX....",
    "....XXX....",
    "....XXX....",
    "....XXX....",
    "....XXX...."
)

private val PYLON_NEON = listOf(
    ".....X.....",
    "....XXX....",
    "...XXXXX...",
    "....XXX....",
    ".....X.....",
    "....XXX....",
    "...XXXXX...",
    "..XXXXXXX..",
    "....XXX....",
    "....XXX....",
    "....XXX....",
    "...XXXXX...",
    "...XXXXX...",
    "..XXXXXXX..",
    "..XXXXXXX..",
    ".XXXXXXXXX.",
    "XXXXXXXXXXX"
)

private val SHROOM_MYSTICAL = listOf(
    "....XXXXX....",
    "...XXXXXXX...",
    "..XXXXXXXXX..",
    ".XXXXXXXXXXX.",
    "XXXXXXXXXXXXX",
    "XXXXXXXXXXXXX",
    ".XXXXXXXXXXX.",
    "....XXXXX....",
    ".....XXX.....",
    ".....XXX.....",
    ".....XXX.....",
    ".....XXX.....",
    "....XXXXX....",
    "....XXXXX....",
    "...XXXXXXX...",
    "..XXXXXXXXX..",
    ".XXXXXXXXXXX."
)

private val SPIKE_COSMIC = listOf(
    ".....X.....",
    "....XXX....",
    "....XXX....",
    "...XXXXX...",
    "...XXXXX...",
    "..XXXXXXX..",
    "..XXXXXXX..",
    ".XXXXXXXXX.",
    ".XXXXXXXXX.",
    "XXXXXXXXXXX",
    ".XXXXXXXXX.",
    "..XXXXXXX..",
    "...XXXXX...",
    "..XXXXXXX..",
    ".XXXXXXXXX.",
    "XXXXXXXXXXX",
    "XXXXXXXXXXX"
)

private data class SpriteRun(val row: Int, val startCol: Int, val endCol: Int)

private fun parseRuns(grid: List<String>): List<SpriteRun> {
    val runs = mutableListOf<SpriteRun>()
    for (r in grid.indices) {
        val line = grid[r]
        var start = -1
        for (c in line.indices) {
            val isSolid = line[c] == 'X'
            if (isSolid && start == -1) {
                start = c
            } else if (!isSolid && start != -1) {
                runs.add(SpriteRun(r, start, c))
                start = -1
            }
        }
        if (start != -1) {
            runs.add(SpriteRun(r, start, line.length))
        }
    }
    return runs
}

private val DINO_BODY_RUNS = parseRuns(DINO_BODY)
private val DINO_LEGS_A_RUNS = parseRuns(DINO_LEGS_A)
private val DINO_LEGS_B_RUNS = parseRuns(DINO_LEGS_B)
private val DINO_LEGS_DEAD_RUNS = parseRuns(DINO_LEGS_DEAD)
private val DINO_DUCK_BODY_RUNS = parseRuns(DINO_DUCK_BODY)
private val DINO_DUCK_LEGS_A_RUNS = parseRuns(DINO_DUCK_LEGS_A)
private val DINO_DUCK_LEGS_B_RUNS = parseRuns(DINO_DUCK_LEGS_B)
private val BIRD_WINGS_UP_RUNS = parseRuns(BIRD_WINGS_UP)
private val BIRD_WINGS_DOWN_RUNS = parseRuns(BIRD_WINGS_DOWN)

private val RUNS_DESERT = parseRuns(CACTUS_DESERT)
private val RUNS_NEON = parseRuns(PYLON_NEON)
private val RUNS_MYSTICAL = parseRuns(SHROOM_MYSTICAL)
private val RUNS_COSMIC = parseRuns(SPIKE_COSMIC)

// Rainbow chromatic trail palette used during FEVER overdrive mode.
private val FEVER_RAINBOW = listOf(
    Color(0xFFFF3366), Color(0xFFFF9933), Color(0xFFFFEE33),
    Color(0xFF66FF66), Color(0xFF33DDFF), Color(0xFF5566FF),
    Color(0xFFCC44FF)
)

// --- Game Biomes (Environments) ---
private enum class GameBiome(
    val title: String,
    val skyGradient: List<Color>,
    val groundColor: Color,
    val groundLineColor: Color,
    val obstacleRuns: List<SpriteRun>,
    val obstacleColor: Color,
    val obstacleGlow: Color,
    val dinoColor: Color,
    val particleColors: List<Color>
) {
    DESERT(
        title = "CYBER DESERT",
        skyGradient = listOf(Color(0xFFFFFBEB), Color(0xFFFDE68A)),
        groundColor = Color(0xFFF59E0B),
        groundLineColor = Color(0xFF78350F),
        obstacleRuns = RUNS_DESERT,
        obstacleColor = Color(0xFF15803D),
        obstacleGlow = Color(0x6622C55E),
        dinoColor = Color(0xFF334155),
        particleColors = listOf(Color(0xFFF59E0B), Color(0xFFEAB308), Color(0xFF10B981), Color(0xFFEF4444))
    ),
    NEON_SYNTH(
        title = "NEON SYNTHWAVE",
        skyGradient = listOf(Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF24243E)),
        groundColor = Color(0xFF1A102F),
        groundLineColor = Color(0xFF06B6D4),
        obstacleRuns = RUNS_NEON,
        obstacleColor = Color(0xFFF43F5E),
        obstacleGlow = Color(0x88EC4899),
        dinoColor = Color(0xFFF8FAFC),
        particleColors = listOf(Color(0xFF06B6D4), Color(0xFFF43F5E), Color(0xFFA855F7), Color(0xFF38BDF8))
    ),
    MYSTICAL(
        title = "MYSTICAL REALM",
        skyGradient = listOf(Color(0xFF064E3B), Color(0xFF022C22), Color(0xFF0F172A)),
        groundColor = Color(0xFF065F46),
        groundLineColor = Color(0xFF34D399),
        obstacleRuns = RUNS_MYSTICAL,
        obstacleColor = Color(0xFFA855F7),
        obstacleGlow = Color(0x88C084FC),
        dinoColor = Color(0xFFF1F5F9),
        particleColors = listOf(Color(0xFF34D399), Color(0xFFA855F7), Color(0xFFFBBF24), Color(0xFF2DD4BF))
    ),
    COSMIC_SPACE(
        title = "COSMIC ZERO-G",
        skyGradient = listOf(Color(0xFF030712), Color(0xFF090D1A), Color(0xFF1E1B4B)),
        groundColor = Color(0xFF111827),
        groundLineColor = Color(0xFF818CF8),
        obstacleRuns = RUNS_COSMIC,
        obstacleColor = Color(0xFF38BDF8),
        obstacleGlow = Color(0x9960A5FA),
        dinoColor = Color(0xFFE0E7FF),
        particleColors = listOf(Color(0xFF818CF8), Color(0xFF38BDF8), Color(0xFFEC4899), Color(0xFFF472B6))
    )
}

// FX Data Models
private data class ShockParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var alpha: Float = 1.0f,
    val color: Color
)

private data class GhostTrail(
    val x: Float,
    val y: Float,
    var alpha: Float = 0.5f,
    val color: Color
)

private data class ShockwaveRing(
    val x: Float,
    val y: Float,
    var radius: Float = 12f,
    var maxRadius: Float = 85f,
    var alpha: Float = 0.85f,
    val color: Color
)

private data class FloatingIndicator(
    var text: String,
    var x: Float,
    var y: Float,
    var alpha: Float = 1.0f,
    val color: Color
)

private data class LaserBeam(
    var startX: Float,
    var endX: Float,
    var y: Float,
    var alpha: Float = 1.0f,
    val color: Color
)

private enum class PickupKind { HEART, STAR, COIN }

private data class PowerPickup(
    val id: Long,
    var x: Float,
    var y: Float,
    val kind: PickupKind,
    var collected: Boolean = false
)

private data class Star(
    val x: Float,
    val y: Float,
    val size: Float,
    val speed: Float,
    val color: Color
)

private enum class ObstacleKind { GROUND, FLYING_LOW, FLYING_HIGH }

// Dynamic Obstacle in the continuous runner stream
private data class StreamObstacle(
    val id: Long,
    var x: Float,
    val signItem: Pair<String, String>,
    val kind: ObstacleKind = ObstacleKind.GROUND,
    var cleared: Boolean = false,
    var bonusAwarded: Boolean = false
)

private enum class GameState {
    IDLE,
    RUNNING,
    GAME_OVER
}

private val ASL_GAME_LETTERS = listOf(
    "A" to "Thumb alongside upright fist",
    "B" to "4 straight fingers, thumb folded",
    "C" to "Curved crescent hand shape",
    "D" to "Index pointing up, fingers in ring",
    "E" to "Curled fingers touching thumb",
    "F" to "Index and thumb touching, 3 up",
    "G" to "Index pointing sideways",
    "H" to "Index & middle extended sideways",
    "I" to "Pinky straight up in fist",
    "L" to "Index & thumb at 90 degrees",
    "O" to "All fingers touching thumb in O",
    "R" to "Middle crossed over index",
    "S" to "Tight fist, thumb over fingers",
    "U" to "Index & middle together upright",
    "V" to "Peace sign / V shape",
    "W" to "Index, middle, ring apart upright",
    "Y" to "Thumb & pinky extended (Hang Loose)"
)

private val ISL_GAME_LETTERS = listOf(
    "A" to "Fist with thumb upright",
    "B" to "4 fingers upright together",
    "C" to "Curved open hand",
    "D" to "Index pointing upwards",
    "SPACE" to "Flat open horizontal palm",
    "NAMASTE" to "Two palms joined at chest",
    "HELLO" to "Salute / wave from forehead",
    "WATER" to "W gesture near mouth",
    "HELP" to "Fist resting on flat palm",
    "YES" to "Nodding closed fist",
    "NO" to "Index and middle tapping thumb"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DinoGameScreen(
    previewView: PreviewView,
    debounceState: DebounceState,
    handLandmarks: List<HandLandmarks>,
    currentMode: RecognitionMode,
    onModeChange: (RecognitionMode) -> Unit,
    onToggleCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("signora_dino_game", Context.MODE_PRIVATE) }

    // Procedural 8-bit / synth audio engine (pure Kotlin AudioTrack synthesis).
    val audio = remember { GameAudioTrack() }
    DisposableEffect(Unit) {
        onDispose { audio.release() }
    }

    var highScore by remember { mutableStateOf(prefs.getInt("high_score", 0)) }
    var score by remember { mutableStateOf(0) }
    var streak by remember { mutableStateOf(0) }
    var gameState by remember { mutableStateOf(GameState.IDLE) }
    var speed by remember { mutableStateOf(0.40f) }

    // 3 Hearts Health & Invincibility i-frames
    val maxHearts = 3
    var hearts by remember { mutableStateOf(maxHearts) }
    var invincibilityTimer by remember { mutableStateOf(0f) }

    // Double Jump & Ducking Mechanics
    var jumpsRemaining by remember { mutableStateOf(2) }
    var isDucking by remember { mutableStateOf(false) }
    var duckHoldActive by remember { mutableStateOf(false) }
    var duckGestureTimer by remember { mutableStateOf(0f) }
    val latestHands by rememberUpdatedState(handLandmarks)

    // Fever / Overdrive Mode
    var feverMeter by remember { mutableStateOf(0f) }      // 0f..1f charge
    var feverActive by remember { mutableStateOf(false) }
    var feverTimeLeft by remember { mutableStateOf(0f) }   // seconds remaining
    var comboMultiplier by remember { mutableStateOf(1) }

    // Biome System
    val allBiomes = GameBiome.values()
    var currentBiomeIndex by remember { mutableStateOf(0) }
    var previousBiomeIndex by remember { mutableStateOf(0) }
    var biomeTransition by remember { mutableStateOf(0f) } // 1f -> 0f smooth warp blend
    val currentBiome = allBiomes[currentBiomeIndex % allBiomes.size]
    val previousBiome = allBiomes[previousBiomeIndex % allBiomes.size]
    var warpBannerText by remember { mutableStateOf<String?>(null) }
    var warpBannerAlpha by remember { mutableStateOf(0f) }

    // Screen Shake effect (0f = none, >0f = active shake magnitude)
    var screenShake by remember { mutableStateOf(0f) }

    // Physics Jump System (True Continuous Runner!)
    var dinoHop by remember { mutableStateOf(0f) } // Vertical lift in normalized units (0 = ground)
    var dinoVY by remember { mutableStateOf(0f) }  // Vertical velocity
    var isGrounded by remember { mutableStateOf(true) }
    var legFrame by remember { mutableStateOf(0) }

    // Obstacle & Pickup Stream Pipeline
    val signList = if (currentMode == RecognitionMode.ISL) ISL_GAME_LETTERS else ASL_GAME_LETTERS
    val activeObstacles = remember { mutableStateListOf<StreamObstacle>() }
    val powerPickups = remember { mutableStateListOf<PowerPickup>() }
    var pickupCounter by remember { mutableStateOf(0L) }
    var clearedSigns by remember { mutableStateOf(listOf<String>()) }
    var obstacleCounter by remember { mutableStateOf(0L) }

    // Particles, Ghost Trails, Shockwave Rings & Laser Beams
    val shockParticles = remember { mutableStateListOf<ShockParticle>() }
    val ghostTrails = remember { mutableStateListOf<GhostTrail>() }
    val shockwaveRings = remember { mutableStateListOf<ShockwaveRing>() }
    val floatingIndicators = remember { mutableStateListOf<FloatingIndicator>() }
    val laserBeams = remember { mutableStateListOf<LaserBeam>() }

    // Pre-allocated cosmic stars
    val stars = remember {
        List(60) {
            Star(
                x = kotlin.random.Random.nextFloat(),
                y = kotlin.random.Random.nextFloat() * 0.70f,
                size = kotlin.random.Random.nextFloat() * 2.8f + 1f,
                speed = kotlin.random.Random.nextFloat() * 0.15f + 0.05f,
                color = listOf(Color.White, Color(0xFF93C5FD), Color(0xFFFDE047), Color(0xFFF472B6)).random()
            )
        }
    }

    // -------------------------------------------------------------------
    // Score, Fever, Biome & Duck helpers
    // -------------------------------------------------------------------
    fun currentMultiplier(): Int {
        val combo = 1 + (streak / 5)
        return (if (feverActive) combo * 2 else combo).coerceIn(1, 8)
    }

    fun checkBiomeWarp() {
        val target = (score / 250) % allBiomes.size
        if (target != currentBiomeIndex) {
            previousBiomeIndex = currentBiomeIndex
            currentBiomeIndex = target
            biomeTransition = 1f
            warpBannerText = "WARP ACTIVE • ENTERING ${allBiomes[target].title}"
            warpBannerAlpha = 1f
            screenShake = 14f
            audio.milestone()
        }
    }

    fun addScore(base: Int, label: String = "") {
        val gained = base * currentMultiplier()
        score += gained
        if (score > highScore) {
            highScore = score
            prefs.edit().putInt("high_score", highScore).apply()
        }
        if (label.isNotBlank()) {
            floatingIndicators.add(FloatingIndicator("+$gained $label", 0.18f, 0.55f, color = Color(0xFF34D399)))
        }
        checkBiomeWarp()
    }

    fun activateFever() {
        feverActive = true
        feverTimeLeft = 8f
        feverMeter = 1f
        screenShake = 20f
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        floatingIndicators.add(FloatingIndicator("★ FEVER OVERDRIVE ★", 0.12f, 0.40f, color = Color(0xFFFF3D7F)))
        shockwaveRings.add(ShockwaveRing(0.17f, 0.73f, 10f, 170f, 0.95f, Color(0xFFFF3D7F)))
        audio.startFever()
    }

    fun chargeFever(amount: Float) {
        if (feverActive) return
        feverMeter = (feverMeter + amount).coerceAtMost(1f)
        if (feverMeter >= 1f) activateFever()
    }

    fun endFever() {
        feverActive = false
        feverTimeLeft = 0f
        feverMeter = 0f
        audio.stopFever()
        floatingIndicators.add(FloatingIndicator("OVERDRIVE ENDED", 0.16f, 0.45f, color = Color(0xFF94A3B8)))
    }

    fun spawnDust() {
        for (i in 0 until 10) {
            shockParticles.add(
                ShockParticle(
                    x = 0.16f + (kotlin.random.Random.nextFloat() - 0.5f) * 0.05f,
                    y = 0.735f,
                    vx = -(kotlin.random.Random.nextFloat() * 0.35f + 0.05f),
                    vy = -(kotlin.random.Random.nextFloat() * 0.18f + 0.02f),
                    color = currentBiome.groundColor
                )
            )
        }
    }

    fun pickObstacleKind(): ObstacleKind {
        // Flyers are introduced gradually and never as the very first obstacles.
        if (score < 120) return ObstacleKind.GROUND
        val flyChance = (0.14f + score * 0.00006f).coerceAtMost(0.36f)
        return if (kotlin.random.Random.nextFloat() < flyChance) {
            if (kotlin.random.Random.nextBoolean()) ObstacleKind.FLYING_LOW else ObstacleKind.FLYING_HIGH
        } else {
            ObstacleKind.GROUND
        }
    }

    fun spawnJumpFX(isDouble: Boolean) {
        shockwaveRings.add(
            ShockwaveRing(
                x = 0.17f,
                y = 0.73f - dinoHop,
                radius = if (isDouble) 8f else 12f,
                maxRadius = if (feverActive) 140f else (if (isDouble) 70f else 85f),
                alpha = 0.95f,
                color = when {
                    feverActive -> FEVER_RAINBOW.random()
                    isDouble -> Color(0xFF38BDF8)
                    else -> currentBiome.obstacleColor
                }
            )
        )
        for (i in 0 until (if (feverActive) 24 else 14)) {
            shockParticles.add(
                ShockParticle(
                    x = 0.17f + (kotlin.random.Random.nextFloat() - 0.5f) * 0.05f,
                    y = 0.73f - dinoHop,
                    vx = (kotlin.random.Random.nextFloat() - 0.5f) * 0.6f,
                    vy = if (isDouble) (kotlin.random.Random.nextFloat() * 0.4f + 0.1f) else -(kotlin.random.Random.nextFloat() * 0.6f + 0.1f),
                    color = if (feverActive) FEVER_RAINBOW.random() else (if (isDouble) Color(0xFF38BDF8) else currentBiome.particleColors.random())
                )
            )
        }
    }

    // Function to trigger a physical, physics-driven leap (Single or Double Jump!)
    fun doJump() {
        if (gameState != GameState.RUNNING) return

        if (isGrounded) {
            isGrounded = false
            jumpsRemaining = 1
            dinoVY = -1.65f
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            audio.jump()
            spawnJumpFX(isDouble = false)
            score += 20 * currentMultiplier()
        } else if (jumpsRemaining > 0) {
            jumpsRemaining = 0
            dinoVY = -1.45f
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            audio.jump()
            spawnJumpFX(isDouble = true)
            floatingIndicators.add(FloatingIndicator("DOUBLE JUMP! 🚀", 0.18f, 0.46f, color = Color(0xFF38BDF8)))
            score += 40 * currentMultiplier()
        }

        if (score > highScore) {
            highScore = score
            prefs.edit().putInt("high_score", highScore).apply()
        }
        checkBiomeWarp()
    }

    // Laser Blaster: Incinerates upcoming obstacle when user signs correctly!
    fun fireSignLaser(targetObs: StreamObstacle) {
        targetObs.cleared = true
        targetObs.bonusAwarded = true
        if (targetObs.signItem.first !in clearedSigns) {
            clearedSigns = clearedSigns + targetObs.signItem.first
        }
        streak++
        val pts = (250 + (streak * 25)) * currentMultiplier()
        score += pts
        if (score > highScore) {
            highScore = score
            prefs.edit().putInt("high_score", highScore).apply()
        }

        // 5-streak combo restores a heart!
        if (streak % 5 == 0 && hearts < maxHearts) {
            hearts = (hearts + 1).coerceAtMost(maxHearts)
            audio.powerup()
            floatingIndicators.add(FloatingIndicator("+1 HEART! ❤️", 0.20f, 0.42f, color = Color(0xFFFF3366)))
        }

        chargeFever(0.35f)
        audio.laser()
        audio.signBonus()
        screenShake = 12f

        // Blazing Laser Beam
        laserBeams.add(
            LaserBeam(
                startX = 0.17f,
                endX = targetObs.x + 0.03f,
                y = 0.72f - (if (targetObs.kind == ObstacleKind.GROUND) 0.05f else 0.12f),
                color = if (feverActive) FEVER_RAINBOW.random() else Color(0xFFFBBF24)
            )
        )

        // Exploding shards
        for (i in 0 until 24) {
            shockParticles.add(
                ShockParticle(
                    x = targetObs.x + (kotlin.random.Random.nextFloat() - 0.5f) * 0.05f,
                    y = 0.70f,
                    vx = (kotlin.random.Random.nextFloat() - 0.5f) * 1.4f,
                    vy = -(kotlin.random.Random.nextFloat() * 1.1f + 0.1f),
                    color = if (feverActive) FEVER_RAINBOW.random() else currentBiome.particleColors.random()
                )
            )
        }
        shockwaveRings.add(
            ShockwaveRing(targetObs.x, 0.70f, 10f, 100f, 0.9f, Color(0xFFFBBF24))
        )
        floatingIndicators.add(
            FloatingIndicator("+$pts SIGN BLAST! ⚡", targetObs.x.coerceIn(0.20f, 0.75f), 0.48f, color = Color(0xFFFBBF24))
        )
        checkBiomeWarp()
    }

    fun startNewGame() {
        score = 0
        streak = 0
        hearts = 3
        invincibilityTimer = 0f
        speed = 0.40f
        dinoHop = 0f
        dinoVY = 0f
        isGrounded = true
        jumpsRemaining = 2
        isDucking = false
        duckHoldActive = false
        duckGestureTimer = 0f
        feverMeter = 0f
        feverActive = false
        feverTimeLeft = 0f
        audio.stopFever()
        clearedSigns = emptyList()
        activeObstacles.clear()
        powerPickups.clear()
        shockParticles.clear()
        ghostTrails.clear()
        shockwaveRings.clear()
        floatingIndicators.clear()
        laserBeams.clear()
        warpBannerText = null
        warpBannerAlpha = 0f
        biomeTransition = 0f
        previousBiomeIndex = currentBiomeIndex
        screenShake = 0f

        // Spawn first 2 streaming obstacles spaced out
        obstacleCounter++
        activeObstacles.add(StreamObstacle(obstacleCounter, 1.30f, signList.random(), ObstacleKind.GROUND))
        obstacleCounter++
        activeObstacles.add(StreamObstacle(obstacleCounter, 2.10f, signList.random(), ObstacleKind.GROUND))

        gameState = GameState.RUNNING
    }

    // Target upcoming obstacle
    val currentUpcomingObstacle = activeObstacles.firstOrNull { it.x > 0.10f }

    // Instant Continuous Gesture Recognition: triggers sign laser blast as obstacle approaches!
    LaunchedEffect(debounceState.candidate, debounceState.top3, activeObstacles.size) {
        if (gameState == GameState.RUNNING) {
            val upcoming = currentUpcomingObstacle
            if (upcoming != null && upcoming.x in 0.16f..0.80f && !upcoming.cleared) {
                val target = upcoming.signItem.first.uppercase().trim()
                val candidateMatch = debounceState.candidate.uppercase().trim() == target
                val top3Match = debounceState.top3.any { (lbl, prob) ->
                    lbl.uppercase().trim() == target && prob >= 0.22f
                }
                val lastConfirmedMatch = debounceState.lastConfirmed.uppercase().trim() == target

                if (candidateMatch || top3Match || lastConfirmedMatch) {
                    fireSignLaser(upcoming)
                }
            }
        }
    }

    // Main 60 FPS Physics & Motion Loop (Continuous, Never Freezing!)
    LaunchedEffect(gameState) {
        var lastTime = System.currentTimeMillis()
        while (gameState == GameState.RUNNING) {
            val now = System.currentTimeMillis()
            val dt = ((now - lastTime) / 1000f).coerceIn(0.005f, 0.04f)
            lastTime = now

            // 0. Fever / Overdrive timing
            if (feverActive) {
                feverTimeLeft -= dt
                if (feverTimeLeft <= 0f) endFever()
            }

            // 0b. Invincibility i-frames decay
            if (invincibilityTimer > 0f) {
                invincibilityTimer = (invincibilityTimer - dt).coerceAtLeast(0f)
            }

            // 0c. Duck resolution (touch hold + swipe + MediaPipe fist/low-wrist gesture)
            if (duckGestureTimer > 0f) duckGestureTimer = (duckGestureTimer - dt).coerceAtLeast(0f)
            val gestureDuck = detectDuckGesture(latestHands)
            val shouldDuck = duckHoldActive || duckGestureTimer > 0f || gestureDuck
            if (shouldDuck && !isDucking) {
                audio.duck()
                spawnDust()
            }
            isDucking = shouldDuck

            // 1. Jump Physics
            if (!isGrounded) {
                dinoVY += 3.8f * dt // Gravity acceleration
                dinoHop -= dinoVY * dt
                if (dinoHop <= 0f) {
                    dinoHop = 0f
                    dinoVY = 0f
                    isGrounded = true
                    jumpsRemaining = 2 // Reset double jump
                    // Touchdown dust kickback
                    spawnDust()
                }
            }

            // Running legs toggle
            legFrame = if ((now / 90) % 2 == 0L) 0 else 1

            // 2. Obstacle Streaming Motion
            val deadObs = mutableListOf<StreamObstacle>()
            for (obs in activeObstacles) {
                obs.x -= speed * dt

                // Collision Detection against Dino (Dino x roughly 0.13f..0.20f)
                if (obs.x in 0.13f..0.20f && !obs.cleared) {
                    val dodged = when {
                        feverActive -> true
                        obs.kind == ObstacleKind.GROUND -> dinoHop >= 0.07f
                        obs.kind == ObstacleKind.FLYING_LOW -> isDucking
                        obs.kind == ObstacleKind.FLYING_HIGH -> dinoHop < 0.12f
                        else -> false
                    }
                    if (dodged) {
                        obs.cleared = true
                        if (feverActive) {
                            // Smash through obstacles -> rainbow shatter shards.
                            obs.bonusAwarded = true
                            score += 150 * currentMultiplier()
                            audio.streak()
                            for (i in 0 until 24) {
                                shockParticles.add(
                                    ShockParticle(
                                        x = obs.x,
                                        y = 0.70f,
                                        vx = (kotlin.random.Random.nextFloat() - 0.5f) * 1.3f,
                                        vy = -(kotlin.random.Random.nextFloat() * 0.95f + 0.10f),
                                        color = FEVER_RAINBOW.random()
                                    )
                                )
                            }
                            shockwaveRings.add(ShockwaveRing(obs.x, 0.70f, 8f, 95f, 0.9f, FEVER_RAINBOW.random()))
                        } else if (!obs.bonusAwarded) {
                            obs.bonusAwarded = true
                            streak++
                            addScore(100, label = "DODGE")
                            chargeFever(0.15f)
                            audio.streak()
                            screenShake = 5f
                        }
                    } else {
                        // Impact!
                        if (invincibilityTimer <= 0f) {
                            obs.cleared = true
                            hearts--
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            screenShake = 22f

                            if (hearts > 0) {
                                invincibilityTimer = 1.8f
                                audio.hurt()
                                floatingIndicators.add(FloatingIndicator("-1 HEART! 💔", 0.18f, 0.45f, color = Color(0xFFEF4444)))
                                for (i in 0 until 20) {
                                    shockParticles.add(
                                        ShockParticle(
                                            x = 0.17f + (kotlin.random.Random.nextFloat() - 0.5f) * 0.05f,
                                            y = 0.72f,
                                            vx = (kotlin.random.Random.nextFloat() - 0.5f) * 1.2f,
                                            vy = -(kotlin.random.Random.nextFloat() * 0.8f + 0.1f),
                                            color = Color(0xFFEF4444)
                                        )
                                    )
                                }
                            } else {
                                // Final CRASH!
                                gameState = GameState.GAME_OVER
                                audio.stopFever()
                                audio.crash()
                                for (i in 0 until 36) {
                                    shockParticles.add(
                                        ShockParticle(
                                            x = 0.17f + (kotlin.random.Random.nextFloat() - 0.5f) * 0.06f,
                                            y = 0.72f,
                                            vx = (kotlin.random.Random.nextFloat() - 0.5f) * 1.5f,
                                            vy = -(kotlin.random.Random.nextFloat() * 1.0f + 0.10f),
                                            color = currentBiome.particleColors.random()
                                        )
                                    )
                                }
                                break
                            }
                        }
                    }
                }

                if (obs.x < -0.25f) {
                    deadObs.add(obs)
                }
            }
            activeObstacles.removeAll(deadObs)

            // 2b. PowerPickups streaming and collection
            val deadPickups = mutableListOf<PowerPickup>()
            for (pup in powerPickups) {
                pup.x -= speed * dt
                if (pup.x in 0.13f..0.21f && !pup.collected) {
                    val closeY = abs((0.74f - dinoHop) - pup.y) < 0.14f
                    if (closeY) {
                        pup.collected = true
                        when (pup.kind) {
                            PickupKind.HEART -> {
                                hearts = (hearts + 1).coerceAtMost(maxHearts)
                                audio.powerup()
                                floatingIndicators.add(FloatingIndicator("+1 HEART! ❤️", 0.18f, 0.44f, color = Color(0xFFFF3366)))
                            }
                            PickupKind.STAR -> {
                                activateFever()
                                audio.powerup()
                                floatingIndicators.add(FloatingIndicator("STAR OVERDRIVE! ⭐", 0.18f, 0.44f, color = Color(0xFFFDE047)))
                            }
                            PickupKind.COIN -> {
                                score += 500 * currentMultiplier()
                                audio.streak()
                                floatingIndicators.add(FloatingIndicator("+${500 * currentMultiplier()} COIN! 💎", 0.18f, 0.44f, color = Color(0xFF38BDF8)))
                            }
                        }
                    }
                }
                if (pup.x < -0.2f) deadPickups.add(pup)
            }
            powerPickups.removeAll(deadPickups)

            // Spawn occasional PowerPickup
            if (activeObstacles.size > 0 && kotlin.random.Random.nextFloat() < 0.007f && powerPickups.size < 2) {
                pickupCounter++
                val k = if (hearts < maxHearts && kotlin.random.Random.nextFloat() < 0.45f) PickupKind.HEART
                        else if (kotlin.random.Random.nextFloat() < 0.35f) PickupKind.STAR else PickupKind.COIN
                powerPickups.add(PowerPickup(pickupCounter, 1.25f, 0.54f, k))
            }

            // Spawn next obstacle dynamically to maintain continuous stream
            val lastObstacle = activeObstacles.maxByOrNull { it.x }
            if (lastObstacle == null || lastObstacle.x < 0.65f) {
                obstacleCounter++
                val nextX = (lastObstacle?.x ?: 1.0f) + (kotlin.random.Random.nextFloat() * 0.40f + 0.65f)
                activeObstacles.add(StreamObstacle(obstacleCounter, nextX, signList.random(), pickObstacleKind()))
            }

            // Speed ramps up with score, accelerated 1.3x during Fever.
            val baseSpeed = (0.40f + (score * 0.00012f)).coerceAtMost(0.75f)
            speed = if (feverActive) (baseSpeed * 1.3f).coerceAtMost(1.05f) else baseSpeed

            // 3. Ghost Trails (Geometry Dash Afterimages / rainbow during Fever)
            if (feverActive) {
                ghostTrails.add(
                    GhostTrail(
                        x = 0.15f,
                        y = 0.74f - dinoHop,
                        alpha = 0.85f,
                        color = FEVER_RAINBOW[(now / 45).toInt() % FEVER_RAINBOW.size]
                    )
                )
            } else if (!isGrounded || (now / 70) % 2 == 0L) {
                ghostTrails.add(
                    GhostTrail(
                        x = 0.15f,
                        y = 0.74f - dinoHop,
                        alpha = if (!isGrounded) 0.65f else 0.40f,
                        color = currentBiome.obstacleGlow
                    )
                )
            }
            val deadTrails = mutableListOf<GhostTrail>()
            for (gt in ghostTrails) {
                gt.alpha -= 3.0f * dt
                if (gt.alpha <= 0f) deadTrails.add(gt)
            }
            ghostTrails.removeAll(deadTrails)

            // 4. Shockwave Rings
            val deadRings = mutableListOf<ShockwaveRing>()
            for (ring in shockwaveRings) {
                ring.radius += (ring.maxRadius - ring.radius) * 7.5f * dt + 120f * dt
                ring.alpha -= 1.8f * dt
                if (ring.alpha <= 0f || ring.radius >= ring.maxRadius) deadRings.add(ring)
            }
            shockwaveRings.removeAll(deadRings)

            // 5. Particles
            val deadParticles = mutableListOf<ShockParticle>()
            for (p in shockParticles) {
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.vy += 1.6f * dt // gravity
                p.alpha -= 1.8f * dt
                if (p.alpha <= 0f) deadParticles.add(p)
            }
            shockParticles.removeAll(deadParticles)

            // 5b. Laser Beams decay
            val deadBeams = mutableListOf<LaserBeam>()
            for (b in laserBeams) {
                b.alpha -= 5.0f * dt
                if (b.alpha <= 0f) deadBeams.add(b)
            }
            laserBeams.removeAll(deadBeams)

            // 6. Floating Indicators
            val deadIndicators = mutableListOf<FloatingIndicator>()
            for (fi in floatingIndicators) {
                fi.y -= 0.14f * dt
                fi.alpha -= 1.4f * dt
                if (fi.alpha <= 0f) deadIndicators.add(fi)
            }
            floatingIndicators.removeAll(deadIndicators)

            // 7. Warp Banner fadeout, Biome blend & Screen Shake decay
            if (warpBannerAlpha > 0f) {
                warpBannerAlpha = (warpBannerAlpha - 0.45f * dt).coerceAtLeast(0f)
            }
            if (biomeTransition > 0f) {
                biomeTransition = (biomeTransition - 1.1f * dt).coerceAtLeast(0f)
            }
            if (screenShake > 0f) {
                screenShake = (screenShake - 35f * dt).coerceAtLeast(0f)
            }

            delay(16) // ~60fps
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = currentBiome.obstacleColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Sign Dino Runner",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = currentBiome.title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = currentBiome.groundLineColor
                            )
                        }
                    }
                },
                actions = {
                    // Mode Toggle (ASL vs ISL)
                    FilterChip(
                        selected = currentMode == RecognitionMode.ISL,
                        onClick = {
                            val newMode = if (currentMode == RecognitionMode.ISL) RecognitionMode.ASL else RecognitionMode.ISL
                            onModeChange(newMode)
                        },
                        label = { Text(if (currentMode == RecognitionMode.ISL) "ISL" else "ASL", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.padding(end = 6.dp)
                    )

                    // Switch Camera
                    IconButton(onClick = onToggleCamera) {
                        Icon(Icons.Default.Cameraswitch, contentDescription = "Switch Camera")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(gameState) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startY = down.position.y

                        // Instant 0ms action on touch down!
                        when (gameState) {
                            GameState.IDLE, GameState.GAME_OVER -> startNewGame()
                            GameState.RUNNING -> doJump()
                        }

                        // Swipe down detection while finger is pressed
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dy = change.position.y - startY
                            if (gameState == GameState.RUNNING && dy > 45f) {
                                duckGestureTimer = 0.65f
                            }
                            if (!change.pressed) break
                        }
                    }
                }
        ) {
            // Main Native Canvas Viewport (60 FPS Hardware Accelerated)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height

                // Apply Screen Shake offset if active
                val shakeX = if (screenShake > 0f) (kotlin.random.Random.nextFloat() - 0.5f) * screenShake else 0f
                val shakeY = if (screenShake > 0f) (kotlin.random.Random.nextFloat() - 0.5f) * screenShake else 0f

                val groundY = canvasH * 0.74f + shakeY
                val u = canvasH * 0.0035f // Scaling unit based on screen height
                val frameNow = System.currentTimeMillis()

                // 1. Dynamic Parallax Sky
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = currentBiome.skyGradient,
                        startY = 0f,
                        endY = groundY
                    ),
                    topLeft = Offset(shakeX, 0f),
                    size = Size(canvasW, groundY)
                )

                // 1b. Smooth hyperspace biome-warp blend: fade the previous biome sky out.
                if (biomeTransition > 0f && previousBiome != currentBiome) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = previousBiome.skyGradient,
                            startY = 0f,
                            endY = groundY
                        ),
                        topLeft = Offset(shakeX, 0f),
                        size = Size(canvasW, groundY),
                        alpha = biomeTransition.coerceIn(0f, 1f)
                    )
                }

                // 2. Celestial & Parallax Backgrounds
                when (currentBiome) {
                    GameBiome.DESERT -> {
                        // Golden Sun & Sand Dunes
                        drawCircle(
                            color = Color(0xFFFBBF24).copy(alpha = 0.65f),
                            radius = canvasW * 0.14f,
                            center = Offset(canvasW * 0.82f + shakeX, canvasH * 0.22f)
                        )
                        val duneColor = Color(0xFFD97706).copy(alpha = 0.35f)
                        drawDune(canvasW * 0.2f + shakeX, groundY, canvasW * 0.35f, canvasH * 0.16f, duneColor)
                        drawDune(canvasW * 0.65f + shakeX, groundY, canvasW * 0.45f, canvasH * 0.22f, duneColor)
                    }

                    GameBiome.NEON_SYNTH -> {
                        // Horizon Synthwave Sun
                        val sunY = groundY - canvasH * 0.06f
                        val sunRadius = canvasW * 0.20f
                        drawCircle(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFFF43F5E), Color(0xFFFBBF24)),
                                startY = sunY - sunRadius,
                                endY = sunY + sunRadius
                            ),
                            radius = sunRadius,
                            center = Offset(canvasW * 0.5f + shakeX, sunY)
                        )
                        for (i in 1..6) {
                            val lineY = sunY + (i * 9f)
                            drawLine(
                                color = Color(0xFF0F0C29),
                                start = Offset(canvasW * 0.5f - sunRadius + shakeX, lineY),
                                end = Offset(canvasW * 0.5f + sunRadius + shakeX, lineY),
                                strokeWidth = 3f + i * 0.8f
                            )
                        }
                    }

                    GameBiome.MYSTICAL -> {
                        // Celestial Moon & Spore Fireflies
                        drawCircle(
                            color = Color(0xFF6EE7B7).copy(alpha = 0.22f),
                            radius = canvasW * 0.18f,
                            center = Offset(canvasW * 0.25f + shakeX, canvasH * 0.20f)
                        )
                        drawCircle(
                            color = Color(0xFFA7F3D0).copy(alpha = 0.85f),
                            radius = canvasW * 0.12f,
                            center = Offset(canvasW * 0.25f + shakeX, canvasH * 0.20f)
                        )
                        val nowMs = System.currentTimeMillis()
                        for (i in 0 until 12) {
                            val fx = ((canvasW * (0.08f * i) + nowMs * 0.02f * (i + 1)) % canvasW) + shakeX
                            val fy = groundY - (sin((nowMs * 0.003f + i).toDouble()).toFloat() * 40f + 50f + i * 15f)
                            drawCircle(
                                color = Color(0xFF34D399).copy(alpha = 0.75f),
                                radius = 3.5f,
                                center = Offset(fx, fy)
                            )
                        }
                    }

                    GameBiome.COSMIC_SPACE -> {
                        // Twinkling Parallax Starfield & Ringed Planet
                        val tSec = (System.currentTimeMillis() % 100000L) / 1000f
                        for (star in stars) {
                            val sx = (((star.x * canvasW + tSec * star.speed * 40f) % (canvasW + 20f)) - 10f) + shakeX
                            drawCircle(
                                color = star.color.copy(alpha = 0.8f),
                                radius = star.size,
                                center = Offset(sx, star.y * canvasH)
                            )
                        }
                        val px = canvasW * 0.78f + shakeX
                        val py = canvasH * 0.24f
                        val pr = canvasW * 0.11f
                        drawCircle(Color(0xFF6366F1).copy(alpha = 0.85f), pr, Offset(px, py))
                        drawOval(
                            color = Color(0xFFEC4899).copy(alpha = 0.7f),
                            topLeft = Offset(px - pr * 1.8f, py - pr * 0.45f),
                            size = Size(pr * 3.6f, pr * 0.9f),
                            style = Stroke(width = 4.5f)
                        )
                    }
                }

                // 3. Ground Plane & Grid
                drawRect(
                    color = currentBiome.groundColor,
                    topLeft = Offset(0f, groundY),
                    size = Size(canvasW, canvasH - groundY)
                )
                drawLine(
                    color = currentBiome.groundLineColor,
                    start = Offset(0f, groundY),
                    end = Offset(canvasW, groundY),
                    strokeWidth = 3.5f
                )

                // Perspective ground grid / pebble animation
                if (currentBiome == GameBiome.NEON_SYNTH || currentBiome == GameBiome.COSMIC_SPACE) {
                    val gridScroll = if (gameState == GameState.RUNNING) {
                        (System.currentTimeMillis() * speed * 0.8f) % (canvasW * 0.12f)
                    } else 0f
                    val gridSpacing = canvasW * 0.12f
                    var gx = -gridScroll + shakeX
                    while (gx < canvasW + gridSpacing) {
                        drawLine(
                            color = currentBiome.groundLineColor.copy(alpha = 0.35f),
                            start = Offset(gx, groundY),
                            end = Offset(gx - canvasW * 0.1f, canvasH),
                            strokeWidth = 2f
                        )
                        gx += gridSpacing
                    }
                } else {
                    val groundScroll = if (gameState == GameState.RUNNING) {
                        (System.currentTimeMillis() * speed * 0.4f) % (canvasW * 0.15f)
                    } else 0f
                    val spacing = canvasW * 0.15f
                    var px = -groundScroll + shakeX
                    var idx = 0
                    while (px < canvasW + spacing) {
                        val py = groundY + u * (0.8f + (idx % 3) * 0.6f)
                        val pw = u * (1.2f + (idx % 2) * 0.8f)
                        drawRect(currentBiome.groundLineColor.copy(alpha = 0.5f), Offset(px, py), Size(pw, u * 0.45f))
                        px += spacing
                        idx++
                    }
                }

                // 4. Shockwave Rings (Geometry Dash Wave Takeoff)
                for (ring in shockwaveRings) {
                    drawCircle(
                        color = ring.color.copy(alpha = ring.alpha.coerceIn(0f, 1f)),
                        radius = ring.radius,
                        center = Offset(ring.x * canvasW + shakeX, groundY - u * 2f),
                        style = Stroke(width = 4.5f * ring.alpha)
                    )
                }

                // 4b. Laser Beams (Sign Blaster Energy Ray!)
                for (b in laserBeams) {
                    val startX = b.startX * canvasW + shakeX
                    val endX = b.endX * canvasW + shakeX
                    val ly = b.y * canvasH + shakeY
                    drawLine(
                        color = b.color.copy(alpha = b.alpha.coerceIn(0f, 1f)),
                        start = Offset(startX, ly),
                        end = Offset(endX, ly),
                        strokeWidth = 9f * b.alpha,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.White.copy(alpha = b.alpha.coerceIn(0f, 1f)),
                        start = Offset(startX, ly),
                        end = Offset(endX, ly),
                        strokeWidth = 3f * b.alpha,
                        cap = StrokeCap.Round
                    )
                }

                // 5. Streaming Obstacles, Aerial Flyers & Floating Target Badges!
                for (obs in activeObstacles) {
                    val obstacleScreenX = obs.x * canvasW + shakeX
                    if (obstacleScreenX > -100f && obstacleScreenX < canvasW + 100f) {
                        val isFlyer = obs.kind != ObstacleKind.GROUND
                        val runs = if (isFlyer) {
                            if (legFrame == 0) BIRD_WINGS_UP_RUNS else BIRD_WINGS_DOWN_RUNS
                        } else {
                            currentBiome.obstacleRuns
                        }
                        val cols = if (isFlyer) 17f else 11f
                        val rows = if (isFlyer) 9f else 17f
                        val obsY = when (obs.kind) {
                            ObstacleKind.GROUND -> groundY - 17 * u
                            ObstacleKind.FLYING_LOW -> groundY - 21 * u
                            ObstacleKind.FLYING_HIGH -> groundY - 34 * u
                        }
                        val spriteColor = when {
                            obs.cleared -> currentBiome.obstacleColor.copy(alpha = 0.4f)
                            isFlyer -> Color(0xFFEC4899)
                            else -> currentBiome.obstacleColor
                        }
                        val cx = obstacleScreenX + (cols / 2f) * u
                        val cy = obsY + (rows / 2f) * u

                        // Aerial shadow to ground the flyer visually.
                        if (isFlyer) {
                            drawOval(
                                color = Color.Black.copy(alpha = 0.16f),
                                topLeft = Offset(obstacleScreenX + 2 * u, groundY - u * 1.4f),
                                size = Size(13 * u, u * 2.0f)
                            )
                        }

                        // Obstacle Glow
                        drawCircle(
                            color = if (isFlyer) Color(0x55EC4899) else currentBiome.obstacleGlow,
                            radius = cols * u * 0.5f,
                            center = Offset(cx, cy)
                        )

                        // Obstacle Pixel Sprite
                        for (run in runs) {
                            drawRect(
                                color = spriteColor,
                                topLeft = Offset(obstacleScreenX + run.startCol * u, obsY + run.row * u),
                                size = Size((run.endCol - run.startCol) * u + 0.6f, u + 0.6f)
                            )
                        }

                        // DUCK prompt chevron for low flyers.
                        if (obs.kind == ObstacleKind.FLYING_LOW && !obs.cleared) {
                            val ax = cx
                            val ay = obsY - 4 * u
                            drawLine(Color(0xFFFBBF24), Offset(ax - 3 * u, ay), Offset(ax, ay + 3 * u), strokeWidth = 2.5f, cap = StrokeCap.Round)
                            drawLine(Color(0xFFFBBF24), Offset(ax + 3 * u, ay), Offset(ax, ay + 3 * u), strokeWidth = 2.5f, cap = StrokeCap.Round)
                        }

                        // Floating Holographic Letter Badge directly above obstacle!
                        val badgeY = obsY - 14 * u
                        val badgeRadius = 7.5f * u
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.85f),
                            radius = badgeRadius,
                            center = Offset(cx, badgeY)
                        )
                        drawCircle(
                            color = if (obs.cleared) Color(0xFF34D399) else if (isFlyer) Color(0xFFEC4899) else currentBiome.obstacleColor,
                            radius = badgeRadius,
                            center = Offset(cx, badgeY),
                            style = Stroke(width = 2.5f)
                        )
                    }
                }

                // 5b. PowerPickups (Heart ❤️, Star ⭐, Coin 💎)
                for (pup in powerPickups) {
                    if (pup.collected) continue
                    val px = pup.x * canvasW + shakeX
                    val py = pup.y * canvasH + shakeY
                    val bob = sin((frameNow % 1000L) / 1000f * 2f * PI.toFloat()) * 4f
                    val cy = py + bob

                    when (pup.kind) {
                        PickupKind.HEART -> {
                            drawCircle(Color(0x66FF3366), 14f * u, Offset(px, cy))
                            drawCircle(Color(0xFFFF3366), 8f * u, Offset(px, cy))
                            drawCircle(Color.White, 3f * u, Offset(px - 2f * u, cy - 2f * u))
                        }
                        PickupKind.STAR -> {
                            drawCircle(Color(0x66FDE047), 16f * u, Offset(px, cy))
                            drawCircle(Color(0xFFFDE047), 9f * u, Offset(px, cy))
                            drawCircle(Color.White, 4f * u, Offset(px, cy))
                        }
                        PickupKind.COIN -> {
                            drawCircle(Color(0x6638BDF8), 12f * u, Offset(px, cy))
                            drawCircle(Color(0xFF38BDF8), 8f * u, Offset(px, cy))
                            drawCircle(Color.White, 3f * u, Offset(px, cy))
                        }
                    }
                }

                // 6. Ghost Trails
                for (trail in ghostTrails) {
                    val trailY = groundY - 22 * u - (0.74f - trail.y) * canvasH
                    for (run in DINO_BODY_RUNS) {
                        drawRect(
                            color = trail.color.copy(alpha = trail.alpha * 0.45f),
                            topLeft = Offset(trail.x * canvasW + run.startCol * u + shakeX, trailY + run.row * u),
                            size = Size((run.endCol - run.startCol) * u + 0.6f, u + 0.6f)
                        )
                    }
                }

                // 7. Exploding Particle Sparks
                for (p in shockParticles) {
                    drawCircle(
                        color = p.color.copy(alpha = p.alpha.coerceIn(0f, 1f)),
                        radius = 5.5f * p.alpha,
                        center = Offset(p.x * canvasW + shakeX, p.y * canvasH + shakeY)
                    )
                }

                // 8. Dinosaur Rendering (duck posture + Fever overdrive aura + i-frame flashing)
                val dinoX = canvasW * 0.15f + shakeX
                val hopPx = dinoHop * canvasH * 0.95f
                val duckPose = isDucking && gameState != GameState.GAME_OVER
                val bodyRuns = if (duckPose) DINO_DUCK_BODY_RUNS else DINO_BODY_RUNS
                val dinoBodyRows = if (duckPose) 10 else 17
                val dinoTotalRows = if (duckPose) 12 else 22
                val dinoY = groundY - dinoTotalRows * u - hopPx

                val isInvincibleBlink = (invincibilityTimer > 0f) && ((frameNow / 80) % 2 == 0L)
                val dinoBodyColor = when {
                    isInvincibleBlink -> Color.White.copy(alpha = 0.35f)
                    feverActive -> FEVER_RAINBOW[((frameNow / 70).toInt()) % FEVER_RAINBOW.size]
                    else -> currentBiome.dinoColor
                }

                // Fever rainbow overdrive aura
                if (feverActive) {
                    val pulse = 1f + 0.14f * sin((frameNow % 1000L) / 1000f * 2f * PI.toFloat())
                    for (i in 0 until 3) {
                        drawCircle(
                            color = FEVER_RAINBOW[((frameNow / 90).toInt() + i) % FEVER_RAINBOW.size]
                                .copy(alpha = 0.34f - i * 0.09f),
                            radius = (14f + i * 5f) * u * pulse,
                            center = Offset(dinoX + 8f * u, dinoY + dinoBodyRows * u * 0.5f)
                        )
                    }
                }

                // Ground Shadow
                val shadowLift = (hopPx / (canvasH * 0.22f)).coerceIn(0f, 1f)
                drawOval(
                    color = Color.Black.copy(alpha = 0.28f * (1f - shadowLift * 0.6f)),
                    topLeft = Offset(dinoX + 4 * u, groundY - u * 1.2f),
                    size = Size((if (duckPose) 22f else 18f) * u * (1f - shadowLift * 0.35f), u * 2.4f)
                )

                // Dino Body
                for (run in bodyRuns) {
                    drawRect(
                        color = dinoBodyColor,
                        topLeft = Offset(dinoX + run.startCol * u, dinoY + run.row * u),
                        size = Size((run.endCol - run.startCol) * u + 0.6f, u + 0.6f)
                    )
                }

                // Dino Legs & Eye
                if (gameState == GameState.GAME_OVER) {
                    for (run in DINO_LEGS_DEAD_RUNS) {
                        drawRect(
                            color = currentBiome.dinoColor,
                            topLeft = Offset(dinoX + run.startCol * u, dinoY + 17 * u + run.row * u),
                            size = Size((run.endCol - run.startCol) * u + 0.6f, u + 0.6f)
                        )
                    }
                    val ex = dinoX + 14 * u
                    val ey = dinoY + 3.2f * u
                    val es = u * 1.3f
                    drawLine(Color.Red, Offset(ex - es, ey - es), Offset(ex + es, ey + es), strokeWidth = 3.5f)
                    drawLine(Color.Red, Offset(ex + es, ey - es), Offset(ex - es, ey + es), strokeWidth = 3.5f)
                } else {
                    val legRuns = if (duckPose) {
                        if (legFrame == 0) DINO_DUCK_LEGS_A_RUNS else DINO_DUCK_LEGS_B_RUNS
                    } else {
                        if (!isGrounded || legFrame == 0) DINO_LEGS_A_RUNS else DINO_LEGS_B_RUNS
                    }
                    for (run in legRuns) {
                        drawRect(
                            color = dinoBodyColor,
                            topLeft = Offset(dinoX + run.startCol * u, dinoY + dinoBodyRows * u + run.row * u),
                            size = Size((run.endCol - run.startCol) * u + 0.6f, u + 0.6f)
                        )
                    }
                    val eyeColor = if (currentBiome == GameBiome.NEON_SYNTH) Color(0xFF06B6D4) else Color(0xFF0F172A)
                    if (duckPose) {
                        drawRect(eyeColor, Offset(dinoX + 14f * u, dinoY + 1.5f * u), Size(u * 1.5f, u * 1.5f))
                    } else {
                        drawRect(eyeColor, Offset(dinoX + 13.5f * u, dinoY + 2.5f * u), Size(u * 1.5f, u * 1.5f))
                    }
                }

                // Cosmic Astronaut Helmet Visor
                if (currentBiome == GameBiome.COSMIC_SPACE && !duckPose) {
                    drawOval(
                        color = Color(0xFF38BDF8).copy(alpha = 0.85f),
                        topLeft = Offset(dinoX + 11.5f * u, dinoY + 1.2f * u),
                        size = Size(5.5f * u, 4.5f * u)
                    )
                }
            }

            // Top HUD Overlay (Scoreboard, Hearts & Streak Multiplier)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 3 Hearts Health Bar
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF3366).copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (i in 1..maxHearts) {
                                val isFull = i <= hearts
                                Icon(
                                    imageVector = if (isFull) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Heart $i",
                                    tint = if (isFull) Color(0xFFFF3366) else Color.White.copy(alpha = 0.28f),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // High Score & Current Score Pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, currentBiome.groundLineColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "HI ${highScore.toString().padStart(5, '0')}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = score.toString().padStart(5, '0'),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                    }
                }

                // Streak / Multiplier Pill
                if ((streak > 0 || comboMultiplier > 1) && gameState == GameState.RUNNING) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (feverActive) Color(0xFFBE123C).copy(alpha = 0.9f) else Color(0xFFD97706).copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (feverActive) Color(0xFFFFE4E6) else Color(0xFFFDE68A)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (feverActive) "FEVER ${comboMultiplier}×" else "${streak}x • ${comboMultiplier}×",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Fever / Overdrive charge meter (top-left, below the HUD row)
            if (gameState == GameState.RUNNING) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (feverActive) Color(0xFFFF3D7F) else Color.White.copy(alpha = 0.18f)
                    ),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 112.dp)
                        .width(148.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                        Text(
                            text = if (feverActive) "★ FEVER OVERDRIVE ${feverTimeLeft.toInt() + 1}s" else "OVERDRIVE",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            color = if (feverActive) Color(0xFFFF3D7F) else Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(if (feverActive) 1f else feverMeter.coerceIn(0f, 1f))
                                    .background(
                                        if (feverActive) Brush.horizontalGradient(FEVER_RAINBOW)
                                        else Brush.horizontalGradient(listOf(Color(0xFF06B6D4), Color(0xFF34D399)))
                                    )
                            )
                        }
                    }
                }
            }

            // Central Sleek Target Sign HUD (Floating at Top-Center, NEVER blocking the screen!)
            currentUpcomingObstacle?.let { targetObs ->
                val target = targetObs.signItem.first.uppercase().trim()
                val isMatched = debounceState.candidate.uppercase().trim() == target ||
                    debounceState.top3.any { it.first.uppercase().trim() == target && it.second >= 0.25f }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isMatched) Color(0xFF064E3B).copy(alpha = 0.92f) else Color(0xFF0F172A).copy(alpha = 0.88f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (isMatched) Color(0xFF34D399) else currentBiome.obstacleColor.copy(alpha = 0.8f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 58.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Target Letter Pill
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isMatched) Color(0xFF10B981) else currentBiome.obstacleColor
                        ) {
                            Text(
                                text = targetObs.signItem.first,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = targetObs.signItem.second,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFE2E8F0)
                            )
                            val actionHint = when (targetObs.kind) {
                                ObstacleKind.FLYING_LOW -> "DUCK! Sign or hold lower screen"
                                ObstacleKind.FLYING_HIGH -> "Run under it • sign or TAP"
                                else -> "Sign letter or TAP to jump"
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isMatched) "✓ MATCHED! DODGING..." else (if (debounceState.candidate.isNotBlank()) "Saw: ${debounceState.candidate}" else actionHint),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMatched) Color(0xFF34D399) else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }
            }

            // Floating Warp Banner Notification
            if (warpBannerAlpha > 0f && warpBannerText != null) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.90f * warpBannerAlpha)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, currentBiome.obstacleColor.copy(alpha = warpBannerAlpha)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 80.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = currentBiome.obstacleColor)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = warpBannerText!!,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                    }
                }
            }

            // Floating Indicators (+200 SIGN BONUS / +100 JUMP)
            for (fi in floatingIndicators) {
                Text(
                    text = fi.text,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = fi.color.copy(alpha = fi.alpha.coerceIn(0f, 1f)),
                    modifier = Modifier
                        .offset(x = 80.dp, y = (fi.y * 500).dp)
                )
            }

            // Start Screen Overlay
            if (gameState == GameState.IDLE) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.95f)),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, currentBiome.obstacleColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = currentBiome.obstacleColor,
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "CHROME DINO RUNNER",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Arcade endless runner with 8-bit audio & Geometry Dash juice!\nTAP to jump • HOLD LOWER SCREEN or SWIPE DOWN to duck under flyers\nSign upcoming letters for a +200 SIGN BONUS. Fill the OVERDRIVE meter for FEVER MODE!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { startNewGame() },
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = currentBiome.obstacleColor),
                                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp)
                            ) {
                                Text("TAP TO START", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                            }
                        }
                    }
                }
            }

            // Game Over Modal Dialog
            if (gameState == GameState.GAME_OVER) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.96f)),
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFEF4444)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                text = "CRASHED!",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFEF4444)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Final Score: $score  •  High: $highScore",
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0)
                            )
                            if (clearedSigns.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Cleared: ${clearedSigns.joinToString(" ")}",
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF34D399),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(22.dp))
                            Button(
                                onClick = { startNewGame() },
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("TAP TO REPLAY", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }

            // Bottom Biome Selector & Cleared Signs Trail (Positioned safely above controls)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = if (gameState == GameState.RUNNING) 80.dp else 16.dp)
            ) {
                // Biome Switcher Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    items(allBiomes) { b ->
                        val isSelected = b == currentBiome
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) b.obstacleColor else Color.Black.copy(alpha = 0.55f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, b.obstacleColor.copy(alpha = 0.6f)),
                            modifier = Modifier.clickable {
                                currentBiomeIndex = allBiomes.indexOf(b)
                                warpBannerText = "WARP ACTIVE • ${b.title}"
                                warpBannerAlpha = 1.0f
                            }
                        ) {
                            Text(
                                text = b.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Cleared Signs Trail
                if (clearedSigns.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CLEARED: ",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                text = clearedSigns.takeLast(8).joinToString(" "),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF34D399)
                            )
                        }
                    }
                }
            }

            // Floating Arcade Virtual Controls (Dual Thumb Buttons: Duck on Left, Jump on Right)
            if (gameState == GameState.RUNNING) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Left Thumb: DUCK Button
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = if (isDucking) Color(0xFFEC4899) else Color.Black.copy(alpha = 0.70f),
                        border = androidx.compose.foundation.BorderStroke(
                            2.dp,
                            if (isDucking) Color.White else Color(0xFFEC4899).copy(alpha = 0.85f)
                        ),
                        modifier = Modifier
                            .size(width = 110.dp, height = 56.dp)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    duckHoldActive = true
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        if (ev.changes.all { !it.pressed }) break
                                    }
                                    duckHoldActive = false
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Duck", tint = Color.White, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("DUCK", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                        }
                    }

                    // Right Thumb: JUMP Button
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = currentBiome.obstacleColor.copy(alpha = 0.90f),
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                        modifier = Modifier
                            .size(width = 110.dp, height = 56.dp)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    doJump()
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Jump", tint = Color.White, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("JUMP", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            // Floating PiP Camera Viewport in Top-Right
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 12.dp)
                    .size(width = 115.dp, height = 155.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(2.dp, currentBiome.obstacleColor.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Camera Preview
                    AndroidView(
                        factory = { _ ->
                            (previewView.parent as? android.view.ViewGroup)?.removeView(previewView)
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Cybernetic Hand Skeleton Overlay inside PiP (True Mirror for Front Camera!)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        val camW = 480f
                        val camH = 640f
                        val scale = maxOf(w / camW, h / camH)
                        val scaledW = camW * scale
                        val scaledH = camH * scale
                        val offX = (w - scaledW) / 2f
                        val offY = (h - scaledH) / 2f

                        for (hand in handLandmarks) {
                            val pts = hand.points
                            if (pts.size < 21) continue
                            val boneColor = if (hand.isRightHand) Color(0xFF06B6D4) else Color(0xFFF43F5E)
                            val jointColor = if (hand.isRightHand) Color(0xFF34D399) else Color(0xFFFB923C)

                            // Bones with mirror correction
                            for ((s, e) in HAND_CONNECTIONS) {
                                if (s < pts.size && e < pts.size) {
                                    val sx = if (hand.isFrontCamera) (1.0f - pts[s].x) else pts[s].x
                                    val ex = if (hand.isFrontCamera) (1.0f - pts[e].x) else pts[e].x
                                    val p1 = Offset(sx * scaledW + offX, pts[s].y * scaledH + offY)
                                    val p2 = Offset(ex * scaledW + offX, pts[e].y * scaledH + offY)
                                    drawLine(boneColor, p1, p2, strokeWidth = 3f, cap = StrokeCap.Round)
                                }
                            }

                            // Joints with mirror correction
                            for (p in pts) {
                                val px = if (hand.isFrontCamera) (1.0f - p.x) else p.x
                                drawCircle(
                                    jointColor,
                                    radius = 3.5f,
                                    center = Offset(px * scaledW + offX, p.y * scaledH + offY)
                                )
                            }
                        }
                    }

                    // Bottom Detected Sign Pill inside PiP
                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp)
                    ) {
                        val upcoming = currentUpcomingObstacle
                        val target = upcoming?.signItem?.first?.uppercase()?.trim() ?: ""
                        val isMatched = target.isNotBlank() && (
                            debounceState.candidate.uppercase().trim() == target ||
                            debounceState.top3.any { it.first.uppercase().trim() == target && it.second >= 0.25f }
                        )

                        Text(
                            text = debounceState.candidate.ifBlank { "--" },
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            color = if (isMatched) Color(0xFF34D399) else Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawDune(cx: Float, groundY: Float, width: Float, height: Float, color: Color) {
    drawOval(
        color = color,
        topLeft = Offset(cx - width / 2f, groundY - height),
        size = Size(width, height * 2f)
    )
}

/**
 * Gesture-based duck detection from MediaPipe hand landmarks:
 * a closed fist (all fingertips pulled in toward the wrist relative to the
 * palm span) or a low hanging wrist both trigger the duck dive.
 */
private fun detectDuckGesture(hands: List<HandLandmarks>): Boolean {
    if (hands.isEmpty()) return false
    for (hand in hands) {
        val pts = hand.points
        if (pts.size < 21) continue
        val wrist = pts[0]
        val middleMcp = pts[9]
        val dxm = middleMcp.x - wrist.x
        val dym = middleMcp.y - wrist.y
        val dzm = middleMcp.z - wrist.z
        val scale = sqrt(dxm * dxm + dym * dym + dzm * dzm).coerceAtLeast(1e-4f)

        var extSum = 0f
        for (tip in intArrayOf(4, 8, 12, 16, 20)) {
            val p = pts[tip]
            val dx = p.x - wrist.x
            val dy = p.y - wrist.y
            val dz = p.z - wrist.z
            extSum += sqrt(dx * dx + dy * dy + dz * dz) / scale
        }
        val avgTipSpan = extSum / 5f
        // Closed fist: fingertips sit close to the wrist relative to the palm span.
        if (avgTipSpan < 1.28f) return true
        // Low wrist / downward fist posture.
        if (wrist.y > 0.72f) return true
    }
    return false
}
