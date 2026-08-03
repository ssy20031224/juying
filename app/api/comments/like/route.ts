import { and, eq } from "drizzle-orm";
import { NextResponse } from "next/server";
import { getDb } from "../../../../db";
import { commentLikes, comments as commentsTable } from "../../../../db/schema";
import { getCurrentUser } from "../../../lib/auth";

export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  const currentUser = await getCurrentUser(request);
  if (!currentUser) {
    return NextResponse.json({ error: "authentication required" }, { status: 401 });
  }
  let body: { media?: unknown; id?: unknown; commentId?: unknown };
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "invalid json" }, { status: 400 });
  }
  const media = String(body.media || "").replace(/[^A-Za-z0-9:_@./-]/g, "_").slice(0, 140);
  const id = String(body.id || body.commentId || "");
  if (!media || !id) return NextResponse.json({ error: "missing media or id" }, { status: 400 });

  try {
    const db = await getDb();
    const comment = await db
      .select({ id: commentsTable.id })
      .from(commentsTable)
      .where(and(eq(commentsTable.id, id), eq(commentsTable.mediaKey, media)))
      .limit(1);
    if (!comment.length) return NextResponse.json({ error: "comment not found" }, { status: 404 });

    const existing = await db
      .select({ userId: commentLikes.userId })
      .from(commentLikes)
      .where(and(eq(commentLikes.userId, currentUser.id), eq(commentLikes.commentId, id)))
      .limit(1);
    if (existing.length) {
      await db
        .delete(commentLikes)
        .where(and(eq(commentLikes.userId, currentUser.id), eq(commentLikes.commentId, id)));
      return NextResponse.json({ ok: true, liked: false }, { headers: { "Cache-Control": "no-store" } });
    }
    await db.insert(commentLikes).values({ userId: currentUser.id, commentId: id });
    return NextResponse.json({ ok: true, liked: true }, { headers: { "Cache-Control": "no-store" } });
  } catch {
    return NextResponse.json({ error: "internal server error" }, { status: 500 });
  }
}
