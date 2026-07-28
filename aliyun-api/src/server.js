import { randomUUID } from "node:crypto";
import express from "express";
import multer from "multer";
import OSS from "ali-oss";
import { config } from "./config.js";
import { pool, transaction } from "./db.js";
import {
  createSession,
  currentUser,
  hashPassword,
  isStrongPassword,
  isValidEmail,
  normalizeEmail,
  normalizeNickname,
  nowSeconds,
  publicUser,
  requireUser,
  revokeSession,
  verifyPassword,
} from "./auth.js";
import { consumeCode, issueCode } from "./email.js";

const app = express();
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 5 * 1024 * 1024, files: 1 },
});
const oss = new OSS({
  ...(config.oss.endpoint ? { endpoint: config.oss.endpoint } : { region: config.oss.region }),
  accessKeyId: config.oss.accessKeyId,
  accessKeySecret: config.oss.accessKeySecret,
  bucket: config.oss.bucket,
  ...(config.oss.stsToken ? { stsToken: config.oss.stsToken } : {}),
  secure: true,
});

app.disable("x-powered-by");
app.set("trust proxy", 1);
app.use(express.json({ limit: "1mb" }));
app.use((req, res, next) => {
  res.setHeader("Cache-Control", "no-store");
  res.setHeader("X-Content-Type-Options", "nosniff");
  next();
});

// TEMP: 账号接口保留但默认停用，避免删除后续恢复所需的登录/注册逻辑。
app.use("/api/auth", (req, res, next) => {
  if (!config.accountAuthEnabled) {
    return res.status(503).json({ error: "account login and registration are temporarily disabled" });
  }
  next();
});

function safeText(value, max = 180) {
  return String(value ?? "").trim().slice(0, max);
}

function mediaKey(value) {
  return safeText(value, 180).replace(/[^A-Za-z0-9:_@./-]/g, "_");
}

function safeInt(value, max = Number.MAX_SAFE_INTEGER) {
  const number = Number(value);
  return Number.isFinite(number) ? Math.max(0, Math.min(Math.floor(number), max)) : 0;
}

function snapshot(value) {
  try {
    const parsed = typeof value === "string" ? JSON.parse(value) : value;
    return JSON.stringify(parsed && typeof parsed === "object" ? parsed : {});
  } catch {
    return "{}";
  }
}

function snapshotText(value) {
  return typeof value === "string" ? value : JSON.stringify(value || {});
}

async function syncPayload(userId, since = 0) {
  const [favoriteRows] = await pool.execute(
    "SELECT media_key, media_snapshot, created_at, updated_at FROM favorites WHERE user_id = ? AND updated_at > ?",
    [userId, since],
  );
  const [progressRows] = await pool.execute(
    `SELECT media_key, episode_key, media_snapshot, episode_name, source_key,
            position_ms, duration_ms, completed, updated_at
       FROM watch_progress WHERE user_id = ? AND updated_at > ?`,
    [userId, since],
  );
  const [cacheRows] = await pool.execute(
    "SELECT device_id, media_key, episode_key, status, updated_at FROM device_cache_items WHERE user_id = ? AND updated_at > ?",
    [userId, since],
  );
  return {
    serverTime: nowSeconds(),
    favorites: favoriteRows.map((row) => ({
      userId,
      mediaKey: row.media_key,
      mediaSnapshot: snapshotText(row.media_snapshot),
      createdAt: Number(row.created_at),
      updatedAt: Number(row.updated_at),
    })),
    progress: progressRows.map((row) => ({
      userId,
      mediaKey: row.media_key,
      episodeKey: row.episode_key,
      mediaSnapshot: snapshotText(row.media_snapshot),
      episodeName: row.episode_name,
      sourceKey: row.source_key,
      positionMs: Number(row.position_ms),
      durationMs: Number(row.duration_ms),
      completed: Boolean(row.completed),
      updatedAt: Number(row.updated_at),
    })),
    deviceCache: cacheRows.map((row) => ({
      userId,
      deviceId: row.device_id,
      mediaKey: row.media_key,
      episodeKey: row.episode_key,
      status: row.status,
      updatedAt: Number(row.updated_at),
    })),
  };
}

app.get("/health", async (_req, res, next) => {
  try {
    await pool.query("SELECT 1");
    res.json({ ok: true });
  } catch (error) {
    next(error);
  }
});

