package ai.signora.ui.screens

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import ai.signora.models.Point3D
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

private fun parseRuns(rows: List<String>): List<SpriteRun> {
    val runs = mutableListOf<SpriteRun>()
    for (r in rows.indices) {
        val row = rows[r]
        var c = 0
        while (c < row.length) {
            if (row[c] != 'X' && row[c] != '#') {
                c++
                continue
            }
            val start = c
            while (c < row.length && (row[c] == 'X' || row[c] == '#')) {
                c++
            }
            runs.add(SpriteRun(r, start, c))
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
        groundColor = Color(0xFF1E1B4B),
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
    var alpha: Float = 0.5f
)

private data class Star(
    val x: Float,
    val y: Float,
    val size: Float,
    val speed: Float,
    val color: Color
)

private enum class GameState {
    IDLE,
    RUNNING,
    PROMPT,
    JUMPING,
    WARP,
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    var gameState by remember { mutableStateOf(GameState.IDLE) }
    var speed by remember { mutableStateOf(0.48f) }

    // Biome System
    val allBiomes = GameBiome.values()
    var currentBiomeIndex by remember { mutableStateOf(0) }
    val currentBiome = allBiomes[currentBiomeIndex % allBiomes.size]
    var warpBannerText by remember { mutableStateOf<String?>(null) }

    // Dino Physics & Animation
    var dinoJumpProgress by remember { mutableStateOf(0f) }
    var legFrame by remember { mutableStateOf(0) }
    var cactusX by remember { mutableStateOf(1.25f) }
    var promptProgress by remember { mutableStateOf(1f) }
    var remainingMs by remember { mutableStateOf(3500) }
    val promptDurationMs = 3500

    // Prompt Letter & Vocabulary
    val signList = if (currentMode == RecognitionMode.ISL) ISL_GAME_LETTERS else ASL_GAME_LETTERS
    var currentSignItem by remember { mutableStateOf(signList.random()) }
    var clearedSigns by remember { mutableStateOf(listOf<String>()) }

    // Particles & FX
    val shockParticles = remember { mutableStateListOf<ShockParticle>() }
    val ghostTrails = remember { mutableStateListOf<GhostTrail>() }
    var floatingScorePopup by remember { mutableStateOf<Pair<Offset, Float>?>(null) } // (pos, alpha)

    // Pre-allocated cosmic stars
    val stars = remember {
        List(60) {
            Star(
                x = kotlin.random.Random.nextFloat(),
                y = kotlin.random.Random.nextFloat() * 0.72f,
                size = kotlin.random.Random.nextFloat() * 2.8f + 1f,
                speed = kotlin.random.Random.nextFloat() * 0.15f + 0.05f,
                color = listOf(Color.White, Color(0xFF93C5FD), Color(0xFFFDE047), Color(0xFFF472B6)).random()
            )
        }
    }

    // Trigger Jump
    fun triggerJump() {
        if (gameState == GameState.PROMPT || gameState == GameState.RUNNING) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            gameState = GameState.JUMPING
            dinoJumpProgress = 0f
            score += 100

            // Floating +100 indicator
            floatingScorePopup = Pair(Offset(0.38f, 0.65f), 1f)

            if (score > highScore) {
                highScore = score
                prefs.edit().putInt("high_score", highScore).apply()
            }
            if (currentSignItem.first !in clearedSigns) {
                clearedSigns = clearedSigns + currentSignItem.first
            }

            // Exploding particle sparks
            shockParticles.clear()
            for (i in 0 until 24) {
                shockParticles.add(
                    ShockParticle(
                        x = 0.38f + (kotlin.random.Random.nextFloat() - 0.5f) * 0.08f,
                        y = 0.74f + (kotlin.random.Random.nextFloat() - 0.5f) * 0.08f,
                        vx = (kotlin.random.Random.nextFloat() - 0.5f) * 0.45f,
                        vy = -(kotlin.random.Random.nextFloat() * 0.55f + 0.15f),
                        color = currentBiome.particleColors.random()
                    )
                )
            }

            // Check Biome progression every 300 points!
            if (score > 0 && score % 300 == 0) {
                val nextIdx = (currentBiomeIndex + 1) % allBiomes.size
                currentBiomeIndex = nextIdx
                warpBannerText = "WARP ACTIVE • ENTERING ${allBiomes[nextIdx].title}"
            }
        }
    }

    fun startNewGame() {
        score = 0
        speed = 0.48f
        cactusX = 1.30f
        currentSignItem = signList.random()
        clearedSigns = emptyList()
        shockParticles.clear()
        ghostTrails.clear()
        currentBiomeIndex = 0
        warpBannerText = null
        gameState = GameState.RUNNING
        dinoJumpProgress = 0f
    }

    // Ultra-Responsive Gesture Detection: checks candidate AND top3 probabilities!
    LaunchedEffect(debounceState.candidate, debounceState.top3, gameState) {
        if (gameState == GameState.PROMPT) {
            val target = currentSignItem.first.uppercase().trim()
            val candidateMatch = debounceState.candidate.uppercase().trim() == target
            val top3Match = debounceState.top3.any { (lbl, prob) ->
                lbl.uppercase().trim() == target && prob >= 0.28f
            }
            val lastConfirmedMatch = debounceState.lastConfirmed.uppercase().trim() == target

            if (candidateMatch || top3Match || lastConfirmedMatch) {
                triggerJump()
            }
        }
    }

    // Main 60 FPS Game Loop
    LaunchedEffect(gameState) {
        var lastTime = System.currentTimeMillis()
        while (gameState != GameState.GAME_OVER && gameState != GameState.IDLE) {
            val now = System.currentTimeMillis()
            val dt = (now - lastTime) / 1000f
            lastTime = now

            // Update particle sparks
            val deadParticles = mutableListOf<ShockParticle>()
            for (p in shockParticles) {
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.vy += 1.3f * dt // gravity
                p.alpha -= 1.6f * dt
                if (p.alpha <= 0f) deadParticles.add(p)
            }
            shockParticles.removeAll(deadParticles)

            // Update ghost trail (Geometry Dash glow effect)
            val deadTrails = mutableListOf<GhostTrail>()
            for (gt in ghostTrails) {
                gt.alpha -= 2.5f * dt
                if (gt.alpha <= 0f) deadTrails.add(gt)
            }
            ghostTrails.removeAll(deadTrails)

            // Update floating score popup
            floatingScorePopup?.let { (pos, alpha) ->
                val newAlpha = alpha - 1.5f * dt
                if (newAlpha <= 0f) {
                    floatingScorePopup = null
                } else {
                    floatingScorePopup = Pair(Offset(pos.x, pos.y - 0.12f * dt), newAlpha)
                }
            }

            when (gameState) {
                GameState.RUNNING -> {
                    // Running animation (10Hz leg toggle)
                    legFrame = if ((now / 100) % 2 == 0L) 0 else 1
                    cactusX -= speed * dt

                    // Record ghost trail while running
                    if ((now / 80) % 2 == 0L) {
                        ghostTrails.add(GhostTrail(0.14f, 0.74f, 0.45f))
                    }

                    // Answer window threshold
                    if (cactusX <= 0.42f) {
                        cactusX = 0.38f
                        gameState = GameState.PROMPT
                        remainingMs = promptDurationMs
                    }
                }

                GameState.PROMPT -> {
                    // Countdown timer ticks down
                    remainingMs -= (dt * 1000).toInt()
                    promptProgress = (remainingMs.toFloat() / promptDurationMs.toFloat()).coerceIn(0f, 1f)

                    if (remainingMs <= 0) {
                        // Crash into obstacle!
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        gameState = GameState.GAME_OVER
                    }
                }

                GameState.JUMPING -> {
                    // 650ms parabolic jump arc
                    dinoJumpProgress += dt / 0.65f
                    cactusX -= speed * 1.5f * dt

                    // Jetpack / ghost trail during jump
                    ghostTrails.add(GhostTrail(0.14f, 0.74f - sin(dinoJumpProgress * PI).toFloat() * 0.22f, 0.6f))

                    if (dinoJumpProgress >= 1f) {
                        dinoJumpProgress = 0f
                        cactusX = 1.35f
                        currentSignItem = signList.random()
                        speed = (speed + 0.025f).coerceAtMost(0.95f) // Increase speed
                        gameState = GameState.RUNNING
                    }
                }

                else -> {}
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
                            currentSignItem = (if (newMode == RecognitionMode.ISL) ISL_GAME_LETTERS else ASL_GAME_LETTERS).random()
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
                .clickable {
                    // Tap-to-jump / Tap-to-start accessibility
                    if (gameState == GameState.IDLE || gameState == GameState.GAME_OVER) {
                        startNewGame()
                    } else if (gameState == GameState.PROMPT) {
                        triggerJump()
                    }
                }
        ) {
            // Main Dynamic Biome Canvas Renderer
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height
                val groundY = canvasH * 0.74f
                val u = minOf(canvasW, canvasH) * 0.0095f

                // 1. Sky Gradient Background
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = currentBiome.skyGradient,
                        startY = 0f,
                        endY = groundY
                    ),
                    size = Size(canvasW, groundY)
                )

                // 2. Biome-Specific Celestial & Parallax Backgrounds
                when (currentBiome) {
                    GameBiome.DESERT -> {
                        // Golden Sun & Sand Dunes
                        drawCircle(
                            color = Color(0xFFFBBF24).copy(alpha = 0.65f),
                            radius = canvasW * 0.14f,
                            center = Offset(canvasW * 0.82f, canvasH * 0.22f)
                        )
                        // Pyramids / Dunes
                        val duneColor = Color(0xFFD97706).copy(alpha = 0.35f)
                        drawDune(canvasW * 0.2f, groundY, canvasW * 0.35f, canvasH * 0.16f, duneColor)
                        drawDune(canvasW * 0.65f, groundY, canvasW * 0.45f, canvasH * 0.22f, duneColor)
                    }

                    GameBiome.NEON_SYNTH -> {
                        // Massive Horizon Synthwave Sun
                        val sunY = groundY - canvasH * 0.06f
                        val sunRadius = canvasW * 0.20f
                        drawCircle(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFFF43F5E), Color(0xFFFBBF24)),
                                startY = sunY - sunRadius,
                                endY = sunY + sunRadius
                            ),
                            radius = sunRadius,
                            center = Offset(canvasW * 0.5f, sunY)
                        )
                        // Sun horizontal line cuts
                        for (i in 1..6) {
                            val lineY = sunY + (i * 9f)
                            drawLine(
                                color = Color(0xFF0F0C29),
                                start = Offset(canvasW * 0.5f - sunRadius, lineY),
                                end = Offset(canvasW * 0.5f + sunRadius, lineY),
                                strokeWidth = 3f + i * 0.8f
                            )
                        }
                    }

                    GameBiome.MYSTICAL -> {
                        // Glowing Celestial Moon
                        drawCircle(
                            color = Color(0xFF6EE7B7).copy(alpha = 0.22f),
                            radius = canvasW * 0.18f,
                            center = Offset(canvasW * 0.25f, canvasH * 0.20f)
                        )
                        drawCircle(
                            color = Color(0xFFA7F3D0).copy(alpha = 0.85f),
                            radius = canvasW * 0.12f,
                            center = Offset(canvasW * 0.25f, canvasH * 0.20f)
                        )
                        // Floating Spore Fireflies
                        val nowMs = System.currentTimeMillis()
                        for (i in 0 until 12) {
                            val fx = ((canvasW * (0.08f * i) + nowMs * 0.02f * (i + 1)) % canvasW)
                            val fy = groundY - (sin((nowMs * 0.003f + i).toDouble()).toFloat() * 40f + 50f + i * 15f)
                            drawCircle(
                                color = Color(0xFF34D399).copy(alpha = 0.75f),
                                radius = 3.5f,
                                center = Offset(fx, fy)
                            )
                        }
                    }

                    GameBiome.COSMIC_SPACE -> {
                        // Starfield with Parallax Twinkling
                        val tSec = (System.currentTimeMillis() % 100000L) / 1000f
                        for (star in stars) {
                            val sx = ((star.x * canvasW + tSec * star.speed * 40f) % (canvasW + 20f)) - 10f
                            drawCircle(
                                color = star.color.copy(alpha = 0.8f),
                                radius = star.size,
                                center = Offset(sx, star.y * canvasH)
                            )
                        }
                        // Giant Ringed Planet in Background
                        val px = canvasW * 0.78f
                        val py = canvasH * 0.24f
                        val pr = canvasW * 0.11f
                        drawCircle(Color(0xFF6366F1).copy(alpha = 0.85f), pr, Offset(px, py))
                        // Planetary Rings
                        drawOval(
                            color = Color(0xFFEC4899).copy(alpha = 0.7f),
                            topLeft = Offset(px - pr * 1.8f, py - pr * 0.45f),
                            size = Size(pr * 3.6f, pr * 0.9f),
                            style = Stroke(width = 4.5f)
                        )
                    }
                }

                // 3. Ground Plane
                drawRect(
                    color = currentBiome.groundColor,
                    topLeft = Offset(0f, groundY),
                    size = Size(canvasW, canvasH - groundY)
                )

                // Ground Laser/Horizon Line
                drawLine(
                    color = currentBiome.groundLineColor,
                    start = Offset(0f, groundY),
                    end = Offset(canvasW, groundY),
                    strokeWidth = 3.5f
                )

                // Geometry Dash / Synthwave Perspective Grid Lines
                if (currentBiome == GameBiome.NEON_SYNTH || currentBiome == GameBiome.COSMIC_SPACE) {
                    val gridScroll = if (gameState == GameState.RUNNING || gameState == GameState.JUMPING) {
                        (System.currentTimeMillis() * speed * 0.8f) % (canvasW * 0.12f)
                    } else 0f
                    val gridSpacing = canvasW * 0.12f
                    var gx = -gridScroll
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
                    // Scrolling pebbles for Desert & Mystical
                    val groundScroll = if (gameState == GameState.RUNNING || gameState == GameState.JUMPING) {
                        (System.currentTimeMillis() * speed * 0.4f) % (canvasW * 0.15f)
                    } else 0f
                    val spacing = canvasW * 0.15f
                    var px = -groundScroll
                    var idx = 0
                    while (px < canvasW + spacing) {
                        val py = groundY + u * (0.8f + (idx % 3) * 0.6f)
                        val pw = u * (1.2f + (idx % 2) * 0.8f)
                        drawRect(currentBiome.groundLineColor.copy(alpha = 0.5f), Offset(px, py), Size(pw, u * 0.45f))
                        px += spacing
                        idx++
                    }
                }

                // 4. Obstacle (Cactus / Pylon / Shroom / Spike)
                val obstacleScreenX = cactusX * canvasW
                if (obstacleScreenX > -60f && obstacleScreenX < canvasW + 60f) {
                    val obsY = groundY - 17 * u

                    // Glow aura around obstacle
                    drawCircle(
                        color = currentBiome.obstacleGlow,
                        radius = 28 * u * 0.5f,
                        center = Offset(obstacleScreenX + 5.5f * u, obsY + 8.5f * u)
                    )

                    for (run in currentBiome.obstacleRuns) {
                        drawRect(
                            color = currentBiome.obstacleColor,
                            topLeft = Offset(obstacleScreenX + run.startCol * u, obsY + run.row * u),
                            size = Size((run.endCol - run.startCol) * u + 0.6f, u + 0.6f)
                        )
                    }
                }

                // 5. Ghost Trail (Geometry Dash Glow Effect)
                for (trail in ghostTrails) {
                    val trailY = groundY - 22 * u - (0.74f - trail.y) * canvasH
                    for (run in DINO_BODY_RUNS) {
                        drawRect(
                            color = currentBiome.obstacleGlow.copy(alpha = trail.alpha * 0.4f),
                            topLeft = Offset(trail.x * canvasW + run.startCol * u, trailY + run.row * u),
                            size = Size((run.endCol - run.startCol) * u + 0.6f, u + 0.6f)
                        )
                    }
                }

                // 6. Exploding Particle Sparks
                for (p in shockParticles) {
                    drawCircle(
                        color = p.color.copy(alpha = p.alpha.coerceIn(0f, 1f)),
                        radius = 5.5f * p.alpha,
                        center = Offset(p.x * canvasW, p.y * canvasH)
                    )
                }

                // 7. Dinosaur Rendering
                val dinoX = canvasW * 0.14f
                val hop = if (gameState == GameState.JUMPING) {
                    sin(dinoJumpProgress.coerceIn(0f, 1f) * PI).toFloat() * canvasH * 0.22f
                } else 0f
                val dinoY = groundY - 22 * u - hop

                // Dino Ground Shadow
                val lift = (hop / (canvasH * 0.22f)).coerceIn(0f, 1f)
                drawOval(
                    color = Color.Black.copy(alpha = 0.25f * (1f - lift * 0.6f)),
                    topLeft = Offset(dinoX + 4 * u, groundY - u * 1.2f),
                    size = Size(18 * u * (1f - lift * 0.35f), u * 2.4f)
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
                    // 'X' dead eye
                    val ex = dinoX + 14 * u
                    val ey = dinoY + 3.2f * u
                    val es = u * 1.3f
                    drawLine(Color.Red, Offset(ex - es, ey - es), Offset(ex + es, ey + es), strokeWidth = 3f)
                    drawLine(Color.Red, Offset(ex + es, ey - es), Offset(ex - es, ey + es), strokeWidth = 3f)
                } else {
                    val legRuns = if (gameState == GameState.JUMPING || legFrame == 0) DINO_LEGS_A_RUNS else DINO_LEGS_B_RUNS
                    for (run in legRuns) {
                        drawRect(
                            color = currentBiome.dinoColor,
                            topLeft = Offset(dinoX + run.startCol * u, dinoY + 17 * u + run.row * u),
                            size = Size((run.endCol - run.startCol) * u + 0.6f, u + 0.6f)
                        )
                    }
                    // Normal eye
                    val eyeColor = if (currentBiome == GameBiome.NEON_SYNTH) Color(0xFF06B6D4) else Color(0xFF0F172A)
                    drawRect(eyeColor, Offset(dinoX + 13.5f * u, dinoY + 2.5f * u), Size(u * 1.5f, u * 1.5f))
                }

                // Cosmic Astronaut Helmet visor if in Cosmic mode!
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // High Score & Current Score Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.55f),
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
                            fontSize = 14.sp,
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
            }

            // Floating Warp Banner Notification (Geometry Dash style transition)
            warpBannerText?.let { banner ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.88f)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, currentBiome.obstacleColor),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 60.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = currentBiome.obstacleColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = banner,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                    }
                }
            }

            // Floating +100 Score indicator
            floatingScorePopup?.let { (pos, alpha) ->
                Text(
                    text = "+100",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    color = Color(0xFF10B981).copy(alpha = alpha.coerceIn(0f, 1f)),
                    modifier = Modifier
                        .offset(x = 140.dp, y = 200.dp)
                )
            }

            // Central Sign Challenge Prompt Badge (When obstacle approaches)
            if (gameState == GameState.PROMPT) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 60.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF0F172A).copy(alpha = 0.95f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(2.dp, currentBiome.obstacleColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                        modifier = Modifier.padding(horizontal = 20.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp)
                        ) {
                            Text(
                                text = "SIGN TO JUMP",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = currentBiome.obstacleColor,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentSignItem.first,
                                fontSize = 52.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )

                            // Posture Tip Hint
                            Text(
                                text = currentSignItem.second,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Animated countdown progress bar
                            LinearProgressIndicator(
                                progress = { promptProgress },
                                color = if (promptProgress < 0.35f) Color(0xFFEF4444) else currentBiome.obstacleColor,
                                trackColor = Color(0xFF334155),
                                modifier = Modifier
                                    .fillMaxWidth(0.65f)
                                    .height(8.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Live recognition feedback
                            val target = currentSignItem.first.uppercase().trim()
                            val isMatch = debounceState.candidate.uppercase().trim() == target ||
                                debounceState.top3.any { it.first.uppercase().trim() == target && it.second >= 0.28f }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (debounceState.candidate.isNotBlank()) "Saw: [${debounceState.candidate}]" else "Show hands to camera...",
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isMatch) Color(0xFF34D399) else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }
            }

            // Start Screen Prompt
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
                                text = "Sign letters to jump over obstacles!\nIncludes 4 vibrant worlds, warp gates & Geometry Dash particle FX.",
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
                                Text("Start Running", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
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
                                Text("Play Again", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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

            // Floating PiP Camera Viewport in Top-Right with Full Cybernetic Skeleton!
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

                        // Exact FILL_CENTER transformation
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
                        val target = currentSignItem.first.uppercase().trim()
                        val isMatched = debounceState.candidate.uppercase().trim() == target ||
                            debounceState.top3.any { it.first.uppercase().trim() == target && it.second >= 0.28f }

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
