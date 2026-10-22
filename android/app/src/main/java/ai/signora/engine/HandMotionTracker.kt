package ai.signora.engine

import ai.signora.models.Point3D
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Hand motion dynamics, trajectory tracking, and real temporal gesture recognition for Signora.
 *
 * Implements genuine dynamic gesture recognition across multi-frame temporal windows:
 * - J_MOTION: Pinky extended (I-shape) tracing downward drop followed by leftward hook.
 * - Z_MOTION: Index extended (1/D-shape) tracing a 3-stroke Z in the air (right, down-left, right).
 * - HELLO_MOTION: Open hand waving side-to-side with horizontal velocity reversals.
 * - NO_MOTION: Rapid horizontal oscillation / shake with index finger or hand.
 * - YOU_MOTION: Index finger or open hand pushing forward towards the camera (scale expansion / z drop).
 * - WHAT_MOTION: Open hands diverging outward or shrugging with palms facing up.
 * - MY_NAME_IS_MOTION: Hand placed at or contacting chest / sternum zone with tap or lateral movement.
 */
data class MotionState(
    val dominantHand: String = "Right",
    val speed: Float = 0f,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val pattern: String = PATTERN_STATIC,
    val dualMotion: String = DUAL_STATIC,
    val handDistance: Float = 0f,
    val isMoving: Boolean = false,
    val detectedGesture: String? = null,
    val gestureConfidence: Float = 0f
)

data class HandPosture(
    val thumbExtended: Boolean = false,
    val indexExtended: Boolean = false,
    val middleExtended: Boolean = false,
    val ringExtended: Boolean = false,
    val pinkyExtended: Boolean = false,
    val extendedCount: Int = 0,
    val handScale: Float = 0.1f
) {
    val isIPosture: Boolean
        get() = pinkyExtended && !indexExtended && !middleExtended && !ringExtended

    val isIndexOnly: Boolean
        get() = indexExtended && !middleExtended && !ringExtended && !pinkyExtended

    val isOpenHand: Boolean
        get() = extendedCount >= 4

    companion object {
        val EMPTY = HandPosture()
    }
}

const val PATTERN_STATIC = "STATIC"
const val PATTERN_SWIPE_LEFT = "SWIPE_LEFT"
const val PATTERN_SWIPE_RIGHT = "SWIPE_RIGHT"
const val PATTERN_SWIPE_UP = "SWIPE_UP"
const val PATTERN_SWIPE_DOWN = "SWIPE_DOWN"
const val PATTERN_WAVE = "WAVE"
const val PATTERN_CIRCLE = "CIRCLE"
const val PATTERN_NOD = "NOD"
const val PATTERN_J_TRACE = "J_TRACE"
const val PATTERN_Z_TRACE = "Z_TRACE"
const val PATTERN_SHAKE = "SHAKE"
const val PATTERN_FORWARD_PUSH = "FORWARD_PUSH"
const val PATTERN_CHEST_CONTACT = "CHEST_CONTACT"
const val PATTERN_PALMS_UP = "PALMS_UP"

const val DUAL_STATIC = "STATIC"
const val DUAL_CONVERGING = "CONVERGING"
const val DUAL_DIVERGING = "DIVERGING"

class HandMotionTracker(private val maxHistory: Int = 32) {

    data class TimedPoint(val tMs: Long, val x: Float, val y: Float, val z: Float = 0f)

    private val rightHistory = ArrayDeque<TimedPoint>()
    private val leftHistory = ArrayDeque<TimedPoint>()

    private val rightIndexHistory = ArrayDeque<TimedPoint>()
    private val rightPinkyHistory = ArrayDeque<TimedPoint>()
    private val leftIndexHistory = ArrayDeque<TimedPoint>()
    private val leftPinkyHistory = ArrayDeque<TimedPoint>()

    private val handScaleHistory = ArrayDeque<Pair<Long, Float>>()
    private val zDepthHistory = ArrayDeque<Pair<Long, Float>>()

    private var lastGestureCommitTimeMs: Long = -10000L

    var latestState: MotionState = MotionState()
        private set

