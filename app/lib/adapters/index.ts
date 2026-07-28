import type { SourceAdapter } from "./types";
import { nativeAdapters } from "./native";
import { remoteAdapters } from "./remote";
import { CompleteScriptAdapter } from "./script-worker";

export const sourceAdapters: Record<string, SourceAdapter> = {
  ...nativeAdapters,
  ...remoteAdapters,
  guazi: new CompleteScriptAdapter("guazi", "guazi.js"),
  shuangxing: new CompleteScriptAdapter("shuangxing", "shuangxing.js"),
  akianime: new CompleteScriptAdapter("akianime", "akianime.js"),
  lmm85: new CompleteScriptAdapter("lmm85", "lmm85.js"),
  dmbus: new CompleteScriptAdapter("dmbus", "dmbus.js"),
};
