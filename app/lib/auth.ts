import { and, eq, gt } from "drizzle-orm";
import { getDb } from "../../db";
import { authSessions, users } from "../../db/schema";

const SESSION_COOKIE = "lanerc_session";
const SESSION_TTL_SECONDS = 60 * 60 * 24 * 30;
const PASSWORD_ITERATIONS = 120_000;

export type AuthUser = {
  id: string;
  email: string;
  nickname: string;
  avatarUrl: string;
};

function bytesToBase64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function base64UrlToBytes(value: string): Uint8Array {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4);
  const binary = atob(padded);
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

async function digestSha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return bytesToBase64Url(new Uint8Array(digest));
}

async function derivePassword(password: string, salt: Uint8Array): Promise<Uint8Array> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(password),
    "PBKDF2",
    false,
    ["deriveBits"],
  );
  const bits = await crypto.subtle.deriveBits(
    {
      name: "PBKDF2",
      salt,
      iterations: PASSWORD_ITERATIONS,
      hash: "SHA-256",
    },
    key,
    256,
  );
  return new Uint8Array(bits);
}

export async function hashPassword(password: string): Promise<string> {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const derived = await derivePassword(password, salt);
  return `pbkdf2$sha256$${PASSWORD_ITERATIONS}$${bytesToBase64Url(salt)}$${bytesToBase64Url(derived)}`;
}

export async function verifyPassword(password: string, encoded: string): Promise<boolean> {
  const [algorithm, hash, iterationsText, saltText, expectedText] = encoded.split("$");
  if (algorithm !== "pbkdf2" || hash !== "sha256") return false;
  const iterations = Number(iterationsText);
  if (!Number.isInteger(iterations) || iterations < 10_000 || !saltText || !expectedText) return false;

  const actual = await crypto.subtle.deriveBits(
    {
      name: "PBKDF2",
      salt: base64UrlToBytes(saltText),
      iterations,
      hash: "SHA-256",
    },
    await crypto.subtle.importKey(
      "raw",
      new TextEncoder().encode(password),
      "PBKDF2",
      false,
      ["deriveBits"],
    ),
    256,
  );
  const actualBytes = new Uint8Array(actual);
  const expectedBytes = base64UrlToBytes(expectedText);
  if (actualBytes.length !== expectedBytes.length) return false;
  let difference = 0;
  for (let index = 0; index < actualBytes.length; index += 1) {
    difference |= actualBytes[index] ^ expectedBytes[index];
  }
  return difference === 0;
}

export function normalizeEmail(value: string): string {
  return value.trim().toLowerCase();
}

export function normalizeNickname(value: string): string {
  return value.trim().slice(0, 24);
}

export function isStrongPassword(value: string): boolean {
  if (value.length < 8 || value.length > 128) return false;
  let types = 0;
  if (/[A-Z]/.test(value)) types += 1;
  if (/[a-z]/.test(value)) types += 1;
  if (/[0-9]/.test(value)) types += 1;
  if (/[^A-Za-z0-9]/.test(value)) types += 1;
  return types >= 3;
}

function parseCookies(header: string | null): Record<string, string> {
  if (!header) return {};
  return Object.fromEntries(
    header.split(";").flatMap((part) => {
      const separator = part.indexOf("=");
      if (separator < 0) return [];
      const key = part.slice(0, separator).trim();
      const value = part.slice(separator + 1).trim();
      return key ? [[key, decodeURIComponent(value)]] : [];
    }),
  );
}

function tokenFromRequest(request: Request): string | null {
  const authorization = request.headers.get("authorization") || "";
  if (authorization.toLowerCase().startsWith("bearer ")) {
    return authorization.slice(7).trim() || null;
  }
  return parseCookies(request.headers.get("cookie"))[SESSION_COOKIE] || null;
}

export async function createSession(userId: string): Promise<string> {
  const token = bytesToBase64Url(crypto.getRandomValues(new Uint8Array(32)));
  const tokenHash = await digestSha256(token);
  const now = Math.floor(Date.now() / 1000);
  const db = await getDb();
  await db.insert(authSessions).values({
    id: crypto.randomUUID(),
    userId,
    tokenHash,
    expiresAt: now + SESSION_TTL_SECONDS,
    createdAt: now,
  });
  return token;
}

export function sessionCookie(token: string): string {
  return [
    `${SESSION_COOKIE}=${encodeURIComponent(token)}`,
    "Path=/",
    "HttpOnly",
    "Secure",
    "SameSite=Lax",
    `Max-Age=${SESSION_TTL_SECONDS}`,
  ].join("; ");
}

export function clearSessionCookie(): string {
  return `${SESSION_COOKIE}=; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=0`;
}

export async function getCurrentUser(request: Request): Promise<AuthUser | null> {
  const token = tokenFromRequest(request);
  if (!token) return null;

  const tokenHash = await digestSha256(token);
  const now = Math.floor(Date.now() / 1000);
  const db = await getDb();
  const rows = await db
    .select({
      id: users.id,
      email: users.email,
      nickname: users.nickname,
      avatarUrl: users.avatarUrl,
    })
    .from(authSessions)
    .innerJoin(users, eq(authSessions.userId, users.id))
    .where(and(eq(authSessions.tokenHash, tokenHash), gt(authSessions.expiresAt, now)))
    .limit(1);
  return rows[0] ?? null;
}

export async function revokeCurrentSession(request: Request): Promise<void> {
  const token = tokenFromRequest(request);
  if (!token) return;
  const db = await getDb();
  await db
    .delete(authSessions)
    .where(eq(authSessions.tokenHash, await digestSha256(token)));
}

export function publicUser(user: AuthUser) {
  return {
    id: user.id,
    email: user.email,
    nickname: user.nickname,
    avatarUrl: user.avatarUrl,
  };
}