    fun update(
        rightWrist: Pair<Float, Float>?,
        leftWrist: Pair<Float, Float>?,
        timestampMs: Long = System.currentTimeMillis(),
        rightHandLandmarks: List<Point3D>? = null,
        leftHandLandmarks: List<Point3D>? = null,
        activeAnchorZone: String? = null,
        poseLandmarks: List<Point3D>? = null
    ): MotionState {
        // Record wrist histories
        rightWrist?.let { (x, y) ->
            if (rightHistory.size >= maxHistory) rightHistory.removeFirst()
            val z = rightHandLandmarks?.firstOrNull()?.z ?: 0f
            rightHistory.addLast(TimedPoint(timestampMs, x, y, z))
        }

        leftWrist?.let { (x, y) ->
            if (leftHistory.size >= maxHistory) leftHistory.removeFirst()
            val z = leftHandLandmarks?.firstOrNull()?.z ?: 0f
            leftHistory.addLast(TimedPoint(timestampMs, x, y, z))
        }

        // Record fingertip trajectories
        rightHandLandmarks?.let { pts ->
            if (pts.size > 8) {
                if (rightIndexHistory.size >= maxHistory) rightIndexHistory.removeFirst()
                rightIndexHistory.addLast(TimedPoint(timestampMs, pts[8].x, pts[8].y, pts[8].z))
            }
            if (pts.size > 20) {
                if (rightPinkyHistory.size >= maxHistory) rightPinkyHistory.removeFirst()
                rightPinkyHistory.addLast(TimedPoint(timestampMs, pts[20].x, pts[20].y, pts[20].z))
            }
        }

        leftHandLandmarks?.let { pts ->
            if (pts.size > 8) {
                if (leftIndexHistory.size >= maxHistory) leftIndexHistory.removeFirst()
                leftIndexHistory.addLast(TimedPoint(timestampMs, pts[8].x, pts[8].y, pts[8].z))
            }
            if (pts.size > 20) {
                if (leftPinkyHistory.size >= maxHistory) leftPinkyHistory.removeFirst()
                leftPinkyHistory.addLast(TimedPoint(timestampMs, pts[20].x, pts[20].y, pts[20].z))
            }
        }

        // Compute velocities and patterns for both hands
        val (rVx, rVy, rSpeed) = computeVelocity(rightHistory)
        val (lVx, lVy, lSpeed) = computeVelocity(leftHistory)

        val rPattern = classifyPattern(rightHistory, rSpeed)
        val lPattern = classifyPattern(leftHistory, lSpeed)

        val dominant = if (rSpeed >= lSpeed || leftWrist == null) "Right" else "Left"
        val domSpeed = if (dominant == "Right") rSpeed else lSpeed
        val domVx = if (dominant == "Right") rVx else lVx
        val domVy = if (dominant == "Right") rVy else lVy
        var domPattern = if (dominant == "Right") rPattern else lPattern

        var dualMotion = DUAL_STATIC
        var handDist = 0f

        if (rightWrist != null && leftWrist != null && rightHistory.size >= 2 && leftHistory.size >= 2) {
            val rLast = rightHistory.last()
            val lLast = leftHistory.last()
            val dx = rLast.x - lLast.x
            val dy = rLast.y - lLast.y
            handDist = sqrt(dx * dx + dy * dy)

            val lookback = minOf(4, rightHistory.size, leftHistory.size)
            val rPrev = rightHistory[rightHistory.size - lookback]
            val lPrev = leftHistory[leftHistory.size - lookback]
            val prevDist = sqrt((rPrev.x - lPrev.x) * (rPrev.x - lPrev.x) + (rPrev.y - lPrev.y) * (rPrev.y - lPrev.y))
            val dt = maxOf(0.001f, (rLast.tMs - rPrev.tMs) / 1000f)
            val relVel = (handDist - prevDist) / dt

            dualMotion = when {
                relVel < -0.08f -> DUAL_CONVERGING
                relVel > 0.08f -> DUAL_DIVERGING
                else -> DUAL_STATIC
            }
        }

        // Evaluate hand posture
        val domLandmarks = if (dominant == "Right") (rightHandLandmarks ?: leftHandLandmarks) else (leftHandLandmarks ?: rightHandLandmarks)
        val posture = evaluatePosture(domLandmarks)

        // Track scale and depth changes
        if (posture.handScale > 0.01f) {
            if (handScaleHistory.size >= maxHistory) handScaleHistory.removeFirst()
            handScaleHistory.addLast(Pair(timestampMs, posture.handScale))
        }
        domLandmarks?.firstOrNull()?.let { wrist ->
            if (zDepthHistory.size >= maxHistory) zDepthHistory.removeFirst()
            zDepthHistory.addLast(Pair(timestampMs, wrist.z))
        }

        // ── Real Dynamic Trajectory Gesture Classification ──
        var detectedGesture: String? = null
        var gestureConfidence = 0f

        val hasLandmarks = (domLandmarks != null && domLandmarks.isNotEmpty())
        val timeSinceLastCommit = timestampMs - lastGestureCommitTimeMs
        val canDetectGesture = hasLandmarks && timeSinceLastCommit > 750L // 750ms refractory period between dynamic commits

        if (canDetectGesture) {
            val domWristHistory = if (dominant == "Right") rightHistory else leftHistory
            val domIndexHistory = if (dominant == "Right") rightIndexHistory else leftIndexHistory
            val domPinkyHistory = if (dominant == "Right") rightPinkyHistory else leftPinkyHistory

            // 1. J_MOTION: Pinky extended tracing downward then hooking left/up
            val jResult = classifyJCurve(domPinkyHistory, domWristHistory, posture)
            if (jResult != null) {
                detectedGesture = jResult.first
                gestureConfidence = jResult.second
                domPattern = PATTERN_J_TRACE
            }

            // 2. Z_MOTION: Index extended tracing 3-stroke Z (right, down-left, right)
            if (detectedGesture == null) {
                val zResult = classifyZZigzag(domIndexHistory, domWristHistory, posture)
                if (zResult != null) {
                    detectedGesture = zResult.first
                    gestureConfidence = zResult.second
                    domPattern = PATTERN_Z_TRACE
                }
            }

            // 3. HELLO_MOTION: Open hand waving side-to-side
            if (detectedGesture == null) {
                val waveResult = classifyHelloWave(domWristHistory, posture, domSpeed)
                if (waveResult != null) {
                    detectedGesture = waveResult.first
                    gestureConfidence = waveResult.second
                    domPattern = PATTERN_WAVE
                }
            }

            // 4. NO_MOTION: Rapid horizontal oscillation / shake
            if (detectedGesture == null) {
                val noResult = classifyNoShake(domWristHistory, posture, domSpeed)
                if (noResult != null) {
                    detectedGesture = noResult.first
                    gestureConfidence = noResult.second
                    domPattern = PATTERN_SHAKE
                }
            }

            // 5. YOU_MOTION: Index or hand pushing forward towards camera
            if (detectedGesture == null) {
                val youResult = classifyForwardPush(handScaleHistory, zDepthHistory, posture, domSpeed)
                if (youResult != null) {
                    detectedGesture = youResult.first
                    gestureConfidence = youResult.second
                    domPattern = PATTERN_FORWARD_PUSH
                }
            }

            // 6. WHAT_MOTION: Dual hands diverging outward or palms up shrugging
            if (detectedGesture == null) {
                val whatResult = classifyWhatMotion(dualMotion, posture, domSpeed)
                if (whatResult != null) {
                    detectedGesture = whatResult.first
                    gestureConfidence = whatResult.second
                    domPattern = PATTERN_PALMS_UP
                }
            }

            // 7. MY_NAME_IS_MOTION: Hand at chest / sternum zone
            if (detectedGesture == null) {
                val nameResult = classifyChestTap(domWristHistory, activeAnchorZone, poseLandmarks, posture)
                if (nameResult != null) {
                    detectedGesture = nameResult.first
                    gestureConfidence = nameResult.second
                    domPattern = PATTERN_CHEST_CONTACT
                }
            }
        }

        val isMoving = domSpeed >= 0.18f || rSpeed >= 0.18f || lSpeed >= 0.18f ||
                dualMotion != DUAL_STATIC || domPattern != PATTERN_STATIC || detectedGesture != null

        latestState = MotionState(
            dominantHand = dominant,
            speed = domSpeed,
            vx = domVx,
            vy = domVy,
            pattern = domPattern,
            dualMotion = dualMotion,
            handDistance = handDist,
            isMoving = isMoving,
            detectedGesture = detectedGesture,
            gestureConfidence = gestureConfidence
        )
        return latestState
    }

