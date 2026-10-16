package ai.signora.ui.screens

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import ai.signora.models.DebounceState
import ai.signora.models.HandLandmarks
import ai.signora.models.RecognitionMode
import kotlinx.coroutines.delay
import kotlin.math.PI
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

private val RUNS_DESERT = parseRuns(CACTUS_DESERT)
private val RUNS_NEON = parseRuns(PYLON_NEON)
private val RUNS_MYSTICAL = parseRuns(SHROOM_MYSTICAL)
private val RUNS_COSMIC = parseRuns(SPIKE_COSMIC)

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

private data class Star(
    val x: Float,
    val y: Float,
    val size: Float,
    val speed: Float,
    val color: Color
)

// Dynamic Obstacle in the continuous runner stream
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

    var highScore by remember { mutableStateOf(prefs.getInt("high_score", 0)) }
    var score by remember { mutableStateOf(0) }
    var streak by remember { mutableStateOf(0) }
    var gameState by remember { mutableStateOf(GameState.IDLE) }
    var speed by remember { mutableStateOf(0.40f) }

    // Biome System
    val allBiomes = GameBiome.values()
    var currentBiomeIndex by remember { mutableStateOf(0) }
    val currentBiome = allBiomes[currentBiomeIndex % allBiomes.size]
    var warpBannerText by remember { mutableStateOf<String?>(null) }
    var warpBannerAlpha by remember { mutableStateOf(0f) }

    // Screen Shake effect (0f = none, >0f = active shake magnitude)
    var screenShake by remember { mutableStateOf(0f) }

    // Physics Jump System (True Continuous Runner!)
    var dinoHop by remember { mutableStateOf(0f) } // Vertical lift in normalized units (0 = ground)
    var dinoVY by remember { mutableStateOf(0f) }  // Vertical velocity
    var isGrounded by remember { mutableStateOf(true) }
    var legFrame by remember { mutableStateOf(0) }

    // Obstacle Stream Pipeline
    val signList = if (currentMode == RecognitionMode.ISL) ISL_GAME_LETTERS else ASL_GAME_LETTERS
    val activeObstacles = remember { mutableStateListOf<StreamObstacle>() }
    var clearedSigns by remember { mutableStateOf(listOf<String>()) }
    var obstacleCounter by remember { mutableStateOf(0L) }

    // Particles, Ghost Trails & Shockwave Rings
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

    // Function to trigger a physical, physics-driven leap!
    fun doJump(isSignBonus: Boolean = false, obstacleId: Long? = null) {
        if (gameState != GameState.RUNNING || !isGrounded) return

        isGrounded = false
        // Physics jump launch velocity
        dinoVY = -1.55f
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

        // Geometry Dash Shockwave Ring at Dino feet
        shockwaveRings.add(
            ShockwaveRing(
                x = 0.17f,
                y = 0.73f,
                radius = 12f,
                maxRadius = 80f,
                alpha = 0.9f,
                color = if (isSignBonus) Color(0xFFFBBF24) else currentBiome.obstacleColor
            )
        )

        // Exploding spark particles at launch
        for (i in 0 until 18) {
            shockParticles.add(
                ShockParticle(
                    x = 0.17f + (kotlin.random.Random.nextFloat() - 0.5f) * 0.06f,
                    y = 0.73f + (kotlin.random.Random.nextFloat() - 0.5f) * 0.04f,
                    vx = (kotlin.random.Random.nextFloat() - 0.5f) * 0.55f,
                    vy = -(kotlin.random.Random.nextFloat() * 0.65f + 0.10f),
                    color = currentBiome.particleColors.random()
                )
            )
        }

        if (isSignBonus) {
            streak++
            val pts = 200 + (streak * 20)
            score += pts
            floatingIndicators.add(
                FloatingIndicator(
                    text = if (streak > 1) "+$pts SIGN COMBO x$streak!" else "+$pts SIGN!",
                    x = 0.22f,
                    y = 0.52f,
                    color = Color(0xFFFBBF24)
                )
            )
        } else {
            score += 100
            floatingIndicators.add(
                FloatingIndicator(
                    text = "+100 JUMP",
                    x = 0.20f,
                    y = 0.55f,
                    color = currentBiome.groundLineColor
                )
            )
        }

        if (score > highScore) {
            highScore = score
            prefs.edit().putInt("high_score", highScore).apply()
        }

        // Check Biome progression every 250 points
        val targetBiomeIdx = (score / 250) % allBiomes.size
        if (targetBiomeIdx != currentBiomeIndex) {
            currentBiomeIndex = targetBiomeIdx
            warpBannerText = "WARP ACTIVE • ENTERING ${allBiomes[targetBiomeIdx].title}"
            warpBannerAlpha = 1.0f
            screenShake = 12f
        }
    }

    fun startNewGame() {
        score = 0
        streak = 0
        speed = 0.40f
        dinoHop = 0f
        dinoVY = 0f
        isGrounded = true
        clearedSigns = emptyList()
        activeObstacles.clear()
        shockParticles.clear()
        ghostTrails.clear()
        shockwaveRings.clear()
        floatingIndicators.clear()
        warpBannerText = null
        warpBannerAlpha = 0f
        screenShake = 0f

        // Spawn first 2 streaming obstacles spaced out
        obstacleCounter++
        activeObstacles.add(StreamObstacle(obstacleCounter, 1.25f, signList.random()))
        obstacleCounter++
        activeObstacles.add(StreamObstacle(obstacleCounter, 1.95f, signList.random()))

        gameState = GameState.RUNNING
    }

    // Target upcoming obstacle
    val currentUpcomingObstacle = activeObstacles.firstOrNull { it.x > 0.10f }

    // Instant Continuous Gesture Recognition: triggers auto-jump as obstacle approaches!
    LaunchedEffect(debounceState.candidate, debounceState.top3, activeObstacles.size) {
        if (gameState == GameState.RUNNING && isGrounded) {
            val upcoming = currentUpcomingObstacle
            if (upcoming != null && upcoming.x in 0.16f..0.72f && !upcoming.cleared) {
                val target = upcoming.signItem.first.uppercase().trim()
                val candidateMatch = debounceState.candidate.uppercase().trim() == target
                val top3Match = debounceState.top3.any { (lbl, prob) ->
                    lbl.uppercase().trim() == target && prob >= 0.25f
                }
                val lastConfirmedMatch = debounceState.lastConfirmed.uppercase().trim() == target

                if (candidateMatch || top3Match || lastConfirmedMatch) {
                    upcoming.cleared = true
                    if (upcoming.signItem.first !in clearedSigns) {
                        clearedSigns = clearedSigns + upcoming.signItem.first
                    }
                    doJump(isSignBonus = true, obstacleId = upcoming.id)
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

            // 1. Jump Physics
            if (!isGrounded) {
                dinoVY += 3.8f * dt // Gravity acceleration
                dinoHop -= dinoVY * dt
                if (dinoHop <= 0f) {
                    dinoHop = 0f
                    dinoVY = 0f
                    isGrounded = true
                    // Touchdown particles
                    for (i in 0 until 8) {
                        shockParticles.add(
                            ShockParticle(
                                x = 0.17f + (kotlin.random.Random.nextFloat() - 0.5f) * 0.05f,
                                y = 0.74f,
                                vx = (kotlin.random.Random.nextFloat() - 0.5f) * 0.25f,
                                vy = -(kotlin.random.Random.nextFloat() * 0.15f + 0.05f),
                                color = currentBiome.groundColor
                            )
                        )
                    }
                }
            }

            // Running legs toggle
            legFrame = if ((now / 90) % 2 == 0L) 0 else 1

            // 2. Obstacle Streaming Motion
            val deadObs = mutableListOf<StreamObstacle>()
            for (obs in activeObstacles) {
                obs.x -= speed * dt

                // Collision Detection against Dino
                // Dino occupies x in 0.13f..0.21f. Obstacle is at obs.x
                if (obs.x in 0.13f..0.20f && !obs.cleared) {
                    if (dinoHop >= 0.08f) {
                        // Successfully flew over!
                        obs.cleared = true
                        if (!obs.bonusAwarded) {
                            obs.bonusAwarded = true
                            score += 100
                            floatingIndicators.add(FloatingIndicator("+100 DODGE", 0.18f, 0.55f, color = Color(0xFF34D399)))
                        }
                    } else {
                        // CRASH!
                        gameState = GameState.GAME_OVER
                        screenShake = 16f
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        break
                    }
                }

                if (obs.x < -0.25f) {
                    deadObs.add(obs)
                }
            }
            activeObstacles.removeAll(deadObs)

            // Spawn next obstacle dynamically to maintain continuous stream
            val lastObstacle = activeObstacles.maxByOrNull { it.x }
            if (lastObstacle == null || lastObstacle.x < 0.65f) {
                obstacleCounter++
                val nextX = (lastObstacle?.x ?: 1.0f) + (kotlin.random.Random.nextFloat() * 0.40f + 0.65f)
                activeObstacles.add(StreamObstacle(obstacleCounter, nextX, signList.random()))
            }

            // Speed gradually ramps up with score
            speed = (0.40f + (score * 0.00012f)).coerceAtMost(0.75f)

            // 3. Ghost Trails (Geometry Dash Afterimages)
            if (!isGrounded || (now / 70) % 2 == 0L) {
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

            // 6. Floating Indicators
            val deadIndicators = mutableListOf<FloatingIndicator>()
            for (fi in floatingIndicators) {
                fi.y -= 0.14f * dt
                fi.alpha -= 1.4f * dt
                if (fi.alpha <= 0f) deadIndicators.add(fi)
            }
            floatingIndicators.removeAll(deadIndicators)

            // 7. Warp Banner fadeout & Screen Shake decay
            if (warpBannerAlpha > 0f) {
                warpBannerAlpha = (warpBannerAlpha - 0.45f * dt).coerceAtLeast(0f)
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
                    // Instant Tap-To-Jump / Tap-To-Replay: Zero-lag touch control everywhere!
                    detectTapGestures {
                        if (gameState == GameState.IDLE || gameState == GameState.GAME_OVER) {
                            startNewGame()
                        } else if (gameState == GameState.RUNNING) {
                            doJump(isSignBonus = false)
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

                // 5. Streaming Obstacles & Floating Target Badges!
                for (obs in activeObstacles) {
                    val obstacleScreenX = obs.x * canvasW + shakeX
                    if (obstacleScreenX > -80f && obstacleScreenX < canvasW + 80f) {
                        val obsY = groundY - 17 * u

                        // Obstacle Glow
                        drawCircle(
                            color = currentBiome.obstacleGlow,
                            radius = 28 * u * 0.5f,
                            center = Offset(obstacleScreenX + 5.5f * u, obsY + 8.5f * u)
                        )

                        // Obstacle Pixel Sprite
                        for (run in currentBiome.obstacleRuns) {
                            drawRect(
                                color = if (obs.cleared) currentBiome.obstacleColor.copy(alpha = 0.4f) else currentBiome.obstacleColor,
                                topLeft = Offset(obstacleScreenX + run.startCol * u, obsY + run.row * u),
                                size = Size((run.endCol - run.startCol) * u + 0.6f, u + 0.6f)
                            )
                        }

                        // Floating Holographic Letter Badge directly above obstacle!
                        val badgeY = obsY - 14 * u
                        val badgeRadius = 7.5f * u

                        // Badge backing pill
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.85f),
                            radius = badgeRadius,
                            center = Offset(obstacleScreenX + 5.5f * u, badgeY)
                        )
                        drawCircle(
                            color = if (obs.cleared) Color(0xFF34D399) else currentBiome.obstacleColor,
                            radius = badgeRadius,
                            center = Offset(obstacleScreenX + 5.5f * u, badgeY),
                            style = Stroke(width = 2.5f)
                        )
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
                val dinoX = canvasW * 0.15f + shakeX
                val hopPx = dinoHop * canvasH * 0.95f
                val dinoY = groundY - 22 * u - hopPx

                // Ground Shadow
                val shadowLift = (hopPx / (canvasH * 0.22f)).coerceIn(0f, 1f)
                drawOval(
                    color = Color.Black.copy(alpha = 0.28f * (1f - shadowLift * 0.6f)),
                    topLeft = Offset(dinoX + 4 * u, groundY - u * 1.2f),
                    size = Size(18 * u * (1f - shadowLift * 0.35f), u * 2.4f)
                )

                // Dino Body
                for (run in DINO_BODY_RUNS) {
                    drawRect(
                        color = currentBiome.dinoColor,
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
                    val legRuns = if (!isGrounded || legFrame == 0) DINO_LEGS_A_RUNS else DINO_LEGS_B_RUNS
                    for (run in legRuns) {
                        drawRect(
                            color = currentBiome.dinoColor,
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

            // Top HUD Overlay (Scoreboard & Biome Pills)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // High Score & Current Score Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, currentBiome.groundLineColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "HI ${highScore.toString().padStart(5, '0')}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = score.toString().padStart(5, '0'),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }
                }

                // Streak Multiplier Pill
                if (streak > 0 && gameState == GameState.RUNNING) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFD97706).copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${streak}x STREAK",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isMatched) "✓ MATCHED! JUMPING..." else (if (debounceState.candidate.isNotBlank()) "Saw: ${debounceState.candidate}" else "Sign letter or TAP to jump"),
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
                                text = "Continuous runner with Geometry Dash effects!\nSign upcoming letters for +200 SIGN BONUS, or tap anywhere to jump.",
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
                    .padding(16.dp)
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

                    // Cybernetic Hand Skeleton Overlay inside PiP
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

                            // Joints
                            for (p in pts) {
                                drawCircle(
                                    jointColor,
                                    radius = 3.5f,
                                    center = Offset(p.x * scaledW + offX, p.y * scaledH + offY)
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
