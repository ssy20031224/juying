"use client";

import { useEffect, useMemo, useRef, useState, useCallback } from "react";
import {
  Bookmark, Check, ChevronLeft, ChevronRight, Expand,
  Heart, Info, Layers3, Maximize, Minimize, MoreVertical,
  Pause, PictureInPicture2, Play, RotateCcw, Share2, SkipBack, SkipForward,
  Volume2, VolumeX, X, Sliders,
} from "lucide-react";
import type { PlayerSession } from "./types";

type Panel = "episodes" | "sources" | "speed" | "quality" | "danmaku" | "more" | null;

type Props = {
  session: PlayerSession;
  favorite: boolean;
  onClose: () => void;
  onResolve: (episodeIndex: number, sourceIndex: number) => Promise<void>;
  onFavorite: () => void;
  onNotice: (message: string) => void;
  onProgressUpdate?: (media: PlayerSession["media"], episodeName: string, episodeIndex: number, currentTime: number, duration: number) => void;
};

const speedOptions = [0.5, 0.75, 1, 1.25, 1.5, 2, 3];

function formatQualityName(rawName: string, height?: number): string {
  if (!rawName || rawName.toUpperCase() === "AUTO" || rawName === "自动") return "自动";
  const lower = rawName.toLowerCase();

  if (height) {
    if (height >= 2160 || lower.includes("4k") || lower.includes("2160")) return "4K";
    if (height >= 1080 || lower.includes("1080") || lower.includes("fhd")) return "1080P";
    if (height >= 720 || lower.includes("720") || lower.includes("hd")) return "720P";
    if (height >= 480 || lower.includes("480") || lower.includes("sd")) return "480P";
    if (height >= 360 || lower.includes("360") || lower.includes("ld")) return "360P";
  }

  if (lower.includes("4k") || lower.includes("2160") || lower === "uhd") return "4K";
  if (lower.includes("1080") || lower.includes("fhd") || lower === "high" || lower === "超清") return "1080P";
  if (lower.includes("720") || lower.includes("hd") || lower === "medium" || lower === "高清") return "720P";
  if (lower.includes("480") || lower.includes("sd") || lower === "low" || lower === "标清") return "480P";
  if (lower.includes("360") || lower.includes("ld") || lower === "流畅") return "360P";

  return rawName.toUpperCase();
}

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

