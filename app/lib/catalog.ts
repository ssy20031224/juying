import type {
  CanonicalEpisode,
  CanonicalEpisodeSource,
  CanonicalMedia,
  Episode,
  SourceItem,
  SourceVariant,
} from "./adapters/types";

export type SourcedItem = SourceItem & { sourceTitle: string; score?: string };
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
  return {
    sourceKey: item.sourceKey,
    sourceTitle: item.sourceTitle,
    sourceMediaId: item.id,
    title: item.title,
    year: item.year,
    kind: item.kind,
    cover: item.cover,
    description: item.description,
  };
}

export function mergeSearchItems(items: SourcedItem[]): CanonicalMedia[] {
  const grouped = new Map<string, CanonicalMedia>();

  for (const item of items) {
    const groupKey = `${normalizeTitle(item.title)}|${item.year || ""}`;
    const variant = toVariant(item);
    const existing = grouped.get(groupKey);
    if (!existing) {
      grouped.set(groupKey, {
        id: canonicalMediaId(item.title, item.year),
        title: item.title,
        year: item.year,
        kind: item.kind,
        score: item.score,
        cover: item.cover,
        description: item.description,
        sourceKey: item.sourceKey,
        sourceTitle: item.sourceTitle,
        sourceCount: 1,
        variants: [variant],
      });
      continue;
    }

    if (!existing.variants.some((entry) => entry.sourceKey === variant.sourceKey && entry.sourceMediaId === variant.sourceMediaId)) {
      existing.variants.push(variant);
    }
    existing.sourceCount = existing.variants.length;
    if (!existing.cover && item.cover) existing.cover = item.cover;
    if (!existing.description && item.description) existing.description = item.description;
    if (!existing.kind && item.kind) existing.kind = item.kind;
    if (!existing.year && item.year) existing.year = item.year;
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
  return {
    item: {
      id: canonicalMediaId(primaryItem.title, primaryItem.year),
      title: primaryItem.title,
      year: primaryItem.year,
      kind: primaryItem.kind,
      cover: primaryItem.cover,
      description: primaryItem.description,
      sourceKey: primary.variant.sourceKey,
      sourceTitle: primary.variant.sourceTitle,
      sourceCount: variants.length,
      variants,
      episodes: sortedEpisodes,
    },
    episodes: sortedEpisodes,
  };
}
