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

        // Canonical hand assignment: [leftSlot, rightSlot]
        var leftHand: HandLandmarks? = null
        var rightHand: HandLandmarks? = null

        if (hands.size == 1) {
            val h = hands[0]
            if (h.isRightHand) {
                rightHand = h
            } else {
                leftHand = h
            }
        } else {
            val h1 = hands[0]
            val h2 = hands[1]
            if (h1.isRightHand != h2.isRightHand) {
                if (h1.isRightHand) {
                    rightHand = h1
                    leftHand = h2
                } else {
                    leftHand = h1
                    rightHand = h2
                }
            } else {
                // Same handedness tag: sort by screen X position (wrist point)
                val x1 = h1.points.getOrNull(WRIST)?.x ?: 0f
                val x2 = h2.points.getOrNull(WRIST)?.x ?: 0f
                if (x1 < x2) {
                    leftHand = h1
                    rightHand = h2
                } else {
                    leftHand = h2
                    rightHand = h1
                }
            }
        }

        // 1. Left hand features in slot 0..73
        if (leftHand != null && leftHand.points.size >= 21) {
            val fLeft = extractSingleHandFeatures(leftHand.points, normalizeNormal = true)
            System.arraycopy(fLeft, 0, out, 0, 74)
            out[162] = 1.0f
        }

        // 2. Right hand features in slot 74..147
        if (rightHand != null && rightHand.points.size >= 21) {
            val fRight = extractSingleHandFeatures(rightHand.points, normalizeNormal = true)
            System.arraycopy(fRight, 0, out, 74, 74)
            out[163] = 1.0f
        }

        // 3. Inter-hand spatial geometry (14D) at offset 148..161
        if (leftHand != null && rightHand != null && leftHand.points.size >= 21 && rightHand.points.size >= 21) {
            var offset = 148
            val pL = leftHand.points
            val pR = rightHand.points
            // Wrist distance
            out[offset++] = dist3D(pL[WRIST], pR[WRIST])
            // Fingertip cross-distances
            for (tip in FINGERTIPS) {
                out[offset++] = dist3D(pL[tip], pR[tip])
            }
            // Index to thumb cross-distances
            out[offset++] = dist3D(pL[INDEX_TIP], pR[THUMB_TIP])
            out[offset++] = dist3D(pL[THUMB_TIP], pR[INDEX_TIP])
            // Relative wrist delta (R - L)
            out[offset++] = pR[WRIST].x - pL[WRIST].x
            out[offset++] = pR[WRIST].y - pL[WRIST].y
            out[offset++] = pR[WRIST].z - pL[WRIST].z
            // Relative index delta (R - L)
            out[offset++] = pR[INDEX_TIP].x - pL[INDEX_TIP].x
            out[offset++] = pR[INDEX_TIP].y - pL[INDEX_TIP].y
            out[offset++] = pR[INDEX_TIP].z - pL[INDEX_TIP].z
        }

        return out
    }

    private fun extractSingleHandFeatures(rawPoints: List<Point3D>, normalizeNormal: Boolean): FloatArray {
        if (rawPoints.size < 21) return FloatArray(74)

        val pts = Array(21) { i ->
            val p = rawPoints[i]
            floatArrayOf(p.x, p.y, p.z)
        }

        // Translate wrist to origin
        val wX = pts[WRIST][0]
        val wY = pts[WRIST][1]
        val wZ = pts[WRIST][2]
        for (i in 0 until 21) {
            pts[i][0] -= wX
            pts[i][1] -= wY
            pts[i][2] -= wZ
        }

        // Scale by palm size: dist(wrist, middle_mcp)
        val palmDist = dist3D(pts[WRIST], pts[MIDDLE_MCP])
        val scale = if (palmDist < 1e-6f) 1.0f else palmDist
        for (i in 0 until 21) {
            pts[i][0] /= scale
            pts[i][1] /= scale
            pts[i][2] /= scale
        }

        val palm = dist3D(pts[WRIST], pts[MIDDLE_MCP]).let { if (it < 1e-6f) 1f else it }

        val feats = FloatArray(74)
        var idx = 0

        // 63 normalized coordinates
        for (i in 0 until 21) {
            feats[idx++] = pts[i][0]
            feats[idx++] = pts[i][1]
            feats[idx++] = pts[i][2]
        }

        // 5 digit extension ratios
        for ((mcp, _, tip) in DIGIT_CHAINS) {
            feats[idx++] = dist3D(pts[tip], pts[mcp]) / palm
        }

        // 1 pinch ratio
        feats[idx++] = dist3D(pts[THUMB_TIP], pts[INDEX_TIP]) / palm

        // 1 spread ratio
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

        // 3 palm normal vector
        val v1 = floatArrayOf(pts[INDEX_MCP][0] - pts[WRIST][0], pts[INDEX_MCP][1] - pts[WRIST][1], pts[INDEX_MCP][2] - pts[WRIST][2])
        val v2 = floatArrayOf(pts[PINKY_MCP][0] - pts[WRIST][0], pts[PINKY_MCP][1] - pts[WRIST][1], pts[PINKY_MCP][2] - pts[WRIST][2])
        val normal = crossProduct(v1, v2)
        if (normalizeNormal) {
            val normLen = sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2])
            val s = if (normLen > 1e-6f) normLen else 1f
            feats[idx++] = normal[0] / s
            feats[idx++] = normal[1] / s
            feats[idx++] = normal[2] / s
        } else {
            feats[idx++] = normal[0]
            feats[idx++] = normal[1]
            feats[idx++] = normal[2]
        }

        return feats
    }

    fun extractSpatialFeatures(hands: List<HandLandmarks>, pose: ai.signora.models.PoseLandmarks?): FloatArray {
        val baseFeats = extractIslFeatures(hands)
        val out = FloatArray(220)
        System.arraycopy(baseFeats, 0, out, 0, 164)

        if (pose == null || pose.points.size < 25) {
            return out
        }

        val p = pose.points
        val pNose = p[0]
        val pMl = p[9]
        val pMr = p[10]
        val pLsh = p[11]
        val pRsh = p[12]
        val pLwrist = p[15]
        val pLhip = p[23]
        val pRhip = p[24]

        val shoulderDist = dist3D(pLsh, pRsh)
        val scale = if (shoulderDist > 0.15f) shoulderDist else 0.15f

        val mouthCenter = Point3D((pMl.x + pMr.x) / 2f, (pMl.y + pMr.y) / 2f, (pMl.z + pMr.z) / 2f)
        val forehead = Point3D(pNose.x, pNose.y - 0.22f * scale, pNose.z)
        val chin = Point3D(mouthCenter.x, mouthCenter.y + 0.12f * scale, mouthCenter.z)
        val chestCenter = Point3D((pLsh.x + pRsh.x) / 2f, (pLsh.y + pRsh.y) / 2f, (pLsh.z + pRsh.z) / 2f)
        val waistCenter = Point3D((pLhip.x + pRhip.x) / 2f, (pLhip.y + pRhip.y) / 2f, (pLhip.z + pRhip.z) / 2f)

        val anchors = listOf(
            forehead,
            pNose,
            mouthCenter,
            chin,
            chestCenter,
            pLsh,
            pRsh,
            pLwrist,
            waistCenter
        )

        var rightHand: HandLandmarks? = null
        var leftHand: HandLandmarks? = null
        for (h in hands) {
            if (h.isRightHand && rightHand == null) rightHand = h
            else if (!h.isRightHand && leftHand == null) leftHand = h
        }

        var offset = 164
        val handPair = listOf(rightHand, leftHand)

        for (h in handPair) {
            if (h != null && h.points.size >= 21) {
                val wrist = h.points[WRIST]
                val idxTip = h.points[INDEX_TIP]

                for (anc in anchors) {
                    val dx = (wrist.x - anc.x) / scale
                    val dy = (wrist.y - anc.y) / scale
                    val dz = (wrist.z - anc.z) / scale
                    val dist = sqrt(dx * dx + dy * dy + dz * dz)
                    out[offset++] = dx
                    out[offset++] = dy
                    out[offset++] = dist
                }

                val dfMx = (idxTip.x - mouthCenter.x) / scale
                val dfMy = (idxTip.y - mouthCenter.y) / scale
                val dfMouth = sqrt(dfMx * dfMx + dfMy * dfMy)
                out[offset++] = dfMouth
            } else {
                offset += 28
            }
        }

        return out
    }

    fun detectBodyAnchor(hand: HandLandmarks, pose: ai.signora.models.PoseLandmarks?): Pair<String, Point3D?> {
        if (pose == null || pose.points.size < 25 || hand.points.size < 21) {
            return Pair("OPEN_SPACE", null)
        }

        val p = pose.points
        val pNose = p[0]
        val pMl = p[9]
        val pMr = p[10]
        val pLsh = p[11]
        val pRsh = p[12]
        val pLwrist = p[15]
        val pRwrist = p[16]
        val pLhip = p[23]
        val pRhip = p[24]

        val shoulderDist = dist3D(pLsh, pRsh)
        val scale = if (shoulderDist > 0.15f) shoulderDist else 0.15f

        val mouthCenter = Point3D((pMl.x + pMr.x) / 2f, (pMl.y + pMr.y) / 2f, (pMl.z + pMr.z) / 2f)
        val forehead = Point3D(pNose.x, pNose.y - 0.22f * scale, pNose.z)
        val chin = Point3D(mouthCenter.x, mouthCenter.y + 0.12f * scale, mouthCenter.z)
        val chestCenter = Point3D((pLsh.x + pRsh.x) / 2f, (pLsh.y + pRsh.y) / 2f, (pLsh.z + pRsh.z) / 2f)
        val waistCenter = Point3D((pLhip.x + pRhip.x) / 2f, (pLhip.y + pRhip.y) / 2f, (pLhip.z + pRhip.z) / 2f)
        val temple = Point3D(if (hand.isRightHand) pRsh.x * 0.4f + pNose.x * 0.6f else pLsh.x * 0.4f + pNose.x * 0.6f, forehead.y, pNose.z)

        // Contact candidates from hand
        val wrist = hand.points[WRIST]
        val indexTip = hand.points[INDEX_TIP]
        val thumbTip = hand.points[THUMB_TIP]
        val palm = Point3D((wrist.x + hand.points[MIDDLE_MCP].x) / 2f, (wrist.y + hand.points[MIDDLE_MCP].y) / 2f, (wrist.y + hand.points[MIDDLE_MCP].y) / 2f)
        val contactPts = listOf(wrist, indexTip, thumbTip, palm)

        val anchorCandidates = listOf(
            Triple("FOREHEAD", forehead, 0.58f * scale),
            Triple("CHIN", chin, 0.52f * scale),
            Triple("MOUTH_LIPS", mouthCenter, 0.50f * scale),
            Triple("TEMPLE", temple, 0.55f * scale),
            Triple("CHEST", chestCenter, 0.68f * scale),
            Triple("WRIST", if (hand.isRightHand) pLwrist else pRwrist, 0.58f * scale),
            Triple("SHOULDER", pRsh, 0.62f * scale),
            Triple("SHOULDER", pLsh, 0.62f * scale),
            Triple("WAIST", waistCenter, 0.65f * scale)
        )

        var bestName = "OPEN_SPACE"
        var bestDist = Float.MAX_VALUE
        var bestPt: Point3D? = null

        for ((name, pt, radius) in anchorCandidates) {
            for (cPt in contactPts) {
                val d = dist3D(cPt, pt)
                if (d < radius && d < bestDist) {
                    bestDist = d
                    bestName = name
                    bestPt = pt
                }
            }
        }

        return Pair(bestName, bestPt)
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
