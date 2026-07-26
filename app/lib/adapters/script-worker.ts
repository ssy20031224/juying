import { execFileSync } from "node:child_process";
import { existsSync } from "node:fs";
import { join } from "node:path";
import type { Episode, MediaType, PlayResult, SourceAdapter, SourceItem, HomeSection } from "./types";

type AnyObject = Record<string, unknown>;
const bundledScripts = import.meta.glob("../../../config/source-scripts/*.js", { query: "?raw", import: "default", eager: true }) as Record<string, string>;
const bundledRunner = Object.values(import.meta.glob("../../../config/script-runner.cjs", { query: "?raw", import: "default", eager: true }) as Record<string, string>)[0] || "";

function clean(value: unknown): string {
  return String(value ?? "")
    .replace(/<[^>]+>/g, "")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, "\"")
    .replace(/&#0?39;/g, "'")
    .replace(/&#x27;/gi, "'")
    .replace(/[\u3000\s]+/g, " ")
    .trim();
}

function mediaType(url: string): MediaType {
  if (/\.m3u8(?:$|[?#])/i.test(url)) return "m3u8";
  if (/\.mp4(?:$|[?#])/i.test(url)) return "mp4";
  if (/\.flv(?:$|[?#])/i.test(url)) return "flv";
  return "auto";
}

function parseJson(value: unknown): unknown {
  try { return JSON.parse(String(value ?? "")); } catch { return null; }
}

function asObject(value: unknown): AnyObject {
  return value && typeof value === "object" && !Array.isArray(value) ? value as AnyObject : {};
}

function asArray(value: unknown): AnyObject[] {
  return Array.isArray(value) ? value.filter((entry): entry is AnyObject => Boolean(entry && typeof entry === "object")) : [];
}

function normalizeItem(sourceKey: string, raw: AnyObject, kind = ""): SourceItem {
  return {
    sourceKey,
    id: String(raw.id ?? raw.vod_id ?? raw.videoId ?? ""),
    title: clean(raw.name ?? raw.vod_name ?? raw.title ?? raw.videoName),
    year: clean(raw.year ?? raw.vod_year),
    kind: clean(raw.type ?? raw.vod_class ?? raw.kind ?? kind),
    cover: String(raw.pic ?? raw.vod_pic ?? raw.cover ?? raw.fengmiantu ?? ""),
    description: clean(raw.desc ?? raw.description ?? raw.vod_content ?? ""),
    sourceCount: 1,
  };
}

function rawResult(value: unknown): unknown {
  if (typeof value === "string") return parseJson(value);
  return value;
}

export class CompleteScriptAdapter implements SourceAdapter {
  readonly sourceKey: string;
  private readonly api: AnyObject;
  private readonly homeFn: string;

  constructor(sourceKey: string, fileName: string) {
    this.sourceKey = sourceKey;
    const bundled = Object.entries(bundledScripts).find(([path]) => path.endsWith(`/${fileName}`))?.[1];
    const candidates = [
      join(process.env.LANERC_SOURCE_ROOT || "", "config", "source-scripts", fileName),
      join(process.env.LANERC_SOURCE_ROOT || "", "public", "source-scripts", fileName),
      join(process.env.INIT_CWD || "", "config", "source-scripts", fileName),
      join(process.env.INIT_CWD || "", "public", "source-scripts", fileName),
      join(process.cwd(), "config", "source-scripts", fileName),
      join(process.cwd(), "public", "source-scripts", fileName),
    ];
    const scriptPath = candidates.find((candidate) => existsSync(candidate));
    this.api = { fileName, scriptPath: scriptPath || "", source: bundled || "" };
    this.homeFn = "home";
  }

  private invoke(name: string, args: unknown[] = []): unknown {
    const payload = JSON.stringify({ scriptPath: this.api.scriptPath, source: this.api.source, method: name, args });
    const runner = [
      join(process.env.LANERC_SOURCE_ROOT || "", "config", "script-runner.cjs"),
      join(process.env.LANERC_SOURCE_ROOT || "", "public", "script-runner.cjs"),
      join(process.env.INIT_CWD || "", "config", "script-runner.cjs"),
      join(process.env.INIT_CWD || "", "public", "script-runner.cjs"),
      join(process.cwd(), "config", "script-runner.cjs"),
      join(process.cwd(), "public", "script-runner.cjs"),
    ].find((candidate) => existsSync(candidate));
    if (!runner && !bundledRunner) throw new Error(`${this.sourceKey} JS worker runner unavailable`);
    try {
      return parseJson(execFileSync(process.execPath, bundledRunner ? ["-e", bundledRunner] : [runner], {
        input: payload,
        encoding: "utf8",
        timeout: 25000,
        maxBuffer: 16 * 1024 * 1024,
        windowsHide: true,
      }));
    } catch (error) {
      const detail = error && typeof error === "object" ? String((error as { stderr?: unknown; code?: unknown }).stderr || (error as { code?: unknown }).code || "") .slice(-240) : "";
      throw new Error(`${this.sourceKey} complete JS worker failed${detail ? `: ${detail}` : ""}`);
    }
  }

  private items(value: unknown): SourceItem[] {
    return asArray(rawResult(value)).map((raw) => normalizeItem(this.sourceKey, raw)).filter((entry) => entry.id && entry.title);
  }

  async home(signal: AbortSignal): Promise<HomeSection[]> {
    signal.throwIfAborted();
    const result: unknown = this.invoke("home", []);
    const parsed = rawResult(result);
    if (Array.isArray(parsed) && parsed.some((entry) => Array.isArray(asObject(entry).items))) {
      return asArray(parsed).map((section) => {
        const obj = asObject(section);
        return { title: clean(obj.title) || this.sourceKey, key: String(obj.key || ""), items: this.items(obj.items) };
      }).filter((section) => section.items.length);
    }
    const items = this.items(parsed);
    return items.length ? [{ title: this.sourceKey, key: "", items: items.slice(0, 12) }] : [];
  }

  async search(query: string, page: number, signal: AbortSignal): Promise<SourceItem[]> {
    signal.throwIfAborted();
    return this.items(this.invoke("search", [query, page]));
  }

  async detail(id: string, signal: AbortSignal) {
    signal.throwIfAborted();
    const raw = asObject(rawResult(this.invoke("detail", [id])));
    const item = normalizeItem(this.sourceKey, { ...raw, id }, "");
    const episodes: Episode[] = asArray(raw.episodes).map((episode, index) => {
      const flagValue = episode.flag ?? episode.url ?? episode.playUrl ?? "";
      const flag = typeof flagValue === "string" ? { raw: flagValue } : asObject(flagValue) as Record<string, string>;
      return {
        id: String(episode.id ?? `${id}-${index}`),
        name: clean(episode.name ?? episode.title) || `第${index + 1}集`,
        route: clean(episode.route ?? episode.from) || "默认线路",
        flag,
      };
    });
    return { item, episodes };
  }

  async play(flag: Episode["flag"], signal: AbortSignal): Promise<PlayResult> {
    signal.throwIfAborted();
    const rawFlag = flag.raw || JSON.stringify(flag);
    const value = rawResult(this.invoke("play", [rawFlag]));
    const result = asObject(value);
    const url = String(result.url || "");
    if (!url) throw new Error(`${this.sourceKey} returned no playable URL; source may require browser challenge/sniffing`);
    return {
      url,
      type: mediaType(url),
      referer: String(result.referer || ""),
      headers: asObject(result.headers) as Record<string, string>,
    };
  }

  async categories(signal: AbortSignal): Promise<unknown> {
    signal.throwIfAborted();
    return rawResult(this.invoke("categories"));
  }

  async searchFiltered(category: string, filters: Record<string, string>, page: number, signal: AbortSignal): Promise<SourceItem[]> {
    signal.throwIfAborted();
    return this.items(this.invoke("searchFiltered", [category, JSON.stringify(filters), page]));
  }
}
