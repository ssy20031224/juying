// Verify each new adapter's search → detail → play pipeline
// Tests directly against the built worker (no server needed)
import assert from "node:assert/strict";
import test from "node:test";

async function render(path, init = {}) {
  const workerUrl = new URL("../dist/server/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `verify-${Date.now()}`);
  const { default: worker } = await import(workerUrl.href);
  return worker.fetch(
    new Request(`http://localhost${path}`, init),
    { ASSETS: { fetch: async () => new Response("Not found", { status: 404 }) } },
    { waitUntil() {}, passThroughOnException() {} },
  );
}

async function apiJson(path, init = {}) {
  const res = await render(path, init);
  return res.json();
}

async function verifySource(sourceKey, label) {
  console.log(`\n━━━ ${label} (${sourceKey}) ━━━`);

  // Step 1: Find a valid media ID from home
  console.log(`  [1/3] Fetching home → find media...`);
  let variant = null;
  try {
    const home = await apiJson(`/api/home?source=${sourceKey}`);
    const sections = home.sections || [];
    for (const s of sections) {
      for (const item of s.items || []) {
        const v = item.variants?.[0];
        if (v && v.sourceMediaId && !v.sourceMediaId.startsWith("media_")) {
          variant = v;
          break;
        }
      }
      if (variant) break;
    }
  } catch (e) {
    console.log(`  ⚠ Home failed: ${e.message}`);
  }

  if (!variant) {
    // Fallback: try search
    console.log(`  Trying search fallback...`);
    try {
      const search = await apiJson(`/api/search?q=%E5%8A%A8%E6%BC%AB`);
      for (const item of search.items || []) {
        const v = item.variants?.find(v2 => v2.sourceKey === sourceKey);
        if (v) { variant = v; break; }
      }
    } catch (e) {
      console.log(`  ⚠ Search failed: ${e.message}`);
    }
  }

  if (!variant) {
    console.log(`  ❌ FAIL: Could not find any media for ${sourceKey}`);
    return false;
  }

  console.log(`  ✓ Found: "${variant.title}" (id: ${variant.sourceMediaId})`);

  // Step 2: Get detail with episodes
  console.log(`  [2/3] Fetching detail → episodes...`);
  let episodes = [];
  try {
    const mediaDetail = await apiJson("/api/media/detail", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ variants: [variant] }),
    });
    episodes = mediaDetail.episodes || [];

    // Fallback: single-source detail
    if (!episodes.length) {
      const detail = await apiJson(`/api/detail?source=${sourceKey}&id=${encodeURIComponent(variant.sourceMediaId)}`);
      episodes = detail.episodes || [];
    }
  } catch (e) {
    console.log(`  ⚠ Detail failed: ${e.message}`);
    try {
      const detail = await apiJson(`/api/detail?source=${sourceKey}&id=${encodeURIComponent(variant.sourceMediaId)}`);
      episodes = detail.episodes || [];
    } catch (e2) {
      console.log(`  ❌ FAIL: Detail completely failed: ${e2.message}`);
      return false;
    }
  }

  if (!episodes.length) {
    console.log(`  ❌ FAIL: No episodes returned`);
    return false;
  }
  console.log(`  ✓ ${episodes.length} episodes`);

  // Step 3: Resolve play URL
  const ep = episodes[0];
  const flag = ep.sources?.[0]?.flag || ep.flag || {};
  console.log(`  [3/3] Resolving play URL for "${ep.name}"...`);

  try {
    const play = await apiJson(`/api/play?source=${sourceKey}`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(flag),
    });

    if (play.url) {
      const display = play.url.length > 150 ? play.url.substring(0, 150) + "..." : play.url;
      console.log(`  ✅ URL: ${display}`);
      console.log(`  ✅ Type: ${play.type || "unknown"}`);
      return true;
    }
    console.log(`  ❌ FAIL: No URL in play response: ${JSON.stringify(play).substring(0, 200)}`);
    return false;
  } catch (e) {
    console.log(`  ❌ FAIL: Play error: ${e.message}`);
    return false;
  }
}

test("dmbus - 动漫巴士 - HTML scraping + hhjx decryption", async () => {
  assert.ok(await verifySource("dmbus", "dmbus"));
});

test("lmm85 - 路漫漫 - HTML scraping + smart_token", async () => {
  assert.ok(await verifySource("lmm85", "lmm85"));
});

test("akianime - Aki动漫 - HTML + JSON API + parser", async () => {
  assert.ok(await verifySource("akianime", "akianime"));
});

test("shuangxing - 双星动漫 - encrypted JSON API", async () => {
  assert.ok(await verifySource("shuangxing", "shuangxing"));
});

test("guazi - 瓜子影视 - RSA + AES encrypted API", async () => {
  assert.ok(await verifySource("guazi", "guazi"));
});
