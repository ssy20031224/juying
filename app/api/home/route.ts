import { NextResponse } from "next/server";
import type { HomeSection } from "../../lib/adapters/types";
import { sourceAdapters } from "../../lib/adapters";
import { SOURCES } from "../../lib/sources";
import { cached } from "../../lib/cache";
import { canonicalMediaId, mergeSearchItems, normalizeTitle, toVariant, type SourcedItem } from "../../lib/catalog";
import { mapWithConcurrency } from "../../lib/fanout";

type Section = HomeSection & { sourceKey: string; sourceTitle: string };

export async function GET(request: Request) {
  const requested = new URL(request.url).searchParams.get("source") || "";
  const sources = requested ? SOURCES.filter((source) => source.key === requested) : SOURCES.filter((source) => source.enabled);
  const sections: Section[] = [];
  const errors: { sourceKey: string; error: string }[] = [];

  const outcomes = await mapWithConcurrency(sources, 6, async (source) => {
    const adapter = sourceAdapters[source.adapter ?? source.key] as (typeof sourceAdapters)[string] & { home?: (signal: AbortSignal) => Promise<HomeSection[]> };
    if (!adapter?.home) throw new Error("home not implemented");
    const home = await cached(`home:${source.key}`, 15 * 60 * 1000, () => adapter.home!(AbortSignal.timeout(12000)));
    const sourceSections: Section[] = [];
    for (const section of home) {
      const items = section.items.map((value) => {
        const sourced = { ...value, sourceKey: source.key, sourceTitle: source.title } as SourcedItem;
        return {
          ...sourced,
          id: canonicalMediaId(sourced.title, sourced.year),
          sourceCount: 1,
          variants: [toVariant(sourced)],
        };
      });
      sourceSections.push({ ...section, items, sourceKey: source.key, sourceTitle: source.title });
    }
    return sourceSections;
  });

  outcomes.forEach((outcome, index) => {
    if (outcome.error) {
      errors.push({ sourceKey: sources[index].key, error: outcome.error });
    }
    if (outcome.value) {
      sections.push(...outcome.value);
    }
  });

  // ── 跨源补充：汇总所有条目做 mergeSearchItems，把有年份/分类的源数据补充给无年份的源 ──
  const allFlat = sections.flatMap((s) => s.items as SourcedItem[]);
  if (allFlat.length > 1) {
    const merged = mergeSearchItems(allFlat);
    const enrichment = new Map<string, { year?: string; kind?: string; score?: string; status?: string; tags?: string[] }>();
    for (const m of merged) {
      const key = normalizeTitle(m.title);
      const existing = enrichment.get(key);
      if (!existing || (!existing.year && m.year) || (!existing.kind && m.kind) || (!existing.status && m.status)) {
        enrichment.set(key, { year: m.year, kind: m.kind, score: m.score, status: m.status, tags: m.tags });
      }
    }
    for (const section of sections) {
      for (const item of section.items as SourcedItem[]) {
        if (!item.year || !item.kind || !item.status) {
          const enriched = enrichment.get(normalizeTitle(item.title));
          if (enriched) {
            if (!item.year && enriched.year) item.year = enriched.year;
            if (!item.kind && enriched.kind) item.kind = enriched.kind;
            if (!item.status && enriched.status) item.status = enriched.status;
            if (!item.tags?.length && enriched.tags?.length) item.tags = enriched.tags;
          }
        }
      }
    }
  }

  const totalItems = sections.reduce((sum, s) => sum + (s.items?.length || 0), 0);
  return NextResponse.json(
    { sections, errors, demo: sections.length === 0 },
    {
      headers: {
        "Cache-Control": sections.length > 0 && totalItems >= 10
          ? "public, s-maxage=600, stale-while-revalidate=3600"
          : "no-cache, no-store, must-revalidate",
      },
    }
  );
}
