import { eq } from "drizzle-orm";
import { NextResponse } from "next/server";
import { getDb } from "../../../../db";
import { authSessions, users } from "../../../../db/schema";
import { consumeVerificationCode } from "../../../lib/email";
import { hashPassword, isStrongPassword, normalizeEmail } from "../../../lib/auth";

export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  let body: { email?: unknown; code?: unknown; password?: unknown; confirmPassword?: unknown };
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "invalid json" }, { status: 400 });
  }
  const email = normalizeEmail(String(body.email ?? ""));
  const code = String(body.code ?? "").trim();
  const password = String(body.password ?? "");
  const confirmPassword = String(body.confirmPassword ?? "");
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || !code) {
    return NextResponse.json({ error: "email and verification code required" }, { status: 400 });
  }
  if (!isStrongPassword(password) || password !== confirmPassword) {
    return NextResponse.json({ error: "password is invalid or confirmation does not match" }, { status: 400 });
  }
  const db = await getDb();
  const rows = await db.select({ id: users.id }).from(users).where(eq(users.email, email)).limit(1);
  const user = rows[0];
  if (!user || !(await consumeVerificationCode(email, "reset-password", code))) {
    return NextResponse.json({ error: "invalid or expired reset request" }, { status: 400 });
  }
  await db.update(users).set({ passwordHash: await hashPassword(password), updatedAt: Math.floor(Date.now() / 1000) }).where(eq(users.id, user.id));
  await db.delete(authSessions).where(eq(authSessions.userId, user.id));
  return NextResponse.json({ ok: true });
}
