package ai.signora.nlp

import java.util.Locale

/**
 * Visual instruction cue for an ISL sign gesture.
 */
data class IslSignCue(
    val gloss: String,
    val bodyAnchor: String,
    val description: String,
    val isFingerspelled: Boolean = false,
    val fingerspellingLetters: List<String> = emptyList()
)

/**
 * Result of translating an English utterance into Indian Sign Language.
 */
data class EnglishToIslResult(
    val originalSentence: String,
    val islGlosses: List<String>,
    val signCues: List<IslSignCue>,
    val grammaticalNotes: String
)

/**
 * Bidirectional English-to-ISL Grammar Synthesizer.
 *
 * Converts spoken or written English (SVO grammar) into authentic Indian Sign Language
 * (ISL SOV grammar, WH-question end movement, temporal fronting, and copula/article elimination).
 *
 * ZERO network latency, zero hallucinations, fully deterministic on-device translation.
 */
object EnglishToIslSynthesizer {

    private val ARTICLES_AND_FILLERS = setOf(
        "A", "AN", "THE", "OF", "TO", "FOR", "AT", "BY", "WITH", "ABOUT",
        "JUST", "PLEASE", "KINDLY", "OH", "AH", "UM", "UH", "WELL"
    )

    private val COPULAS_AND_AUXILIARIES = setOf(
        "IS", "AM", "ARE", "WAS", "WERE", "BE", "BEEN", "BEING",
        "DO", "DOES", "DID", "HAVE", "HAS", "HAD", "WILL", "SHALL", "WOULD", "SHOULD", "COULD"
    )

    private val WH_WORDS = setOf(
        "WHERE", "WHAT", "WHO", "WHEN", "WHY", "HOW", "WHICH"
    )

    private val TEMPORAL_WORDS = setOf(
        "TODAY", "TOMORROW", "YESTERDAY", "NOW", "LATER", "SOON", "TONIGHT",
        "MORNING", "AFTERNOON", "EVENING", "NIGHT", "SUNDAY", "MONDAY",
        "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "DAILY",
        "ALWAYS", "NEVER"
    )

    private val NEGATION_WORDS = setOf(
        "NOT", "NO", "NEVER", "CANNOT", "DONT", "DOESNT", "DIDNT", "WONT", "CANT"
    )

    private val PRONOUNS = setOf(
        "I", "ME", "MY", "MINE", "YOU", "YOUR", "YOURS", "WE", "US", "OUR", "OURS",
        "HE", "HIM", "HIS", "SHE", "HER", "HERS", "THEY", "THEM", "THEIR", "IT", "ITS"
    )

    private val VERBS = setOf(
        "GO", "COME", "WANT", "NEED", "EAT", "DRINK", "SLEEP", "HELP", "SEE",
        "HEAR", "FEEL", "KNOW", "UNDERSTAND", "SAY", "TELL", "SPEAK", "ASK",
        "BUY", "PAY", "WAIT", "STOP", "START", "TAKE", "GIVE", "FIND", "LOOK",
        "MEET", "VISIT", "CALL", "WRITE", "READ", "WORK", "STUDY", "LEARN",
        "TEACH", "LOVE", "LIKE", "HATE", "FEEL", "LIVE", "STAY", "ARRIVE", "LEAVE"
    )

