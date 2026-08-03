import { and, asc, eq } from "drizzle-orm";
import { NextResponse } from "next/server";
import { getDb } from "../../../db";
import { danmakus } from "../../../db/schema";
import { getCurrentUser } from "../../lib/auth";

export const dynamic = "force-dynamic";

const MAX_DANMAKU = 2000;
const MAX_TEXT_LEN = 100;
const COLOR_PATTERN = /^#[0-9A-Fa-f]{6,8}$/;

function sanitizeKey(raw: string, max: number): string {
  // \w with unicode flag keeps CJK episode names intact (仅过滤控制/分隔符)
  return String(raw || "").replace(/[^\w:@./-]/gu, "_").slice(0, max);
}

export async function GET(request: Request) {
  const params = new URL(request.url).searchParams;
  const media = sanitizeKey(params.get("media") || "", 140);
  const episode = sanitizeKey(params.get("episode") || "", 180);
  if (!media || !episode) return NextResponse.json({ error: "missing media or episode" }, { status: 400 });

  try {
    const db = await getDb();
    const rows = await db
      .select({
        id: danmakus.id,
        text: danmakus.text,
        color: danmakus.color,
        positionMs: danmakus.positionMs,
        ts: danmakus.createdAt,
      })
      .from(danmakus)
      .where(and(eq(danmakus.mediaKey, media), eq(danmakus.episodeKey, episode)))
      .orderBy(asc(danmakus.positionMs))
      .limit(MAX_DANMAKU);
    return NextResponse.json(
      {
        danmakus: rows.map((row) => ({
          id: row.id,
          text: row.text,
          color: row.color,
          positionMs: Number(row.positionMs || 0),
          ts: Number(row.ts) * 1000,
        })),
      },
      { headers: { "Cache-Control": "no-store" } },
    );
  } catch {
    return NextResponse.json({ error: "danmaku storage not configured" }, { status: 503 });
  }
}

export async function POST(request: Request) {
  const currentUser = await getCurrentUser(request);
  if (!currentUser) {
    return NextResponse.json({ error: "authentication required" }, { status: 401 });
  }
  let body: { media?: unknown; episode?: unknown; positionMs?: unknown; text?: unknown; color?: unknown };
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "invalid json" }, { status: 400 });
  }

  const media = sanitizeKey(String(body.media || ""), 140);
  const episode = sanitizeKey(String(body.episode || ""), 180);
  const text = String(body.text || "").trim().slice(0, MAX_TEXT_LEN);
  if (!media || !episode || !text) {
    return NextResponse.json({ error: "missing media or episode or text" }, { status: 400 });
  }
  const positionMs = Math.max(0, Math.min(Number(body.positionMs) || 0, 24 * 60 * 60 * 1000));
  const color = String(body.color || "").match(COLOR_PATTERN) ? String(body.color) : "#FFFFFFFF";

  try {
    const db = await getDb();
    await db.insert(danmakus).values({
      id: crypto.randomUUID(),
      userId: currentUser.id,
      mediaKey: media,
      episodeKey: episode,
      positionMs,
      text,
      color,
    });
    const rows = await db
      .select({
        id: danmakus.id,
        text: danmakus.text,
        color: danmakus.color,
        positionMs: danmakus.positionMs,
        ts: danmakus.createdAt,
      })
      .from(danmakus)
      .where(and(eq(danmakus.mediaKey, media), eq(danmakus.episodeKey, episode)))
      .orderBy(asc(danmakus.positionMs))
      .limit(MAX_DANMAKU);
    return NextResponse.json(
      {
        danmakus: rows.map((row) => ({
          id: row.id,
          text: row.text,
          color: row.color,
          positionMs: Number(row.positionMs || 0),
          ts: Number(row.ts) * 1000,
        })),
      },
      { headers: { "Cache-Control": "no-store" } },
    );
  } catch {
    return NextResponse.json({ error: "internal server error" }, { status: 500 });
  }
}
