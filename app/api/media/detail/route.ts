import { NextResponse } from "next/server";
import { sourceAdapters } from "../../../lib/adapters";
import type { SourceVariant } from "../../../lib/adapters/types";
import { cached } from "../../../lib/cache";
import { mergeDetails, type SourcedDetail } from "../../../lib/catalog";
import { mapWithConcurrency } from "../../../lib/fanout";
import { SOURCES } from "../../../lib/sources";

function validVariant(value: unknown): value is SourceVariant {
  if (!value || typeof value !== "object") return false;
  const variant = value as Partial<SourceVariant>;
  return Boolean(variant.sourceKey && variant.sourceMediaId);
}

export async function POST(request: Request) {
  const body = (await request.json().catch(() => ({}))) as { variants?: unknown[] };
  const variants = (body.variants || []).filter(validVariant).slice(0, 8);
  if (!variants.length) return NextResponse.json({ error: "no source variants supplied" }, { status: 400 });

  const unique = variants.filter((variant, index, list) =>
    list.findIndex((entry) => entry.sourceKey === variant.sourceKey && entry.sourceMediaId === variant.sourceMediaId) === index,
  );

  const outcomes = await mapWithConcurrency(unique, 3, async (variant): Promise<SourcedDetail> => {
    const source = SOURCES.find((entry) => entry.key === variant.sourceKey);
    const adapter = source?.adapter ? sourceAdapters[source.adapter] : undefined;
    if (!source || !adapter) throw new Error("source adapter unavailable");
    const result = await cached(
      `detail:${source.key}:${variant.sourceMediaId}`,
      30 * 60 * 1000,
      () => adapter.detail(variant.sourceMediaId, AbortSignal.timeout(12000)),
    );
    return {
      variant: { ...variant, sourceTitle: variant.sourceTitle || source.title },
      item: result.item,
      episodes: result.episodes,
    };
  });

  const details = outcomes.flatMap((outcome) => outcome.value ? [outcome.value] : []);
  const errors = outcomes.flatMap((outcome, index) => outcome.error ? [{
    sourceKey: unique[index].sourceKey,
    error: outcome.error,
  }] : []);

  if (!details.length) {
    return NextResponse.json({ error: "all source details failed", errors }, { status: 502 });
  }

  const merged = mergeDetails(details);
  return NextResponse.json({ ...merged.item, episodes: merged.episodes, errors });
}
