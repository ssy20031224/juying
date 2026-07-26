import type { SourceAdapter } from "./types";
import { nativeAdapters } from "./native";
import { remoteAdapters } from "./remote";
import { extraAdapters } from "./extra-sources";
import { createJsAdapter } from "./js-source";
import { SOURCES } from "../sources";

export const sourceAdapters: Record<string, SourceAdapter> = {
  ...nativeAdapters,
  ...remoteAdapters,
  ...extraAdapters,
};

for (const source of SOURCES) {
  if (source.runtime === "js-worker" || !source.adapter) {
    if (!sourceAdapters[source.key]) {
      try {
        sourceAdapters[source.key] = createJsAdapter(source.key, source.localFile);
      } catch {
        // Source file missing or cannot be loaded — skip
      }
    }
  } else {
    const adapterKey = source.adapter;
    if (!sourceAdapters[adapterKey]) {
      try {
        sourceAdapters[adapterKey] = createJsAdapter(source.key, source.localFile);
      } catch {
        // Source file missing or cannot be loaded — skip
      }
    }
  }
}