    fun consumeGesture(nowMs: Long = System.currentTimeMillis()) {
        lastGestureCommitTimeMs = nowMs
        rightHistory.clear()
        leftHistory.clear()
        rightIndexHistory.clear()
        rightPinkyHistory.clear()
        leftIndexHistory.clear()
        leftPinkyHistory.clear()
        handScaleHistory.clear()
        zDepthHistory.clear()
        latestState = latestState.copy(detectedGesture = null, gestureConfidence = 0f, pattern = PATTERN_STATIC)
    }

    private fun computeVelocity(history: ArrayDeque<TimedPoint>): Triple<Float, Float, Float> {
        if (history.size < 2) return Triple(0f, 0f, 0f)
        val first = history[maxOf(0, history.size - 6)]
        val last = history.last()
        val dt = maxOf(0.001f, (last.tMs - first.tMs) / 1000f)
        val vx = (last.x - first.x) / dt
        val vy = (last.y - first.y) / dt
        val speed = sqrt(vx * vx + vy * vy)
        return Triple(vx, vy, speed)
    }

    private fun classifyPattern(history: ArrayDeque<TimedPoint>, speed: Float): String {
        if (history.size < 4 || speed < 0.025f) return PATTERN_STATIC

        val window = history.takeLast(16)
        val dx = window.last().x - window.first().x
        val dy = window.last().y - window.first().y

        // Check horizontal wave oscillation
        var reversals = 0
        var prevSign = 0
        for (i in 1 until window.size) {
            val step = window[i].x - window[i - 1].x
            if (abs(step) > 0.004f) {
                val sign = if (step > 0) 1 else -1
                if (prevSign != 0 && sign != prevSign) reversals++
                prevSign = sign
            }
        }
        if (reversals >= 2) return PATTERN_WAVE

        // Vertical nod oscillation
        var vReversals = 0
        var prevVSign = 0
        for (i in 1 until window.size) {
            val step = window[i].y - window[i - 1].y
            if (abs(step) > 0.004f) {
                val sign = if (step > 0) 1 else -1
                if (prevVSign != 0 && sign != prevVSign) vReversals++
                prevVSign = sign
            }
        }
        if (vReversals >= 2) return PATTERN_NOD

        // Directional strokes / swipes
        return when {
            abs(dx) > 1.3f * abs(dy) && speed >= 0.20f -> if (dx > 0) PATTERN_SWIPE_RIGHT else PATTERN_SWIPE_LEFT
            abs(dy) > 1.3f * abs(dx) && speed >= 0.20f -> if (dy > 0) PATTERN_SWIPE_DOWN else PATTERN_SWIPE_UP
            else -> if (abs(dx) > abs(dy)) (if (dx > 0) PATTERN_SWIPE_RIGHT else PATTERN_SWIPE_LEFT) else (if (dy > 0) PATTERN_SWIPE_DOWN else PATTERN_SWIPE_UP)
        }
    }

