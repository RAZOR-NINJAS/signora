"""Comprehensive Sign Language Reference Dictionary for Signora.

Contains structured visual descriptions, hand configurations, movement trajectories,
and accuracy tips for ASL fingerspelling, ISL dual-handed signs, and ASL dynamic words.
"""

from typing import Any, Dict, List

# ASL Fingerspelling (Alphabet A-Z and SPACE)
ASL_GESTURES: Dict[str, Dict[str, str]] = {
    "A": {
        "hands": "1 Hand",
        "posture": "Closed fist; thumb rests upright against side of index finger; palm faces forward.",
        "tip": "Keep palm forward; do not wrap thumb over knuckles.",
    },
    "B": {
        "hands": "1 Hand",
        "posture": "Four fingers held straight up and pressed together; thumb folded flat across palm.",
        "tip": "Keep all 4 fingers vertical with no gaps between them.",
    },
    "C": {
        "hands": "1 Hand",
        "posture": "Fingers and thumb curved to form an open 'C' shape; palm angled slightly inwards.",
        "tip": "Turn hand slightly profile to display the curved 'C' contour.",
    },
    "D": {
        "hands": "1 Hand",
        "posture": "Index finger points straight up; thumb touches tips of middle, ring, pinky in a circle.",
        "tip": "Form a clear circular loop with the 3 fingers while keeping index vertical.",
    },
    "E": {
        "hands": "1 Hand",
        "posture": "All four fingers curled tightly down onto upper palm; thumb folded across below tips.",
        "tip": "Keep fingertips resting lightly against thumb or palm.",
    },
    "F": {
        "hands": "1 Hand",
        "posture": "Index fingertip and thumb tip touch forming an 'O' ring; other 3 fingers fanned open upright.",
        "tip": "Keep middle, ring, and pinky spread wide and upright.",
    },
    "G": {
        "hands": "1 Hand",
        "posture": "Index finger and thumb parallel pointing horizontally sideways; other 3 fingers curled into fist.",
        "tip": "Turn hand profile so index finger points across the frame.",
    },
    "H": {
        "hands": "1 Hand",
        "posture": "Index and middle fingers extended parallel horizontally sideways; others curled into fist.",
        "tip": "Keep index and middle tightly joined pointing sideways.",
    },
    "I": {
        "hands": "1 Hand",
        "posture": "Pinky finger extended straight up; other 3 fingers curled into fist with thumb across.",
        "tip": "Extend pinky fully upright toward top of camera view.",
    },
    "J": {
        "hands": "1 Hand",
        "posture": "Pinky extended upright, swooping downward and curving up like tracing a 'J'.",
        "tip": "Start with 'I' handshape and trace a hook trajectory.",
    },
    "K": {
        "hands": "1 Hand",
        "posture": "Index finger upright, middle finger angled forward, thumb tucked into crook of middle finger.",
        "tip": "Keep index finger vertical and middle finger pointing 45 degrees forward.",
    },
    "L": {
        "hands": "1 Hand",
        "posture": "Index finger points straight up; thumb extended at 90-degree right angle forming an 'L'.",
        "tip": "Keep both thumb and index finger rigidly straight.",
    },
    "M": {
        "hands": "1 Hand",
        "posture": "Three fingers (index, middle, ring) folded over thumb; thumb peeks between ring and pinky.",
        "tip": "Ensure thumb is visible underneath all three folded fingers.",
    },
    "N": {
        "hands": "1 Hand",
        "posture": "Two fingers (index, middle) folded over thumb; thumb peeks between middle and ring fingers.",
        "tip": "Rest index and middle over thumb; pinky and ring stay curled.",
    },
    "O": {
        "hands": "1 Hand",
        "posture": "All five fingertips curved and touching thumb tip to form a round 'O'.",
        "tip": "Show circular aperture clearly to camera.",
    },
    "P": {
        "hands": "1 Hand",
        "posture": "Downward-angled 'K' handshape: index pointing forward, middle pointing down, thumb between.",
        "tip": "Angle hand downward from wrist.",
    },
    "Q": {
        "hands": "1 Hand",
        "posture": "Downward-angled 'G' handshape: index finger and thumb pointing straight down.",
        "tip": "Point index and thumb downward toward lap.",
    },
    "R": {
        "hands": "1 Hand",
        "posture": "Index and middle fingers crossed over each other; other fingers clenched into fist.",
        "tip": "Middle finger wraps in front of index finger.",
    },
    "S": {
        "hands": "1 Hand",
        "posture": "Clenched tight fist with thumb wrapped across front of fingers.",
        "tip": "Thumb is in front of fingers (distinct from 'A' where thumb is at the side).",
    },
    "T": {
        "hands": "1 Hand",
        "posture": "Fist with thumb tucked between index and middle fingers, peeking upward.",
        "tip": "Thumb tip pokes up between index and middle knuckles.",
    },
    "U": {
        "hands": "1 Hand",
        "posture": "Index and middle fingers extended straight up together; ring and pinky curled into fist.",
        "tip": "Keep index and middle tightly pressed against each other.",
    },
    "V": {
        "hands": "1 Hand",
        "posture": "Index and middle fingers extended forming a 'V' peace sign; others curled into fist.",
        "tip": "Spread index and middle apart cleanly.",
    },
    "W": {
        "hands": "1 Hand",
        "posture": "Index, middle, and ring fingers extended spread open; thumb and pinky touch.",
        "tip": "Spread the three fingers in a distinct 'W' trident.",
    },
    "X": {
        "hands": "1 Hand",
        "posture": "Index finger hooked like a curved claw/tongs; other fingers clenched in fist.",
        "tip": "Bend top two joints of index finger into a hook.",
    },
    "Y": {
        "hands": "1 Hand",
        "posture": "Thumb and pinky extended wide ('hang loose' sign); index, middle, ring curled.",
        "tip": "Stretch thumb and pinky wide apart with palm facing camera.",
    },
    "Z": {
        "hands": "1 Hand",
        "posture": "Index finger points forward and traces the zigzag shape of 'Z' in the air.",
        "tip": "Trace a sharp horizontal-diagonal-horizontal zigzag.",
    },
    "SPACE": {
        "hands": "1 Hand",
        "posture": "Open flat relaxed palm held stationary in camera view.",
        "tip": "Hold steady for dwell confirmation interval (600ms) to insert a word space.",
    },
}

