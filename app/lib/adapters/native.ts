/* eslint-disable @typescript-eslint/no-explicit-any */
import { createDecipheriv, createHash } from "node:crypto";
import type { Episode, MediaType, PlayResult, QualityOption, SourceAdapter, SourceItem } from "./types";

type Json = Record<string, any>;
export type HomeSection = { title: string; key: string; items: SourceItem[] };

const UA_CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36";
const UA_CYC = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 cyc-desktop/1.0.8 Chrome/128.0 Safari/537.36";

// Lanerc's own JS source resolves its service host at runtime. These are
// implementation details of that adapter, not user-facing environment vars.
const LANERC_DISCOVERY_ENDPOINT = "https://anime999x-1366475786.cos.ap-guangzhou.myqcloud.com/apis.json";
const LANERC_FALLBACK_ENDPOINT = "http://lol.jngaoke.cn/";
const LANERC_STALE_ENDPOINT = "https://server.jngaoke.cn/";
const LANERC_AUTH_FALLBACK = "com.clggjv.xcjfmd.ffo";
const LANERC_DECRYPT_KEY = "8f81c2519e3b661834219e7142000093";

function clean(value: unknown): string {
  return String(value ?? "").replace(/<[^>]+>/g, "").replace(/&nbsp;/g, " ").replace(/&amp;/g, "&").trim();
}

