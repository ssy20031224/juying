/**
 * JS Source Adapter — wraps remote_sources/*.js scripts as SourceAdapter instances.
 */
import { loadSource } from "../js-engine";
import type { SourceAdapter, SourceItem, Episode, PlayResult, MediaType, HomeSection } from "./types";

type Json = Record<string, unknown>;

function clean(value: unknown): string {
  return String(value ?? "").replace(/<[^>]+>/g, "").replace(/&nbsp;/g, " ").replace(/&amp;/g, "&").trim();
}

function mediaType(url: string): MediaType {
  if (/\.m3u8(?:$|[?#])/i.test(url)) return "m3u8";
  if (/\.mp4(?:$|[?#])/i.test(url)) return "mp4";
  if (/\.flv(?:$|[?#])/i.test(url)) return "flv";
  return "auto";
}

function parseItems(sourceKey: string, raw: string): SourceItem[] {
  try {
    const arr = JSON.parse(raw) as Json[];
    if (!Array.isArray(arr)) return [];
    return arr.map((it) => {
      const kindText = clean(it.type ?? it.kind ?? it.vod_class ?? it.vodArea ?? it.type_name ?? it.area ?? "");
      return {
        sourceKey,
        id: String(it.id ?? ""),
        title: clean(it.name ?? it.title ?? ""),
        year: String(it.year ?? ""),
        kind: kindText.split(/[\s,，、/|·]+/)[0] || "",
        tags: kindText ? kindText.split(/[\s,，、/|·]+/).map((s: string) => s.trim()).filter(Boolean) : undefined,
        status: String(it.remarks ?? it.status ?? ""),
        score: String(it.score ?? ""),
        cover: String(it.pic ?? it.cover ?? ""),
        description: String(it.desc ?? it.description ?? ""),
        sourceCount: 1,
      };
    }).filter((it) => it.id && it.title);
  } catch { return []; }
}

export function createJsAdapter(key: string, localFile: string): SourceAdapter {
  const js = () => {
    try {
      return loadSource(key, localFile);
    } catch {
      return {};
    }
  };

  return {
    sourceKey: key,

    async search(query: string, page: number, _signal: AbortSignal): Promise<SourceItem[]> {
      const fn = js().search;
      if (!fn) return [];
      const raw = fn(query, page);
      return parseItems(key, raw);
    },

    async searchFiltered(category: string, filters: Record<string, string>, page: number, _signal: AbortSignal): Promise<SourceItem[]> {
      const fn = js().searchFiltered;
      if (!fn) return [];
      const raw = fn(category, JSON.stringify(filters), page);
      return parseItems(key, raw);
    },

    async home(_signal: AbortSignal): Promise<HomeSection[]> {
      const fn = js().homeSections;
      if (!fn) return [];
      try {
        const raw = fn();
        const arr = JSON.parse(raw) as Json[];
        if (!Array.isArray(arr)) return [];
        return arr.map((section) => ({
          title: String(section.title ?? ""),
          key: String(section.key ?? ""),
          items: parseItems(key, JSON.stringify(section.items ?? [])),
        }));
      } catch { return []; }
    },

    async detail(id: string, _signal: AbortSignal) {
      const fn = js().detail;
      if (!fn) return { item: { sourceKey: key, id, title: "", sourceCount: 1 }, episodes: [] };
      try {
        const raw = fn(id);
        const data = JSON.parse(raw) as Json;
        const item: SourceItem = {
          sourceKey: key,
          id: String(data.id ?? id),
          title: clean(data.name ?? data.title ?? ""),
          year: String(data.year ?? ""),
          kind: String(data.type ?? ""),
          cover: String(data.pic ?? ""),
          description: String(data.desc ?? ""),
          sourceCount: 1,
        };
        const episodes: Episode[] = (Array.isArray(data.episodes) ? data.episodes as Json[] : []).map((ep, i) => {
          const rawUrl = ep.url;
          let flag: Record<string, string>;

          if (typeof rawUrl === "string" && rawUrl.startsWith("{")) {
            // JSON flag (Lanerc, Gugu) — expand all fields, preserve raw
            try {
              const parsed = JSON.parse(rawUrl) as Json;
              if (typeof parsed === "object" && !Array.isArray(parsed)) {
                flag = Object.fromEntries(Object.entries(parsed).map(([k, v]) => [k, String(v ?? "")]));
                if (!flag.url) flag.url = rawUrl;
              } else { flag = { url: rawUrl }; }
            } catch { flag = { url: rawUrl }; }
          } else if (typeof rawUrl === "string" && rawUrl.includes("&")) {
            // URL query-string flag (Guazi "vod_d_id=x&vurl_id=y&...")
            flag = { _raw: rawUrl };
          } else if (typeof rawUrl === "string" && rawUrl.includes("@")) {
            // @-separated flag (Jinpai "id@nid", AuvFun "vid@eid@title",
            // Sanqiu "flag@@from", Shuangxing "url@code@name@idx")
            // Pass the raw string through; each source's play() splits by @ itself
            flag = { _raw: rawUrl };
          } else if (typeof rawUrl === "object" && rawUrl) {
            flag = Object.fromEntries(Object.entries(rawUrl).map(([k, v]) => [k, String(v ?? "")]));
          } else {
            flag = { _raw: String(rawUrl ?? "") };
          }
          return {
            id: String(ep.id ?? `${key}-${i}`),
            name: clean(ep.name) || `第${i + 1}集`,
            route: String(ep.route ?? "在线播放"),
            flag,
          };
        });
        return { item, episodes };
      } catch {
        return { item: { sourceKey: key, id, title: "", sourceCount: 1 }, episodes: [] };
      }
    },

    async play(flag: Episode["flag"], _signal: AbortSignal): Promise<PlayResult> {
      const fn = js().play;
      if (!fn) throw new Error(`${key}: play not implemented`);
      try {
        const flagArg = (() => {
          if (typeof flag === "string") return flag;
          if (flag._raw) return String(flag._raw);
          const keys = Object.keys(flag);
          if (keys.length === 1) return String(flag[keys[0]] ?? "");
          return JSON.stringify(flag);
        })();
        console.log(`[${key}] play flagArg=${typeof flagArg} len=${(flagArg || "").length}`);
        const raw = fn(flagArg);
        console.log(`[${key}] play raw (first 200): ${(raw || "").substring(0, 200)}`);
        const data = JSON.parse(raw) as Json;
        const url = String(data.url ?? "");
        if (!url) throw new Error(`${key}: no play url (raw: ${raw.substring(0, 100)})`);
        return {
          url,
          type: mediaType(url),
          headers: data.headers ? (typeof data.headers === "string" ? JSON.parse(data.headers) as Record<string, string> : data.headers as Record<string, string>) : undefined,
          referer: data.referer ? String(data.referer) : undefined,
          resolutions: Array.isArray(data.resolutions) ? (data.resolutions as Json[]).map((r, i) => ({
            id: String(r.id ?? i), name: String(r.name ?? "默认"), url: String(r.url ?? ""), type: mediaType(String(r.url ?? "")),
          })) : undefined,
        };
      } catch (e) {
        if (e instanceof Error && e.message.startsWith(key)) throw e;
        throw new Error(`${key}: play failed`);
      }
    },
  };
}
