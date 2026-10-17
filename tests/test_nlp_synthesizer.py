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


def test_third_person_present():
    assert IslGrammarSynthesizer.synthesize(["HE", "GO"]) == "He goes."
    assert IslGrammarSynthesizer.synthesize(["SHE", "EAT", "FOOD"]) == "She eats food."
    assert IslGrammarSynthesizer.synthesize(["HE", "WORK"]) == "He works."


def test_wh_who_when_why():
    assert IslGrammarSynthesizer.synthesize(["WHO", "CALL"]) == "Who called?"
    assert IslGrammarSynthesizer.synthesize(["WHEN", "YOU", "COME"]) == "When will you come?"
    assert IslGrammarSynthesizer.synthesize(["WHY", "YOU", "GO"]) == "Why did you go?"

