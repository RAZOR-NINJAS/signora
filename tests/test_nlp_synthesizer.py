"""Unit tests for Universal ISL Grammar Synthesizer (Zero-Hallucination & Universal Vocabulary)."""

import pytest
from signora.nlp import IslGrammarSynthesizer, GlossToken, SentenceBuffer


def test_doctor_i_go_tomorrow():
    # User's exact prompt example: DOCTOR I GO TOMORROW
    # MUST NOT add extra words like 'morning' or 'planning to visit'
    tokens = ["DOCTOR", "I", "GO", "TOMORROW"]
    res = IslGrammarSynthesizer.synthesize(tokens)
    assert res == "I will go to the doctor tomorrow."


def test_emergency_chest_pain():
    # Emergency with body part and pain
    tokens = ["HELP", "CHEST", "PAIN"]
    res = IslGrammarSynthesizer.synthesize(tokens)
    assert res == "Help! I have pain in my chest."


def test_wh_question_train_station():
    # Wh-question with destination
    tokens = ["TRAIN", "STATION", "WHERE"]
    res = IslGrammarSynthesizer.synthesize(tokens)
    assert res == "Where is the train station?"


def test_wh_question_name():
    tokens = ["YOU", "NAME", "WHAT"]
    res = IslGrammarSynthesizer.synthesize(tokens)
    assert res == "What is your name?"


def test_wh_question_how_are_you():
    tokens = ["YOU", "HOW"]
    res = IslGrammarSynthesizer.synthesize(tokens)
    assert res == "How are you?"


def test_polite_request():
    tokens = ["WATER", "GIVE", "PLEASE"]
    res = IslGrammarSynthesizer.synthesize(tokens)
    assert res == "Please give water."


def test_polite_request_door_open():
    tokens = ["DOOR", "OPEN", "PLEASE"]
    res = IslGrammarSynthesizer.synthesize(tokens)
    assert res == "Please open the door."


def test_statement_perfect_tense():
    tokens = ["I", "WORK", "FINISH"]
    res = IslGrammarSynthesizer.synthesize(tokens)
    assert res == "I have worked."


def test_statement_past_tense():
    tokens = ["YESTERDAY", "I", "CALL", "DOCTOR"]
    res = IslGrammarSynthesizer.synthesize(tokens)
    assert res == "I called the doctor yesterday."


def test_single_tokens():
    assert IslGrammarSynthesizer.synthesize(["HELLO"]) == "Hello."
    assert IslGrammarSynthesizer.synthesize(["NAMASTE"]) == "Namaste."
    assert IslGrammarSynthesizer.synthesize(["THANK_YOU"]) == "Thank you."
    assert IslGrammarSynthesizer.synthesize(["YES"]) == "Yes."
    assert IslGrammarSynthesizer.synthesize(["NO"]) == "No."
    assert IslGrammarSynthesizer.synthesize(["HELP"]) == "Help!"
    assert IslGrammarSynthesizer.synthesize(["EMERGENCY"]) == "Emergency!"


def test_arbitrary_custom_sign():
    # Unknown word must NOT be dropped, and no hallucinated words added
    tokens = ["CUSTOMSIGN", "PLEASE"]
    res = IslGrammarSynthesizer.synthesize(tokens)
    assert res == "Please give customsign."


def test_sentence_buffer_pause():
    results = []
    buf = SentenceBuffer(sentence_pause_s=0.1, on_sentence_complete=lambda toks: results.append(toks))
    buf.push(GlossToken("HELLO", timestamp=100.0))
    buf.push(GlossToken("WORLD", timestamp=100.02))
    assert len(results) == 0

    buf.tick(now=100.05)
    assert len(results) == 0

    buf.tick(now=100.15)  # 130ms later -> fires!
    assert len(results) == 1
    assert [t.label for t in results[0]] == ["HELLO", "WORLD"]
    # Tokens persist for viewing until next sentence begins!
    assert [t.label for t in buf.peek()] == ["HELLO", "WORLD"]

    # Next sentence begins on new token push
    buf.push(GlossToken("NEXT", timestamp=100.20))
    assert [t.label for t in buf.peek()] == ["NEXT"]

    # Pop removes last token
    buf.push(GlossToken("WORD", timestamp=100.21))
    assert len(buf.peek()) == 2
    popped = buf.pop()
    assert popped.label == "WORD"
    assert len(buf.peek()) == 1


