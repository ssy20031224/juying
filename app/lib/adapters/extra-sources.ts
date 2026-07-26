import { createCipheriv, createDecipheriv, createHash, privateDecrypt, publicEncrypt, constants } from "node:crypto";
import { inflateSync } from "node:zlib";
import type { Episode, PlayResult, SourceAdapter, SourceItem } from "./types";
import type { HomeSection } from "./native";

type Json = Record<string, unknown>;
const UA_CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36";
const UA_IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1";

function clean(value: unknown): string {
  return String(value ?? "").replace(/<[^>]+>/g, "").replace(/&nbsp;/g, " ").replace(/&amp;/g, "&").replace(/&quot;/g, "\"").replace(/&#0?39;/g, "'").replace(/&apos;/g, "'").replace(/&#x27;/gi, "'").replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/[\u3000\s]+/g, " ").trim();
}

function stripTags(value: string): string { return value.replace(/<[^>]+>/g, ""); }
function mediaType(url: string) {
  const l = url.toLowerCase();
  if (l.includes(".m3u8")) return "m3u8" as const;
  if (l.includes(".mp4")) return "mp4" as const;
  if (l.includes(".flv")) return "flv" as const;
  return "auto" as const;
}
function firstMatch(html: string, pattern: RegExp): string { return pattern.exec(html)?.[1] || ""; }
function matchAll(html: string, pattern: RegExp): RegExpExecArray[] { return [...html.matchAll(new RegExp(pattern.source, pattern.flags.includes("g") ? pattern.flags : pattern.flags + "g"))]; }
function yearClean(value: unknown): string { const n = Number(value); return Number.isFinite(n) && n > 1900 ? String(n) : ""; }
function timeoutSignal(signal: AbortSignal, ms: number): AbortSignal { return AbortSignal.any([signal, AbortSignal.timeout(ms)]); }
function toItem(sourceKey: string, raw: { id: string; title: string; year?: string; kind?: string; tags?: string[]; status?: string; cover?: string; description?: string }): SourceItem {
  return { sourceKey, id: raw.id, title: raw.title || "未命名", year: raw.year || "", kind: raw.kind || "动漫", tags: raw.tags, status: raw.status || "", cover: raw.cover || "", description: raw.description || "", sourceCount: 1 };
}

async function getText(url: string, signal: AbortSignal, extraHeaders: Record<string, string> = {}, timeoutMs = 15000): Promise<string> {
  const headers: Record<string, string> = { "User-Agent": UA_CHROME, Accept: "text/html,application/xhtml+xml", ...extraHeaders };
  const r = await fetch(url, { signal: timeoutSignal(signal, timeoutMs), headers, cache: "no-store", redirect: "follow" });
  if (!r.ok) throw new Error(`upstream ${r.status}`);
  return r.text();
}

async function getJson(url: string, signal: AbortSignal, extraHeaders: Record<string, string> = {}, timeoutMs = 15000): Promise<Json> {
  const text = await getText(url, signal, { ...extraHeaders, Accept: "application/json" }, timeoutMs);
  try { return JSON.parse(text) as Json; } catch { throw new Error("upstream returned non-json"); }
}

async function postForm(url: string, body: string, signal: AbortSignal, extraHeaders: Record<string, string> = {}, timeoutMs = 15000): Promise<string> {
  const headers: Record<string, string> = { "User-Agent": UA_CHROME, "Content-Type": "application/x-www-form-urlencoded", ...extraHeaders };
  const r = await fetch(url, { method: "POST", signal: timeoutSignal(signal, timeoutMs), headers, body, cache: "no-store", redirect: "follow" });
  if (!r.ok) throw new Error(`upstream ${r.status}`);
  return r.text();
}

function md5(value: string): string { return createHash("md5").update(value).digest("hex"); }
function sha1(value: string): string { return createHash("sha1").update(value).digest("hex"); }
function sha256(value: string): string { return createHash("sha256").update(value).digest("hex"); }

// ─────────────────────────────────────────────────────────────────── DmbusAdapter ──
const DMBUS_SITE = "https://dmbus.cc";
const DMBUS_UA = UA_IPHONE;

function dmbusReq(path: string, signal: AbortSignal, referer = `${DMBUS_SITE}/`): Promise<string> {
  return getText(`${DMBUS_SITE}${path}`, signal, { "User-Agent": DMBUS_UA, Referer: referer }, 15000);
}

function dmbusParseList(html: string): SourceItem[] {
  const items: SourceItem[] = [];
  const re = /<a href="\/v\/(\d+)\.html" class="cover lazy" data-bg="([^"]*)"[^>]*>[\s\S]*?<a class="title" href="\/v\/\d+\.html" title="([^"]*)">[\s\S]*?<span class="desc">([^<]*)<\/span>/g;
  for (const m of html.matchAll(re)) {
    const title = clean(m[3]); if (!title) continue;
    items.push({ sourceKey: "dmbus", id: m[1], title, cover: m[2].trim(), kind: "", year: "", description: clean(m[4]), sourceCount: 1 });
  }
  return items.filter((v, i, a) => a.findIndex(x => x.id === v.id) === i);
}

/* hhjx player OKOK token table — deterministic substitution used by the player to decode keys */
const OKOK_MAP: Record<string, string> = {
  "0Oo0o0Oo":"a","1O0bO001":"b","1OoCcO1":"c","3O0dO0O3":"d","4OoEeO4":"e","5O0fO0O5":"f","6OoGgO6":"g","7O0hO0O7":"h","8OoIiO8":"i","9O0jO0O9":"j","0OoKkO0":"k","1O0lO0O1":"l","2OoMmO2":"m","3O0nO0O3":"n","4OoOoO4":"o","5O0pO0O5":"p","6OoQqO6":"q","7O0rO0O7":"r","8OoSsO8":"s","9O0tOoO9":"t","0OoUuO0":"u","1O0vO0O1":"v","2OoWwO2":"w","3O0xO0O3":"x","4OoYyO4":"y","5O0zO0O5":"z",
  "0OoAAO0":"A","1O0BBO1":"B","2OoCCO2":"C","3O0DDO3":"D","4OoEEO4":"E","5O0FFO5":"F","6OoGGO6":"G","7O0HHO7":"H","8OoIIO8":"I","9O0JJO9":"J","0OoKKO0":"K","1O0LLO1":"L","2OoMMO2":"M","3O0NNO3":"N","4OoOOO4":"O","5O0PPO5":"P","6OoQQO6":"Q","7O0RRO7":"R","8OoSSO8":"S","9O0TTO9":"T","0OoUO0":"U","1O0VVO1":"V","2OoWWO2":"W","3O0XXO3":"X","4OoYYO4":"Y","5O0ZZO5":"Z"
};
function okokDecode(t: string): string {
  const decoded = Buffer.from(t, "base64").toString("binary");
  let result = "";
  for (let i = 0; i < decoded.length; i++) {
    let matched = false;
    for (const [k, v] of Object.entries(OKOK_MAP)) {
      if (decoded.startsWith(k, i)) { result += v; i += k.length - 1; matched = true; break; }
    }
    if (!matched) result += decoded[i];
  }
  return result;
}

function dmbusAbsUrl(u: string, base: string): string {
  if (!u) return u;
  if (/^https?:\/\//i.test(u)) return u;
  if (u.startsWith("//")) return `https:${u}`;
  if (u.startsWith("/") && base) return base + u;
  return u;
}

export class DmbusAdapter implements SourceAdapter {
  readonly sourceKey = "dmbus";

  async home(signal: AbortSignal): Promise<HomeSection[]> {
    const html = await dmbusReq("/", signal);
    if (!html) return [];
    const sections: HomeSection[] = [];
    const chunks = html.split('class="c_title"');
    for (let i = 1; i < chunks.length; i++) {
      const title = clean(firstMatch(chunks[i], /^[^>]*>([^<]+)<\/a>/));
      if (!title) continue;
      const listHtml = firstMatch(chunks[i], /<ul class="v_list[^"]*">([\s\S]*?)<\/ul>/);
      if (!listHtml) continue;
      const items = dmbusParseList(listHtml);
      if (items.length) sections.push({ title, key: "", items: items.slice(0, 12) });
    }
    return sections;
  }

  async search(query: string, page: number, signal: AbortSignal): Promise<SourceItem[]> {
    const key = query.trim();
    if (!key) return dmbusParseList(await dmbusReq("/", signal));
    if (/^[1-4]$/.test(key)) {
      const url = page > 1 ? `/list-${key}-${page}.html` : `/list-${key}.html`;
      return dmbusParseList(await dmbusReq(url, signal));
    }
    const su = `/s----------.html?wd=${encodeURIComponent(key)}${page > 1 ? `&page=${page}` : ""}`;
    return dmbusParseList(await dmbusReq(su, signal));
  }

  async detail(id: string, signal: AbortSignal) {
    const html = await dmbusReq(`/v/${id}.html`, signal);
    const result = toItem(this.sourceKey, {
      id,
      title: clean(firstMatch(html, /<h1 class="v_title"><a[^>]*>([^<]+)<\/a>/) || firstMatch(html, /og:title" content="《([^》]+)》/) || ""),
      cover: firstMatch(html, /og:image"\s*content="([^"]+)"/) || firstMatch(html, /<div class="cover"><img src="([^"]+)"/) || "",
      year: yearClean(firstMatch(html, /og:video:release_date" content="([^"]+)"/)),
      kind: (firstMatch(html, /og:video:class" content="([^"]*)"/) || "").split(",").slice(0, 2).join(" "),
      description: clean(stripTags(firstMatch(html, /<div id="intro"><p>([\s\S]*?)<\/p>/) || firstMatch(html, /og:description" content="([^"]*)"/) || "")),
    });

    const ctrl = firstMatch(html, /<ul class="tab_control play_from">([\s\S]*?)<\/ul>/);
    const lineNames = ctrl ? [...ctrl.matchAll(/<li[^>]*>([^<]+)<\/li>/g)].map(m => clean(m[1])) : [];

    const episodes: Episode[] = [];
    const eps = [...html.matchAll(/<a href="\/p\/(\d+)-(\d+)-(\d+)\.html"[^>]*>([^<]+)<\/a>/g)];
    const byLine = new Map<string, { name: string; ep: number }[]>();
    const order: string[] = [];
    for (const [, , lineNo, epNum, label] of eps) {
      if (!byLine.has(lineNo)) { byLine.set(lineNo, []); order.push(lineNo); }
      byLine.get(lineNo)!.push({ name: clean(label) || epNum, ep: parseInt(epNum, 10) || 0 });
    }
    for (let k = 0; k < order.length; k++) {
      const list = byLine.get(order[k])!;
      list.sort((a, b) => a.ep - b.ep);
      const route = lineNames[k] || `线路${order[k]}`;
      for (const ep of list) {
        episodes.push({ id: `dmbus-${order[k]}-${ep.ep}`, name: ep.name, route, flag: { path: `/p/${id}-${order[k]}-${ep.ep}.html` } });
      }
    }
    return { item: result, episodes };
  }

  async play(flag: Episode["flag"], signal: AbortSignal): Promise<PlayResult> {
    const path = String(flag.path || "");
    const playUrl = path.startsWith("http") ? path : `${DMBUS_SITE}${path}`;
    const html = await getText(playUrl, signal, { "User-Agent": DMBUS_UA, Referer: `${DMBUS_SITE}/` });
    let iframe = firstMatch(html, /<iframe[^>]*src="([^"]+)"/).replace(/&amp;/g, "&");
    if (iframe.startsWith("//")) iframe = `https:${iframe}`;

    if (iframe && iframe.includes("/index.php?url=")) {
      const base = firstMatch(iframe, /^(https?:\/\/[^/]+)/);
      const pl = await getText(iframe, signal, { "User-Agent": DMBUS_UA, Referer: playUrl });
      const u = firstMatch(pl, /var url\s*=\s*"([^"]+)"/);
      const t = firstMatch(pl, /var t\s*=\s*"([^"]+)"/);
      const kb = firstMatch(pl, /var key\s*=\s*OKOK\("([^"]+)"\)/);
      if (base && u && t && kb) {
        const key = okokDecode(kb);
        const body = `url=${encodeURIComponent(u)}&t=${encodeURIComponent(t)}&key=${encodeURIComponent(key)}&act=0&play=1`;
        const resp = await postForm(`${base}/api.php`, body, signal, {
          "User-Agent": DMBUS_UA, Referer: iframe, Origin: base, "X-Requested-With": "XMLHttpRequest",
        });
        try {
          const dj = JSON.parse(resp) as Json;
          if (Number(dj.code) === 200 && dj.url) {
            const real = dmbusAbsUrl(String(dj.url).replace(/\\\//g, "/"), base);
            return { url: real, type: mediaType(real), referer: String(dj.referer || (real.startsWith(base) ? iframe : "")) };
          }
        } catch { /* api.php returned non-JSON */ }
      }
      throw new Error("dmbus hhjx decryption failed — source may need browser sniffing for this line");
    }
    throw new Error("dmbus could not resolve play url");
  }

  async categories(signal: AbortSignal): Promise<unknown> { signal.throwIfAborted(); return []; }
  async searchFiltered(_cat: string, _filters: Record<string, string>, _page: number, _signal: AbortSignal): Promise<SourceItem[]> { return []; }
}

// ─────────────────────────────────────────────────────────────────── Lmm85Adapter ──
const LMM85_SITE = "https://www.lmm85.com";
const LMM85_UA = UA_IPHONE;
const LMM85_SMART_SALT = "Lmm2026@VipS3cr3t!Kx9PqZ";

function lmm85Req(path: string, signal: AbortSignal, referer = `${LMM85_SITE}/`): Promise<string> {
  return getText(`${LMM85_SITE}${path}`, signal, { "User-Agent": LMM85_UA, Referer: referer }, 15000);
}

function lmm85IsBlocked(html: string): boolean {
  if (!html) return false;
  return html.includes("_cf_chl_opt") || html.includes("Just a moment") || html.includes("challenge-platform") || html.includes("<title>身份验证");
}

function lmm85ParseCards(html: string): SourceItem[] {
  if (!html) return [];
  const items: SourceItem[] = [];
  const chunks = html.split("img-box cover-md");
  for (let i = 1; i < chunks.length; i++) {
    const id = firstMatch(chunks[i], /\/detail\/(\d+)\.html/);
    if (!id) continue;
    const pic = firstMatch(chunks[i], /data-src="([^"]+)"/) || firstMatch(chunks[i], /<img[^>]+src="([^"]+)"/);
    const remark = firstMatch(chunks[i], /<span class="label">([^<]*)<\/span>/);
    const name = clean(firstMatch(chunks[i], /<h6 class="title">\s*<a[^>]*>([^<]+)<\/a>/));
    if (!name) continue;
    items.push({ sourceKey: "lmm85", id, title: name, cover: pic || "", kind: "", year: "", description: clean(remark), sourceCount: 1 });
  }
  return items;
}

function lmm85ListFromPath(path: string, page: number, signal: AbortSignal): Promise<SourceItem[]> {
  const isLabel = path.startsWith("label/");
  const url = isLabel
    ? `/${path}${page > 1 ? `/page/${page}` : ""}.html`
    : `/${path}${page > 1 ? `_${page}` : ""}.html`;
  return lmm85Req(url, signal).then(h => lmm85IsBlocked(h) ? [] : lmm85ParseCards(h));
}

function lmm85CleanMedia(u: string): string {
  if (!u) return u;
  for (const ext of [".m3u8", ".mp4", ".flv", ".mkv"]) {
    const idx = u.toLowerCase().indexOf(ext);
    if (idx >= 0) {
      const after = u.charAt(idx + ext.length);
      return after && after !== "?" ? u.substring(0, idx + ext.length) : u;
    }
  }
  return u;
}

async function lmm85StaticPlay(pageUrl: string, signal: AbortSignal): Promise<string> {
  const html = await getText(pageUrl, signal, { "User-Agent": LMM85_UA, Referer: `${LMM85_SITE}/` });
  if (lmm85IsBlocked(html)) return "";
  const conf = firstMatch(html, /player_aaaa\s*=\s*(\{[\s\S]*?\})\s*<\/script>/) || firstMatch(html, /player_aaaa\s*=\s*(\{[\s\S]*?\});/);
  if (!conf) return "";
  try {
    const obj = JSON.parse(conf) as Json;
    let raw = String(obj.url || "");
    const enc = Number(obj.encrypt);
    if (enc === 1) raw = decodeURIComponent(raw);
    else if (enc === 2) raw = decodeURIComponent(Buffer.from(raw, "base64").toString("utf8"));
    return lmm85CleanMedia(raw);
  } catch { return ""; }
}

export class Lmm85Adapter implements SourceAdapter {
  readonly sourceKey = "lmm85";

  async home(signal: AbortSignal): Promise<HomeSection[]> {
    const defs = [
      { title: "最近更新", path: "label/new" },
      { title: "热门影片", path: "label/hot" },
      { title: "日本动漫", path: "type/ribendongman" },
      { title: "国产动漫", path: "type/guochandongman" },
      { title: "欧美动漫", path: "type/oumeidongman" },
      { title: "动画电影", path: "type/dianying" },
    ];
    const sections: HomeSection[] = [];
    for (const d of defs) {
      try {
        const items = await lmm85ListFromPath(d.path, 1, signal);
        if (items.length) sections.push({ title: d.title, key: d.path, items: items.slice(0, 12) });
      } catch { /* source section unavailable */ }
    }
    return sections;
  }

  async search(query: string, page: number, signal: AbortSignal): Promise<SourceItem[]> {
    const key = query.trim();
    if (!key) return lmm85ListFromPath("label/new", page, signal);
    if (key.includes("type/") || key.includes("label/")) return lmm85ListFromPath(key, page, signal);

    // Try server search with smart_token
    const su = `/vod/search/page/${page}/wd/${encodeURIComponent(key)}.html`;
    let html = await lmm85Req(su, signal);
    if (lmm85IsBlocked(html)) {
      try {
        const ts = Math.floor(Date.now() / 1000);
        const token = md5(`${ts}${LMM85_SMART_SALT}`);
        await postForm(`${LMM85_SITE}/index.php/ajax/smart_verify`, `smart_token=${token}&ts=${ts}`, signal, {
          "X-Requested-With": "XMLHttpRequest", "Content-Type": "application/x-www-form-urlencoded", Referer: `${LMM85_SITE}/vod/search.html?wd=${encodeURIComponent(key)}`,
        });
        html = await lmm85Req(su, signal);
      } catch { /* smart_token fallback */ }
    }
    if (!html || lmm85IsBlocked(html)) return [];
    return lmm85ParseCards(html);
  }

  async detail(id: string, signal: AbortSignal) {
    const html = await lmm85Req(`/detail/${id}.html`, signal);
    if (!html || lmm85IsBlocked(html)) return { item: toItem(this.sourceKey, { id, title: "" }), episodes: [] };

    const result = toItem(this.sourceKey, {
      id,
      title: clean(firstMatch(html, /<h1 class="page-title">([^<]+)<\/h1>/)),
      cover: firstMatch(html, /<img class="url_img"[^>]*src="([^"]+)"/) || "",
      kind: clean(firstMatch(html, /\/type\/[a-z0-9]+\.html"\s*title="([^"]+)"/)),
      year: yearClean(firstMatch(html, /\/year\/(\d{4})\.html/)),
      description: clean(stripTags(firstMatch(html, /<div class="video-info-item video-info-content">([\s\S]*?)<\/div>/))),
    });

    const tabNames = [...html.matchAll(/data-dropdown-value="([^"]+)"/g)].map(m => clean(m[1]));
    const episodes: Episode[] = [];
    for (const [, eid, sid, nid, epName] of html.matchAll(/\/play\/(\d+)_(\d+)_(\d+)\.html"[^>]*>\s*<span>([^<]+)<\/span>/g)) {
      const route = tabNames[parseInt(sid, 10) - 1] || `线路${sid}`;
      episodes.push({ id: `${sid}-${nid}`, name: clean(epName), route, flag: { path: `${eid}_${sid}_${nid}` } });
    }
    return { item: result, episodes };
  }

  async play(flag: Episode["flag"], signal: AbortSignal): Promise<PlayResult> {
    const path = String(flag.path || "");
    const [id, sid, nid] = path.split("_");
    const pageUrl = `${LMM85_SITE}/play/${id}_${sid}_${nid}.html`;

    const direct = await lmm85StaticPlay(pageUrl, signal);
    if (direct && /^https?:/i.test(direct)) {
      return { url: direct, type: mediaType(direct) };
    }
    throw new Error("lmm85 requires WebView sniffing for this line — direct player_aaaa extraction failed");
  }

  async categories(signal: AbortSignal): Promise<unknown> { signal.throwIfAborted(); return []; }
  async searchFiltered(_cat: string, _filters: Record<string, string>, _page: number, _signal: AbortSignal): Promise<SourceItem[]> { return []; }
}

// ─────────────────────────────────────────────────────────────────── AkiAnimeAdapter ──
const AKI_SITE = "https://www.akianime.com";
const AKI_UA = UA_CHROME;

function akiAbsUrl(u: string): string {
  if (!u || /^https?:\/\//.test(u)) return u;
  return `${AKI_SITE}${u.startsWith("/") ? "" : "/"}${u}`;
}

function akiDecodeEntities(s: string): string {
  return s.replace(/&nbsp;/gi, " ").replace(/&amp;/gi, "&").replace(/&lt;/gi, "<").replace(/&gt;/gi, ">").replace(/&quot;/gi, "\"").replace(/&#0?39;/g, "'").replace(/&apos;/gi, "'");
}

async function akiEnsureCookie(signal: AbortSignal): Promise<void> {
  await getText(`${AKI_SITE}/`, signal, { "User-Agent": AKI_UA, Referer: `${AKI_SITE}/` });
}

async function akiDsApi(params: Record<string, string>, signal: AbortSignal): Promise<Json> {
  const body = new URLSearchParams(params).toString();
  const text = await postForm(`${AKI_SITE}/index.php/ds_api/vod`, body, signal, {
    "User-Agent": AKI_UA, Referer: `${AKI_SITE}/`, "X-Requested-With": "XMLHttpRequest", "Content-Type": "application/x-www-form-urlencoded",
  });
  // If blocked (HTML returned instead of JSON), warm cookie and retry once
  if (!text.startsWith("{") && !text.startsWith("[")) {
    await akiEnsureCookie(signal);
    const retry = await postForm(`${AKI_SITE}/index.php/ds_api/vod`, body, signal, {
      "User-Agent": AKI_UA, Referer: `${AKI_SITE}/`, "X-Requested-With": "XMLHttpRequest", "Content-Type": "application/x-www-form-urlencoded",
    });
    return JSON.parse(retry) as Json;
  }
  return JSON.parse(text) as Json;
}

function akiParseApiList(json: Json): SourceItem[] {
  const list = Array.isArray(json.list) ? json.list : [];
  return list.flatMap((v: Json) => {
    const url = String(v.url || "");
    const id = firstMatch(url, /\/bgmdetail\/([^/.]+)\.html/) || String(v.vod_id || "");
    if (!id) return [];
    return [{
      sourceKey: "akianime", id,
      title: akiDecodeEntities(clean(v.vod_name || "")),
      kind: "番剧", year: v.vod_year ? String(v.vod_year) : "", cover: akiAbsUrl(String(v.vod_pic || "")),
      description: akiDecodeEntities(stripTags(String(v.vod_blurb || ""))).replace(/\s+/g, " ").trim(), sourceCount: 1,
    }];
  });
}

function akiParseSearchHtml(html: string): SourceItem[] {
  const seen = new Set<string>();
  const items: SourceItem[] = [];
  const re = /data-src="(\/upload\/[^"]+)"[\s\S]*?\/bgmdetail\/([^"/]+?)\.html"[^>]*>\s*<h3[^>]*>([^<]+)<\/h3>[\s\S]*?slide-info-remarks[^>]*>([^<]*)</g;
  for (const m of html.matchAll(re)) {
    const id = m[2]; if (seen.has(id)) continue; seen.add(id);
    items.push({ sourceKey: "akianime", id, title: akiDecodeEntities(m[3]).trim(), cover: akiAbsUrl(m[1]), kind: "番剧", year: "", description: akiDecodeEntities(stripTags(m[4])).trim(), sourceCount: 1 });
  }
  return items;
}

async function akiResolveByParser(token: string, from: string, signal: AbortSignal): Promise<string> {
  // Fetch player config to get parser URL
  let parse = "";
  try {
    const jsText = await getText(`${AKI_SITE}/static/js/playerconfig.js?t=${Date.now()}`, signal, { "User-Agent": AKI_UA, Referer: `${AKI_SITE}/` });
    const block = firstMatch(jsText, /player_list\s*=\s*(\{[\s\S]*?\})\s*,\s*MacPlayerConfig\.downer_list/);
    if (block) {
      const cfg = JSON.parse(block) as Json;
      const entry = cfg[from] as Json | undefined;
      if (entry && (entry.ps === "1" || entry.ps === 1) && entry.parse) parse = String(entry.parse);
    }
  } catch { /* playerconfig parse failed */ }
  if (!parse) return "";

  const pageUrl = parse + encodeURIComponent(token);
  const html = await getText(pageUrl, signal, { "User-Agent": AKI_UA, Referer: pageUrl });

  // mac parser: var config = { url, key, time }
  const cfg = (() => { try { return JSON.parse(firstMatch(html, /var\s+config\s*=\s*(\{[\s\S]*?\})/) || "{}") as Json; } catch { return {} as Json; } })();
  if (cfg.url) {
    const origin = firstMatch(pageUrl, /^(https?:\/\/[^/]+)/);
    const apiPath = pageUrl.replace(/\/[^/]*$/, "/api_config.php");
    const body = `url=${encodeURIComponent(String(cfg.url))}&time=${encodeURIComponent(String(cfg.time || ""))}&key=${encodeURIComponent(String(cfg.key || ""))}&title=`;
    const resp = await postForm(apiPath, body, signal, {
      "User-Agent": AKI_UA, Referer: pageUrl, "X-Requested-With": "XMLHttpRequest", "Content-Type": "application/x-www-form-urlencoded",
    });
    try {
      const r = JSON.parse(resp) as Json;
      if (String(r.code) === "200" && r.url) return String(r.url);
    } catch { /* api_config.php returned non-JSON */ }
  }
  // Direct URL in parser page
  const direct = firstMatch(html, /(https?:[^"'\s\\]+\.(?:m3u8|mp4|flv|m4s)[^"'\s\\]*)/);
  return direct ? direct.replace(/\\\//g, "/") : "";
}

export class AkiAnimeAdapter implements SourceAdapter {
  readonly sourceKey = "akianime";

  async home(signal: AbortSignal): Promise<HomeSection[]> {
    await akiEnsureCookie(signal);
    const rows: [string, string, string][] = [["", "time", "最近更新"], ["", "hits", "人气热门"], ["", "score", "高分推荐"], ["异世界", "time", "异世界"]];
    const sections: HomeSection[] = [];
    for (const [cls, by, title] of rows) {
      try {
        const data = await akiDsApi({ mid: "1", tid: "20", class: cls, area: "", year: "", by, page: "1" }, signal);
        const items = akiParseApiList(data);
        if (items.length) sections.push({ title, key: cls, items: items.slice(0, 12) });
      } catch { /* section unavailable */ }
    }
    return sections;
  }

  async search(query: string, page: number, signal: AbortSignal): Promise<SourceItem[]> {
    const kw = query.trim();
    if (!kw) {
      await akiEnsureCookie(signal);
      const data = await akiDsApi({ mid: "1", tid: "20", class: "", area: "", year: "", by: "time", page: String(page) }, signal);
      return akiParseApiList(data);
    }
    if (page > 1) return [];
    const segs = [kw, "", "", "", "", "", "", "", "", "", "", "", "", ""];
    const url = `${AKI_SITE}/bgmsearch/${segs.map(encodeURIComponent).join("-")}.html`;
    const html = await getText(url, signal, { "User-Agent": AKI_UA });
    return akiParseSearchHtml(html);
  }

  async detail(id: string, signal: AbortSignal) {
    const html = await getText(akiAbsUrl(`/bgmdetail/${id}.html`), signal, { "User-Agent": AKI_UA });
    const result = toItem(this.sourceKey, {
      id,
      title: akiDecodeEntities(clean(firstMatch(html, /detail-info[^>]*">\s*<h3[^>]*>([^<]+)</))),
      cover: akiAbsUrl(firstMatch(html, /data-src="(\/upload\/[^"]+)"/) || ""),
      year: yearClean(firstMatch(html, /\/bgmsearch\/-+(\d{4})\.html/) || ""),
      kind: akiDecodeEntities(clean(firstMatch(html, /类型\s*:<\/strong>\s*<a[^>]*>([^<]+)</))),
      description: (() => {
        const d = akiDecodeEntities(stripTags(firstMatch(html, /<em[^>]*>简介[：:\s]*<\/em>([\s\S]*?)<\/(?:div|p|span)>/))
          || firstMatch(html, /<div class="[^"]*juqing[^"]*"[^>]*>([\s\S]*?)<\/div>/)
          || firstMatch(html, /class="check"[^>]*>([\s\S]*?)<\/div>/)).replace(/\s+/g, " ").trim();
        return /^(暂无简介|暂无剧情介绍)/.test(d) ? "" : d.length > 300 ? d.substring(0, 300) : d;
      })(),
    });

    const tabs = [...html.matchAll(/swiper-slide[^>]*>(?:<i[^>]*><\/i>)?(?:&nbsp;|\s)*([^<]+?)<span class="badge">(\d+)<\/span>/g)].map(m => clean(m[1]).replace(/(不要相信|请不要|切勿相信|视频里的广告).*$/, "").replace(/[-—－|｜·、,]+$/, "").trim() || "线路");
    const idRe = id.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const eps = [...html.matchAll(new RegExp(`/bgmplay/${idRe}-(\\d+)-(\\d+)\\.html"[^>]*>([^<]+)<`, "g"))];

    const lineOrder: string[] = []; const lineSeen = new Set<string>();
    for (const [, ln] of eps) { if (!lineSeen.has(ln)) { lineSeen.add(ln); lineOrder.push(ln); } }
    const lineNames: Record<string, string> = {};
    for (let t = 0; t < lineOrder.length; t++) lineNames[lineOrder[t]] = tabs[t] || `线路${t + 1}`;

    const seen = new Set<string>();
    const episodes: Episode[] = [];
    for (const [, line, ep] of eps) {
      const k = `${line}-${ep}`; if (seen.has(k)) continue; seen.add(k);
      episodes.push({
        id: `aki-${line}-${ep}`,
        name: akiDecodeEntities(ep).trim() || `第${ep}集`,
        route: lineNames[line] || `线路${line}`,
        flag: { path: `/bgmplay/${id}-${line}-${ep}.html` },
      });
    }
    return { item: result, episodes };
  }

  async play(flag: Episode["flag"], signal: AbortSignal): Promise<PlayResult> {
    const path = String(flag.path || "");
    const pageUrl = path.startsWith("http") ? path : akiAbsUrl(path);
    const html = await getText(pageUrl, signal, { "User-Agent": AKI_UA, Referer: `${AKI_SITE}/` });

    const pj = (() => { try { return JSON.parse(firstMatch(html, /player_aaaa\s*=\s*(\{[^<]*\})/) || "{}") as Json; } catch { return {} as Json; } })();
    let u = String(pj.url || "");
    if (!u) {
      u = firstMatch(html, /player_aaaa[\s\S]*?"url"\s*:\s*"([^"]*)"/);
      u = u.replace(/\\u([0-9a-fA-F]{4})/g, (_, h: string) => String.fromCharCode(parseInt(h, 16))).replace(/\\\//g, "/");
    }
    if (u) {
      const enc = Number(pj.encrypt);
      if (enc === 1) u = decodeURIComponent(u);
      else if (enc === 2) u = decodeURIComponent(Buffer.from(u, "base64").toString("utf8"));
    }

    if (/^https?:\/\//.test(u) && /\.(m3u8|mp4|flv|m4s)(\?|#|$)/i.test(u)) {
      if (/[^\x00-\x7F]/.test(u)) u = encodeURI(u);
      return { url: u, type: mediaType(u), headers: { "User-Agent": AKI_UA } };
    }

    // External parser resolution for Doki/encrypted tokens
    if (!/^https?:\/\//.test(u) && u) {
      const resolved = await akiResolveByParser(u, String(pj.from || ""), signal);
      if (resolved) {
        if (/[^\x00-\x7F]/.test(resolved)) try { u = encodeURI(resolved); } catch { u = resolved; }
        return { url: resolved, type: mediaType(resolved), headers: { "User-Agent": AKI_UA } };
      }
    }
    throw new Error("akianime requires browser sniffing for this line");
  }

  async categories(signal: AbortSignal): Promise<unknown> { signal.throwIfAborted(); return []; }
  async searchFiltered(_cat: string, _filters: Record<string, string>, _page: number, _signal: AbortSignal): Promise<SourceItem[]> { return []; }
}

// ─────────────────────────────────────────────────────────────────── ShuangxingAdapter ──
const SHUANGXING_HOST = "http://175.178.65.250:19987/app/bn";
const SHUANGXING_APPKEY = "f66f65db127e48449f073c2c6eb0f993";

function sxRandHex(bytes: number): string {
  let out = ""; const chars = "0123456789ABCDEF";
  for (let i = 0; i < bytes * 2; i++) out += chars[Math.floor(Math.random() * 16)];
  return out;
}
function sxBase64(value: Buffer): string { return value.toString("base64"); }
function sxNonce(): string { return sxBase64(Buffer.from(sxRandHex(16), "hex")); }

function sxEncryptBody(plain: string, aesKey: string): string {
  const iv = Buffer.from(sxRandHex(16), "hex");
  const enc = createCipheriv("aes-256-cbc", Buffer.from(aesKey, "utf8"), iv);
  const encrypted = Buffer.concat([enc.update(plain, "utf8"), enc.final()]);
  return sxBase64(Buffer.concat([iv, encrypted]));
}

function sxDecryptBody(encoded: string, aesKey: string): string {
  if (!encoded) return "";
  try {
    const raw = Buffer.from(encoded, "base64");
    if (raw.length <= 16) return "";
    const iv = raw.subarray(0, 16);
    const cipher = raw.subarray(16);
    const decipher = createDecipheriv("aes-256-cbc", Buffer.from(aesKey, "utf8"), iv);
    const decrypted = Buffer.concat([decipher.update(cipher), decipher.final()]);
    // Try zlib inflate first
    try {
      return inflateSync(decrypted).toString("utf8");
    } catch { return decrypted.toString("utf8"); }
  } catch { return ""; }
}

async function sxApiPost(path: string, data: Json, token: string, signal: AbortSignal): Promise<Json> {
  const aesKey = sxRandHex(16); // random per-request AES key
  const now = String(Date.now());
  const nonce = sxNonce();
  const body = { ...data, timestamp: now, nonce };
  const encodedBody = sxEncryptBody(JSON.stringify(body), aesKey);

  const sign = sha256(`${encodedBody}:${now}:${nonce}:${token}:${SHUANGXING_APPKEY}`);
  const headers: Record<string, string> = {
    "User-Agent": UA_CHROME, Accept: "application/json", "Content-Type": "application/json",
    client_type: "android", uuid: aesKey, timestamp: now, sign, nonce, appkey: SHUANGXING_APPKEY, version: "6.4.5", api_version: "v1",
  };
  const r = await fetch(`${SHUANGXING_HOST}${path}`, {
    method: "POST", signal: timeoutSignal(signal, 25000), headers, body: encodedBody, cache: "no-store",
  });
  if (!r.ok) throw new Error(`upstream ${r.status}`);
  const text = await r.text();
  const decrypted = sxDecryptBody(text, aesKey);
  return JSON.parse(decrypted) as Json;
}

function sxMapItems(list: Json[], genre?: string): SourceItem[] {
  return list.filter(v => v.id != null && v.name).map(v => {
    const classText = clean(v.class || v.type_name || "");
    const tags = classText ? classText.split(/[\s,，、/|·]+/).map((s: string) => s.trim()).filter(Boolean) : undefined;
    const effectiveTags = tags?.length ? tags : (genre ? [genre] : undefined);
    return {
      sourceKey: "shuangxing", id: String(v.id), title: clean(v.name),
      cover: String(v.pic || ""), kind: "动漫", year: String(v.year || ""),
      tags: effectiveTags,
      description: String(v.blurb || ""), sourceCount: 1,
    };
  });
}

export class ShuangxingAdapter implements SourceAdapter {
  readonly sourceKey = "shuangxing";
  private token = "";
  private playerConf: Json | null = null;
  private parsers: Json[] = [];

  async #ensureSession(signal: AbortSignal) {
    if (this.token && this.playerConf) return;
    const sys = await sxApiPost("/app/systemInit", { v: "6.4.5", n: "双子星动漫", s: "054FA8DDA4319C6B6A9B954CA5777541C993F00B1B0BD4394F7EDE48184C4594", pl: "1", apiVersion: "v2", token: "" }, "", signal);
    if (sys.player) this.playerConf = sys.player as Json;
    if (sys.parser_api) this.parsers = Array.isArray(sys.parser_api) ? sys.parser_api as Json[] : [];
    if (!this.token) {
      const login = await sxApiPost("/app/log", {
        os: "android", name: "xiaomi", version: "15", sdkInt: 32, device: "xiaomi", brand: "xiaomi",
        manufacturer: "xiaomi", product: "b0q", hardware: "xiaomi", isPhysicalDevice: true,
        androidId: "V417IR", bootloader: "unknown", display: "V417IR release-keys", host: "a11-gz01-test",
        tags: "release-keys", type: "user", finger: "xiaomi/b0q/b0q:15/V619IR/613:user/release-keys",
        app: { version: "6.4.5", name: "双子星动漫", package: "com.yingfu.mobile.android.pgsp", buildNumber: "2003", buildSignature: "054FA8DDA4319C6B6A9B954CA5777541C993F00B1B0BD4394F7EDE48184C4594", install: Date.now(), update: Date.now() },
        did: sxRandHex(16), apiVersion: "v2", channel: "", token: "",
      }, "", signal);
      if (login.userInfo) this.token = String((login.userInfo as Json).user_token || "");
    }
  }

  async home(signal: AbortSignal): Promise<HomeSection[]> {
    try {
      await this.#ensureSession(signal);
      const data = await sxApiPost("/vod/search", { kw: "", page: 1, limit: 21, pid: "1", orderBy: "time", isCategory: 1, token: this.token }, this.token, signal);
      const items = sxMapItems(Array.isArray(data.data) ? data.data as Json[] : []);
      return items.length ? [{ title: "推荐动漫", key: "", items: items.slice(0, 12) }] : [];
    } catch { return []; }
  }

  async search(query: string, page: number, signal: AbortSignal): Promise<SourceItem[]> {
    await this.#ensureSession(signal);
    const key = query.trim();
    if (!key || key.startsWith("@")) {
      const pid = key.startsWith("@") ? key.slice(1) : "1";
      const data = await sxApiPost("/vod/search", { kw: "", page, limit: 21, pid, orderBy: "time", isCategory: 1, token: this.token }, this.token, signal);
      return sxMapItems(Array.isArray(data.data) ? data.data as Json[] : []);
    }
    const data = await sxApiPost("/vod/search", { kw: key, page, limit: 21, orderBy: "vod_hits_month", sort: "desc", token: this.token }, this.token, signal);
    return sxMapItems(Array.isArray(data.data) ? data.data as Json[] : []);
  }

  async detail(id: string, signal: AbortSignal) {
    await this.#ensureSession(signal);
    const resp = await sxApiPost("/vod/detail", { id, eps: "1", v: "2.0.0", pl: 1, token: this.token }, this.token, signal);
    const d = (resp.data || {}) as Json;
    const result = toItem(this.sourceKey, {
      id, title: String(d.name || ""), cover: String(d.pic || ""),
      year: String(d.year || ""), kind: "动漫", description: String(d.content || d.blurb || ""),
    });

    const codeToName: Record<string, string> = {};
    if (this.playerConf) {
      for (const [, pv] of Object.entries(this.playerConf)) {
        const p = pv as Json; const pc = String(p.code || "").trim();
        if (pc) codeToName[pc] = String(p.name || "").trim() || pc;
      }
    }
    const fromArr = String(d.play_from || "").split("$$$");
    const urlArr = String(d.play_url || "").split("$$$");
    const episodes: Episode[] = [];
    for (let i = 0; i < urlArr.length; i++) {
      const code = i < fromArr.length ? fromArr[i].trim() : "";
      const lineName = codeToName[code] || code || `线路${i + 1}`;
      for (const seg of urlArr[i].split("#")) {
        if (!seg) continue;
        const dollar = seg.indexOf("$");
        const epName = dollar >= 0 ? seg.slice(0, dollar) : `第${urlArr[i].split("#").indexOf(seg) + 1}集`;
        const epBody = dollar >= 0 ? seg.slice(dollar + 1) : seg;
        if (!epBody) continue;
        const idx = epName.replace(/\D+/g, "") || "1";
        episodes.push({ id: `sx-${i}-${epName}`, name: epName, route: lineName, flag: { token: `${epBody}@${code}@${result.title}@${idx}` } });
      }
    }
    return { item: result, episodes };
  }

  async play(flag: Episode["flag"], signal: AbortSignal): Promise<PlayResult> {
    await this.#ensureSession(signal);
    const parts = String(flag.token || "").split("@");
    const urlId = parts[0] || "";
    const code = parts[1] || "";

    // Look up player config
    let pobj: Json | null = null;
    if (this.playerConf) {
      pobj = (this.playerConf[code] || null) as Json | null;
      if (!pobj) {
        for (const [, pv] of Object.entries(this.playerConf)) {
          if (String((pv as Json).code || "").trim() === code) { pobj = pv as Json; break; }
        }
      }
    }
    const type = pobj ? Number(pobj.type || 0) : 0;
    if (type === 0 || !pobj) {
      return { url: urlId, type: mediaType(urlId) };
    }

    // Use parser (align with JS: allow empty string = permit all parsers)
    const allowRaw = pobj?.parseUrl ? String(pobj.parseUrl).split(",").map((s: string) => s.trim()).filter(Boolean) : [];

    if (!this.parsers.length) {
      // No parsers at all: fall back to raw urlId (backend may treat it as direct link)
      if (/^https?:/i.test(urlId)) {
        return { url: urlId, type: mediaType(urlId) };
      }
      throw new Error("shuangxing no parsers available and urlId is not a direct media link");
    }

    // Try allow-listed parsers first, then all parsers as fallback
    for (const parser of this.parsers) {
      const pid = String(parser.id);
      if (allowRaw.length && !allowRaw.includes(pid)) continue;
      try {
        const r = await sxApiPost("/app/vodParser", { id: Number(parser.id), url: urlId, token: this.token }, this.token, signal);
        const data = r.data;
        if (data && String(data).startsWith("http")) {
          return { url: String(data), type: mediaType(String(data)) };
        }
      } catch { /* next parser */ }
    }
    // If allow list filtered out everything, retry with all parsers
    if (allowRaw.length) {
      for (const parser of this.parsers) {
        try {
          const r = await sxApiPost("/app/vodParser", { id: Number(parser.id), url: urlId, token: this.token }, this.token, signal);
          const data = r.data;
          if (data && String(data).startsWith("http")) {
            return { url: String(data), type: mediaType(String(data)) };
          }
        } catch { /* next parser */ }
      }
    }
    // Last resort: fall back to raw urlId
    if (/^https?:/i.test(urlId)) {
      return { url: urlId, type: mediaType(urlId) };
    }
    throw new Error("shuangxing could not resolve play url");
  }

  async categories(signal: AbortSignal): Promise<unknown> { signal.throwIfAborted(); return []; }
  async searchFiltered(kind: string, filters: Record<string, string>, page: number, signal: AbortSignal): Promise<SourceItem[]> {
    await this.#ensureSession(signal);
    const pid = kind ? kind.replace(/^@/, "") : "1";
    const body: Record<string, unknown> = {
      kw: "",
      page: page,
      limit: 21,
      pid: pid,
      orderBy: "time",
      isCategory: 1,
      token: this.token,
    };
    if (filters.genre) body.class = filters.genre;
    if (filters.year) body.year = filters.year;
    const result = await sxApiPost("/vod/search", body as Json, this.token, signal);
    return sxMapItems(Array.isArray(result.data) ? result.data as Json[] : [], filters.genre);
  }
}

// ─────────────────────────────────────────────────────────────────── GuaziAdapter ──
const GUAZI_HOSTS = ["https://apinew.uozvr.com", "https://api.w32z7vtd.com", "https://api.6a7nnf7.com", "https://api.umygrx3.com", "https://api.rmedphk.com"];
const GUAZI_ENC_KEY = "OITxa5OqAYjhswxx";
const GUAZI_ENC_IV = "rCMNwZASNBKZ8mXV";
const GUAZI_SALT = "*&zvdvdvddbfikkkumtmdwqppp?|4Y!s!2br";
const GUAZI_RSA_PUB = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDUM5+/y8sPsWkd1/RQS64X259EUwxFXFE5HlA65MqrxnPs0JqoSRojSDy5QhwvROlaD6TwRQHKMY2OAZ6SnQeUJsChTEFIR9qUkwrs3/MVUMxjsv6JS6Oe/juclyJGTgVmDhB55EafXsD0SQYVj/QXXsxR6ewR5E2kL52yAAD4yQIDAQAB";
const GUAZI_RSA_PRIV = "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGAe6hKrWLi1zQmjTT1ozbE4QdFeJGNxubxld6GrFGximxfMsMB6BpJhpcTouAqywAFppiKetUBBbXwYsYU1wNr648XVmPmCMCy4rY8vdliFnbMUj086DU6Z+/oXBdWU3/b1G0DN3E9wULRSwcKZT3wj/cCI1vsCm3gj2R5SqkA9Y0CAwEAAQKBgAJH+4CxV0/zBVcLiBCHvSANm0l7HetybTh/j2p0Y1sTXro4ALwAaCTUeqdBjWiLSo9lNwDHFyq8zX90+gNxa7c5EqcWV9FmlVXr8VhfBzcZo1nXeNdXFT7tQ2yah/odtdcx+vRMSGJd1t/5k5bDd9wAvYdIDblMAg+wiKKZ5KcdAkEA1cCakEN4NexkF5tHPRrR6XOY/XHfkqXxEhMqmNbB9U34saTJnLWIHC8IXys6Qmzz30TtzCjuOqKRRy+FMM4TdwJBAJQZFPjsGC+RqcG5UvVMiMPhnwe/bXEehShK86yJK/g/UiKrO87h3aEu5gcJqBygTq3BBBoH2md3pr/W+hUMWBsCQQChfhTIrdDinKi6lRxrdBnn0Ohjg2cwuqK5zzU9p/N+S9x7Ck8wUI53DKm8jUJE8WAG7WLj/oCOWEh+ic6NIwTdAkEAj0X8nhx6AXsgCYRql1klbqtVmL8+95KZK7PnLWG/IfjQUy3pPGoSaZ7fdquG8bq8oyf5+dzjE/oTXcByS+6XRQJAP/5ciy1bL3NhUhsaOVy55MHXnPjdcTX0FaLi+ybXZIfIQ2P4rb19mVq1feMbCXhz+L1rG8oat5lYKfpe8k83ZA==";
const GUAZI_DEVICE_OLD_KEY = "aLFBMWpxBrIDAD1Si/KVvm41";
const GUAZI_UA = "Lavf/57.83.100";
const GUAZI_CODE = "GZ0369";
const GUAZI_PKG = "com.ae06aebdbb.y286327f5a.ofe849883020260517";
const GUAZI_VERSION = "2604028";
const GUAZI_VER = "3.0.3.2";
const GUAZI_PHONE_MODEL = "xiaomi-25031";

function gzEncReq(plain: string): string {
  const cipher = createCipheriv("aes-128-cbc", Buffer.from(GUAZI_ENC_KEY, "utf8"), Buffer.from(GUAZI_ENC_IV, "utf8"));
  cipher.setAutoPadding(true);
  return Buffer.concat([cipher.update(plain, "utf8"), cipher.final()]).toString("hex").toUpperCase();
}

function gzDecResp(data: { keys: unknown; response_key: unknown }): string {
  try {
    const kj = privateDecrypt({ key: `-----BEGIN PRIVATE KEY-----\n${GUAZI_RSA_PRIV.match(/.{1,64}/g)!.join("\n")}\n-----END PRIVATE KEY-----`, padding: constants.RSA_PKCS1_PADDING }, Buffer.from(String(data.keys), "base64")).toString("utf8");
    const ko = JSON.parse(kj) as Json;
    if (!ko.key || !ko.iv) return "";
    const decipher = createDecipheriv("aes-128-cbc", Buffer.from(String(ko.key), "utf8"), Buffer.from(String(ko.iv), "utf8"));
    decipher.setAutoPadding(true);
    return Buffer.concat([decipher.update(Buffer.from(String(data.response_key), "hex")), decipher.final()]).toString("utf8");
  } catch { return ""; }
}

async function gzRawApi(host: string, path: string, obj: Json, token: string, deviceId: string, signal: AbortSignal): Promise<string> {
  const time = String(Math.floor(Date.now() / 1000));
  const rk = gzEncReq(JSON.stringify(obj));
  const keys = publicEncrypt({ key: `-----BEGIN PUBLIC KEY-----\n${GUAZI_RSA_PUB.match(/.{1,64}/g)!.join("\n")}\n-----END PUBLIC KEY-----`, padding: constants.RSA_PKCS1_PADDING }, Buffer.from(JSON.stringify({ iv: GUAZI_ENC_IV, key: GUAZI_ENC_KEY }))).toString("base64");
  const sign = md5(`token_id=,token=${token},phone_type=1,request_key=${rk},app_id=1,time=${time},keys=${keys}${GUAZI_SALT}`).toUpperCase();

  const form = new URLSearchParams();
  form.set("token", token); form.set("token_id", ""); form.set("phone_type", "1"); form.set("time", time);
  form.set("phone_model", GUAZI_PHONE_MODEL); form.set("keys", keys); form.set("request_key", rk);
  form.set("signature", sign); form.set("app_id", "1"); form.set("ad_version", "1");

  const headers: Record<string, string> = {
    "User-Agent": GUAZI_UA, code: GUAZI_CODE, deviceId, lang: "zh_cn",
    "Cache-Control": "no-cache", "Content-Type": "application/x-www-form-urlencoded",
    Version: GUAZI_VERSION, PackageName: GUAZI_PKG, Ver: GUAZI_VER, "api-ver": GUAZI_VER, Referer: host,
  };
  const r = await fetch(`${host}${path}`, {
    method: "POST", signal: timeoutSignal(signal, 20000), headers, body: form.toString(), cache: "no-store",
  });
  if (!r.ok) return "";
  const resp = await r.json() as Json;
  if (!resp.data || !(resp.data as Json).keys) return "";
  return gzDecResp(resp.data as { keys: unknown; response_key: unknown });
}

function gzMapItem(it: Json): SourceItem {
  const dType = String(it.d_type || "");
  let kind = "国漫";
  if (dType === "31") kind = "日漫";
  else if (dType === "33") kind = "欧美";
  else { const area = String(it.vod_area || ""); if (/日本|日韩/.test(area)) kind = "日漫"; else if (/欧美|美国/.test(area)) kind = "欧美"; }
  return {
    sourceKey: "guazi", id: String(it.vod_id), title: clean(it.vod_name), cover: String(it.vod_pic || "").trim(),
    kind, year: yearClean(it.vod_year), description: clean(it.new_continue || it.vod_continu), sourceCount: 1,
  };
}

export class GuaziAdapter implements SourceAdapter {
  readonly sourceKey = "guazi";
  private token = "";
  private deviceId = String(864150060000000 + Math.floor(Math.random() * 10000));
  private detailCache = new Map<string, string>();
  private detailOrder: string[] = [];

  #regenDevice() { this.deviceId = String(864150060000000 + Math.floor(Math.random() * 10000)); }

  async #ensureToken(signal: AbortSignal) {
    if (this.token) return;
    for (const host of GUAZI_HOSTS) {
      try {
        this.#regenDevice();
        const deviceKey = (() => { let s = ""; for (let i = 0; i < 40; i++) s += "0123456789ABCDEF"[Math.floor(Math.random() * 16)]; return s; })();

        const signUp = await gzRawApi(host, "/App/Authentication/Device/signUp", { new_key: deviceKey, old_key: GUAZI_DEVICE_OLD_KEY, phone_type: 1, code: "" }, "", this.deviceId, signal);
        if (signUp) {
          const r = JSON.parse(signUp) as Json;
          if (r.token) this.token = String(r.token);
        }
        const refresh = await gzRawApi(host, "/App/Authentication/Authenticator/refresh", {}, this.token, this.deviceId, signal);
        if (refresh) {
          const r = JSON.parse(refresh) as Json;
          if (r.token) this.token = String(r.token);
        }
        if (this.token) return;
      } catch { continue; }
    }
  }

  async #api(host: string, path: string, obj: Json, signal: AbortSignal): Promise<Json> {
    await this.#ensureToken(signal);
    if (!this.token) return {};
    let resp = await gzRawApi(host, path, obj, this.token, this.deviceId, signal);
    if (!resp && this.token) {
      this.token = "";
      try { await this.#ensureToken(signal); resp = this.token ? await gzRawApi(host, path, obj, this.token, this.deviceId, signal) : ""; } catch { return {}; }
    }
    if (!resp) return {};
    try { return JSON.parse(resp) as Json; } catch { return {}; }
  }

  async #apiAll(path: string, obj: Json, signal: AbortSignal): Promise<Json> {
    for (const host of GUAZI_HOSTS) {
      try { const r = await this.#api(host, path, obj, signal); if (r && Object.keys(r).length) return r; } catch { /* next */ }
    }
    return {};
  }

  async home(signal: AbortSignal): Promise<HomeSection[]> {
    try {
      const items = (await this.search("", 1, signal)).slice(0, 12);
      return items.length ? [{ title: "瓜子推荐", key: "", items }] : [];
    } catch { return []; }
  }

  async search(query: string, page: number, signal: AbortSignal): Promise<SourceItem[]> {
    const key = query.trim();
    if (!key || key === "31" || key === "30" || key === "33") {
      const sub = key && (key === "30" || key === "31" || key === "33") ? key : "31";
      const j = await this.#apiAll("/App/IndexList/indexList", { tid: "3", page: String(page), sort: "d_id", area: "0", sub, year: "0", pageSize: "30" }, signal);
      return (Array.isArray(j.list) ? j.list as Json[] : []).map(gzMapItem);
    }
    if (page > 1) return [];
    const j = await this.#apiAll("/App/Index/findMoreVod", { keywords: key, order_val: "1" }, signal);
    return (Array.isArray(j.list) ? j.list as Json[] : []).filter(v => String(v.t_id) === "4").map(gzMapItem);
  }

  async detail(id: string, signal: AbortSignal) {
    const key = String(id);
    const cached = this.detailCache.get(key);
    if (cached) return JSON.parse(cached) as { item: SourceItem; episodes: Episode[] };

    const d = (await this.#apiAll("/App/IndexPlay/playInfo", { token_id: "", vod_id: id, mobile_time: String(Math.floor(Date.now() / 1000)), token: this.token }, signal)).vodInfo as Json || {};
    const result = toItem(this.sourceKey, {
      id, title: clean(d.vod_name), cover: String(d.vod_pic || "").trim(),
      kind: (() => { const a = String(d.vod_area || ""); if (/日本|日韩/.test(a)) return "日漫"; if (/欧美|美国/.test(a)) return "欧美"; return "国漫"; })(),
      year: yearClean(d.vod_year), description: clean(d.vod_use_content),
    });
    const episodes: Episode[] = [];
    const list = (await this.#apiAll("/App/Resource/Vurl/show", { vurl_cloud_id: "2", vod_d_id: id }, signal)).list as Json[] || [];
    for (let i = 0; i < list.length; i++) {
      const ep = list[i] || {} as Json;
      const play = (ep.play || {}) as Json;
      const epTitle = clean(ep.title) || `第${i + 1}集`;
      for (const [res, pv] of Object.entries(play)) {
        const p = pv as Json;
        if (String(p.show_type) === "2" || !p.param) continue;
        episodes.push({ id: `gz-${i}-${res}`, name: epTitle, route: `瓜子`, flag: { param: String(p.param) } });
        break;
      }
    }
    const out = { item: result, episodes };
    // Only cache successful results with episodes (like JS source)
    if (episodes.length > 0) {
      const json = JSON.stringify(out);
      this.detailCache.set(key, json);
      this.detailOrder.push(key);
      if (this.detailOrder.length > 80) {
        const oldest = this.detailOrder.shift();
        if (oldest) this.detailCache.delete(oldest);
      }
    }
    return out;
  }

  async play(flag: Episode["flag"], signal: AbortSignal): Promise<PlayResult> {
    const param = String(flag.param || "");
    const obj: Record<string, string> = {};
    for (const kv of param.split("&")) {
      const eq = kv.indexOf("=");
      if (eq >= 0) obj[kv.slice(0, eq)] = kv.slice(eq + 1);
    }
    const j = await this.#apiAll("/App/Resource/VurlDetail/showOne", obj, signal);
    const url = String(j.url || "");
    if (!url) throw new Error("guazi returned no play url");
    return { url, type: mediaType(url), headers: { "User-Agent": GUAZI_UA } };
  }

  async categories(signal: AbortSignal): Promise<unknown> { signal.throwIfAborted(); return []; }
  async searchFiltered(kind: string, filters: Record<string, string>, page: number, signal: AbortSignal): Promise<SourceItem[]> {
    await this.#ensureToken(signal);
    if (!this.token) return [];
    const subMap: Record<string, string> = { 日漫: "31", 日番: "31", 国漫: "30", 欧美: "33" };
    const sub = subMap[kind] || "31";
    const j = await this.#apiAll("/App/IndexList/indexList", {
      tid: "3",
      page: String(page),
      sort: filters.sort || "d_id",
      area: "0",
      sub: sub,
      year: filters.year || "0",
      pageSize: "30",
    }, signal);
    return (Array.isArray(j.list) ? j.list as Json[] : []).map(gzMapItem);
  }
}

export const extraAdapters: Record<string, SourceAdapter> = {
  dmbus: new DmbusAdapter(),
  lmm85: new Lmm85Adapter(),
  akianime: new AkiAnimeAdapter(),
  shuangxing: new ShuangxingAdapter(),
  guazi: new GuaziAdapter(),
};
