import { eq } from "drizzle-orm";
import { NextResponse } from "next/server";
import { getDb } from "../../../../db";
import { users } from "../../../../db/schema";
import { ACCOUNT_AUTH_ENABLED, accountAuthDisabledResponse, getCurrentUser, publicUser } from "../../../lib/auth";

export const dynamic = "force-dynamic";

const MAX_AVATAR_BYTES = 5 * 1024 * 1024;

function config() {
  const endpointHost = (process.env.ALIYUN_OSS_ENDPOINT || "").trim().replace(/^https?:\/\//, "").replace(/\/+$/, "");
  const bucket = (process.env.ALIYUN_OSS_BUCKET || "").trim();
  const publicBase = (process.env.ALIYUN_OSS_PUBLIC_BASE_URL || "").trim().replace(/\/+$/, "");
  return {
    accessKeyId: (process.env.ALIYUN_OSS_ACCESS_KEY_ID || "").trim(),
    accessKeySecret: (process.env.ALIYUN_OSS_ACCESS_KEY_SECRET || "").trim(),
    endpointHost,
    bucket,
    publicBase,
  };
}

async function hmac(secret: string, value: string): Promise<string> {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-1" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(value));
  let binary = "";
  new Uint8Array(signature).forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary);
}

export async function POST(request: Request) {
  if (!ACCOUNT_AUTH_ENABLED) return accountAuthDisabledResponse();
  const current = await getCurrentUser(request);
  if (!current) return NextResponse.json({ error: "authentication required" }, { status: 401 });
  const form = await request.formData();
  const value = form.get("file");
  if (!(value instanceof File)) return NextResponse.json({ error: "avatar file required" }, { status: 400 });
  const type = value.type.toLowerCase();
  const extension = type === "image/png" ? "png" : type === "image/webp" ? "webp" : type === "image/gif" ? "gif" : "jpg";
  if (!["image/jpeg", "image/png", "image/webp", "image/gif"].includes(type)) {
    return NextResponse.json({ error: "only jpg, png, webp or gif images are supported" }, { status: 400 });
  }
  if (value.size <= 0 || value.size > MAX_AVATAR_BYTES) {
    return NextResponse.json({ error: "avatar must be smaller than 5MB" }, { status: 400 });
  }

  const cfg = config();
  if (!cfg.accessKeyId || !cfg.accessKeySecret || !cfg.endpointHost || !cfg.bucket) {
    return NextResponse.json({ error: "avatar storage not configured" }, { status: 503 });
  }
  const objectKey = `avatars/${current.id}/${crypto.randomUUID()}.${extension}`;
  const date = new Date().toUTCString();
  const contentType = type === "image/jpeg" ? "image/jpeg" : type;
  const signature = await hmac(
    cfg.accessKeySecret,
    `PUT\n\n${contentType}\n${date}\n/${cfg.bucket}/${objectKey}`,
  );
  const upload = await fetch(`https://${cfg.bucket}.${cfg.endpointHost}/${objectKey}`, {
    method: "PUT",
    headers: {
      Date: date,
      "Content-Type": contentType,
      Authorization: `OSS ${cfg.accessKeyId}:${signature}`,
    },
    body: await value.arrayBuffer(),
  });
  if (!upload.ok) return NextResponse.json({ error: "avatar upload failed" }, { status: 502 });

  const avatarUrl = `${cfg.publicBase || `https://${cfg.bucket}.${cfg.endpointHost}`}/${objectKey}`;
  const db = await getDb();
  await db.update(users).set({ avatarUrl, updatedAt: Math.floor(Date.now() / 1000) }).where(eq(users.id, current.id));
  return NextResponse.json({ user: publicUser({ ...current, avatarUrl }) });
}
