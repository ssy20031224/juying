import { NextResponse } from "next/server";
import { sourceAdapters } from "../../lib/adapters";
import { SOURCES } from "../../lib/sources";
import { cached } from "../../lib/cache";

export async function POST(request: Request) {
  const source = new URL(request.url).searchParams.get("source") || "";
  const sourceConfig = SOURCES.find((entry) => entry.key === source);
  const adapter = sourceConfig?.adapter ? sourceAdapters[sourceConfig.adapter] : undefined;
  if (!adapter) return NextResponse.json({ error: "source adapter unavailable" }, { status: 400 });

  try {
    const input = (await request.json()) as Record<string, string>;
    const inputKey = Object.entries(input).sort(([a], [b]) => a.localeCompare(b)).map(([key, value]) => `${key}=${value}`).join("&");
    const result = await cached(`play:${source}:${inputKey}`, 15 * 1000, () => adapter.play(input, AbortSignal.timeout(18000)));
    return NextResponse.json({
      url: result.url,
      type: result.type,
      qualityOptions: result.resolutions || [],
      referer: result.referer || "",
      headers: result.headers || {},
    });
  } catch (error) {
    return NextResponse.json({ error: error instanceof Error ? error.message : "source play failed" }, { status: 502 });
  }
}
