package ai.signora.ui.screens

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.ui.graphics.nativeCanvas
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
    ".....XXX.....",
    "....XXXXX....",
    "....XXXXX....",
    "...XXXXXXX...",
    "..XXXXXXXXX..",
    ".XXXXXXXXXXX."
)

private val SATELLITE_COSMIC = listOf(
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

private val RUNS_DESERT = parseRuns(CACTUS_DESERT)
private val RUNS_NEON = parseRuns(PYLON_NEON)
private val RUNS_MYSTICAL = parseRuns(SHROOM_MYSTICAL)
private val RUNS_COSMIC = parseRuns(SATELLITE_COSMIC)

// Rainbow chromatic trail palette used during FEVER overdrive mode
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

// Dynamic Obstacle in the runner
private data class StreamObstacle(
    val id: Long,
    var x: Float,
    val signItem: Pair<String, String>,
    var cleared: Boolean = false,
    var bonusAwarded: Boolean = false
)

private enum class GameState {
    IDLE,
    RUNNING,
    ENCOUNTER,
    JUMPING,
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
    "E" to "Curled fingers touching thumb",
    "NAMASTE" to "Two palms joined at chest",
    "HELLO" to "Salute / wave from forehead",
    "HELP" to "Closed fist on open palm",
    "EMERGENCY" to "Wave hands across chest",
    "DOCTOR" to "Tap wrist pulse with 2 fingers",
    "THANK_YOU" to "Flat hand moved forward from chin",
    "PLEASE" to "Circular rubbing motion over chest",
    "CONGRATS" to "Clapping hands celebration",
    "RESPECT" to "Bowed hands salute",
    "PEACE" to "Two fingers peace V gesture"
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

    // Procedural 8-bit synth audio engine
    val audio = remember { GameAudioTrack() }
    DisposableEffect(Unit) {
        onDispose { audio.release() }
    }

    var highScore by remember { mutableStateOf(prefs.getInt("high_score", 0)) }
    var score by remember { mutableStateOf(0) }
    var streak by remember { mutableStateOf(0) }
    var gameState by remember { mutableStateOf(GameState.IDLE) }
    var speed by remember { mutableStateOf(0.42f) }

    // 3 Hearts Health & Invincibility i-frames
    val maxHearts = 3
    var hearts by remember { mutableStateOf(maxHearts) }
    var invincibilityTimer by remember { mutableStateOf(0f) }

    // Encounter Wait & Jump Mechanics
    val encounterMaxTime = 6.0f
    var encounterTimer by remember { mutableStateOf(encounterMaxTime) }
    var jumpProgress by remember { mutableStateOf(0f) }
    val jumpDuration = 0.65f
    var dinoHop by remember { mutableStateOf(0f) }
    var legFrame by remember { mutableStateOf(0) }

    // Fever / Overdrive Mode
    var feverMeter by remember { mutableStateOf(0f) }
    var feverActive by remember { mutableStateOf(false) }
    var feverTimeLeft by remember { mutableStateOf(0f) }

    // Biome System
    val allBiomes = GameBiome.values()
    var currentBiomeIndex by remember { mutableStateOf(0) }
    var previousBiomeIndex by remember { mutableStateOf(0) }
    var biomeTransition by remember { mutableStateOf(0f) }
    val currentBiome = allBiomes[currentBiomeIndex % allBiomes.size]
    val previousBiome = allBiomes[previousBiomeIndex % allBiomes.size]
    var warpBannerText by remember { mutableStateOf<String?>(null) }
    var warpBannerAlpha by remember { mutableStateOf(0f) }

    // Screen Shake effect
    var screenShake by remember { mutableStateOf(0f) }

    // Obstacle & Pickup Pipeline
    val signList = if (currentMode == RecognitionMode.ISL) ISL_GAME_LETTERS else ASL_GAME_LETTERS
    val activeObstacles = remember { mutableStateListOf<StreamObstacle>() }
    val powerPickups = remember { mutableStateListOf<PowerPickup>() }
    var pickupCounter by remember { mutableStateOf(0L) }
    var clearedSigns by remember { mutableStateOf(listOf<String>()) }
    var obstacleCounter by remember { mutableStateOf(0L) }

    // Particles, Ghost Trails & Shockwaves
    val shockParticles = remember { mutableStateListOf<ShockParticle>() }
    val ghostTrails = remember { mutableStateListOf<GhostTrail>() }
    val shockwaveRings = remember { mutableStateListOf<ShockwaveRing>() }
    val floatingIndicators = remember { mutableStateListOf<FloatingIndicator>() }

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

    fun currentMultiplier(): Int {
        val combo = 1 + (streak / 4)
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

    fun activateFever() {
        feverActive = true
        feverTimeLeft = 8f
        feverMeter = 1f
        screenShake = 16f
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        floatingIndicators.add(FloatingIndicator("★ FEVER OVERDRIVE ★", 0.14f, 0.40f, color = Color(0xFFFF3D7F)))
        shockwaveRings.add(ShockwaveRing(0.16f, 0.73f, 10f, 150f, 0.95f, Color(0xFFFF3D7F)))
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

    fun startNewGame() {
        score = 0
        streak = 0
        hearts = 3
        invincibilityTimer = 0f
        speed = 0.42f
        dinoHop = 0f
        jumpProgress = 0f
        encounterTimer = encounterMaxTime
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
        warpBannerText = null
        warpBannerAlpha = 0f
        biomeTransition = 0f
        previousBiomeIndex = currentBiomeIndex
        screenShake = 0f

        obstacleCounter++
        activeObstacles.add(StreamObstacle(obstacleCounter, 1.25f, signList.random()))

        gameState = GameState.RUNNING
    }

    // Active obstacle upcoming or waiting in encounter
    val currentUpcomingObstacle = activeObstacles.firstOrNull { !it.cleared || it.x > 0.02f }

    // Manual Leap Fallback (Triggered by tapping anywhere on the screen)
    fun doManualJump() {
        if (gameState == GameState.ENCOUNTER) {
            val upcoming = currentUpcomingObstacle
            if (upcoming != null && !upcoming.cleared) {
                upcoming.cleared = true
                val pts = 60 * currentMultiplier()
                score += pts
                if (score > highScore) {
                    highScore = score
                    prefs.edit().putInt("high_score", highScore).apply()
                }
                audio.jump()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                floatingIndicators.add(
                    FloatingIndicator("+$pts MANUAL LEAP", upcoming.x.coerceIn(0.20f, 0.70f), 0.48f, color = Color(0xFF38BDF8))
                )
                jumpProgress = 0f
                gameState = GameState.JUMPING
            }
        }
    }

    // Camera Gesture Recognition: evaluates target sign while Dino is waiting in front of cactus!
    LaunchedEffect(debounceState.candidate, debounceState.top3, debounceState.lastConfirmed, gameState) {
        if (gameState == GameState.ENCOUNTER) {
            val upcoming = currentUpcomingObstacle
            if (upcoming != null && !upcoming.cleared) {
                val target = upcoming.signItem.first.uppercase().trim()
                val candidateMatch = debounceState.candidate.uppercase().trim() == target
                val top3Match = debounceState.top3.any { (lbl, prob) ->
                    lbl.uppercase().trim() == target && prob >= 0.20f
                }
                val lastConfirmedMatch = debounceState.lastConfirmed.uppercase().trim() == target

                if (candidateMatch || top3Match || lastConfirmedMatch) {
                    upcoming.cleared = true
                    upcoming.bonusAwarded = true
                    if (target !in clearedSigns) {
                        clearedSigns = clearedSigns + target
                    }
                    streak++
                    val pts = (250 + (streak * 25)) * currentMultiplier()
                    score += pts
                    if (score > highScore) {
                        highScore = score
                        prefs.edit().putInt("high_score", highScore).apply()
                    }

                    if (streak % 5 == 0 && hearts < maxHearts) {
                        hearts = (hearts + 1).coerceAtMost(maxHearts)
                        audio.powerup()
                        floatingIndicators.add(FloatingIndicator("+1 HEART! ❤️", 0.20f, 0.42f, color = Color(0xFFFF3366)))
                    }

                    chargeFever(0.25f)
                    audio.signBonus()
                    audio.jump()
                    screenShake = 10f
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                    // Confetti particles at the obstacle
                    for (i in 0 until 24) {
                        shockParticles.add(
                            ShockParticle(
                                x = upcoming.x + (kotlin.random.Random.nextFloat() - 0.5f) * 0.05f,
                                y = 0.70f,
                                vx = (kotlin.random.Random.nextFloat() - 0.5f) * 1.3f,
                                vy = -(kotlin.random.Random.nextFloat() * 1.0f + 0.1f),
                                color = if (feverActive) FEVER_RAINBOW.random() else currentBiome.particleColors.random()
                            )
                        )
                    }
                    shockwaveRings.add(
                        ShockwaveRing(upcoming.x, 0.70f, 10f, 100f, 0.9f, Color(0xFF34D399))
                    )
                    floatingIndicators.add(
                        FloatingIndicator("+$pts SIGN BONUS! 🌟", upcoming.x.coerceIn(0.20f, 0.75f), 0.48f, color = Color(0xFF34D399))
                    )

                    jumpProgress = 0f
                    gameState = GameState.JUMPING
                }
            }
        }
    }

    // 60 FPS Game Loop with Encounter & Jump Mechanics
    LaunchedEffect(gameState) {
        var lastTime = System.currentTimeMillis()
        while (gameState == GameState.RUNNING || gameState == GameState.ENCOUNTER || gameState == GameState.JUMPING) {
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

            when (gameState) {
                GameState.RUNNING -> {
                    legFrame = if ((now / 90) % 2 == 0L) 0 else 1
                    dinoHop = 0f

                    // Move obstacles towards Dino
                    for (obs in activeObstacles) {
                        obs.x -= speed * dt

                        // Halts when reaching Dino at x = 0.28f!
                        if (obs.x <= 0.28f && !obs.cleared) {
                            obs.x = 0.28f
                            gameState = GameState.ENCOUNTER
                            encounterTimer = encounterMaxTime
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            break
                        }
                    }

                    // PowerPickups streaming and collection
                    val deadPickups = mutableListOf<PowerPickup>()
                    for (pup in powerPickups) {
                        pup.x -= speed * dt
                        if (pup.x in 0.13f..0.21f && !pup.collected) {
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
                        if (pup.x < -0.2f) deadPickups.add(pup)
                    }
                    powerPickups.removeAll(deadPickups)

                    // Occasional PowerPickup during run
                    if (kotlin.random.Random.nextFloat() < 0.005f && powerPickups.size < 1) {
                        pickupCounter++
                        val k = if (hearts < maxHearts && kotlin.random.Random.nextFloat() < 0.45f) PickupKind.HEART
                                else if (kotlin.random.Random.nextFloat() < 0.35f) PickupKind.STAR else PickupKind.COIN
                        powerPickups.add(PowerPickup(pickupCounter, 1.20f, 0.54f, k))
                    }

                    score += 1
                }

                GameState.ENCOUNTER -> {
                    // Dino is waiting in front of the cactus
                    dinoHop = 0f
                    encounterTimer -= dt

                    if (encounterTimer <= 0f) {
                        // Encounter timeout! Dino tripped!
                        hearts--
                        streak = 0
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        screenShake = 22f

                        if (hearts <= 0) {
                            audio.stopFever()
                            audio.crash()
                            gameState = GameState.GAME_OVER
                        } else {
                            audio.hurt()
                            invincibilityTimer = 1.8f
                            floatingIndicators.add(FloatingIndicator("-1 HEART! 💔", 0.20f, 0.45f, color = Color(0xFFEF4444)))
                            currentUpcomingObstacle?.cleared = true
                            jumpProgress = 0f
                            gameState = GameState.JUMPING
                        }
                    }
                }

                GameState.JUMPING -> {
                    jumpProgress += dt / jumpDuration
                    val t = jumpProgress.coerceIn(0f, 1f)
                    // Parabolic jump arc: sin(pi * t)
                    dinoHop = sin(t * PI.toFloat()) * 0.20f

                    // Move obstacle backwards underneath Dino: from 0.28f to -0.24f
                    val upcoming = currentUpcomingObstacle
                    if (upcoming != null) {
                        upcoming.x = 0.28f - t * 0.52f
                    }

                    // Ghost trails behind Dino during the leap
                    if (feverActive) {
                        ghostTrails.add(
                            GhostTrail(
                                x = 0.16f,
                                y = 0.74f - dinoHop,
                                alpha = 0.85f,
                                color = FEVER_RAINBOW[(now / 45).toInt() % FEVER_RAINBOW.size]
                            )
                        )
                    } else {
                        ghostTrails.add(
                            GhostTrail(
                                x = 0.16f,
                                y = 0.74f - dinoHop,
                                alpha = 0.55f,
                                color = currentBiome.obstacleGlow
                            )
                        )
                    }

                    if (jumpProgress >= 1.0f) {
                        dinoHop = 0f
                        jumpProgress = 0f
                        spawnDust()
                        activeObstacles.removeAll { it.cleared || it.x < 0.05f }

                        // Schedule next obstacle ahead!
                        obstacleCounter++
                        val nextX = 1.30f + kotlin.random.Random.nextFloat() * 0.30f
                        activeObstacles.add(StreamObstacle(obstacleCounter, nextX, signList.random()))

                        checkBiomeWarp()
                        gameState = GameState.RUNNING
                    }
                }

                else -> {}
            }

            // Speed ramps up slightly with score
            val baseSpeed = (0.42f + (score * 0.00008f)).coerceAtMost(0.70f)
            speed = if (feverActive) (baseSpeed * 1.3f).coerceAtMost(0.95f) else baseSpeed

            // Ghost Trails decay
            val deadTrails = mutableListOf<GhostTrail>()
            for (gt in ghostTrails) {
                gt.alpha -= 3.0f * dt
                if (gt.alpha <= 0f) deadTrails.add(gt)
            }
            ghostTrails.removeAll(deadTrails)

            // Shockwave Rings decay
            val deadRings = mutableListOf<ShockwaveRing>()
            for (ring in shockwaveRings) {
                ring.radius += (ring.maxRadius - ring.radius) * 7.5f * dt + 120f * dt
                ring.alpha -= 1.8f * dt
                if (ring.alpha <= 0f || ring.radius >= ring.maxRadius) deadRings.add(ring)
            }
            shockwaveRings.removeAll(deadRings)

            // Particles decay
            val deadParticles = mutableListOf<ShockParticle>()
            for (p in shockParticles) {
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.vy += 1.6f * dt
                p.alpha -= 1.8f * dt
                if (p.alpha <= 0f) deadParticles.add(p)
            }
            shockParticles.removeAll(deadParticles)

            // Floating Indicators decay
            val deadIndicators = mutableListOf<FloatingIndicator>()
            for (fi in floatingIndicators) {
                fi.y -= 0.14f * dt
                fi.alpha -= 1.4f * dt
                if (fi.alpha <= 0f) deadIndicators.add(fi)
            }
            floatingIndicators.removeAll(deadIndicators)

            // Warp Banner fadeout, Biome blend & Screen Shake decay
            if (warpBannerAlpha > 0f) {
                warpBannerAlpha = (warpBannerAlpha - 0.45f * dt).coerceAtLeast(0f)
            }
            if (biomeTransition > 0f) {
                biomeTransition = (biomeTransition - 1.1f * dt).coerceAtLeast(0f)
            }
            if (screenShake > 0f) {
                screenShake = (screenShake - 35f * dt).coerceAtLeast(0f)
            }

            delay(16)
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
                    FilterChip(
                        selected = currentMode == RecognitionMode.ISL,
                        onClick = {
                            val newMode = if (currentMode == RecognitionMode.ISL) RecognitionMode.ASL else RecognitionMode.ISL
                            onModeChange(newMode)
                        },
                        label = { Text(if (currentMode == RecognitionMode.ISL) "ISL" else "ASL", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.padding(end = 6.dp)
                    )

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
                        awaitFirstDown(requireUnconsumed = false)
                        when (gameState) {
                            GameState.IDLE, GameState.GAME_OVER -> startNewGame()
                            GameState.ENCOUNTER -> doManualJump()
                            GameState.RUNNING -> spawnDust()
                            GameState.JUMPING -> {}
                        }
                    }
                }
        ) {
            // Main Native Canvas Viewport (60 FPS Hardware Accelerated)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height

                val shakeX = if (screenShake > 0f) (kotlin.random.Random.nextFloat() - 0.5f) * screenShake else 0f
                val shakeY = if (screenShake > 0f) (kotlin.random.Random.nextFloat() - 0.5f) * screenShake else 0f

                val groundY = canvasH * 0.74f + shakeY
                val u = canvasH * 0.0035f
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

                // 4. Shockwave Rings
                for (ring in shockwaveRings) {
                    drawCircle(
                        color = ring.color.copy(alpha = ring.alpha.coerceIn(0f, 1f)),
                        radius = ring.radius,
                        center = Offset(ring.x * canvasW + shakeX, groundY - u * 2f),
                        style = Stroke(width = 4.5f * ring.alpha)
                    )
                }

                // 5. Obstacles & Floating Target Badges
                val badgePaint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 13f * u
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    isAntiAlias = true
                }

                for (obs in activeObstacles) {
                    val obstacleScreenX = obs.x * canvasW + shakeX
                    if (obstacleScreenX > -100f && obstacleScreenX < canvasW + 100f) {
                        val runs = currentBiome.obstacleRuns
                        val cols = 11f
                        val rows = 17f
                        val obsY = groundY - 17 * u
                        val spriteColor = if (obs.cleared) currentBiome.obstacleColor.copy(alpha = 0.4f) else currentBiome.obstacleColor
                        val cx = obstacleScreenX + (cols / 2f) * u
                        val cy = obsY + (rows / 2f) * u

                        // Obstacle Glow
                        drawCircle(
                            color = currentBiome.obstacleGlow,
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

                        // Floating Holographic Letter Badge directly above obstacle!
                        val badgeY = obsY - 14 * u
                        val badgeRadius = 8.5f * u

                        // Pulsing ring during encounter
                        if (gameState == GameState.ENCOUNTER && obs == currentUpcomingObstacle) {
                            val pulse = 1f + 0.18f * sin((frameNow % 600L) / 600f * 2f * PI.toFloat())
                            drawCircle(
                                color = currentBiome.obstacleColor.copy(alpha = 0.40f),
                                radius = badgeRadius * pulse,
                                center = Offset(cx, badgeY)
                            )
                        }

                        drawCircle(
                            color = Color.Black.copy(alpha = 0.85f),
                            radius = badgeRadius,
                            center = Offset(cx, badgeY)
                        )
                        drawCircle(
                            color = if (obs.cleared) Color(0xFF34D399) else currentBiome.obstacleColor,
                            radius = badgeRadius,
                            center = Offset(cx, badgeY),
                            style = Stroke(width = 2.5f)
                        )

                        // Render target letter inside holographic badge
                        drawContext.canvas.nativeCanvas.drawText(
                            obs.signItem.first,
                            cx,
                            badgeY + 4.5f * u,
                            badgePaint
                        )
                    }
                }

                // 5b. PowerPickups
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

                // 8. Dinosaur Rendering
                val dinoX = canvasW * 0.16f + shakeX
                val hopPx = dinoHop * canvasH * 0.95f
                val dinoY = groundY - 22 * u - hopPx

                val isInvincibleBlink = (invincibilityTimer > 0f) && ((frameNow / 80) % 2 == 0L)
                val dinoBodyColor = when {
                    isInvincibleBlink -> Color.White.copy(alpha = 0.35f)
                    feverActive -> FEVER_RAINBOW[((frameNow / 70).toInt()) % FEVER_RAINBOW.size]
                    else -> currentBiome.dinoColor
                }

                if (feverActive) {
                    val pulse = 1f + 0.14f * sin((frameNow % 1000L) / 1000f * 2f * PI.toFloat())
                    for (i in 0 until 3) {
                        drawCircle(
                            color = FEVER_RAINBOW[((frameNow / 90).toInt() + i) % FEVER_RAINBOW.size]
                                .copy(alpha = 0.34f - i * 0.09f),
                            radius = (14f + i * 5f) * u * pulse,
                            center = Offset(dinoX + 8f * u, dinoY + 8.5f * u)
                        )
                    }
                }

                // Ground Shadow
                val shadowLift = (hopPx / (canvasH * 0.22f)).coerceIn(0f, 1f)
                drawOval(
                    color = Color.Black.copy(alpha = 0.28f * (1f - shadowLift * 0.6f)),
                    topLeft = Offset(dinoX + 4 * u, groundY - u * 1.2f),
                    size = Size(18f * u * (1f - shadowLift * 0.35f), u * 2.4f)
                )

                // Dino Body
                for (run in DINO_BODY_RUNS) {
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
                    val legRuns = when (gameState) {
                        GameState.JUMPING -> DINO_LEGS_A_RUNS
                        GameState.ENCOUNTER -> DINO_LEGS_A_RUNS
                        else -> if (legFrame == 0) DINO_LEGS_A_RUNS else DINO_LEGS_B_RUNS
                    }
                    for (run in legRuns) {
                        drawRect(
                            color = dinoBodyColor,
                            topLeft = Offset(dinoX + run.startCol * u, dinoY + 17 * u + run.row * u),
                            size = Size((run.endCol - run.startCol) * u + 0.6f, u + 0.6f)
                        )
                    }
                    val eyeColor = if (currentBiome == GameBiome.NEON_SYNTH) Color(0xFF06B6D4) else Color(0xFF0F172A)
                    drawRect(eyeColor, Offset(dinoX + 13.5f * u, dinoY + 2.5f * u), Size(u * 1.5f, u * 1.5f))
                }

                // Cosmic Astronaut Helmet Visor
                if (currentBiome == GameBiome.COSMIC_SPACE) {
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
                if (streak > 0 && gameState != GameState.IDLE) {
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
                                text = if (feverActive) "FEVER ${currentMultiplier()}×" else "${streak}x • ${currentMultiplier()}×",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // ENCOUNTER CHALLENGE HUD CARD (Appears when Dino halts in front of cactus)
            if (gameState == GameState.ENCOUNTER && currentUpcomingObstacle != null) {
                val obs = currentUpcomingObstacle
                val targetSign = obs.signItem.first
                val targetHint = obs.signItem.second
                val progress = (encounterTimer / encounterMaxTime).coerceIn(0f, 1f)
                val isCandidateMatch = debounceState.candidate.uppercase().trim() == targetSign.uppercase().trim()

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.95f)),
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp,
                        if (isCandidateMatch) Color(0xFF34D399) else currentBiome.obstacleColor
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 64.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth(0.92f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = currentBiome.obstacleColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "OBSTACLE AHEAD • MAKE SIGN",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = currentBiome.obstacleColor
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (encounterTimer < 2.0f) Color(0xFFEF4444).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = String.format("%.1fs", encounterTimer),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    color = if (encounterTimer < 2.0f) Color(0xFFEF4444) else Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = when {
                                progress > 0.5f -> Color(0xFF34D399)
                                progress > 0.25f -> Color(0xFFFBBF24)
                                else -> Color(0xFFEF4444)
                            },
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = currentBiome.obstacleColor.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(2.dp, currentBiome.obstacleColor)
                            ) {
                                Text(
                                    text = targetSign,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sign: $targetSign",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Text(
                                    text = targetHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFCBD5E1),
                                    maxLines = 2
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCandidateMatch) Color(0xFF34D399).copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.4f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isCandidateMatch) Color(0xFF34D399) else Color.White.copy(alpha = 0.15f)
                                )
                            ) {
                                Text(
                                    text = when {
                                        isCandidateMatch -> "✓ MATCH! LEAPING..."
                                        debounceState.candidate.isNotBlank() -> "Saw: [${debounceState.candidate}]"
                                        else -> "Hold sign in camera view..."
                                    },
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCandidateMatch) Color(0xFF34D399) else Color(0xFF94A3B8),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            Text(
                                text = "Tap screen to leap →",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
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
                                text = "Dino sprints forward until encountering an obstacle, then pauses!\nForm the matching hand sign to camera to leap over the obstacle.\nOr tap anywhere on the screen for a manual leap.",
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

            // Bottom Biome Selector & Cleared Signs Trail
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 16.dp)
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

                            // Bones
                            for ((s, e) in HAND_CONNECTIONS) {
                                if (s < pts.size && e < pts.size) {
                                    val p1 = Offset(pts[s].x * scaledW + offX, pts[s].y * scaledH + offY)
                                    val p2 = Offset(pts[e].x * scaledW + offX, pts[e].y * scaledH + offY)
                                    drawLine(boneColor, p1, p2, strokeWidth = 3f, cap = StrokeCap.Round)
                                }
                            }

                            // Joints with glow
                            for (p in pts) {
                                val center = Offset(p.x * scaledW + offX, p.y * scaledH + offY)
                                drawCircle(
                                    jointColor,
                                    radius = 3.5f,
                                    center = center
                                )
                                drawCircle(
                                    jointColor.copy(alpha = 0.35f),
                                    radius = 7f,
                                    center = center,
                                    style = Stroke(width = 1.5f)
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
                            debounceState.top3.any { it.first.uppercase().trim() == target && it.second >= 0.20f }
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
