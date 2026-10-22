package ai.signora.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import ai.signora.ml.BodySegmentation
import ai.signora.ml.BodySegmentZone
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
    isFitCenter: Boolean = false,
    rightMotionTrail: List<Pair<Float, Float>> = emptyList(),
    leftMotionTrail: List<Pair<Float, Float>> = emptyList(),
    modifier: Modifier = Modifier
) {
    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    val hudPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textSize = 28f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    // Hoist expensive BodySegmentation computation out of the Canvas draw lambda
    // so it only re-runs when hands/pose change, not on every draw pass
    val segmentData = remember(hands, pose) {
        if (pose != null && pose.points.size >= 25) {
            val segs = BodySegmentation.computeSegments(pose)
            val active = BodySegmentation.detectActiveSegments(hands, pose)
            Triple(segs, active, pose)
        } else {
            null
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val camW = cameraResolution.first.toFloat().coerceAtLeast(1f)
        val camH = cameraResolution.second.toFloat().coerceAtLeast(1f)

        val scale = if (isFitCenter) {
            minOf(canvasWidth / camW, canvasHeight / camH)
        } else {
            maxOf(canvasWidth / camW, canvasHeight / camH)
        }
        val scaledW = camW * scale
        val scaledH = camH * scale
        val offsetX = (canvasWidth - scaledW) / 2f
        val offsetY = (canvasHeight - scaledH) / 2f

        val toScreen: (Point3D) -> Offset = { p ->
            Offset(
                x = p.x * scaledW + offsetX,
                y = p.y * scaledH + offsetY
            )
        }

        // 1. Compute and Draw Anatomical Body Segments
        if (segmentData != null) {
            val (segments, activeSegments, poseNonNull) = segmentData
            val targetSegment = BodySegmentation.matchTargetSegment(targetAnchor, segments)

            drawBodySegments(
                segments = segments,
                activeSegments = activeSegments,
                targetSegment = targetSegment,
                isTargetMatched = isAnchorMatched,
                toScreen = toScreen,
                scaledW = scaledW,
                scaledH = scaledH,
                textPaint = textPaint
            )

            // Draw Upper Body Skeleton Lines
            drawBodyPose(poseNonNull, toScreen)

            // Draw Top Active Segment Cybernetic HUD Pill
            drawSegmentHud(
                activeSegments = activeSegments,
                targetSegment = targetSegment,
                isMatched = isAnchorMatched,
                canvasWidth = canvasWidth,
                hudPaint = hudPaint
            )
        }

        // 2. Draw Hand Skeletons
        for (hand in hands) {
            drawHandSkeleton(hand, toScreen)
        }

        // 2.5 Draw Glowing Motion Trails for dynamic gestures
        val cyanBase = Color(0xFF06B6D4) // Right hand neon cyan
        val roseBase = Color(0xFFF43F5E) // Left hand neon rose

        listOf(
            rightMotionTrail to cyanBase,
            leftMotionTrail to roseBase
        ).forEach { (trail, color) ->
            if (trail.size >= 2) {
                val n = trail.size
                for (i in 1 until n) {
                    val p0 = trail[i - 1]
                    val p1 = trail[i]
                    val o0 = Offset(p0.first * scaledW + offsetX, p0.second * scaledH + offsetY)
                    val o1 = Offset(p1.first * scaledW + offsetX, p1.second * scaledH + offsetY)
                    val alpha = i.toFloat() / n.toFloat()
                    val width = (3.5f * alpha).coerceAtLeast(1.5f)
                    drawLine(
                        color = color.copy(alpha = alpha * 0.9f),
                        start = o0,
                        end = o1,
                        strokeWidth = width,
                        cap = StrokeCap.Round
                    )
                }
                val last = trail.last()
                val lastOffset = Offset(last.first * scaledW + offsetX, last.second * scaledH + offsetY)
                drawCircle(color = color, radius = 6f, center = lastOffset)
                drawCircle(color = Color.White, radius = 2.5f, center = lastOffset)
            }
        }

        // 3. Draw Target Reticle if targetAnchorPt is provided
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

private fun DrawScope.drawBodySegments(
    segments: List<BodySegmentZone>,
    activeSegments: List<BodySegmentZone>,
    targetSegment: BodySegmentZone?,
    isTargetMatched: Boolean,
    toScreen: (Point3D) -> Offset,
    scaledW: Float,
    scaledH: Float,
    textPaint: Paint
) {
    val emerald = Color(0xFF10B981)
    val amber = Color(0xFFF59E0B)
    val indigo = Color(0xFF818CF8)
    val cyan = Color(0xFF06B6D4)

    for (seg in segments) {
        val isActive = activeSegments.any { it.id == seg.id }
        val isTarget = (targetSegment?.id == seg.id)
        if (!isActive && !isTarget) continue

        val center = toScreen(seg.center)
        val w = seg.width * scaledW
        val h = seg.height * scaledH
        val left = center.x - w / 2f
        val top = center.y - h / 2f
        val right = left + w
        val bottom = top + h

        val strokeColor = when {
            isActive -> emerald
            isTarget -> if (isTargetMatched) emerald else amber
            else -> indigo.copy(alpha = 0.40f)
        }

        val fillColor = when {
            isActive -> emerald.copy(alpha = 0.24f)
            isTarget -> (if (isTargetMatched) emerald else amber).copy(alpha = 0.18f)
            else -> indigo.copy(alpha = 0.05f)
        }

        val cornerRadius = CornerRadius(16f, 16f)

        // 1. Semi-transparent segment background
        drawRoundRect(
            color = fillColor,
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = cornerRadius
        )

        // 2. High-tech border
        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = cornerRadius,
            style = Stroke(
                width = if (isActive || isTarget) 3.5f else 1.5f,
                pathEffect = if (isActive || (isTarget && isTargetMatched)) null else PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
            )
        )

        // 3. Cybernetic corner brackets
        val bracketLen = minOf(14f, w * 0.22f, h * 0.22f)
        val bracketColor = if (isActive || isTarget) strokeColor else cyan.copy(alpha = 0.70f)
        val bWidth = if (isActive || isTarget) 3.5f else 2.2f

        // Top-Left
        drawLine(bracketColor, Offset(left - 2f, top + bracketLen), Offset(left - 2f, top - 2f), bWidth, StrokeCap.Round)
        drawLine(bracketColor, Offset(left - 2f, top - 2f), Offset(left + bracketLen, top - 2f), bWidth, StrokeCap.Round)

        // Top-Right
        drawLine(bracketColor, Offset(right + 2f - bracketLen, top - 2f), Offset(right + 2f, top - 2f), bWidth, StrokeCap.Round)
        drawLine(bracketColor, Offset(right + 2f, top - 2f), Offset(right + 2f, top + bracketLen), bWidth, StrokeCap.Round)

        // Bottom-Left
        drawLine(bracketColor, Offset(left - 2f, bottom - bracketLen), Offset(left - 2f, bottom + 2f), bWidth, StrokeCap.Round)
        drawLine(bracketColor, Offset(left - 2f, bottom + 2f), Offset(left + bracketLen, bottom + 2f), bWidth, StrokeCap.Round)

        // Bottom-Right
        drawLine(bracketColor, Offset(right + 2f - bracketLen, bottom + 2f), Offset(right + 2f, bottom + 2f), bWidth, StrokeCap.Round)
        drawLine(bracketColor, Offset(right + 2f, bottom + 2f), Offset(right + 2f, bottom - bracketLen), bWidth, StrokeCap.Round)

        // 4. Segment Tag Label
        textPaint.color = strokeColor.toArgb()
        val tag = when {
            isActive -> "▶ ${seg.shortTag} ◀"
            isTarget -> if (isTargetMatched) "✓ ${seg.shortTag}" else "🎯 ${seg.shortTag}"
            else -> seg.shortTag
        }
        drawContext.canvas.nativeCanvas.drawText(
            tag,
            center.x,
            top + 22f,
            textPaint
        )
    }
}

private fun DrawScope.drawSegmentHud(
    activeSegments: List<BodySegmentZone>,
    targetSegment: BodySegmentZone?,
    isMatched: Boolean,
    canvasWidth: Float,
    hudPaint: Paint
) {
    if (activeSegments.isEmpty() && targetSegment == null) return

    val hudWidth = if (activeSegments.size >= 2) 520f else 440f
    val hudHeight = 44f
    val hudLeft = (canvasWidth - hudWidth) / 2f
    val hudTop = 16f

    val emerald = Color(0xFF10B981)
    val amber = Color(0xFFF59E0B)

    val activeColor = if (isMatched || activeSegments.any { it == targetSegment }) emerald else if (activeSegments.isNotEmpty()) emerald else amber

    drawRoundRect(
        color = Color(0xFF0F172A).copy(alpha = 0.85f),
        topLeft = Offset(hudLeft, hudTop),
        size = Size(hudWidth, hudHeight),
        cornerRadius = CornerRadius(22f, 22f)
    )

    drawRoundRect(
        color = activeColor.copy(alpha = 0.70f),
        topLeft = Offset(hudLeft, hudTop),
        size = Size(hudWidth, hudHeight),
        cornerRadius = CornerRadius(22f, 22f),
        style = Stroke(width = 2f)
    )

    hudPaint.color = activeColor.toArgb()
    val hudText = when {
        isMatched -> "✓ TARGET MATCHED: ${targetSegment?.shortTag ?: ""}"
        activeSegments.size >= 2 -> "📍 DUAL ZONES: ${activeSegments[0].shortTag} + ${activeSegments[1].shortTag}"
        activeSegments.size == 1 -> "📍 ZONE: ${activeSegments[0].displayName.uppercase()}"
        targetSegment != null -> "🎯 TARGET: ${targetSegment.shortTag}"
        else -> ""
    }

    drawContext.canvas.nativeCanvas.drawText(
        hudText,
        canvasWidth / 2f,
        hudTop + 31f,
        hudPaint
    )
}

private fun DrawScope.drawBodyPose(pose: PoseLandmarks, toScreen: (Point3D) -> Offset) {
    val pts = pose.points
    val boneColor = Color(0xFF6366F1).copy(alpha = 0.65f)
    val jointColor = Color(0xFFA5B4FC)

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
            drawCircle(
                color = jointColor,
                radius = 5f,
                center = center
            )
            drawCircle(
                color = Color.White,
                radius = 2f,
                center = center
            )
        }
    }
}

