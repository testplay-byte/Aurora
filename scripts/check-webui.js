#!/usr/bin/env node
/* ============================================================================
 * Aurora — web UI lint (scripts/check-webui.js)
 * Syntax-checks every inline <script> in data/*.html plus app.js using
 * node's vm. Catches missing braces/IIFEs before they reach the device.
 * Run: node scripts/check-webui.js
 * ==========================================================================*/
const fs = require("fs");
const path = require("path");
const vm = require("vm");

const dataDir = path.join(__dirname, "..", "firmware", "aurora", "data");
let failures = 0;

if (!fs.existsSync(dataDir)) {
  console.error(`data dir not found: ${dataDir}`);
  process.exit(2);
}

const pages = fs.readdirSync(dataDir).filter((f) => f.endsWith(".html")).sort();
for (const page of pages) {
  const html = fs.readFileSync(path.join(dataDir, page), "utf8");
  const blocks = [...html.matchAll(/<script(?![^>]*src)[^>]*>([\s\S]*?)<\/script>/g)];
  if (blocks.length === 0) {
    console.log(`  ${page}: no inline script`);
    continue;
  }
  blocks.forEach((m, i) => {
    try {
      new vm.Script(m[1], { filename: `${page}#${i}` });
    } catch (e) {
      failures++;
      console.error(`  FAIL ${page} block ${i}: ${e.message}`);
    }
  });
  console.log(`  ok   ${page} (${blocks.length} script)`);
}

try {
  new vm.Script(fs.readFileSync(path.join(dataDir, "app.js"), "utf8"), { filename: "app.js" });
  console.log("  ok   app.js");
} catch (e) {
  failures++;
  console.error(`  FAIL app.js: ${e.message}`);
}

// no external CDN references allowed (device must work in AP mode, offline)
const cdnPattern = /https?:\/\/(cdn|unpkg|fonts\.googleapis|cdnjs)/i;
for (const f of [...pages, "app.js", "app.css"]) {
  const p = path.join(dataDir, f);
  if (fs.existsSync(p) && cdnPattern.test(fs.readFileSync(p, "utf8"))) {
    failures++;
    console.error(`  FAIL ${f}: external CDN reference (must stay offline-safe)`);
  }
}

console.log(failures ? `\n${failures} problem(s) found` : `\nweb UI lint passed (${pages.length} pages)`);
process.exit(failures ? 1 : 0);
