import { eq } from "drizzle-orm";
import { NextResponse } from "next/server";
import { getDb } from "../../../../db";
import { users } from "../../../../db/schema";
import {
  ACCOUNT_AUTH_ENABLED,
  accountAuthDisabledResponse,
  getCurrentUser,
  hashPassword,
  isStrongPassword,
  verifyPassword,
} from "../../../lib/auth";

export const dynamic = "force-dynamic";

export async function POST(request: Request) {
  if (!ACCOUNT_AUTH_ENABLED) return accountAuthDisabledResponse();
  const current = await getCurrentUser(request);
  if (!current) return NextResponse.json({ error: "authentication required" }, { status: 401 });

  let body: { oldPassword?: unknown; newPassword?: unknown; confirmPassword?: unknown };
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "invalid json" }, { status: 400 });
  }

  const oldPassword = String(body.oldPassword ?? "");
  const newPassword = String(body.newPassword ?? "");
  const confirmPassword = String(body.confirmPassword ?? "");
  if (!oldPassword) {
    return NextResponse.json({ error: "请输入原密码" }, { status: 400 });
  }
  if (!isStrongPassword(newPassword) || newPassword !== confirmPassword) {
    return NextResponse.json({ error: "新密码强度不足或两次输入不一致" }, { status: 400 });
  }
  if (oldPassword === newPassword) {
    return NextResponse.json({ error: "新密码不能与原密码相同" }, { status: 400 });
  }

  const db = await getDb();
  const rows = await db
    .select({ passwordHash: users.passwordHash })
    .from(users)
    .where(eq(users.id, current.id))
    .limit(1);
  const user = rows[0];
  if (!user || !(await verifyPassword(oldPassword, user.passwordHash))) {
    return NextResponse.json({ error: "原密码不正确" }, { status: 400 });
  }

  await db
    .update(users)
    .set({ passwordHash: await hashPassword(newPassword), updatedAt: Math.floor(Date.now() / 1000) })
    .where(eq(users.id, current.id));
  return NextResponse.json({ ok: true });
}
