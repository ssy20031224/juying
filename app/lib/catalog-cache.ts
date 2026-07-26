import { sourceAdapters } from "./adapters";
import { SOURCES, type Source } from "./sources";
import type { SourceItem } from "./adapters/types";
import { mapWithConcurrency } from "./fanout";
import { mergeSearchItems, type SourcedItem } from "./catalog";

type CatalogEntry = {
  items: SourcedItem[];
  updatedAt: number;
  building: boolean;
};

const catalog = new Map<string, CatalogEntry>();
const REFRESH_INTERVAL = 10 * 60 * 1000; // 10 minutes
const BUILD_TIMEOUT = 30 * 1000; // 30 seconds max for building

function catalogKey(sourceKey: string): string {
  return `catalog:${sourceKey}`;
}

async function fetchSourceCatalog(source: Source): Promise<SourcedItem[]> {
  const adapter = sourceAdapters[source.adapter ?? source.key];
  if (!adapter) return [];

  const allItems: SourcedItem[] = [];
  const seen = new Set<string>();

  // Fetch 8 pages (160 items) per source to cover older years
  for (let page = 1; page <= 8; page++) {
    try {
      const items = await adapter.search("", page, AbortSignal.timeout(5000));
      if (!items.length) break;
      for (const item of items) {
        if (!seen.has(item.id)) {
          seen.add(item.id);
          allItems.push({ ...item, sourceKey: source.key, sourceTitle: source.title } as SourcedItem);
        }
      }
      if (items.length < 12) break; // source exhausted
    } catch {
      break;
    }
  }
  return allItems;
}

export async function warmCatalog(): Promise<void> {
  const enabledSources = SOURCES.filter((s) => s.enabled);
  const outcomes = await mapWithConcurrency(enabledSources, 6, async (source) => {
    try {
      const items = await fetchSourceCatalog(source);
      catalog.set(catalogKey(source.key), {
        items,
        updatedAt: Date.now(),
        building: false,
      });
      return items.length;
    } catch {
      return 0;
    }
  });
  const total = outcomes.reduce((sum, r) => sum + (r.value || 0), 0);
  console.log(`[catalog] warm complete: ${total} items from ${enabledSources.length} sources`);
}

export function getCatalog(filterKey?: string): SourcedItem[] {
  const enabledSources = SOURCES.filter((s) => s.enabled);

  // Check if any source cache is stale
  const now = Date.now();
  let anyStale = false;
  for (const source of enabledSources) {
    const entry = catalog.get(catalogKey(source.key));
    if (!entry || now - entry.updatedAt > REFRESH_INTERVAL) {
      anyStale = true;
      break;
    }
  }

  // Trigger background refresh if stale
  if (anyStale) {
    const building = [...catalog.values()].some((e) => e.building);
    if (!building) {
      warmCatalog().catch(() => {});
    }
  }

  // Merge all source catalogs
  const allItems: SourcedItem[] = [];
  for (const source of enabledSources) {
    const entry = catalog.get(catalogKey(source.key));
    if (entry) allItems.push(...entry.items);
  }

  if (filterKey) {
    return mergeSearchItems(allItems) as SourcedItem[];
  }
  return allItems;
}

export function getMergedCatalog(): SourcedItem[] {
  const all = getCatalog("");
  return mergeSearchItems(all) as SourcedItem[];
}

// Initialize on module load
warmCatalog().catch(() => {});