app.post("/api/auth/request-code", async (req, res, next) => {
  try {
    const email = normalizeEmail(req.body?.email);
    const purpose = safeText(req.body?.purpose, 32);
    if (!isValidEmail(email) || !["register", "change-email", "reset-password"].includes(purpose)) {
      return res.status(400).json({ error: "invalid email or purpose" });
    }
    const [rows] = await pool.execute("SELECT id FROM users WHERE email = ? LIMIT 1", [email]);
    if (purpose === "register" && rows.length > 0) {
      return res.status(409).json({ error: "email already registered" });
    }
    if (purpose === "change-email") {
      const user = await currentUser(req);
      if (!user) return res.status(401).json({ error: "authentication required" });
      if (rows.length > 0) return res.status(409).json({ error: "email already registered" });
    }
    if (purpose === "reset-password" && rows.length === 0) return res.json({ ok: true });
    await issueCode(email, purpose);
    res.json({ ok: true });
  } catch (error) {
    next(error);
  }
});

app.post("/api/auth/register", async (req, res, next) => {
  try {
    const email = normalizeEmail(req.body?.email);
    const password = String(req.body?.password || "");
    const code = safeText(req.body?.code, 12);
    const nickname = normalizeNickname(req.body?.nickname) || `漫友_${String(Date.now()).slice(-4)}`;
    if (!isValidEmail(email)) return res.status(400).json({ error: "invalid email" });
    if (!isStrongPassword(password)) return res.status(400).json({ error: "password is too weak" });
    if (!code) return res.status(400).json({ error: "email verification code required" });

    const user = await transaction(async (connection) => {
      const [existing] = await connection.execute("SELECT id FROM users WHERE email = ? LIMIT 1 FOR UPDATE", [email]);
      if (existing.length > 0) {
        const conflict = new Error("email already registered");
        conflict.status = 409;
        throw conflict;
      }
      if (!(await consumeCode(email, "register", code, connection))) {
        const invalid = new Error("invalid or expired email verification code");
        invalid.status = 400;
        throw invalid;
      }
      const now = nowSeconds();
      const row = {
        id: randomUUID(),
        email,
        nickname,
        avatar_url: "",
      };
      await connection.execute(
        "INSERT INTO users (id, email, password_hash, nickname, avatar_url, created_at, updated_at) VALUES (?, ?, ?, ?, '', ?, ?)",
        [row.id, email, await hashPassword(password), nickname, now, now],
      );
      return row;
    });
    const token = await createSession(user.id);
    res.status(201).json({ user: publicUser(user), token });
  } catch (error) {
    next(error);
  }
});

app.post("/api/auth/login", async (req, res, next) => {
  try {
    const email = normalizeEmail(req.body?.email);
    const password = String(req.body?.password || "");
    const [rows] = await pool.execute(
      "SELECT id, email, password_hash, nickname, avatar_url FROM users WHERE email = ? LIMIT 1",
      [email],
    );
    const user = rows[0];
    if (!user || !(await verifyPassword(password, user.password_hash))) {
      return res.status(401).json({ error: "invalid email or password" });
    }
    const token = await createSession(user.id);
    res.json({ user: publicUser(user), token });
  } catch (error) {
    next(error);
  }
});

app.get("/api/auth/me", requireUser, (req, res) => {
  res.json({ user: publicUser(req.accountUser) });
});

app.post("/api/auth/logout", async (req, res, next) => {
  try {
    await revokeSession(req);
    res.json({ ok: true });
  } catch (error) {
    next(error);
  }
});

app.post("/api/auth/change-email", requireUser, async (req, res, next) => {
  try {
    const email = normalizeEmail(req.body?.email);
    const code = safeText(req.body?.code, 12);
    if (!isValidEmail(email) || !code) return res.status(400).json({ error: "email and code required" });
    await transaction(async (connection) => {
      const [existing] = await connection.execute("SELECT id FROM users WHERE email = ? LIMIT 1 FOR UPDATE", [email]);
      if (existing.length > 0) {
        const conflict = new Error("email already registered");
        conflict.status = 409;
        throw conflict;
      }
      if (!(await consumeCode(email, "change-email", code, connection))) {
        const invalid = new Error("invalid or expired email verification code");
        invalid.status = 400;
        throw invalid;
      }
      await connection.execute("UPDATE users SET email = ?, updated_at = ? WHERE id = ?", [
        email,
        nowSeconds(),
        req.accountUser.id,
      ]);
    });
    res.json({ user: publicUser({ ...req.accountUser, email }) });
  } catch (error) {
    next(error);
  }
});

