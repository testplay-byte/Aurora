/* ==========================================================================
   Aurora Web UI — shared helpers (app.js)
   API client, toasts, settings merge-save, palette editor, secret gate.
   ========================================================================== */

const App = (() => {
  // --- toast ------------------------------------------------------------
  let toastTimer = null;
  function toast(msg, ok = true) {
    let el = document.getElementById("toast");
    if (!el) {
      el = document.createElement("div");
      el.id = "toast";
      document.body.appendChild(el);
    }
    el.textContent = msg;
    el.classList.toggle("err", !ok);
    el.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove("show"), 2200);
  }

  // --- fetch helpers ------------------------------------------------------
  async function get(url) {
    const res = await fetch(url, { cache: "no-store" });
    if (!res.ok) throw new Error(`${url} → ${res.status}`);
    const type = res.headers.get("content-type") || "";
    return type.includes("json") ? res.json() : res.text();
  }

  async function post(url, body) {
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: body === undefined ? "{}" : JSON.stringify(body),
    });
    const text = await res.text();
    if (!res.ok) throw new Error(text || `HTTP ${res.status}`);
    return text;
  }

  // --- settings (GET once, PATCH-style merge POST) ------------------------
  let cached = null;
  async function loadSettings() {
    cached = await get("/api/settings");
    return cached;
  }
  async function patchSettings(partial) {
    await post("/api/settings", partial);
  }

  // --- color helpers -------------------------------------------------------
  const hex2rgb = (h) => {
    h = h.replace("#", "");
    return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
  };
  const rgb2hex = (r, g, b) =>
    "#" + [r, g, b].map((v) => Math.max(0, Math.min(255, v | 0)).toString(16).padStart(2, "0")).join("");

  /* ----------------------------------------------------------------------
     Palette editor — one shared implementation for every mode page.
     opts: { mount, colors: [[r,g,b]…], max, fixed, onChange(colors) }
       max  = max entries (cap "add")
       fixed= slots cannot be removed (SPIRAL: exactly 4)
     Colors are edited in place; onChange receives the full array.
     ---------------------------------------------------------------------- */
  function palette(opts) {
    const { mount, max = 8, fixed = false, onChange } = opts;
    let colors = opts.colors.map((c) => c.slice(0, 3));

    function emit() { onChange(colors.map((c) => c.slice(0, 3))); }

    function render() {
      mount.innerHTML = "";
      colors.forEach((c, i) => {
        const sw = document.createElement("div");
        sw.className = "swatch" + (fixed ? " fixed" : "");
        sw.style.background = `rgb(${c[0]},${c[1]},${c[2]})`;

        const inp = document.createElement("input");
        inp.type = "color";
        inp.value = rgb2hex(c[0], c[1], c[2]);
        inp.addEventListener("input", () => {
          const [r, g, b] = hex2rgb(inp.value);
          colors[i] = [r, g, b];
          sw.style.background = `rgb(${r},${g},${b})`;
          emit();
        });
        sw.appendChild(inp);

        if (!fixed && colors.length > 1) {
          const kill = document.createElement("button");
          kill.className = "kill";
          kill.textContent = "×";
          kill.title = "Remove color";
          kill.addEventListener("click", (e) => {
            e.preventDefault();
            e.stopPropagation();
            colors.splice(i, 1);
            render();
            emit();
          });
          sw.appendChild(kill);
        }
        mount.appendChild(sw);
      });

      if (!fixed && colors.length < max) {
        const add = document.createElement("button");
        add.className = "add";
        add.textContent = "+";
        add.title = "Add color";
        add.addEventListener("click", () => {
          colors.push(colors[colors.length - 1].slice(0, 3));
          render();
          emit();
        });
        mount.appendChild(add);
      }
    }
    render();
    return { get: () => colors.map((c) => c.slice(0, 3)) };
  }

  /* ----------------------------------------------------------------------
     Secret gate: `count` clicks on `el` within sliding `windowMs` windows
     opens `target` (v1 behavior: 10 clicks on STOP).
     ---------------------------------------------------------------------- */
  function secretGate(el, target, count = 10, windowMs = 2000) {
    let clicks = 0, first = 0;
    el.addEventListener("click", () => {
      const now = Date.now();
      if (now - first > windowMs) { clicks = 0; first = now; }
      clicks++;
      if (clicks >= count) {
        clicks = 0;
        window.open(target, "_blank");
      }
    });
  }

  // --- tiny DOM helpers ----------------------------------------------------
  const $ = (sel) => document.querySelector(sel);
  function bindToggle(id, onChange) {
    const el = document.getElementById(id);
    if (el) el.addEventListener("change", () => onChange(el.checked));
  }
  function bindSlider(id, onChange) {
    const el = document.getElementById(id);
    if (el) el.addEventListener("input", () => onChange(+el.value));
  }

  return { toast, get, post, loadSettings, patchSettings, palette,
           hex2rgb, rgb2hex, secretGate, $, bindToggle, bindSlider };
})();
