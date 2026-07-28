import { NextResponse } from "next/server";
import type { HomeSection } from "../../lib/adapters/native";
import { sourceAdapters } from "../../lib/adapters";
import { SOURCES } from "../../lib/sources";
import { cached } from "../../lib/cache";
import { canonicalMediaId, toVariant, type SourcedItem } from "../../lib/catalog";

type Section = HomeSection & { sourceKey: string; sourceTitle: string };

export async function GET(request: Request) {
  const requested = new URL(request.url).searchParams.get("source") || "";
  const sources = requested ? SOURCES.filter((source) => source.key === requested) : SOURCES.filter((source) => source.enabled && source.adapter);
  const sections: Section[] = [];
  const errors: { sourceKey: string; error: string }[] = [];

  for (const source of sources) {
    try {
      if (!source.adapter) continue;
      const adapter = sourceAdapters[source.adapter] as (typeof sourceAdapters)[string] & { home?: (signal: AbortSignal) => Promise<HomeSection[]> };
      if (!adapter?.home) continue;
      const home = await cached(`home:${source.key}`, 15 * 60 * 1000, () => adapter.home!(AbortSignal.timeout(12000)));
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
        sections.push({ ...section, items, sourceKey: source.key, sourceTitle: source.title });
      }
    } catch (error) {
      errors.push({ sourceKey: source.key, error: error instanceof Error ? error.message : "home request failed" });
    }
  }

  return NextResponse.json({ sections, errors, demo: sections.length === 0 });
}
