import { createCipheriv, createDecipheriv, createHash, randomBytes } from "node:crypto";
import type { Episode, MediaType, PlayResult, SourceAdapter, SourceItem } from "./types";
import type { HomeSection } from "./native";

type Json = Record<string, unknown>;

const UA_IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
const UA_OKHTTP = "okhttp/3.12.0";

function clean(value: unknown): string {
  return String(value ?? "")
    .replace(/<[^>]+>/g, "")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, "\"")
    .replace(/&#0?39;/g, "'")
    .replace(/[\u3000\s]+/g, " ")
    .trim();
}

function mediaType(url: string): MediaType {
  if (/\.m3u8(?:$|[?#])/i.test(url)) return "m3u8";
  if (/\.mp4(?:$|[?#])/i.test(url)) return "mp4";
  if (/\.flv(?:$|[?#])/i.test(url)) return "flv";
  return "auto";
}

async function text(url: string, signal: AbortSignal, init: RequestInit = {}): Promise<string> {
  const response = await fetch(url, {
    ...init,
    signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]),
    cache: "no-store",
    redirect: "follow",
  });
  if (!response.ok) throw new Error(`upstream ${response.status}`);
  return response.text();
}

async function json(url: string, signal: AbortSignal, init: RequestInit = {}): Promise<unknown> {
  const raw = (await text(url, signal, init)).replace(/^\uFEFF/, "");
  return JSON.parse(raw);
}

function asArray(value: unknown): Json[] {
  return Array.isArray(value) ? value.filter((entry): entry is Json => Boolean(entry && typeof entry === "object")) : [];
}

function md5(value: string): string {
  return createHash("md5").update(value).digest("hex");
}

function aesCbcDecrypt(value: string, key: string, iv = key): string {
  const decipher = createDecipheriv("aes-128-cbc", Buffer.from(key, "utf8"), Buffer.from(iv, "utf8"));
  return Buffer.concat([decipher.update(Buffer.from(value, "base64")), decipher.final()]).toString("utf8");
}

function aesCbcEncrypt(value: string, key: string, iv = key): string {
  const cipher = createCipheriv("aes-128-cbc", Buffer.from(key, "utf8"), Buffer.from(iv, "utf8"));
  return Buffer.concat([cipher.update(value, "utf8"), cipher.final()]).toString("base64");
}

function item(sourceKey: string, raw: Json, kind = ""): SourceItem {
  return {
    sourceKey,
    id: String(raw.videoId ?? raw.id ?? raw.vod_id ?? ""),
    title: clean(raw.videoName ?? raw.name ?? raw.vod_name),
    year: clean(raw.year ?? raw.vod_year),
    kind: clean(raw.typeName ?? raw.type ?? raw.vod_class ?? kind),
    cover: String(raw.fengmiantu ?? raw.dahengtu ?? raw.pic ?? raw.vod_pic ?? ""),
    description: clean(raw.blurb ?? raw.shortBlurb ?? raw.desc ?? raw.vod_content),
    sourceCount: 1,
  };
}

export class YzxAdapter implements SourceAdapter {
  readonly sourceKey = "yzx";
  private host = "https://js.trgfd.cn";
  private version = "2.5.0";
  private readonly key = "UvsoWWyu3PM8GpEsaqm4VsBcJrDJy7i7";

  private async refresh(signal: AbortSignal) {
    try {
      const data = await json("https://ss.trgfd.cn/cache/index/com.baiyunvideo.app.json", signal, {
        headers: { "User-Agent": UA_OKHTTP },
      }) as Json;
      const app = data.app as Json | undefined;
      const channels = asArray(data.qudao);
      if (app?.textURL) this.host = String(app.textURL).replace(/\/+$/, "");
      if (channels[0]?.banben) this.version = String(channels[0].banben);
    } catch {
      // Reviewed source script intentionally keeps stable fallbacks.
    }
  }

  private async list(sort: string, page: number, signal: AbortSignal): Promise<SourceItem[]> {
    const path = `/cache/zhaopian/${encodeURIComponent("动漫")}/${encodeURIComponent("全部")}/${encodeURIComponent("全部")}/${encodeURIComponent("全部")}/${encodeURIComponent(sort)}/${page}.json`;
    let data: unknown;
    try {
      data = await json(`${this.host}${path}`, signal, { headers: { "User-Agent": UA_OKHTTP } });
    } catch {
      await this.refresh(signal);
      data = await json(`${this.host}${path}`, signal, { headers: { "User-Agent": UA_OKHTTP } });
    }
    return asArray(data).map((entry) => item(this.sourceKey, entry, "动漫")).filter((entry) => entry.id && entry.title);
  }

  async home(signal: AbortSignal): Promise<HomeSection[]> {
    const sections: HomeSection[] = [];
    for (const [title, sort] of [["最新动漫", "最新"], ["人气热播", "最热"], ["高分动漫", "评分"]] as const) {
      const items = (await this.list(sort, 1, signal)).slice(0, 12);
      if (items.length) sections.push({ title, key: "dm", items });
    }
    return sections;
  }

  async search(query: string, page: number, signal: AbortSignal): Promise<SourceItem[]> {
    if (!query || query === "dm" || query === "动漫") return this.list("最新", page, signal);
    if (page > 1) return [];
    let data: unknown;
    const path = `/vc/api/search/${encodeURIComponent(query)}/1.json`;
    try {
      data = await json(`${this.host}${path}`, signal, { headers: { "User-Agent": UA_OKHTTP } });
    } catch {
      await this.refresh(signal);
      data = await json(`${this.host}${path}`, signal, { headers: { "User-Agent": UA_OKHTTP } });
    }
    return asArray(data)
      .filter((entry) => clean(entry.typeName) === "动漫")
      .map((entry) => item(this.sourceKey, entry, "动漫"))
      .filter((entry) => entry.id && entry.title);
  }

  private decryptDetail(raw: string): Json {
    const bytes = Buffer.from(raw.trim(), "base64");
    if (bytes.length < 29) throw new Error("yzx detail payload is too short");
    const iv = bytes.subarray(0, 12);
    const tag = bytes.subarray(bytes.length - 16);
    const body = bytes.subarray(12, bytes.length - 16);
    const decipher = createDecipheriv("aes-256-gcm", Buffer.from(this.key, "utf8"), iv);
    decipher.setAuthTag(tag);
    return JSON.parse(Buffer.concat([decipher.update(body), decipher.final()]).toString("utf8")) as Json;
  }

  async detail(id: string, signal: AbortSignal) {
    const dir = Math.floor((Number.parseInt(id, 10) || 0) / 1000);
    const fetchDetail = () => text(
      `${this.host}/cache/videos/${dir}/${encodeURIComponent(id)}.json?version=${encodeURIComponent(this.version)}&baoming=com.baiyunvideo.app&channel=fenxiang`,
      signal,
      { headers: { "User-Agent": UA_OKHTTP } },
    );
    let raw = await fetchDetail();
    let data: Json;
    try {
      data = this.decryptDetail(raw);
    } catch {
      await this.refresh(signal);
      raw = await fetchDetail();
      data = this.decryptDetail(raw);
    }
    const result = item(this.sourceKey, { ...data, id, type: "动漫" }, "动漫");
    const episodes: Episode[] = asArray(data.playUrlList).flatMap((episode, index) => {
      if (episode.ji === undefined || episode.ji === null) return [];
      return [{
        id: `${id}-${index}`,
        name: clean(episode.name) || `第${index + 1}集`,
        route: "云帆直连",
        flag: { id, ji: String(episode.ji), index: String(index) },
      }];
    });
    return { item: result, episodes };
  }

  async play(flag: Episode["flag"], signal: AbortSignal): Promise<PlayResult> {
    const id = String(flag.id || "");
    const androidId = randomBytes(8).toString("hex");
    const url = `${this.host}/vc/api/video/playurl?sid=${encodeURIComponent(id)}&ji=${encodeURIComponent(String(flag.ji || ""))}&jiIndex=${encodeURIComponent(String(flag.index || "0"))}&t=0&y=0&isjiid=1&androidId=${androidId}&version=${encodeURIComponent(this.version)}&baoming=com.baiyunvideo.app&channel=fenxiang`;
    const response = await json(url, signal, { headers: { "User-Agent": UA_OKHTTP, vuk: md5(id + this.key) } }) as Json;
    const data = response.data as Json | undefined;
    const mediaUrl = String(data?.url || "");
    if (!mediaUrl) throw new Error("yzx returned no play url");
    return { url: mediaUrl, type: mediaType(mediaUrl), headers: { "User-Agent": UA_OKHTTP } };
  }
}

const XIFAN_SITE = "https://anime.xifanacg.com";

function absoluteXifan(url: string): string {
  if (!url) return "";
  if (/^https?:/i.test(url)) return url;
  if (url.startsWith("//")) return `https:${url}`;
  return `${XIFAN_SITE}${url.startsWith("/") ? "" : "/"}${url}`;
}

function firstMatch(input: string, pattern: RegExp): string {
  return pattern.exec(input)?.[1] || "";
}

function xifanList(html: string, kind = ""): SourceItem[] {
  if (!html || /ds-verify|verify\/index\.html|请输入验证码/.test(html)) return [];
  const ids = [...html.matchAll(/href="\/bangumi\/(\d+)\.html"/g)].map((match) => match[1]);
  return [...new Set(ids)].slice(0, 40).flatMap((id) => {
    const escaped = id.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const title = clean(firstMatch(html, new RegExp(`/bangumi/${escaped}\\.html"[^>]*title="([^"]+)"`)));
    if (!title) return [];
    const cover = firstMatch(html, new RegExp(`/bangumi/${escaped}\\.html"[\\s\\S]{0,360}?data-src="([^"]+)"`));
    const note = clean(firstMatch(html, new RegExp(`/bangumi/${escaped}[\\s\\S]{0,360}?public-list-prb[^>]*>([^<]+)<`)));
    const year = firstMatch(html, new RegExp(`/bangumi/${escaped}[\\s\\S]{0,500}?/search/year/((?:19|20)\\d{2})\\.html`));
    return [{ sourceKey: "xifanacg", id, title, year, kind, cover: absoluteXifan(cover), description: note, sourceCount: 1 }];
  });
}

export class XifanAdapter implements SourceAdapter {
  readonly sourceKey = "xifanacg";
  private headers = { "User-Agent": UA_IPHONE, Accept: "text/html,application/xhtml+xml" };

  async home(signal: AbortSignal): Promise<HomeSection[]> {
    const html = await text(`${XIFAN_SITE}/`, signal, { headers: this.headers });
    const hotIndex = html.indexOf("热乎の新番");
    const oldIndex = html.indexOf("刚上架の旧番");
    const sections: HomeSection[] = [];
    if (hotIndex >= 0) {
      const items = xifanList(html.slice(hotIndex, oldIndex > hotIndex ? oldIndex : undefined), "日漫");
      if (items.length) sections.push({ title: "热乎の新番", key: "日漫", items });
    }
    if (oldIndex >= 0) {
      const items = xifanList(html.slice(oldIndex), "日漫");
      if (items.length) sections.push({ title: "刚上架の旧番", key: "完结", items });
    }
    return sections;
  }

  async search(query: string, page: number, signal: AbortSignal): Promise<SourceItem[]> {
    const typeMap: Record<string, [string, string]> = {
      日漫: ["1", "日漫"], 完结: ["2", "日漫"], 剧场版: ["3", "剧场版"], 欧美: ["21", "欧美"],
    };
    if (!query) return xifanList(await text(`${XIFAN_SITE}/`, signal, { headers: this.headers }));
    if (typeMap[query]) {
      const [typeId, kind] = typeMap[query];
      const suffix = page > 1 ? `-${page}` : "";
      return xifanList(await text(`${XIFAN_SITE}/type/${typeId}${suffix}.html`, signal, { headers: this.headers }), kind);
    }
    if (page > 1) return [];
    const response = await json(`${XIFAN_SITE}/index.php/ajax/suggest?mid=1&limit=30&wd=${encodeURIComponent(query)}`, signal, { headers: this.headers }) as Json;
    return asArray(response.list).map((entry) => ({
      sourceKey: this.sourceKey,
      id: String(entry.id || ""),
      title: clean(entry.name),
      cover: absoluteXifan(String(entry.pic || "")),
      sourceCount: 1,
    })).filter((entry) => entry.id && entry.title);
  }

  async detail(id: string, signal: AbortSignal) {
    const html = await text(`${XIFAN_SITE}/bangumi/${encodeURIComponent(id)}.html`, signal, { headers: this.headers });
    const title = clean(
      firstMatch(html, /slide-info-title[^>]*>([^<]+)/) ||
      firstMatch(html, /property="og:title"\s+content="([^"]+)"/),
    );
    const cover = firstMatch(html, /mask-this2[^>]*data-src="([^"]+)"/);
    const result: SourceItem = {
      sourceKey: this.sourceKey,
      id,
      title: title || "未命名",
      year: firstMatch(html, /\/search\/year\/((?:19|20)\d{2})\.html/),
      kind: "动漫",
      cover: absoluteXifan(cover),
      description: clean(firstMatch(html, /name="description"\s+content="([^"]{10,})"/)),
      sourceCount: 1,
    };
    const episodes: Episode[] = [...html.matchAll(new RegExp(`this-link"\\s+href="(/watch/${id}/(\\d+)/(\\d+)\\.html)"[^>]*>([^<]+)`, "g"))]
      .map((match, index) => ({
        id: `${match[2]}-${match[3]}`,
        name: clean(match[4]) || `第${index + 1}集`,
        route: `线路${match[2]}`,
        flag: { path: match[1] },
      }));
    return { item: result, episodes };
  }

  async play(flag: Episode["flag"], signal: AbortSignal): Promise<PlayResult> {
    const path = String(flag.path || "");
    const html = await text(absoluteXifan(path), signal, { headers: { ...this.headers, Referer: `${XIFAN_SITE}/` } });
    const raw = firstMatch(html, /player_aaaa\s*=\s*(\{.*?\})\s*;?\s*<\/script>/);
    let url = "";
    if (raw) {
      try {
        const player = JSON.parse(raw) as Json;
        url = String(player.url || "").replace(/\\\//g, "/");
        if (Number(player.encrypt) === 1) url = decodeURIComponent(url);
        if (Number(player.encrypt) === 2) url = decodeURIComponent(Buffer.from(url, "base64").toString("utf8"));
      } catch {
        url = "";
      }
    }
    if (!url) url = firstMatch(html, /"url"\s*:\s*"([^"]+\.(?:m3u8|mp4|flv|mkv)[^"]*)"/).replace(/\\\//g, "/");
    if (!url) throw new Error("xifanacg requires browser sniffing for this line");
    const encoded = url.replace(/[^\x00-\x7F]/g, (char) => encodeURIComponent(char));
    return { url: encoded, type: mediaType(url) };
  }
}

export class GuguAdapter implements SourceAdapter {
  readonly sourceKey = "gugu";
  private readonly base = "https://www.gugu3.com";
  private readonly key = "nKfZ8KX6JTNWRzTD";
  private readonly apiUa = "okhttp/3.14.9";
  private initialized = false;
  private types: { id: string; name: string }[] = [];
  private banners: SourceItem[] = [];

  private map(raw: Json): SourceItem {
    return {
      sourceKey: this.sourceKey,
      id: String(raw.vod_id ?? ""),
      title: clean(raw.vod_name),
      year: clean(raw.vod_year),
      kind: clean(raw.vod_class ?? raw.type_name).split(/[\s,，、/|·]+/).slice(0, 2).join(" "),
      cover: String(raw.vod_pic || ""),
      description: clean(raw.vod_content),
      sourceCount: 1,
    };
  }

  private headers(extra: Record<string, string> = {}) {
    return {
      "User-Agent": this.apiUa,
      "Content-Type": "application/x-www-form-urlencoded",
      "app-user-device-id": "",
      "app-version-code": "",
      "app-api-verify-time": String(Math.floor(Date.now() / 1000)),
      "app-ui-mode": "light",
      ...extra,
    };
  }

  private async post(path: string, params: Record<string, string>, signal: AbortSignal, extraHeaders: Record<string, string> = {}): Promise<Json> {
    const body = new URLSearchParams(params);
    const response = await json(`${this.base}/api.php${path}`, signal, {
      method: "POST",
      headers: this.headers(extraHeaders),
      body,
    }) as Json;
    const encrypted = String(response.data || "");
    if (!encrypted) throw new Error(`gugu returned no data for ${path}`);
    return JSON.parse(aesCbcDecrypt(encrypted, this.key)) as Json;
  }

  private async ensureHome(signal: AbortSignal) {
    if (this.initialized) return;
    const data = await this.post("/getappapi.index/initV119", {}, signal);
    this.banners = asArray(data.banner_list).map((entry) => this.map(entry)).filter((entry) => entry.id && entry.title);
    this.types = asArray(data.type_list).flatMap((entry) => {
      const id = String(entry.type_id ?? "");
      const name = clean(entry.type_name);
      if (!id || id === "0" || name === "全部" || /正版QQ群|伦理|福利|小影院/.test(name)) return [];
      return [{ id, name }];
    });
    this.initialized = this.types.length > 0;
  }

  private async typeList(typeId: string, page: number, signal: AbortSignal): Promise<SourceItem[]> {
    const data = await this.post(`/getappapi.index/typeFilterVodList?page=${page}`, { type_id: typeId, page: String(page) }, signal);
    return asArray(data.recommend_list).map((entry) => this.map(entry)).filter((entry) => entry.id && entry.title);
  }

  async home(signal: AbortSignal): Promise<HomeSection[]> {
    await this.ensureHome(signal);
    const sections: HomeSection[] = [];
    const first = this.banners.length ? this.banners : await this.typeList("0", 1, signal);
    if (first.length) sections.push({ title: "热门推荐", key: "", items: first.slice(0, 12) });
    for (const sourceType of this.types.slice(0, 4)) {
      const items = (await this.typeList(sourceType.id, 1, signal)).slice(0, 12);
      if (items.length) sections.push({ title: sourceType.name, key: sourceType.id, items });
    }
    return sections;
  }

  async search(query: string, page: number, signal: AbortSignal): Promise<SourceItem[]> {
    await this.ensureHome(signal);
    if (!query) return this.typeList("0", page, signal);
    if (/^\d+$/.test(query) && this.types.some((entry) => entry.id === query)) return this.typeList(query, page, signal);
    const data = await this.post("/getappapi.index/searchList", { type_id: "0", keywords: query, page: String(page) }, signal);
    return asArray(data.search_list).map((entry) => this.map(entry)).filter((entry) => entry.id && entry.title);
  }

  async detail(id: string, signal: AbortSignal) {
    const data = await this.post("/getappapi.index/vodDetail", { vod_id: id }, signal);
    const raw = (data.vod && typeof data.vod === "object" ? data.vod : {}) as Json;
    const result = this.map({ ...raw, vod_id: id });
    const episodes: Episode[] = [];
    for (const [lineIndex, line] of asArray(data.vod_play_list).entries()) {
      const player = (line.player_info && typeof line.player_info === "object" ? line.player_info : {}) as Json;
      const route = clean(player.show) || `线路${lineIndex + 1}`;
      for (const [index, episode] of asArray(line.urls).entries()) {
        const parseApiUrl = String(episode.parse_api_url || "");
        const episodeUrl = String(episode.url || "");
        episodes.push({
          id: `${lineIndex}-${index}`,
          name: clean(episode.name) || `第${index + 1}集`,
          route,
          flag: {
            parseApiUrl: /^https?:/i.test(parseApiUrl) ? parseApiUrl : "",
            parse: String(player.parse || ""),
            episodeUrl,
            token: String(episode.token || ""),
            p: /^https?:/i.test(parseApiUrl) ? parseApiUrl : "",
            u: episodeUrl,
            t: String(episode.token || ""),
          },
        });
      }
    }
    return { item: result, episodes };
  }

  async play(flag: Episode["flag"], signal: AbortSignal): Promise<PlayResult> {
    const parseApiUrl = String(flag.parseApiUrl || "");
    const episodeUrl = String(flag.episodeUrl || "");
    if (/^https?:/i.test(episodeUrl) && /\.(m3u8|mp4|flv|mkv)(?:$|[?#])/i.test(episodeUrl)) {
      return { url: episodeUrl, type: mediaType(episodeUrl), headers: { "User-Agent": this.apiUa } };
    }
    if (/^https?:/i.test(parseApiUrl) && /[?&](url|key)=/.test(parseApiUrl)) {
      const response = await text(parseApiUrl, signal, { headers: { "User-Agent": UA_IPHONE } });
      try {
        const parsed = JSON.parse(response) as Json;
        const direct = String(parsed.url || "");
        if (direct) return { url: direct, type: mediaType(direct), headers: { "User-Agent": UA_IPHONE } };
      } catch {
        const direct = firstMatch(response, /"url"\s*:\s*"([^"]+)"/).replace(/\\\//g, "/");
        if (direct) return { url: direct, type: mediaType(direct), headers: { "User-Agent": UA_IPHONE } };
      }
    }

    const time = String(Math.floor(Date.now() / 1000));
    const encryptedUrl = aesCbcEncrypt(episodeUrl, this.key);
    const body = parseApiUrl || `parse_api=${String(flag.parse || "")}&url=${encodeURIComponent(encryptedUrl)}&token=${encodeURIComponent(String(flag.token || ""))}`;
    const response = await json(`${this.base}/api.php/getappapi.index/vodParse`, signal, {
      method: "POST",
      headers: this.headers({ "app-api-verify-time": time, "app-api-verify-sign": aesCbcEncrypt(time, this.key) }),
      body,
    }) as Json;
    const decrypted = JSON.parse(aesCbcDecrypt(String(response.data || ""), this.key)) as Json;
    let direct = String(decrypted.url || "");
    if (!direct && typeof decrypted.json === "string") {
      try { direct = String((JSON.parse(decrypted.json) as Json).url || ""); } catch { direct = ""; }
    } else if (!direct && decrypted.json && typeof decrypted.json === "object") {
      direct = String((decrypted.json as Json).url || "");
    }
    if (!direct) throw new Error("gugu returned no play url");
    return { url: direct, type: mediaType(direct), headers: { "User-Agent": UA_IPHONE } };
  }
}

export const remoteAdapters: Record<string, SourceAdapter> = {
  yzx: new YzxAdapter(),
  xifanacg: new XifanAdapter(),
  gugu: new GuguAdapter(),
};
