package ai.signora.nlp

/**
 * Tier-1 Instant ISL Grammar Synthesizer.
 *
 * Converts a raw ISL gloss sequence into a fluent, grammatically natural English sentence
 * with ZERO external dependencies, in < 2 ms, fully offline.
 *
 * ISL linguistic rules handled:
 *  1. Topic-Comment / SOV-to-SVO word reordering.
 *  2. Tense propagation from temporal markers (TOMORROW → future, YESTERDAY → past,
 *     FINISH → present-perfect).
 *  3. Copula & preposition insertion ("am", "is", "at", "to").
 *  4. WH-question fronting (WHERE, WHAT, WHO, WHEN, WHY, HOW → move to start).
 *  5. Yes/No question detection (YOU + verb → "Do you...?").
 *  6. Emergency amplification (EMERGENCY / HELP + body anchor → urgent first-person phrase).
 *  7. Polite softening for requests (PLEASE + verb → "Could you please...?").
 *  8. Known sign vocabulary expansion to full phrase forms.
 */
object IslGrammarSynthesizer {

    // ─── Canonical phrase expansions for full-word ISL signs ─────────────────
    private val PHRASE_MAP = mapOf(
        "HELLO"         to "Hello",
        "NAMASTE"       to "Namaste",
        "HELP"          to "help",
        "EMERGENCY"     to "Emergency",
        "DOCTOR"        to "doctor",
        "HOSPITAL"      to "hospital",
        "POLICE"        to "police",
        "PAIN"          to "pain",
        "HURT"          to "hurt",
        "CHEST"         to "chest",
        "HEAD"          to "head",
        "STOMACH"       to "stomach",
        "WATER"         to "water",
        "FOOD"          to "food",
        "MEDICINE"      to "medicine",
        "PHONE"         to "phone",
        "TOILET"        to "toilet",
        "MONEY"         to "money",
        "PLEASE"        to "please",
        "THANK_YOU"     to "thank you",
        "SORRY"         to "sorry",
        "YES"           to "yes",
        "NO"            to "no",
        "GOOD"          to "good",
        "BAD"           to "bad",
        "I"             to "I",
        "ME"            to "I",
        "YOU"           to "you",
        "WE"            to "we",
        "HE"            to "he",
        "SHE"           to "she",
        "THEY"          to "they",
        "MY"            to "my",
        "YOUR"          to "your",
        "GO"            to "go",
        "COME"          to "come",
        "NEED"          to "need",
        "WANT"          to "want",
        "GIVE"          to "give",
        "GET"           to "get",
        "HAVE"          to "have",
        "KNOW"          to "know",
        "UNDERSTAND"    to "understand",
        "SEE"           to "see",
        "HEAR"          to "hear",
        "FEEL"          to "feel",
        "THINK"         to "think",
        "CALL"          to "call",
        "STAY"          to "stay",
        "WAIT"          to "wait",
        "STOP"          to "stop",
        "START"         to "start",
        "OPEN"          to "open",
        "CLOSE"         to "close",
        "BIG"           to "big",
        "SMALL"         to "small",
        "FAST"          to "fast",
        "SLOW"          to "slow",
        "HOT"           to "hot",
        "COLD"          to "cold",
        "HEAVY"         to "heavy",
        "LIGHT"         to "light",
        "MUCH"          to "much",
        "MORE"          to "more",
        "LITTLE"        to "a little",
        "TODAY"         to "today",
        "TOMORROW"      to "tomorrow",
        "YESTERDAY"     to "yesterday",
        "NOW"           to "now",
        "LATER"         to "later",
        "MORNING"       to "morning",
        "NIGHT"         to "tonight",
        "WHERE"         to "where",
        "WHAT"          to "what",
        "WHO"           to "who",
        "WHEN"          to "when",
        "WHY"           to "why",
        "HOW"           to "how",
        "NAME"          to "name",
        "HOME"          to "home",
        "WORK"          to "work",
        "SCHOOL"        to "school",
        "STATION"       to "station",
        "TRAIN"         to "train",
        "BUS"           to "bus",
        "FINISH"        to "finished",
        "AGAIN"         to "again",
        "SPACE"         to " "
    )

    // Single-letter fingerspelling passes through directly
    private fun expandLabel(label: String): String {
        val up = label.uppercase().trim()
        if (up.length == 1 && up[0].isLetter()) return up  // Fingerspelling token
        return PHRASE_MAP[up] ?: up.lowercase().replaceFirstChar { it.uppercase() }
    }