app.post("/api/auth/nickname", requireUser, async (req, res, next) => {
  try {
    const nickname = normalizeNickname(req.body?.nickname);
    if (!nickname) return res.status(400).json({ error: "nickname required" });
    const now = nowSeconds();
    await pool.execute("UPDATE users SET nickname = ?, updated_at = ? WHERE id = ?", [
      nickname,
      now,
      req.accountUser.id,
    ]);
    res.json({ user: publicUser({ ...req.accountUser, nickname }) });
  } catch (error) {
    next(error);
  }
});

app.post("/api/auth/reset-password", async (req, res, next) => {
  try {
    const email = normalizeEmail(req.body?.email);
    const code = safeText(req.body?.code, 12);
    const password = String(req.body?.password || "");
    const confirmPassword = String(req.body?.confirmPassword || "");
    if (!isValidEmail(email) || !code) return res.status(400).json({ error: "email and code required" });
    if (!isStrongPassword(password) || password !== confirmPassword) {
      return res.status(400).json({ error: "password is invalid or confirmation does not match" });
    }
    await transaction(async (connection) => {
      const [rows] = await connection.execute("SELECT id FROM users WHERE email = ? LIMIT 1 FOR UPDATE", [email]);
      const user = rows[0];
      if (!user || !(await consumeCode(email, "reset-password", code, connection))) {
        const invalid = new Error("invalid or expired reset request");
        invalid.status = 400;
        throw invalid;
      }
      await connection.execute("UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?", [
        await hashPassword(password),
        nowSeconds(),
        user.id,
      ]);
      await connection.execute("DELETE FROM auth_sessions WHERE user_id = ?", [user.id]);
    });
    res.json({ ok: true });
  } catch (error) {
    next(error);
  }
});

app.post("/api/auth/avatar", requireUser, upload.single("file"), async (req, res, next) => {
  try {
    if (!req.file) return res.status(400).json({ error: "avatar file required" });
    const allowed = {
      "image/jpeg": "jpg",
      "image/png": "png",
      "image/webp": "webp",
      "image/gif": "gif",
    };
    const extension = allowed[req.file.mimetype];
    if (!extension) return res.status(400).json({ error: "unsupported avatar format" });
    const objectKey = `avatars/${req.accountUser.id}/${randomUUID()}.${extension}`;
    await oss.put(objectKey, req.file.buffer, {
      headers: { "Content-Type": req.file.mimetype },
    });
    const avatarUrl = `${config.oss.publicBaseUrl}/${objectKey}`;
    await pool.execute("UPDATE users SET avatar_url = ?, updated_at = ? WHERE id = ?", [
      avatarUrl,
      nowSeconds(),
      req.accountUser.id,
    ]);
    res.json({ user: publicUser({ ...req.accountUser, avatar_url: avatarUrl }) });
  } catch (error) {
    next(error);
  }
});

app.get("/api/sync", requireUser, async (req, res, next) => {
  try {
    const payload = await syncPayload(req.accountUser.id, safeInt(req.query.since, 9_999_999_999));
    res.json({ user: publicUser(req.accountUser), ...payload });
  } catch (error) {
    next(error);
  }
});