# ISL (Indian Sign Language) Dual-Handed Alphabet & Static Words
ISL_GESTURES: Dict[str, Dict[str, str]] = {
    "A": {
        "hands": "2 Hands",
        "category": "Letter",
        "posture": "Non-dominant palm held flat; dominant index finger touches base of non-dominant thumb.",
        "tip": "Keep non-dominant palm flat facing torso or camera.",
    },
    "B": {
        "hands": "2 Hands",
        "category": "Letter",
        "posture": "Both hands form circles with thumb and index; loops touch forming a figure-8.",
        "tip": "Touch the two loops together at chest height.",
    },
    "C": {
        "hands": "1 Hand",
        "category": "Letter",
        "posture": "Single hand curved into an open 'C' shape with palm facing center.",
        "tip": "Clear side profile of the 'C' curvature.",
    },
    "D": {
        "hands": "2 Hands",
        "category": "Letter",
        "posture": "Non-dominant index points upright; dominant thumb and index form loop touching non-dominant index.",
        "tip": "Forms a visual lowercase or capital 'D'.",
    },
    "E": {
        "hands": "2 Hands",
        "category": "Letter",
        "posture": "Dominant index finger touches the tip of non-dominant index finger.",
        "tip": "Touch index tips together horizontally.",
    },
    "F": {
        "hands": "2 Hands",
        "category": "Letter",
        "posture": "Dominant index & middle fingers cross over non-dominant index & middle fingers.",
        "tip": "Cross fingers perpendicular like a grid hash.",
    },
    "G": {
        "hands": "2 Hands",
        "category": "Letter",
        "posture": "Both fists clenched; dominant fist stacked directly on top of non-dominant fist.",
        "tip": "Keep fists vertically stacked at center chest.",
    },
    "H": {
        "hands": "2 Hands",
        "category": "Letter",
        "posture": "Non-dominant palm flat; dominant open flat hand strokes across non-dominant palm outward.",
        "tip": "Slide dominant palm across surface of other hand.",
    },
    "I": {
        "hands": "2 Hands",
        "category": "Letter",
        "posture": "Dominant index finger points and touches the tip of non-dominant middle finger.",
        "tip": "Keep non-dominant fingers spread slightly.",
    },
    "K": {
        "hands": "2 Hands",
        "category": "Letter",
        "posture": "Non-dominant index upright; dominant index points into the middle knuckle joint.",
        "tip": "Dominant index acts as the diagonal branch of 'K'.",
    },
    "L": {
        "hands": "1 Hand",
        "category": "Letter",
        "posture": "Single hand: thumb and index finger extended at right angle forming an 'L'.",
        "tip": "Palm faces camera or forward.",
    },
    "M": {
        "hands": "2 Hands",
        "category": "Letter",
        "posture": "Dominant 3 fingers (index, middle, ring) rest across non-dominant flat palm.",
        "tip": "Three fingers represent the 3 stems of 'M'.",
    },
    "N": {
        "hands": "2 Hands",
        "category": "Letter",
        "posture": "Dominant 2 fingers (index, middle) rest across non-dominant flat palm.",
        "tip": "Two fingers represent the 2 stems of 'N'.",
    },
    "O": {
        "hands": "2 Hands",
        "category": "Letter",
        "posture": "Dominant index finger touches the tip of non-dominant ring finger.",
        "tip": "Target the ring finger tip cleanly.",
    },
    "P": {
        "hands": "2 Hands",
        "category": "Letter",
        "posture": "Non-dominant index points upright; dominant thumb and index form a loop touching index tip.",
        "tip": "Top loop mimics letter 'P'.",
    },
    "Q": {
        "hands": "2 Hands",
        "category": "Letter",
        "posture": "Non-dominant hand forms circle (thumb & index); dominant index hooks through from bottom.",
        "tip": "Hook index finger into non-dominant loop.",
    },
    "R": {
        "hands": "2 Hands",
        "category": "Letter",
        "posture": "Dominant index curls into a hook resting on non-dominant open flat palm.",
        "tip": "Keep non-dominant palm facing upward.",
    },
    "S": {
        "hands": "2 Hands",
        "category": "Letter",
        "posture": "Pinky fingers of both hands hook together at chest height.",
        "tip": "Interlock both pinkies cleanly.",
    },
    "T": {
        "hands": "2 Hands",
        "category": "Letter",
        "posture": "Dominant index touches side edge of non-dominant vertical flat palm.",
        "tip": "Dominant index forms horizontal crossbar of 'T'.",
    },
    "U": {
        "hands": "2 Hands",
        "category": "Letter",
        "posture": "Dominant index finger touches the tip of non-dominant pinky finger.",
        "tip": "Final vowel position on non-dominant hand.",
    },
    "V": {
        "hands": "1 Hand",
        "category": "Letter",
        "posture": "Single hand peace sign / 'V' with index and middle fingers spread wide.",
        "tip": "Palm facing camera.",
    },
    "W": {
        "hands": "2 Hands",
        "category": "Letter",
        "posture": "Both hands interlock all fingers pointing diagonally upward.",
        "tip": "Intertwine fingers to create a zigzag pattern.",
    },
    "X": {
        "hands": "2 Hands",
        "category": "Letter",
        "posture": "Dominant index finger crosses over non-dominant index finger forming an 'X'.",
        "tip": "Cross index fingers at 90-degree angle.",
    },
    "Y": {
        "hands": "2 Hands",
        "category": "Letter",
        "posture": "Non-dominant thumb and index spread wide in 'V'; dominant index rests into the crook.",
        "tip": "Dominant finger forms the stem of 'Y'.",
    },
    "SPACE": {
        "hands": "1 Hand",
        "category": "Special",
        "posture": "Open relaxed flat palm held steady in camera view.",
        "tip": "Hold for 500ms to insert space between words.",
    },
    "NAMASTE": {
        "hands": "2 Hands",
        "category": "Word",
        "posture": "Both flat open palms pressed together upright at chest level (prayer greeting).",
        "tip": "Traditional greeting mudra; align palms together at sternum.",
    },
    "GOOD": {
        "hands": "1 Hand",
        "category": "Word",
        "posture": "Single closed fist with thumb extended straight upright (thumbs-up).",
        "tip": "Hold thumb pointing directly to ceiling.",
    },
    "BAD": {
        "hands": "1 Hand",
        "category": "Word",
        "posture": "Single closed fist with pinky finger extended straight upright.",
        "tip": "Keep pinky fully vertical, other fingers clenched.",
    },
    "PEACE": {
        "hands": "1 Hand",
        "category": "Word",
        "posture": "Single hand upright 'V' sign with index and middle fingers spread wide.",
        "tip": "Palm facing forward toward camera.",
    },
    "OK": {
        "hands": "1 Hand",
        "category": "Word",
        "posture": "Thumb and index fingertips touch forming circle; middle, ring, pinky fanned up.",
        "tip": "Standard 'OK' sign; keep 3 fingers spread open.",
    },
    "HOUSE": {
        "hands": "2 Hands",
        "category": "Word",
        "posture": "Fingertips of both angled open flat hands meet at peak forming a triangular pitched roof.",
        "tip": "Touch fingertips together at 45-degree angle.",
    },
    "BOOK": {
        "hands": "2 Hands",
        "category": "Word",
        "posture": "Both flat open palms facing upward, touching pinky edges together like an open book.",
        "tip": "Keep palms open flat facing ceiling.",
    },
    "TIME": {
        "hands": "2 Hands",
        "category": "Word",
        "posture": "Dominant index finger points and taps wrist of non-dominant arm (watch face).",
        "tip": "Hold non-dominant arm horizontal and tap wrist.",
    },
    "LOVE": {
        "hands": "1 Hand",
        "category": "Word",
        "posture": "Thumb, index finger, and pinky extended wide; middle and ring folded (ILY sign).",
        "tip": "Palm facing forward toward camera.",
    },
    "STOP": {
        "hands": "1 Hand",
        "category": "Word",
        "posture": "Single flat open hand held vertically facing directly toward camera.",
        "tip": "Fingers straight, palm flat toward lens.",
    },
}