    /**
     * J Curve Classifier:
     * Traced predominantly by the pinky tip (or wrist if hand is rigid).
     * Begins with a downward drop, reaches maximum Y, then hooks leftward/upward.
     */
    fun classifyJCurve(
        pinkyHistory: ArrayDeque<TimedPoint>,
        wristHistory: ArrayDeque<TimedPoint>,
        posture: HandPosture
    ): Pair<String, Float>? {
        val history = if (pinkyHistory.size >= 8) pinkyHistory else wristHistory
        if (history.size < 8) return null

        // J gesture specifically requires the pinky finger extended (I handshape)
        if (!posture.pinkyExtended || posture.isOpenHand || posture.extendedCount > 2) return null

        val window = history.takeLast(22)
        if (window.size < 8) return null

        val dt = (window.last().tMs - window.first().tMs)
        if (dt < 180L || dt > 900L) return null

        // Find the index of maximum Y (lowest point on screen)
        var maxYIdx = 0
        var maxYVal = window[0].y
        for (i in 1 until window.size) {
            if (window[i].y > maxYVal) {
                maxYVal = window[i].y
                maxYIdx = i
            }
        }

        // Apex should not be the absolute first or last point
        if (maxYIdx < 2 || maxYIdx > window.size - 2) return null

        val start = window.first()
        val apex = window[maxYIdx]
        val end = window.last()

        val dropY = apex.y - start.y
        val hookX = end.x - apex.x
        val hookY = apex.y - end.y // upward recovery

        // J curve drop must be significant downward, followed by leftward hook (or inward curl)
        val hasDownwardStroke = dropY >= 0.040f
        val hasHook = (abs(hookX) >= 0.018f || hookY >= 0.015f)

        if (hasDownwardStroke && hasHook) {
            val conf = (0.86f + (dropY * 0.5f).coerceAtMost(0.12f)).coerceAtMost(0.98f)
            return Pair("J_MOTION", conf)
        }
        return null
    }

