"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import type { CSSProperties } from "react";
import {
  Bookmark, Check, ChevronLeft, ChevronRight, Compass, Download, Film,
  Heart, History, Home as HomeIcon, Info, Layers3, Menu, Play, Search, Settings2,
  Share2, Sparkles, UserRound, X,
} from "lucide-react";

type Episode = { name: string; url: string; route: string; flag?: Record<string, string> };

type Result = {
  id: string;
  title: string;
  year: string;
  kind: string;
  score: string;
  cover?: string;
  sourceKey: string;
  sourceTitle: string;
  sourceCount?: number;
  description?: string;
  episodes?: Episode[];
};

type SearchResponse = {
  items: Result[];
  sources: { key: string; title: string; enabled: boolean; count: number; error?: string; status?: string }[];
  demo?: boolean;
};

type HomeSection = { title: string; key: string; sourceKey: string; sourceTitle: string; items: Result[] };
type View = "home" | "library" | "profile";
type Quality = { id: string; name: string; url?: string; width?: number; height?: number; bitrate?: number };
type PlayerState = { title: string; url: string; type?: string; cover?: string; route?: string; qualityOptions?: Quality[] };

const sourcePills = [
  { label: "全源", tone: "mint" },
  { label: "Lanerc", tone: "cyan" },
  { label: "AuvFun", tone: "violet" },
  { label: "次元城", tone: "amber" },
  { label: "更多 10 个", tone: "slate" },
];

const demoItems: Result[] = [
  { id: "demo-1", title: "雾山五行 · 番外篇", year: "2025", kind: "国漫 / 奇幻", score: "9.1", sourceKey: "lanerc", sourceTitle: "Lanerc", sourceCount: 4, description: "山海之间的少年，踏上一场关于火与记忆的旅程。", episodes: [{ name: "第 01 集", url: "", route: "Lanerc" }, { name: "第 02 集", url: "", route: "Lanerc" }] },
  { id: "demo-2", title: "银河边缘的邮差", year: "2024", kind: "科幻 / 冒险", score: "8.7", sourceKey: "AuvFun", sourceTitle: "AuvFun", sourceCount: 2, description: "一封迟到三十年的信，把邮差送向宇宙尽头。", episodes: [{ name: "第 01 集", url: "", route: "AuvFun" }] },
  { id: "demo-3", title: "夏日终曲", year: "2023", kind: "爱情 / 剧情", score: "8.4", sourceKey: "dmbus", sourceTitle: "次元城", sourceCount: 3, description: "在海风停下之前，他们决定把未说出口的话说完。", episodes: [{ name: "正片", url: "", route: "次元城" }] },
  { id: "demo-4", title: "星门观测站", year: "2025", kind: "科幻 / 悬疑", score: "8.9", sourceKey: "shuangxing", sourceTitle: "双星", sourceCount: 1, description: "观测站收到一组来自未来的坐标。", episodes: [{ name: "第 01 集", url: "", route: "双星" }] },
];

const colorFor = (key: string) => ({ lanerc: "#18b7d1", AuvFun: "#8c6ff5", dmbus: "#e9a23b", shuangxing: "#14a88a" }[key] || "#758196");
const storageKeys = { favorites: "juying:favorites", history: "juying:history" };

function Cover({ item, className = "", priority = false }: { item?: Partial<Result>; className?: string; priority?: boolean }) {
  return item?.cover ? <img className={`cover-image ${className}`} src={item.cover} alt={`${item.title || "影片"} 封面`} loading={priority ? "eager" : "lazy"} /> : <div className={`cover-fallback ${className}`}><span>{(item?.title || "聚").slice(0, 1)}</span></div>;
}

function SearchForm({ query, setQuery, loading, onSubmit }: { query: string; setQuery: (value: string) => void; loading: boolean; onSubmit: (event: FormEvent) => void }) {
  return <form className="searchbar" onSubmit={onSubmit} role="search">
    <Search size={18} strokeWidth={2.2} aria-hidden="true" />
    <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="今天想看些什么？" aria-label="搜索影片" />
    <kbd>⌘ K</kbd>
    <button type="submit" disabled={loading}>{loading ? "检索中" : "开始检索"}<ChevronRight size={17} /></button>
  </form>;
}

