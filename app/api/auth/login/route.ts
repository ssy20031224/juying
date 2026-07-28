import { eq } from "drizzle-orm";
import { getDb } from "../../../../db";
import { users } from "../../../../db/schema";
import {
  createSession,
  publicUser,
  sessionCookie,
  normalizeEmail,
  verifyPassword,
} from "../../../lib/auth";

export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  let body: { email?: unknown; password?: unknown };
  try {
    body = await request.json();
  } catch {
    return Response.json({ error: "invalid json" }, { status: 400 });
  }

  const email = normalizeEmail(String(body.email ?? ""));
  const password = String(body.password ?? "");
  const db = await getDb();
  const rows = await db.select().from(users).where(eq(users.email, email)).limit(1);
  const user = rows[0];
  if (!user || !(await verifyPassword(password, user.passwordHash))) {
    return Response.json({ error: "invalid email or password" }, { status: 401 });
  }

  const token = await createSession(user.id);
  return new Response(
    JSON.stringify({
      user: publicUser({ id: user.id, email: user.email, nickname: user.nickname }),
      token,
    }),
    {
      status: 200,
      headers: {
        "Content-Type": "application/json; charset=utf-8",
        "Set-Cookie": sessionCookie(token),
        "Cache-Control": "no-store",
      },
    },
  );
}