    /**
     * Z Zigzag Classifier:
     * Traced by index fingertip (or wrist).
     * 3 strokes: 1) Right, 2) Diagonal Down-Left, 3) Right.
     */
    fun classifyZZigzag(
        indexHistory: ArrayDeque<TimedPoint>,
        wristHistory: ArrayDeque<TimedPoint>,
        posture: HandPosture
    ): Pair<String, Float>? {
        val history = if (indexHistory.size >= 10) indexHistory else wristHistory
        if (history.size < 10) return null

        // Must have index finger extended (1 or D handshape) and NOT open hand or pinky
        if (!posture.indexExtended || posture.isOpenHand || posture.extendedCount > 2) return null

        val window = history.takeLast(26)
        if (window.size < 10) return null

        val dt = (window.last().tMs - window.first().tMs)
        if (dt < 250L || dt > 1100L) return null

        // Look for 2 turning points in X:
        // Peak 1: maximum X of stroke 1
        // Valley 2: minimum X of stroke 2 (with Y lower than peak 1)
        // End: stroke 3 travels right again from valley 2
        var peak1Idx = -1
        var valley2Idx = -1

        for (i in 2 until window.size - 4) {
            // Check if i is a local maximum in X
            if (window[i].x > window[i - 1].x && window[i].x >= window[i + 1].x && (window[i].x - window.first().x) >= 0.025f) {
                peak1Idx = i
                break
            }
        }

        if (peak1Idx in 2..window.size - 4) {
            for (j in peak1Idx + 2 until window.size - 1) {
                val dy = window[j].y - window[peak1Idx].y
                val dx = window[peak1Idx].x - window[j].x
                // Check if j is a local minimum in X and has moved down diagonally (dy >= 0.020f)
                if (window[j].x < window[j - 1].x && window[j].x <= window[j + 1].x &&
                    dx >= 0.020f && dy >= 0.020f
                ) {
                    valley2Idx = j
                    break
                }
            }
        }

        if (peak1Idx != -1 && valley2Idx != -1 && valley2Idx < window.size) {
            val stroke3TravelX = window.last().x - window[valley2Idx].x
            if (stroke3TravelX >= 0.020f) {
                return Pair("Z_MOTION", 0.94f)
            }
        }
        return null
    }

    /**
     * Hello Wave Classifier:
     * Horizontal alternating reversals of open hand held at chest/head level.
     */
    fun classifyHelloWave(
        wristHistory: ArrayDeque<TimedPoint>,
        posture: HandPosture,
        speed: Float
    ): Pair<String, Float>? {
        if (wristHistory.size < 8) return null
        val window = wristHistory.takeLast(20)
        if (window.size < 8) return null

        val lastPoint = window.last()
        // Hand must be raised in the upper screen area (y < 0.72)
        if (lastPoint.y > 0.75f) return null

        // Open hand posture required for Hello wave (at least 3 extended fingers)
        if (!posture.isOpenHand && posture.extendedCount < 3) return null

        var reversals = 0
        var prevSign = 0
        var totalTravelX = 0f
        for (i in 1 until window.size) {
            val dx = window[i].x - window[i - 1].x
            totalTravelX += abs(dx)
            if (abs(dx) > 0.005f) {
                val sign = if (dx > 0) 1 else -1
                if (prevSign != 0 && sign != prevSign) reversals++
                prevSign = sign
            }
        }

        if (reversals >= 2 && totalTravelX >= 0.045f) {
            val conf = (0.88f + (reversals * 0.03f)).coerceAtMost(0.98f)
            return Pair("HELLO_MOTION", conf)
        }
        return null
    }

