export type PlayerSource = {
  sourceKey: string;
  sourceTitle: string;
  sourceMediaId: string;
  route: string;
  flag: Record<string, string>;
};

export type PlayerEpisode = {
  id: string;
  name: string;
  number?: number;
  sources: PlayerSource[];
};

export type PlayerMedia = {
  id: string;
  title: string;
  cover?: string;
  sourceKey: string;
  sourceTitle: string;
};

export type PlayerQuality = {
  id: string;
  name: string;
  url?: string;
  width?: number;
  height?: number;
  bitrate?: number;
};

export type PlayerSession = {
  media: PlayerMedia;
  episodes: PlayerEpisode[];
  episodeIndex: number;
  sourceIndex: number;
  url: string;
  type?: string;
  route?: string;
  qualityOptions?: PlayerQuality[];
};