# Auto-populate comprehensive 200 daily usage static words into ISL dictionary
try:
    from signora.isl.collect import get_sign_guide
    from signora.isl.config import STATIC_WORDS, get_expected_hands

    for _w in STATIC_WORDS:
        if _w not in ISL_GESTURES:
            _h = "1 Hand" if get_expected_hands(_w) == 1 else "2 Hands"
            _g1, _g2 = get_sign_guide(_w)
            ISL_GESTURES[_w] = {
                "hands": _h,
                "category": "Word",
                "posture": _g1,
                "tip": _g2,
            }
except Exception:
    pass


# ASL Dynamic Words (Holistic trajectory-based vocabulary)
WORDS_GESTURES: Dict[str, Dict[str, str]] = {
    "HELLO": {
        "hands": "1 Hand",
        "trajectory": "Flat open hand starts near temple/brow and arcs outward in a crisp salute motion.",
        "tip": "Keep hand open and make an outward salute sweep.",
    },
    "THANKYOU": {
        "hands": "1 Hand",
        "trajectory": "Fingertips of flat open hand touch chin or lips, then move outward toward viewer.",
        "tip": "Start at lower lip/chin and extend palm outward.",
    },
    "PLEASE": {
        "hands": "1 Hand",
        "trajectory": "Flat open palm rubs in a smooth clockwise circular motion over center of chest.",
        "tip": "Keep palm pressed flat against chest area during circle.",
    },
    "YES": {
        "hands": "1 Hand",
        "trajectory": "Clenched 'S' fist nods up and down at wrist, mimicking a head nodding yes.",
        "tip": "Nod fist twice smoothly at camera height.",
    },
    "NO": {
        "hands": "1 Hand",
        "trajectory": "Index and middle fingers extended snap shut onto thumb repeatedly.",
        "tip": "Snap fingertips together firmly.",
    },
    "HELP": {
        "hands": "2 Hands",
        "trajectory": "Dominant fist rests thumbs-up on flat non-dominant palm; both hands lift upward together.",
        "tip": "Lift both hands together smoothly in front of chest.",
    },
    "SORRY": {
        "hands": "1 Hand",
        "trajectory": "Closed 'A' fist rubs in a circular motion over center of chest.",
        "tip": "Rub knuckle/fist on chest in a circle.",
    },
    "GOOD": {
        "hands": "2 Hands",
        "trajectory": "Dominant flat hand touches chin, then moves down to rest onto flat non-dominant palm.",
        "tip": "Smooth downward motion ending palm-on-palm.",
    },
    "BAD": {
        "hands": "1 Hand",
        "trajectory": "Flat hand touches chin, then flips palm downward and pushes away.",
        "tip": "Flipping motion moving away from face.",
    },
    "NAME": {
        "hands": "2 Hands",
        "trajectory": "'H' fingers (index & middle) of dominant hand tap twice across non-dominant 'H' fingers.",
        "tip": "Tap fingers perpendicularly twice.",
    },
    "MORE": {
        "hands": "2 Hands",
        "trajectory": "Both hands with fingertips touching thumb (flattened O) tap together at center repeatedly.",
        "tip": "Tap fingertips of both hands together at midline.",
    },
    "LOVE": {
        "hands": "2 Hands",
        "trajectory": "Both arms crossed over chest with closed fists, hugging torso.",
        "tip": "Cross forearms firmly across chest.",
    },
    "EAT": {
        "hands": "1 Hand",
        "trajectory": "Fingertips gathered to thumb (squished 'O') tapping repeatedly at lips/mouth.",
        "tip": "Bring gathered fingertips to mouth twice.",
    },
    "DRINK": {
        "hands": "1 Hand",
        "trajectory": "Hand forms 'C' cup shape and tilts upward toward mouth as if drinking from a glass.",
        "tip": "Tip thumb upward toward mouth.",
    },
    "WHERE": {
        "hands": "1 Hand",
        "trajectory": "Index finger extended upright shakes smoothly side-to-side with questioning posture.",
        "tip": "Wiggle upright index finger left and right.",
    },
    "FINISHED": {
        "hands": "2 Hands",
        "trajectory": "Both open flat hands with palms facing chest flip rapidly outward to face away.",
        "tip": "Quick outward flick of both wrists.",
    },
    "GOOD_MORNING": {
        "hands": "2 Hands",
        "trajectory": "Compound sign: Sign 'GOOD' (chin to hand) followed immediately by 'MORNING' (rising arm).",
        "tip": "Perform good then sunrise arm motion seamlessly.",
    },
    "MORNING": {
        "hands": "2 Hands",
        "trajectory": "Non-dominant arm horizontal; dominant arm rises from crook of elbow like sunrise.",
        "tip": "Bring dominant arm up from below horizontal arm.",
    },
    "AFTERNOON": {
        "hands": "2 Hands",
        "trajectory": "Dominant flat forearm rests angled downward over horizontal non-dominant forearm.",
        "tip": "Angle arm at 45 degrees over base forearm.",
    },
    "NIGHT": {
        "hands": "2 Hands",
        "trajectory": "Dominant bent hand arches down over horizontal non-dominant forearm like sunset.",
        "tip": "Cup hand downward over horizontal arm.",
    },
    "HOW": {
        "hands": "2 Hands",
        "trajectory": "Both curved hands with knuckles touching roll outward until palms face upward.",
        "tip": "Roll knuckles outward together.",
    },
    "WELCOME": {
        "hands": "1 Hand",
        "trajectory": "Open flat hand with palm up sweeps inward from side toward torso invitingly.",
        "tip": "Sweeping inviting motion toward body.",
    },
    "HAVE": {
        "hands": "2 Hands",
        "trajectory": "Fingertips of both bent hands touch upper chest firmly.",
        "tip": "Bring both bent hands to touch chest simultaneously.",
    },
    "WATER": {
        "hands": "1 Hand",
        "trajectory": "'W' handshape (3 fingers upright) taps index finger against chin twice.",
        "tip": "Form 'W' and tap side of index to chin.",
    },
    "FOOD": {
        "hands": "1 Hand",
        "trajectory": "Gathered fingertips (squished 'O') tap against lips twice.",
        "tip": "Tap mouth area gently twice.",
    },
}


def search_all_gestures(query: str) -> List[Dict[str, Any]]:
    """Search across ASL, ISL, and Words dictionaries for a matching sign or term."""
    q = query.strip().upper()
    if not q:
        return []
    results = []
    # ASL
    for sign, info in ASL_GESTURES.items():
        if q == sign or q in sign or q in info["posture"].upper() or q in info["tip"].upper():
            results.append({"system": "ASL Fingerspelling", "sign": sign, **info})
    # ISL
    for sign, info in ISL_GESTURES.items():
        if (
            q == sign
            or q in sign
            or q in info["posture"].upper()
            or q in info.get("tip", "").upper()
        ):
            results.append({"system": "ISL Dual-Handed", "sign": sign, **info})
    # Words
    for sign, info in WORDS_GESTURES.items():
        if q == sign or q in sign or q in info["trajectory"].upper() or q in info["tip"].upper():
            results.append({"system": "ASL Dynamic Words", "sign": sign, **info})
    return results
