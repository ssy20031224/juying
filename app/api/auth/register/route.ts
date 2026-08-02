import { eq } from "drizzle-orm";
import { getDb } from "../../../../db";
import { users } from "../../../../db/schema";
import {
  createSession,
  ACCOUNT_AUTH_ENABLED,
  accountAuthDisabledResponse,
  hashPassword,
  isStrongPassword,
  normalizeEmail,
  normalizeNickname,
  publicUser,
  sessionCookie,
} from "../../../lib/auth";
import { consumeVerificationCode } from "../../../lib/email";

export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  if (!ACCOUNT_AUTH_ENABLED) return accountAuthDisabledResponse();
  let body: { email?: unknown; password?: unknown; nickname?: unknown; code?: unknown };
  try {
    body = await request.json();
  } catch {
    return Response.json({ error: "invalid json" }, { status: 400 });
  }

  const email = normalizeEmail(String(body.email ?? ""));
  const password = String(body.password ?? "");
  const code = String(body.code ?? "").trim();
  const nickname = normalizeNickname(String(body.nickname ?? ""));

  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return Response.json({ error: "invalid email" }, { status: 400 });
  }
  if (!isStrongPassword(password)) {
    return Response.json(
      { error: "password must be 8-128 characters and include at least three character types" },
      { status: 400 },
    );
  }
  if (!nickname) {
    return Response.json({ error: "nickname required" }, { status: 400 });
  }
  if (!code) {
    return Response.json({ error: "email verification code required" }, { status: 400 });
  }

  const db = await getDb();
  const existing = await db.select({ id: users.id }).from(users).where(eq(users.email, email)).limit(1);
  if (existing.length > 0) {
    return Response.json({ error: "email already registered" }, { status: 409 });
  }
  // Derive the password before consuming the one-time code. This prevents a
  // transient crypto/runtime failure from burning a valid code.
  const passwordHash = await hashPassword(password);
  if (!(await consumeVerificationCode(email, "register", code))) {
    return Response.json({ error: "invalid or expired email verification code" }, { status: 400 });
  }

  const user = {
    id: crypto.randomUUID(),
    email,
    passwordHash,
    nickname,
    avatarUrl: "",
  };
  await db.insert(users).values(user);

  const token = await createSession(user.id);
  return new Response(JSON.stringify({ user: publicUser(user), token }), {
    status: 201,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Set-Cookie": sessionCookie(token),
      "Cache-Control": "no-store",
    },
  });
}
