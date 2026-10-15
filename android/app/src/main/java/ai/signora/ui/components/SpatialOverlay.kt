package ai.signora.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import ai.signora.models.HandLandmarks
import ai.signora.models.Point3D
import ai.signora.models.PoseLandmarks

// Canonical hand connections
private val HAND_CONNECTIONS = listOf(
    // Palm
    0 to 1, 0 to 5, 5 to 9, 9 to 13, 13 to 17, 0 to 17,
    // Thumb
    1 to 2, 2 to 3, 3 to 4,
    // Index
    5 to 6, 6 to 7, 7 to 8,
    // Middle
    9 to 10, 10 to 11, 11 to 12,
    // Ring
    13 to 14, 14 to 15, 15 to 16,
    // Pinky
    17 to 18, 18 to 19, 19 to 20
)

// Upper body connections
private val UPPER_BODY_CONNECTIONS = listOf(
    11 to 12, // Shoulders
    11 to 13, 13 to 15, // Left Arm
    12 to 14, 14 to 16, // Right Arm
    11 to 23, 12 to 24, // Torso sides
    23 to 24 // Waist
)

@Composable
fun SpatialOverlay(
    hands: List<HandLandmarks>,
    pose: PoseLandmarks?,
    targetAnchor: String?,
    targetAnchorPt: Point3D?,
    isAnchorMatched: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // 1. Draw Upper Body Skeleton
        if (pose != null && pose.points.size >= 25) {
            drawBodyPose(pose, canvasWidth, canvasHeight)
        }

        // 2. Draw Hand Skeletons
        for (hand in hands) {
            drawHandSkeleton(hand, canvasWidth, canvasHeight)
        }

        // 3. Draw Cybernetic Target Reticle if targetAnchor is set
        if (targetAnchorPt != null && targetAnchor != null) {
            drawAnchorReticle(
                targetAnchor = targetAnchor,
                pt = targetAnchorPt,
                isMatched = isAnchorMatched,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight
            )
        }
    }
}

private fun DrawScope.drawBodyPose(pose: PoseLandmarks, w: Float, h: Float) {
    val pts = pose.points
    val isFront = pose.isFrontCamera
    val boneColor = Color(0xFF6366F1).copy(alpha = 0.55f) // Electric Indigo
    val jointColor = Color(0xFF818CF8).copy(alpha = 0.85f)

    fun toScreen(p: Point3D): Offset {
        val x = if (isFront) (1f - p.x) * w else p.x * w
        val y = p.y * h
        return Offset(x, y)
    }

    // Bones
    for ((s, e) in UPPER_BODY_CONNECTIONS) {
        if (s < pts.size && e < pts.size) {
            val p1 = toScreen(pts[s])
            val p2 = toScreen(pts[e])
            drawLine(
                color = boneColor,
                start = p1,
                end = p2,
                strokeWidth = 4f
            )
        }
    }

    // Key Joints: Shoulders (11, 12), Elbows (13, 14), Wrists (15, 16), Hips (23, 24)
    val keyJoints = intArrayOf(11, 12, 13, 14, 15, 16, 23, 24)
    for (idx in keyJoints) {
        if (idx < pts.size) {
            val center = toScreen(pts[idx])
            drawCircle(
                color = jointColor,
                radius = 6f,
                center = center
            )
        }
    }
}

private fun DrawScope.drawHandSkeleton(hand: HandLandmarks, w: Float, h: Float) {
    val pts = hand.points
    val isFront = hand.isFrontCamera
    val isRight = hand.isRightHand

    // Cyan for right hand, Magenta for left hand
    val boneColor = if (isRight) Color(0xFF06B6D4) else Color(0xFFF43F5E)
    val jointColor = if (isRight) Color(0xFF34D399) else Color(0xFFFB923C)

    fun toScreen(p: Point3D): Offset {
        val x = if (isFront) (1f - p.x) * w else p.x * w
        val y = p.y * h
        return Offset(x, y)
    }

    // Bones
    for ((s, e) in HAND_CONNECTIONS) {
        if (s < pts.size && e < pts.size) {
            val p1 = toScreen(pts[s])
            val p2 = toScreen(pts[e])
            drawLine(
                color = boneColor,
                start = p1,
                end = p2,
                strokeWidth = 5f
            )
        }
    }

    // Joints
    for (p in pts) {
        val center = toScreen(p)
        drawCircle(
            color = jointColor,
            radius = 5.5f,
            center = center
        )
    }
}

private fun DrawScope.drawAnchorReticle(
    targetAnchor: String,
    pt: Point3D,
    isMatched: Boolean,
    canvasWidth: Float,
    canvasHeight: Float
) {
    val cx = (1f - pt.x) * canvasWidth
    val cy = pt.y * canvasHeight
    val center = Offset(cx, cy)

    val reticleColor = if (isMatched) Color(0xFF10B981) else Color(0xFFF59E0B) // Emerald vs Amber
    val glowColor = reticleColor.copy(alpha = 0.25f)

    // Outer glow
    drawCircle(
        color = glowColor,
        radius = 48f,
        center = center
    )

    // Outer dashed target ring
    drawCircle(
        color = reticleColor,
        radius = 36f,
        center = center,
        style = Stroke(
            width = 3.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
        )
    )

    // Inner target ring
    drawCircle(
        color = reticleColor,
        radius = 18f,
        center = center,
        style = Stroke(width = 2.5f)
    )

    // Center focal dot
    drawCircle(
        color = reticleColor,
        radius = 5f,
        center = center
    )

    // Crosshair ticks
    val tickLen = 12f
    drawLine(color = reticleColor, start = Offset(cx - 42f, cy), end = Offset(cx - 42f + tickLen, cy), strokeWidth = 3f)
    drawLine(color = reticleColor, start = Offset(cx + 42f - tickLen, cy), end = Offset(cx + 42f, cy), strokeWidth = 3f)
    drawLine(color = reticleColor, start = Offset(cx, cy - 42f), end = Offset(cx, cy - 42f + tickLen), strokeWidth = 3f)
    drawLine(color = reticleColor, start = Offset(cx, cy + 42f - tickLen), end = Offset(cx, cy + 42f), strokeWidth = 3f)
}
