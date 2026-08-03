import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

export async function GET() {
  const announcement = {
    id: "announcement_v3",
    title: "新功能上线：多设备同步、弹幕回放与消息提醒",
    summary: "新功能上线：多设备同步、弹幕回放与消息提醒",
    content: "【聚映新功能】\n1. 登录账号后，追番收藏与观看记录支持多设备云端同步。\n2. 弹幕会按剧集与时间点保存，再次观看同一集时可回放历史弹幕。\n3. 收藏的连载番剧更新时，会在首页铃铛中提醒您追番；收到评论回复也会第一时间通知。\n4. 评论区支持楼中楼回复与点赞，可长按或点击管理自己的评论。\n5. 如遇播放卡顿，可切换视频源或降低清晰度；请妥善保管账号密码，勿泄露给他人。",
    updatedAt: "2026-08-03 12:00",
    enabled: true,
  };

  return NextResponse.json({ announcement }, { headers: { "Cache-Control": "no-store" } });
}
