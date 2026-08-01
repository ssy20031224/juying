import { desc, eq } from "drizzle-orm";
import { NextResponse } from "next/server";
import { getDb } from "../../../db";
import { comments as commentsTable, users } from "../../../db/schema";
import { getCurrentUser } from "../../lib/auth";

export const dynamic = "force-dynamic";
const COMMENTS_POSTING_ENABLED = process.env.COMMENTS_POSTING_ENABLED !== "false";

const MAX_COMMENTS = 500;
const MAX_TEXT_LEN = 200;
const MAX_NICK_LEN = 24;
const MAX_MEDIA_KEY_LEN = 140;

type CloudComment = { id: string; nick: string; text: string; ts: number };

interface OssConfig {
  accessKeyId: string;
  accessKeySecret: string;
  endpointHost: string;
  bucket: string;
  prefix: string;
  publicBase: string;
  writeConfigured: boolean;
}

function ossConfig(): OssConfig {
  const accessKeyId = (process.env.ALIYUN_OSS_ACCESS_KEY_ID || "").trim();
  const accessKeySecret = (process.env.ALIYUN_OSS_ACCESS_KEY_SECRET || "").trim();
  const endpointHost = (process.env.ALIYUN_OSS_ENDPOINT || "")
    .trim()
    .replace(/^https?:\/\//, "")
    .replace(/\/+$/, "");
  const bucket = (process.env.ALIYUN_OSS_BUCKET || "").trim();
  const prefix = (process.env.COMMENTS_OSS_PREFIX || "comments").trim().replace(/^\/+|\/+$/g, "") || "comments";
  const publicBase = (process.env.COMMENTS_PUBLIC_BASE_URL || "").trim().replace(/\/+$/, "");
  return {
    accessKeyId,
    accessKeySecret,
    endpointHost,
    bucket,
    prefix,
    publicBase,
    writeConfigured: Boolean(accessKeyId && accessKeySecret && endpointHost && bucket),
  };
}

function sanitizeMediaKey(raw: string): string {
  return raw.replace(/[^A-Za-z0-9:_@./-]/g, "_").slice(0, MAX_MEDIA_KEY_LEN);
}

async function readComments(cfg: OssConfig, objectKey: string): Promise<CloudComment[]> {
  if (!cfg.publicBase) return [];
  try {
    const response = await fetch(`${cfg.publicBase}/${objectKey}`, { cache: "no-store" });
    if (!response.ok) return [];
    const data = (await response.json()) as { comments?: unknown };
    if (!Array.isArray(data?.comments)) return [];
    return (data.comments as CloudComment[]).filter(
      (item) => item && typeof item.text === "string" && item.text.trim().length > 0,
    );
  } catch {
    return [];
  }
}

async function hmacSha1Base64(secret: string, data: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-1" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(data));
  let binary = "";
  new Uint8Array(signature).forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary);
}

async function writeComments(cfg: OssConfig, objectKey: string, comments: CloudComment[]): Promise<boolean> {
  const contentType = "application/json; charset=utf-8";
  const date = new Date().toUTCString();
  const stringToSign = `PUT\n\n${contentType}\n${date}\n/${cfg.bucket}/${objectKey}`;
  const signature = await hmacSha1Base64(cfg.accessKeySecret, stringToSign);
  try {
    const response = await fetch(`https://${cfg.bucket}.${cfg.endpointHost}/${objectKey}`, {
      method: "PUT",
      headers: {
        Date: date,
        "Content-Type": contentType,
        Authorization: `OSS ${cfg.accessKeyId}:${signature}`,
      },
      body: JSON.stringify({ comments }),
    });
    return response.ok;
  } catch {
    return false;
  }
}

async function readDbComments(media: string): Promise<CloudComment[]> {
  const db = await getDb();
  const rows = await db
    .select({
      id: commentsTable.id,
      nick: users.nickname,
      text: commentsTable.text,
      ts: commentsTable.createdAt,
    })
    .from(commentsTable)
    .innerJoin(users, eq(users.id, commentsTable.userId))
    .where(eq(commentsTable.mediaKey, media))
    .orderBy(desc(commentsTable.createdAt))
    .limit(MAX_COMMENTS);
  return rows.reverse().map((row) => ({
    id: row.id,
    nick: row.nick,
    text: row.text,
    ts: Number(row.ts) * 1000,
  }));
}

async function writeDbComment(userId: string, media: string, text: string): Promise<CloudComment[]> {
  const db = await getDb();
  await db.insert(commentsTable).values({
    id: crypto.randomUUID(),
    userId,
    mediaKey: media,
    text,
  });
  return readDbComments(media);
}

export async function GET(request: Request) {
  const media = sanitizeMediaKey(new URL(request.url).searchParams.get("media") || "");
  if (!media) return NextResponse.json({ error: "missing media" }, { status: 400 });

  try {
    const comments = await readDbComments(media);
    return NextResponse.json({ comments }, { headers: { "Cache-Control": "no-store" } });
  } catch {
    // D1 may not be provisioned in local/legacy deployments; keep OSS fallback below.
  }

  const cfg = ossConfig();
  if (!cfg.publicBase) return NextResponse.json({ error: "comments storage not configured" }, { status: 503 });
  const comments = await readComments(cfg, `${cfg.prefix}/${media}.json`);
  return NextResponse.json({ comments }, { headers: { "Cache-Control": "no-store" } });
}

export async function POST(request: Request) {
  if (!COMMENTS_POSTING_ENABLED) {
    return NextResponse.json({ error: "comment posting is temporarily disabled" }, { status: 503 });
  }
  let body: { media?: unknown; nick?: unknown; text?: unknown };
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "invalid json" }, { status: 400 });
  }

  const media = sanitizeMediaKey(String(body.media || ""));
  const text = String(body.text || "").trim().slice(0, MAX_TEXT_LEN);
  if (!media || !text) return NextResponse.json({ error: "missing media or text" }, { status: 400 });

  const currentUser = await getCurrentUser(request);
  if (!currentUser && process.env.COMMENTS_REQUIRE_ACCOUNT !== "false") {
    return NextResponse.json({ error: "authentication required" }, { status: 401 });
  }
  if (currentUser) {
    try {
      const comments = await writeDbComment(currentUser.id, media, text);
      return NextResponse.json({ comments }, { headers: { "Cache-Control": "no-store" } });
    } catch {
      // Fall through to OSS only when D1 is not available yet.
    }
  }

  const cfg = ossConfig();
  if (!cfg.publicBase || !cfg.writeConfigured) {
    return NextResponse.json({ error: "comments storage not configured" }, { status: 503 });
  }
  const nick = String(body.nick || "").trim().slice(0, MAX_NICK_LEN) || "动漫用户";
  const objectKey = `${cfg.prefix}/${media}.json`;
  const comments = await readComments(cfg, objectKey);
  comments.push({ id: crypto.randomUUID(), nick, text, ts: Date.now() });
  const trimmed = comments.slice(-MAX_COMMENTS);
  const saved = await writeComments(cfg, objectKey, trimmed);
  if (!saved) return NextResponse.json({ error: "comments storage write failed" }, { status: 502 });
  return NextResponse.json({ comments: trimmed }, { headers: { "Cache-Control": "no-store" } });
}
