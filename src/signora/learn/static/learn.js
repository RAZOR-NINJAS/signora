const SIGN_TIPS = {
  A: "Form a fist with thumb straight up resting against the outer side of the index finger.",
  B: "Hold 4 fingers straight up touching together, with your thumb folded across the palm.",
  C: "Curve all fingers and thumb to form the shape of a letter 'C'.",
  D: "Point index finger straight up, while thumb touches the tips of middle, ring, and pinky.",
  E: "Curl all four fingers downward into your palm, with thumb folded flat beneath them.",
  F: "Touch thumb and index fingertips together into a circle; extend remaining 3 fingers upright.",
  G: "Point index finger and thumb horizontally forward in parallel, like a gentle pinch.",
  H: "Point index and middle fingers horizontally forward together, thumb tucked below.",
  I: "Make a fist and extend only your pinky finger straight up.",
  J: "Start with 'I' handshape, then trace the letter 'J' curve downward in the air.",
  K: "Point index finger up, middle finger angled forward, with thumb nestled between them.",
  L: "Extend thumb and index finger to form an 'L' shape; curl other three fingers into palm.",
  M: "Tuck thumb beneath three fingers (index, middle, ring) showing pinky on the far side.",
  N: "Tuck thumb beneath two fingers (index and middle) with remaining fingers curled.",
  O: "Curve all finger tips to meet the thumb tip, forming an 'O' circle.",
  P: "Same shape as 'K', but point your hand downwards toward the ground.",
  Q: "Same shape as 'G', but point index finger and thumb downwards.",
  R: "Cross your middle finger over your index finger, with other fingers tucked into palm.",
  S: "Make a tight fist with your thumb wrapped horizontally across the front of your fingers.",
  T: "Make a fist with your thumb tucked directly between index and middle fingers.",
  U: "Extend index and middle fingers straight up together, thumb crossed over ring finger.",
  V: "Extend index and middle fingers upright spread apart to form a 'V' peace sign.",
  W: "Extend index, middle, and ring fingers upright in a 'W' shape, thumb holding pinky down.",
  X: "Make a fist and hook your index finger like a small curved hook or claw.",
  Y: "Extend thumb and pinky finger outward (shaka sign), keeping middle three fingers curled.",
  Z: "Extend index finger and trace the letter 'Z' zigzag horizontally in the air."
};

let currentMode = "drill";
let currentTarget = "A";
let pollTimer = null;
let cameraOn = true;
let isHolding = false;
let holdStartTime = null;
const HOLD_DURATION_MS = 600;

document.addEventListener("DOMContentLoaded", () => {
  buildAlphabetGrid();
  startPolling();
});

function buildAlphabetGrid() {
  const grid = document.getElementById("alphabet-grid");
  grid.innerHTML = "";
  const letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".split("");
  letters.forEach(letter => {
    const btn = document.createElement("button");
    btn.className = `grid-letter-btn ${letter === currentTarget ? "active" : ""}`;
    btn.id = `btn-letter-${letter}`;
    btn.textContent = letter;
    btn.onclick = () => selectLetter(letter);
    grid.appendChild(btn);
  });
}

function selectLetter(letter) {
  currentTarget = letter;
  updateTargetDisplay(letter);
  fetch("/api/set_target", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ target: letter })
  });
  updateActiveGridButton(letter);
}

function updateActiveGridButton(letter) {
  document.querySelectorAll(".grid-letter-btn").forEach(b => b.classList.remove("active"));
  const btn = document.getElementById(`btn-letter-${letter}`);
  if (btn) btn.classList.add("active");
}

function updateTargetDisplay(letter) {
  document.getElementById("target-symbol").textContent = letter;
  document.getElementById("target-tip").textContent = SIGN_TIPS[letter] || "Practice this letter shape.";
  resetFeedback();
}

function resetFeedback() {
  const banner = document.getElementById("feedback-banner");
  banner.className = "feedback-banner idle";
  document.getElementById("feedback-icon").textContent = "👁";
  document.getElementById("feedback-text").textContent = "Position your hand in camera view";
  isHolding = false;
  holdStartTime = null;
}