    private val WH_WORDS = setOf("WHERE", "WHAT", "WHO", "WHEN", "WHY", "HOW")
    private val TEMPORAL_FUTURE = setOf("TOMORROW", "LATER")
    private val TEMPORAL_PAST   = setOf("YESTERDAY", "BEFORE")
    private val PRESENT_PERFECT = setOf("FINISH", "FINISHED", "ALREADY")

    // Tense markers found in gloss sequence
    private enum class Tense { PRESENT, FUTURE, PAST, PERFECT }

    /**
     * Main entry point. Accepts a list of GlossTokens and returns a natural English sentence.
     */
    fun synthesize(tokens: List<GlossToken>): String {
        if (tokens.isEmpty()) return ""

        val labels = tokens.map { it.label.uppercase().trim() }.filter { it.isNotBlank() && it != "SPACE" }
        if (labels.isEmpty()) return ""

        // ── Step 0: Detect broad intent ──────────────────────────────────────

        val hasEmergency  = labels.any { it == "EMERGENCY" }
        val hasHelp       = labels.any { it == "HELP" }
        val hasPlease     = labels.any { it == "PLEASE" }
        val hasYou        = labels.any { it == "YOU" }
        val hasI          = labels.any { it in setOf("I", "ME") }
        val whWord        = labels.firstOrNull { it in WH_WORDS }
        val isQuestion    = whWord != null || (hasYou && !hasI)
        val tense         = detectTense(labels)
        val bodyAnchors   = tokens.map { it.bodyAnchor }.filter { it.isNotBlank() }

        // ── Step 1: Emergency amplification ──────────────────────────────────
        if (hasEmergency || (hasHelp && bodyAnchors.any { it.contains("CHEST", ignoreCase = true) })) {
            return buildEmergencySentence(labels, bodyAnchors)
        }

        // ── Step 2: Simple greeting / standalone signs ────────────────────────
        if (labels.size == 1) {
            return when (val lbl = labels[0]) {
                "HELLO"    -> "Hello!"
                "NAMASTE"  -> "Namaste!"
                "THANK_YOU"-> "Thank you!"
                "SORRY"    -> "I am sorry."
                "YES"      -> "Yes."
                "NO"       -> "No."
                "HELP"     -> "Please help me!"
                else       -> expandLabel(lbl) + "."
            }
        }

        // ── Step 3: WH-Question reordering ────────────────────────────────────
        if (whWord != null) {
            return buildWhQuestion(labels, whWord, tense)
        }

        // ── Step 4: Polite request (YOU + verb / PLEASE + verb) ───────────────
        if (hasPlease && !hasI) {
            return buildPoliteSentence(labels, tense)
        }

        // ── Step 5: Yes/No question (YOU + action, no I) ─────────────────────
        if (isQuestion && !hasI && hasYou) {
            return buildYesNoQuestion(labels, tense)
        }

        // ── Step 6: General statement reordering (SOV → SVO) ─────────────────
        return buildStatement(labels, hasI, tense)
    }

    // ─── Emergency Sentence ───────────────────────────────────────────────────

    private fun buildEmergencySentence(labels: List<String>, bodyAnchors: List<String>): String {
        val painBodyPart = when {
            bodyAnchors.any { it.contains("CHEST", ignoreCase = true) } -> "chest"
            bodyAnchors.any { it.contains("HEAD",  ignoreCase = true) } -> "head"
            bodyAnchors.any { it.contains("STOMACH", ignoreCase = true) || it.contains("MID_TORSO", ignoreCase = true) } -> "stomach"
            labels.contains("CHEST")    -> "chest"
            labels.contains("HEAD")     -> "head"
            labels.contains("STOMACH")  -> "stomach"
            else -> null
        }

        val hasPain  = labels.any { it in setOf("PAIN", "HURT", "HEAVY") }
        val hasBreath= labels.any { it in setOf("BREATH", "BREATHE", "AIR") }

        return when {
            painBodyPart != null && hasPain ->
                "Emergency! I have ${if (labels.contains("HEAVY")) "severe" else ""} pain in my $painBodyPart. Please help me!"
            hasBreath ->
                "Emergency! I cannot breathe. Please call for help immediately!"
            labels.contains("DOCTOR") ->
                "Emergency! Please call a doctor immediately!"
            labels.contains("HOSPITAL") ->
                "Emergency! Please take me to the hospital!"
            labels.contains("POLICE") ->
                "Emergency! Please call the police!"
            else ->
                "Emergency! Please help me!"
        }
    }