private fun DrawScope.drawHandSkeleton(hand: HandLandmarks, toScreen: (Point3D) -> Offset) {
    val pts = hand.points
    if (pts.size < 21) return
    val isRight = hand.isRightHand

    val boneColor = if (isRight) Color(0xFF06B6D4) else Color(0xFFF43F5E)
    val jointColor = if (isRight) Color(0xFF34D399) else Color(0xFFFB923C)

    for ((s, e) in HAND_CONNECTIONS) {
        if (s < pts.size && e < pts.size) {
            val p1 = toScreen(pts[s])
            val p2 = toScreen(pts[e])
            drawLine(
                color = boneColor,
                start = p1,
                end = p2,
                strokeWidth = 3.5f,
                cap = StrokeCap.Round
            )
        }
    }

    for (p in pts) {
        val center = toScreen(p)
        drawCircle(
            color = jointColor,
            radius = 4.5f,
            center = center
        )
        drawCircle(
            color = Color.White,
            radius = 1.8f,
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

    val reticleColor = if (isMatched) Color(0xFF10B981) else Color(0xFFF59E0B)
    val glowColor = reticleColor.copy(alpha = 0.28f)

    drawCircle(color = glowColor, radius = 48f, center = center)
    drawCircle(
        color = reticleColor,
        radius = 34f,
        center = center,
        style = Stroke(
            width = 3.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
        )
    )
    drawCircle(color = reticleColor, radius = 16f, center = center, style = Stroke(width = 2.5f))
    drawCircle(color = reticleColor, radius = 4.5f, center = center)

    val tickLen = 12f
    drawLine(color = reticleColor, start = Offset(cx - 40f, cy), end = Offset(cx - 40f + tickLen, cy), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(color = reticleColor, start = Offset(cx + 40f - tickLen, cy), end = Offset(cx + 40f, cy), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(color = reticleColor, start = Offset(cx, cy - 40f), end = Offset(cx, cy - 40f + tickLen), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(color = reticleColor, start = Offset(cx, cy + 40f - tickLen), end = Offset(cx, cy + 40f), strokeWidth = 3f, cap = StrokeCap.Round)
}
