package ai.signora.ml

import ai.signora.models.HandLandmarks
import ai.signora.models.Point3D
import ai.signora.models.PoseLandmarks
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * High-definition anatomical body segmentation specification for ISL spatial tracking.
 *
 * Divides the upper body into 8 discrete spatial segments:
 * - HEAD (Crown, Forehead, Temple)
 * - CHIN (Chin, Mouth, Lower Face)
 * - THROAT (Neck, Suprasternal notch)
 * - CHEST_LEFT / HEART (Cardiac zone)
 * - CHEST_CENTER (Sternum)
 * - CHEST_RIGHT (Right pectoralis)
 * - MID_TORSO (Middle torso, Abdomen, Stomach)
 * - WAIST (Waist, Navel, Pelvis)
 * - FOREARMS (Left & Right Forearms / Wrists)
 */
data class BodySegmentZone(
    val id: String,
    val displayName: String,
    val shortTag: String,
    val center: Point3D,
    val width: Float,
    val height: Float,
    val aliases: List<String>
) {
    fun contains(pt: Point3D): Boolean {
        val dx = abs(pt.x - center.x) / max(width / 2f, 0.01f)
        val dy = abs(pt.y - center.y) / max(height / 2f, 0.01f)
        return dx <= 1.15f && dy <= 1.15f
    }

    fun normalizedDistance(pt: Point3D): Float {
        val dx = abs(pt.x - center.x) / max(width / 2f, 0.01f)
        val dy = abs(pt.y - center.y) / max(height / 2f, 0.01f)
        return sqrt(dx * dx + dy * dy)
    }

    fun matches(anchor: String?): Boolean {
        if (anchor == null) return false
        val clean = anchor.uppercase().trim()
        if (clean == id) return true
        for (alias in aliases) {
            if (clean == alias || clean.contains(alias) || alias.contains(clean)) return true
        }
        return false
    }
}

object BodySegmentation {

