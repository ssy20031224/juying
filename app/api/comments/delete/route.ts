import { and, eq, inArray } from "drizzle-orm";
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
    const target = await db
      .select({ id: commentsTable.id, userId: commentsTable.userId })
      .from(commentsTable)
      .where(and(eq(commentsTable.id, id), eq(commentsTable.mediaKey, media)))
      .limit(1);
    if (!target.length) return NextResponse.json({ error: "comment not found" }, { status: 404 });
    if (target[0].userId !== currentUser.id) {
      return NextResponse.json({ error: "forbidden" }, { status: 403 });
    }
    // 仅作者可删除；同时删除该评论的楼中楼回复及其点赞记录
    const replies = await db
      .select({ id: commentsTable.id })
      .from(commentsTable)
      .where(eq(commentsTable.parentId, id));
    const ids = [id, ...replies.map((row) => row.id)];
    await db.delete(commentLikes).where(inArray(commentLikes.commentId, ids));
    await db.delete(commentsTable).where(inArray(commentsTable.id, ids));
    return NextResponse.json({ ok: true }, { headers: { "Cache-Control": "no-store" } });
  } catch {
    return NextResponse.json({ error: "internal server error" }, { status: 500 });
  }
}
