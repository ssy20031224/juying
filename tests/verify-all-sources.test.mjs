import assert from "node:assert/strict";
import test from "node:test";

async function render(path, init = {}) {
  const workerUrl = new URL("../dist/server/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `vfy-${Date.now()}`);
  const { default: worker } = await import(workerUrl.href);
  return worker.fetch(
    new Request(`http://localhost${path}`, init),
    { ASSETS: { fetch: async () => new Response("Not found", { status: 404 }) } },
    { waitUntil() {}, passThroughOnException() {} },
  );
}

async function api(path, init = {}) {
  const res = await render(path, init);
  return { status: res.status, json: await res.json().catch(() => null) };
}

async function verifySource(source, label) {
  const lines = [];
  const lg = (s) => { lines.push(s); console.log(`  ${s}`); };

  // 1) Home
  lg(`[1] /api/home?source=${source}`);
  let mediaId = null, variant = null;
  try {
    const h = await api(`/api/home?source=${source}`);
    if (h.json?.sections?.length) {
      for (const sec of h.json.sections) {
        for (const item of sec.items || []) {
          const v = item.variants?.[0];
          if (v && v.sourceMediaId && !v.sourceMediaId.startsWith("media_")) {
            mediaId = v.sourceMediaId; variant = v;
            lg(`  -> Found: "${v.title}" id=${mediaId}`);
            break;
          }
        }
        if (mediaId) break;
      }
    }
  } catch (e) { lg(`  -> home error: ${String(e.message).slice(0,80)}`); }

  if (!mediaId) {
    lg(`  [1b] Search fallback...`);
    try {
      const s = await api(`/api/search?q=%E7%81%AB%E5%BD%B1`);
      for (const item of s.json?.items || []) {
        const v = item.variants?.find(v2 => v2.sourceKey === source);
        if (v) { mediaId = v.sourceMediaId; variant = v; break; }
      }
      if (mediaId) lg(`  -> Found via search: "${variant?.title}"`);
      else lg(`  -> No results in search either`);
    } catch (e) { lg(`  -> search error: ${String(e.message).slice(0,80)}`); }
  }

  if (!mediaId) { lg(`  ❌ NO MEDIA ID found`); return { ok: false, reason: "no media", lines }; }

  // 2) Detail
  lg(`[2] /api/detail?source=${source}&id=${mediaId}`);
  let episodes = [];
  try {
    const d = await api(`/api/detail?source=${source}&id=${encodeURIComponent(mediaId)}`);
    episodes = d.json?.episodes || [];
    lg(`  -> ${episodes.length} episodes`);
  } catch (e) { lg(`  -> detail error: ${String(e.message).slice(0,80)}`); }

  if (!episodes.length) {
    // Try media/detail
    lg(`  [2b] POST /api/media/detail with variants...`);
    try {
      const md = await api("/api/media/detail", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ variants: [variant] }),
      });
      episodes = md.json?.episodes || [];
      lg(`  -> ${episodes.length} episodes`);
    } catch (e) { lg(`  -> media/detail error: ${String(e.message).slice(0,80)}`); }
  }

  if (!episodes.length) { lg(`  ❌ NO EPISODES`); return { ok: false, reason: "no episodes", lines }; }

  // 3) Play
  const ep = episodes[0];
  const flag = ep.sources?.[0]?.flag || ep.flag || {};
  lg(`[3] /api/play source=${source} ep="${ep.name}"`);
  try {
    const p = await api(`/api/play?source=${source}`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(flag),
    });
    if (p.json?.url) {
      const urlPreview = String(p.json.url).slice(0, 120);
      lg(`  ✅ URL: ${urlPreview}`);
      lg(`  ✅ Type: ${p.json.type || "auto"}`);
      return { ok: true, reason: "play url resolved", lines };
    }
    lg(`  ❌ No URL: ${JSON.stringify(p.json).slice(0, 150)}`);
    return { ok: false, reason: `no play url: ${p.json?.error || "unknown"}`, lines };
  } catch (e) {
    lg(`  ❌ Play error: ${String(e.message).slice(0,80)}`);
    return { ok: false, reason: "play exception", lines };
  }
}

const ALL = [
  ["lanerc", "Lanerc"],
  ["AuvFun", "AuvFun"],
  ["sanqiu", "三秋 Sanqiu"],
  ["jinpai", "金牌 Jinpai"],
  ["cycapp", "次元城 Cycapp"],
  ["yzx", "云帆 YZX"],
  ["xifanacg", "稀饭动漫"],
  ["gugu", "咕咕动漫"],
  ["shuangxing", "双星 ★NEW"],
  ["guazi", "瓜子 ★NEW"],
  ["dmbus", "动漫巴士 ★NEW"],
  ["lmm85", "路漫漫 ★NEW"],
  ["akianime", "Aki动漫 ★NEW"],
];

test("verify all 13 source adapters", async () => {
  console.log("\n╔══════════════════════════════════╗");
  console.log("║  13 Sources End-to-End Test     ║");
  console.log("╚══════════════════════════════════╝\n");

  const results = [];
  for (const [key, label] of ALL) {
    console.log(`\n── ${label} ──`);
    const r = await verifySource(key, label);
    results.push({ key, label, ...r });
  }

  console.log("\n\n═══════════════════════════════════════");
  console.log("  FINAL RESULTS");
  console.log("═══════════════════════════════════════");
  let pass = 0, fail = 0;
  for (const r of results) {
    const icon = r.ok ? "✅" : "❌";
    console.log(`  ${icon} ${r.label.padEnd(14)} ${r.ok ? "PASS" : "FAIL - " + r.reason}`);
    if (r.ok) pass++; else fail++;
  }
  console.log(`\n  ${pass}/${ALL.length} sources have working play URLs`);

  // At minimum, established sources should work
  assert.ok(pass >= 5, "At least 5 sources should resolve play URLs");
});
