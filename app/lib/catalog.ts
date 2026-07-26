import type {
  CanonicalEpisode,
  CanonicalEpisodeSource,
  CanonicalMedia,
  Episode,
  SourceItem,
  SourceVariant,
} from "./adapters/types";

export type SourcedItem = SourceItem & { sourceTitle: string; score?: string; variants?: SourceVariant[] };
export type SourcedDetail = {
  variant: SourceVariant;
  item: SourceItem;
  episodes: Episode[];
};

function fnv1a(value: string): string {
  let hash = 0x811c9dc5;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return (hash >>> 0).toString(36);
}

export function normalizeTitle(value: string): string {
  return value
    .normalize("NFKC")
    .toLowerCase()
    .replace(/[～~·•・:：—–_\-\s()[\]【】《》<>“”"'!?！？,.，。]/g, "");
}

export function canonicalMediaId(title: string, year?: string): string {
  return `media_${fnv1a(`${normalizeTitle(title)}|${year || ""}`)}`;
}

export function toVariant(item: SourcedItem): SourceVariant {
  if (item.variants && item.variants.length > 0 && item.variants[0]?.sourceMediaId && !item.variants[0].sourceMediaId.startsWith("media_")) {
    return item.variants[0];
  }
  return {
    sourceKey: item.sourceKey || "",
    sourceTitle: item.sourceTitle || "",
    sourceMediaId: (item.id && item.id.startsWith("media_")) ? (item.variants?.[0]?.sourceMediaId || item.id) : (item.id || ""),
    title: item.title || "",
    year: item.year || "",
    kind: item.kind || "",
    cover: item.cover || "",
    description: item.description || "",
  };
}

export function mergeSearchItems(items: SourcedItem[]): CanonicalMedia[] {
  const grouped = new Map<string, CanonicalMedia>();
  const byTitle = new Map<string, string>(); // normalizedTitle → groupKey

  for (const item of items) {
    if (!item) continue;
    const rawVariants = (Array.isArray(item.variants) && item.variants.length) ? item.variants : [toVariant(item)];
    const existingVariants: SourceVariant[] = rawVariants.map((v) => ({
      ...v,
      sourceKey: v?.sourceKey || item.sourceKey || "",
      sourceTitle: v?.sourceTitle || item.sourceTitle || "",
      sourceMediaId: v?.sourceMediaId || item.id || "",
    }));

    const normTitle = normalizeTitle(item.title || "");
    const groupKey = `${normTitle}|${item.year || ""}`;
    let key = groupKey;
    let existing = grouped.get(key);

    if (!existing && !item.year) {
      const altKey = byTitle.get(normTitle);
      if (altKey) { key = altKey; existing = grouped.get(key); }
    }
    if (!existing && item.year) {
      const keyWithoutYear = `${normTitle}|`;
      const existingWithout = grouped.get(keyWithoutYear);
      if (existingWithout) {
        grouped.delete(keyWithoutYear);
        const merged: CanonicalMedia = {
          ...existingWithout,
          id: item.id && item.id.startsWith("media_") ? item.id : canonicalMediaId(item.title || "", item.year),
          year: item.year,
          kind: item.kind || existingWithout.kind,
          cover: item.cover || existingWithout.cover,
          description: item.description || existingWithout.description,
          score: item.score || existingWithout.score,
          variants: Array.isArray(existingWithout.variants) ? [...existingWithout.variants] : [],
        };
        grouped.set(key, merged);
        existing = merged;
      }
    }

    if (!existing) {
      const entry: CanonicalMedia = {
        id: item.id && item.id.startsWith("media_") ? item.id : canonicalMediaId(item.title || "", item.year),
        title: item.title || "",
        year: item.year || "",
        kind: item.kind || "",
        tags: item.tags,
        status: item.status || "",
        score: item.score || "8.5",
        cover: item.cover || "",
        description: item.description || "",
        sourceKey: item.sourceKey || "",
        sourceTitle: item.sourceTitle || "",
        sourceCount: existingVariants.length,
        variants: [...existingVariants],
      };
      grouped.set(key, entry);
      byTitle.set(normTitle, key);
      continue;
    }

    if (!Array.isArray(existing.variants)) {
      existing.variants = [];
    }

    for (const variant of existingVariants) {
      const vId = variant.sourceMediaId || "";
      if (!vId.startsWith("media_") && !existing.variants.some((entry) => (entry?.sourceKey === variant.sourceKey) && ((entry?.sourceMediaId || "") === vId))) {
        existing.variants.push(variant);
      }
    }
    existing.sourceCount = existing.variants.length;
    if (!existing.cover && item.cover) existing.cover = item.cover;
    if (!existing.description && item.description) existing.description = item.description;
    if (!existing.kind && item.kind) existing.kind = item.kind;
    if (!existing.year && item.year) existing.year = item.year;
    if (!existing.status && item.status) existing.status = item.status;
    if (item.tags?.length) {
      const mergedTags = new Set(existing.tags || []);
      for (const t of item.tags) mergedTags.add(t);
      existing.tags = [...mergedTags];
    }
  }

  return [...grouped.values()];
}

function episodeNumber(name: string): number | undefined {
  const match = name.match(/(?:第\s*)?(\d+(?:\.\d+)?)\s*(?:集|话|話|期|$)/i) || name.match(/(\d+(?:\.\d+)?)/);
  if (!match) return undefined;
  const value = Number(match[1]);
  return Number.isFinite(value) ? value : undefined;
}

function episodeKey(episode: Episode, index: number): string {
  const number = episodeNumber(episode.name);
  if (number !== undefined) return `number:${number}`;
  const normalized = normalizeTitle(episode.name);
  return normalized ? `name:${normalized}` : `index:${index}`;
}

export function mergeDetails(details: SourcedDetail[]): { item: CanonicalMedia; episodes: CanonicalEpisode[] } {
  if (!details.length) throw new Error("no source detail available");
  const primary = details[0];
  const variants = details.map((detail) => detail.variant);
  const episodes = new Map<string, CanonicalEpisode>();

  for (const detail of details) {
    detail.episodes.forEach((episode, index) => {
      const key = episodeKey(episode, index);
      const source: CanonicalEpisodeSource = {
        sourceKey: detail.variant.sourceKey,
        sourceTitle: detail.variant.sourceTitle,
        sourceMediaId: detail.variant.sourceMediaId,
        route: episode.route,
        flag: episode.flag,
      };
      const existing = episodes.get(key);
      if (existing) {
        existing.sources.push(source);
      } else {
        episodes.set(key, {
          id: key.replace(":", "_"),
          name: episode.name,
          number: episodeNumber(episode.name),
          sources: [source],
        });
      }
    });
  }

  const sortedEpisodes = [...episodes.values()].sort((left, right) => {
    if (left.number !== undefined && right.number !== undefined) return left.number - right.number;
    if (left.number !== undefined) return -1;
    if (right.number !== undefined) return 1;
    return left.name.localeCompare(right.name, "zh-CN");
  });

  const primaryItem = primary.item;
  const bestYear = primaryItem.year || details.find((d) => d.item.year)?.item.year || "";
  const bestKind = primaryItem.kind || details.find((d) => d.item.kind)?.item.kind || "";
  const bestCover = primaryItem.cover || details.find((d) => d.item.cover)?.item.cover || "";
  const bestDesc = primaryItem.description || details.find((d) => d.item.description)?.item.description || "";
  const bestStatus = primaryItem.status || details.find((d) => d.item.status)?.item.status || "";
  const mergedTags = [...new Set(details.flatMap((d) => d.item.tags || []))];
  return {
    item: {
      id: canonicalMediaId(primaryItem.title, bestYear),
      title: primaryItem.title,
      year: bestYear,
      kind: bestKind,
      tags: mergedTags.length ? mergedTags : undefined,
      status: bestStatus,
      cover: bestCover,
      description: bestDesc,
      sourceKey: primary.variant.sourceKey,
      sourceTitle: primary.variant.sourceTitle,
      sourceCount: variants.length,
      variants,
      episodes: sortedEpisodes,
    },
    episodes: sortedEpisodes,
  };
}
