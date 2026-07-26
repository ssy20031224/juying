import { NextResponse } from "next/server";
import fs from "node:fs";
import path from "node:path";
import { mapWithConcurrency } from "../../lib/fanout";
import { sourceAdapters } from "../../lib/adapters";
import { SOURCES, type Source } from "../../lib/sources";
import { cachedConditional, cached } from "../../lib/cache";
import { mergeSearchItems, type SourcedItem } from "../../lib/catalog";
import { getMergedCatalog, getCatalog } from "../../lib/catalog-cache";

let _logReady = false;
let _logFile = "";

function logSearch(line: string) {
  const ts = new Date().toISOString();
  const entry = `${ts} ${line}`;
  if (!_logReady) {
    try {
      const dir = path.join(process.cwd(), "logs");
      if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
      _logFile = path.join(dir, "search-requests.log");
      _logReady = true;
    } catch { /* no fs in dev worker */ }
  }
  if (_logReady) {
    try {
      fs.appendFileSync(_logFile, entry + "\n", "utf8");
    } catch { /* ignore */ }
  }
  console.log(line);
}

type CatalogItem = SourcedItem & {
  score?: string;
  sourceCount?: number;
};

type CatalogSourceStatus = {
  key: string;
  title: string;
  enabled: boolean;
  count: number;
  error?: string;
  status?: string;
};

function normalizeQuery(raw: string): string {
  return raw.trim().toLowerCase();
}

async function searchSource(source: Source, q: string, page: number): Promise<SourceItem[]> {
  const adapter = sourceAdapters[source.adapter ?? source.key];
  if (!adapter) return [];
  const cacheKey = `search:${source.key}:${q}:${page}`;
  // Keyword searches: cache all results (even empty) for 1 min to avoid
  // repeated timeouts during fuzzy fallback across sources.
  const isKeyword = q.length > 0;
  const ttlMs = isKeyword ? 60 * 1000 : 5 * 60 * 1000;
  const timeoutMs = isKeyword ? 3000 : 5000;
  try {
    return await cachedConditional(
      cacheKey, ttlMs,
      async () => {
        const results = await adapter.search(q, page, AbortSignal.timeout(timeoutMs));
        if (!q && results.length < 10 && typeof adapter.home === "function") {
          try {
            const homeSections = await adapter.home(AbortSignal.timeout(4000));
            const homeItems = homeSections.flatMap((s) => s.items);
            const existingIds = new Set(results.map((r) => r.id));
            for (const item of homeItems) {
              if (results.length >= 20) break;
              if (!existingIds.has(item.id)) { existingIds.add(item.id); results.push(item); }
            }
          } catch { /* ignore */ }
        }
        return results;
      },
      isKeyword ? () => true : (val) => Array.isArray(val) && val.length >= 10,
    );
  } catch { return []; }
}

async function searchSourceMultiPage(source: Source, q: string, startPage: number, needCount: number): Promise<SourceItem[]> {
  const allItems: SourceItem[] = [];
  const seen = new Set<string>();
  let page = startPage;
  const started = Date.now();
  while (allItems.length < needCount && page <= startPage + 25 && (Date.now() - started) < 15000) {
    try {
      const pageItems = await searchSource(source, q, page);
      if (!pageItems.length) break;
      for (const item of pageItems) {
        if (!seen.has(item.id)) { seen.add(item.id); allItems.push(item); }
      }
      page++;
    } catch { break; }
  }
  return allItems;
}

