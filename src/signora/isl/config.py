"""Configuration constants and paths for ISL (Indian Sign Language) mode."""

from pathlib import Path
from typing import Dict, List

from signora.models.manifest import get_model_path

# Manual Alphabet: ISLRTC standard 2-handed alphabet
ALL_LETTERS = list("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
# Phase 1 static letters (excluding dynamic motion letters J and Z)
STATIC_LETTERS = [c for c in ALL_LETTERS if c not in ("J", "Z")]
SPECIAL_SIGNS = ["SPACE"]

# 200 Static ISL Words (iconic daily usage signs requiring zero hand motion or facial dynamics)
STATIC_WORDS: List[str] = [
    # 1. Greetings & Courtesy (15)
    "NAMASTE",       # Both flat hands joined at chest in prayer [2 hands]
    "HELLO",         # Open flat palm facing forward at shoulder height [1 hand]
    "WELCOME",       # Both open palms upturned, extended forward in gesture of receiving [2 hands]
    "THANK_YOU",     # Flat right fingertips touch chin/lips then held facing forward [1 hand]
    "PLEASE",        # Flat open right palm placed over center of chest [1 hand]
    "CONGRATS",      # Both thumbs up pointing upward side-by-side [2 hands]
    "RESPECT",       # Right flat palm vertical beside temple, left hand flat at chest [2 hands]
    "PEACE",         # V-sign (victory) with index and middle fingers extended [1 hand]
    "LOVE",          # Thumb, index, and pinky extended (ILY sign) [1 hand]
    "FRIEND",        # Index fingers of both hands hooked into each other [2 hands]
    "FAMILY",        # Both hands form 'F' shapes with index & thumb circles touching [2 hands]
    "TOGETHER",      # Both open palms facing each other with fingers interlaced [2 hands]
    "PROMISE",       # Right index vertical touching lips, left flat palm horizontal [2 hands]
    "APOLOGY",       # Right closed fist placed gently on chest [1 hand]
    "GOODBYE",       # Open flat right hand held steady tilted slightly outward [1 hand]

    # 2. Daily Responses & Common Feedback (15)
    "YES",           # Closed right fist with thumb resting on knuckles [1 hand]
    "NO",            # Index and middle fingers extended together snapping onto thumb [1 hand]
    "OK",            # Thumb and index tips forming a ring with other 3 fingers up [1 hand]
    "GOOD",          # Right thumb pointing straight up (thumbs up) [1 hand]
    "BAD",           # Right pinky extended upright while other fingers folded [1 hand]
    "MAYBE",         # Both open flat palms facing upward held level side-by-side [2 hands]
    "SURE",          # Right index pointing vertically upward near corner of mouth [1 hand]
    "AGREE",         # Both index fingers extended pointing forward parallel [2 hands]
    "DISAGREE",      # Both index fingers extended pointing away from each other [2 hands]
    "TRUE",          # Right index finger placed vertically against chin [1 hand]
    "FALSE",         # Right index finger positioned horizontally across lips [1 hand]
    "RIGHT",         # Right fist with index extended resting on top of left fist [2 hands]
    "WRONG",         # Right hand forming 'Y' shape held under chin [1 hand]
    "PERFECT",       # Both hands forming 'OK' circles touching each other [2 hands]
    "READY",         # Both hands forming 'R' shape (crossed fingers) side-by-side [2 hands]

    # 3. Emergency, Safety & Medical (20)
    "HELP",          # Closed right fist with thumb up resting on open left flat palm [2 hands]
    "EMERGENCY",     # Right hand forming 'E' shape held firmly upright at chest [1 hand]
    "DOCTOR",        # Right fingertips (three fingers) resting on left inner wrist pulse [2 hands]
    "MEDICINE",      # Right middle finger tip placed on center of open left palm [2 hands]
    "HOSPITAL",      # Right index and middle fingers form a cross on left shoulder [2 hands]
    "PAIN",          # Both index fingers pointing directly at each other without touching [2 hands]
    "SICK",          # Right middle finger on forehead, left middle finger on stomach [2 hands]
    "FEVER",         # Back of right hand placed firmly against forehead [1 hand]
    "INJURY",        # Right index pointing directly at an open left flat palm [2 hands]
    "BLOOD",         # Right open curved hand held over left fist [2 hands]
    "SAFE",          # Both fists crossed at wrists in front of chest then held firm [2 hands]
    "DANGER",        # Both open hands held forward palms facing away with spread fingers [2 hands]
    "POLICE",        # Right hand 'C' shape held at left chest like a badge [1 hand]
    "AMBULANCE",     # Both hands forming open cups placed over ears/sides of head [2 hands]
    "FIRE",          # Both open hands with fingers fluttering upward frozen in flame pose [2 hands]
    "CAUTION",       # Both hands with open palms facing downward at waist level [2 hands]
    "OXYGEN",        # Cupped right hand held over mouth and nose [1 hand]
    "BANDAGE",       # Two flat fingers of right hand wrapped across left forearm [2 hands]
    "PILL",          # Right index and thumb pinched together touching left palm [2 hands]
    "REST",          # Both flat hands crossed over chest resting against shoulders [2 hands]

    # 4. Food & Nutrition (25)
    "WATER",         # Right hand 'W' shape (3 fingers) with index touching bottom lip [1 hand]
    "FOOD",          # Right flattened O shape (fingertips together) touching lips [1 hand]
    "TEA",           # Right hand 'F' pinch holding imaginary cup handle over left palm [2 hands]
    "COFFEE",        # Right fist resting directly on top of left fist [2 hands]
    "MILK",          # Closed right fist held at chin height (milking grip pose) [1 hand]
    "RICE",          # Cupped right hand resting over open left palm [2 hands]
    "BREAD",         # Right curved hand slicing against vertical left flat hand [2 hands]
    "FRUIT",         # Right 'F' shape with thumb/index ring twisting on right cheek [1 hand]
    "APPLE",         # Right fist knuckle placed against cheek [1 hand]
    "BANANA",        # Left index pointing up, right hand peeling grip around it [2 hands]
    "SUGAR",         # Two flat fingers (index & middle) brushing down across chin [1 hand]
    "SALT",          # Two fingers of right hand resting across two fingers of left hand [2 hands]
    "SWEET",         # Flat right hand fingers resting gently on chin [1 hand]
    "SPICY",         # Right open hand held spread in front of open mouth [1 hand]
    "HOT",           # Right clawed hand held facing mouth [1 hand]
    "COLD",          # Both hands in fists with elbows tucked close to ribs [2 hands]
    "HUNGRY",        # Right 'C' shaped hand placed on center of chest moving down [1 hand]
    "THIRSTY",       # Right index finger pointing at Adam's apple / throat [1 hand]
    "BREAKFAST",     # Food sign followed by morning horizontal arm pose [2 hands]
    "LUNCH",         # Food sign followed by vertical noon arm pose [2 hands]
    "DINNER",        # Food sign followed by sunset folded wrist pose [2 hands]
    "PLATE",         # Both open curved hands forming a wide horizontal circle [2 hands]
    "CUP",           # Right 'C' shaped hand resting on flat open left palm [2 hands]
    "SPOON",         # Right curved two fingers scooped into left cupped palm [2 hands]
    "FORK",          # Right 'V' fingers pointing downward into left flat palm [2 hands]

    # 5. Home, Objects & Technology (25)
    "HOUSE",         # Both hands flat with fingertips meeting at apex to form a roof [2 hands]
    "HOME",          # Right flattened hand touching cheek then moved to jaw [1 hand]
    "ROOM",          # Both flat hands facing each other defining four corners [2 hands]
    "DOOR",          # Both flat vertical palms touching index finger edges [2 hands]
    "WINDOW",        # Both flat palms horizontal, one positioned above the other [2 hands]
    "BED",           # Both flat hands pressed together resting against right cheek [2 hands]
    "CHAIR",         # Right curved two fingers resting on left curved two fingers [2 hands]
    "TABLE",         # Both forearms folded horizontally one resting on the other [2 hands]
    "BOOK",          # Both open flat palms facing up with pinky edges touching [2 hands]
    "PEN",           # Right hand in writing grip (pinch) resting on left flat palm [2 hands]
    "PAPER",         # Flat right palm resting face-down on flat left palm [2 hands]
    "PHONE",         # Right 'Y' shape held with thumb to ear, pinky to mouth [1 hand]
    "COMPUTER",      # Right 'C' shape curved resting along left horizontal forearm [2 hands]
    "BAG",           # Right fist clenched downward at side as if holding bag handle [1 hand]
    "MONEY",         # Right hand with thumb rubbing against index and middle tips [1 hand]
    "KEY",           # Right index knuckle bent into key shape pressing into left palm [2 hands]
    "LIGHT",         # Right hand pinched opening into a flared 5-finger star above head [1 hand]
    "FAN",           # Right hand 3 fingers spread pointing upward like fan blades [1 hand]
    "CLOTHES",       # Both open hands with thumbs resting on upper chest collar [2 hands]
    "SHOES",         # Both closed fists bumping together at knuckles side-by-side [2 hands]
    "GLASSES",       # Both hands 'C' shapes or pinches held around eyes [2 hands]
    "WATCH",         # Right index tapping or resting on left wrist [2 hands]
    "MIRROR",        # Flat open right palm held facing own face [1 hand]
    "SOAP",          # Right flat palm rubbing over back of left hand [2 hands]
    "TOWEL",         # Both fists held as if holding towel ends behind neck [2 hands]

    # 6. People & Family (20)
    "MOTHER",        # Open flat right hand with thumb touching chin [1 hand]
    "FATHER",        # Open flat right hand with thumb touching forehead [1 hand]
    "BROTHER",       # Right index touching forehead then resting on left index [2 hands]
    "SISTER",        # Right index touching chin then resting on left index [2 hands]
    "SON",           # Right hand salute at forehead then cradling left arm [2 hands]
    "DAUGHTER",      # Right hand salute at chin then cradling left arm [2 hands]
    "BABY",          # Both arms folded horizontally cradling an infant [2 hands]
    "CHILD",         # Flat right palm facing downward at waist height [1 hand]
    "BOY",           # Right hand grabbing imaginary baseball cap visor at forehead [1 hand]
    "GIRL",          # Right thumb tracing downward along right jawline [1 hand]
    "MAN",           # Right hand thumb touching forehead then chest [1 hand]
    "WOMAN",         # Right hand thumb touching chin then chest [1 hand]
    "TEACHER",       # Both flattened 'O' hands at temples then held flat down [2 hands]
    "STUDENT",       # Right hand gathering from left flat palm to forehead [2 hands]
    "DOCTOR_PERSON", # Right 'D' or 3-fingers touching left wrist pulse [2 hands]
    "LEADER",        # Right hand grasping left fingers and holding forward [2 hands]
    "NEIGHBOR",      # Both open palms facing each other side-by-side close together [2 hands]
    "GUEST",         # Both open upturned hands beckoning inward at chest [2 hands]
    "PERSON",        # Both flat vertical hands moving downward parallel [2 hands]
    "TEAM",          # Both 'T' hands circling together until thumbs meet [2 hands]

    # 7. Time, Days & Schedule (20)
    "TIME",          # Right index finger pointing at wrist watch location on left wrist [2 hands]
    "TODAY",         # Both 'Y' hands facing upward held firmly in front of body [2 hands]
    "TOMORROW",      # Right 'A' hand with thumb touching cheek pointing forward [1 hand]
    "YESTERDAY",     # Right 'A' hand with thumb touching cheek pointing backward [1 hand]
    "NOW",           # Both curved hands with palms upturned dropped firmly at waist [2 hands]
    "LATER",         # Right 'L' hand with index pointing forward like a gun [1 hand]
    "MORNING",       # Right flat palm rising upward under left horizontal arm [2 hands]
    "AFTERNOON",     # Right flat palm angled resting on left forearm [2 hands]
    "EVENING",       # Right curved hand cupping over left horizontal forearm [2 hands]
    "NIGHT",         # Right curved hand draped over left horizontal forearm [2 hands]
    "DAY",           # Right arm vertical with index pointing up resting on left flat arm [2 hands]
    "WEEK",          # Right index finger sliding along flat open left palm [2 hands]
    "MONTH",         # Right index sliding down back of left vertical index finger [2 hands]
    "YEAR",          # Both fists with right fist placed directly on top of left fist [2 hands]
    "EARLY",         # Right middle finger sliding across back of left fist [2 hands]
    "LATE",          # Right flat hand held downward beside hip facing backward [1 hand]
    "CLOCK",         # Right 'C' shape held around imaginary round dial on wall [1 hand]
    "CALENDAR",      # Right open 4 fingers held like a grid against left palm [2 hands]
    "ALWAYS",        # Right index pointing upward held steady in high air [1 hand]
    "NEVER",         # Right flat hand angled downward in front of chest [1 hand]

    # 8. Directions & Places (20)
    "HERE",          # Both open flat palms facing upward held steady in front of hips [2 hands]
    "THERE",         # Right index finger pointing firmly at a distant location [1 hand]
    "LEFT_DIR",      # Left hand forming 'L' pointing to the left side [1 hand]
    "RIGHT_DIR",     # Right hand forming 'R' pointing to the right side [1 hand]
    "UP",            # Right index finger pointing straight up toward ceiling [1 hand]
    "DOWN",          # Right index finger pointing straight down toward floor [1 hand]
    "FRONT",         # Right flat hand vertical held in front of forehead palm in [1 hand]
    "BEHIND",        # Right fist with thumb pointing backward over right shoulder [1 hand]
    "INSIDE",        # Right fingers cupped and inserted into left 'C' shaped hand [2 hands]
    "OUTSIDE",       # Right hand cupped pulling out of left 'C' shape [2 hands]
    "NEAR",          # Back of right curved hand resting close to left open palm [2 hands]
    "FAR",           # Right index finger pointing far ahead with shielded eyes [1 hand]
    "SCHOOL",        # Right flat palm clapping down horizontally onto left flat palm [2 hands]
    "OFFICE",        # Both 'O' hands facing each other then forming room walls [2 hands]
    "MARKET",        # Both hands cupped with thumbs up held side-by-side [2 hands]
    "STATION",       # Both flat palms horizontal forming platform edges [2 hands]
    "ROAD",          # Both flat vertical palms parallel facing each other pointing ahead [2 hands]
    "CITY",          # Both fingertips touching to form a series of house roofs [2 hands]
    "VILLAGE",       # Both hands 'V' shapes held side-by-side [2 hands]
    "COUNTRY",       # Right 'Y' shape resting on left elbow [2 hands]

    # 9. Emotions, Feelings & States (20)
    "HAPPY",         # Both open flat palms placed over chest pointing upward [2 hands]
    "SAD",           # Both open hands placed before face with fingers drooping [2 hands]
    "ANGRY",         # Both hands forming clawed shapes held in front of chest [2 hands]
    "CALM",          # Both flat palms facing downward pressing gently down [2 hands]
    "TIRED",         # Both curved hands with fingertip edges resting on ribs/chest [2 hands]
    "STRONG",        # Both fists clenched with biceps flexed at shoulder level [2 hands]
    "WEAK",          # Right fingertips resting limp and bent against left open palm [2 hands]
    "BRAVE",         # Both fists held firmly at shoulders facing inward [2 hands]
    "AFRAID",        # Both open hands held protective in front of chest palms in [2 hands]
    "BORED",         # Right index fingertip pressed into tip of nose [1 hand]
    "SURPRISED",     # Both index and thumbs pinched at eyes then held wide open [2 hands]
    "BUSY",          # Right 'B' hand vertical rubbing across left horizontal wrist [2 hands]
    "FREE",          # Both 'F' hands crossed at wrists then held uncrossed [2 hands]
    "PROUD",         # Right 'A' hand thumb sliding up center of chest [1 hand]
    "LONELY",        # Right index finger pointing up held alone at chin [1 hand]
    "PATIENT",       # Right thumb tip resting on lips with closed fist [1 hand]
    "NERVOUS",       # Both hands with fingers spread held trembling at waist [2 hands]
    "HOPE",          # Both hands with crossed fingers (R-shape) held near eyes [2 hands]
    "RELAX",         # Both hands with open palms resting on knees or thighs [2 hands]
    "SILENT",        # Right index finger held vertically against lips (shh pose) [1 hand]

    # 10. Actions & States (Static Postures) (20)
    "STOP",          # Open flat right palm facing forward, fingers upright [1 hand]
    "WAIT",          # Both open hands held forward palms up, fingers slightly curved [2 hands]
    "LISTEN",        # Right hand cupped behind right ear [1 hand]
    "LOOK",          # Right 'V' shape fingers pointing forward from eyes [1 hand]
    "THINK",         # Right index finger touching right temple [1 hand]
    "REMEMBER",      # Right thumb touching temple then resting on left thumb [2 hands]
    "FORGET",        # Right flat hand wiped across forehead from left to right [1 hand]
    "UNDERSTAND",    # Right index flicking up from closed fist near temple [1 hand]
    "LEARN",         # Right fingers gathering from left open palm to forehead [2 hands]
    "WORK",          # Right fist tapping or resting on top of left fist [2 hands]
    "PLAY",          # Both hands forming 'Y' shapes held at waist [2 hands]
    "SLEEP",         # Both flat palms pressed together resting under right ear [2 hands]
    "WAKE",          # Both index and thumbs opening wide beside both eyes [2 hands]
    "EAT",           # Right flattened hand with fingertips touching mouth [1 hand]
    "DRINK",         # Right hand 'C' shape tilted toward mouth like drinking glass [1 hand]
    "OPEN",          # Both flat hands side-by-side with palms facing chest [2 hands]
    "CLOSE",         # Both flat hands side-by-side with palms touching [2 hands]
    "SIT",           # Right curved index and middle fingers hooked over left two fingers [2 hands]
    "STAND",         # Right 'V' fingers pointing downward standing on left flat palm [2 hands]
    "GIVE",          # Both flat hands palms upturned extended forward [2 hands]
]

TRAIN_LABELS = STATIC_LETTERS + SPECIAL_SIGNS + STATIC_WORDS
SIGNS = TRAIN_LABELS
NUM_SIGNS = len(SIGNS)

# Mapping of signs that require 1 hand vs 2 hands
EXPECTED_HANDS: Dict[str, int] = {
    # 1-handed letters
    "C": 1,
    "L": 1,
    "V": 1,
    "SPACE": 1,

    # 1-handed words
    "HELLO": 1,
    "THANK_YOU": 1,
    "PLEASE": 1,
    "APOLOGY": 1,
    "GOODBYE": 1,
    "PEACE": 1,
    "LOVE": 1,
    "YES": 1,
    "NO": 1,
    "OK": 1,
    "GOOD": 1,
    "BAD": 1,
    "SURE": 1,
    "TRUE": 1,
    "FALSE": 1,
    "WRONG": 1,
    "EMERGENCY": 1,
    "FEVER": 1,
    "POLICE": 1,
    "OXYGEN": 1,
    "WATER": 1,
    "FOOD": 1,
    "MILK": 1,
    "FRUIT": 1,
    "APPLE": 1,
    "SUGAR": 1,
    "SWEET": 1,
    "SPICY": 1,
    "HOT": 1,
    "HUNGRY": 1,
    "THIRSTY": 1,
    "HOME": 1,
    "PHONE": 1,
    "BAG": 1,
    "MONEY": 1,
    "LIGHT": 1,
    "FAN": 1,
    "MIRROR": 1,
    "MOTHER": 1,
    "FATHER": 1,
    "CHILD": 1,
    "BOY": 1,
    "GIRL": 1,
    "MAN": 1,
    "WOMAN": 1,
    "TOMORROW": 1,
    "YESTERDAY": 1,
    "LATER": 1,
    "LATE": 1,
    "CLOCK": 1,
    "ALWAYS": 1,
    "NEVER": 1,
    "THERE": 1,
    "LEFT_DIR": 1,
    "RIGHT_DIR": 1,
    "UP": 1,
    "DOWN": 1,
    "FRONT": 1,
    "BEHIND": 1,
    "FAR": 1,
    "BORED": 1,
    "PROUD": 1,
    "LONELY": 1,
    "PATIENT": 1,
    "SILENT": 1,
    "STOP": 1,
    "LISTEN": 1,
    "LOOK": 1,
    "THINK": 1,
    "FORGET": 1,
    "UNDERSTAND": 1,
    "EAT": 1,
    "DRINK": 1,
}


def get_expected_hands(sign: str) -> int:
    """Return whether the sign requires 1 hand or 2 hands (defaults to 2 hands)."""
    return EXPECTED_HANDS.get(sign.upper(), 2)


# Feature dimensionality
SINGLE_HAND_DIM = 74
INTER_HAND_DIM = 14
PRESENCE_DIM = 2
FEATURE_DIM = SINGLE_HAND_DIM * 2 + INTER_HAND_DIM + PRESENCE_DIM  # 164

# Dwell & Recognition timings
BURST_FRAMES = 14
DWELL_MS = 1000
SPACE_DWELL_MS = 800
SMOOTH_MS = 300
CONFIRM_THRESHOLD = 0.52
REPEAT_COOLDOWN_MS = 550
SPACE_COOLDOWN_MS = 600


def get_isl_weights_path() -> Path:
    return get_model_path("isl_signs")

