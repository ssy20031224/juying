import { NextResponse } from "next/server";
import { nativeAdapters } from "../../lib/adapters/native";
import { SOURCES } from "../../lib/sources";
import { cached } from "../../lib/cache";

export async function GET(request: Request) {
  const params = new URL(request.url).searchParams;
  const source = params.get("source") || "";
  const id = params.get("id") || "";
  const sourceConfig = SOURCES.find((entry) => entry.key === source);
  const native = sourceConfig?.adapter ? nativeAdapters[sourceConfig.adapter] : undefined;
  if (!native || !id) return NextResponse.json({ episodes: [] });

  try {
    const result = await cached(`detail:${source}:${id}`, 30 * 60 * 1000, () => native.detail(id, AbortSignal.timeout(12000)));
    return NextResponse.json({
      id,
      title: result.item.title,
      year: result.item.year || "",
      description: result.item.description || "",
      cover: result.item.cover || "",
      episodes: result.episodes.map((episode) => ({ name: episode.name, url: "", route: episode.route, flag: episode.flag })),
    });
  } catch (error) {
    return NextResponse.json({ episodes: [], error: error instanceof Error ? error.message : "source detail failed" }, { status: 502 });
  }
}
