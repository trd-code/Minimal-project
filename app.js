(() => {
  "use strict";

  const canvas = document.getElementById("board");
  const ctx = canvas.getContext("2d");

  // ---- State ----
  const COLORS = ["#111827", "#ef4444", "#f59e0b", "#22c55e", "#3b82f6", "#a855f7", "#ffffff"];
  const BG = {
    white: { fill: "#ffffff", grid: false },
    dark:  { fill: "#1f2937", grid: false },
    grid:  { fill: "#ffffff", grid: true },
  };

  const state = {
    tool: "pen",
    color: "#111827",
    size: 4,
    pages: [],        // each: { strokes: [...], undone: [...], bg: "white" }
    current: 0,
    drawing: false,
  };

  // ---- Persistence ----
  const STORAGE_KEY = "drawboard_v1";

  function save() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({
        pages: state.pages,
        current: state.current,
      }));
    } catch (e) { /* storage full or unavailable */ }
  }

  function load() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const data = JSON.parse(raw);
        if (data.pages && data.pages.length) {
          state.pages = data.pages.map(p => ({
            strokes: p.strokes || [],
            undone: [],
            bg: p.bg || "white",
          }));
          state.current = Math.min(data.current || 0, state.pages.length - 1);
          return;
        }
      }
    } catch (e) { /* ignore corrupt data */ }
    newPage();
  }

  function page() { return state.pages[state.current]; }

  function newPage(bg = "white") {
    state.pages.push({ strokes: [], undone: [], bg });
  }

  // ---- Canvas sizing (high DPI) ----
  let dpr = 1, W = 0, H = 0;

  function resize() {
    dpr = window.devicePixelRatio || 1;
    W = window.innerWidth;
    H = window.innerHeight;
    canvas.width = Math.floor(W * dpr);
    canvas.height = Math.floor(H * dpr);
    canvas.style.width = W + "px";
    canvas.style.height = H + "px";
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    redraw();
  }

  // ---- Drawing ----
  function drawStroke(s) {
    const pts = s.points;
    if (!pts.length) return;

    ctx.save();
    ctx.lineJoin = "round";
    ctx.lineCap = "round";

    if (s.tool === "eraser") {
      ctx.globalCompositeOperation = "destination-out";
      ctx.strokeStyle = "rgba(0,0,0,1)";
      ctx.globalAlpha = 1;
    } else {
      ctx.globalCompositeOperation = "source-over";
      ctx.strokeStyle = s.color;
      ctx.globalAlpha = s.tool === "marker" ? 0.4 : 1;
    }
    ctx.lineWidth = s.size;

    if (pts.length === 1) {
      // a dot
      ctx.beginPath();
      ctx.arc(pts[0].x, pts[0].y, s.size / 2, 0, Math.PI * 2);
      ctx.fillStyle = s.tool === "eraser" ? "rgba(0,0,0,1)" : s.color;
      ctx.globalAlpha = s.tool === "marker" ? 0.4 : (s.tool === "eraser" ? 1 : 1);
      ctx.fill();
    } else {
      ctx.beginPath();
      ctx.moveTo(pts[0].x, pts[0].y);
      for (let i = 1; i < pts.length - 1; i++) {
        const midX = (pts[i].x + pts[i + 1].x) / 2;
        const midY = (pts[i].y + pts[i + 1].y) / 2;
        ctx.quadraticCurveTo(pts[i].x, pts[i].y, midX, midY);
      }
      const last = pts[pts.length - 1];
      ctx.lineTo(last.x, last.y);
      ctx.stroke();
    }
    ctx.restore();
  }

  function paintBackground() {
    const conf = BG[page().bg] || BG.white;
    ctx.save();
    ctx.globalCompositeOperation = "source-over";
    ctx.fillStyle = conf.fill;
    ctx.fillRect(0, 0, W, H);
    if (conf.grid) {
      const gap = 28;
      ctx.strokeStyle = "rgba(0,0,0,0.08)";
      ctx.lineWidth = 1;
      ctx.beginPath();
      for (let x = gap; x < W; x += gap) { ctx.moveTo(x, 0); ctx.lineTo(x, H); }
      for (let y = gap; y < H; y += gap) { ctx.moveTo(0, y); ctx.lineTo(W, y); }
      ctx.stroke();
    }
    ctx.restore();
  }

  function redraw() {
    ctx.clearRect(0, 0, W, H);
    paintBackground();
    for (const s of page().strokes) drawStroke(s);
  }

  // ---- Pointer handling ----
  let active = null;

  function pointerPos(e) {
    return { x: e.clientX, y: e.clientY };
  }

  function onDown(e) {
    if (state.drawing) return;
    // only draw with primary contact
    e.preventDefault();
    state.drawing = true;
    active = {
      tool: state.tool,
      color: state.color,
      size: state.tool === "marker" ? state.size * 2.2
           : state.tool === "eraser" ? state.size * 3
           : state.size,
      points: [pointerPos(e)],
    };
    page().undone = [];
    try { canvas.setPointerCapture(e.pointerId); } catch (_) {}
  }

  function onMove(e) {
    if (!state.drawing || !active) return;
    e.preventDefault();
    // coalesced events for smoother lines
    const evs = e.getCoalescedEvents ? e.getCoalescedEvents() : [e];
    for (const ev of evs) active.points.push(pointerPos(ev));
    // incremental draw for responsiveness
    redraw();
    drawStroke(active);
  }

  function onUp(e) {
    if (!state.drawing || !active) return;
    e.preventDefault();
    state.drawing = false;
    page().strokes.push(active);
    active = null;
    redraw();
    save();
  }

  canvas.addEventListener("pointerdown", onDown);
  canvas.addEventListener("pointermove", onMove);
  canvas.addEventListener("pointerup", onUp);
  canvas.addEventListener("pointercancel", onUp);
  canvas.addEventListener("pointerleave", onUp);

  // ---- UI wiring ----
  const $ = (id) => document.getElementById(id);

  function updatePageLabel() {
    $("pageLabel").textContent = `${state.current + 1} / ${state.pages.length}`;
  }

  // Tools
  document.querySelectorAll(".tool").forEach(btn => {
    btn.addEventListener("click", () => {
      document.querySelectorAll(".tool").forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      state.tool = btn.dataset.tool;
    });
  });

  // Swatches
  const swatchWrap = $("swatches");
  COLORS.forEach((c, i) => {
    const el = document.createElement("div");
    el.className = "swatch" + (i === 0 ? " active" : "");
    el.style.background = c;
    el.addEventListener("click", () => {
      document.querySelectorAll(".swatch").forEach(s => s.classList.remove("active"));
      el.classList.add("active");
      state.color = c;
      // picking a color implies drawing, not erasing
      if (state.tool === "eraser") {
        state.tool = "pen";
        document.querySelectorAll(".tool").forEach(b =>
          b.classList.toggle("active", b.dataset.tool === "pen"));
      }
    });
    swatchWrap.appendChild(el);
  });

  // Size
  $("sizeRange").addEventListener("input", (e) => {
    state.size = parseInt(e.target.value, 10);
  });

  // Undo / Redo
  $("undoBtn").addEventListener("click", () => {
    const p = page();
    if (p.strokes.length) {
      p.undone.push(p.strokes.pop());
      redraw();
      save();
    }
  });
  $("redoBtn").addEventListener("click", () => {
    const p = page();
    if (p.undone.length) {
      p.strokes.push(p.undone.pop());
      redraw();
      save();
    }
  });

  // Clear current page
  $("clearBtn").addEventListener("click", () => {
    const p = page();
    if (p.strokes.length && confirm("ล้างสไลด์นี้ทั้งหมด?")) {
      p.strokes = [];
      p.undone = [];
      redraw();
      save();
    }
  });

  // Pages
  $("prevPage").addEventListener("click", () => {
    if (state.current > 0) { state.current--; updatePageLabel(); redraw(); save(); }
  });
  $("nextPage").addEventListener("click", () => {
    if (state.current < state.pages.length - 1) { state.current++; updatePageLabel(); redraw(); save(); }
  });
  $("addPage").addEventListener("click", () => {
    newPage(page().bg);
    state.current = state.pages.length - 1;
    updatePageLabel();
    redraw();
    save();
  });

  // Save as image
  $("saveBtn").addEventListener("click", () => {
    const a = document.createElement("a");
    a.download = `สไลด์-${state.current + 1}.png`;
    a.href = canvas.toDataURL("image/png");
    a.click();
  });

  // Menu
  const menu = $("menuSheet");
  const openMenu = () => menu.classList.remove("hidden");
  const closeMenu = () => menu.classList.add("hidden");
  $("menuBtn").addEventListener("click", openMenu);
  $("closeMenu").addEventListener("click", closeMenu);
  menu.addEventListener("click", (e) => { if (e.target === menu) closeMenu(); });

  function setBg(name) {
    page().bg = name;
    redraw();
    save();
    closeMenu();
  }
  $("bgWhite").addEventListener("click", () => setBg("white"));
  $("bgDark").addEventListener("click", () => setBg("dark"));
  $("bgGrid").addEventListener("click", () => setBg("grid"));

  $("deletePage").addEventListener("click", () => {
    if (state.pages.length <= 1) {
      page().strokes = [];
      page().undone = [];
    } else {
      state.pages.splice(state.current, 1);
      state.current = Math.max(0, state.current - 1);
    }
    updatePageLabel();
    redraw();
    save();
    closeMenu();
  });

  $("clearAll").addEventListener("click", () => {
    if (confirm("ล้างทุกสไลด์และเริ่มใหม่?")) {
      state.pages = [];
      newPage();
      state.current = 0;
      updatePageLabel();
      redraw();
      save();
    }
    closeMenu();
  });

  // ---- Init ----
  window.addEventListener("resize", resize);
  window.addEventListener("orientationchange", () => setTimeout(resize, 200));
  // prevent iOS double-tap zoom / pull to refresh
  document.addEventListener("gesturestart", (e) => e.preventDefault());
  document.body.addEventListener("touchmove", (e) => {
    if (e.target === canvas) e.preventDefault();
  }, { passive: false });

  load();
  updatePageLabel();
  resize();

  // Service worker for offline / installable app
  if ("serviceWorker" in navigator) {
    window.addEventListener("load", () => {
      navigator.serviceWorker.register("sw.js").catch(() => {});
    });
  }
})();