def test_no_hallucinated_need_or_and():
    """Verify that 'I need' is NEVER fabricated unless 'NEED' or 'WANT' was signed."""
    # Plain noun
    assert IslGrammarSynthesizer.synthesize(["WATER"]) == "Water."
    # List of nouns
    assert IslGrammarSynthesizer.synthesize(["WATER", "FOOD"]) == "Water and food."
    # Compound noun
    assert IslGrammarSynthesizer.synthesize(["TRAIN", "STATION"]) == "Train station."
    # Copula adjectives
    assert IslGrammarSynthesizer.synthesize(["I", "HAPPY"]) == "I am happy."
    assert IslGrammarSynthesizer.synthesize(["YOU", "BEAUTIFUL"]) == "You are beautiful."
    assert IslGrammarSynthesizer.synthesize(["TEA", "COLD"]) == "The tea is cold."
    # Greetings & idioms
    assert IslGrammarSynthesizer.synthesize(["GOOD", "MORNING"]) == "Good morning."
    assert IslGrammarSynthesizer.synthesize(["THANK", "YOU"]) == "Thank you."
    # Name introduction
    assert IslGrammarSynthesizer.synthesize(["MY", "NAME", "RAHUL"]) == "My name is Rahul."
    assert IslGrammarSynthesizer.synthesize(["MY_NAME_IS", "RAHUL"]) == "My name is Rahul."
    assert IslGrammarSynthesizer.synthesize(["MY_NAME_IS"]) == "My name is."
    # Fingerspelled letters
    assert IslGrammarSynthesizer.synthesize(["D", "O", "G"]) == "Dog."
    # Explicit NEED must still work
    assert IslGrammarSynthesizer.synthesize(["I", "NEED", "WATER"]) == "I need water."


def test_third_person_present():
    assert IslGrammarSynthesizer.synthesize(["HE", "GO"]) == "He goes."
    assert IslGrammarSynthesizer.synthesize(["SHE", "EAT", "FOOD"]) == "She eats food."
    assert IslGrammarSynthesizer.synthesize(["HE", "WORK"]) == "He works."


def test_wh_who_when_why():
    assert IslGrammarSynthesizer.synthesize(["WHO", "CALL"]) == "Who called?"
    assert IslGrammarSynthesizer.synthesize(["WHEN", "YOU", "COME"]) == "When will you come?"
    assert IslGrammarSynthesizer.synthesize(["WHY", "YOU", "GO"]) == "Why did you go?"


def test_tomorrow_off_and_temporal_states():
    # User's exact prompt requirement
    assert IslGrammarSynthesizer.synthesize(["TOMORROW", "OFF"]) == "Tomorrow is an off."
    assert IslGrammarSynthesizer.synthesize(["OFF", "TOMORROW"]) == "Tomorrow is an off."
    assert IslGrammarSynthesizer.synthesize(["TODAY", "HOLIDAY"]) == "Today is a holiday."
    assert IslGrammarSynthesizer.synthesize(["YESTERDAY", "OFF"]) == "Yesterday was an off."
    assert IslGrammarSynthesizer.synthesize(["SUNDAY", "OFF"]) == "Sunday is an off."


def test_location_state_and_medicine_needs():
    assert IslGrammarSynthesizer.synthesize(["OFFICE", "CLOSED"]) == "The office is closed."
    assert IslGrammarSynthesizer.synthesize(["STORE", "OPEN"]) == "The store is open."
    assert IslGrammarSynthesizer.synthesize(["FEVER", "MEDICINE"]) == "I need medicine for fever."
    assert IslGrammarSynthesizer.synthesize(["HEADACHE", "MEDICINE"]) == "I need medicine for headache."
    assert IslGrammarSynthesizer.synthesize(["I", "HOME"]) == "I am at home."
    assert IslGrammarSynthesizer.synthesize(["HE", "OFFICE"]) == "He is at the office."
    assert IslGrammarSynthesizer.synthesize(["WATER", "WANT"]) == "I want water."


def test_negation():
    assert IslGrammarSynthesizer.synthesize(["I", "NOT", "GO"]) == "I do not go."
    assert IslGrammarSynthesizer.synthesize(["I", "NOT", "GO", "TOMORROW"]) == "I will not go tomorrow."
    assert IslGrammarSynthesizer.synthesize(["FOOD", "NOT", "WANT"]) == "I do not want food."