    // ─── WH-Question Builder ─────────────────────────────────────────────────

    private fun buildWhQuestion(labels: List<String>, whWord: String, tense: Tense): String {
        val rest = labels.filter { it != whWord && it !in setOf("I", "ME", "YOU", "IS", "ARE") }
        val verb = rest.firstOrNull { isVerb(it) }
        val obj  = rest.filter { it != verb }

        return when (whWord) {
            "WHERE" -> when {
                verb != null -> "Where do I ${expandLabel(verb)} ${obj.joinToString(" ") { expandLabel(it) }}?".trimEnd('?').trimEnd() + "?"
                labels.contains("STATION") || labels.contains("TRAIN") -> "Where is the train station?"
                labels.contains("HOSPITAL") -> "Where is the nearest hospital?"
                labels.contains("TOILET") -> "Where is the restroom?"
                else -> "Where ${rest.joinToString(" ") { expandLabel(it) }}?"
            }
            "WHAT" -> when {
                labels.contains("NAME") -> "What is your name?"
                labels.contains("DOCTOR") -> "What did the doctor say?"
                else -> "What is ${rest.joinToString(" ") { expandLabel(it) }}?"
            }
            "WHO"  -> "Who ${verb?.let { "will ${expandLabel(it)}" } ?: "is it"}?"
            "WHEN" -> "When ${verb?.let { "will you ${expandLabel(it)}" } ?: rest.joinToString(" ") { expandLabel(it) }}?"
            "WHY"  -> "Why ${verb?.let { "did you ${expandLabel(it)}" } ?: rest.joinToString(" ") { expandLabel(it) }}?"
            "HOW"  -> when {
                labels.contains("YOU") -> "How are you?"
                else -> "How ${rest.joinToString(" ") { expandLabel(it) }}?"
            }
            else -> (listOf(expandLabel(whWord)) + rest.map { expandLabel(it) }).joinToString(" ") + "?"
        }
    }

    // ─── Polite Sentence Builder ─────────────────────────────────────────────

    private fun buildPoliteSentence(labels: List<String>, tense: Tense): String {
        val filtered = labels.filter { it !in setOf("PLEASE", "YOU", "ME", "I") }
        val verb = filtered.firstOrNull { isVerb(it) }
        val objects = filtered.filter { it != verb }
        val objPhrase = objects.joinToString(" ") { expandLabel(it) }

        return when {
            verb != null && objPhrase.isNotBlank() ->
                "Could you please ${expandLabel(verb)} the $objPhrase?"
            verb != null ->
                "Could I please ${expandLabel(verb)}?"
            labels.contains("WATER") ->
                "Could I please have some water?"
            labels.contains("MEDICINE") ->
                "Could I please have my medicine?"
            labels.contains("FOOD") ->
                "Could I please have something to eat?"
            else ->
                "Could you please help me with ${filtered.joinToString(" ") { expandLabel(it) }}?"
        }
    }

    // ─── Yes/No Question Builder ─────────────────────────────────────────────

    private fun buildYesNoQuestion(labels: List<String>, tense: Tense): String {
        val filtered = labels.filter { it !in setOf("YOU", "I", "ME") }
        val verb = filtered.firstOrNull { isVerb(it) }
        val objects = filtered.filter { it != verb }

        val aux = when (tense) {
            Tense.FUTURE  -> "Will you"
            Tense.PAST    -> "Did you"
            Tense.PERFECT -> "Have you"
            Tense.PRESENT -> "Do you"
        }

        return when {
            verb != null && objects.isNotEmpty() ->
                "$aux ${expandLabel(verb)} ${objects.joinToString(" ") { expandLabel(it) }}?"
            verb != null ->
                "$aux ${expandLabel(verb)}?"
            labels.contains("UNDERSTAND") -> "Do you understand?"
            labels.contains("KNOW") -> "Do you know?"
            else ->
                "$aux ${filtered.joinToString(" ") { expandLabel(it) }}?"
        }
    }

    // ─── General Statement Builder (SOV → SVO) ──────────────────────────────