async function filteredSearch(
  q: string, page: number, yearFilter: string, kindFilter: string, genreFilter: string,
  sort: string, sourceFilter: string,
) {
  const activeSources = SOURCES.filter((source) => {
    if (!source.enabled) return false;
    if (sourceFilter && sourceFilter !== "全部") return source.title === sourceFilter || source.key === sourceFilter;
    return true;
  });

  const filterCacheKey = `filtered-search:year=${yearFilter}:kind=${kindFilter}:genre=${genreFilter}:sort=${sort}:source=${sourceFilter}`;
  const cacheEntry = await cached<{ items: CatalogItem[]; statuses: CatalogSourceStatus[] }>(
    filterCacheKey, 5 * 60 * 1000, async () => {
      const outcomes = await mapWithConcurrency(activeSources, 9, async (source) => {
        const defaultStatus: CatalogSourceStatus = {
          key: source.key, title: source.title, enabled: source.enabled, count: 0,
          status: source.enabled ? "ok" : "disabled",
        };
        if (!source.enabled) return { items: [] as CatalogItem[], sourceStatus: defaultStatus };
        try {
          const rawItems = await searchSourceMultiPage(source, q, 1, 500);
          const items: CatalogItem[] = rawItems.map((item) => ({
            ...item, score: item.score || "8.5", sourceKey: source.key, sourceTitle: source.title, sourceCount: 1,
          }));
          return { items, sourceStatus: { ...defaultStatus, count: items.length, status: items.length ? "ok" : "empty" } };
        } catch (error) {
          const message = error instanceof Error ? error.message : "search failed";
          return { items: [] as CatalogItem[], sourceStatus: { ...defaultStatus, status: "error", error: message } };
        }
      });
      const allItems = outcomes.flatMap((r) => r.value?.items || []);
      const statuses = outcomes.map((r, i) => r.value?.sourceStatus || {
        key: activeSources[i].key, title: activeSources[i].title, enabled: false, count: 0, status: "error", error: r.error || "unknown",
      }) as CatalogSourceStatus[];
      return { items: mergeSearchItems(allItems) as CatalogItem[], statuses };
    });

  let mergedItems = cacheEntry.items;
  const sourcesStatus = cacheEntry.statuses;

  if (kindFilter && kindFilter !== "全部") {
    mergedItems = mergedItems.filter((item) => (item.kind || "").includes(kindFilter));
  }
  if (yearFilter && yearFilter !== "全部") {
    mergedItems = mergedItems.filter((item) => (item.year || "").includes(yearFilter));
  }
  if (genreFilter && genreFilter !== "全部") {
    mergedItems = mergedItems.filter((item) => {
      const tags = item.tags || [];
      const text = `${item.kind || ""} ${tags.join(" ")}`;
      return text.includes(genreFilter);
    });
  }

  const primaryKey = (item: CatalogItem) => sort === "score" || sort === "hot" ? (item.sourceCount || 1) : parseInt(item.year || "", 10) || 0;
  const secondaryKey = (item: CatalogItem) => sort === "score" || sort === "hot" ? parseInt(item.year || "", 10) || 0 : (item.sourceCount || 1);
  mergedItems.sort((a, b) => {
    const pd = primaryKey(b) - primaryKey(a);
    if (pd !== 0) return pd;
    const sd = secondaryKey(b) - secondaryKey(a);
    if (sd !== 0) return sd;
    return (a.title || "").localeCompare(b.title || "", "zh-CN") || (a.id || "").localeCompare(b.id || "");
  });

  const pageSize = 20;
  const total = mergedItems.length;
  const pagedItems = mergedItems.slice((page - 1) * pageSize, page * pageSize);

  return NextResponse.json(
    { items: pagedItems, sources: sourcesStatus, demo: mergedItems.length === 0, total, page, pageSize },
    { headers: { "Cache-Control": "public, s-maxage=300, stale-while-revalidate=3600" } },
  );
}

