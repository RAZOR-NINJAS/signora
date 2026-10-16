package ai.signora.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
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
    cameraResolution: Pair<Int, Int> = Pair(480, 640),
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Dynamic camera analysis resolution in portrait
        val camW = cameraResolution.first.toFloat().coerceAtLeast(1f)
        val camH = cameraResolution.second.toFloat().coerceAtLeast(1f)

        // Exact FILL_CENTER transformation matching PreviewView
        val scale = maxOf(canvasWidth / camW, canvasHeight / camH)
        val scaledW = camW * scale
        val scaledH = camH * scale
        val offsetX = (canvasWidth - scaledW) / 2f
        val offsetY = (canvasHeight - scaledH) / 2f

        // CameraXManager already produces upright, front-camera mirrored frames,
        // so landmark normalized (x, y) map directly to camera image coordinates.
        val toScreen: (Point3D) -> Offset = { p ->
            Offset(
                x = p.x * scaledW + offsetX,
                y = p.y * scaledH + offsetY
            )
        }

        // 1. Draw Upper Body Skeleton
        if (pose != null && pose.points.size >= 25) {
            drawBodyPose(pose, toScreen)
        }

        // 2. Draw Hand Skeletons
        for (hand in hands) {
            drawHandSkeleton(hand, toScreen)
        }

        // 3. Draw Cybernetic Target Reticle if targetAnchor is set
        if (targetAnchorPt != null && targetAnchor != null) {
            drawAnchorReticle(
                targetAnchor = targetAnchor,
                pt = targetAnchorPt,
                isMatched = isAnchorMatched,
                toScreen = toScreen
            )
        }
    }
}

private fun DrawScope.drawBodyPose(pose: PoseLandmarks, toScreen: (Point3D) -> Offset) {
    val pts = pose.points
    val boneColor = Color(0xFF6366F1).copy(alpha = 0.65f) // Electric Indigo
    val jointColor = Color(0xFFA5B4FC) // Soft Violet glow

    // Bones
    for ((s, e) in UPPER_BODY_CONNECTIONS) {
        if (s < pts.size && e < pts.size) {
            val p1 = toScreen(pts[s])
            val p2 = toScreen(pts[e])
            drawLine(
                color = boneColor,
                start = p1,
                end = p2,
                strokeWidth = 4.5f,
                cap = StrokeCap.Round
            )
        }
    }

    // Key Joints: Shoulders (11, 12), Elbows (13, 14), Wrists (15, 16), Hips (23, 24)
    val keyJoints = intArrayOf(11, 12, 13, 14, 15, 16, 23, 24)
    for (idx in keyJoints) {
        if (idx < pts.size) {
            val center = toScreen(pts[idx])
            // Outer glow
            drawCircle(
                color = jointColor.copy(alpha = 0.35f),
                radius = 9f,
                center = center
            )
            // Inner core
            drawCircle(
                color = jointColor,
                radius = 5.5f,
                center = center
            )
        }
    }
}

private fun DrawScope.drawHandSkeleton(hand: HandLandmarks, toScreen: (Point3D) -> Offset) {
    val pts = hand.points
    if (pts.size < 21) return
    val isRight = hand.isRightHand

    // Cyan for right hand, Magenta for left hand
    val boneColor = if (isRight) Color(0xFF06B6D4) else Color(0xFFF43F5E)
    val jointColor = if (isRight) Color(0xFF34D399) else Color(0xFFFB923C)

    // Bones
    for ((s, e) in HAND_CONNECTIONS) {
        if (s < pts.size && e < pts.size) {
            val p1 = toScreen(pts[s])
            val p2 = toScreen(pts[e])
            drawLine(
                color = boneColor,
                start = p1,
                end = p2,
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
        }
    }

    // Joints
    for (p in pts) {
        val center = toScreen(p)
        drawCircle(
            color = jointColor.copy(alpha = 0.35f),
            radius = 8f,
            center = center
        )
        drawCircle(
            color = jointColor,
            radius = 5f,
            center = center
        )
    }
}

private fun DrawScope.drawAnchorReticle(
    targetAnchor: String,
    pt: Point3D,
    isMatched: Boolean,
    toScreen: (Point3D) -> Offset
) {
    val center = toScreen(pt)
    val cx = center.x
    val cy = center.y

    val reticleColor = if (isMatched) Color(0xFF10B981) else Color(0xFFF59E0B) // Emerald vs Amber
    val glowColor = reticleColor.copy(alpha = 0.28f)

    // Outer glow halo
    drawCircle(
        color = glowColor,
        radius = 48f,
        center = center
    )

    // Outer dashed target ring
    drawCircle(
        color = reticleColor,
        radius = 34f,
        center = center,
        style = Stroke(
            width = 3.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
        )
    )

    // Inner target ring
    drawCircle(
        color = reticleColor,
        radius = 16f,
        center = center,
        style = Stroke(width = 2.5f)
    )

    // Center focal point
    drawCircle(
        color = reticleColor,
        radius = 4.5f,
        center = center
    )

    // Crosshair ticks
    val tickLen = 12f
    drawLine(color = reticleColor, start = Offset(cx - 40f, cy), end = Offset(cx - 40f + tickLen, cy), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(color = reticleColor, start = Offset(cx + 40f - tickLen, cy), end = Offset(cx + 40f, cy), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(color = reticleColor, start = Offset(cx, cy - 40f), end = Offset(cx, cy - 40f + tickLen), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(color = reticleColor, start = Offset(cx, cy + 40f - tickLen), end = Offset(cx, cy + 40f), strokeWidth = 3f, cap = StrokeCap.Round)
}
