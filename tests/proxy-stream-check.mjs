import path from "node:path";

async function main() {
  const distPath = path.resolve("dist/server/index.js");
  const { default: worker } = await import("file:///" + distPath);
  const env = { ASSETS: { fetch: async () => new Response("Not found", { status: 404 }) } };
  const ctx = { waitUntil() {}, passThroughOnException() {} };

  async function api(p, init) {
    let res = await worker.fetch(new Request("http://localhost" + p, init), env, ctx);
    return { status: res.status, json: await res.json().catch(() => null) };
  }

  async function checkSource(key, label) {
    console.log(`\n=== ${label} (${key}) ===`);

    // 1. Home -> mediaId
    let h = await api(`/api/home?source=${key}`);
    let mid = null, variant = null;
    if (h.json?.sections) {
      for (let sec of h.json.sections) {
        for (let item of sec.items || []) {
          let vv = item.variants?.[0];
          if (vv && vv.sourceMediaId && !vv.sourceMediaId.startsWith("media_")) {
            mid = vv.sourceMediaId; variant = vv; break;
          }
        }
        if (mid) break;
      }
    }
    if (!mid) { console.log("  SKIP: no home media"); return; }
    console.log(`  Media: ${variant.title.slice(0, 30)}`);

    // 2. Detail
    let d = await api(`/api/detail?source=${key}&id=${encodeURIComponent(mid)}`);
    let eps = d.json?.episodes || [];
    if (!eps.length) { console.log("  SKIP: no eps"); return; }
    console.log(`  Episodes: ${eps.length}`);

    // 3. Play
    let flag = eps[0].sources?.[0]?.flag || eps[0].flag || {};
    let p = await api(`/api/play?source=${key}`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(flag),
    });
    if (!p.json?.url) { console.log(`  FAIL play: ${JSON.stringify(p.json)}`); return; }

    let isProxy = p.json.url.startsWith("/api/proxy/stream");
    console.log(`  URL: ${isProxy ? "PROXY" : "DIRECT"} ${p.json.url.slice(0, 180)}`);
    console.log(`  Type: ${p.json.type}`);

    if (!isProxy) {
      console.log("  (skipping stream check for direct URL)");
      return;
    }

    // 4. Test proxy stream
    let streamRes = await worker.fetch(new Request("http://localhost" + p.json.url, {
      headers: {
        "user-agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "sec-ch-ua": '"Chromium";v="130"',
        "sec-ch-ua-platform": '"Windows"',
        "accept": "*/*",
      }
    }), env, ctx);
    console.log(`  Stream: ${streamRes.status} CT=${(streamRes.headers.get("content-type") || "").slice(0, 50)}`);

    if (streamRes.status !== 200) {
      let body = await streamRes.text().catch(() => "");
      console.log(`  ERROR: ${body.slice(0, 200)}`);
      return;
    }

    let isM3u8Type = (p.json.type === "m3u8")
      || (streamRes.headers.get("content-type") || "").includes("mpegurl");

    if (isM3u8Type) {
      let text = await streamRes.text();
      let valid = text.includes("#EXTM3U");
      console.log(`  Valid m3u8: ${valid} (${text.length}B)`);
      if (!valid) {
        console.log(`  !!! NOT a valid m3u8 manifest`);
        console.log(`  First 200: ${text.slice(0, 200)}`);
        return;
      }
      // Test first segment
      let segLines = text.split("\n").filter(l => l.trim() && !l.startsWith("#"));
      if (segLines[0]) {
        let segUrl = "http://localhost" + segLines[0].trim();
        let segRes = await worker.fetch(new Request(segUrl, {
          headers: {
            "user-agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "sec-ch-ua-platform": '"Windows"',
          }
        }), env, ctx);
        let buf = await segRes.arrayBuffer();
        console.log(`  Segment: ${segRes.status} CT=${(segRes.headers.get("content-type")||"").slice(0,30)} size=${buf.byteLength}`);
        if (buf.byteLength < 200) {
          console.log(`  Seg body: ${new TextDecoder().decode(buf).slice(0, 200)}`);
        } else {
          console.log(`  Segment OK (${buf.byteLength} bytes of video data)`);
        }
      }
    } else {
      let buf = await streamRes.arrayBuffer();
      console.log(`  Video bytes: ${buf.byteLength}`);
      if (buf.byteLength < 500) {
        let body = new TextDecoder().decode(buf);
        console.log(`  Small response: ${body.slice(0, 200)}`);
      } else {
        console.log(`  Stream OK (${buf.byteLength} bytes)`);
      }
    }
  }

  // Test all 9 working sources
  const sources = [
    ["lanerc", "Lanerc"],
    ["AuvFun", "AuvFun"],
    ["sanqiu", "Sanqiu"],
    ["jinpai", "Jinpai"],
    ["cycapp", "Cycapp"],
    ["yzx", "YZX"],
    ["xifanacg", "Xifanacg"],
    ["gugu", "Gugu"],
    ["shuangxing", "Shuangxing"],
  ];

  for (let i = 0; i < sources.length; i++) {
    try {
      await checkSource(sources[i][0], sources[i][1]);
    } catch (e) {
      console.log(`  EXCEPTION: ${(e.message || e).toString().slice(0, 150)}`);
    }
  }

  console.log("\n=== DONE ===");
}

main().catch(e => console.error("FATAL:", e));
