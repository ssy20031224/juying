import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

// 与 AppUpdateManager 中的国内分发地址保持一致；每次发布后自动指向最新版本
const UPDATE_MANIFEST_URL =
  "https://ssyjuying.oss-cn-shanghai.aliyuncs.com/api/android/update.json";

/**
 * 分享用的稳定下载地址：读取最新 update.json，把 APK 字节流式转发给浏览器，
 * 强制以 "juying-x.y.z.apk" 文件名下载，点击后直接弹出下载提示、下载完即可安装。
 * 阿里云 OSS 对象是 .bin 后缀（默认域名拒绝直接分发 .apk），这里统一转换为 .apk。
 */
export async function GET() {
  try {
    const manifestResponse = await fetch(UPDATE_MANIFEST_URL, { cache: "no-store" });
    if (!manifestResponse.ok) {
      return new Response("update manifest unavailable", { status: 502 });
    }
    const manifest = (await manifestResponse.json()) as {
      versionName?: unknown;
      apkUrls?: unknown;
    };
    const urls = Array.isArray(manifest.apkUrls)
      ? manifest.apkUrls.filter((value): value is string => typeof value === "string")
      : [];
    // 国内云分发优先（阿里云/腾讯云直链），GitHub 仅作最后回退
    const downloadUrl =
      urls.find(
        (url) =>
          url.startsWith("https://") && !/github\.com|githubusercontent\.com/i.test(url),
      ) ?? urls.find((url) => url.startsWith("https://"));
    if (!downloadUrl) {
      return new Response("no apk url available", { status: 404 });
    }

    const version = String(manifest.versionName ?? "latest").trim().replace(/^v/, "");
    const upstream = await fetch(downloadUrl, { cache: "no-store" });
    if (!upstream.ok) {
      return new Response("apk unavailable", { status: 502 });
    }
    const headers = new Headers();
    headers.set("Content-Type", "application/vnd.android.package-archive");
    headers.set("Content-Disposition", `attachment; filename="juying-${version}.apk"`);
    headers.set("Cache-Control", "no-store");
    headers.set("X-Content-Type-Options", "nosniff");
    return new Response(upstream.body, { status: 200, headers });
  } catch {
    return new Response("download unavailable", { status: 502 });
  }
}