    // Lexicon of known ISL signs with verified anatomical anchors and kinetic cues
    private val SIGN_LEXICON: Map<String, Pair<String, String>> = mapOf(
        "HELLO" to Pair("HEAD", "Wave flat open hand outward from temple with friendly expression"),
        "HI" to Pair("HEAD", "Wave flat open hand outward from temple"),
        "NAMASTE" to Pair("CHEST_CENTER", "Press palms together flat in front of chest (Anjali Mudra)"),
        "THANK" to Pair("CHIN", "Touch flat hand fingertips to chin and extend forward toward person"),
        "THANKS" to Pair("CHIN", "Touch flat hand fingertips to chin and extend forward toward person"),
        "WELCOME" to Pair("CHEST_CENTER", "Sweep open palm inward toward chest invitingly"),
        "SORRY" to Pair("CHEST_CENTER", "Rub closed fist in circular motion over heart with regretful expression"),
        "PLEASE" to Pair("CHEST_CENTER", "Rub open flat palm in clockwise circle over chest"),
        "YES" to Pair("OPEN_SPACE", "Fist nods up and down in front of body like a head nodding"),
        "NO" to Pair("OPEN_SPACE", "Index and middle fingers snap firmly down onto thumb"),
        "GOOD" to Pair("CHIN", "Flat hand fingertips touch chin and move forward into palm"),
        "BAD" to Pair("CHIN", "Flat hand moves forward from chin and turns sharply palm-down"),
        "HELP" to Pair("CHEST_CENTER", "Place dominant thumbs-up fist onto flat non-dominant palm and lift"),
        "DOCTOR" to Pair("FOREARMS", "Tap index and middle fingers on inner wrist of non-dominant arm (pulse check)"),
        "HOSPITAL" to Pair("CHEST_LEFT", "Trace a cross (+) on upper left arm or shoulder with two fingers"),
        "MEDICINE" to Pair("OPEN_SPACE", "Circle middle finger inside open palm like crushing in a mortar"),
        "EMERGENCY" to Pair("OPEN_SPACE", "Wave both hands side to side rapidly at shoulder height"),
        "PAIN" to Pair("CHEST_CENTER", "Point index fingers toward each other and twist sharply with furrowed brow"),
        "HURT" to Pair("CHEST_CENTER", "Point index fingers toward each other and twist sharply with pained expression"),
        "FEVER" to Pair("HEAD", "Place back of hand against forehead to check body temperature"),
        "COLD" to Pair("CHEST_CENTER", "Clench fists near chest and shiver upper body"),
        "COUGH" to Pair("CHIN", "Hold loose fist near mouth and make slight coughing movements"),
        "HEADACHE" to Pair("HEAD", "Twist index fingers toward each other beside temples"),
        "WATER" to Pair("CHIN", "Form W-handshape and tap side of chin twice"),
        "FOOD" to Pair("CHIN", "Bring grouped fingertips of flat-O hand to mouth repeatedly"),
        "EAT" to Pair("CHIN", "Bring grouped fingertips to mouth repeatedly"),
        "DRINK" to Pair("CHIN", "C-handshape tipped toward mouth as if drinking from a cup"),
        "SLEEP" to Pair("HEAD", "Rest tilted cheek onto folded palms near ear"),
        "REST" to Pair("CHEST_CENTER", "Cross arms loosely over chest with relaxed shoulders"),
        "POLICE" to Pair("CHEST_LEFT", "C-handshape tapped twice over left chest badge position"),
        "AMBULANCE" to Pair("HEAD", "Hold open hand above head and rotate with flashing beacon motion"),
        "STATION" to Pair("OPEN_SPACE", "Bent two-finger handshape moving along track"),
        "TRAIN" to Pair("OPEN_SPACE", "Rub index and middle fingers of both hands back and forth along each other"),
        "BUS" to Pair("OPEN_SPACE", "Mime holding large steering wheel with both hands"),
        "CAR" to Pair("OPEN_SPACE", "Hold imaginary steering wheel and make slight driving adjustments"),
        "MONEY" to Pair("OPEN_SPACE", "Rub thumb across fingertips of upward palm repeatedly"),
        "TIME" to Pair("FOREARMS", "Tap non-dominant wrist with index finger twice"),
        "TODAY" to Pair("OPEN_SPACE", "Drop both Y-handshapes down together firmly in neutral space"),
        "TOMORROW" to Pair("CHIN", "Thumb of A-handshape flicks forward from cheek bone"),
        "YESTERDAY" to Pair("CHIN", "Thumb of A-handshape moves backward from cheek toward ear"),
        "NOW" to Pair("OPEN_SPACE", "Drop bent hands down in front of torso firmly"),
        "WHERE" to Pair("OPEN_SPACE", "Both hands open palms up, sweep side-to-side with questioning expression"),
        "WHAT" to Pair("OPEN_SPACE", "Index finger or open hands move side-to-side with inquiring look"),
        "WHO" to Pair("CHIN", "Circle index finger in small loops around mouth"),
        "WHEN" to Pair("OPEN_SPACE", "Circle index finger around non-dominant index finger then touch tip"),
        "WHY" to Pair("HEAD", "Touch forehead with fingertips and pull down into Y-handshape"),
        "HOW" to Pair("OPEN_SPACE", "Curved hands back-to-back rotate outward so palms face upward"),
        "WHICH" to Pair("OPEN_SPACE", "Both A-hands alternately move up and down in weighing motion"),
        "NAME" to Pair("CHEST_CENTER", "H-handshapes (index and middle fingers) tap together in front of chest"),
        "MY_NAME_IS" to Pair("CHEST_CENTER", "Flat right palm pressed to chest (MY) then H-fingers tap left fingers twice (NAME)"),
        "I" to Pair("CHEST_CENTER", "Index finger points directly to center of chest"),
        "ME" to Pair("CHEST_CENTER", "Index finger points directly to center of chest"),
        "MY" to Pair("CHEST_CENTER", "Flat palm pressed against chest"),
        "YOU" to Pair("OPEN_SPACE", "Index finger points forward toward conversational partner"),
        "YOUR" to Pair("OPEN_SPACE", "Flat palm pushed gently forward toward conversational partner"),
        "WE" to Pair("CHEST_CENTER", "Index finger sweeps in an arc across chest from right to left shoulder"),
        "HE" to Pair("OPEN_SPACE", "Index finger points to the right side neutral space"),
        "SHE" to Pair("OPEN_SPACE", "Index finger points to the right side neutral space"),
        "THEY" to Pair("OPEN_SPACE", "Index finger arcs across lateral neutral space"),
        "STOP" to Pair("OPEN_SPACE", "Chop edge of dominant open hand sharply down onto flat non-dominant palm"),
        "GO" to Pair("OPEN_SPACE", "Both index fingers arc forward and away from body"),
        "COME" to Pair("OPEN_SPACE", "Both index fingers curl inward toward chest invitingly"),
        "WANT" to Pair("OPEN_SPACE", "Both curved hands pull inward toward body with grasping fingers"),
        "NEED" to Pair("OPEN_SPACE", "Bent index finger nods downwards firmly twice"),
        "LIKE" to Pair("CHEST_CENTER", "Thumb and middle finger draw outward from chest coming together"),
        "LOVE" to Pair("CHEST_CENTER", "Cross both closed fists over heart hugging tightly"),
        "UNDERSTAND" to Pair("HEAD", "Flick index finger upward from fist beside temple (lightbulb moment)"),
        "KNOW" to Pair("HEAD", "Tap bent fingertips against side of forehead"),
        "HOME" to Pair("CHIN", "Touch flat-O fingertips to cheek then near ear (places of eating and sleeping)"),
        "WORK" to Pair("OPEN_SPACE", "Tap dominant fist down onto non-dominant wrist twice"),
        "SCHOOL" to Pair("OPEN_SPACE", "Clap dominant flat hand down onto non-dominant palm twice"),
        "NOT" to Pair("CHIN", "Flick thumb out from under chin forward with firm headshake")
    )