function MovieCard({ item, index, onOpen, favorite, onFavorite }: { item: Result; index: number; onOpen: (item: Result) => void; favorite: boolean; onFavorite: (item: Result) => void }) {
  return <article className="movie-card" tabIndex={0} onClick={() => onOpen(item)} onKeyDown={(event) => event.key === "Enter" && onOpen(item)}>
    <div className="poster-wrap"><Cover item={item} /><span className="poster-index">{String(index + 1).padStart(2, "0")}</span><span className="poster-tag">{(item.kind || "影视").split(/[\s/，,]/)[0]}</span><button className={`favorite-chip ${favorite ? "is-favorite" : ""}`} aria-label={favorite ? "取消收藏" : "收藏影片"} onClick={(event) => { event.stopPropagation(); onFavorite(item); }}>{favorite ? <Heart size={15} fill="currentColor" /> : <Bookmark size={15} />}</button></div>
    <div className="movie-copy"><div className="movie-meta"><span>{item.year || "—"}</span><span className="score">★ {item.score || "-"}</span></div><h3>{item.title}</h3><p>{item.kind || "影视"}</p><div className="movie-source"><i style={{ background: colorFor(item.sourceKey) }} />{item.sourceTitle}<span>{item.sourceCount || 1} 线路</span></div></div>
  </article>;
}

