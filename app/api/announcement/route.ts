import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

export async function GET() {
  const announcement = {
    id: "announcement_v2",
    title: "手机环境异常及注册账号注意事项",
    summary: "手机环境异常及注册账号注意事项",
    content: "【重要通知】\n1. 本应用已升级全自动云端同步与防封安全防护。\n2. 如遇到播放卡顿或换源提示，建议在设置中开启“自动切换高速源”。\n3. 账号密码需设置8位以上并包含多种字符组合，切勿泄漏个人账号信息。",
    updatedAt: "2026-08-03 08:30",
    enabled: true,
  };

  return NextResponse.json({ announcement }, { headers: { "Cache-Control": "no-store" } });
}
