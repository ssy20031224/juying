import { NextResponse } from "next/server";

const PRIVATE_HOST = /^(localhost|0\.0\.0\.0|127\.|10\.|169\.254\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.|\[?::1\]?$)/i;

export async function GET(request: Request) {
  const raw = new URL(request.url).searchParams.get("url") || "";
  let source: URL;
  try {
    source = new URL(raw);
  } catch {
    return NextResponse.json({ error: "invalid cover url" }, { status: 400 });
  }
  if (!["http:", "https:"].includes(source.protocol) || PRIVATE_HOST.test(source.hostname)) {
    return NextResponse.json({ error: "cover host is not allowed" }, { status: 400 });
  }

  try {
    let referer = source.protocol && source.host ? `${source.protocol}//${source.host}/` : "";
    if (source.hostname.includes("doubanio.com") || source.hostname.includes("douban.com")) {
      referer = "https://movie.douban.com/";
    }
    const headers: Record<string, string> = {
      Accept: "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
      "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    };
    if (referer) headers["Referer"] = referer;

    let response = await fetch(source, { redirect: "follow", signal: AbortSignal.timeout(8000), headers });

    // doubanio 可能返回 418，去掉 Sec-Fetch 头重试
    if (response.status === 418 && referer) {
      const retryHeaders: Record<string, string> = {
        Accept: "image/*",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36",
        Referer: referer,
      };
      try {
        const retry = await fetch(source, { redirect: "follow", signal: AbortSignal.timeout(8000), headers: retryHeaders });
        if (retry.ok) response = retry;
      } catch { /* keep original */ }
    }

    const contentType = response.headers.get("content-type") || "";
    if (!response.ok || !response.body) {
      return NextResponse.json({ error: "cover unavailable" }, { status: 404 });
    }
    const finalContentType = contentType.toLowerCase().startsWith("image/") ? contentType : "image/jpeg";
    return new Response(response.body, {
      headers: {
        "Content-Type": finalContentType,
        "Cache-Control": "public, max-age=86400, stale-while-revalidate=604800",
        "X-Content-Type-Options": "nosniff",
      },
    });
  } catch {
    return NextResponse.json({ error: "cover fetch failed" }, { status: 404 });
  }
}