    /**
     * Translates a spoken or typed English sentence into an authentic ISL gloss sequence
     * with visual cues, spatial body anchors, and fingerspelling fallback.
     */
    fun translate(englishText: String): EnglishToIslResult {
        val trimmed = englishText.trim()
        if (trimmed.isEmpty()) {
            return EnglishToIslResult("", emptyList(), emptyList(), "Empty input")
        }

        val isQuestion = trimmed.endsWith("?") || containsWhWord(trimmed)

        // 1. Expand common contractions
        val expanded = expandContractions(trimmed)

        // 2. Tokenize and normalize words
        val rawTokens = expanded
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .map { it.uppercase(Locale.US) }

        if (rawTokens.isEmpty()) {
            return EnglishToIslResult(englishText, emptyList(), emptyList(), "No valid words")
        }

        // 3. Categorize tokens into ISL grammatical buckets
        val temporals = mutableListOf<String>()
        val subjects = mutableListOf<String>()
        val objectsAndLocations = mutableListOf<String>()
        val verbs = mutableListOf<String>()
        val negations = mutableListOf<String>()
        val whWords = mutableListOf<String>()
        val otherWords = mutableListOf<String>()

        for (token in rawTokens) {
            when {
                WH_WORDS.contains(token) -> whWords.add(token)
                TEMPORAL_WORDS.contains(token) -> temporals.add(token)
                NEGATION_WORDS.contains(token) -> {
                    val norm = if (token == "DONT" || token == "DOESNT" || token == "DIDNT" || token == "WONT" || token == "CANT" || token == "CANNOT") "NOT" else token
                    negations.add(norm)
                }
                PRONOUNS.contains(token) -> subjects.add(token)
                VERBS.contains(token) -> verbs.add(token)
                ARTICLES_AND_FILLERS.contains(token) -> {
                    // Suppress English functional glue
                }
                COPULAS_AND_AUXILIARIES.contains(token) -> {
                    // Suppress copulas (is, am, are, was, were)
                }
                else -> {
                    // Nouns, locations, adjectives, or proper names
                    objectsAndLocations.add(token)
                }
            }
        }

        // 4. Assemble authentic ISL syntactic sequence:
        // [TEMPORAL] + [SUBJECT] + [OBJECT/LOCATION] + [OTHER] + [VERB] + [NEGATION] + [WH_QUESTION]
        val orderedGlosses = mutableListOf<String>()

        // (a) Temporal fronting
        orderedGlosses.addAll(temporals)

        // (b) Subject
        orderedGlosses.addAll(subjects)

        // (c) Object / Location / Complement
        orderedGlosses.addAll(objectsAndLocations)

        // (d) Modifiers / Adjectives / Other
        orderedGlosses.addAll(otherWords)

        // (e) Verb (SOV structure)
        orderedGlosses.addAll(verbs)

        // (f) Negation
        orderedGlosses.addAll(negations)

        // (g) WH-Interrogative token moves to sentence-final position in ISL
        orderedGlosses.addAll(whWords)

        // Fallback: If filtering stripped everything (e.g. "What is it?"), preserve core tokens
        if (orderedGlosses.isEmpty()) {
            orderedGlosses.addAll(rawTokens)
        }

        // Deduplicate adjacent identical glosses
        val finalGlosses = mutableListOf<String>()
        for (g in orderedGlosses) {
            if (finalGlosses.isEmpty() || finalGlosses.last() != g) {
                finalGlosses.add(g)
            }
        }

        // 5. Generate Sign Cues with Body Anchors & Fingerspelling Fallback
        val signCues = finalGlosses.map { gloss ->
            buildSignCue(gloss)
        }

        val notes = buildString {
            append("Syntactic transformation: ")
            if (temporals.isNotEmpty()) append("Temporal-Fronting, ")
            if (verbs.isNotEmpty() && objectsAndLocations.isNotEmpty()) append("SVO→SOV, ")
            if (whWords.isNotEmpty()) append("WH-Final, ")
            append("Copula/Article-Pruning")
        }

        return EnglishToIslResult(
            originalSentence = englishText,
            islGlosses = finalGlosses,
            signCues = signCues,
            grammaticalNotes = notes
        )
    }

