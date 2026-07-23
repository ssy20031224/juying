import { NextResponse } from "next/server";
import { nativeAdapters, type HomeSection } from "../../lib/adapters/native";
import { SOURCES } from "../../lib/sources";
import { cached } from "../../lib/cache";

type Section = HomeSection & { sourceKey: string; sourceTitle: string };

const envBySource: Record<string, string> = {
  AuvFun: "AUVFUN_BASE_URL",
  cycapp: "CYCAPP_BASE_URL",
  jinpai: "JINPAI_BASE_URL",
  sanqiu: "SANQIU_BASE_URL",
};

export async function GET(request: Request) {
  const requested = new URL(request.url).searchParams.get("source") || "";
  const sources = requested ? SOURCES.filter((source) => source.key === requested) : SOURCES.filter((source) => source.enabled && source.adapter);
  const sections: Section[] = [];
  const errors: { sourceKey: string; error: string }[] = [];

  for (const source of sources) {
    try {
      if (!source.adapter) continue;
      const adapter = nativeAdapters[source.adapter] as (typeof nativeAdapters)[string] & { home?: (signal: AbortSignal) => Promise<HomeSection[]> };
      if (!adapter?.home) continue;
      const envName = envBySource[source.key];
      if (envName && !process.env[envName]?.trim()) throw new Error("source is not configured");
      const home = await cached(`home:${source.key}`, 15 * 60 * 1000, () => adapter.home!(AbortSignal.timeout(12000)));
      for (const section of home) sections.push({ ...section, sourceKey: source.key, sourceTitle: source.title });
    } catch (error) {
      errors.push({ sourceKey: source.key, error: error instanceof Error ? error.message : "home request failed" });
    }
  }

  return NextResponse.json({ sections, errors, demo: sections.length === 0 });
}