export function MediaPlayer({ session, favorite, onClose, onResolve, onFavorite, onNotice, onProgressUpdate }: Props) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const shellRef = useRef<HTMLElement>(null);
  const hlsRef = useRef<import("hls.js").default | null>(null);
  const lastSavedSecond = useRef(-1);
  const resumeAfterSourceChange = useRef(0);
  const clickTimerRef = useRef<NodeJS.Timeout | null>(null);
  const osdTimerRef = useRef<NodeJS.Timeout | null>(null);
  const mouseTimerRef = useRef<NodeJS.Timeout | null>(null);
  const originalRateBeforeHold = useRef<number>(1);
  const onNoticeRef = useRef(onNotice);

  const [playing, setPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [muted, setMuted] = useState(false);
  const [volumeVal, setVolumeVal] = useState(1);
  const [rate, setRate] = useState(1);
  const [panel, setPanel] = useState<Panel>(null);
  const [fullscreen, setFullscreen] = useState(false);
  const [controlsVisible, setControlsVisible] = useState(true);
  const [danmakuEnabled, setDanmakuEnabled] = useState(false);
  const [danmakuOpacity, setDanmakuOpacity] = useState(75);
  const [danmakuSize, setDanmakuSize] = useState(50);
  const [selectedQuality, setSelectedQuality] = useState("");
  const [activeUrl, setActiveUrl] = useState(session.url);
  const [mediaError, setMediaError] = useState("");
  const [osdText, setOsdText] = useState("");
  const [isLongPressing, setIsLongPressing] = useState(false);
  const [hlsQualities, setHlsQualities] = useState<Array<{ id: string; name: string; levelIndex: number }>>([]);
  const autoRetryRef = useRef(0);

  useEffect(() => {
    onNoticeRef.current = onNotice;
  }, [onNotice]);

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

  const allQualities = useMemo(() => {
    const list: Array<{ id: string; name: string; url?: string; levelIndex?: number }> = [];
    if (session.qualityOptions && session.qualityOptions.length > 0) {
      session.qualityOptions.forEach((q) => {
        list.push({
          id: q.id,
          name: formatQualityName(q.name, q.height),
          url: q.url,
        });
      });
    } else if (hlsQualities.length > 0) {
      hlsQualities.forEach((q) => {
        list.push({
          id: q.id,
          name: q.name,
          levelIndex: q.levelIndex,
        });
      });
    }
    return list;
  }, [session.qualityOptions, hlsQualities]);

  const selectedQualityLabel = useMemo(() => {
    if (!selectedQuality) return "自动";
    const found = allQualities.find((q) => q.id === selectedQuality);
    return found ? found.name : "自动";
  }, [selectedQuality, allQualities]);

  function showOsd(text: string, duration = 1200) {
    setOsdText(text);
    if (osdTimerRef.current) clearTimeout(osdTimerRef.current);
    if (duration > 0) {
      osdTimerRef.current = setTimeout(() => {
        setOsdText("");
      }, duration);
    }
  }

  function togglePlay() {
    const video = videoRef.current;
    if (!video) return;
    if (video.paused) {
      void video.play().catch(() => {
        setMediaError("当前线路格式不受支持，请尝试换源");
        onNoticeRef.current("当前线路格式不受支持，请尝试换源");
      });
      showOsd("播放");
    } else {
      video.pause();
      showOsd("已暂停");
    }
  }

  // Mouse movement auto-hide controls handler
  function handleMouseMove() {
    setControlsVisible(true);
    if (mouseTimerRef.current) clearTimeout(mouseTimerRef.current);
    if (playing && !panel) {
      mouseTimerRef.current = setTimeout(() => {
        setControlsVisible(false);
      }, 4000);
    }
  }

  useEffect(() => {
    if (playing && !panel) {
      if (mouseTimerRef.current) clearTimeout(mouseTimerRef.current);
      mouseTimerRef.current = setTimeout(() => {
        setControlsVisible(false);
      }, 4000);
    } else {
      if (mouseTimerRef.current) clearTimeout(mouseTimerRef.current);
      setControlsVisible(true);
    }
  }, [playing, panel]);

  useEffect(() => {
    const onFullscreen = () => setFullscreen(Boolean(document.fullscreenElement));
    document.addEventListener("fullscreenchange", onFullscreen);
    return () => document.removeEventListener("fullscreenchange", onFullscreen);
  }, []);

  // Video initialization effect - strictly dependent on activeUrl and session.type ONLY
  useEffect(() => {
    const video = videoRef.current;
    if (!video || !activeUrl) return;
    let disposed = false;
    setMediaError("");
    setHlsQualities([]);
    autoRetryRef.current = 0;

    const fail = (message: string) => {
      if (disposed) return;
      setPlaying(false);
      setMediaError(message);
      onNoticeRef.current(message);
    };
    const start = () => {
      if (disposed || !video) return;
      if (!activeUrl || !activeUrl.trim()) {
        fail("当前线路暂无有效的视频地址，请尝试切换其他线路");
        return;
      }
      try {
        const promise = video.play();
        if (promise !== undefined) {
          promise.catch((err: unknown) => {
            if (disposed) return;
            const errName = err && typeof err === "object" && "name" in err ? String(err.name) : "";
            if (errName === "NotSupportedError" && !isHls) {
              // 降级策略：某些代理/动态 URL 未显式包含 .m3u8 后缀，当原生播放抛出 NotSupportedError 时尝试 Hls.js
              void import("hls.js").then(({ default: Hls }) => {
                if (disposed || !video) return;
                if (Hls.isSupported()) {
                  const hls = new Hls({ enableWorker: true, lowLatencyMode: true });
                  hlsRef.current = hls;
                  hls.on(Hls.Events.MANIFEST_PARSED, () => {
                    void video.play().catch(() => fail("当前线路音视频编码不支持，正在自动换源"));
                  });
                  hls.on(Hls.Events.ERROR, (_event, data) => {
                    if (data.fatal) fail("HLS 解轨失败，正在自动换源");
                  });
                  hls.loadSource(activeUrl);
                  hls.attachMedia(video);
                  return;
                }
                fail("浏览器暂不支持当前线路或音视频编码，正在自动换源");
              }).catch(() => fail("浏览器暂不支持当前线路或音视频编码，请点击下方「换源」按钮"));
              return;
            }
            const message = errName === "NotSupportedError"
              ? "浏览器暂不支持当前线路或音视频编码，正在自动换源"
              : "视频播放在当前设备受阻，请更换线路或重试";
            fail(message);
          });
        }
      } catch {
        fail("视频初始化失败，请尝试切换其他线路");
      }
    };

    let decodedUrl = activeUrl;
    try { decodedUrl = decodeURIComponent(activeUrl); } catch { /* ignore */ }

    const isHls = session.type === "m3u8"
      || /\.m3u8(?:$|[?#])/i.test(activeUrl)
      || /\.m3u8(?:$|[?#])/i.test(decodedUrl)
      || activeUrl.toLowerCase().includes("m3u8")
      || decodedUrl.toLowerCase().includes("m3u8");

    if (isHls && !video.canPlayType("application/vnd.apple.mpegurl")) {
      void import("hls.js").then(({ default: Hls }) => {
        if (disposed) return;
        if (!Hls.isSupported()) {
          fail("当前浏览器不支持 HLS 流播放，正在自动换源");
          return;
        }
        const hls = new Hls({ enableWorker: true, lowLatencyMode: true });
        hlsRef.current = hls;

        hls.on(Hls.Events.MANIFEST_PARSED, (_event, data) => {
          if (data.levels && data.levels.length > 1) {
            const parsed = data.levels.map((lvl, index) => ({
              id: `hls_level_${index}`,
              name: formatQualityName(lvl.name || "", lvl.height),
              levelIndex: index,
            }));
            setHlsQualities(parsed);
          }
          start();
        });

        hls.on(Hls.Events.ERROR, (_event, data) => {
          if (data.fatal) fail("HLS 流加载中断，正在自动换源");
        });
        hls.loadSource(activeUrl);
        hls.attachMedia(video);
      }).catch(() => fail("播放组件准备就绪前中断，请重试"));
    } else if (activeUrl && activeUrl.trim()) {
      video.src = activeUrl;
      video.load();
      start();
    } else {
      fail("该剧集未返回合法播放链接，正在自动换源");
    }

    return () => {
      disposed = true;
      if (hlsRef.current) {
        hlsRef.current.destroy();
        hlsRef.current = null;
      }
      video.pause();
      video.removeAttribute("src");
      video.load();
    };
  }, [activeUrl, session.type]);

  const stateRef = useRef({ isLongPressing });
  useEffect(() => {
    stateRef.current = { isLongPressing };
  }, [isLongPressing]);

  // Keyboard Shortcuts Handler - Mounted once
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      const activeElement = document.activeElement;
      const isInput = activeElement && (
        activeElement.tagName === "INPUT" ||
        activeElement.tagName === "TEXTAREA" ||
        activeElement.getAttribute("contenteditable") === "true"
      );
      if (isInput) return;

      const video = videoRef.current;
      if (!video) return;

      switch (event.code) {
        case "Space":
          event.preventDefault();
          togglePlay();
          break;

        case "ArrowLeft":
          event.preventDefault();
          seek(Math.max(0, video.currentTime - 15));
          showOsd("快退 15 秒");
          break;

        case "ArrowRight":
          event.preventDefault();
          if (event.repeat) {
            if (!stateRef.current.isLongPressing) {
              originalRateBeforeHold.current = video.playbackRate;
              video.playbackRate = 3.0;
              setIsLongPressing(true);
              showOsd("3.0X 极速播放中...", 0);
            }
          } else {
            seek(Math.min(video.duration || 0, video.currentTime + 15));
            showOsd("快进 15 秒");
          }
          break;

        case "ArrowUp": {
          event.preventDefault();
          const nextVol = Math.min(1, video.volume + 0.1);
          video.volume = nextVol;
          setVolumeVal(nextVol);
          video.muted = false;
          setMuted(false);
          showOsd(`音量 ${Math.round(nextVol * 100)}%`);
          break;
        }

        case "ArrowDown": {
          event.preventDefault();
          const nextVol = Math.max(0, video.volume - 0.1);
          video.volume = nextVol;
          setVolumeVal(nextVol);
          if (nextVol === 0) {
            video.muted = true;
            setMuted(true);
          }
          showOsd(`音量 ${Math.round(nextVol * 100)}%`);
          break;
        }

        case "KeyF":
          event.preventDefault();
          void toggleFullscreen();
          break;

        case "KeyM":
          event.preventDefault();
          video.muted = !video.muted;
          setMuted(video.muted);
          showOsd(video.muted ? "已静音" : `音量 ${Math.round(video.volume * 100)}%`);
          break;
      }
    };

    const handleKeyUp = (event: KeyboardEvent) => {
      if (event.code === "ArrowRight" && stateRef.current.isLongPressing) {
        const video = videoRef.current;
        if (video) video.playbackRate = originalRateBeforeHold.current;
        setIsLongPressing(false);
        setOsdText("");
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    window.addEventListener("keyup", handleKeyUp);
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
      window.removeEventListener("keyup", handleKeyUp);
    };
  }, []);

  function saveProgress(position = currentTime, total = duration) {
    if (!episode) return;
    try {
      localStorage.setItem(progressKey, JSON.stringify({
        episodeId: episode.id,
        episodeName: episode.name,
        episodeIndex: session.episodeIndex,
        sourceKey: source?.sourceKey || session.media.sourceKey,
        position,
        duration: total,
        completed: total > 0 && position / total >= 0.9,
        updatedAt: new Date().toISOString(),
      }));
    } catch { /* device storage can be unavailable */ }

    if (onProgressUpdate) {
      onProgressUpdate(session.media, episode.name, session.episodeIndex, position, total);
    }
  }

  function handleVideoClick(event: React.MouseEvent) {
    event.stopPropagation();
    if (clickTimerRef.current) {
      clearTimeout(clickTimerRef.current);
      clickTimerRef.current = null;
      void toggleFullscreen();
    } else {
      clickTimerRef.current = setTimeout(() => {
        clickTimerRef.current = null;
        togglePlay();
      }, 220);
    }
  }

  function handleWheel(event: React.WheelEvent) {
    event.preventDefault();
    event.stopPropagation();
    const video = videoRef.current;
    if (!video) return;
    const delta = event.deltaY < 0 ? 0.05 : -0.05;
    const nextVolume = Math.min(1, Math.max(0, video.volume + delta));
    video.volume = nextVolume;
    setVolumeVal(nextVolume);
    video.muted = nextVolume === 0;
    setMuted(video.muted);
    showOsd(`音量 ${Math.round(nextVolume * 100)}%`);
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
    showOsd(`倍速 ${value}X`);
    try { localStorage.setItem("juying:playback-rate", String(value)); } catch { /* ignore */ }
  }

  async function togglePictureInPicture() {
    const video = videoRef.current;
    if (!video || !pipSupported) return onNoticeRef.current("当前浏览器不支持画中画");
    try {
      if (document.pictureInPictureElement) await document.exitPictureInPicture();
      else await video.requestPictureInPicture();
    } catch {
      onNoticeRef.current("画中画启动失败，可能被当前设备或视频协议限制");
    }
  }

  async function toggleFullscreen() {
    try {
      if (document.fullscreenElement) await document.exitFullscreen();
      else await shellRef.current?.requestFullscreen();
    } catch {
      onNoticeRef.current("当前设备无法进入全屏");
    }
  }

  async function share() {
    const text = `${session.media.title} · ${episode?.name || ""}`;
    try {
      if (navigator.share) await navigator.share({ title: session.media.title, text, url: location.href });
      else {
        await navigator.clipboard.writeText(`${text} ${location.href}`);
        onNoticeRef.current("分享链接已复制");
      }
    } catch { /* user cancellation is not an error */ }
  }

  function openPanel(next: Exclude<Panel, null>) {
    setPanel((current) => current === next ? null : next);
    setControlsVisible(true);
  }

  return <div className="overlay player-overlay">
    <section ref={shellRef} onMouseMove={handleMouseMove} className={`player-shell ${controlsVisible ? "controls-visible" : ""}`} role="dialog" aria-modal="true" aria-label="视频播放器">
      <div className={`player-video-stage ${!controlsVisible ? "hide-cursor" : ""}`} onWheel={handleWheel}>
        <video
          ref={videoRef}
          playsInline
          poster={session.media.cover}
          referrerPolicy={"no-referrer" as any}
          onClick={handleVideoClick}
          onLoadedMetadata={(event) => {
            const video = event.currentTarget;
            setDuration(video.duration || 0);
            setVolumeVal(video.volume);
            setMuted(video.muted);
            if (resumeAfterSourceChange.current > 0 && resumeAfterSourceChange.current < video.duration) {
              video.currentTime = resumeAfterSourceChange.current;
              setCurrentTime(resumeAfterSourceChange.current);
              resumeAfterSourceChange.current = 0;
            }
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
                onNoticeRef.current(`已续播到 ${timeLabel(saved.position)}`);
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
          onError={() => {
            setPlaying(false);
            // Auto-retry once: most failures are expired play URLs, re-resolving gets a fresh one
            if (autoRetryRef.current < 1) {
              autoRetryRef.current += 1;
              onNoticeRef.current("播放链接已过期，正在自动刷新...");
              void onResolve(session.episodeIndex, session.sourceIndex);
              return;
            }
            setMediaError("当前线路无法播放，请打开换源面板尝试其他线路");
            onNoticeRef.current("当前线路无法播放，请打开换源面板尝试其他线路");
          }}
        />

        {osdText && <div className="player-osd-badge">{osdText}</div>}
        {danmakuEnabled && <div className="danmaku-empty" style={{ opacity: danmakuOpacity / 100, fontSize: `${12 + danmakuSize / 10}px` }}>暂无弹幕通道</div>}
        {mediaError && <div className="player-error-card" onClick={(event) => event.stopPropagation()}><Info size={22} /><strong>播放失败</strong><span>{mediaError}</span><div><button onClick={() => openPanel("sources")}><Layers3 size={16} />换源</button><button onClick={onClose}>关闭播放器</button></div></div>}

        <div className="player-top-controls" onClick={(event) => event.stopPropagation()}>
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
            <button onClick={(event) => { event.stopPropagation(); togglePlay(); }} aria-label={playing ? "暂停" : "播放"}>{playing ? <Pause size={20} fill="currentColor" /> : <Play size={20} fill="currentColor" />}</button>
            <button disabled={!hasPrevious} onClick={(event) => { event.stopPropagation(); void onResolve(session.episodeIndex - 1, 0); }} aria-label="上一集"><SkipBack size={19} /></button>
            <button disabled={!hasNext} onClick={(event) => { event.stopPropagation(); void onResolve(session.episodeIndex + 1, 0); }} aria-label="下一集"><SkipForward size={19} /></button>
            
            <div className="volume-control-group" onClick={(event) => event.stopPropagation()}>
              <button onClick={(event) => { event.stopPropagation(); const video = videoRef.current; if (!video) return; video.muted = !video.muted; setMuted(video.muted); showOsd(video.muted ? "已静音" : `音量 ${Math.round(video.volume * 100)}%`); }} aria-label={muted ? "取消静音" : "静音"}>
                {muted || volumeVal === 0 ? <VolumeX size={19} /> : <Volume2 size={19} />}
              </button>
              <div className="volume-slider-popover">
                <span>{muted ? "0%" : `${Math.round(volumeVal * 100)}%`}</span>
                <div className="volume-slider-track">
                  <input
                    type="range"
                    min="0"
                    max="1"
                    step="0.01"
                    value={muted ? 0 : volumeVal}
                    onChange={(event) => {
                      event.stopPropagation();
                      const video = videoRef.current;
                      const val = Number(event.target.value);
                      setVolumeVal(val);
                      if (video) {
                        video.volume = val;
                        video.muted = val === 0;
                        setMuted(video.muted);
                      }
                    }}
                  />
                </div>
              </div>
            </div>

            <span className="player-route-label">{source?.sourceTitle || session.media.sourceTitle}</span>

            <div className="control-bar-right">
              <button className={`btn-pill ${panel === "episodes" ? "active" : ""}`} onClick={(event) => { event.stopPropagation(); openPanel("episodes"); }}>选集</button>
              <button className={`btn-pill ${panel === "sources" ? "active" : ""}`} onClick={(event) => { event.stopPropagation(); openPanel("sources"); }}>换源<b>{episode?.sources.length || 0}</b></button>
              <button className={`btn-pill ${panel === "speed" ? "active" : ""}`} onClick={(event) => { event.stopPropagation(); openPanel("speed"); }}>{rate}x</button>
              {allQualities.length > 0 || session.qualityOptions?.length ? (
                <button className={`btn-pill ${panel === "quality" ? "active" : ""}`} onClick={(event) => { event.stopPropagation(); openPanel("quality"); }}>{selectedQualityLabel}</button>
              ) : null}
              <button className={`btn-pill ${danmakuEnabled ? "active" : ""}`} onClick={(event) => { event.stopPropagation(); setDanmakuEnabled((v) => !v); }}>弹幕</button>
              <button className={`btn-icon ${favorite ? "active favorite" : ""}`} onClick={(event) => { event.stopPropagation(); onFavorite(); }} title="收藏">
                {favorite ? <Heart size={18} fill="currentColor" /> : <Bookmark size={18} />}
              </button>
              <button className="btn-icon" onClick={(event) => { event.stopPropagation(); void share(); }} title="分享"><Share2 size={18} /></button>
              <button onClick={(event) => { event.stopPropagation(); void togglePictureInPicture(); }} disabled={!pipSupported} aria-label="画中画"><PictureInPicture2 size={19} /></button>
              <button onClick={(event) => { event.stopPropagation(); void toggleFullscreen(); }} aria-label={fullscreen ? "退出全屏" : "全屏"}>{fullscreen ? <Minimize size={19} /> : <Maximize size={19} />}</button>
            </div>
          </div>
        </div>

        {panel && <div className="player-panel" onClick={(event) => event.stopPropagation()}>
          <div className="player-panel-head">
            <strong>{panel === "episodes" ? "选集" : panel === "sources" ? "播放来源" : panel === "speed" ? "播放倍速" : panel === "quality" ? "清晰度选择" : panel === "danmaku" ? "弹幕设置" : "更多功能"}</strong>
            <button onClick={() => setPanel(null)} aria-label="关闭面板"><X size={18} /></button>
          </div>

          {panel === "episodes" && <div className="player-episode-grid">{session.episodes.map((item, index) => <button key={`${item.id || "ep"}-${index}`} className={index === session.episodeIndex ? "active" : ""} onClick={() => void onResolve(index, 0)}><span>{item.name}</span><small>{item.sources.length} 个来源</small>{index === session.episodeIndex && <Check size={14} />}</button>)}</div>}

          {panel === "sources" && <div className="player-source-list">{episode?.sources.map((item, index) => <button key={`${item.sourceKey}-${item.route}-${index}`} className={index === session.sourceIndex ? "active" : ""} onClick={() => void onResolve(session.episodeIndex, index)}><Layers3 size={17} /><span><strong>{item.sourceTitle}</strong><small>{item.route}</small></span>{index === session.sourceIndex ? <Check size={17} /> : <ChevronRight size={17} />}</button>)}</div>}

          {panel === "speed" && <div className="speed-grid">{speedOptions.map((value) => <button key={value} className={rate === value ? "active" : ""} onClick={() => changeRate(value)}>{value}x</button>)}</div>}

          {panel === "quality" && <div className="quality-grid">
            <button className={!selectedQuality ? "active" : ""} onClick={() => {
              resumeAfterSourceChange.current = videoRef.current?.currentTime || 0;
              setSelectedQuality("");
              if (hlsRef.current) hlsRef.current.currentLevel = -1;
              setActiveUrl(session.url);
              setPanel(null);
              showOsd("已切回 自动 清晰度");
            }}>自动</button>
            {allQualities.map((q) => <button key={q.id} className={selectedQuality === q.id ? "active" : ""} onClick={() => {
              const position = videoRef.current?.currentTime || 0;
              resumeAfterSourceChange.current = position;
              setSelectedQuality(q.id);
              if (q.levelIndex !== undefined && hlsRef.current) {
                hlsRef.current.currentLevel = q.levelIndex;
              } else if (q.url) {
                setActiveUrl(q.url);
              }
              setPanel(null);
              showOsd(`已切换至 ${q.name}`);
            }}>{q.name}</button>)}
          </div>}

          {panel === "danmaku" && <div className="danmaku-settings">
            <label><span>显示弹幕<small>当前没有接入授权弹幕通道</small></span><input type="checkbox" checked={danmakuEnabled} onChange={(event) => setDanmakuEnabled(event.target.checked)} /></label>
            <label><span>透明度</span><input type="range" min="10" max="100" value={danmakuOpacity} onChange={(event) => setDanmakuOpacity(Number(event.target.value))} /><b>{danmakuOpacity}%</b></label>
            <label><span>字号</span><input type="range" min="10" max="100" value={danmakuSize} onChange={(event) => setDanmakuSize(Number(event.target.value))} /><b>{danmakuSize}%</b></label>
          </div>}

          {panel === "more" && <div className="player-more-grid">
            <button onClick={() => { const video = videoRef.current; if (video) seek(Math.max(0, video.currentTime - 15)); showOsd("快退 15 秒"); }}><RotateCcw size={19} />后退 15 秒</button>
            <button onClick={() => { const video = videoRef.current; if (video) seek(Math.min(video.duration, video.currentTime + 15)); showOsd("快进 15 秒"); }}><ChevronRight size={19} />前进 15 秒</button>
            <button onClick={() => openPanel("danmaku")}><Sliders size={19} />弹幕设置</button>
            <button onClick={() => void toggleFullscreen()}><Expand size={19} />横屏/全屏</button>
          </div>}
        </div>}
      </div>

      <p className="player-storage-note">媒体由来源方直连，聚映不保存视频文件。离线缓存仅在来源明确允许时开放。</p>
    </section>
  </div>;
}
