"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import {
  Bookmark, Captions, Check, ChevronLeft, ChevronRight, Download, Expand,
  Gauge, Heart, Layers3, ListVideo, Maximize, Minimize, MoreVertical,
  Pause, PictureInPicture2, Play, RotateCcw, Share2, SkipBack, SkipForward,
  SlidersHorizontal, Volume2, VolumeX, X,
} from "lucide-react";
import type { PlayerSession } from "./types";

type Panel = "episodes" | "sources" | "speed" | "danmaku" | "more" | null;

type Props = {
  session: PlayerSession;
  favorite: boolean;
  onClose: () => void;
  onResolve: (episodeIndex: number, sourceIndex: number) => Promise<void>;
  onFavorite: () => void;
  onNotice: (message: string) => void;
};

const speedOptions = [0.5, 0.75, 1, 1.25, 1.5, 2, 3];

function timeLabel(value: number): string {
  if (!Number.isFinite(value) || value < 0) return "00:00";
  const total = Math.floor(value);
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  return hours
    ? `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`
    : `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

export function MediaPlayer({ session, favorite, onClose, onResolve, onFavorite, onNotice }: Props) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const shellRef = useRef<HTMLElement>(null);
  const lastSavedSecond = useRef(-1);
  const [playing, setPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [muted, setMuted] = useState(false);
  const [rate, setRate] = useState(1);
  const [panel, setPanel] = useState<Panel>(null);
  const [fullscreen, setFullscreen] = useState(false);
  const [controlsVisible, setControlsVisible] = useState(true);
  const [danmakuEnabled, setDanmakuEnabled] = useState(false);
  const [danmakuOpacity, setDanmakuOpacity] = useState(75);
  const [danmakuSize, setDanmakuSize] = useState(50);
  const [selectedQuality, setSelectedQuality] = useState("");

  const episode = session.episodes[session.episodeIndex];
  const source = episode?.sources[session.sourceIndex];
  const progressKey = `juying:progress:${session.media.id}`;
  const hasPrevious = session.episodeIndex > 0;
  const hasNext = session.episodeIndex < session.episodes.length - 1;
  const pipSupported = typeof document !== "undefined" && Boolean(document.pictureInPictureEnabled);

  const sourceLabel = useMemo(
    () => source ? `${source.sourceTitle} · ${source.route}` : session.route || "来源直连",
    [session.route, source],
  );

  useEffect(() => {
    const onFullscreen = () => setFullscreen(Boolean(document.fullscreenElement));
    document.addEventListener("fullscreenchange", onFullscreen);
    return () => document.removeEventListener("fullscreenchange", onFullscreen);
  }, []);

  function saveProgress(position = currentTime, total = duration) {
    if (!episode) return;
    try {
      localStorage.setItem(progressKey, JSON.stringify({
        episodeId: episode.id,
        episodeIndex: session.episodeIndex,
        sourceKey: source?.sourceKey || session.media.sourceKey,
        position,
        duration: total,
        completed: total > 0 && position / total >= 0.9,
        updatedAt: new Date().toISOString(),
      }));
    } catch { /* device storage can be unavailable */ }
  }

  function togglePlay() {
    const video = videoRef.current;
    if (!video) return;
    if (video.paused) void video.play();
    else video.pause();
  }

  function seek(value: number) {
    const video = videoRef.current;
    if (!video) return;
    video.currentTime = value;
    setCurrentTime(value);
  }

  function changeRate(value: number) {
    const video = videoRef.current;
    if (video) video.playbackRate = value;
    setRate(value);
    setPanel(null);
    try { localStorage.setItem("juying:playback-rate", String(value)); } catch { /* ignore */ }
  }

  async function togglePictureInPicture() {
    const video = videoRef.current;
    if (!video || !pipSupported) return onNotice("当前浏览器不支持画中画");
    try {
      if (document.pictureInPictureElement) await document.exitPictureInPicture();
      else await video.requestPictureInPicture();
    } catch {
      onNotice("画中画启动失败，可能被当前设备或视频协议限制");
    }
  }

  async function toggleFullscreen() {
    try {
      if (document.fullscreenElement) await document.exitFullscreen();
      else await shellRef.current?.requestFullscreen();
    } catch {
      onNotice("当前设备无法进入全屏");
    }
  }

  async function share() {
    const text = `${session.media.title} · ${episode?.name || ""}`;
    try {
      if (navigator.share) await navigator.share({ title: session.media.title, text, url: location.href });
      else {
        await navigator.clipboard.writeText(`${text} ${location.href}`);
        onNotice("分享链接已复制");
      }
    } catch { /* user cancellation is not an error */ }
  }

  function openPanel(next: Exclude<Panel, null>) {
    setPanel((current) => current === next ? null : next);
    setControlsVisible(true);
  }

  return <div className="overlay player-overlay">
    <section ref={shellRef} className={`player-shell ${controlsVisible ? "controls-visible" : ""}`} role="dialog" aria-modal="true" aria-label="视频播放器">
      <div className="player-video-stage" onClick={() => setControlsVisible((value) => !value)}>
        <video
          ref={videoRef}
          autoPlay
          playsInline
          poster={session.media.cover}
          src={session.url}
          onLoadedMetadata={(event) => {
            const video = event.currentTarget;
            setDuration(video.duration || 0);
            const storedRate = Number(localStorage.getItem("juying:playback-rate") || "1");
            if (speedOptions.includes(storedRate)) {
              video.playbackRate = storedRate;
              setRate(storedRate);
            }
            try {
              const saved = JSON.parse(localStorage.getItem(progressKey) || "{}") as { episodeId?: string; position?: number };
              if (saved.episodeId === episode?.id && saved.position && saved.position < video.duration - 10) {
                video.currentTime = saved.position;
                setCurrentTime(saved.position);
                onNotice(`已续播到 ${timeLabel(saved.position)}`);
              }
            } catch { /* ignore malformed progress */ }
          }}
          onTimeUpdate={(event) => {
            const video = event.currentTarget;
            setCurrentTime(video.currentTime);
            const second = Math.floor(video.currentTime);
            if (second > 0 && second % 12 === 0 && second !== lastSavedSecond.current) {
              lastSavedSecond.current = second;
              saveProgress(video.currentTime, video.duration);
            }
          }}
          onDurationChange={(event) => setDuration(event.currentTarget.duration || 0)}
          onPlay={() => setPlaying(true)}
          onPause={(event) => { setPlaying(false); saveProgress(event.currentTarget.currentTime, event.currentTarget.duration); }}
          onEnded={() => {
            saveProgress(duration, duration);
            if (hasNext) void onResolve(session.episodeIndex + 1, 0);
          }}
          onError={() => onNotice("当前线路无法播放，请打开换源面板尝试其他线路")}
        />

        {danmakuEnabled && <div className="danmaku-empty" style={{ opacity: danmakuOpacity / 100, fontSize: `${12 + danmakuSize / 10}px` }}>暂无弹幕通道</div>}

        <div className="player-top-controls">
          <button onClick={(event) => { event.stopPropagation(); saveProgress(); onClose(); }} aria-label="返回"><ChevronLeft size={24} /></button>
          <div><strong>{session.media.title} · {episode?.name}</strong><span>{sourceLabel}</span></div>
          <button onClick={(event) => { event.stopPropagation(); openPanel("more"); }} aria-label="更多操作"><MoreVertical size={22} /></button>
        </div>

        <button className="player-center-play" onClick={(event) => { event.stopPropagation(); togglePlay(); }} aria-label={playing ? "暂停" : "播放"}>
          {playing ? <Pause size={30} fill="currentColor" /> : <Play size={30} fill="currentColor" />}
        </button>

        <div className="player-bottom-controls" onClick={(event) => event.stopPropagation()}>
          <div className="progress-line">
            <span>{timeLabel(currentTime)}</span>
            <input aria-label="播放进度" type="range" min="0" max={duration || 0} step="0.1" value={Math.min(currentTime, duration || 0)} onChange={(event) => seek(Number(event.target.value))} />
            <span>{timeLabel(duration)}</span>
          </div>
          <div className="control-line">
            <button onClick={togglePlay} aria-label={playing ? "暂停" : "播放"}>{playing ? <Pause size={20} fill="currentColor" /> : <Play size={20} fill="currentColor" />}</button>
            <button disabled={!hasPrevious} onClick={() => void onResolve(session.episodeIndex - 1, 0)} aria-label="上一集"><SkipBack size={19} /></button>
            <button disabled={!hasNext} onClick={() => void onResolve(session.episodeIndex + 1, 0)} aria-label="下一集"><SkipForward size={19} /></button>
            <button onClick={() => { const video = videoRef.current; if (!video) return; video.muted = !video.muted; setMuted(video.muted); }} aria-label={muted ? "取消静音" : "静音"}>{muted ? <VolumeX size={19} /> : <Volume2 size={19} />}</button>
            <span className="player-route-label">{source?.sourceTitle || session.media.sourceTitle}</span>
            <button onClick={togglePictureInPicture} disabled={!pipSupported} aria-label="画中画"><PictureInPicture2 size={19} /></button>
            <button onClick={toggleFullscreen} aria-label={fullscreen ? "退出全屏" : "全屏"}>{fullscreen ? <Minimize size={19} /> : <Maximize size={19} />}</button>
          </div>
        </div>
      </div>

      <div className="player-action-row">
        <button onClick={() => openPanel("episodes")}><ListVideo size={18} /><span>选集</span></button>
        <button onClick={() => openPanel("sources")}><Layers3 size={18} /><span>换源</span><b>{episode?.sources.length || 0}</b></button>
        <button onClick={() => openPanel("speed")}><Gauge size={18} /><span>{rate}x</span></button>
        <button className={danmakuEnabled ? "active" : ""} onClick={() => setDanmakuEnabled((value) => !value)}><Captions size={18} /><span>弹幕</span></button>
        <button onClick={() => openPanel("danmaku")}><SlidersHorizontal size={18} /><span>弹幕设置</span></button>
        <button onClick={onFavorite} className={favorite ? "active favorite" : ""}>{favorite ? <Heart size={18} fill="currentColor" /> : <Bookmark size={18} />}<span>收藏</span></button>
        <button onClick={() => void share()}><Share2 size={18} /><span>分享</span></button>
        <button disabled title="来源未声明允许离线缓存"><Download size={18} /><span>缓存</span></button>
      </div>

      {session.qualityOptions?.length ? <div className="player-quality-row"><span>清晰度</span><button className={!selectedQuality ? "active" : ""} onClick={() => { setSelectedQuality(""); onNotice("已切回自动清晰度"); }}>AUTO</button>{session.qualityOptions.map((quality) => <button key={quality.id} className={selectedQuality === quality.id ? "active" : ""} onClick={() => {
        if (!quality.url) return;
        const position = videoRef.current?.currentTime || 0;
        setSelectedQuality(quality.id);
        if (videoRef.current) {
          videoRef.current.src = quality.url;
          videoRef.current.currentTime = position;
          void videoRef.current.play();
        }
      }}>{quality.name}</button>)}</div> : null}

      {panel && <div className="player-panel">
        <div className="player-panel-head">
          <strong>{panel === "episodes" ? "选集" : panel === "sources" ? "播放来源" : panel === "speed" ? "播放倍速" : panel === "danmaku" ? "弹幕设置" : "更多功能"}</strong>
          <button onClick={() => setPanel(null)} aria-label="关闭面板"><X size={18} /></button>
        </div>

        {panel === "episodes" && <div className="player-episode-grid">{session.episodes.map((item, index) => <button key={item.id} className={index === session.episodeIndex ? "active" : ""} onClick={() => void onResolve(index, 0)}><span>{item.name}</span><small>{item.sources.length} 个来源</small>{index === session.episodeIndex && <Check size={14} />}</button>)}</div>}

        {panel === "sources" && <div className="player-source-list">{episode?.sources.map((item, index) => <button key={`${item.sourceKey}-${item.route}-${index}`} className={index === session.sourceIndex ? "active" : ""} onClick={() => void onResolve(session.episodeIndex, index)}><Layers3 size={17} /><span><strong>{item.sourceTitle}</strong><small>{item.route}</small></span>{index === session.sourceIndex ? <Check size={17} /> : <ChevronRight size={17} />}</button>)}</div>}

        {panel === "speed" && <div className="speed-grid">{speedOptions.map((value) => <button key={value} className={rate === value ? "active" : ""} onClick={() => changeRate(value)}>{value}x</button>)}</div>}

        {panel === "danmaku" && <div className="danmaku-settings">
          <label><span>显示弹幕<small>当前没有接入授权弹幕通道</small></span><input type="checkbox" checked={danmakuEnabled} onChange={(event) => setDanmakuEnabled(event.target.checked)} /></label>
          <label><span>透明度</span><input type="range" min="10" max="100" value={danmakuOpacity} onChange={(event) => setDanmakuOpacity(Number(event.target.value))} /><b>{danmakuOpacity}%</b></label>
          <label><span>字号</span><input type="range" min="10" max="100" value={danmakuSize} onChange={(event) => setDanmakuSize(Number(event.target.value))} /><b>{danmakuSize}%</b></label>
        </div>}

        {panel === "more" && <div className="player-more-grid">
          <button onClick={() => { const video = videoRef.current; if (video) seek(Math.max(0, video.currentTime - 10)); }}><RotateCcw size={19} />后退 10 秒</button>
          <button onClick={() => { const video = videoRef.current; if (video) seek(Math.min(video.duration, video.currentTime + 10)); }}><ChevronRight size={19} />前进 10 秒</button>
          <button onClick={() => void toggleFullscreen()}><Expand size={19} />横屏/全屏</button>
          <button onClick={() => void share()}><Share2 size={19} />分享当前集</button>
        </div>}
      </div>}

      <p className="player-storage-note">媒体由来源方直连，聚映不保存视频文件。离线缓存仅在来源明确允许时开放。</p>
    </section>
  </div>;
}
