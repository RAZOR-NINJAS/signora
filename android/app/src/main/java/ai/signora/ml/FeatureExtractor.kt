package ai.signora.ml

import ai.signora.models.HandLandmarks
import ai.signora.models.Point3D
import kotlin.math.sqrt

/**
 * Scale-invariant geometric feature extractor for ASL and ISL.
 *
 * Implements the mathematical feature normalization pipeline of Signora:
 * - 74D ASL: Wrist translation, palm-scale normalization, 15 inter-joint distance ratios,
 *            digit extensions, fingertip pinch & spread, palm normal vector, and mean depth.
 * - 164D ISL: Two-hand 74D descriptors (148D) + 14D inter-hand relative geometry + 2D presence flags.
 */
object FeatureExtractor {

    private const val WRIST = 0
    private const val THUMB_TIP = 4
    private const val INDEX_MCP = 5
    private const val INDEX_TIP = 8
    private const val MIDDLE_MCP = 9
    private const val MIDDLE_TIP = 12
    private const val RING_TIP = 16
    private const val PINKY_MCP = 17
    private const val PINKY_TIP = 20

    private val DIGIT_CHAINS = listOf(
        Triple(2, 3, 4),   // Thumb
        Triple(5, 6, 8),   // Index
        Triple(9, 10, 12), // Middle
        Triple(13, 14, 16),// Ring
        Triple(17, 18, 20) // Pinky
    )

    private val FINGERTIPS = intArrayOf(THUMB_TIP, INDEX_TIP, MIDDLE_TIP, RING_TIP, PINKY_TIP)

    fun extractAslFeatures(rawPoints: List<Point3D>, isRightHand: Boolean = true): FloatArray {
        if (rawPoints.size < 21) return FloatArray(74)

        // 1. Invert X for left hand to ensure orientation invariance
        val pts = Array(21) { i ->
            val p = rawPoints[i]
            floatArrayOf(if (isRightHand) p.x else -p.x, p.y, p.z)
        }

        // 2. Translate wrist (index 0) to origin
        val wX = pts[WRIST][0]
        val wY = pts[WRIST][1]
        val wZ = pts[WRIST][2]
        for (i in 0 until 21) {
            pts[i][0] -= wX
            pts[i][1] -= wY
            pts[i][2] -= wZ
        }

        // 3. Scale by palm size: dist(wrist, middle_mcp)
        val palmDist = dist3D(pts[WRIST], pts[MIDDLE_MCP])
        val scale = if (palmDist < 1e-6f) 1.0f else palmDist
        for (i in 0 until 21) {
            pts[i][0] /= scale
            pts[i][1] /= scale
            pts[i][2] /= scale
        }

        val palm = dist3D(pts[WRIST], pts[MIDDLE_MCP]).let { if (it < 1e-6f) 1f else it }

        // Output buffer: 74 features
        val feats = FloatArray(74)
        var idx = 0

        // 63 normalized coordinates (21 * 3)
        for (i in 0 until 21) {
            feats[idx++] = pts[i][0]
            feats[idx++] = pts[i][1]
            feats[idx++] = pts[i][2]
        }

        // 5 digit extension ratios
        for ((mcp, _, tip) in DIGIT_CHAINS) {
            feats[idx++] = dist3D(pts[tip], pts[mcp]) / palm
        }

        // 1 pinch ratio (thumb tip to index tip)
        feats[idx++] = dist3D(pts[THUMB_TIP], pts[INDEX_TIP]) / palm

        // 1 spread ratio (adjacent fingertip distances)
        val spreadPairs = listOf(
            INDEX_TIP to MIDDLE_TIP,
            MIDDLE_TIP to RING_TIP,
            RING_TIP to PINKY_TIP
        )
        var spreadSum = 0f
        for ((a, b) in spreadPairs) {
            spreadSum += dist3D(pts[a], pts[b])
        }
        feats[idx++] = spreadSum / (3f * palm)

        // 1 mean fingertip depth
        var depthSum = 0f
        for (tip in FINGERTIPS) {
            depthSum += pts[tip][2]
        }
        feats[idx++] = depthSum / FINGERTIPS.size

        // 3 palm normal vector (cross product: index_mcp - wrist X pinky_mcp - wrist)
        val v1 = floatArrayOf(pts[INDEX_MCP][0] - pts[WRIST][0], pts[INDEX_MCP][1] - pts[WRIST][1], pts[INDEX_MCP][2] - pts[WRIST][2])
        val v2 = floatArrayOf(pts[PINKY_MCP][0] - pts[WRIST][0], pts[PINKY_MCP][1] - pts[WRIST][1], pts[PINKY_MCP][2] - pts[WRIST][2])
        val normal = crossProduct(v1, v2)
        feats[idx++] = normal[0]
        feats[idx++] = normal[1]
        feats[idx++] = normal[2]

        return feats
    }

    fun extractIslFeatures(hands: List<HandLandmarks>): FloatArray {
        val out = FloatArray(164)
        if (hands.isEmpty()) return out

        // Identify primary and secondary hand
        val hand1 = hands[0]
        val hand2 = if (hands.size > 1) hands[1] else null

        // 1. Single-hand features for hand 1 (74D)
        val f1 = extractAslFeatures(hand1.points, hand1.isRightHand)
        System.arraycopy(f1, 0, out, 0, 74)

        // 2. Single-hand features for hand 2 (74D) if present
        if (hand2 != null) {
            val f2 = extractAslFeatures(hand2.points, hand2.isRightHand)
            System.arraycopy(f2, 0, out, 74, 74)
        }

        // 3. Inter-hand spatial geometry (14D)
        var offset = 148
        if (hand2 != null && hand1.points.size >= 21 && hand2.points.size >= 21) {
            val p1 = hand1.points
            val p2 = hand2.points
            // Wrist distance
            out[offset++] = dist3D(p1[WRIST], p2[WRIST])
            // Fingertip cross-distances
            for (tip in FINGERTIPS) {
                out[offset++] = dist3D(p1[tip], p2[tip])
            }
            // Index to thumb cross-distances
            out[offset++] = dist3D(p1[INDEX_TIP], p2[THUMB_TIP])
            out[offset++] = dist3D(p1[THUMB_TIP], p2[INDEX_TIP])
            // Relative wrist delta
            out[offset++] = p2[WRIST].x - p1[WRIST].x
            out[offset++] = p2[WRIST].y - p1[WRIST].y
            out[offset++] = p2[WRIST].z - p1[WRIST].z
            // Relative index delta
            out[offset++] = p2[INDEX_TIP].x - p1[INDEX_TIP].x
            out[offset++] = p2[INDEX_TIP].y - p1[INDEX_TIP].y
            out[offset++] = p2[INDEX_TIP].z - p1[INDEX_TIP].z
        } else {
            offset += 14
        }

        // 4. Hand presence flags (2D)
        out[162] = 1.0f // Hand 1 present
        out[163] = if (hand2 != null) 1.0f else 0.0f

        return out
    }

    private fun dist3D(a: FloatArray, b: FloatArray): Float {
        val dx = a[0] - b[0]
        val dy = a[1] - b[1]
        val dz = a[2] - b[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun dist3D(a: Point3D, b: Point3D): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun crossProduct(a: FloatArray, b: FloatArray): FloatArray {
        return floatArrayOf(
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        )
    }
}
