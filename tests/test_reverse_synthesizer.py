import pytest
from signora.nlp.reverse_synthesizer import EnglishToIslSynthesizer


def test_svo_to_sov():
    res = EnglishToIslSynthesizer.translate("I want water")
    assert res.isl_glosses == ["I", "WATER", "WANT"]
    assert res.sign_cues[0].body_anchor == "CHEST_CENTER"
    assert res.sign_cues[1].body_anchor == "CHIN"
    assert res.sign_cues[2].body_anchor == "OPEN_SPACE"


def test_wh_question_movement():
    res = EnglishToIslSynthesizer.translate("Where is the hospital?")
    assert res.isl_glosses == ["HOSPITAL", "WHERE"]
    assert res.sign_cues[0].body_anchor == "CHEST_LEFT"


def test_wh_what_is_your_name():
    res = EnglishToIslSynthesizer.translate("What is your name?")
    assert res.isl_glosses == ["YOUR", "NAME", "WHAT"]


def test_temporal_fronting():
    res = EnglishToIslSynthesizer.translate("I will go to hospital tomorrow")
    assert res.isl_glosses == ["TOMORROW", "I", "HOSPITAL", "GO"]


def test_negation():
    res = EnglishToIslSynthesizer.translate("I do not understand")
    assert res.isl_glosses == ["I", "UNDERSTAND", "NOT"]


def test_contraction():
    res = EnglishToIslSynthesizer.translate("I can't come")
    assert res.isl_glosses == ["I", "COME", "NOT"]


def test_fingerspelling_fallback():
    res = EnglishToIslSynthesizer.translate("Meet Vaibhav")
    cue = next(c for c in res.sign_cues if c.gloss == "VAIBHAV")
    assert cue.is_fingerspelled is True
    assert cue.fingerspelling_letters == ["V", "A", "I", "B", "H", "A", "V"]


def test_empty():
    res = EnglishToIslSynthesizer.translate("   ")
    assert res.isl_glosses == []