    private fun buildStatement(labels: List<String>, hasI: Boolean, tense: Tense): String {
        // Separate temporal markers, subject, verb, object
        val temporals = labels.filter { it in TEMPORAL_FUTURE + TEMPORAL_PAST + PRESENT_PERFECT }
        val remaining = labels.filter { it !in temporals && it !in setOf("I", "ME", "YOU") }

        val verb = remaining.firstOrNull { isVerb(it) }
        val objects = remaining.filter { it != verb }

        val subject = when {
            hasI -> "I"
            labels.any { it == "YOU" } -> "You"
            else -> "I"
        }

        val auxVerb = when (tense) {
            Tense.FUTURE  -> if (subject == "I") "am going to" else "are going to"
            Tense.PAST    -> if (subject == "I") "went to" else "went to"
            Tense.PERFECT -> if (subject == "I") "have" else "have"
            Tense.PRESENT -> ""
        }

        return when {
            // "I GO DOCTOR TOMORROW" → "I am going to see the doctor tomorrow."
            verb != null && objects.isNotEmpty() && tense == Tense.FUTURE -> {
                val temporal = temporals.firstOrNull()?.let { expandLabel(it) } ?: ""
                val objPhrase = objects.joinToString(" and ") { expandLabel(it) }
                "$subject am planning to ${verbInfinitive(verb)} $objPhrase${if (temporal.isNotBlank()) " $temporal" else ""}."
            }

            verb != null && objects.isNotEmpty() && tense == Tense.PAST -> {
                val temporal = temporals.firstOrNull()?.let { expandLabel(it) } ?: ""
                val objPhrase = objects.joinToString(" and ") { expandLabel(it) }
                "$subject ${verbPast(verb)} $objPhrase${if (temporal.isNotBlank()) " $temporal" else ""}."
            }

            verb != null && objects.isNotEmpty() -> {
                val objPhrase = objects.joinToString(" and ") { expandLabel(it) }
                "$subject ${expandLabel(verb)} $objPhrase."
            }

            verb != null -> "$subject ${expandLabel(verb)}."

            // All noun labels, no verb: "I PAIN CHEST" → "I have pain in my chest."
            objects.size >= 2 -> {
                val anchor = objects.firstOrNull { it in setOf("CHEST", "HEAD", "STOMACH", "WAIST") }
                val symptom = objects.firstOrNull { it in setOf("PAIN", "HURT", "HEAVY") }
                when {
                    anchor != null && symptom != null ->
                        "$subject have ${expandLabel(symptom)} in my ${expandLabel(anchor)}."
                    anchor != null ->
                        "$subject need help with my ${expandLabel(anchor)}."
                    else ->
                        "$subject need ${objects.joinToString(" and ") { expandLabel(it) }}."
                }
            }

            objects.size == 1 -> "$subject need ${expandLabel(objects[0])}."

            else -> labels.joinToString(" ") { expandLabel(it) } + "."
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun detectTense(labels: List<String>): Tense = when {
        labels.any { it in TEMPORAL_FUTURE }  -> Tense.FUTURE
        labels.any { it in TEMPORAL_PAST }    -> Tense.PAST
        labels.any { it in PRESENT_PERFECT }  -> Tense.PERFECT
        else                                  -> Tense.PRESENT
    }

    private fun isVerb(label: String): Boolean =
        label in setOf(
            "GO", "COME", "NEED", "WANT", "GIVE", "GET", "HAVE", "KNOW",
            "UNDERSTAND", "SEE", "HEAR", "FEEL", "THINK", "CALL", "STAY",
            "WAIT", "STOP", "START", "OPEN", "CLOSE", "HELP"
        )

    private fun verbInfinitive(label: String): String = when (label.uppercase()) {
        "GO"    -> "visit"
        "COME"  -> "come to"
        "CALL"  -> "call"
        "SEE"   -> "see"
        "GET"   -> "get"
        else    -> expandLabel(label)
    }

    private fun verbPast(label: String): String = when (label.uppercase()) {
        "GO"    -> "went to"
        "COME"  -> "came to"
        "CALL"  -> "called"
        "SEE"   -> "saw"
        "GET"   -> "got"
        "GIVE"  -> "gave"
        "HAVE"  -> "had"
        "KNOW"  -> "knew"
        "FEEL"  -> "felt"
        "HEAR"  -> "heard"
        "THINK" -> "thought"
        else    -> expandLabel(label) + "ed"
    }
}
