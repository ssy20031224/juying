import { and, desc, eq, gt, isNull } from "drizzle-orm";
import { getDb } from "../../db";
import { verificationCodes } from "../../db/schema";

export type VerificationPurpose = "register" | "change-email" | "reset-password";

const CODE_TTL_SECONDS = 10 * 60;
const RESEND_INTERVAL_SECONDS = 60;

function codeHash(email: string, purpose: VerificationPurpose, code: string): Promise<string> {
  return crypto.subtle
    .digest(
      "SHA-256",
      new TextEncoder().encode(`${email}|${purpose}|${code}|${process.env.AUTH_CODE_PEPPER || "lanerc-code"}`),
    )
    .then((value) => {
      let binary = "";
      new Uint8Array(value).forEach((byte) => {
        binary += String.fromCharCode(byte);
      });
      return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
    });
}

function makeCode(): string {
  const bytes = crypto.getRandomValues(new Uint32Array(1));
  return String(100000 + (bytes[0] % 900000));
}

async function sendWithResend(to: string, code: string, purpose: VerificationPurpose): Promise<void> {
  const apiKey = (process.env.RESEND_API_KEY || "").trim();
  const from = (process.env.EMAIL_FROM || "").trim();
  if (!apiKey || !from) {
    throw new Error("email provider is not configured; set RESEND_API_KEY and EMAIL_FROM");
  }

  const purposeText =
    purpose === "register" ? "注册账号" : purpose === "change-email" ? "修改邮箱" : "重置密码";
  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from,
      to: [to],
      subject: `聚映账号${purposeText}验证码`,
      html: `<div style="font-family:Arial,sans-serif"><h2>聚映账号安全验证</h2><p>本次操作：${purposeText}</p><p style="font-size:28px;letter-spacing:8px;font-weight:bold">${code}</p><p>验证码 10 分钟内有效。如果不是本人操作，请忽略此邮件。</p></div>`,
    }),
  });
  if (!response.ok) throw new Error(`email provider rejected request (${response.status})`);
}

function percentEncode(value: string): string {
  return encodeURIComponent(value).replace(/\!/g, "%21").replace(/'/g, "%27").replace(/\(/g, "%28").replace(/\)/g, "%29").replace(/\*/g, "%2A");
}

async function sendWithAliyun(to: string, code: string, purpose: VerificationPurpose): Promise<void> {
  const accessKeyId = (process.env.ALIYUN_DM_ACCESS_KEY_ID || "").trim();
  const accessKeySecret = (process.env.ALIYUN_DM_ACCESS_KEY_SECRET || "").trim();
  const accountName = (process.env.ALIYUN_DM_ACCOUNT_NAME || "").trim();
  const fromAlias = (process.env.ALIYUN_DM_FROM_ALIAS || "聚映").trim();
  if (!accessKeyId || !accessKeySecret || !accountName) {
    throw new Error("Aliyun DirectMail is not configured");
  }
  const purposeText =
    purpose === "register" ? "注册账号" : purpose === "change-email" ? "修改邮箱" : "重置密码";
  const params: Record<string, string> = {
    Action: "SingleSendMail",
    Version: "2015-11-23",
    Format: "JSON",
    AccessKeyId: accessKeyId,
    SignatureMethod: "HMAC-SHA1",
    SignatureVersion: "1.0",
    SignatureNonce: crypto.randomUUID(),
    Timestamp: new Date().toISOString().replace(/\.\d{3}Z$/, "Z"),
    AccountName: accountName,
    AddressType: "1",
    ReplyToAddress: "true",
    ToAddress: to,
    FromAlias: fromAlias,
    Subject: `聚映账号${purposeText}验证码`,
    HtmlBody: `<div><h2>聚映账号安全验证</h2><p>本次操作：${purposeText}</p><p style="font-size:28px;letter-spacing:8px;font-weight:bold">${code}</p><p>验证码 10 分钟内有效。如果不是本人操作，请忽略此邮件。</p></div>`,
  };
  const canonical = Object.keys(params)
    .sort()
    .map((key) => `${percentEncode(key)}=${percentEncode(params[key])}`)
    .join("&");
  const stringToSign = `POST&%2F&${percentEncode(canonical)}`;
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(`${accessKeySecret}&`),
    { name: "HMAC", hash: "SHA-1" },
    false,
    ["sign"],
  );
  const signed = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(stringToSign));
  let binary = "";
  new Uint8Array(signed).forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  const body = `${canonical}&Signature=${percentEncode(btoa(binary))}`;
  const response = await fetch("https://dm.aliyuncs.com/", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });
  if (!response.ok) throw new Error(`Aliyun DirectMail rejected request (${response.status})`);
  const result = (await response.json()) as { Code?: string; Message?: string };
  if (result.Code) throw new Error(result.Message || result.Code);
}

async function sendVerificationEmail(
  to: string,
  code: string,
  purpose: VerificationPurpose,
): Promise<void> {
  if ((process.env.ALIYUN_DM_ACCESS_KEY_ID || "").trim()) {
    return sendWithAliyun(to, code, purpose);
  }
  return sendWithResend(to, code, purpose);
}

export async function issueVerificationCode(
  email: string,
  purpose: VerificationPurpose,
): Promise<void> {
  const normalizedEmail = email.trim().toLowerCase();
  const now = Math.floor(Date.now() / 1000);
  const db = await getDb();
  const recent = await db
    .select({ id: verificationCodes.id })
    .from(verificationCodes)
    .where(
      and(
        eq(verificationCodes.email, normalizedEmail),
        eq(verificationCodes.purpose, purpose),
        gt(verificationCodes.createdAt, now - RESEND_INTERVAL_SECONDS),
      ),
    )
    .limit(1);
  if (recent.length > 0) throw new Error("please wait before requesting another code");

  const code = makeCode();
  await sendVerificationEmail(normalizedEmail, code, purpose);
  await db.insert(verificationCodes).values({
    id: crypto.randomUUID(),
    email: normalizedEmail,
    purpose,
    codeHash: await codeHash(normalizedEmail, purpose, code),
    expiresAt: now + CODE_TTL_SECONDS,
    createdAt: now,
  });
}

export async function consumeVerificationCode(
  email: string,
  purpose: VerificationPurpose,
  code: string,
): Promise<boolean> {
  const normalizedEmail = email.trim().toLowerCase();
  const now = Math.floor(Date.now() / 1000);
  const db = await getDb();
  const rows = await db
    .select()
    .from(verificationCodes)
    .where(
      and(
        eq(verificationCodes.email, normalizedEmail),
        eq(verificationCodes.purpose, purpose),
        gt(verificationCodes.expiresAt, now),
        isNull(verificationCodes.consumedAt),
      ),
    )
    .orderBy(desc(verificationCodes.createdAt))
    .limit(1);
  const row = rows[0];
  if (!row || (await codeHash(normalizedEmail, purpose, code)) !== row.codeHash) return false;
  await db
    .update(verificationCodes)
    .set({ consumedAt: now })
    .where(eq(verificationCodes.id, row.id));
  return true;
}
