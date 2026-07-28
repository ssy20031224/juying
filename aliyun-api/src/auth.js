import {
  createHash,
  randomBytes,
  randomUUID,
  pbkdf2 as pbkdf2Callback,
  timingSafeEqual,
} from "node:crypto";
import { promisify } from "node:util";
import { pool } from "./db.js";

const pbkdf2 = promisify(pbkdf2Callback);
const PASSWORD_ITERATIONS = 120_000;
const SESSION_TTL_SECONDS = 30 * 24 * 60 * 60;

export function nowSeconds() {
  return Math.floor(Date.now() / 1000);
}

export function normalizeEmail(value) {
  return String(value || "").trim().toLowerCase();
}

export function normalizeNickname(value) {
  return String(value || "").trim().slice(0, 24);
}

export function isValidEmail(value) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

export function isStrongPassword(value) {
  if (typeof value !== "string" || value.length < 8 || value.length > 128) return false;
  return [/[A-Z]/, /[a-z]/, /[0-9]/, /[^A-Za-z0-9]/].filter((rule) => rule.test(value)).length >= 3;
}

export async function hashPassword(password) {
  const salt = randomBytes(16);
  const derived = await pbkdf2(password, salt, PASSWORD_ITERATIONS, 32, "sha256");
  return `pbkdf2$sha256$${PASSWORD_ITERATIONS}$${salt.toString("base64url")}$${derived.toString("base64url")}`;
}

export async function verifyPassword(password, encoded) {
  const [algorithm, hash, iterationsText, saltText, expectedText] = String(encoded || "").split("$");
  if (algorithm !== "pbkdf2" || hash !== "sha256") return false;
  const iterations = Number(iterationsText);
  if (!Number.isInteger(iterations) || iterations < 10_000) return false;
  const expected = Buffer.from(expectedText || "", "base64url");
  const actual = await pbkdf2(password, Buffer.from(saltText || "", "base64url"), iterations, expected.length, "sha256");
  return expected.length > 0 && timingSafeEqual(actual, expected);
}

export function tokenHash(token) {
  return createHash("sha256").update(token).digest("base64url");
}

export async function createSession(userId) {
  const token = randomBytes(32).toString("base64url");
  const now = nowSeconds();
  await pool.execute(
    "INSERT INTO auth_sessions (id, user_id, token_hash, expires_at, created_at) VALUES (?, ?, ?, ?, ?)",
    [randomUUID(), userId, tokenHash(token), now + SESSION_TTL_SECONDS, now],
  );
  return token;
}

export function publicUser(user) {
  return {
    id: user.id,
    email: user.email,
    nickname: user.nickname,
    avatarUrl: user.avatar_url || "",
  };
}

export async function currentUser(req) {
  const authorization = String(req.headers.authorization || "");
  if (!authorization.toLowerCase().startsWith("bearer ")) return null;
  const token = authorization.slice(7).trim();
  if (!token) return null;
  const [rows] = await pool.execute(
    `SELECT u.id, u.email, u.nickname, u.avatar_url
       FROM auth_sessions s
       JOIN users u ON u.id = s.user_id
      WHERE s.token_hash = ? AND s.expires_at > ?
      LIMIT 1`,
    [tokenHash(token), nowSeconds()],
  );
  return rows[0] || null;
}

export async function requireUser(req, res, next) {
  try {
    const user = await currentUser(req);
    if (!user) return res.status(401).json({ error: "authentication required" });
    req.accountUser = user;
    next();
  } catch (error) {
    next(error);
  }
}

export async function revokeSession(req) {
  const authorization = String(req.headers.authorization || "");
  if (!authorization.toLowerCase().startsWith("bearer ")) return;
  const token = authorization.slice(7).trim();
  if (token) await pool.execute("DELETE FROM auth_sessions WHERE token_hash = ?", [tokenHash(token)]);
}
