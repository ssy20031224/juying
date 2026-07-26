import assert from "node:assert/strict";
import test from "node:test";

async function render(path = "/") {
  const workerUrl = new URL("../dist/server/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `${process.pid}-${Date.now()}`);
  const { default: worker } = await import(workerUrl.href);
  return worker.fetch(
    new Request(`http://localhost${path}`, { headers: { accept: "text/html" } }),
    { ASSETS: { fetch: async () => new Response("Not found", { status: 404 }) } },
    { waitUntil() {}, passThroughOnException() {} },
  );
}

test("server-renders the aggregation landing page", async () => {
  const response = await render();
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);
  const html = await response.text();
  assert.match(html, /<title>聚映 · 多源观影<\/title>/i);
  assert.match(html, /多源检索 · 不存储影片/);
  assert.match(html, /只聚合元数据与临时播放入口/);
  assert.match(html, /role="search"/);
  assert.doesNotMatch(html, /codex-preview|Your site is taking shape|react-loading-skeleton/);
});

test("search endpoint returns normalized source status", async () => {
  const response = await render("/api/search?q=%E6%98%9F");
  assert.equal(response.status, 200);
  const payload = await response.json();
  assert.equal(typeof payload.demo, "boolean");
  assert.ok(Array.isArray(payload.items));
  assert.ok(Array.isArray(payload.sources));
});

test("home endpoint exposes live-source boundaries without fetching media", async () => {
  const response = await render("/api/home");
  assert.equal(response.status, 200);
  const payload = await response.json();
  assert.ok(Array.isArray(payload.sections));
  assert.ok(Array.isArray(payload.errors));
  assert.equal(typeof payload.demo, "boolean");
});
