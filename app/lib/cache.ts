type CacheEntry<T> = { value: T; expiresAt: number };

const entries = new Map<string, CacheEntry<unknown>>();
const inFlight = new Map<string, Promise<unknown>>();

export async function cached<T>(key: string, ttlMs: number, factory: () => Promise<T>): Promise<T> {
  return cachedConditional(key, ttlMs, factory, () => true);
}

export async function cachedConditional<T>(
  key: string,
  ttlMs: number,
  factory: () => Promise<T>,
  shouldCache: (val: T) => boolean = () => true
): Promise<T> {
  const now = Date.now();
  const existing = entries.get(key) as CacheEntry<T> | undefined;
  if (existing && existing.expiresAt > now) {
    if (shouldCache(existing.value)) {
      return existing.value;
    }
    entries.delete(key);
  }
  if (existing) entries.delete(key);

  const running = inFlight.get(key) as Promise<T> | undefined;
  if (running) return running;

  const promise = factory().then((value) => {
    if (shouldCache(value)) {
      entries.set(key, { value, expiresAt: Date.now() + ttlMs });
    }
    return value;
  }).finally(() => inFlight.delete(key));
  inFlight.set(key, promise);
  return promise;
}

export function invalidateCache(key: string) {
  entries.delete(key);
}
