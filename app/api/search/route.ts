import { NextResponse } from "next/server";
import { mapWithConcurrency } from "../../lib/fanout";
import { sourceAdapters } from "../../lib/adapters";
import { SOURCES, type Source } from "../../lib/sources";
import { cached } from "../../lib/cache";
import { mergeSearchItems, type SourcedItem } from "../../lib/catalog";

type Item = SourcedItem & { year: string; kind: string; score: string };

type SourceOutcome = { items: Item[]; error?: string };

const demo: Item[] = [
  { id: "demo-1", title: "雾山五行 · 番外篇", year: "2025", kind: "国漫 / 奇幻", score: "9.1", sourceKey: "lanerc", sourceTitle: "Lanerc", sourceCount: 4, description: "山海之间的少年，踏上一场关于火与记忆的旅程。" },
  { id: "demo-2", title: "银河边缘的邮差", year: "2024", kind: "科幻 / 冒险", score: "8.7", sourceKey: "AuvFun", sourceTitle: "AuvFun", sourceCount: 2, description: "一封迟到三十年的信，把邮差送向宇宙尽头。" },
  { id: "demo-3", title: "夏日终曲", year: "2023", kind: "爱情 / 剧情", score: "8.4", sourceKey: "dmbus", sourceTitle: "dmbus", sourceCount: 3, description: "在海风停下之前，他们决定把未说出口的话说完。" },
  { id: "demo-4", title: "星门观测站", year: "2025", kind: "科幻 / 悬疑", score: "8.9", sourceKey: "shuangxing", sourceTitle: "双星", sourceCount: 1, description: "观测站收到一组来自未来的坐标。" },
];

function payload(value: unknown): Record<string, unknown> {
  let current = value as Record<string, unknown>;
  for (let i = 0; i < 4 && current && typeof current === "object" && current.data; i += 1) current = current.data as Record<string, unknown>;
  return current || {};
}

// Kept as a compatibility normalizer for the legacy response shape.
// eslint-disable-next-line @typescript-eslint/no-unused-vars
function normalize(value: unknown, source: Source): Item[] {
  const data = payload(value);
  const list = Array.isArray(data.search_vods) ? data.search_vods : Array.isArray(data.vod_list) ? data.vod_list : [];
  return list.map((raw, index) => {
    const item = (raw || {}) as Record<string, unknown>;
    return {
      id: String(item.vod_id || item.id || `${source.key}-${index}`),
      title: String(item.vod_name || item.name || item.title || "未命名"),
      year: String(item.vod_year || item.year || ""),
      kind: String(item.vod_class || item.type || "影视"),
      score: String(item.vod_score || item.score || "-"),
      sourceKey: source.key,
      sourceTitle: source.title,
      sourceCount: 1,
      description: String(item.vod_blurb || item.vod_content || item.desc || ""),
    };
  });
}

async function searchSource(query: string, source: Source): Promise<SourceOutcome> {
  if (source.adapter && sourceAdapters[source.adapter]) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10000);
    try {
      const items = await cached(`search:${source.key}:${query.toLowerCase()}`, 5 * 60 * 1000, () => sourceAdapters[source.adapter!].search(query, 1, controller.signal));
      return { items: items.map((value) => ({ ...value, sourceKey: source.key, sourceTitle: source.title })) as Item[] };
    } finally {
      clearTimeout(timeout);
    }
  }
  return { items: [], error: "adapter not enabled" };
}

export async function GET(request: Request) {
  const query = new URL(request.url).searchParams.get("q")?.trim() || "";
  if (!query) return NextResponse.json({ items: mergeSearchItems(demo), sources: SOURCES.map((source) => ({ ...source, count: 0, status: "idle" })), demo: true });

  const liveSources = SOURCES.filter((source) => source.enabled && source.adapter);
  const configuredConcurrency = Number(process.env.SEARCH_SOURCE_CONCURRENCY || 4);
  const outcomes = await mapWithConcurrency(liveSources, Number.isFinite(configuredConcurrency) ? configuredConcurrency : 4, (source) => searchSource(query, source));
  const allItems: Item[] = [];
  const sourceStats = new Map<string, { count: number; status: string; latencyMs?: number; error?: string }>();
  outcomes.forEach((outcome, index) => {
    const source = liveSources[index];
    const result = outcome.value;
    sourceStats.set(source.key, { count: result?.items.length || 0, status: outcome.error || result?.error ? "error" : "ok", latencyMs: outcome.durationMs, error: outcome.error || result?.error });
    allItems.push(...(result?.items || []));
  });
  const items = mergeSearchItems(allItems);
  const sources = SOURCES.map((source) => ({ ...source, ...(sourceStats.get(source.key) || { count: 0, status: source.adapter ? "not configured" : "catalog-only" }) }));
  return NextResponse.json({ items, sources, demo: items.length === 0 });
}