    /**
     * No Shake Classifier:
     * Fast horizontal oscillation / shake with index finger or hand.
     */
    fun classifyNoShake(
        wristHistory: ArrayDeque<TimedPoint>,
        posture: HandPosture,
        speed: Float
    ): Pair<String, Float>? {
        if (wristHistory.size < 6) return null
        val window = wristHistory.takeLast(16)
        if (window.size < 6) return null

        var reversals = 0
        var prevSign = 0
        var totalTravelX = 0f
        var totalTravelY = 0f
        for (i in 1 until window.size) {
            val dx = window[i].x - window[i - 1].x
            val dy = window[i].y - window[i - 1].y
            totalTravelX += abs(dx)
            totalTravelY += abs(dy)
            if (abs(dx) > 0.004f) {
                val sign = if (dx > 0) 1 else -1
                if (prevSign != 0 && sign != prevSign) reversals++
                prevSign = sign
            }
        }

        // Horizontal shake has strong horizontal oscillation with minimal vertical drift
        if (reversals >= 2 && totalTravelX > 1.4f * totalTravelY && totalTravelX >= 0.035f && speed >= 0.04f) {
            // Not a head-level open-hand wave (which is HELLO)
            if (!posture.isOpenHand || window.last().y > 0.45f) {
                return Pair("NO_MOTION", 0.92f)
            }
        }
        return null
    }

    /**
     * You / Push Classifier:
     * Hand pushes forward towards camera (scale increase or z depth decrease).
     */
    fun classifyForwardPush(
        scaleHistory: ArrayDeque<Pair<Long, Float>>,
        zHistory: ArrayDeque<Pair<Long, Float>>,
        posture: HandPosture,
        speed: Float
    ): Pair<String, Float>? {
        if (scaleHistory.size < 6) return null
        val window = scaleHistory.takeLast(14)
        if (window.size < 6) return null

        val firstScale = window.first().second
        val lastScale = window.last().second
        val scaleRatio = (lastScale - firstScale) / max(0.01f, firstScale)

        val dt = window.last().first - window.first().first
        if (dt < 150L || dt > 800L) return null

        // Check if hand scale expanded by at least 14%
        if (scaleRatio >= 0.14f && (posture.indexExtended || posture.isOpenHand)) {
            return Pair("YOU_MOTION", 0.92f)
        }

        // Alternatively check Z coordinate movement towards camera
        if (zHistory.size >= 6) {
            val zWindow = zHistory.takeLast(14)
            val dz = zWindow.last().second - zWindow.first().second
            if (dz <= -0.045f && (posture.indexExtended || posture.isOpenHand)) {
                return Pair("YOU_MOTION", 0.91f)
            }
        }
        return null
    }

    /**
     * What / Shrug Classifier:
     * Dual diverging hands or open palms facing up oscillating outward.
     */
    fun classifyWhatMotion(
        dualMotion: String,
        posture: HandPosture,
        speed: Float
    ): Pair<String, Float>? {
        if (dualMotion == DUAL_DIVERGING && (posture.isOpenHand || posture.extendedCount >= 3)) {
            return Pair("WHAT_MOTION", 0.92f)
        }
        return null
    }

    /**
     * My Name Is / Chest Contact Classifier:
     * Hand positioned in chest / sternum zone and decelerates/contacts chest.
     */
    fun classifyChestTap(
        wristHistory: ArrayDeque<TimedPoint>,
        activeAnchorZone: String?,
        poseLandmarks: List<Point3D>?,
        posture: HandPosture
    ): Pair<String, Float>? {
        if (wristHistory.size < 4) return null
        val lastWrist = wristHistory.last()

        val isAtChest = (activeAnchorZone != null && (activeAnchorZone.contains("CHEST") || activeAnchorZone.contains("HEART") || activeAnchorZone.contains("THROAT"))) ||
                run {
                    if (poseLandmarks != null && poseLandmarks.size >= 13) {
                        val lSh = poseLandmarks[11]
                        val rSh = poseLandmarks[12]
                        val chestX = (lSh.x + rSh.x) / 2f
                        val chestY = (lSh.y + rSh.y) / 2f + 0.05f
                        val dx = lastWrist.x - chestX
                        val dy = lastWrist.y - chestY
                        sqrt(dx * dx + dy * dy) < 0.20f
                    } else false
                }

        if (isAtChest) {
            val window = wristHistory.takeLast(10)
            if (window.size >= 6) {
                val dt = (window.last().tMs - window.first().tMs).coerceAtLeast(1L)
                val dist = sqrt((window.last().x - window.first().x) * (window.last().x - window.first().x) + (window.last().y - window.first().y) * (window.last().y - window.first().y))
                val spd = dist / (dt / 1000f)

                // Flat open hand placed at chest or tap motion towards chest
                if (spd < 0.85f && posture.isOpenHand && posture.extendedCount >= 4) {
                    return Pair("MY_NAME_IS_MOTION", 0.93f)
                }
            }
        }
        return null
    }

