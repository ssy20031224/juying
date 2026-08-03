import { and, eq, gt, sql } from "drizzle-orm";
import { NextResponse } from "next/server";
import { getDb } from "../../../db";
import { deviceCacheItems, favorites, watchProgress } from "../../../db/schema";
import { getCurrentUser, publicUser } from "../../lib/auth";

export const dynamic = "force-dynamic";

const MAX_ITEMS = 500;
const MAX_SNAPSHOT = 12_000;

function text(value: unknown, max = 180): string {
  return String(value ?? "").trim().slice(0, max);
}

function mediaKey(value: unknown): string {
  return text(value, 180).replace(/[^A-Za-z0-9:_@./-]/g, "_");
}

function asInt(value: unknown, fallback = 0, max = Number.MAX_SAFE_INTEGER): number {
  const number = Number(value);
  if (!Number.isFinite(number)) return fallback;
  return Math.max(0, Math.min(Math.floor(number), max));
}

function asBool(value: unknown): boolean {
  return value === true || value === 1 || value === "1" || value === "true";
}

async function requireUser(request: Request) {
  const user = await getCurrentUser(request);
  if (!user) return null;
  return user;
}

export async function GET(request: Request) {
  const user = await requireUser(request);
  if (!user) return NextResponse.json({ error: "authentication required" }, { status: 401 });

  const since = asInt(new URL(request.url).searchParams.get("since"), 0, 9_999_999_999);
  try {
    const db = await getDb();
    const [favoriteRows, progressRows, cacheRows] = await Promise.all([
      db
        .select()
        .from(favorites)
        .where(and(eq(favorites.userId, user.id), gt(favorites.updatedAt, since)))
        .catch(() => []),
      db
        .select()
        .from(watchProgress)
        .where(and(eq(watchProgress.userId, user.id), gt(watchProgress.updatedAt, since)))
        .catch(() => []),
      db
        .select()
        .from(deviceCacheItems)
        .where(and(eq(deviceCacheItems.userId, user.id), gt(deviceCacheItems.updatedAt, since)))
        .catch(() => []),
    ]);

    return NextResponse.json({
      user: publicUser(user),
      serverTime: Math.floor(Date.now() / 1000),
      favorites: favoriteRows,
      progress: progressRows,
      deviceCache: cacheRows,
    });
  } catch (error) {
    return NextResponse.json({
      user: publicUser(user),
      serverTime: Math.floor(Date.now() / 1000),
      favorites: [],
      progress: [],
      deviceCache: [],
    });
  }
}

export async function POST(request: Request) {
  const user = await requireUser(request);
  if (!user) return NextResponse.json({ error: "authentication required" }, { status: 401 });

  let body: {
    favorites?: unknown;
    progress?: unknown;
    deviceCache?: unknown;
    deviceId?: unknown;
    replaceFavorites?: unknown;
    replaceProgress?: unknown;
    replaceDeviceCache?: unknown;
  };
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "invalid json" }, { status: 400 });
  }

  const favoriteItems = Array.isArray(body.favorites) ? body.favorites.slice(0, MAX_ITEMS) : [];
  const progressItems = Array.isArray(body.progress) ? body.progress.slice(0, MAX_ITEMS) : [];
  const cacheItems = Array.isArray(body.deviceCache) ? body.deviceCache.slice(0, MAX_ITEMS) : [];
  const currentDeviceId = text(body.deviceId, 120);

  // 注意：不能用 drizzle 的 db.transaction() —— 其 D1 驱动用原生 BEGIN/COMMIT SQL，
  // 而 D1 明确拒绝该写法（须用 batch API 或顺序执行）。这里改为顺序执行，
  // 每条 upsert 幂等，即使中途失败，下次同步会自动补齐。
  try {
    const db = await getDb();
    if (body.replaceFavorites === true) {
      await db.delete(favorites).where(eq(favorites.userId, user.id)).catch(() => {});
    }
    if (body.replaceProgress === true) {
      await db.delete(watchProgress).where(eq(watchProgress.userId, user.id)).catch(() => {});
    }
    if (body.replaceDeviceCache === true && currentDeviceId) {
      await db
        .delete(deviceCacheItems)
        .where(
          and(
            eq(deviceCacheItems.userId, user.id),
            eq(deviceCacheItems.deviceId, currentDeviceId),
          ),
        )
        .catch(() => {});
    }
    for (const raw of favoriteItems) {
      if (!raw || typeof raw !== "object") continue;
      const item = raw as Record<string, unknown>;
      const key = mediaKey(item.mediaKey);
      if (!key) continue;
      const snapshot = text(item.mediaSnapshot, MAX_SNAPSHOT) || "{}";
      await db
        .insert(favorites)
        .values({ userId: user.id, mediaKey: key, mediaSnapshot: snapshot })
        .onConflictDoUpdate({
          target: [favorites.userId, favorites.mediaKey],
          set: { mediaSnapshot: snapshot, updatedAt: sql`(unixepoch())` },
        })
        .catch(() => {});
    }

    for (const raw of progressItems) {
      if (!raw || typeof raw !== "object") continue;
      const item = raw as Record<string, unknown>;
      const media = mediaKey(item.mediaKey);
      const episode = mediaKey(item.episodeKey);
      if (!media || !episode) continue;
      await db
        .insert(watchProgress)
        .values({
          userId: user.id,
          mediaKey: media,
          episodeKey: episode,
          mediaSnapshot: text(item.mediaSnapshot, MAX_SNAPSHOT) || "{}",
          episodeName: text(item.episodeName, 180),
          sourceKey: text(item.sourceKey, 120),
          positionMs: asInt(item.positionMs, 0, 86_400_000),
          durationMs: asInt(item.durationMs, 0, 86_400_000),
          completed: asBool(item.completed),
        })
        .onConflictDoUpdate({
          target: [watchProgress.userId, watchProgress.mediaKey, watchProgress.episodeKey],
          set: {
            episodeName: text(item.episodeName, 180),
            mediaSnapshot: text(item.mediaSnapshot, MAX_SNAPSHOT) || "{}",
            sourceKey: text(item.sourceKey, 120),
            positionMs: asInt(item.positionMs, 0, 86_400_000),
            durationMs: asInt(item.durationMs, 0, 86_400_000),
            completed: asBool(item.completed),
            updatedAt: sql`(unixepoch())`,
          },
        })
        .catch(() => {});
    }

    for (const raw of cacheItems) {
      if (!raw || typeof raw !== "object") continue;
      const item = raw as Record<string, unknown>;
      const device = text(item.deviceId, 120);
      const media = mediaKey(item.mediaKey);
      const episode = mediaKey(item.episodeKey);
      if (!device || !media || !episode) continue;
      await db
        .insert(deviceCacheItems)
        .values({
          userId: user.id,
          deviceId: device,
          mediaKey: media,
          episodeKey: episode,
          status: text(item.status, 32) || "downloaded",
        })
        .onConflictDoUpdate({
          target: [
            deviceCacheItems.userId,
            deviceCacheItems.deviceId,
            deviceCacheItems.mediaKey,
            deviceCacheItems.episodeKey,
          ],
          set: { status: text(item.status, 32) || "downloaded", updatedAt: sql`(unixepoch())` },
        })
        .catch(() => {});
    }
  } catch (error) {}

  return GET(request);
}
