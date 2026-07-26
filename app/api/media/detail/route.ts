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

  const outcomes = await mapWithConcurrency(unique, 5, async (variant): Promise<SourcedDetail> => {
    const source = SOURCES.find((entry) => entry.key === variant.sourceKey);
    const adapter = source ? sourceAdapters[source.adapter ?? source.key] : undefined;
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
    const primary = unique[0];
    return NextResponse.json({
      id: primary.sourceMediaId,
      title: primary.title || "未命名影片",
      year: primary.year || "",
      kind: primary.kind || "影视",
      score: "-",
      cover: primary.cover || "",
      description: primary.description || "所选来源的网络响应超时或已被清理，无法完成全量剧集解析。",
      sourceKey: primary.sourceKey,
      sourceTitle: primary.sourceTitle || primary.sourceKey,
      sourceCount: unique.length,
      variants: unique,
      episodes: [],
      error: "第三方来源详情拉取超时",
      errors,
    });
  }

  const merged = mergeDetails(details);
  return NextResponse.json({ ...merged.item, episodes: merged.episodes, errors });
}
