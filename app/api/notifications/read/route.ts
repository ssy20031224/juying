import { and, eq, inArray } from "drizzle-orm";
import { NextResponse } from "next/server";
import { getDb } from "../../../../db";
import { notifications } from "../../../../db/schema";
import { getCurrentUser } from "../../../lib/auth";

export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  const user = await getCurrentUser(request);
  if (!user) return NextResponse.json({ error: "authentication required" }, { status: 401 });

  let body: { id?: unknown; ids?: unknown };
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "invalid json" }, { status: 400 });
  }

  try {
    const db = await getDb();
    const rawId = String(body.id || "").trim();
    if (rawId) {
      await db
        .update(notifications)
        .set({ read: true })
        .where(and(eq(notifications.userId, user.id), eq(notifications.id, rawId)));
    } else if (Array.isArray(body.ids)) {
      const ids = body.ids.map((value) => String(value).trim()).filter(Boolean);
      if (ids.length > 0) {
        await db
          .update(notifications)
          .set({ read: true })
          .where(and(eq(notifications.userId, user.id), inArray(notifications.id, ids)));
      }
    } else {
      await db.update(notifications).set({ read: true }).where(eq(notifications.userId, user.id));
    }
    return NextResponse.json({ ok: true }, { headers: { "Cache-Control": "no-store" } });
  } catch {
    return NextResponse.json({ error: "internal server error" }, { status: 500 });
  }
}
