import { NextResponse } from "next/server";

function rewriteM3u8(content: string, targetUrl: URL, reqUrl: URL, referer: string, ua: string): string {
  const proxyBase = `${reqUrl.origin}/api/proxy/stream`;
  const extraParams = (referer ? `&referer=${encodeURIComponent(referer)}` : "") + (ua ? `&ua=${encodeURIComponent(ua)}` : "");
  const lines = content.split(/\r?\n/);
  const rewritten = lines.map((line) => {
    const trimmed = line.trim();
    if (!trimmed) return line;
    if (trimmed.startsWith("#")) {
      return trimmed.replace(/URI=["']([^"']+)["']/g, (_, uri) => {
        try {
          const absolute = new URL(uri, targetUrl.href).href;
          return `URI="${proxyBase}?url=${encodeURIComponent(absolute)}${extraParams}"`;
        } catch {
          return _;
        }
      });
    }
    try {
      const absolute = new URL(trimmed, targetUrl.href).href;
      return `${proxyBase}?url=${encodeURIComponent(absolute)}${extraParams}`;
    } catch {
      return line;
    }
  });
  return rewritten.join("\n");
}

export async function OPTIONS() {
  return new Response(null, {
    status: 204,
    headers: {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, HEAD, OPTIONS",
      "Access-Control-Allow-Headers": "*",
    },
  });
}