def test_third_person_wh_questions():
    """Verify WH questions with third-person singular pronouns use 'does' and correct copula."""
    assert IslGrammarSynthesizer.synthesize(["WHERE", "HE", "GO"]) == "Where does he go?"
    assert IslGrammarSynthesizer.synthesize(["WHERE", "SHE", "WORK"]) == "Where does she work?"
    assert IslGrammarSynthesizer.synthesize(["WHAT", "HE", "WANT"]) == "What does he want?"
    assert IslGrammarSynthesizer.synthesize(["HOW", "IT", "WORK"]) == "How does it work?"
    assert IslGrammarSynthesizer.synthesize(["HOW", "HE"]) == "How is he?"
    assert IslGrammarSynthesizer.synthesize(["HOW", "THEY"]) == "How are they?"
    assert IslGrammarSynthesizer.synthesize(["WHERE", "HE"]) == "Where is he?"


def test_continuous_wh_questions():
    """Verify continuous participle questions use correct subject copula."""
    assert IslGrammarSynthesizer.synthesize(["WHERE", "HE", "GO"]) != "Where are he going?"
    assert IslGrammarSynthesizer.synthesize(["WHERE", "GO"]) == "Where are you going?"
    assert IslGrammarSynthesizer.synthesize(["WHERE", "I", "GO"]) == "Where do I go?"


def test_third_person_and_plural_statements():
    """Verify third-person and plural pronouns with symptoms and predicate nouns."""
    assert IslGrammarSynthesizer.synthesize(["HE", "CHEST", "PAIN"]) == "He has pain in his chest."
    assert IslGrammarSynthesizer.synthesize(["SHE", "HEAD", "PAIN"]) == "She has pain in her head."
    assert IslGrammarSynthesizer.synthesize(["THEY", "STOMACH", "PAIN"]) == "They have pain in their stomach."
    assert IslGrammarSynthesizer.synthesize(["HE", "FEVER"]) == "He has fever."
    assert IslGrammarSynthesizer.synthesize(["SHE", "SICK"]) == "She is sick."
    assert IslGrammarSynthesizer.synthesize(["THEY", "DOCTOR"]) == "They are doctors."
    assert IslGrammarSynthesizer.synthesize(["WE", "DOCTOR"]) == "We are doctors."


def test_empty_and_space_tokens():
    """Verify empty and space token handling."""
    assert IslGrammarSynthesizer.synthesize([]) == ""
    assert IslGrammarSynthesizer.synthesize(["SPACE"]) == ""
    assert IslGrammarSynthesizer.synthesize(["", " "]) == ""


def test_buffer_flush_idempotency():
    """Verify SentenceBuffer flush does not fire duplicate callbacks when already finalized."""
    fired = []
    buf = SentenceBuffer(on_sentence_complete=lambda toks: fired.append(toks))
    buf.push(GlossToken("HELLO", timestamp=10.0))
    buf.flush()
    assert len(fired) == 1
    buf.flush()  # Second flush should be idempotent
    assert len(fired) == 1


def test_noun_subject_wh_questions():
    """Verify WH questions with noun subjects are formulated with accurate agreement and do not drop nouns."""
    assert IslGrammarSynthesizer.synthesize(["WHEN", "TRAIN", "ARRIVE"]) == "When will the train arrive?"
    assert IslGrammarSynthesizer.synthesize(["WHERE", "BUS", "GO"]) == "Where does the bus go?"
    assert IslGrammarSynthesizer.synthesize(["WHY", "TRAIN", "STOP"]) == "Why did the train stop?"
    assert IslGrammarSynthesizer.synthesize(["WHAT", "DOCTOR", "SAY"]) == "What does the doctor say?"
    assert IslGrammarSynthesizer.synthesize(["HOW", "MACHINE", "WORK"]) == "How does the machine work?"
    assert IslGrammarSynthesizer.synthesize(["WHO", "YOU", "SEE"]) == "Who do you see?"
    assert IslGrammarSynthesizer.synthesize(["WHAT", "YOU", "DO", "TODAY"]) == "What do you do today?"


def test_buffer_pop_reset():
    """Verify pop resets last_token_time when the buffer is emptied."""
    buf = SentenceBuffer(sentence_pause_s=1.0)
    buf.push(GlossToken("HELLO", timestamp=10.0))
    assert buf.last_token_time == 10.0
    popped = buf.pop()
    assert popped is not None and popped.label == "HELLO"
    assert buf.last_token_time == 0.0
    assert buf.pop() is None