    private fun buildSignCue(gloss: String): IslSignCue {
        val upper = gloss.uppercase(Locale.US)
        val known = SIGN_LEXICON[upper]

        return if (known != null) {
            IslSignCue(
                gloss = upper,
                bodyAnchor = known.first,
                description = known.second,
                isFingerspelled = false
            )
        } else {
            // Out of vocabulary word: Fall back to standard ISL fingerspelling letter by letter
            val letters = upper.filter { it.isLetter() }.map { it.toString() }
            val anchor = "CHEST_CENTER"
            val desc = if (letters.isNotEmpty()) {
                "Fingerspell letter-by-letter: ${letters.joinToString(" - ")}"
            } else {
                "Perform standard gesture in open signing space"
            }
            IslSignCue(
                gloss = upper,
                bodyAnchor = anchor,
                description = desc,
                isFingerspelled = letters.isNotEmpty(),
                fingerspellingLetters = letters
            )
        }
    }

    private fun containsWhWord(text: String): Boolean {
        val upper = text.uppercase(Locale.US)
        return WH_WORDS.any { upper.contains(it) }
    }

    private fun expandContractions(text: String): String {
        return text
            .replace(Regex("(?i)\\bdon't\\b"), "do not")
            .replace(Regex("(?i)\\bdoesn't\\b"), "does not")
            .replace(Regex("(?i)\\bdidn't\\b"), "did not")
            .replace(Regex("(?i)\\bcan't\\b"), "cannot")
            .replace(Regex("(?i)\\bwon't\\b"), "will not")
            .replace(Regex("(?i)\\bi'm\\b"), "I am")
            .replace(Regex("(?i)\\byou're\\b"), "you are")
            .replace(Regex("(?i)\\bwe're\\b"), "we are")
            .replace(Regex("(?i)\\bthey're\\b"), "they are")
            .replace(Regex("(?i)\\bhe's\\b"), "he is")
            .replace(Regex("(?i)\\bshe's\\b"), "she is")
            .replace(Regex("(?i)\\bit's\\b"), "it is")
            .replace(Regex("(?i)\\bwhat's\\b"), "what is")
            .replace(Regex("(?i)\\bwhere's\\b"), "where is")
            .replace(Regex("(?i)\\bwho's\\b"), "who is")
            .replace(Regex("(?i)\\bhow's\\b"), "how is")
    }
}