export default function Home() {
  const [view, setView] = useState<View>("home");
  const [query, setQuery] = useState("");
  const [activeQuery, setActiveQuery] = useState("");
  const [items, setItems] = useState<Result[]>(demoItems);
  const [sourceStats, setSourceStats] = useState<SearchResponse["sources"]>([]);
  const [homeSections, setHomeSections] = useState<HomeSection[]>([]);
  const [selected, setSelected] = useState<Result | null>(null);
  const [playing, setPlaying] = useState<PlayerState | null>(null);
  const [selectedQuality, setSelectedQuality] = useState<string>("");
  const [favorites, setFavorites] = useState<Result[]>([]);
  const [history, setHistory] = useState<Result[]>([]);
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState("正在连接已收录来源");

  useEffect(() => {
    if ("serviceWorker" in navigator) navigator.serviceWorker.register("/sw.js").catch(() => undefined);
    const timer = window.setTimeout(() => {
      try {
        setFavorites(JSON.parse(localStorage.getItem(storageKeys.favorites) || "[]") as Result[]);
        setHistory(JSON.parse(localStorage.getItem(storageKeys.history) || "[]") as Result[]);
      } catch { /* ignore malformed device-local state */ }
    }, 0);
    fetch("/api/home")
      .then((response) => response.json() as Promise<{ sections?: HomeSection[]; errors?: { sourceKey: string; error: string }[] }>)
      .then((payload) => {
        const sections = payload.sections || [];
        setHomeSections(sections);
        if (sections.length) {
          setItems(sections.flatMap((section) => section.items));
          setNotice(`已加载 ${sections.length} 个首页分区，封面和元数据来自来源方`);
        } else if (payload.errors?.length) setNotice("部分来源暂不可用，仍可浏览本地演示内容");
      })
      .catch(() => setNotice("实时来源连接较慢，当前显示演示内容"));
    return () => window.clearTimeout(timer);
  }, []);

  const featured = homeSections[0]?.items?.[0] || items[0] || demoItems[0];
  const hotItems = homeSections.find((section) => section.title.includes("热门"))?.items || items.slice(0, 6);
  const libraryItems = useMemo(() => Array.from(new Map(items.map((item) => [`${item.sourceKey}-${item.id}`, item])).values()), [items]);
  const filteredItems = useMemo(() => {
    if (!activeQuery) return libraryItems;
    const needle = activeQuery.toLowerCase();
    return libraryItems.filter((item) => `${item.title} ${item.kind} ${item.sourceTitle}`.toLowerCase().includes(needle));
  }, [activeQuery, libraryItems]);
  const favoriteIds = useMemo(() => new Set(favorites.map((item) => `${item.sourceKey}-${item.id}`)), [favorites]);

  function saveDeviceState(key: string, value: Result[]) {
    try { localStorage.setItem(key, JSON.stringify(value.slice(0, 40))); } catch { /* private mode */ }
  }

  function toggleFavorite(item: Result) {
    const id = `${item.sourceKey}-${item.id}`;
    const next = favoriteIds.has(id) ? favorites.filter((value) => `${value.sourceKey}-${value.id}` !== id) : [item, ...favorites];
    setFavorites(next); saveDeviceState(storageKeys.favorites, next);
  }

  function addHistory(item: Result) {
    const id = `${item.sourceKey}-${item.id}`;
    const next = [item, ...history.filter((value) => `${value.sourceKey}-${value.id}` !== id)];
    setHistory(next); saveDeviceState(storageKeys.history, next);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    const next = query.trim();
    setActiveQuery(next);
    setView("library");
    if (!next) return;
    setLoading(true); setNotice("正在并发检索已启用来源");
    try {
      const response = await fetch(`/api/search?q=${encodeURIComponent(next)}`);
      const payload = (await response.json()) as SearchResponse;
      setItems(payload.items.length ? payload.items : demoItems);
      setSourceStats(payload.sources || []);
      setNotice(payload.demo ? "当前为演示结果，来源暂未返回匹配内容" : `已完成 ${payload.sources.filter((source) => source.count > 0).length} 个来源的检索`);
    } catch {
      setNotice("检索服务暂时不可用，已保留最近内容");
    } finally { setLoading(false); }
  }

  async function openDetail(item: Result) {
    setSelected(item); addHistory(item);
    if (item.id.startsWith("demo-")) return;
    try {
      const response = await fetch(`/api/detail?source=${encodeURIComponent(item.sourceKey)}&id=${encodeURIComponent(item.id)}`);
      const detail = (await response.json()) as Result;
      setSelected({ ...item, ...detail });
    } catch { setNotice("详情暂时无法获取，请稍后重试"); }
  }

  async function resolvePlay(episode: Episode) {
    if (!selected) return;
    if (episode.url) { setPlaying({ title: `${selected.title} · ${episode.name}`, url: episode.url, cover: selected.cover, route: episode.route }); return; }
    if (!episode.flag) { setNotice("该演示条目没有真实播放地址"); return; }
    setNotice("正在向来源请求临时播放地址");
    try {
      const response = await fetch(`/api/play?source=${encodeURIComponent(selected.sourceKey)}`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(episode.flag) });
      const payload = (await response.json()) as { url?: string; type?: string; qualityOptions?: Quality[]; referer?: string; error?: string };
      if (!payload.url) { setNotice(payload.error || "来源没有返回可播放地址"); return; }
      setSelected(null); setSelectedQuality(""); setPlaying({ title: `${selected.title} · ${episode.name}`, url: payload.url, type: payload.type, cover: selected.cover, route: episode.route, qualityOptions: payload.qualityOptions });
    } catch { setNotice("播放地址解析失败，请更换线路重试"); }
  }

  function closeOverlays() { setSelected(null); setPlaying(null); }

  return <main className="app-shell">
    <header className="desktop-nav"><a className="brand" href="#top" aria-label="聚映首页"><span className="brand-mark">J</span><span>聚映<span className="brand-dot">·</span></span></a><nav><button className={view === "home" ? "active" : ""} onClick={() => setView("home")}>发现</button><button className={view === "library" ? "active" : ""} onClick={() => setView("library")}>片库</button><button onClick={() => setView("profile")}>我的</button></nav><a className="install-link" href="/manifest.webmanifest"><Download size={14} /> 安装到手机</a></header>

    <div className="mobile-top"><div className="user-greeting"><div className="avatar"><Sparkles size={15} /></div><div><span>晚上好，</span><strong>准备看点什么？</strong></div></div><button className="icon-button" aria-label="观看历史" onClick={() => setView("profile")}><History size={20} /></button></div>

    {view === "home" && <>
      <section className="mobile-search"><SearchForm query={query} setQuery={setQuery} loading={loading} onSubmit={submit} /><div className="mobile-tabs"><button className="active">精选</button><button onClick={() => { setView("library"); setActiveQuery("日漫"); }}>日漫</button><button onClick={() => { setView("library"); setActiveQuery("剧场版"); }}>剧场版</button></div></section>
      <section className="desktop-hero" id="top"><div className="eyebrow"><span className="live-dot" /> 多源检索 · 不存储影片</div><h1>把分散的片源，<em>聚</em>到一起。</h1><p>搜索一次，查看多个授权来源的结果，选择线路后由你的播放器直连来源。</p><SearchForm query={query} setQuery={setQuery} loading={loading} onSubmit={submit} /><div className="source-pills">{sourcePills.map((pill) => <span className={`source-pill ${pill.tone}`} key={pill.label}><i />{pill.label}</span>)}</div></section>
      <section className="mobile-feature"><div className="feature-image"><Cover item={featured} priority /><div className="feature-gradient" /><div className="feature-copy"><span>今日精选 · {featured.sourceTitle}</span><h1>{featured.title}</h1><p>{featured.kind || "精选内容"}</p></div><button className="feature-play" aria-label={`播放 ${featured.title}`} onClick={() => openDetail(featured)}><Play size={18} fill="currentColor" /></button><div className="feature-dots"><i className="active" /><i /><i /></div></div><div className="quick-actions"><button onClick={() => setView("library")}><Compass size={19} /><span>发现更多</span></button><button onClick={() => setView("profile")}><History size={19} /><span>观看记录</span></button><button onClick={() => setView("profile")}><Heart size={19} /><span>我的收藏</span></button></div></section>
      <HomeSections sections={homeSections} items={hotItems} onOpen={openDetail} favorites={favoriteIds} onFavorite={toggleFavorite} />
    </>}

    {view === "library" && <section className="library-view"><div className="library-head"><div><p className="eyebrow">{activeQuery ? "搜索结果" : "全部内容"}</p><h1>{activeQuery ? `关于“${activeQuery}”` : "片库"}</h1><span>{filteredItems.length} 部 · {notice}</span></div><SearchForm query={query} setQuery={setQuery} loading={loading} onSubmit={submit} /></div><div className="source-status">{sourceStats.length ? sourceStats.map((source) => <span key={source.key} style={{ "--source-color": colorFor(source.key) } as CSSProperties}><i />{source.title} {source.count ? `· ${source.count}` : "· 无结果"}</span>) : <><span><i />13 个来源已收录</span><span><i className="muted" />播放地址不经过本站存储</span></>}</div><div className="library-grid">{filteredItems.map((item, index) => <MovieCard key={`${item.sourceKey}-${item.id}`} item={item} index={index} onOpen={openDetail} favorite={favoriteIds.has(`${item.sourceKey}-${item.id}`)} onFavorite={toggleFavorite} />)}</div></section>}

    {view === "profile" && <ProfileView favorites={favorites} history={history} onOpen={openDetail} onFavorite={toggleFavorite} />}

    <nav className="mobile-bottom" aria-label="主导航"><button className={view === "home" ? "active" : ""} onClick={() => setView("home")}><HomeIcon size={21} /><span>首页</span></button><button className={view === "library" ? "active" : ""} onClick={() => setView("library")}><Film size={21} /><span>片库</span></button><button className={view === "profile" ? "active" : ""} onClick={() => setView("profile")}><UserRound size={21} /><span>我的</span></button></nav>

    <footer className="site-footer"><span>聚映 · 多源检索工具</span><span>只聚合元数据与临时播放入口，不保存影片文件</span></footer>

    {selected && <div className="overlay" onClick={closeOverlays}><section className="detail-sheet" role="dialog" aria-modal="true" aria-label="影片详情" onClick={(event) => event.stopPropagation()}><button className="sheet-close" onClick={() => setSelected(null)} aria-label="关闭"><X size={20} /></button><div className="detail-cover"><Cover item={selected} /><span>{selected.sourceTitle}</span></div><div className="detail-content"><p className="eyebrow">{selected.sourceTitle} · {selected.year || "最新"}</p><h2>{selected.title}</h2><p className="detail-description">{selected.description || "来源方暂未提供简介，选择剧集后即可请求播放地址。"}</p><div className="detail-actions"><button onClick={() => toggleFavorite(selected)} className={favoriteIds.has(`${selected.sourceKey}-${selected.id}`) ? "selected" : ""}>{favoriteIds.has(`${selected.sourceKey}-${selected.id}`) ? <Heart size={16} fill="currentColor" /> : <Bookmark size={16} />} {favoriteIds.has(`${selected.sourceKey}-${selected.id}`) ? "已收藏" : "收藏"}</button><button><Share2 size={16} /> 分享</button></div><div className="episode-block"><div className="episode-title"><span>选集</span><small>{selected.episodes?.length || 0} 集 · {selected.sourceCount || 1} 条线路</small></div>{selected.episodes?.length ? <div className="episode-grid">{selected.episodes.map((episode) => <button key={`${episode.route}-${episode.name}`} onClick={() => resolvePlay(episode)}><span>{episode.name}</span><small>{episode.route}</small><Play size={14} fill="currentColor" /></button>)}</div> : <div className="empty-state"><Info size={18} /> 暂无剧集信息</div>}</div></div></section></div>}

    {playing && <div className="overlay player-overlay" onClick={closeOverlays}><section className="player-sheet" role="dialog" aria-modal="true" aria-label="视频播放器" onClick={(event) => event.stopPropagation()}><div className="player-topbar"><button onClick={() => setPlaying(null)} aria-label="返回"><ChevronLeft size={23} /></button><div><strong>{playing.title}</strong><span>{playing.route || "来源直连"}</span></div><button aria-label="更多操作"><Menu size={21} /></button></div><video controls autoPlay playsInline poster={playing.cover} src={playing.url} onError={() => setNotice("播放器无法加载该地址，可尝试更换线路")} /><div className="player-toolbar"><span><Layers3 size={16} /> {playing.type?.toUpperCase() || "AUTO"}</span><span><Check size={16} /> 来源直连</span><button><Settings2 size={16} /> 播放设置</button></div>{playing.qualityOptions?.length ? <div className="quality-row"><span>清晰度</span>{playing.qualityOptions.map((quality) => <button key={quality.id} className={selectedQuality === quality.id ? "active" : ""} onClick={() => { setSelectedQuality(quality.id); if (quality.url) setPlaying((current) => current ? { ...current, url: quality.url || current.url } : current); }}>{quality.name}</button>)}</div> : null}<p className="player-note">播放地址来自来源方，本站不保存媒体内容。若来源要求额外请求头，将由对应播放器适配处理。</p></section></div>}
  </main>;
}

