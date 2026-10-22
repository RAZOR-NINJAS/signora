---
title: "Command: pytest commands"
tags:
  - cmd/testing
aliases:
  - pytest commands
  - pytest
  - test
---

# `pytest commands`

> [!INFO] Summary
> **Automated Test Suite Runner**. Executes 166 unit and integration test cases covering debounce timing, NLP grammar reconstruction, phone webcam bridging, whole-body spatial anchors, and Piper neural speech synthesis.

---

## 1. Syntax

Execute with the virtual environment activated:
```bash
./.venv/bin/pytest [TARGETS] [OPTIONS]
```

---

## 2. Common Test Commands

### Run Entire Test Suite (166 Tests)
```bash
./.venv/bin/pytest tests/
```

### Run Debounce & Fluid Timing Tests
```bash
./.venv/bin/pytest tests/test_core_debounce.py
```

### Run Zero-Hallucination NLP Grammar Tests
```bash
./.venv/bin/pytest tests/test_nlp_synthesizer.py
```

### Run Phone Webcam Bridge Tests
```bash
./.venv/bin/pytest tests/test_phone.py
```

### Run ISL Topology & Body Spatial Tests
```bash
./.venv/bin/pytest tests/test_isl.py tests/test_body.py
```

### Run Audio & Speech Synthesis Tests
```bash
./.venv/bin/pytest tests/test_tts.py tests/test_alert.py
```

---

## 3. Useful Pytest Flags

| Flag | Purpose | Example |
|---|---|---|
| `-v` | Verbose mode (displays individual test names) | `pytest -v tests/test_core_debounce.py` |
| `-k <EXPR>` | Filter tests by name pattern | `pytest -k "dwell or apex"` |
| `--tb=short` | Shorter tracebacks on failures | `pytest --tb=short tests/` |
| `-x` | Stop immediately after first failure | `pytest -x tests/` |
| `--lf` | Run only tests that failed in the last run | `pytest --lf` |

---

## 4. Related Commands
- [[signora doctor]] — System environment and hardware check.
- [[signora isl]] — Test ISL live in camera window.
