"""Bidirectional English-to-ISL Grammar Synthesizer for Desktop Python.

Converts spoken or written English (SVO grammar) into authentic Indian Sign Language
(ISL SOV grammar, WH-question end movement, temporal fronting, and copula/article elimination).
"""

import re
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Set, Tuple


@dataclass
class IslSignCue:
    gloss: str
    body_anchor: str
    description: str
    is_fingerspelled: bool = False
    fingerspelling_letters: List[str] = field(default_factory=list)


@dataclass
class EnglishToIslResult:
    original_sentence: str
    isl_glosses: List[str]
    sign_cues: List[IslSignCue]
    grammatical_notes: str


class EnglishToIslSynthesizer:
    ARTICLES_AND_FILLERS: Set[str] = {
        "A", "AN", "THE", "OF", "TO", "FOR", "AT", "BY", "WITH", "ABOUT",
        "JUST", "PLEASE", "KINDLY", "OH", "AH", "UM", "UH", "WELL"
    }

    COPULAS_AND_AUXILIARIES: Set[str] = {
        "IS", "AM", "ARE", "WAS", "WERE", "BE", "BEEN", "BEING",
        "DO", "DOES", "DID", "HAVE", "HAS", "HAD", "WILL", "SHALL", "WOULD", "SHOULD", "COULD"
    }

    WH_WORDS: Set[str] = {
        "WHERE", "WHAT", "WHO", "WHEN", "WHY", "HOW", "WHICH"
    }

    TEMPORAL_WORDS: Set[str] = {
        "TODAY", "TOMORROW", "YESTERDAY", "NOW", "LATER", "SOON", "TONIGHT",
        "MORNING", "AFTERNOON", "EVENING", "NIGHT", "SUNDAY", "MONDAY",
        "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "DAILY",
        "ALWAYS", "NEVER"
    }

    NEGATION_WORDS: Set[str] = {
        "NOT", "NO", "NEVER", "CANNOT", "DONT", "DOESNT", "DIDNT", "WONT", "CANT"
    }

    PRONOUNS: Set[str] = {
        "I", "ME", "MY", "MINE", "YOU", "YOUR", "YOURS", "WE", "US", "OUR", "OURS",
        "HE", "HIM", "HIS", "SHE", "HER", "HERS", "THEY", "THEM", "THEIR", "IT", "ITS"
    }

    VERBS: Set[str] = {
        "GO", "COME", "WANT", "NEED", "EAT", "DRINK", "SLEEP", "HELP", "SEE",
        "HEAR", "FEEL", "KNOW", "UNDERSTAND", "SAY", "TELL", "SPEAK", "ASK",
        "BUY", "PAY", "WAIT", "STOP", "START", "TAKE", "GIVE", "FIND", "LOOK",
        "MEET", "VISIT", "CALL", "WRITE", "READ", "WORK", "STUDY", "LEARN",
        "TEACH", "LOVE", "LIKE", "HATE", "LIVE", "STAY", "ARRIVE", "LEAVE"
    }

    SIGN_LEXICON: Dict[str, Tuple[str, str]] = {
        "HELLO": ("HEAD", "Wave flat open hand outward from temple with friendly expression"),
        "HI": ("HEAD", "Wave flat open hand outward from temple"),
        "NAMASTE": ("CHEST_CENTER", "Press palms together flat in front of chest (Anjali Mudra)"),
        "THANK": ("CHIN", "Touch flat hand fingertips to chin and extend forward toward person"),
        "THANKS": ("CHIN", "Touch flat hand fingertips to chin and extend forward toward person"),
        "WELCOME": ("CHEST_CENTER", "Sweep open palm inward toward chest invitingly"),
        "SORRY": ("CHEST_CENTER", "Rub closed fist in circular motion over heart with regretful expression"),
        "PLEASE": ("CHEST_CENTER", "Rub open flat palm in clockwise circle over chest"),
        "YES": ("OPEN_SPACE", "Fist nods up and down in front of body like a head nodding"),
        "NO": ("OPEN_SPACE", "Index and middle fingers snap firmly down onto thumb"),
        "GOOD": ("CHIN", "Flat hand fingertips touch chin and move forward into palm"),
        "BAD": ("CHIN", "Flat hand moves forward from chin and turns sharply palm-down"),
        "HELP": ("CHEST_CENTER", "Place dominant thumbs-up fist onto flat non-dominant palm and lift"),
        "DOCTOR": ("FOREARMS", "Tap index and middle fingers on inner wrist of non-dominant arm (pulse check)"),
        "HOSPITAL": ("CHEST_LEFT", "Trace a cross (+) on upper left arm or shoulder with two fingers"),
        "MEDICINE": ("OPEN_SPACE", "Circle middle finger inside open palm like crushing in a mortar"),
        "EMERGENCY": ("OPEN_SPACE", "Wave both hands side to side rapidly at shoulder height"),
        "PAIN": ("CHEST_CENTER", "Point index fingers toward each other and twist sharply with furrowed brow"),
        "HURT": ("CHEST_CENTER", "Point index fingers toward each other and twist sharply with pained expression"),
        "FEVER": ("HEAD", "Place back of hand against forehead to check body temperature"),
        "COLD": ("CHEST_CENTER", "Clench fists near chest and shiver upper body"),
        "COUGH": ("CHIN", "Hold loose fist near mouth and make slight coughing movements"),
        "HEADACHE": ("HEAD", "Twist index fingers toward each other beside temples"),
        "WATER": ("CHIN", "Form W-handshape and tap side of chin twice"),
        "FOOD": ("CHIN", "Bring grouped fingertips of flat-O hand to mouth repeatedly"),
        "EAT": ("CHIN", "Bring grouped fingertips to mouth repeatedly"),
        "DRINK": ("CHIN", "C-handshape tipped toward mouth as if drinking from a cup"),
        "SLEEP": ("HEAD", "Rest tilted cheek onto folded palms near ear"),
        "REST": ("CHEST_CENTER", "Cross arms loosely over chest with relaxed shoulders"),
        "POLICE": ("CHEST_LEFT", "C-handshape tapped twice over left chest badge position"),
        "AMBULANCE": ("HEAD", "Hold open hand above head and rotate with flashing beacon motion"),
        "STATION": ("OPEN_SPACE", "Bent two-finger handshape moving along track"),
        "TRAIN": ("OPEN_SPACE", "Rub index and middle fingers of both hands back and forth along each other"),
        "BUS": ("OPEN_SPACE", "Mime holding large steering wheel with both hands"),
        "CAR": ("OPEN_SPACE", "Hold imaginary steering wheel and make slight driving adjustments"),
        "MONEY": ("OPEN_SPACE", "Rub thumb across fingertips of upward palm repeatedly"),
        "TIME": ("FOREARMS", "Tap non-dominant wrist with index finger twice"),
        "TODAY": ("OPEN_SPACE", "Drop both Y-handshapes down together firmly in neutral space"),
        "TOMORROW": ("CHIN", "Thumb of A-handshape flicks forward from cheek bone"),
        "YESTERDAY": ("CHIN", "Thumb of A-handshape moves backward from cheek toward ear"),
        "NOW": ("OPEN_SPACE", "Drop bent hands down in front of torso firmly"),
        "WHERE": ("OPEN_SPACE", "Both hands open palms up, sweep side-to-side with questioning expression"),
        "WHAT": ("OPEN_SPACE", "Index finger or open hands move side-to-side with inquiring look"),
        "WHO": ("CHIN", "Circle index finger in small loops around mouth"),
        "WHEN": ("OPEN_SPACE", "Circle index finger around non-dominant index finger then touch tip"),
        "WHY": ("HEAD", "Touch forehead with fingertips and pull down into Y-handshape"),
        "HOW": ("OPEN_SPACE", "Curved hands back-to-back rotate outward so palms face upward"),
        "WHICH": ("OPEN_SPACE", "Both A-hands alternately move up and down in weighing motion"),
        "NAME": ("CHEST_CENTER", "H-handshapes (index and middle fingers) tap together in front of chest"),
        "MY_NAME_IS": ("CHEST_CENTER", "Flat right palm pressed to chest (MY) then H-fingers tap left fingers twice (NAME)"),
        "I": ("CHEST_CENTER", "Index finger points directly to center of chest"),
        "ME": ("CHEST_CENTER", "Index finger points directly to center of chest"),
        "MY": ("CHEST_CENTER", "Flat palm pressed against chest"),
        "YOU": ("OPEN_SPACE", "Index finger points forward toward conversational partner"),
        "YOUR": ("OPEN_SPACE", "Flat palm pushed gently forward toward conversational partner"),
        "WE": ("CHEST_CENTER", "Index finger sweeps in an arc across chest from right to left shoulder"),
        "HE": ("OPEN_SPACE", "Index finger points to the right side neutral space"),
        "SHE": ("OPEN_SPACE", "Index finger points to the right side neutral space"),
        "THEY": ("OPEN_SPACE", "Index finger arcs across lateral neutral space"),
        "STOP": ("OPEN_SPACE", "Chop edge of dominant open hand sharply down onto flat non-dominant palm"),
        "GO": ("OPEN_SPACE", "Both index fingers arc forward and away from body"),
        "COME": ("OPEN_SPACE", "Both index fingers curl inward toward chest invitingly"),
        "WANT": ("OPEN_SPACE", "Both curved hands pull inward toward body with grasping fingers"),
        "NEED": ("OPEN_SPACE", "Bent index finger nods downwards firmly twice"),
        "LIKE": ("CHEST_CENTER", "Thumb and middle finger draw outward from chest coming together"),
        "LOVE": ("CHEST_CENTER", "Cross both closed fists over heart hugging tightly"),
        "UNDERSTAND": ("HEAD", "Flick index finger upward from fist beside temple (lightbulb moment)"),
        "KNOW": ("HEAD", "Tap bent fingertips against side of forehead"),
        "HOME": ("CHIN", "Touch flat-O fingertips to cheek then near ear (places of eating and sleeping)"),
        "WORK": ("OPEN_SPACE", "Tap dominant fist down onto non-dominant wrist twice"),
        "SCHOOL": ("OPEN_SPACE", "Clap dominant flat hand down onto non-dominant palm twice"),
        "NOT": ("CHIN", "Flick thumb out from under chin forward with firm headshake")
    }

    @classmethod
    def translate(cls, english_text: str) -> EnglishToIslResult:
        trimmed = english_text.strip()
        if not trimmed:
            return EnglishToIslResult("", [], [], "Empty input")

        expanded = cls._expand_contractions(trimmed)
        raw_tokens = [
            t.upper()
            for t in re.sub(r"[^a-zA-Z0-9\s]", " ", expanded).split()
            if t
        ]

        if not raw_tokens:
            return EnglishToIslResult(english_text, [], [], "No valid words")

        temporals: List[str] = []
        subjects: List[str] = []
        objects_and_locations: List[str] = []
        verbs: List[str] = []
        negations: List[str] = []
        wh_words: List[str] = []
        other_words: List[str] = []

        for token in raw_tokens:
            if token in cls.WH_WORDS:
                wh_words.append(token)
            elif token in cls.TEMPORAL_WORDS:
                temporals.append(token)
            elif token in cls.NEGATION_WORDS:
                norm = "NOT" if token in {"DONT", "DOESNT", "DIDNT", "WONT", "CANT", "CANNOT"} else token
                negations.append(norm)
            elif token in cls.PRONOUNS:
                subjects.append(token)
            elif token in cls.VERBS:
                verbs.append(token)
            elif token in cls.ARTICLES_AND_FILLERS or token in cls.COPULAS_AND_AUXILIARIES:
                pass
            else:
                objects_and_locations.append(token)

        # Assemble ISL sequence:
        # [TEMPORAL] + [SUBJECT] + [OBJECT] + [OTHER] + [VERB] + [NEGATION] + [WH]
        ordered: List[str] = []
        ordered.extend(temporals)
        ordered.extend(subjects)
        ordered.extend(objects_and_locations)
        ordered.extend(other_words)
        ordered.extend(verbs)
        ordered.extend(negations)
        ordered.extend(wh_words)

        if not ordered:
            ordered.extend(raw_tokens)

        # Deduplicate consecutive duplicates
        final_glosses: List[str] = []
        for g in ordered:
            if not final_glosses or final_glosses[-1] != g:
                final_glosses.append(g)

        sign_cues = [cls._build_sign_cue(g) for g in final_glosses]

        notes = []
        if temporals:
            notes.append("Temporal-Fronting")
        if verbs and objects_and_locations:
            notes.append("SVO→SOV")
        if wh_words:
            notes.append("WH-Final")
        notes.append("Copula/Article-Pruning")

        return EnglishToIslResult(
            original_sentence=english_text,
            isl_glosses=final_glosses,
            sign_cues=sign_cues,
            grammatical_notes=", ".join(notes)
        )

    @classmethod
    def _build_sign_cue(cls, gloss: str) -> IslSignCue:
        upper = gloss.upper()
        if upper in cls.SIGN_LEXICON:
            anchor, desc = cls.SIGN_LEXICON[upper]
            return IslSignCue(
                gloss=upper,
                body_anchor=anchor,
                description=desc,
                is_fingerspelled=False
            )
        else:
            letters = [c for c in upper if c.isalpha()]
            desc = f"Fingerspell letter-by-letter: {' - '.join(letters)}" if letters else "Perform standard gesture"
            return IslSignCue(
                gloss=upper,
                body_anchor="CHEST_CENTER",
                description=desc,
                is_fingerspelled=bool(letters),
                fingerspelling_letters=letters
            )

    @classmethod
    def _expand_contractions(cls, text: str) -> str:
        replacements = [
            (r"\bdon't\b", "do not"),
            (r"\bdoesn't\b", "does not"),
            (r"\bdidn't\b", "did not"),
            (r"\bcan't\b", "cannot"),
            (r"\bwon't\b", "will not"),
            (r"\bi'm\b", "I am"),
            (r"\byou're\b", "you are"),
            (r"\bwe're\b", "we are"),
            (r"\bthey're\b", "they are"),
            (r"\bhe's\b", "he is"),
            (r"\bshe's\b", "she is"),
            (r"\bit's\b", "it is"),
            (r"\bwhat's\b", "what is"),
            (r"\bwhere's\b", "where is"),
            (r"\bwho's\b", "who is"),
            (r"\bhow's\b", "how is"),
        ]
        res = text
        for pat, repl in replacements:
            res = re.sub(pat, repl, res, flags=re.IGNORECASE)
        return res
