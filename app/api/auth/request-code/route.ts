import { eq } from "drizzle-orm";
import { NextResponse } from "next/server";
import { getDb } from "../../../../db";
import { users } from "../../../../db/schema";
import { issueVerificationCode, type VerificationPurpose } from "../../../lib/email";
import { ACCOUNT_AUTH_ENABLED, accountAuthDisabledResponse, getCurrentUser, normalizeEmail } from "../../../lib/auth";

export const dynamic = "force-dynamic";

const purposes = new Set<VerificationPurpose>(["register", "change-email", "reset-password"]);

export async function POST(request: Request) {
  if (!ACCOUNT_AUTH_ENABLED) return accountAuthDisabledResponse();
  let body: { email?: unknown; purpose?: unknown };
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "invalid json" }, { status: 400 });
  }
  const email = normalizeEmail(String(body.email ?? ""));
  const purpose = String(body.purpose ?? "") as VerificationPurpose;
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || !purposes.has(purpose)) {
    return NextResponse.json({ error: "invalid email or purpose" }, { status: 400 });
  }

  const db = await getDb();
  const rows = await db.select({ id: users.id }).from(users).where(eq(users.email, email)).limit(1);
  if (purpose === "register" && rows.length > 0) {
    return NextResponse.json({ error: "email already registered" }, { status: 409 });
  }
  if (purpose === "change-email") {
    const current = await getCurrentUser(request);
    if (!current) return NextResponse.json({ error: "authentication required" }, { status: 401 });
    if (rows.length > 0) return NextResponse.json({ error: "email already registered" }, { status: 409 });
  }
  if (purpose === "reset-password" && rows.length === 0) {
    return NextResponse.json({ ok: true });
  }

  try {
    await issueVerificationCode(email, purpose);
    return NextResponse.json({ ok: true });
  } catch (error) {
    const message = error instanceof Error ? error.message : "failed to send verification code";
    return NextResponse.json({ error: message }, { status: 502 });
  }
}
