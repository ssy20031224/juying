export type MediaType = "m3u8" | "mp4" | "flv" | "auto";

export type SourceItem = {
  sourceKey: string;
  id: string;
  title: string;
  year?: string;
  kind?: string;
  cover?: string;
  description?: string;
  sourceCount: number;
};

export type SourceVariant = {
  sourceKey: string;
  sourceTitle: string;
  sourceMediaId: string;
  title: string;
  year?: string;
  kind?: string;
  cover?: string;
  description?: string;
};

export type CanonicalEpisodeSource = {
  sourceKey: string;
  sourceTitle: string;
  sourceMediaId: string;
  route: string;
  flag: Record<string, string>;
};

export type CanonicalEpisode = {
  id: string;
  name: string;
  number?: number;
  sources: CanonicalEpisodeSource[];
};

export type CanonicalMedia = {
  id: string;
  title: string;
  year?: string;
  kind?: string;
  score?: string;
  cover?: string;
  description?: string;
  sourceKey: string;
  sourceTitle: string;
  sourceCount: number;
  variants: SourceVariant[];
  episodes?: CanonicalEpisode[];
};

export type Episode = {
  id: string;
  name: string;
  route: string;
  flag: Record<string, string>;
};

export type QualityOption = {
  id: string;
  name: string;
  url: string;
  type: MediaType;
  width?: number;
  height?: number;
  bitrate?: number;
};

export type PlayResult = {
  url: string;
  type: MediaType;
  headers?: Record<string, string>;
  referer?: string;
  resolutions?: QualityOption[];
  expiresAt?: string;
};

export interface SourceAdapter {
  search(query: string, page: number, signal: AbortSignal): Promise<SourceItem[]>;
  detail(id: string, signal: AbortSignal): Promise<{ item: SourceItem; episodes: Episode[] }>;
  play(flag: Episode["flag"], signal: AbortSignal): Promise<PlayResult>;
}
