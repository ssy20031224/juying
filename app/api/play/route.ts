import { NextResponse } from "next/server";
import { sourceAdapters } from "../../lib/adapters";
import { SOURCES } from "../../lib/sources";
import { cached } from "../../lib/cache";

const NON_BROWSER_UA = /^(okhttp|Dart\/\d|Lavf\/|libcurl|Wget|curl|python)/i;
const PROXY_FORCE_HOSTS = [
  "kqgfbs.com", "ppvod", "ppvod01", "whip.", "WJiZxLXA2",
  "douyinvod.com", "bytetos.com", "bytedance", "bytecdn", "douyincdn",
  "ibyteimg", "pstatp.com", "ixigua.com", "v6.douyinvod", "csp-sign", "bytetos"
];

function buildProxyUrl(rawUrl: string, referer?: string, headersObj?: Record<string, string> | string): string {
  if (!rawUrl) return rawUrl;

  let ref = referer || "";
  let cookie = "";
  let ua = "";

  if (typeof headersObj === "string") {
    try { headersObj = JSON.parse(headersObj); } catch { headersObj = undefined; }
  }
  if (headersObj && typeof headersObj === "object") {
    if (!ref && headersObj["Referer"]) ref = headersObj["Referer"];
    if (headersObj["Cookie"]) cookie = headersObj["Cookie"];
    if (headersObj["User-Agent"]) ua = headersObj["User-Agent"];
  }

  // Proxy when: HTTP (mixed content), cookie required,
  // Quark/Douyin CDN (need specific referer), adapter returned a custom
  // Referer the CDN expects, or CDN known to reject direct browser access.
  const isHttp = /^http:\/\//.test(rawUrl);
  const needsCookie = Boolean(cookie);
  const isQuark = rawUrl.includes("quark.cn") || rawUrl.includes("drive.quark");
  const isByteDouyin = rawUrl.includes("douyinvod.com") || rawUrl.includes("bytetos.com") || rawUrl.includes("bytedance") || rawUrl.includes("douyincdn");
  const isProblemCdn = PROXY_FORCE_HOSTS.some((h) => rawUrl.includes(h));
  const isIpBound = rawUrl.includes("whip=");
  const hasAdapterRef = Boolean(ref);

  if (!isHttp && !needsCookie && !isQuark && !isByteDouyin && (!isProblemCdn || isIpBound) && !hasAdapterRef) return rawUrl;

  if (isQuark && !ref) ref = "https://pan.quark.cn/";
  if (isByteDouyin && !ref) ref = "https://www.douyin.com/";

  const params = new URLSearchParams();
  params.set("url", rawUrl);
  if (ref) params.set("referer", ref);
  if (cookie) params.set("cookie", cookie);
  if (ua) params.set("ua", ua);

  return `/api/proxy/stream?${params.toString()}`;
}

export async function POST(request: Request) {
  const source = new URL(request.url).searchParams.get("source") || "";
  const sourceConfig = SOURCES.find((entry) => entry.key === source);
  const adapterKey = sourceConfig?.adapter || source;
  const adapter = sourceAdapters[adapterKey] || sourceAdapters[source];
  if (!adapter) return NextResponse.json({ error: "source adapter unavailable" }, { status: 400 });

  try {
    const input = (await request.json()) as Record<string, string>;
    const inputKey = Object.entries(input).sort(([a], [b]) => a.localeCompare(b)).map(([key, value]) => `${key}=${value}`).join("&");
    const cacheTtl = source === "AuvFun" ? 0 : 15 * 1000;
    const result = await cached(`play:${source}:${inputKey}`, cacheTtl, () => adapter.play(input, AbortSignal.timeout(18000)));

    const playUrl = buildProxyUrl(result.url, result.referer, result.headers);

    return NextResponse.json({
      url: playUrl,
      type: result.type,
      qualityOptions: (result.resolutions || []).map((opt) => ({
        name: opt.name,
        url: buildProxyUrl(opt.url, result.referer, result.headers),
        type: opt.type,
      })),
    });
  } catch (error) {
    return NextResponse.json({ error: error instanceof Error ? error.message : "source play failed" }, { status: 502 });
  }
}
