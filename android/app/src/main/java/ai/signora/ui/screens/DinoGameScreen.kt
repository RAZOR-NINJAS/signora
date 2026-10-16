package ai.signora.ui.screens

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ai.signora.models.DebounceState
import ai.signora.models.HandLandmarks
import ai.signora.models.Point3D
import ai.signora.models.RecognitionMode
import ai.signora.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

// --- Authentic Chrome Dino Pixel Sprites (from Signora Game Engine) ---
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
private val CACTUS = listOf(
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
private val CACTUS_RUNS = parseRuns(CACTUS)

private data class ShockParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var alpha: Float = 1.0f,
    val color: Color
)

private enum class GameState {
    IDLE,
    RUNNING,
    PROMPT,
    JUMPING,
    GAME_OVER
}

private val ASL_GAME_LETTERS = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "L", "O", "R", "S", "U", "V", "W", "Y")
private val ISL_GAME_LETTERS = listOf("A", "B", "C", "D", "SPACE", "NAMASTE", "HELLO", "WATER", "HELP", "YES", "NO")

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
    var speed by remember { mutableStateOf(0.48f) } // Fraction of width per second

    // Dino physics
    var dinoJumpProgress by remember { mutableStateOf(0f) }
    var legFrame by remember { mutableStateOf(0) } // 0 = A, 1 = B
    var cactusX by remember { mutableStateOf(1.25f) }
    var targetSign by remember { mutableStateOf("A") }
    var remainingMs by remember { mutableStateOf(3500) }
    val promptDurationMs = 3500
    var clearedLetters by remember { mutableStateOf(listOf<String>()) }
    val particles = remember { mutableStateListOf<ShockParticle>() }

    var isPipExpanded by remember { mutableStateOf(true) }

    val signPool = if (currentMode == RecognitionMode.ISL) ISL_GAME_LETTERS else ASL_GAME_LETTERS

    fun triggerJump() {
        if (gameState == GameState.PROMPT || gameState == GameState.RUNNING) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            gameState = GameState.JUMPING
            dinoJumpProgress = 0f
            score += 100
            if (score > highScore) {
                highScore = score
                prefs.edit().putInt("high_score", highScore).apply()
            }
            if (targetSign !in clearedLetters) {
                clearedLetters = clearedLetters + targetSign
            }

            // Spawn particle sparks
            particles.clear()
            val colors = listOf(Color(0xFF10B981), Color(0xFF34D399), Color(0xFFFBBF24), Color(0xFF60A5FA))
            for (i in 0 until 18) {
                particles.add(
                    ShockParticle(
                        x = 0.38f + (kotlin.random.Random.nextFloat() - 0.5f) * 0.08f,
                        y = 0.76f + (kotlin.random.Random.nextFloat() - 0.5f) * 0.08f,
                        vx = (kotlin.random.Random.nextFloat() - 0.5f) * 0.35f,
                        vy = -(kotlin.random.Random.nextFloat() * 0.45f + 0.1f),
                        color = colors.random()
                    )
                )
            }
        }
    }

    fun startNewGame() {
        score = 0
        speed = 0.48f
        cactusX = 1.30f
        targetSign = signPool.random()
        clearedLetters = emptyList()
        particles.clear()
        gameState = GameState.RUNNING
        dinoJumpProgress = 0f
    }

    // Gesture detection watcher: when in PROMPT, matching sign triggers jump!
    LaunchedEffect(debounceState.candidate, gameState) {
        if (gameState == GameState.PROMPT) {
            val detected = debounceState.candidate.uppercase().trim()
            if (detected == targetSign.uppercase().trim()) {
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

            // Update particle physics
            val deadParticles = mutableListOf<ShockParticle>()
            for (p in particles) {
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.vy += 1.2f * dt // gravity
                p.alpha -= 1.8f * dt
                if (p.alpha <= 0f) deadParticles.add(p)
            }
            particles.removeAll(deadParticles)

            when (gameState) {
                GameState.RUNNING -> {
                    // Running animation (10Hz leg toggle)
                    legFrame = if ((now / 100) % 2 == 0L) 0 else 1
                    cactusX -= speed * dt

                    // When cactus reaches the answer threshold, pause for sign challenge!
                    if (cactusX <= 0.42f) {
                        cactusX = 0.38f
                        gameState = GameState.PROMPT
                        remainingMs = promptDurationMs
                    }
                }

                GameState.PROMPT -> {
                    // Countdown timer ticks down
                    remainingMs -= (dt * 1000).toInt()
                    if (remainingMs <= 0) {
                        // Time out -> Dino crashed!
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        gameState = GameState.GAME_OVER
                    }
                }

                GameState.JUMPING -> {
                    // 650ms parabolic jump arc
                    dinoJumpProgress += dt / 0.65f
                    cactusX -= speed * 1.5f * dt

                    if (dinoJumpProgress >= 1f) {
                        dinoJumpProgress = 0f
                        cactusX = 1.35f
                        targetSign = signPool.random()
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
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Sign Dino Runner",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                },
                actions = {
                    // Mode Toggle (ASL vs ISL)
                    FilterChip(
                        selected = currentMode == RecognitionMode.ISL,
                        onClick = {
                            val newMode = if (currentMode == RecognitionMode.ISL) RecognitionMode.ASL else RecognitionMode.ISL
                            onModeChange(newMode)
                            targetSign = (if (newMode == RecognitionMode.ISL) ISL_GAME_LETTERS else ASL_GAME_LETTERS).random()
                        },
                        label = { Text(if (currentMode == RecognitionMode.ISL) "ISL" else "ASL", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.padding(end = 8.dp)
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
        },
        containerColor = Color(0xFFF7F7F7) // Authentic Chrome Dino paper background
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clickable {
                    // Tap-to-jump / Tap-to-start accessibility fallback
                    if (gameState == GameState.IDLE || gameState == GameState.GAME_OVER) {
                        startNewGame()
                    } else if (gameState == GameState.PROMPT) {
                        triggerJump()
                    }
                }
        ) {
            // Main Chrome Dino Canvas Renderer
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height
                val groundY = canvasH * 0.76f

                // Sprite scaling unit (pixel grid)
                val u = minOf(canvasW, canvasH) * 0.0095f

                // 1. Draw Clouds
                val cloudColor = Color(0xFFD8D8D8)
                val cloudOffsets = listOf(0.12f, 0.48f, 0.82f)
                val cloudYs = listOf(0.16f, 0.10f, 0.22f)
                val tSec = (System.currentTimeMillis() % 100000L) / 1000f
                for (i in cloudOffsets.indices) {
                    val cx = ((cloudOffsets[i] * canvasW + tSec * 15f * (i + 1)) % (canvasW + 180f)) - 90f
                    val cy = cloudYs[i] * canvasH
                    drawRect(cloudColor, Offset(cx, cy), Size(u * 9f, u * 2.2f))
                    drawRect(cloudColor, Offset(cx + u * 2f, cy - u * 2f), Size(u * 5f, u * 2f))
                    drawRect(cloudColor, Offset(cx + u * 1f, cy + u * 2f), Size(u * 6f, u * 1.5f))
                }

                // 2. Draw Ground Line with Pebbles
                val inkColor = Color(0xFF535353)
                val inkDimColor = Color(0xFF9A9A9A)
                drawLine(
                    color = inkColor,
                    start = Offset(0f, groundY),
                    end = Offset(canvasW, groundY),
                    strokeWidth = 3f
                )

                // Moving pebbles
                val groundScroll = if (gameState == GameState.RUNNING || gameState == GameState.JUMPING) {
                    (System.currentTimeMillis() * speed * 0.4f) % (canvasW * 0.15f)
                } else 0f
                val spacing = canvasW * 0.15f
                var px = -groundScroll
                var idx = 0
                while (px < canvasW + spacing) {
                    val py = groundY + u * (0.8f + (idx % 3) * 0.6f)
                    val pw = u * (1.2f + (idx % 2) * 0.8f)
                    drawRect(inkDimColor, Offset(px, py), Size(pw, u * 0.45f))
                    px += spacing
                    idx++
                }

                // 3. Draw Cactus Obstacle
                val cactusScreenX = cactusX * canvasW
                if (cactusScreenX > -50f && cactusScreenX < canvasW + 50f) {
                    val cactusY = groundY - 17 * u
                    for (run in CACTUS_RUNS) {
                        drawRect(
                            color = inkColor,
                            topLeft = Offset(cactusScreenX + run.startCol * u, cactusY + run.row * u),
                            size = Size((run.endCol - run.startCol) * u + 0.6f, u + 0.6f)
                        )
                    }
                }

                // 4. Draw Particle Sparks
                for (p in particles) {
                    drawCircle(
                        color = p.color.copy(alpha = p.alpha.coerceIn(0f, 1f)),
                        radius = 4.5f * p.alpha,
                        center = Offset(p.x * canvasW, p.y * canvasH)
                    )
                }

                // 5. Draw Dinosaur
                val dinoX = canvasW * 0.14f
                val hop = if (gameState == GameState.JUMPING) {
                    sin(dinoJumpProgress.coerceIn(0f, 1f) * PI).toFloat() * canvasH * 0.22f
                } else 0f
                val dinoY = groundY - 22 * u - hop

                // Dino Ground Shadow
                val lift = (hop / (canvasH * 0.22f)).coerceIn(0f, 1f)
                drawOval(
                    color = Color(0x33535353).copy(alpha = 0.25f * (1f - lift * 0.6f)),
                    topLeft = Offset(dinoX + 4 * u, groundY - u * 1.2f),
                    size = Size(18 * u * (1f - lift * 0.35f), u * 2.4f)
                )

                // Dino Body
                for (run in DINO_BODY_RUNS) {
                    drawRect(
                        color = inkColor,
                        topLeft = Offset(dinoX + run.startCol * u, dinoY + run.row * u),
                        size = Size((run.endCol - run.startCol) * u + 0.6f, u + 0.6f)
                    )
                }

                // Dino Legs & Eyes
                if (gameState == GameState.GAME_OVER) {
                    for (run in DINO_LEGS_DEAD_RUNS) {
                        drawRect(
                            color = inkColor,
                            topLeft = Offset(dinoX + run.startCol * u, dinoY + 17 * u + run.row * u),
                            size = Size((run.endCol - run.startCol) * u + 0.6f, u + 0.6f)
                        )
                    }
                    // 'X' dead eye
                    val ex = dinoX + 14 * u
                    val ey = dinoY + 3.2f * u
                    val es = u * 1.3f
                    drawLine(Color(0xFFF7F7F7), Offset(ex - es, ey - es), Offset(ex + es, ey + es), strokeWidth = 2.5f)
                    drawLine(Color(0xFFF7F7F7), Offset(ex + es, ey - es), Offset(ex - es, ey + es), strokeWidth = 2.5f)
                } else {
                    val legRuns = if (gameState == GameState.JUMPING || legFrame == 0) DINO_LEGS_A_RUNS else DINO_LEGS_B_RUNS
                    for (run in legRuns) {
                        drawRect(
                            color = inkColor,
                            topLeft = Offset(dinoX + run.startCol * u, dinoY + 17 * u + run.row * u),
                            size = Size((run.endCol - run.startCol) * u + 0.6f, u + 0.6f)
                        )
                    }
                    // Normal eye
                    drawRect(Color(0xFFF7F7F7), Offset(dinoX + 13.5f * u, dinoY + 2.5f * u), Size(u * 1.5f, u * 1.5f))
                }
            }

            // Top HUD Overlay (High Score & Current Score in authentic retro style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // High Score & Current Score Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.08f),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "HI ${highScore.toString().padStart(5, '0')}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF757575)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = score.toString().padStart(5, '0'),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = Color(0xFF222222)
                        )
                    }
                }
            }

            // Central Sign Challenge Prompt Badge (When Cactus is close)
            if (gameState == GameState.PROMPT) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 80.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.95f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp)
                        ) {
                            Text(
                                text = "SIGN TO JUMP",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = targetSign,
                                fontSize = 54.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Animated countdown progress bar
                            val progress = (remainingMs.toFloat() / promptDurationMs.toFloat()).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { progress },
                                color = if (progress < 0.35f) Color(0xFFEF4444) else Color(0xFF10B981),
                                trackColor = Color(0xFFE2E8F0),
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(8.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Live recognition feedback
                            val isMatch = debounceState.candidate.equals(targetSign, ignoreCase = true)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (debounceState.candidate.isNotBlank()) "Saw: [${debounceState.candidate}]" else "Waiting for hand...",
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isMatch) Color(0xFF10B981) else Color(0xFF64748B)
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
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "READY TO RUN?",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Sign letters with your hands in front of the camera to jump obstacles!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF64748B),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { startNewGame() },
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
                            ) {
                                Text("Start Running", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                text = "GAME OVER",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFEF4444)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Score: $score  •  High: $highScore",
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF334155)
                            )
                            if (clearedLetters.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Cleared: ${clearedLetters.joinToString(" ")}",
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF10B981),
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

            // Bottom Cleared Letters Trail
            if (clearedLetters.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Black.copy(alpha = 0.06f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 16.dp)
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
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = clearedLetters.takeLast(8).joinToString(" "),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }

            // Floating PiP Camera Viewport in Top-Right
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.92f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .size(width = 110.dp, height = 145.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
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

                    // Minimal Hand Overlay inside PiP
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        for (hand in handLandmarks) {
                            val color = if (hand.isRightHand) Color(0xFF06B6D4) else Color(0xFFF43F5E)
                            for (p in hand.points) {
                                drawCircle(
                                    color = color,
                                    radius = 2.5f,
                                    center = Offset(p.x * w, p.y * h)
                                )
                            }
                        }
                    }

                    // Bottom Detected Sign Pill inside PiP
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = debounceState.candidate.ifBlank { "--" },
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            color = if (debounceState.candidate.equals(targetSign, ignoreCase = true)) Color(0xFF34D399) else Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
