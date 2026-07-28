type CacheEntry<T> = { value: T; expiresAt: number };

// Process-local cache: useful for a warm Node/Worker isolate and safe because it
// only contains metadata or short-lived source URLs, never video bytes.
const entries = new Map<string, CacheEntry<unknown>>();
const inFlight = new Map<string, Promise<unknown>>();

export async function cached<T>(key: string, ttlMs: number, factory: () => Promise<T>): Promise<T> {
  const now = Date.now();
  const existing = entries.get(key) as CacheEntry<T> | undefined;
  if (existing && existing.expiresAt > now) return existing.value;
  if (existing) entries.delete(key);

  const running = inFlight.get(key) as Promise<T> | undefined;
  if (running) return running;

  const promise = factory().then((value) => {
    entries.set(key, { value, expiresAt: Date.now() + ttlMs });
    return value;
  }).finally(() => inFlight.delete(key));
  inFlight.set(key, promise);
  return promise;
}