function mediaType(url: string): MediaType {
  if (/\.m3u8(?:$|[?#])/i.test(url)) return "m3u8";
  if (/\.mp4(?:$|[?#])/i.test(url)) return "mp4";
  if (/\.flv(?:$|[?#])/i.test(url)) return "flv";
  return "auto";
}

function timeoutSignal(signal: AbortSignal, timeoutMs: number): AbortSignal {
  return AbortSignal.any([signal, AbortSignal.timeout(timeoutMs)]);
}

async function requestText(url: string, signal: AbortSignal, headers: Record<string, string>, timeoutMs = 15000): Promise<string> {
  const response = await fetch(url, { signal: timeoutSignal(signal, timeoutMs), headers, cache: "no-store" });
  if (!response.ok) throw new Error(`upstream ${response.status}`);
  return response.text();
}

async function requestJson(url: string, signal: AbortSignal, headers: Record<string, string>, timeoutMs = 15000): Promise<Json> {
  const text = await requestText(url, signal, headers, timeoutMs);
  try {
    return JSON.parse(text) as Json;
  } catch {
    throw new Error("upstream returned non-json");
  }
}

function parseMaybeEncrypted(text: string, aesKey: string): Json {
  const trimmed = text.trim();
  let parsed: unknown;
  try { parsed = JSON.parse(trimmed); } catch { parsed = null; }
  if (parsed && typeof parsed === "object") return parsed as Json;
  if (typeof parsed !== "string" || !aesKey) return {};
  const decipher = createDecipheriv("aes-128-ecb", Buffer.from(aesKey, "utf8"), Buffer.alloc(0));
  decipher.setAutoPadding(true);
  const plain = Buffer.concat([decipher.update(Buffer.from(parsed, "base64")), decipher.final()]).toString("utf8");
  return JSON.parse(plain) as Json;
}

function md5(value: string): string { return createHash("md5").update(value).digest("hex"); }
function sha1(value: string): string { return createHash("sha1").update(value).digest("hex"); }
function sha256(value: string): string { return createHash("sha256").update(value).digest("hex"); }
function guessName(value: unknown, fallback: string): string { return clean(value) || fallback; }
function number(value: unknown): number | undefined { const n = Number(value); return Number.isFinite(n) && n > 0 ? n : undefined; }

function item(sourceKey: string, raw: Json): SourceItem {
  return {
    sourceKey,
    id: String(raw.id ?? raw.vod_id ?? raw.vodId ?? ""),
    title: guessName(raw.title ?? raw.name ?? raw.vod_name ?? raw.vodName, "未命名"),
    year: clean(raw.year ?? raw.vod_year ?? raw.vodYear),
    kind: clean(raw.type ?? raw.vod_class ?? raw.vodArea),
    cover: String(raw.pic ?? raw.vod_pic ?? raw.vodPic ?? raw.img ?? raw.image ?? raw.cover ?? raw.banner ?? ""),
    description: clean(raw.desc ?? raw.vod_content ?? raw.vodContent ?? raw.description),
    sourceCount: 1,
  };
}

function quality(name: unknown, url: string, index: number, raw?: Json): QualityOption {
  return { id: String(raw?.id ?? url ?? index), name: guessName(name, `线路 ${index + 1}`), url, type: mediaType(url), width: number(raw?.width), height: number(raw?.height ?? raw?.resolution), bitrate: number(raw?.bitrate ?? raw?.bandwidth) };
}

function requireEnv(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} is not configured`);
  return value.replace(/\/$/, "");
}

function lanercNormalizeHost(value: unknown): string {
  const host = String(value ?? "").trim();
  if (!/^https?:\/\//i.test(host)) return "";
  return `${host.replace(/\/+$/, "")}/`;
}

function lanercPayload(value: unknown): Json {
  let current = value as Json;
  for (let index = 0; index < 4 && current && typeof current === "object" && current.data; index += 1) {
    current = current.data as Json;
  }
  return current || {};
}

function lanercFindDeep(value: unknown, key: string, depth = 0): unknown {
  if (!value || typeof value !== "object" || depth > 12) return "";
  const record = value as Json;
  if (Object.prototype.hasOwnProperty.call(record, key)) return record[key];
  for (const child of Object.values(record)) {
    const found = lanercFindDeep(child, key, depth + 1);
    if (found !== "" && found !== null && found !== undefined) return found;
  }
  return "";
}

function lanercRestoreAlphabet(value: string): string {
  return value.replace(/1/g, "!").replace(/5/g, "@").replace(/9/g, "#").replace(/\//g, "*").replace(/-/g, "&")
    .replace(/!/g, "9").replace(/@/g, "1").replace(/#/g, "5").replace(/\*/g, "+").replace(/&/g, "/");
}

function lanercDecodeResponse(value: unknown): Json {
  if (!value || typeof value !== "object" || Array.isArray(value)) return (value || {}) as Json;
  const response = value as Json;
  if (Number(response.code) !== 201 || typeof response.data !== "string") return response;
  try {
    let ciphertext = lanercRestoreAlphabet(response.data);
    while (ciphertext.length % 4) ciphertext += "=";
    const decipher = createDecipheriv("aes-256-ecb", Buffer.from(LANERC_DECRYPT_KEY, "utf8"), Buffer.alloc(0));
    decipher.setAutoPadding(true);
    const plaintext = Buffer.concat([decipher.update(Buffer.from(ciphertext, "base64")), decipher.final()]).toString("utf8");
    return JSON.parse(plaintext) as Json;
  } catch {
    return {};
  }
}

export class LanercAdapter implements SourceAdapter {
  readonly sourceKey = "lanerc";
  private resolvedHost = "";
  private runtime: { sign: string; auth: string } | null = null;

  private async json(url: string, signal: AbortSignal, method: "GET" | "POST" = "GET", body?: Json, timeoutMs = 15000): Promise<Json> {
    const headers: Record<string, string> = { Accept: "application/json" };
    const init: RequestInit = { method, headers, signal: timeoutSignal(signal, timeoutMs), cache: "no-store" };
    if (body) {
      headers["Content-Type"] = "application/json";
      init.body = JSON.stringify(body);
    }
    const response = await fetch(url, init);
    if (!response.ok) throw new Error(`upstream ${response.status}`);
    const text = await response.text();
    let parsed: unknown;
    try { parsed = JSON.parse(text); } catch { return {}; }
    return lanercDecodeResponse(parsed);
  }

  private async resolveHost(signal: AbortSignal): Promise<string> {
    if (this.resolvedHost) return this.resolvedHost;
    const fallback = lanercNormalizeHost(LANERC_FALLBACK_ENDPOINT);
    try {
      const probe = lanercPayload(await this.json(`${fallback}app/home`, signal, "GET", undefined, 3000));
      if (probe.banner || probe.hot_list || Array.isArray(probe.vod_list)) {
        this.resolvedHost = fallback;
        return this.resolvedHost;
      }
    } catch {
      // Match Lanerc's short probe: discovery is attempted next.
    }
    try {
      const discovery = await this.json(LANERC_DISCOVERY_ENDPOINT, signal, "GET", undefined, 3000);
      const discovered = lanercNormalizeHost(lanercFindDeep(discovery, "domain"));
      if (discovered && discovered.toLowerCase() !== LANERC_STALE_ENDPOINT.toLowerCase()) {
        this.resolvedHost = discovered;
        return this.resolvedHost;
      }
    } catch {
      // Keep the same final fallback as the source JS.
    }
    this.resolvedHost = fallback;
    return this.resolvedHost;
  }

  private async apiGet(path: string, signal: AbortSignal): Promise<Json> {
    return this.json(`${await this.resolveHost(signal)}${path.replace(/^\/+/, "")}`, signal);
  }

  private async apiPost(path: string, body: Json, signal: AbortSignal): Promise<Json> {
    return this.json(`${await this.resolveHost(signal)}${path.replace(/^\/+/, "")}`, signal, "POST", body);
  }

  private async runtimeValues(signal: AbortSignal, flag: Json = {}): Promise<{ sign: string; auth: string }> {
    if (!this.runtime) {
      const config = await this.apiGet("app/config?platform=android", signal);
      this.runtime = { sign: String(lanercFindDeep(config, "sign") || ""), auth: String(lanercFindDeep(config, "auth") || LANERC_AUTH_FALLBACK) };
    }
    return { sign: String(flag.sign || this.runtime.sign || ""), auth: String(flag.auth || this.runtime.auth || LANERC_AUTH_FALLBACK) };
  }

  async search(query: string, _page: number, signal: AbortSignal): Promise<SourceItem[]> {
    const data = lanercPayload(await this.apiGet(`app/vod/search?keyword=${encodeURIComponent(query)}`, signal));
    return (Array.isArray(data.search_vods) ? data.search_vods : []).map((raw: Json) => item(this.sourceKey, raw));
  }

  async home(signal: AbortSignal): Promise<HomeSection[]> {
    const home = lanercPayload(await this.apiGet("app/home", signal));
    const sections: HomeSection[] = [];
    const cards = (values: unknown, title: string) => (Array.isArray(values) ? values : []).map((raw: Json) => item(this.sourceKey, { ...raw, type: raw.type || title })).filter((value) => value.id && value.title);
    const banner = cards(home.banner, "推荐");
    const hot = cards(home.hot_list, "热门");
    if (banner.length) sections.push({ title: "轮播", key: "__hero__", items: banner.slice(0, 5) });
    if (hot.length) sections.push({ title: "热门", key: "", items: hot.slice(0, 12) });
    for (const group of Array.isArray(home.vod_list) ? home.vod_list : []) {
      const title = String(group?.sort_name || "分类");
      const values = cards(group?.vods, title).slice(0, 12);
      if (values.length) sections.push({ title, key: String(group?.sort_id || title), items: values });
    }
    return sections;
  }

  async detail(id: string, signal: AbortSignal) {
    const data = lanercPayload(await this.apiGet(`app/getvod/${encodeURIComponent(id)}`, signal));
    const info = data.video_play_info && typeof data.video_play_info === "object" ? data.video_play_info : data;
    const result = item(this.sourceKey, { id, title: info.vod_name || info.name || info.title, pic: info.vod_pic || info.pic, desc: info.vod_blurb || info.vod_content, type: info.vod_type || info.vod_class, year: info.vod_year, score: info.vod_score });
    const episodes: Episode[] = [];
    for (const line of Array.isArray(data.video_play_list) ? data.video_play_list : []) {
      const route = String(line?.name || line?.title || "在线播放");
      const values = Array.isArray(line?.video) ? line.video : String(line?.video || "").split("#").filter(Boolean);
      values.forEach((value: unknown, index: number) => {
        const raw = typeof value === "object" && value ? String((value as Json).vid || (value as Json).url || "") : String(value);
        const parts = raw.split("$");
        if (parts[1] || raw) episodes.push({ id: `${route}-${index}`, name: parts[0] || `第${index + 1}集`, route, flag: { vid: parts[1] || raw, player: String(line?.player || "") } });
      });
    }
    return { item: result, episodes };
  }

  async play(flag: Episode["flag"], signal: AbortSignal): Promise<PlayResult> {
    const values = await this.runtimeValues(signal, flag);
    const response = await this.apiPost("app/proxyx3x", { vid: String(flag.vid || ""), player: String(flag.player || ""), sign: values.sign, auth: values.auth }, signal);
    const url = String(lanercFindDeep(response, "play_url") || "");
    if (!url) throw new Error("Lanerc returned no play url");
    return { url, type: mediaType(url) };
  }
}

export class AuvFunAdapter implements SourceAdapter {
  readonly sourceKey = "AuvFun";
  private base() { return requireEnv("AUVFUN_BASE_URL"); }
  private async call(path: string, query: Record<string, string>, signal: AbortSignal): Promise<Json> {
    const secret = requireEnv("AUVFUN_API_SECRET");
    const timestamp = Math.floor(Date.now() / 1000) + 60;
    const fullPath = `/app${path}`;
    const params = new URLSearchParams({ ...query, sign: Buffer.from(md5(`${timestamp}${fullPath}${secret}`), "hex").toString("base64url").slice(0, 22), time: String(timestamp) });
    const raw = await requestText(`${this.base()}${fullPath}?${params}`, signal, { "User-Agent": "Dart/3.11 (dart:io)", Accept: "application/json" });
    return parseMaybeEncrypted(raw, process.env.AUVFUN_AES_KEY?.trim() || "");
  }
  async search(query: string, page: number, signal: AbortSignal): Promise<SourceItem[]> {
    const data = await this.call("/video/search", { keyWord: query, page: String(page), size: "20" }, signal);
    return (Array.isArray(data.data) ? data.data : []).map((raw) => item(this.sourceKey, raw));
  }
  async home(signal: AbortSignal): Promise<HomeSection[]> {
    const tabs = await this.call("/tab/getList", {}, signal);
    const tab = (Array.isArray(tabs.data) ? tabs.data : [])[0] || {};
    const data = await this.call("/video/getList", { tabId: String(tab.id || "") }, signal);
    return (Array.isArray(data.data) ? data.data : []).flatMap((section: Json) => {
      const items = (Array.isArray(section.videoList) ? section.videoList : []).map((raw: Json) => item(this.sourceKey, raw)).slice(0, 12);
      return items.length ? [{ title: guessName(section.title, "推荐"), key: String(section.id || ""), items }] : [];
    });
  }
  async detail(id: string, signal: AbortSignal) {
    const data = await this.call("/video/getDetail", { videoId: id }, signal);
    const raw = data.data || {};
    const result = item(this.sourceKey, raw);
    const title = result.title;
    const episodes: Episode[] = (Array.isArray(raw.episodeList) ? raw.episodeList : []).map((episode: Json, index: number) => ({ id: String(episode.id ?? index), name: guessName(episode.title, `第${index + 1}集`), route: "在线播放", flag: { videoId: id, episodeId: String(episode.id ?? ""), videoTitle: title } }));
    return { item: result, episodes };
  }
  async play(flag: Episode["flag"], signal: AbortSignal): Promise<PlayResult> {
    const data = await this.call("/episode/jx", { videoTitle: flag.videoTitle || "", episodeId: flag.episodeId || "", deviceId: process.env.AUVFUN_DEVICE_ID?.trim() || "" }, signal);
    const list = Array.isArray(data.data?.resolutionList) ? data.data.resolutionList : [];
    const resolutions = list.filter((raw: Json) => raw?.url).map((raw: Json, index: number) => quality(raw.name, String(raw.url), index, raw));
    if (!resolutions.length) throw new Error("AuvFun returned no resolution");
    const preferred = [...resolutions].sort((a, b) => (b.height || 0) - (a.height || 0))[0];
    const playHeader = data.data?.playHeader || {};
    const referer = String(playHeader.Referer || "https://pan.quark.cn/");
    return { url: preferred.url, type: preferred.type, resolutions, referer, headers: { "User-Agent": String(playHeader.UserAgent || UA_CHROME), Referer: referer, ...(playHeader.Cookie ? { Cookie: String(playHeader.Cookie) } : {}) } };
  }
}

export class CycappAdapter implements SourceAdapter {
  readonly sourceKey = "cycapp";
  private base() { return requireEnv("CYCAPP_BASE_URL"); }
  private async call(path: string, signal: AbortSignal, referer?: string): Promise<Json> {
    return requestJson(`${this.base()}${path}`, signal, { "User-Agent": UA_CYC, Referer: referer || `${this.base()}/`, Accept: "application/json" });
  }
  async search(query: string, page: number, signal: AbortSignal): Promise<SourceItem[]> {
    const data = await this.call(query ? `/video/search?text=${encodeURIComponent(query)}&pg=${page}&type_id=0&limit=20` : `/video/query?page=${page}&limit=20&tid=20`, signal);
    return (Array.isArray(data.data) ? data.data : []).map((raw) => item(this.sourceKey, raw));
  }
  async home(signal: AbortSignal): Promise<HomeSection[]> {
    const sections: HomeSection[] = [];
    const rank = await this.call("/rank/video_list?id=1", signal);
    const rankItems = (Array.isArray(rank.data) ? rank.data : []).map((raw: Json) => item(this.sourceKey, raw)).slice(0, 12);
    if (rankItems.length) sections.push({ title: "动画排行榜", key: "20", items: rankItems });
    for (const category of ["20", "21", "26", "27"]) {
      const data = await this.call(`/video/query?page=1&limit=12&tid=${category}`, signal);
      const items = (Array.isArray(data.data) ? data.data : []).map((raw: Json) => item(this.sourceKey, raw)).slice(0, 12);
      if (items.length) sections.push({ title: `分类 ${category}`, key: category, items });
    }
    return sections;
  }
  async detail(id: string, signal: AbortSignal) {
    const data = await this.call(`/video/info/${encodeURIComponent(id)}`, signal);
    const raw = data.data || {};
    const result = item(this.sourceKey, raw);
    const episodes: Episode[] = [];
    for (const route of Array.isArray(raw.vod_play_from) ? raw.vod_play_from : []) {
      if (!route?.code) continue;
      const routeData = await this.call(`/video/play_url?id=${encodeURIComponent(id)}&from=${encodeURIComponent(String(route.code))}`, signal);
      for (const [index, episode] of (Array.isArray(routeData.data) ? routeData.data : []).entries()) {
        if (episode?.url) episodes.push({ id: `${route.code}-${index}`, name: guessName(episode.name, `第${index + 1}集`), route: guessName(route.name, String(route.code)), flag: { url: String(episode.url) } });
      }
    }
    return { item: result, episodes };
  }
  async play(flag: Episode["flag"], signal: AbortSignal): Promise<PlayResult> {
    let url = String(flag.url || "").split("#")[0];
    if (!url) throw new Error("cycapp missing episode url");
    if (!/\.m3u8(?:$|[?#])/i.test(url)) {
      const resolved = await requestJson(url, signal, { "User-Agent": UA_CYC, Referer: url, Accept: "application/json" });
      url = String(resolved.url || url);
    }
    return { url, type: mediaType(url), referer: url, headers: { "User-Agent": UA_CYC, Referer: url } };
  }
}

export class JinpaiAdapter implements SourceAdapter {
  readonly sourceKey = "jinpai";
  private base() { return requireEnv("JINPAI_BASE_URL"); }
  private async call(path: string, raw: string, timestamp: string, signal: AbortSignal): Promise<Json> {
    return requestJson(`${this.base()}${path}`, signal, { sign: sha1(md5(raw)), T: timestamp, Deviceid: "Deviceid", "User-Agent": "okhttp/3.15", Accept: "application/json" });
  }
  async search(query: string, page: number, signal: AbortSignal): Promise<SourceItem[]> {
    const key = process.env.JINPAI_KEY?.trim() || "";
    const t = String(Math.floor(Date.now() / 1000));
    const path = query ? `/api/mw-movie/anonymous/video/searchByWord?keyword=${encodeURIComponent(query)}&pageNum=${page}&pageSize=20` : `/api/mw-movie/anonymous/video/list?type1=4&pageNum=${page}&area=&year=`;
    const raw = query ? `keyword=${query}&pageNum=${page}&pageSize=20&key=${key}&t=${t}` : `area=&pageNum=${page}&type1=4&year=&key=${key}&t=${t}`;
    const data = await this.call(path, raw, t, signal);
    const list = query ? data.data?.result?.list : data.data?.list;
    return (Array.isArray(list) ? list : []).filter((value: Json) => !query || Number(value.typeId1) === 4).map((rawItem: Json) => item(this.sourceKey, { id: rawItem.vodId, title: rawItem.vodName, pic: rawItem.vodPic, year: rawItem.vodYear, type: rawItem.vodArea, remarks: rawItem.vodRemarks }));
  }
  async home(signal: AbortSignal): Promise<HomeSection[]> {
    const items = await this.search("", 1, signal);
    return items.length ? [{ title: "最新动漫", key: "", items: items.slice(0, 12) }] : [];
  }
  async detail(id: string, signal: AbortSignal) {
    const key = process.env.JINPAI_KEY?.trim() || "";
    const t = String(Math.floor(Date.now() / 1000));
    const data = await this.call(`/api/mw-movie/anonymous/video/detail?id=${encodeURIComponent(id)}`, `id=${id}&key=${key}&t=${t}`, t, signal);
    const raw = data.data || {};
    const result = item(this.sourceKey, { id, title: raw.vodName, pic: raw.vodPic, year: raw.vodYear, type: raw.vodArea, desc: raw.vodContent, remarks: raw.vodRemarks });
    const episodes: Episode[] = (Array.isArray(raw.episodeList) ? raw.episodeList : []).filter((episode: Json) => episode?.nid).map((episode: Json, index: number) => ({ id: String(episode.nid), name: guessName(episode.name, `第${index + 1}集`), route: "在线播放", flag: { id, nid: String(episode.nid) } }));
    return { item: result, episodes };
  }
  async play(flag: Episode["flag"], signal: AbortSignal): Promise<PlayResult> {
    const key = process.env.JINPAI_KEY?.trim() || "";
    const t = String(Math.floor(Date.now() / 1000));
    const id = String(flag.id || "");
    const nid = String(flag.nid || "");
    const data = await this.call(`/api/mw-movie/anonymous/v2/video/episode/url?id=${encodeURIComponent(id)}&nid=${encodeURIComponent(nid)}`, `id=${id}&nid=${nid}&key=${key}&t=${t}`, t, signal);
    const resolutions = (Array.isArray(data.data?.list) ? data.data.list : []).filter((raw: Json) => /^https?:/i.test(String(raw?.url || ""))).map((raw: Json, index: number) => quality(raw.resolutionName || raw.resolution, String(raw.url), index, raw));
    if (!resolutions.length) throw new Error("jinpai returned no resolution");
    return { url: resolutions[0].url, type: resolutions[0].type, resolutions, referer: this.base(), headers: { Origin: this.base(), Referer: this.base(), "User-Agent": UA_CHROME } };
  }
}

export class SanqiuAdapter implements SourceAdapter {
  readonly sourceKey = "sanqiu";
  private base() { return (process.env.SANQIU_BASE_URL?.trim() || "").replace(/\/$/, ""); }
  private headers(): Record<string, string> {
    const nonce = String(Math.floor(Math.random() * 999) + 1);
    const time = String(Math.floor(Date.now() / 1000));
    const raw = `finger=${process.env.SANQIU_SIGN_FINGER || ""}&id=com.sunshine.tv&nonce=${nonce}&sk=SK-thanks&time=${time}&v=4`;
    return { "user-agent": "okhttp/4.12.0", "x-ave": "4", "x-aid": "com.sunshine.tv", "x-time": time, "x-nonc": nonce, "x-sign": sha256(raw).toUpperCase(), "x-device-id": "0b4328287a5d953e" };
  }
  private async call(path: string, signal: AbortSignal): Promise<Json> { return requestJson(`${this.base()}${path}`, signal, this.headers()); }
  async search(query: string, page: number, signal: AbortSignal): Promise<SourceItem[]> {
    const path = query ? `/api.php/app/search/index?wd=${encodeURIComponent(query)}&page=${page}&limit=15` : `/api.php/app/filter/vod?type_name=%E5%8A%A8%E6%BC%AB&sort=time&page=${page}`;
    const data = await this.call(path, signal);
    const list = Array.isArray(data.data) ? data.data : [];
    return list.filter((raw: Json) => !query || String(raw.type_name || "动漫") === "动漫").map((raw: Json) => item(this.sourceKey, raw));
  }
  async home(signal: AbortSignal): Promise<HomeSection[]> {
    const items = await this.search("", 1, signal);
    return items.length ? [{ title: "最新动漫", key: "", items: items.slice(0, 12) }] : [];
  }
  async detail(id: string, signal: AbortSignal) {
    const data = await this.call(`/api.php/app/vod/get_detail?vod_id=${encodeURIComponent(id)}`, signal);
    const raw = Array.isArray(data.data) ? data.data[0] || {} : {};
    const result = item(this.sourceKey, raw);
    const froms = String(raw.vod_play_from || "").split("$$$");
    const lines = String(raw.vod_play_url || "").split("$$$");
    const showMap = Object.fromEntries((Array.isArray(data.vodplayer) ? data.vodplayer : []).map((p: Json) => [String(p.from), String(p.show || p.from)]));
    const episodes: Episode[] = [];
    lines.forEach((line: string, lineIndex: number) => line.split("#").forEach((segment: string, episodeIndex: number) => {
      if (!segment) return;
      const split = segment.indexOf("$");
      const name = split >= 0 ? segment.slice(0, split).trim() : `第${episodeIndex + 1}集`;
      const encoded = (split >= 0 ? segment.slice(split + 1) : segment).trim();
      if (encoded) episodes.push({ id: `${lineIndex}-${episodeIndex}`, name, route: showMap[froms[lineIndex]?.trim()] || froms[lineIndex]?.trim() || `线路${lineIndex + 1}`, flag: { real: encoded, from: froms[lineIndex]?.trim() || "" } });
    }));
    return { item: result, episodes };
  }
  async play(flag: Episode["flag"], signal: AbortSignal): Promise<PlayResult> {
    const real = String(flag.real || "");
    if (/^https?:\/\//i.test(real) && /\.(m3u8|mp4|flv|mkv|avi|mov)(?:$|[?#])/i.test(real)) return { url: real, type: mediaType(real) };
    for (let attempt = 0; attempt < 3; attempt += 1) {
      const data = await this.call(`/api.php/app/decode/url/?url=${encodeURIComponent(real)}&vodFrom=${encodeURIComponent(String(flag.from || ""))}`, signal);
      const url = String(data.data || "");
      if (/^https?:/i.test(url)) return { url, type: mediaType(url) };
    }
    throw new Error("sanqiu returned no play url");
  }
}

export const nativeAdapters: Record<string, SourceAdapter> = {
  lanerc: new LanercAdapter(),
  AuvFun: new AuvFunAdapter(),
  cycapp: new CycappAdapter(),
  jinpai: new JinpaiAdapter(),
  sanqiu: new SanqiuAdapter(),
};
