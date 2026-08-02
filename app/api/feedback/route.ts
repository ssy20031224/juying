import { NextResponse } from "next/server";
import { getDb } from "../../../db";
import { feedback } from "../../../db/schema";
import { getCurrentUser } from "../../lib/auth";

export const dynamic = "force-dynamic";

const CATEGORIES = new Set(["suggestion", "bug", "content", "account", "other"]);

export async function POST(request: Request) {
  const current = await getCurrentUser(request);
  if (!current) return NextResponse.json({ error: "authentication required" }, { status: 401 });

  let body: { category?: unknown; text?: unknown; appVersion?: unknown; device?: unknown };
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "invalid json" }, { status: 400 });
  }

  const category = String(body.category ?? "suggestion").trim();
  const text = String(body.text ?? "").trim();
  const appVersion = String(body.appVersion ?? "").trim().slice(0, 40);
  const device = String(body.device ?? "").trim().slice(0, 160);
  if (!CATEGORIES.has(category)) {
    return NextResponse.json({ error: "invalid feedback category" }, { status: 400 });
  }
  if (text.length < 5 || text.length > 2000) {
    return NextResponse.json({ error: "反馈内容需为 5 至 2000 个字符" }, { status: 400 });
  }

  const db = await getDb();
  await db.insert(feedback).values({
    id: crypto.randomUUID(),
    userId: current.id,
    category,
    text,
    appVersion,
    device,
  });
  return NextResponse.json({ ok: true }, { status: 201 });
}