app.post("/api/sync", requireUser, async (req, res, next) => {
  try {
    const favorites = Array.isArray(req.body?.favorites) ? req.body.favorites.slice(0, 500) : [];
    const progress = Array.isArray(req.body?.progress) ? req.body.progress.slice(0, 500) : [];
    const deviceCache = Array.isArray(req.body?.deviceCache) ? req.body.deviceCache.slice(0, 500) : [];
    const now = nowSeconds();
    await transaction(async (connection) => {
      if (req.body?.replaceFavorites === true) {
        await connection.execute("DELETE FROM favorites WHERE user_id = ?", [req.accountUser.id]);
      }
      if (req.body?.replaceProgress === true) {
        await connection.execute("DELETE FROM watch_progress WHERE user_id = ?", [req.accountUser.id]);
      }
      for (const item of favorites) {
        const media = mediaKey(item?.mediaKey);
        if (!media) continue;
        await connection.execute(
          `INSERT INTO favorites (user_id, media_key, media_snapshot, created_at, updated_at)
           VALUES (?, ?, ?, ?, ?)
           ON DUPLICATE KEY UPDATE media_snapshot = VALUES(media_snapshot), updated_at = VALUES(updated_at)`,
          [req.accountUser.id, media, snapshot(item?.mediaSnapshot), now, now],
        );
      }
      for (const item of progress) {
        const media = mediaKey(item?.mediaKey);
        const episode = mediaKey(item?.episodeKey);
        if (!media || !episode) continue;
        await connection.execute(
          `INSERT INTO watch_progress
             (user_id, media_key, episode_key, media_snapshot, episode_name, source_key,
              position_ms, duration_ms, completed, updated_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
           ON DUPLICATE KEY UPDATE
             media_snapshot = VALUES(media_snapshot), episode_name = VALUES(episode_name),
             source_key = VALUES(source_key), position_ms = VALUES(position_ms),
             duration_ms = VALUES(duration_ms), completed = VALUES(completed),
             updated_at = VALUES(updated_at)`,
          [
            req.accountUser.id,
            media,
            episode,
            snapshot(item?.mediaSnapshot),
            safeText(item?.episodeName),
            safeText(item?.sourceKey, 120),
            safeInt(item?.positionMs, 86_400_000),
            safeInt(item?.durationMs, 86_400_000),
            item?.completed ? 1 : 0,
            now,
          ],
        );
      }
      for (const item of deviceCache) {
        const device = safeText(item?.deviceId, 120);
        const media = mediaKey(item?.mediaKey);
        const episode = mediaKey(item?.episodeKey);
        if (!device || !media || !episode) continue;
        await connection.execute(
          `INSERT INTO device_cache_items
             (user_id, device_id, media_key, episode_key, status, updated_at)
           VALUES (?, ?, ?, ?, ?, ?)
           ON DUPLICATE KEY UPDATE status = VALUES(status), updated_at = VALUES(updated_at)`,
          [req.accountUser.id, device, media, episode, safeText(item?.status, 32) || "downloaded", now],
        );
      }
    });
    const payload = await syncPayload(req.accountUser.id);
    res.json({ user: publicUser(req.accountUser), ...payload });
  } catch (error) {
    next(error);
  }
});

app.get("/api/comments", async (req, res, next) => {
  try {
    const media = mediaKey(req.query.media);
    if (!media) return res.status(400).json({ error: "missing media" });
    const [rows] = await pool.execute(
      `SELECT c.id, u.nickname AS nick, c.text, c.created_at
         FROM comments c JOIN users u ON u.id = c.user_id
        WHERE c.media_key = ? ORDER BY c.created_at DESC LIMIT 500`,
      [media],
    );
    res.json({
      comments: rows.reverse().map((row) => ({
        id: row.id,
        nick: row.nick,
        text: row.text,
        ts: Number(row.created_at) * 1000,
      })),
    });
  } catch (error) {
    next(error);
  }
});

app.post("/api/comments", (req, res, next) => {
  // TEMP: 评论发送关闭；GET /api/comments 读取仍保持可用。
  if (!config.commentsPostingEnabled) {
    return res.status(503).json({ error: "comment posting is temporarily disabled" });
  }
  requireUser(req, res, next);
}, async (req, res, next) => {
  try {
    const media = mediaKey(req.body?.media);
    const text = safeText(req.body?.text, 200);
    if (!media || !text) return res.status(400).json({ error: "missing media or text" });
    await pool.execute(
      "INSERT INTO comments (id, user_id, media_key, episode_key, text, created_at) VALUES (?, ?, ?, '', ?, ?)",
      [randomUUID(), req.accountUser.id, media, text, nowSeconds()],
    );
    const [rows] = await pool.execute(
      `SELECT c.id, u.nickname AS nick, c.text, c.created_at
         FROM comments c JOIN users u ON u.id = c.user_id
        WHERE c.media_key = ? ORDER BY c.created_at DESC LIMIT 500`,
      [media],
    );
    res.json({
      comments: rows.reverse().map((row) => ({
        id: row.id,
        nick: row.nick,
        text: row.text,
        ts: Number(row.created_at) * 1000,
      })),
    });
  } catch (error) {
    next(error);
  }
});

app.use((_req, res) => {
  res.status(404).json({ error: "api route not found" });
});

app.use((error, _req, res, _next) => {
  console.error(error);
  const status = Number(error?.status) || (error?.code === "ER_DUP_ENTRY" ? 409 : 500);
  const message = status >= 500 ? "internal server error" : error.message;
  res.status(status).json({ error: message });
});

const server = app.listen(config.port, "0.0.0.0", () => {
  console.log(`Lanerc Aliyun API listening on port ${config.port}`);
});

async function shutdown() {
  server.close();
  await pool.end();
  process.exit(0);
}

process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);