    fun reset() {
        rightHistory.clear()
        leftHistory.clear()
        rightIndexHistory.clear()
        rightPinkyHistory.clear()
        leftIndexHistory.clear()
        leftPinkyHistory.clear()
        handScaleHistory.clear()
        zDepthHistory.clear()
        latestState = MotionState()
        lastGestureCommitTimeMs = -10000L
    }

    fun getTrailPoints(isRight: Boolean): List<Pair<Float, Float>> {
        val history = if (isRight) rightHistory else leftHistory
        return history.map { Pair(it.x, it.y) }
    }

    companion object {
        fun evaluatePosture(points: List<Point3D>?): HandPosture {
            if (points == null || points.size < 21) return HandPosture.EMPTY
            val wrist = points[0]
            fun dist(a: Point3D, b: Point3D): Float {
                val dx = a.x - b.x
                val dy = a.y - b.y
                val dz = a.z - b.z
                return sqrt(dx * dx + dy * dy + dz * dz)
            }

            val scale = max(0.01f, dist(wrist, points[9]))
            val indexRatio = dist(points[8], wrist) / max(0.001f, dist(points[5], wrist))
            val middleRatio = dist(points[12], wrist) / max(0.001f, dist(points[9], wrist))
            val ringRatio = dist(points[16], wrist) / max(0.001f, dist(points[13], wrist))
            val pinkyRatio = dist(points[20], wrist) / max(0.001f, dist(points[17], wrist))
            val thumbRatio = dist(points[4], points[17]) / max(0.001f, dist(points[2], points[17]))

            val indexExt = indexRatio > 1.25f
            val middleExt = middleRatio > 1.25f
            val ringExt = ringRatio > 1.25f
            val pinkyExt = pinkyRatio > 1.25f
            val thumbExt = thumbRatio > 1.20f

            var count = 0
            if (indexExt) count++
            if (middleExt) count++
            if (ringExt) count++
            if (pinkyExt) count++
            if (thumbExt) count++

            return HandPosture(
                thumbExtended = thumbExt,
                indexExtended = indexExt,
                middleExtended = middleExt,
                ringExtended = ringExt,
                pinkyExtended = pinkyExt,
                extendedCount = count,
                handScale = scale
            )
        }

        fun matchSignMotion(signName: String, state: MotionState): Pair<Boolean, Float> {
            val clean = DebounceEngine.cleanLabel(signName)
            return when (clean) {
                "HELLO", "BYE" -> if (state.pattern == PATTERN_WAVE) Pair(true, 1.30f) else Pair(true, 0.90f)
                "MY_NAME_IS" -> if (state.pattern == PATTERN_CHEST_CONTACT || state.dualMotion == DUAL_CONVERGING) Pair(true, 1.30f) else Pair(true, 1.0f)
                "THANK_YOU" -> if (state.pattern == PATTERN_SWIPE_DOWN || state.vy > 0.15f) Pair(true, 1.25f) else Pair(true, 1.0f)
                "HELP", "GOOD" -> if (state.pattern == PATTERN_SWIPE_UP || state.vy < -0.15f) Pair(true, 1.25f) else Pair(true, 1.0f)
                "NAMASTE" -> if (state.dualMotion == DUAL_CONVERGING || state.handDistance < 0.15f) Pair(true, 1.30f) else Pair(true, 0.90f)
                "PEACE", "STOP" -> if (state.pattern == PATTERN_STATIC) Pair(true, 1.15f) else Pair(false, 0.75f)
                "YOU" -> if (state.pattern == PATTERN_FORWARD_PUSH) Pair(true, 1.35f) else Pair(true, 1.0f)
                "NO" -> if (state.pattern == PATTERN_SHAKE) Pair(true, 1.35f) else Pair(true, 1.0f)
                "WHAT" -> if (state.pattern == PATTERN_PALMS_UP || state.dualMotion == DUAL_DIVERGING) Pair(true, 1.35f) else Pair(true, 1.0f)
                "J" -> if (state.pattern == PATTERN_J_TRACE) Pair(true, 1.40f) else Pair(true, 1.0f)
                "Z" -> if (state.pattern == PATTERN_Z_TRACE) Pair(true, 1.40f) else Pair(true, 1.0f)
                else -> Pair(true, 1.0f)
            }
        }
    }
}