export async function GET(request: Request) {
  const reqUrl = new URL(request.url);
  const targetUrl = reqUrl.searchParams.get("url");
  if (!targetUrl) {
    return NextResponse.json({ error: "missing url" }, { status: 400 });
  }

  let source: URL;
  try {
    source = new URL(targetUrl);
  } catch {
    return NextResponse.json({ error: "invalid target url" }, { status: 400 });
  }

  if (!["http:", "https:"].includes(source.protocol)) {
    return NextResponse.json({ error: "disallowed protocol" }, { status: 400 });
  }

  const referer = reqUrl.searchParams.get("referer")
    || `${source.protocol}//${source.host}/`
    || (targetUrl.includes("quark.cn") ? "https://pan.quark.cn/" : "")
    || (targetUrl.includes("douyinvod") || targetUrl.includes("douyin") || targetUrl.includes("bytetos") || targetUrl.includes("bytedance") ? "https://www.douyin.com/" : "");
  const cookie = reqUrl.searchParams.get("cookie") || "";
  const ua = reqUrl.searchParams.get("ua") || request.headers.get("user-agent") || "";
  const origin = reqUrl.searchParams.get("origin") || "";

  // 转发用户浏览器的真实请求头，CDN 看到的是用户指纹而非服务器指纹
  const forwardHeaders: Record<string, string> = {};
  if (ua) forwardHeaders["User-Agent"] = ua;
  const userAccept = request.headers.get("accept");
  if (userAccept) forwardHeaders["Accept"] = userAccept;
  const userLang = request.headers.get("accept-language");
  if (userLang) forwardHeaders["Accept-Language"] = userLang;
  // 浏览器指纹头 — CDN 用这些判断是否为真实浏览器
  const platform = request.headers.get("sec-ch-ua-platform") || "\"Windows\"";
  const uaHints = request.headers.get("sec-ch-ua") || `"Chromium";v="130", "Google Chrome";v="130", "Not?A_Brand";v="99"`;
  forwardHeaders["sec-ch-ua"] = uaHints;
  forwardHeaders["sec-ch-ua-mobile"] = request.headers.get("sec-ch-ua-mobile") || "?0";
  forwardHeaders["sec-ch-ua-platform"] = platform;
  forwardHeaders["sec-fetch-dest"] = request.headers.get("sec-fetch-dest") || "video";
  forwardHeaders["sec-fetch-mode"] = request.headers.get("sec-fetch-mode") || "cors";
  forwardHeaders["sec-fetch-site"] = request.headers.get("sec-fetch-site") || "cross-site";
  forwardHeaders["Accept-Encoding"] = "identity"; // 避免 CDN 返回压缩流
  forwardHeaders["Upgrade-Insecure-Requests"] = "1";

  if (referer) forwardHeaders["Referer"] = referer;
  if (origin) forwardHeaders["Origin"] = origin;
  if (cookie) forwardHeaders["Cookie"] = cookie;
  const range = request.headers.get("range");
  if (range) forwardHeaders["Range"] = range;

  // Binary segments (.ts / .m4s / .key): CDNs often reject browser fingerprint
  // headers (sec-ch-ua etc.) on raw media chunks. Use clean UA-only headers.
  const pathname = source.pathname.toLowerCase();
  const isSegment = /\.(ts|mp4|m4s|aac|key|png|jpg|jpeg|webp)(?:$|\?)/i.test(pathname);
  if (isSegment) {
    delete forwardHeaders["sec-ch-ua"];
    delete forwardHeaders["sec-ch-ua-mobile"];
    delete forwardHeaders["sec-ch-ua-platform"];
    delete forwardHeaders["sec-fetch-dest"];
    delete forwardHeaders["sec-fetch-mode"];
    delete forwardHeaders["sec-fetch-site"];
    delete forwardHeaders["Origin"];
    delete forwardHeaders["origin"];
    delete forwardHeaders["Upgrade-Insecure-Requests"];
  }

  // 针对抖音/字节 CDN (douyinvod.com / bytetos.com / pstatp.com)，清理引发 referer-acl-deny 403 的本地 Origin
  if (targetUrl.includes("douyinvod") || targetUrl.includes("bytetos") || targetUrl.includes("bytedance") || targetUrl.includes("douyincdn") || targetUrl.includes("pstatp")) {
    delete forwardHeaders["Origin"];
    delete forwardHeaders["origin"];
    delete forwardHeaders["sec-fetch-mode"];
    delete forwardHeaders["sec-fetch-site"];
    delete forwardHeaders["sec-fetch-dest"];
    forwardHeaders["Referer"] = referer || "https://www.douyin.com/";
  }

  // 针对金牌源 (ppvod01.kqgfbs.com / TencentEdgeOne) 清理引发跨域 403 的敏感标头
  if (targetUrl.includes("kqgfbs.com") || targetUrl.includes("ppvod")) {
    delete forwardHeaders["Origin"];
    delete forwardHeaders["origin"];
    delete forwardHeaders["sec-fetch-mode"];
    delete forwardHeaders["sec-fetch-site"];
    delete forwardHeaders["sec-fetch-dest"];
    delete forwardHeaders["sec-ch-ua"];
    delete forwardHeaders["sec-ch-ua-mobile"];
    delete forwardHeaders["sec-ch-ua-platform"];
    forwardHeaders["User-Agent"] = "okhttp/3.15";
    forwardHeaders["Referer"] = `${source.protocol}//${source.host}/`;
  }

  // 通用加密 HLS CDN (wmvbo.com / playxf.top / pan.wo.cn 等):
  // 分片使用限时 token，浏览器指纹头会触发反爬 403。
  if (targetUrl.includes("wmvbo.com") || targetUrl.includes("playxf.top") || targetUrl.includes("pan.wo.cn")) {
    delete forwardHeaders["sec-ch-ua"];
    delete forwardHeaders["sec-ch-ua-mobile"];
    delete forwardHeaders["sec-ch-ua-platform"];
    delete forwardHeaders["sec-fetch-dest"];
    delete forwardHeaders["sec-fetch-mode"];
    delete forwardHeaders["sec-fetch-site"];
    delete forwardHeaders["Origin"];
    delete forwardHeaders["origin"];
    forwardHeaders["User-Agent"] = "okhttp/3.15";
  }

  try {
    let response: Response;
    try {
      response = await fetch(targetUrl, {
        method: "GET",
        headers: forwardHeaders,
        redirect: "follow",
      });
    } catch {
      // 网络错误（DNS/超时）重试一次
      response = await fetch(targetUrl, {
        method: "GET",
        headers: forwardHeaders,
        redirect: "follow",
      });
    }

    // 多阶梯 CDN 403 防盗链智能解封重试策略
    const isJinpaiStream = targetUrl.includes("kqgfbs.com") || targetUrl.includes("ppvod");
    const activeReferer = isJinpaiStream ? `${source.protocol}//${source.host}/` : referer;

    if (response.status === 403 || response.status === 400) {
      // 阶梯 1: 移除可能引发 CDN 拒答的敏感 Origin 与 sec-fetch-* 头，仅保留合法 Referer 与 UA
      const step1Headers: Record<string, string> = {
        "User-Agent": isJinpaiStream ? "okhttp/3.15" : (ua || "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36"),
        Accept: "*/*",
      };
      if (activeReferer) step1Headers["Referer"] = activeReferer;
      if (cookie) step1Headers["Cookie"] = cookie;
      if (range) step1Headers["Range"] = range;

      try {
        const retry1 = await fetch(targetUrl, { method: "GET", headers: step1Headers, redirect: "follow" });
        if (retry1.ok || retry1.status === 206) response = retry1;
      } catch { /* next step */ }
    }

    if (response.status === 403 || response.status === 400) {
      // 阶梯 2: 模拟移动应用 OkHttp 指纹重试
      const step2Headers: Record<string, string> = {
        "User-Agent": "okhttp/3.15",
        Accept: "*/*",
      };
      if (activeReferer) step2Headers["Referer"] = activeReferer;
      if (cookie) step2Headers["Cookie"] = cookie;
      if (range) step2Headers["Range"] = range;

      try {
        const retry2 = await fetch(targetUrl, { method: "GET", headers: step2Headers, redirect: "follow" });
        if (retry2.ok || retry2.status === 206) response = retry2;
      } catch { /* next step */ }
    }

    if (response.status === 403 || response.status === 400) {
      // 阶梯 3: 无 Referer、无 Origin、完全纯净 Chrome UA 重试
      const step3Headers: Record<string, string> = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        Accept: "*/*",
      };
      if (range) step3Headers["Range"] = range;

      try {
        const retry3 = await fetch(targetUrl, { method: "GET", headers: step3Headers, redirect: "follow" });
        if (retry3.ok || retry3.status === 206) response = retry3;
      } catch { /* final fallback */ }
    }

    if (!response.ok && response.status !== 206) {
      return new Response(`upstream returned ${response.status}`, {
        status: response.status,
        headers: {
          "Content-Type": "text/plain",
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Headers": "*",
        },
      });
    }

    const rawContentType = (response.headers.get("content-type") || "").toLowerCase();

    // 区分 HLS 索引文件与二进制媒体分片 (.ts, .mp4, .m4s, .key...)
    const isM3u8 = !isSegment && (pathname.endsWith(".m3u8") || rawContentType.includes("mpegurl") || rawContentType.includes("x-mpegurl"));

    if (isM3u8) {
      const text = await response.text();
      if (text.includes("#EXTM3U")) {
        const rewritten = rewriteM3u8(text, source, reqUrl, referer, ua);
        return new Response(rewritten, {
          status: 200,
          headers: {
            "Content-Type": "application/vnd.apple.mpegurl",
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Headers": "*",
            "Access-Control-Allow-Methods": "GET, HEAD, OPTIONS",
            "Cache-Control": "public, max-age=3600",
          },
        });
      }
    }

    const responseHeaders = new Headers();
    const passHeaders = ["content-length", "content-range", "accept-ranges", "cache-control"];
    passHeaders.forEach((key) => {
      const val = response.headers.get(key);
      if (val) responseHeaders.set(key, val);
    });

    if (pathname.endsWith(".ts")) {
      responseHeaders.set("Content-Type", "video/mp2t");
    } else if (pathname.endsWith(".mp4")) {
      responseHeaders.set("Content-Type", "video/mp4");
    } else if (pathname.endsWith(".m4s")) {
      responseHeaders.set("Content-Type", "video/iso.segment");
    } else {
      responseHeaders.set("Content-Type", rawContentType || "application/octet-stream");
    }

    responseHeaders.set("Access-Control-Allow-Origin", "*");
    responseHeaders.set("Access-Control-Allow-Headers", "*");
    responseHeaders.set("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");

    return new Response(response.body, {
      status: response.status,
      headers: responseHeaders,
    });
  } catch (error) {
    return NextResponse.json({ error: error instanceof Error ? error.message : "proxy failed" }, { status: 502 });
  }
}
