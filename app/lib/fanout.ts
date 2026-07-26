export type FanoutResult<T> = {
  value?: T;
  error?: string;
  durationMs: number;
};

/** Run independent source calls with an explicit per-request concurrency cap. */
export async function mapWithConcurrency<T, R>(
  values: T[],
  limit: number,
  task: (value: T) => Promise<R>,
): Promise<FanoutResult<R>[]> {
  const results: FanoutResult<R>[] = new Array(values.length);
  let cursor = 0;
  const worker = async () => {
    while (true) {
      const index = cursor++;
      if (index >= values.length) return;
      const started = Date.now();
      try {
        results[index] = { value: await task(values[index]), durationMs: Date.now() - started };
      } catch (error) {
        results[index] = { error: error instanceof Error ? error.message : "source request failed", durationMs: Date.now() - started };
      }
    }
  };
  await Promise.all(Array.from({ length: Math.max(1, Math.min(limit, values.length || 1)) }, worker));
  return results;
}
