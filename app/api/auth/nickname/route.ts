import { eq } from "drizzle-orm";
import { NextResponse } from "next/server";
import { getDb } from "../../../../db";
import { users } from "../../../../db/schema";
import { ACCOUNT_AUTH_ENABLED, accountAuthDisabledResponse, getCurrentUser, normalizeNickname, publicUser } from "../../../lib/auth";

export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  if (!ACCOUNT_AUTH_ENABLED) return accountAuthDisabledResponse();
  const current = await getCurrentUser(request);
  if (!current) return NextResponse.json({ error: "authentication required" }, { status: 401 });

  let body: { nickname?: unknown };
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "invalid json" }, { status: 400 });
  }

  const nickname = normalizeNickname(String(body.nickname ?? ""));
  if (!nickname) return NextResponse.json({ error: "nickname required" }, { status: 400 });

  const db = await getDb();
  await db
    .update(users)
    .set({ nickname, updatedAt: Math.floor(Date.now() / 1000) })
    .where(eq(users.id, current.id));
  return NextResponse.json({ user: publicUser({ ...current, nickname }) });
}
