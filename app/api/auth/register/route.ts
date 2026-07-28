import { eq } from "drizzle-orm";
import { getDb } from "../../../../db";
import { users } from "../../../../db/schema";
import {
  createSession,
  hashPassword,
  isStrongPassword,
  normalizeEmail,
  normalizeNickname,
  publicUser,
  sessionCookie,
} from "../../../lib/auth";

export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  let body: { email?: unknown; password?: unknown; nickname?: unknown };
  try {
    body = await request.json();
  } catch {
    return Response.json({ error: "invalid json" }, { status: 400 });
  }

  const email = normalizeEmail(String(body.email ?? ""));
  const password = String(body.password ?? "");
  const nickname = normalizeNickname(String(body.nickname ?? "")) || `漫友_${Math.floor(1000 + Math.random() * 9000)}`;

  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return Response.json({ error: "invalid email" }, { status: 400 });
  }
  if (!isStrongPassword(password)) {
    return Response.json(
      { error: "password must be 8-128 characters and include at least three character types" },
      { status: 400 },
    );
  }

  const db = await getDb();
  const existing = await db.select({ id: users.id }).from(users).where(eq(users.email, email)).limit(1);
  if (existing.length > 0) {
    return Response.json({ error: "email already registered" }, { status: 409 });
  }

  const user = {
    id: crypto.randomUUID(),
    email,
    passwordHash: await hashPassword(password),
    nickname,
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