function HomeSections({ sections, items, onOpen, favorites, onFavorite }: { sections: HomeSection[]; items: Result[]; onOpen: (item: Result) => void; favorites: Set<string>; onFavorite: (item: Result) => void }) {
  const displaySections = sections.length ? sections : [{ title: "热门推荐", key: "demo", sourceKey: "lanerc", sourceTitle: "演示", items }];
  return <section className="home-content"><div className="section-heading"><div><p className="eyebrow">实时聚合</p><h2>好内容，正在发生</h2></div><button>查看全部 <ChevronRight size={16} /></button></div>{displaySections.map((section) => <div className="content-row" key={`${section.sourceKey}-${section.key}`}><div className="row-heading"><h3>{section.title}</h3><span>{section.sourceTitle}</span><button aria-label={`查看${section.title}`}><ChevronRight size={16} /></button></div><div className="horizontal-grid">{section.items.slice(0, 8).map((item, index) => <MovieCard key={`${item.sourceKey}-${item.id}-${index}`} item={item} index={index} onOpen={onOpen} favorite={favorites.has(`${item.sourceKey}-${item.id}`)} onFavorite={onFavorite} />)}</div></div>)}</section>;
}

function ProfileView({ favorites, history, onOpen, onFavorite }: { favorites: Result[]; history: Result[]; onOpen: (item: Result) => void; onFavorite: (item: Result) => void }) {
  return <section className="profile-view"><div className="profile-card"><div className="profile-avatar"><Sparkles size={23} /></div><div><p>聚映用户</p><h1>本地观影空间</h1><span>收藏和观看记录仅保存在当前设备</span></div><button aria-label="设置"><Settings2 size={19} /></button></div><div className="profile-banner"><div><Sparkles size={18} /><strong>保持轻盈，继续发现</strong><span>不下载、不转存，随时从来源直达</span></div><ChevronRight size={20} /></div><div className="profile-shortcuts"><button><Heart size={20} /><span>我的收藏</span><b>{favorites.length}</b></button><button><History size={20} /><span>历史记录</span><b>{history.length}</b></button><button><Download size={20} /><span>离线缓存</span><b>未启用</b></button></div><div className="profile-section"><div className="section-heading"><div><p className="eyebrow">最近观看</p><h2>继续追番</h2></div></div>{history.length ? <div className="compact-list">{history.slice(0, 5).map((item) => <button key={`${item.sourceKey}-${item.id}`} onClick={() => onOpen(item)}><Cover item={item} /><span><strong>{item.title}</strong><small>{item.sourceTitle} · {item.year || "最新"}</small></span><ChevronRight size={17} /></button>)}</div> : <div className="empty-state"><History size={18} /> 打开一部影片后，这里会记录最近观看</div>}</div><div className="profile-section"><div className="section-heading"><div><p className="eyebrow">我的收藏</p><h2>想看的内容</h2></div></div>{favorites.length ? <div className="compact-list">{favorites.slice(0, 5).map((item) => <div className="compact-row" key={`${item.sourceKey}-${item.id}`} role="button" tabIndex={0} onClick={() => onOpen(item)} onKeyDown={(event) => event.key === "Enter" && onOpen(item)}><Cover item={item} /><span><strong>{item.title}</strong><small>{item.sourceTitle} · {item.kind || "影视"}</small></span><button className="remove-favorite" onClick={(event) => { event.stopPropagation(); onFavorite(item); }} aria-label="取消收藏"><X size={15} /></button></div>)}</div> : <div className="empty-state"><Bookmark size={18} /> 在影片详情中点击收藏，建立自己的片单</div>}</div></section>;
}