    fun computeSegments(pose: PoseLandmarks?): List<BodySegmentZone> {
        if (pose == null || pose.points.size < 25) return emptyList()

        val pts = pose.points
        val pNose = pts[0]
        val pMl = pts[9]
        val pMr = pts[10]
        val pLsh = pts[11]
        val pRsh = pts[12]
        val pLel = pts[13]
        val pRel = pts[14]
        val pLwrist = pts[15]
        val pRwrist = pts[16]
        val pLhip = pts[23]
        val pRhip = pts[24]

        val dxSh = pLsh.x - pRsh.x
        val dySh = pLsh.y - pRsh.y
        val shoulderDist = max(sqrt(dxSh * dxSh + dySh * dySh), 0.16f)
        val sScale = shoulderDist

        val mouthCenter = Point3D((pMl.x + pMr.x) / 2f, (pMl.y + pMr.y) / 2f, (pMl.z + pMr.z) / 2f)
        val chestCenter = Point3D((pLsh.x + pRsh.x) / 2f, (pLsh.y + pRsh.y) / 2f, (pLsh.z + pRsh.z) / 2f)
        val waistCenter = Point3D((pLhip.x + pRhip.x) / 2f, (pLhip.y + pRhip.y) / 2f, (pLhip.z + pRhip.z) / 2f)

        val chestY = chestCenter.y + 0.06f * sScale
        val midY = (chestCenter.y * 0.40f + waistCenter.y * 0.60f)
        val throatY = mouthCenter.y + (chestCenter.y - mouthCenter.y) * 0.50f

        val headCenter = Point3D(pNose.x, pNose.y - 0.28f * sScale, pNose.z)
        val chinCenter = Point3D(mouthCenter.x, mouthCenter.y + 0.08f * sScale, mouthCenter.z)
        val throatCenter = Point3D(mouthCenter.x, throatY, mouthCenter.z)

        val chestLeftCenter = Point3D(pLsh.x * 0.35f + chestCenter.x * 0.65f, chestY, chestCenter.z)
        val chestRightCenter = Point3D(pRsh.x * 0.35f + chestCenter.x * 0.65f, chestY, chestCenter.z)
        val midTorsoCenter = Point3D(chestCenter.x, midY, chestCenter.z)

        val lArmCenter = Point3D((pLel.x + pLwrist.x) / 2f, (pLel.y + pLwrist.y) / 2f, (pLel.z + pLwrist.z) / 2f)
        val rArmCenter = Point3D((pRel.x + pRwrist.x) / 2f, (pRel.y + pRwrist.y) / 2f, (pRel.z + pRwrist.z) / 2f)

        return listOf(
            BodySegmentZone(
                id = "HEAD",
                displayName = "Head & Crown",
                shortTag = "HEAD",
                center = headCenter,
                width = 0.66f * sScale,
                height = 0.36f * sScale,
                aliases = listOf("HEAD", "CROWN", "FOREHEAD", "TEMPLE", "EYES")
            ),
            BodySegmentZone(
                id = "CHIN",
                displayName = "Chin & Mouth",
                shortTag = "CHIN",
                center = chinCenter,
                width = 0.52f * sScale,
                height = 0.26f * sScale,
                aliases = listOf("CHIN", "MOUTH", "MOUTH_LIPS", "FACE", "CHIN_MOUTH")
            ),
            BodySegmentZone(
                id = "THROAT",
                displayName = "Throat & Neck",
                shortTag = "THROAT",
                center = throatCenter,
                width = 0.44f * sScale,
                height = 0.22f * sScale,
                aliases = listOf("THROAT", "NECK")
            ),
            BodySegmentZone(
                id = "CHEST_LEFT",
                displayName = "Left Chest (Heart)",
                shortTag = "CHEST (L)",
                center = chestLeftCenter,
                width = 0.34f * sScale,
                height = 0.38f * sScale,
                aliases = listOf("CHEST_LEFT", "HEART", "LEFT_CHEST")
            ),
            BodySegmentZone(
                id = "CHEST_CENTER",
                displayName = "Center Chest (Sternum)",
                shortTag = "CHEST",
                center = Point3D(chestCenter.x, chestY, chestCenter.z),
                width = 0.36f * sScale,
                height = 0.38f * sScale,
                aliases = listOf("CHEST", "CHEST_CENTER", "STERNUM")
            ),
            BodySegmentZone(
                id = "CHEST_RIGHT",
                displayName = "Right Chest",
                shortTag = "CHEST (R)",
                center = chestRightCenter,
                width = 0.34f * sScale,
                height = 0.38f * sScale,
                aliases = listOf("CHEST_RIGHT", "RIGHT_CHEST")
            ),
            BodySegmentZone(
                id = "MID_TORSO",
                displayName = "Middle Torso (Stomach)",
                shortTag = "MID TORSO",
                center = midTorsoCenter,
                width = 0.78f * sScale,
                height = 0.40f * sScale,
                aliases = listOf("MID_TORSO", "STOMACH", "ABDOMEN", "MIDTORSO", "MIDDLE_TORSO", "BELLY")
            ),
            BodySegmentZone(
                id = "WAIST",
                displayName = "Waist & Hips",
                shortTag = "WAIST",
                center = waistCenter,
                width = 0.82f * sScale,
                height = 0.32f * sScale,
                aliases = listOf("WAIST", "HIPS", "PELVIS")
            ),
            BodySegmentZone(
                id = "LEFT_ARM",
                displayName = "Left Forearm",
                shortTag = "L. ARM",
                center = lArmCenter,
                width = 0.32f * sScale,
                height = 0.40f * sScale,
                aliases = listOf("WRIST", "LEFT_WRIST", "FOREARM", "LEFT_FOREARM")
            ),
            BodySegmentZone(
                id = "RIGHT_ARM",
                displayName = "Right Forearm",
                shortTag = "R. ARM",
                center = rArmCenter,
                width = 0.32f * sScale,
                height = 0.40f * sScale,
                aliases = listOf("WRIST", "RIGHT_WRIST", "FOREARM", "RIGHT_FOREARM")
            )
        )
    }

    /**
     * Determines which body segment the active hands are currently engaging with.
     */
    fun detectActiveSegment(
        hands: List<HandLandmarks>,
        pose: PoseLandmarks?
    ): BodySegmentZone? {
        if (pose == null || hands.isEmpty()) return null
        val segments = computeSegments(pose)
        if (segments.isEmpty()) return null

        var bestSegment: BodySegmentZone? = null
        var minNormDist = 1.35f // Capture proximity threshold

        for (hand in hands) {
            if (hand.points.size < 21) continue
            val wrist = hand.points[0]
            val indexTip = hand.points[8]
            val thumbTip = hand.points[4]
            val palm = Point3D(
                (wrist.x + hand.points[9].x) / 2f,
                (wrist.y + hand.points[9].y) / 2f,
                (wrist.z + hand.points[9].z) / 2f
            )
            val candidates = listOf(indexTip, thumbTip, palm, wrist)

            for (seg in segments) {
                for (pt in candidates) {
                    val d = seg.normalizedDistance(pt)
                    if (d < minNormDist) {
                        minNormDist = d
                        bestSegment = seg
                    }
                }
            }
        }

        return bestSegment
    }

    fun matchTargetSegment(
        targetAnchor: String?,
        segments: List<BodySegmentZone>
    ): BodySegmentZone? {
        if (targetAnchor == null) return null
        return segments.firstOrNull { it.matches(targetAnchor) }
    }
}