// Progressive prefix fallback for Chinese fuzzy search:
// "葬送的芙利连" → "葬送的芙利" → "葬送"
// Capped at 2 retries + 3s timeout each to stay under 10s total.
async function fuzzySearchSource(source: Source, q: string, page: number): Promise<SourceItem[]> {
  const started = Date.now();
  const MAX_TOTAL_MS = 8000;
  const PER_CALL_MS = 3000;

  const results = await searchSource(source, q, page);
  if (results.length >= 5 || q.length <= 2) return results;

  const seen = new Set(results.map((r) => r.id));
  // At most 2 prefix retries, each with 3s timeout
  for (let attempt = 0; attempt < 2; attempt++) {
    if (Date.now() - started > MAX_TOTAL_MS) break;
    const len = q.length - 1 - attempt;
    if (len < 2) break;
    const prefix = q.slice(0, len);
    try {
      const more = await searchSource(source, prefix, 1);
      for (const item of more) {
        if (!seen.has(item.id)) { seen.add(item.id); results.push(item); }
      }
      if (results.length >= 10) break;
      // If prefix also returned 0, don't bother with shorter ones
      if (!more.length) break;
    } catch { break; }
  }
  return results;
}

export async function GET(request: Request) {
  const url = new URL(request.url);
  const q = normalizeQuery(url.searchParams.get("q") || url.searchParams.get("keyword") || "");
  const sourceFilter = url.searchParams.get("source") || "";
  const sort = url.searchParams.get("sort") || "update";
  const yearFilter = url.searchParams.get("year") || "";
  const kindFilter = url.searchParams.get("kind") || "";
  const genreFilter = url.searchParams.get("genre") || "";
  const page = Math.max(1, parseInt(url.searchParams.get("page") || "1", 10));

  logSearch(`Search request: q="${q}", source="${sourceFilter}", year="${yearFilter}", sort="${sort}", page=${page}`);

  // Fast path: browsing without keyword → use pre-built catalog cache or source-native filtering
  if (!q && !sourceFilter) {
    const hasFilter = (kindFilter && kindFilter !== "全部") || (genreFilter && genreFilter !== "全部") || (yearFilter && yearFilter !== "全部");

    // Filters active: call source-native searchFiltered for accurate server-side filtering
    if (hasFilter) {
      // Sources that support server-side genre filtering — when genre is
      // active, only query these (others would return unfiltered noise).
      const GENRE_SOURCES = new Set(["xifanacg", "gugu", "lanerc", "shuangxing"]);
      const genreActive = genreFilter && genreFilter !== "全部";
      const activeSources = SOURCES.filter((s) => {
        if (!s.enabled) return false;
        if (genreActive) return GENRE_SOURCES.has(s.adapter ?? s.key);
        return true;
      });

      // Cache filtered results for 3 minutes to speed repeat queries
      type FilteredEntry = { items: CatalogItem[]; statuses: CatalogSourceStatus[] };
      const cacheKey = `filtered:kind=${kindFilter}:genre=${genreFilter}:year=${yearFilter}:sort=${sort}`;
      const cacheEntry = await cached<FilteredEntry>(cacheKey, 3 * 60 * 1000, async () => {
        const outcomes = await mapWithConcurrency(activeSources, activeSources.length, async (source) => {
          const defaultStatus: CatalogSourceStatus = {
            key: source.key, title: source.title, enabled: source.enabled, count: 0,
            status: source.enabled ? "ok" : "disabled",
          };
          if (!source.enabled) return { items: [] as CatalogItem[], sourceStatus: defaultStatus };
          const adapter = sourceAdapters[source.adapter ?? source.key];
          if (!adapter) return { items: [] as CatalogItem[], sourceStatus: { ...defaultStatus, status: "error", error: "no adapter" } };

          try {
            let items: SourceItem[] = [];
            if (typeof adapter.searchFiltered === "function") {
              const effectiveKind = kindFilter !== "全部" ? kindFilter : "";
              const effectiveGenre = genreFilter !== "全部" ? genreFilter : "";
              const effectiveYear = yearFilter !== "全部" ? yearFilter : "";
              // Fetch up to 2 pages per source (40-60 items) — enough for 2-3 UI pages
              const allPaged: SourceItem[] = [];
              const seen = new Set<string>();
              for (let p = 1; p <= 2 && allPaged.length < 100; p++) {
                try {
                  const pageItems = await adapter.searchFiltered(effectiveKind, {
                    genre: effectiveGenre, class: effectiveGenre,
                    year: effectiveYear,
                    sort, by: sort, extend_sort: sort,
                  }, p, AbortSignal.timeout(4000));
                  if (!pageItems.length) break;
                  for (const it of pageItems) {
                    if (!seen.has(it.id)) { seen.add(it.id); allPaged.push(it); }
                  }
                } catch { break; }
              }
              items = allPaged;
            }

            // Fallback: use catalog + client-side filter for sources without searchFiltered
            if (!items.length) {
              const catalogItems = getCatalog(source.key);
              items = catalogItems.filter((it) => {
                if (kindFilter && kindFilter !== "全部" && !(it.kind || "").includes(kindFilter)) return false;
                if (yearFilter && yearFilter !== "全部" && !(it.year || "").includes(yearFilter)) return false;
                if (genreFilter && genreFilter !== "全部") {
                  const tags = it.tags || [];
                  if (!(`${it.kind || ""} ${tags.join(" ")}`.includes(genreFilter))) return false;
                }
                return true;
              });
            }

            const catalogItems: CatalogItem[] = items.map((it) => ({
              ...it, score: it.score || "8.5", sourceKey: source.key, sourceTitle: source.title, sourceCount: 1,
            }));
            return { items: catalogItems, sourceStatus: { ...defaultStatus, count: items.length, status: items.length ? "ok" : "empty" } };
          } catch (error) {
            const message = error instanceof Error ? error.message : "search failed";
            return { items: [] as CatalogItem[], sourceStatus: { ...defaultStatus, status: "error", error: message } };
          }
        });

        const allItems = outcomes.flatMap((r) => r.value?.items || []);
        const statuses = outcomes.map((r, i) => r.value?.sourceStatus || {
          key: activeSources[i].key, title: activeSources[i].title, enabled: false, count: 0, status: "error", error: r.error || "unknown",
        }) as CatalogSourceStatus[];
        return { items: mergeSearchItems(allItems) as CatalogItem[], statuses };
      });

      let mergedItems = cacheEntry.items;
      const sourcesStatus = cacheEntry.statuses;

      // Post-merge client-side filters: kinds and year only.
      // Genre is handled server-side by genre-supporting sources (already
      // filtered at source selection above); applying it again client-side
      // would drop items whose genre tags are truncated by the JS script.
      if (kindFilter && kindFilter !== "全部") {
        mergedItems = mergedItems.filter((item) => (item.kind || "").includes(kindFilter));
      }
      if (yearFilter && yearFilter !== "全部") {
        mergedItems = mergedItems.filter((item) => (item.year || "").includes(yearFilter));
      }

      const primaryKey = (item: CatalogItem) => sort === "score" || sort === "hot" ? (item.sourceCount || 1) : parseInt(item.year || "", 10) || 0;
      const secondaryKey = (item: CatalogItem) => sort === "score" || sort === "hot" ? parseInt(item.year || "", 10) || 0 : (item.sourceCount || 1);
      mergedItems.sort((a, b) => {
        const pd = primaryKey(b) - primaryKey(a);
        if (pd !== 0) return pd;
        const sd = secondaryKey(b) - secondaryKey(a);
        if (sd !== 0) return sd;
        return (a.title || "").localeCompare(b.title || "", "zh-CN") || (a.id || "").localeCompare(b.id || "");
      });

      const pageSize = 20;
      const total = mergedItems.length;
      const pagedItems = mergedItems.slice((page - 1) * pageSize, page * pageSize);

      return NextResponse.json(
        { items: pagedItems as CatalogItem[], sources: sourcesStatus, demo: total === 0, total, page, pageSize },
        { headers: { "Cache-Control": "public, s-maxage=300, stale-while-revalidate=3600" } },
      );
    }

    // No filters: use pre-built catalog cache
    const mergedItems = getMergedCatalog() as CatalogItem[];
    const primaryKey = (item: CatalogItem) => sort === "score" || sort === "hot" ? (item.sourceCount || 1) : parseInt(item.year || "", 10) || 0;
    const secondaryKey = (item: CatalogItem) => sort === "score" || sort === "hot" ? parseInt(item.year || "", 10) || 0 : (item.sourceCount || 1);
    mergedItems.sort((a, b) => {
      const pd = primaryKey(b) - primaryKey(a);
      if (pd !== 0) return pd;
      const sd = secondaryKey(b) - secondaryKey(a);
      if (sd !== 0) return sd;
      return (a.title || "").localeCompare(b.title || "", "zh-CN") || (a.id || "").localeCompare(b.id || "");
    });

    const pageSize = 20;
    const total = mergedItems.length;
    const pagedItems = mergedItems.slice((page - 1) * pageSize, page * pageSize);

    return NextResponse.json(
      { items: pagedItems, sources: SOURCES.filter((s) => s.enabled).map((s) => ({ key: s.key, title: s.title, enabled: true, count: 0, status: "ok" }) as CatalogSourceStatus), demo: mergedItems.length === 0, total, page, pageSize },
      { headers: { "Cache-Control": "public, s-maxage=120, stale-while-revalidate=600" } },
    );
  }

  const activeSources = SOURCES.filter((source) => {
    if (!source.enabled) return false;
    if (sourceFilter && sourceFilter !== "全部") {
      return source.title === sourceFilter || source.key === sourceFilter;
    }
    return true;
  });

  // Keyword search: always query sources directly
  if (q) {
    const outcomes = await mapWithConcurrency(activeSources, 9, async (source) => {
      const defaultStatus: CatalogSourceStatus = {
        key: source.key, title: source.title, enabled: source.enabled, count: 0,
        status: source.enabled ? "ok" : "disabled",
      };
      if (!source.enabled) return { items: [] as CatalogItem[], sourceStatus: defaultStatus };
      try {
        const rawItems = await fuzzySearchSource(source, q, page);
        const items: CatalogItem[] = rawItems.map((item) => ({
          ...item, score: item.score || "8.5", sourceKey: source.key, sourceTitle: source.title, sourceCount: 1,
        }));
        return { items, sourceStatus: { ...defaultStatus, count: items.length, status: items.length ? "ok" : "empty" } };
      } catch (error) {
        const message = error instanceof Error ? error.message : "search failed";
        return { items: [] as CatalogItem[], sourceStatus: { ...defaultStatus, status: "error", error: message } };
      }
    });
    const allItems = outcomes.flatMap((r) => r.value?.items || []);
    const sourcesStatus = outcomes.map((r, i) => r.value?.sourceStatus || {
      key: activeSources[i].key, title: activeSources[i].title, enabled: false, count: 0, status: "error", error: r.error || "unknown",
    }) as CatalogSourceStatus[];
    let mergedItems = mergeSearchItems(allItems) as CatalogItem[];

    // Title relevance filter: for queries with 3+ Chinese chars, require
    // at least a 2-char substring match or 50%+ character overlap to filter
    // out noise from overly broad source search APIs and fuzzy fallback.
    if (q.length >= 3) {
      const qChars = [...q].filter((c) => /[\u4e00-\u9fff]/.test(c));
      const minOverlap = qChars.length >= 5 ? 3 : 2;
      mergedItems = mergedItems.filter((item) => {
        const t = item.title || "";
        // Substring match: any 2 consecutive chars from query appear in title
        for (let i = 0; i < q.length - 1; i++) {
          if (t.includes(q.slice(i, i + 2))) return true;
        }
        // Character overlap fallback
        const tChars = new Set([...t].filter((c) => /[\u4e00-\u9fff]/.test(c)));
        let overlap = 0;
        for (const c of qChars) { if (tChars.has(c)) overlap++; }
        return overlap >= minOverlap;
      });
    }

    // Final fallback: if still too few, fuzzy-match against catalog and merge
    if (mergedItems.length < 5 && q.length >= 2) {
      const catalog = getMergedCatalog() as CatalogItem[];
      const chars = new Set([...q].filter((c) => /[\u4e00-\u9fff\w]/.test(c)));
      if (chars.size >= 2) {
        const existingIds = new Set(mergedItems.map((i) => i.id));
        const fuzzy = catalog
          .filter((item) => {
            if (existingIds.has(item.id)) return false;
            const t = item.title || "";
            // Require 2-char substring from query in title (stricter than char overlap)
            for (let i = 0; i < q.length - 1; i++) {
              if (t.includes(q.slice(i, i + 2))) return true;
            }
            const titleChars = new Set([...item.title].filter((c) => /[\u4e00-\u9fff\w]/.test(c)));
            let overlap = 0;
            for (const c of chars) { if (titleChars.has(c)) overlap++; }
            return overlap >= Math.min(chars.size, 3);
          })
          .sort((a, b) => {
            const aOverlap = [...chars].filter((c) => a.title.includes(c)).length;
            const bOverlap = [...chars].filter((c) => b.title.includes(c)).length;
            return bOverlap - aOverlap;
          })
          .slice(0, 40);
        // Merge: source API results first, then catalog fuzzy matches
        mergedItems = [...mergedItems, ...fuzzy];
      }
    }
    const primaryKey = (item: CatalogItem) => sort === "hot" ? (item.sourceCount || 1) : parseInt(item.year || "", 10) || 0;
    const secondaryKey = (item: CatalogItem) => sort === "hot" ? parseInt(item.year || "", 10) || 0 : (item.sourceCount || 1);
    // Keyword search: score by title relevance to query
    const relevanceScore = (item: CatalogItem) => {
      if (!q) return 0;
      const t = item.title || "";
      // Exact match bonus
      if (t === q) return 1000;
      if (t.startsWith(q)) return 500;
      if (t.includes(q)) return 200;
      // 2-char substring match count
      let score = 0;
      for (let i = 0; i < q.length - 1; i++) {
        if (t.includes(q.slice(i, i + 2))) score += 10;
      }
      return score;
    };
    mergedItems.sort((a, b) => {
      const ra = relevanceScore(a), rb = relevanceScore(b);
      if (ra !== rb) return rb - ra;
      const pd = primaryKey(b) - primaryKey(a);
      if (pd !== 0) return pd;
      const sd = secondaryKey(b) - secondaryKey(a);
      if (sd !== 0) return sd;
      return (a.title || "").localeCompare(b.title || "", "zh-CN") || (a.id || "").localeCompare(b.id || "");
    });
    // After relevance filter + sort, recalculate source counts from filtered items
    const filteredSourceCounts = new Map<string, number>();
    for (const item of mergedItems) {
      const sk = item.sourceKey;
      filteredSourceCounts.set(sk, (filteredSourceCounts.get(sk) || 0) + 1);
    }
    const filteredStatuses = sourcesStatus.map((s) => ({
      ...s,
      count: filteredSourceCounts.get(s.key) || 0,
      status: (filteredSourceCounts.get(s.key) || 0) > 0 ? "ok" : "empty",
    }));

    return NextResponse.json(
      { items: mergedItems, sources: filteredStatuses, demo: mergedItems.length === 0 },
      { headers: { "Cache-Control": "no-cache, no-store, must-revalidate" } },
    );
  }

  // Browsing with source filter: delegate to filtered search
  if (sourceFilter && sourceFilter !== "全部") {
    return await filteredSearch(q, page, yearFilter, kindFilter, genreFilter, sort, sourceFilter);
  }

  // Should never reach here — catalog fast path handled at top
  const emptyResult: CatalogItem[] = [];
  return NextResponse.json(
    { items: emptyResult, sources: [], demo: true },
    { headers: { "Cache-Control": "no-cache, no-store, must-revalidate" } },
  );
}
