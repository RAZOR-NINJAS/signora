/* Sign Dino - an ASL fingerspelling game.
 *
 * The rule that keeps this playable: the world runs on one 60fps requestAnimationFrame
 * loop that never touches the camera and never waits for a detection. State comes from
 * /api/state at 10Hz. If the camera stutters, the scene does not.
 *
 * The scene is a Chrome-dino style runner. A pixel dinosaur runs toward a cactus.
 * When the cactus gets close the world pauses and the answer window opens: form the
 * target letter in time and the dino jumps the cactus; run out of time and it crashes.
 */

(() => {
  "use strict";

  const stage = document.getElementById("stage");
  const ctx = stage.getContext("2d");
  const overlay = document.getElementById("overlay");
  const ovTitle = document.getElementById("ov-title");
  const ovSub = document.getElementById("ov-sub");
  const ovHint = document.getElementById("ov-hint");
  const startBtn = document.getElementById("start-btn");
  const cam = document.getElementById("cam");
  const camOff = document.getElementById("cam-off");
  const camLetter = document.getElementById("cam-letter");
  const camFps = document.getElementById("cam-fps");
  const panel = document.getElementById("panel");

  // ---- world constants -------------------------------------------------

  const GROUND = 0.80;          // ground line, fraction of height
  const DINO_X = 0.12;          // dino's left edge, fraction of width
  const CACTUS_START = 1.15;    // cactus spawn, fraction of width
  const CACTUS_CLOSE = 0.34;    // where the cactus stops for the answer window
  const JUMP_MS = 620;          // how long the jump arc lasts

  const COL = {
    bg: "#f7f7f7",
    ink: "#535353",
    inkDim: "#9a9a9a",
    faint: "#dcdcdc",
    good: "#3f8f4f",
    bad: "#c0392b",
  };

  // ---- pixel sprites ---------------------------------------------------
  // 'X' is a filled cell. Rows are drawn on a grid of `unit` pixels, which is
  // what gives the whole scene its chunky Chrome-dino look.

  const DINO_BODY = [
    "         XXXXXXXX",
    "        XXXXXXXXXX",
    "        XXXXXXXXXX",
    "        XXXXXXXXXX",
    "        XXXX",
    "        XXXXXXXXXX",
    "        XXXXXXXXXX",
    "XX      XXXXXXXX",
    "XX     XXXXXXXXX",
    "XXX   XXXXXXXXXX",
    "XXXX XXXXXXXXXXX",
    "XXXXXXXXXXXXXXXX",
    " XXXXXXXXXXXXXXX",
    "  XXXXXXXXXXXXX",
    "   XXXXXXXXXXX",
    "    XXXXXXXXX",
    "     XXXXXXX",
  ];
  const DINO_LEGS_A = [
    "     XX XXX",
    "     XX  XX",
    "     XX  XX",
    "     XX",
    "    XXX",
  ];
  const DINO_LEGS_B = [
    "     XX XXX",
    "     XX  XX",
    "     XX  XX",
    "         XX",
    "        XXX",
  ];
  const DINO_LEGS_DEAD = [
    "     XX XXX",
    "     XX  XX",
    "    XXX  XX",
    "   XXX    XX",
    "  XXX      XX",
  ];
  const CACTUS = [
    "....XXX....",
    "....XXX....",
    "....XXX....",
    ".X..XXX..X.",
    ".X..XXX..X.",
    ".X..XXX..X.",
    ".XXXXXX..X.",
    "....XXX..X.",
    "....XXXXXX.",
    "....XXX....",
    "....XXX....",
    "....XXX....",
    "....XXX....",
    "....XXX....",
    "....XXX....",
    "....XXX....",
  ];

  // ---- precomputed draw runs ------------------------------------------
  // Every sprite row is reduced once to its runs of filled cells. The cells in
  // a run abut and overlap by 0.6px, so a single wider fillRect covers exactly
  // the same pixels as drawing them one by one. That removes about five sixths
  // of the per-frame sprite draw calls without changing a single pixel, which
  // is what keeps the 60fps loop cheap on a slow exhibition laptop.
  function bitmapRuns(rows) {
    const runs = [];
    const filled = (ch) => ch === "X" || ch === "#";
    for (let r = 0; r < rows.length; r++) {
      const row = rows[r];
      let c = 0;
      while (c < row.length) {
        if (!filled(row[c])) { c++; continue; }
        let end = c;
        while (end < row.length && filled(row[end])) end++;
        // flat triples: row, first column, last column (exclusive)
        runs.push(r, c, end);
        c = end;
      }
    }
    return runs;
  }

  const DINO_BODY_RUNS = bitmapRuns(DINO_BODY);
  const DINO_LEGS_A_RUNS = bitmapRuns(DINO_LEGS_A);
  const DINO_LEGS_B_RUNS = bitmapRuns(DINO_LEGS_B);
  const DINO_LEGS_DEAD_RUNS = bitmapRuns(DINO_LEGS_DEAD);
  const CACTUS_RUNS = bitmapRuns(CACTUS);

  // Drifting clouds as [xFraction, yFraction, scale]. Module scope so the
  // animation loop does not rebuild this array sixty times a second.
  const CLOUDS = [
    [0.15, 0.16, 1.0],
    [0.55, 0.10, 0.8],
    [0.85, 0.20, 1.2],
  ];

  // ---- state -----------------------------------------------------------

  let W = 0, H = 0, dpr = 1;
  let state = null;             // latest server snapshot
  let running = false;
  let cactus = { x: CACTUS_START };
  let groundScroll = 0;
  let shock = [];               // particles from a cleared cactus
  let playedMarks = [];         // letters cleared, drawn along the bottom
  let jumpStart = -1;
  let lastT = performance.now();

  // ---- helpers ---------------------------------------------------------

  const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);

  // Sprite cell size, cached per resize. The draw functions used to each
  // recompute it, four times a frame, for no reason.
  let U = 0;
  const unit = () => U;

  function resize() {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    W = window.innerWidth;
    H = window.innerHeight;
    stage.width = Math.floor(W * dpr);
    stage.height = Math.floor(H * dpr);
    stage.style.width = W + "px";
    stage.style.height = H + "px";
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    U = Math.min(W, H) * 0.009;
  }
  window.addEventListener("resize", resize);
  resize();

  function drawBitmap(runs, x, y, u, color) {
    ctx.fillStyle = color;
    for (let i = 0; i < runs.length; i += 3) {
      const r = runs[i];
      const c0 = runs[i + 1];
      ctx.fillRect(x + c0 * u, y + r * u, (runs[i + 2] - c0) * u + 0.6, u + 0.6);
    }
  }

  // ---- the dinosaur ----------------------------------------------------

  function drawDino(t, jumping, dead) {
    const u = unit();
    const groundY = GROUND * H;
    const hop = jumping ? Math.sin(clamp(jumping, 0, 1) * Math.PI) * H * 0.17 : 0;
    const x = DINO_X * W;
    const y = groundY - 22 * u - hop;

    // shadow on the ground, tightening as the dino leaves it
    const lift = clamp(hop / (H * 0.17), 0, 1);
    ctx.beginPath();
    ctx.ellipse(x + 9 * u, groundY, 9 * u * (1 - lift * 0.3), u * 1.1, 0, 0, Math.PI * 2);
    ctx.fillStyle = `rgba(83,83,83,${0.16 * (1 - lift * 0.5)})`;
    ctx.fill();

    drawBitmap(DINO_BODY_RUNS, x, y, u, COL.ink);

    if (dead) {
      drawBitmap(DINO_LEGS_DEAD_RUNS, x, y + 17 * u, u, COL.ink);
      // X eye
      ctx.strokeStyle = COL.bg;
      ctx.lineWidth = Math.max(1.5, u * 0.5);
      const ex = x + 13.5 * u, ey = y + 2.5 * u, s = u * 1.1;
      ctx.beginPath();
      ctx.moveTo(ex - s, ey - s); ctx.lineTo(ex + s, ey + s);
      ctx.moveTo(ex + s, ey - s); ctx.lineTo(ex - s, ey + s);
      ctx.stroke();
    } else {
      const frame = Math.floor(t * 10) % 2 === 0 ? DINO_LEGS_A_RUNS : DINO_LEGS_B_RUNS;
      drawBitmap(frame, x, y + 17 * u, u, COL.ink);
      // eye
      ctx.fillStyle = COL.bg;
      ctx.fillRect(x + 13 * u, y + 2 * u, u * 1.3, u * 1.3);
    }
  }

  // ---- the cactus ------------------------------------------------------

  function drawCactus() {
    const x = cactus.x * W;
    if (x < -0.15 * W) return;
    const u = unit();
    const y = GROUND * H - 16 * u;
    drawBitmap(CACTUS_RUNS, x, y, u, COL.ink);
  }

  // ---- ground and sky --------------------------------------------------

  function drawGround(scroll) {
    const y = GROUND * H;
    ctx.strokeStyle = COL.ink;
    ctx.lineWidth = Math.max(1.5, H * 0.0025);
    ctx.beginPath();
    ctx.moveTo(0, y);
    ctx.lineTo(W, y);
    ctx.stroke();

    // pebbles scrolling with the world
    const u = unit();
    ctx.fillStyle = COL.inkDim;
    const spacing = W * 0.16;
    const off = scroll % spacing;
    for (let i = -1; i * spacing - off < W + spacing; i++) {
      const px = i * spacing - off;
      const seed = ((i % 5) + 5) % 5;
      const py = y + u * (0.7 + (seed % 3) * 0.6);
      const pw = u * (0.9 + (seed % 2) * 0.7);
      ctx.fillRect(px, py, pw, u * 0.4);
    }
  }

  function drawClouds(t) {
    const u = unit();
    ctx.fillStyle = COL.faint;
    for (let i = 0; i < CLOUDS.length; i++) {
      const c = CLOUDS[i];
      const fx = c[0], fy = c[1], s = c[2];
      const x = ((fx * W + t * 8 * s) % (W + 240)) - 120;
      const y = fy * H;
      ctx.fillRect(x, y, u * 8 * s, u * 2 * s);
      ctx.fillRect(x + u * 2 * s, y - u * 2 * s, u * 5 * s, u * 2 * s);
      ctx.fillRect(x + u * 1 * s, y + u * 2 * s, u * 6 * s, u * 1.5 * s);
    }
  }

  // ---- HUD -------------------------------------------------------------

  const MONO = 'ui-monospace, "SF Mono", Menlo, Consolas, monospace';

  // Font strings rebuilt every frame is pure waste: the HUD sets the same four
  // sizes sixty times a second. Rebuild them only when the height actually
  // changes. The strings are byte-for-byte what the inline template made.
  let fontHi = "", fontTarget = "", fontCaption = "", fontMarks = "";
  let fontForHeight = -1;

  function ensureFonts() {
    const h = Math.round(H);
    if (h === fontForHeight) return;
    fontForHeight = h;
    fontHi = `700 ${Math.round(H * 0.022)}px ${MONO}`;
    fontTarget = `700 ${Math.round(H * 0.16)}px ${MONO}`;
    fontCaption = `600 ${Math.round(H * 0.02)}px ${MONO}`;
    fontMarks = `600 ${Math.round(H * 0.018)}px ${MONO}`;
  }

  // The zero-padded score only changes when the score does.
  let scoreFor = null;
  let scoreText = "00000";

  function drawHud() {
    if (!state) return;
    ensureFonts();
    const phase = state.phase;

    ctx.save();
    ctx.textBaseline = "top";

    // score, top right, monospace like the Chrome dino
    ctx.textAlign = "right";
    ctx.font = fontHi;
    ctx.fillStyle = COL.inkDim;
    ctx.fillText("HI", W - 22 - H * 0.075, 24);
    if (state.score !== scoreFor) {
      scoreFor = state.score;
      scoreText = String(state.score).padStart(5, "0");
    }
    ctx.fillStyle = COL.ink;
    ctx.fillText(scoreText, W - 22, 24);
    ctx.textAlign = "left";

    // target letter, big and centred
    if (phase === "ready" || phase === "answer") {
      const ready = phase === "ready";
      const cx = W / 2;
      const cy = H * 0.24;

      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.font = fontTarget;
      ctx.fillStyle = ready ? COL.inkDim : COL.ink;
      ctx.fillText(state.target || "?", cx, cy);

      // countdown bar under the letter
      const barW = W * 0.22;
      const barH = Math.max(6, H * 0.012);
      const bx = cx - barW / 2;
      const by = cy + H * 0.11;
      ctx.fillStyle = COL.faint;
      ctx.fillRect(bx, by, barW, barH);
      if (phase === "answer") {
        const p = clamp(state.progress, 0, 1);
        ctx.fillStyle = p > 0.72 ? COL.bad : COL.ink;
        ctx.fillRect(bx, by, barW * (1 - p), barH);
      }

      // caption
      ctx.font = fontCaption;
      ctx.fillStyle = COL.inkDim;
      const secs = (state.remaining_ms / 1000).toFixed(1);
      ctx.fillText(ready ? "get ready" : `${secs}s`, cx, by + barH + H * 0.03);

      // what the camera currently thinks, and whether it counts
      if (phase === "answer" && state.detected) {
        const good = state.detected === state.target;
        const strong = state.confidence >= (state.config.min_conf || 0);
        ctx.fillStyle = good && strong ? COL.good : COL.inkDim;
        ctx.fillText(
          strong ? `saw ${state.detected}` : "uncertain",
          cx,
          by + barH + H * 0.06
        );
      }
      ctx.textAlign = "left";
      ctx.textBaseline = "top";
    }

    // cleared letters accumulate along the bottom
    if (playedMarks.length) {
      ctx.font = fontMarks;
      ctx.fillStyle = COL.inkDim;
      ctx.fillText("CLEARED", 22, H - 46);
      ctx.fillStyle = COL.ink;
      ctx.fillText(playedMarks.join(" "), 22, H - 28);
    }
    ctx.restore();
  }

  // ---- feedback effects ------------------------------------------------

  function burst(x, y, color) {
    for (let i = 0; i < 26; i++) {
      const a = (Math.PI * 2 * i) / 26 + Math.random() * 0.3;
      const sp = 0.5 + Math.random() * 1.7;
      shock.push({
        x, y,
        vx: Math.cos(a) * sp,
        vy: Math.sin(a) * sp - 0.7,
        life: 1,
        r: 1.4 + Math.random() * 2.6,
        color,
      });
    }
  }

  function drawShock(dt) {
    for (let i = shock.length - 1; i >= 0; i--) {
      const p = shock[i];
      p.x += p.vx * dt * 190;
      p.y += p.vy * dt * 190;
      p.vy += dt * 3.2;
      p.life -= dt * 1.25;
      if (p.life <= 0) { shock.splice(i, 1); continue; }
      ctx.globalAlpha = clamp(p.life, 0, 1) * 0.85;
      ctx.fillStyle = p.color;
      ctx.beginPath();
      ctx.arc(p.x, p.y, p.r * p.life, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.globalAlpha = 1;
  }

  // ---- overlays --------------------------------------------------------

  function showOverlay(title, html, sub, btn, hint) {
    ovTitle.innerHTML = title;
    ovSub.innerHTML = sub;
    startBtn.textContent = btn;
    ovHint.innerHTML = hint || "";
    overlay.hidden = false;
  }

  function hideOverlay() {
    overlay.hidden = true;
  }

  function renderEnd(snap) {
    const target = snap.target;
    const lost = snap.misses[snap.misses.length - 1] || target;
    const got = snap.plays.length;
    const perfect = snap.misses.length === 0;
    const title = perfect
      ? `<span class="lit">Perfect</span> run`
      : got === 0
      ? `The dino <span class="lit">crashed</span>`
      : snap.cleared >= 12
      ? `A whole <span class="lit">stampede</span>`
      : `The dino <span class="lit">crashed</span>`;
    const sub = perfect
      ? `Every letter right. ${got} cactus${got === 1 ? "" : "es"} cleared with no mistake.`
      : `You cleared <strong>${got}</strong> cactus${got === 1 ? "" : "es"} before the crash.`;
    const hint = perfect
      ? `Try it with more letters, or a friend.`
      : `The letter you missed was <strong>${lost}</strong> &mdash; hold it steady next time.`;
    showOverlay(title, "", sub, "Play again", hint);
  }

  function renderIdle() {
    showOverlay(
      `Sign <span class="lit">Dino</span>`,
      "",
      "A dinosaur runs toward a cactus. When it stops, form the letter before the timer runs out &mdash; get it right and it jumps clear.",
      "Start",
      "Your camera picture sits in the corner. Form the shape before the timer runs out."
    );
  }

  // ---- network ---------------------------------------------------------

  async function post(path, body) {
    try {
      const res = await fetch(path, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body || {}),
      });
      return await res.json();
    } catch (e) {
      return { ok: false, error: String(e) };
    }
  }

  async function poll() {
    try {
      const res = await fetch("/api/state", { cache: "no-store" });
      const snap = await res.json();
      const prev = state;
      state = snap;

      if (snap.camera) {
        camFps.textContent = snap.camera.fps ? `${snap.camera.fps} fps` : "";
      }

      // react to transitions the server reported
      if (prev && prev.phase !== snap.phase) {
        onPhaseChange(prev.phase, snap);
      }
      if (!prev) {
        onPhaseChange(null, snap);
      }
    } catch (e) {
      /* server not up yet; the loop keeps drawing */
    }
    setTimeout(poll, 100);
  }

  function onPhaseChange(from, snap) {
    const phase = snap.phase;
    if (phase === "ready") {
      jumpStart = -1;
      cactus.x = CACTUS_START;
      beep("open");
    } else if (phase === "answer") {
      cactus.x = CACTUS_CLOSE;
      beep("tick");
    } else if (phase === "correct") {
      jumpStart = performance.now();
      playedMarks = snap.plays.slice();
      const gx = DINO_X * W + W * 0.05;
      const gy = GROUND * H - H * 0.16;
      burst(gx, gy, COL.good);
      beep("good");
    } else if (phase === "wrong") {
      beep("bad");
    } else if (phase === "finished") {
      const done = performance.now();
      for (let i = 0; i < snap.plays.length; i++) {
        setTimeout(() => {
          burst(
            W * 0.18 + i * (W * 0.055),
            GROUND * H - H * (0.10 + 0.06 * ((i * 7) % 5)),
            COL.good
          );
        }, i * 110);
      }
      setTimeout(() => running && renderEnd(snap), done && 900);
    }
  }

  // ---- sound -----------------------------------------------------------
  // WebAudio, generated. No asset files to lose on the exhibition machine.

  let actx = null;

  function ctxAudio() {
    if (!actx) {
      const AC = window.AudioContext || window.webkitAudioContext;
      if (AC) actx = new AC();
    }
    if (actx && actx.state === "suspended") actx.resume();
    return actx;
  }

  function tone(freq, dur, type, gain, when) {
    const a = ctxAudio();
    if (!a) return;
    const t0 = a.currentTime + (when || 0);
    const osc = a.createOscillator();
    const g = a.createGain();
    osc.type = type || "sine";
    osc.frequency.setValueAtTime(freq, t0);
    g.gain.setValueAtTime(0, t0);
    g.gain.linearRampToValueAtTime(gain == null ? 0.16 : gain, t0 + 0.012);
    g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
    osc.connect(g).connect(a.destination);
    osc.start(t0);
    osc.stop(t0 + dur + 0.05);
  }

  function speak(letter) {
    const a = ctxAudio();
    if (!a || !window.speechSynthesis) return;
    const u = new SpeechSynthesisUtterance(letter);
    u.rate = 0.95;
    u.pitch = 1.05;
    u.volume = 0.85;
    speechSynthesis.cancel();
    speechSynthesis.speak(u);
  }

  function beep(kind) {
    if (kind === "good") {
      tone(523.25, 0.16, "triangle", 0.16);
      tone(783.99, 0.26, "triangle", 0.12, 0.08);
      if (state && state.target) speak(state.target);
    } else if (kind === "bad") {
      tone(196, 0.42, "sawtooth", 0.10);
      tone(146.83, 0.52, "sawtooth", 0.09, 0.06);
    } else if (kind === "open") {
      tone(659.25, 0.13, "sine", 0.10);
      tone(987.77, 0.18, "sine", 0.07, 0.07);
    } else if (kind === "tick") {
      tone(880, 0.07, "sine", 0.07);
    }
  }

  // ---- camera ----------------------------------------------------------

  /* The camera belongs to the server process, not the browser: only one process
   can open a webcam, and the server needs it for detection. So the corner
   window shows frames the server sends, refreshed a few times a second. */
  let previewTimer = null;
  let previewImg = null;

  function startPreview() {
    camOff.hidden = true;
    if (previewTimer) return;
    // One Image reused for every poll. Allocating a fresh one roughly nine
    // times a second churns the decoder and the garbage collector for nothing.
    if (!previewImg) previewImg = new Image();
    const img = previewImg;
    const tick = () => {
      img.onload = () => { cam.src = img.src; };
      img.onerror = () => {
        camOff.hidden = false;
        camOff.textContent = "waiting for the camera…";
      };
      img.src = `/api/preview.jpg?t=${Date.now()}`;
    };
    tick();
    previewTimer = setInterval(tick, 110);
  }

  function stopPreview() {
    if (previewTimer) { clearInterval(previewTimer); previewTimer = null; }
    camOff.hidden = false;
    camOff.textContent = "camera off — press start";
  }

  // ---- main loop -------------------------------------------------------

  function frame(now) {
    const dt = Math.min((now - lastT) / 1000, 0.05);
    lastT = now;
    const t = now / 1000;

    // sky
    ctx.fillStyle = COL.bg;
    ctx.fillRect(0, 0, W, H);
    drawClouds(t);

    // the world only scrolls while the dino is travelling
    if (running && state && (state.phase === "ready" || state.phase === "correct")) {
      groundScroll += dt * W * 0.35;
    }

    // cactus position, driven by the phase
    if (running && state) {
      if (state.phase === "ready") {
        const readyS = Math.max(0.2, (state.config.ready_ms || 2200) / 1000);
        const speed = (CACTUS_START - CACTUS_CLOSE) / readyS;
        cactus.x = Math.max(CACTUS_CLOSE, cactus.x - speed * dt);
      } else if (state.phase === "answer") {
        cactus.x = CACTUS_CLOSE;
      } else if (state.phase === "correct") {
        cactus.x -= dt * 0.9;
      }
    }

    drawGround(groundScroll);

    const jumping = jumpStart > 0 ? (now - jumpStart) / JUMP_MS : 0;
    const dead = !!(state && state.phase === "wrong");

    drawCactus();
    drawDino(t, jumping, dead);
    drawShock(dt);
    drawHud();

    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);

  // ---- controls --------------------------------------------------------

  startBtn.addEventListener("click", async () => {
    startBtn.disabled = true;
    ctxAudio();
    const camRes = await post("/api/camera", { on: true });
    if (camRes.ok) startPreview();
    else {
      camOff.hidden = false;
      camOff.textContent = camRes.message || "camera unavailable";
    }
    const res = await post("/api/start");
    if (res.ok) {
      running = true;
      playedMarks = [];
      cactus = { x: CACTUS_START };
      groundScroll = 0;
      hideOverlay();
    } else {
      showOverlay(
        `Cannot <span class="lit">start</span>`,
        "",
        res.error || "unknown problem",
        "Try again",
        "Check the terminal for details."
      );
    }
    startBtn.disabled = false;
  });

  document.getElementById("settings-btn").addEventListener("click", () => {
    panel.classList.toggle("show");
  });

  document.getElementById("apply-btn").addEventListener("click", async () => {
    await post("/api/settings", {
      ready_ms: +document.getElementById("s-ready").value,
      answer_ms: +document.getElementById("s-answer").value,
      hold_ms: +document.getElementById("s-hold").value,
      max_cacti: +document.getElementById("s-cacti").value,
    });
  });

  const sliders = [
    ["s-ready", "v-ready", "ready_ms", (v) => `${v} ms`],
    ["s-answer", "v-answer", "answer_ms", (v) => `${v} ms`],
    ["s-hold", "v-hold", "hold_ms", (v) => `${v} ms`],
    ["s-cacti", "v-cacti", "max_cacti", (v) => (v === 0 ? "endless" : String(v))],
  ];

  function syncSliders(cfg) {
    if (!cfg) return;
    for (const [sid, vid, key, fmt] of sliders) {
      const el = document.getElementById(sid);
      const lab = document.getElementById(vid);
      if (!el) continue;
      // Writing an unchanged .value still costs layout work, and this runs
      // seven times a second for the whole panel.
      const v = cfg[key];
      if (document.activeElement !== el && +el.value !== +v) el.value = v;
      if (lab) lab.textContent = fmt(v);
    }
  }

  for (const [sid, vid, , fmt] of sliders) {
    const el = document.getElementById(sid);
    const lab = document.getElementById(vid);
    if (!el) continue;
    el.addEventListener("input", () => {
      lab.textContent = fmt(+el.value);
    });
  }

  // keep the corner readout fresh even when nothing else changes
  setInterval(() => {
    if (!state) return;
    syncSliders(state.config);
    if (state.detected) {
      const strong = state.confidence >= (state.config.min_conf || 0);
      camLetter.textContent = `${state.detected}${strong ? "" : " ?"}`;
      camLetter.style.color = strong ? COL.ink : COL.inkDim;
    } else {
      camLetter.textContent = "—";
      camLetter.style.color = COL.inkDim;
    }
  }, 140);

  fetch("/api/config").then((r) => r.json()).then(syncSliders).catch(() => {});
  poll();
  renderIdle();

  // show the end card whenever the server ends a run
  setInterval(() => {
    if (state && (state.phase === "wrong" || state.phase === "finished")) {
      if (overlay.hidden && running) {
        running = false;
        renderEnd(state);
      }
    }
  }, 200);
})();
