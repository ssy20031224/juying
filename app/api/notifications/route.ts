import { and, desc, eq, gt } from "drizzle-orm";
import { NextResponse } from "next/server";
import { getDb } from "../../../db";
import { notifications } from "../../../db/schema";
import { getCurrentUser } from "../../lib/auth";

export const dynamic = "force-dynamic";

const MAX_NOTIFICATIONS = 100;
const MAX_SNAPSHOT = 12_000;
const DEDUPE_WINDOW_SECONDS = 30 * 24 * 60 * 60;

function text(value: unknown, max: number): string {
  return String(value ?? "").trim().slice(0, max);
}

function mediaKey(value: unknown): string {
  return text(value, 180).replace(/[^A-Za-z0-9:_@./-]/g, "_");
}

function serialize(row: typeof notifications.$inferSelect) {
  return {
    id: row.id,
    type: row.type,
    title: row.title,
    body: row.body,
    mediaKey: row.mediaKey,
    episodeName: row.episodeName,
    commentId: row.commentId,
    mediaSnapshot: row.mediaSnapshot,
    read: Boolean(row.read),
    ts: Number(row.createdAt) * 1000,
  };
}

export async function GET(request: Request) {
  const user = await getCurrentUser(request);
  if (!user) return NextResponse.json({ error: "authentication required" }, { status: 401 });

  try {
    const db = await getDb();
    const rows = await db
      .select()
      .from(notifications)
      .where(eq(notifications.userId, user.id))
      .orderBy(desc(notifications.read), desc(notifications.createdAt))
      .limit(MAX_NOTIFICATIONS);
    return NextResponse.json(
      { notifications: rows.map(serialize) },
      { headers: { "Cache-Control": "no-store" } },
    );
  } catch {
    return NextResponse.json({ notifications: [] }, { headers: { "Cache-Control": "no-store" } });
  }
}

export async function POST(request: Request) {
  const user = await getCurrentUser(request);
  if (!user) return NextResponse.json({ error: "authentication required" }, { status: 401 });

  let body: {
    type?: unknown;
    title?: unknown;
    body?: unknown;
    mediaKey?: unknown;
    episodeName?: unknown;
    mediaSnapshot?: unknown;
  };
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "invalid json" }, { status: 400 });
  }

  const type = text(body.type, 32) || "favorite_update";
  const title = text(body.title, 120);
  const content = text(body.body, 500);
  if (!title || !content) return NextResponse.json({ error: "missing title or body" }, { status: 400 });
  const media = mediaKey(body.mediaKey);
  const episode = text(body.episodeName, 180);
  const snapshot = text(body.mediaSnapshot, MAX_SNAPSHOT) || "{}";

  try {
    const db = await getDb();
    // 同类同剧集在去重窗口内不重复提醒
    if (media && episode) {
      const existing = await db
        .select({ id: notifications.id })
        .from(notifications)
        .where(
          and(
            eq(notifications.userId, user.id),
            eq(notifications.type, type),
            eq(notifications.mediaKey, media),
            eq(notifications.episodeName, episode),
            gt(notifications.createdAt, Math.floor(Date.now() / 1000) - DEDUPE_WINDOW_SECONDS),
          ),
        )
        .limit(1);
      if (existing.length > 0) {
        return NextResponse.json({ ok: true, deduped: true }, { headers: { "Cache-Control": "no-store" } });
      }
    }
    await db.insert(notifications).values({
      id: crypto.randomUUID(),
      userId: user.id,
      type,
      title,
      body: content,
      mediaKey: media,
      episodeName: episode,
      mediaSnapshot: snapshot,
    });
    return NextResponse.json({ ok: true, deduped: false }, { headers: { "Cache-Control": "no-store" } });
  } catch {
    return NextResponse.json({ error: "internal server error" }, { status: 500 });
  }
}

export async function DELETE(request: Request) {
  const user = await getCurrentUser(request);
  if (!user) return NextResponse.json({ error: "authentication required" }, { status: 401 });
  const id = text(new URL(request.url).searchParams.get("id"), 64);
  if (!id) return NextResponse.json({ error: "missing id" }, { status: 400 });
  try {
    const db = await getDb();
    await db.delete(notifications).where(and(eq(notifications.id, id), eq(notifications.userId, user.id)));
    return NextResponse.json({ ok: true }, { headers: { "Cache-Control": "no-store" } });
  } catch {
    return NextResponse.json({ error: "internal server error" }, { status: 500 });
  }
}