function setMode(mode) {
  currentMode = mode;
  document.getElementById("btn-mode-drill").classList.toggle("active", mode === "drill");
  document.getElementById("btn-mode-quiz").classList.toggle("active", mode === "quiz");
  document.getElementById("quiz-timer").classList.toggle("hidden", mode !== "quiz");
  document.getElementById("challenge-badge").textContent = mode === "quiz" ? "TIMED QUIZ" : "PRACTICE DRILL";

  fetch("/api/mode", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ mode })
  });
}

function nextRandomSign() {
  fetch("/api/next", { method: "POST" })
    .then(r => r.json())
    .then(d => {
      if (d.ok && d.target) {
        currentTarget = d.target;
        updateTargetDisplay(d.target);
        updateActiveGridButton(d.target);
      }
    });
}

function skipSign() {
  nextRandomSign();
}

function toggleCamera() {
  cameraOn = !cameraOn;
  fetch("/api/camera", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ on: cameraOn })
  });
}

function startPolling() {
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = setInterval(pollState, 150);
}

function pollState() {
  fetch("/api/state")
    .then(r => r.json())
    .then(data => {
      // Update stats
      document.getElementById("stat-score").textContent = data.score;
      document.getElementById("stat-streak").textContent = `🔥 ${data.streak}`;

      // Update quiz timer
      if (data.mode === "quiz") {
        document.getElementById("quiz-seconds").textContent = data.quiz_remaining;
      }

      // Update target if server synchronized
      if (data.target && data.target !== currentTarget) {
        currentTarget = data.target;
        updateTargetDisplay(data.target);
        updateActiveGridButton(data.target);
      }

      // Update camera preview image and indicators
      const camFeed = document.getElementById("cam-feed");
      if (data.camera && data.camera.on) {
        camFeed.src = `/api/preview.jpg?t=${Date.now()}`;
        document.getElementById("cam-status").textContent = "Camera Active";
        document.getElementById("cam-status").style.color = "var(--accent-green)";
        document.getElementById("fps-indicator").textContent = `${data.camera.fps} FPS`;
      } else {
        document.getElementById("cam-status").textContent = "Camera Off";
        document.getElementById("cam-status").style.color = "var(--accent-rose)";
      }

      // Update detection badge and confidence meter
      const detected = data.detected || "—";
      const conf = data.confidence || 0.0;
      document.getElementById("detected-badge").textContent = detected;
      document.getElementById("conf-bar-fill").style.width = `${Math.round(conf * 100)}%`;

      // Check match with target
      evaluateMatch(detected, conf, data.target);
    })
    .catch(() => {});
}

function evaluateMatch(detected, conf, target) {
  const banner = document.getElementById("feedback-banner");
  const icon = document.getElementById("feedback-icon");
  const text = document.getElementById("feedback-text");

  if (!detected || detected === "—" || conf < 0.5) {
    if (!isHolding) {
      banner.className = "feedback-banner idle";
      icon.textContent = "👁";
      text.textContent = "Hold your hand up to the camera";
    }
    isHolding = false;
    holdStartTime = null;
    return;
  }

  if (detected === target) {
    const now = Date.now();
    if (!isHolding) {
      isHolding = true;
      holdStartTime = now;
      banner.className = "feedback-banner holding";
      icon.textContent = "⏳";
      text.textContent = `Holding sign ${target}... keep steady`;
    } else if (now - holdStartTime >= HOLD_DURATION_MS) {
      banner.className = "feedback-banner success";
      icon.textContent = "✓";
      text.textContent = `Correct! Perfect ${target} sign!`;
      isHolding = false;
      holdStartTime = null;

      // Report success to backend
      fetch("/api/success", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ detected: target })
      })
        .then(r => r.json())
        .then(res => {
          if (res.ok && res.next) {
            setTimeout(() => {
              currentTarget = res.next;
              updateTargetDisplay(res.next);
              updateActiveGridButton(res.next);
            }, 600);
          }
        });
    }
  } else {
    isHolding = false;
    holdStartTime = null;
    banner.className = "feedback-banner idle";
    icon.textContent = "⚠️";
    text.textContent = `Detected ${detected} (${Math.round(conf * 100)}%). Adjust toward ${target}.`;
  }
}
