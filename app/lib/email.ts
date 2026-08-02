import { and, desc, eq, gt, isNull } from "drizzle-orm";
import { getDb } from "../../db";
import { verificationCodes } from "../../db/schema";

export type VerificationPurpose = "register" | "change-email" | "reset-password";

const CODE_TTL_SECONDS = 10 * 60;
const RESEND_INTERVAL_SECONDS = 60;

export class VerificationRateLimitError extends Error {
  constructor(readonly retryAfterSeconds: number) {
    super("verification code requested too frequently");
    this.name = "VerificationRateLimitError";
  }
}

const PURPOSE_COPY: Record<VerificationPurpose, { action: string; title: string; hint: string }> = {
  register: {
    action: "注册聚映账号",
    title: "欢迎加入聚映",
    hint: "完成验证后即可创建账号，并在不同设备间同步追番、播放进度与缓存索引。",
  },
  "change-email": {
    action: "修改账号邮箱",
    title: "确认新的账号邮箱",
    hint: "验证成功后，新邮箱将用于登录、找回密码和接收账号安全通知。",
  },
  "reset-password": {
    action: "重置账号密码",
    title: "确认密码重置",
    hint: "验证成功后即可设置新密码。修改完成后，其他设备上的登录会话将失效。",
  },
};

function verificationMail(code: string, purpose: VerificationPurpose) {
  const copy = PURPOSE_COPY[purpose];
  const subject = `【聚映】${copy.action}验证码：${code}`;
  const html = `<!doctype html>
<html lang="zh-CN">
  <body style="margin:0;background:#f4f7fb;color:#172033;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',Arial,sans-serif;">
    <div style="display:none;max-height:0;overflow:hidden;opacity:0;">${copy.action}验证码为 ${code}，10 分钟内有效。</div>
    <div style="max-width:560px;margin:0 auto;padding:32px 16px;">
      <div style="overflow:hidden;border:1px solid #e7edf5;border-radius:22px;background:#ffffff;box-shadow:0 12px 34px rgba(33,64,104,.08);">
        <div style="padding:25px 30px;background:linear-gradient(135deg,#21b9d2,#6687f5);color:#ffffff;">
          <div style="font-size:13px;letter-spacing:3px;opacity:.86;">JUYING · 聚映</div>
          <div style="margin-top:10px;font-size:25px;font-weight:700;">${copy.title}</div>
        </div>
        <div style="padding:30px;">
          <p style="margin:0 0 12px;font-size:15px;line-height:1.8;">你正在进行：<strong>${copy.action}</strong></p>
          <p style="margin:0 0 24px;color:#68758a;font-size:14px;line-height:1.8;">${copy.hint}</p>
          <div style="padding:20px;text-align:center;border:1px solid #dce8f6;border-radius:16px;background:#f6fbff;">
            <div style="margin-bottom:9px;color:#7c8ba1;font-size:12px;letter-spacing:2px;">本次验证码</div>
            <div style="color:#168fac;font-size:34px;font-weight:800;letter-spacing:10px;">${code}</div>
          </div>
          <p style="margin:22px 0 0;color:#68758a;font-size:13px;line-height:1.8;">验证码将在 <strong>10 分钟</strong>后失效，请勿转发或告知他人。聚映工作人员不会向你索要验证码或密码。</p>
          <p style="margin:10px 0 0;color:#9aa5b5;font-size:12px;line-height:1.7;">若并非你本人操作，请忽略本邮件；账号信息不会因此发生变化。</p>
        </div>
      </div>
      <p style="margin:18px 0 0;text-align:center;color:#9aa5b5;font-size:12px;">此邮件由系统自动发送，请勿直接回复。</p>
    </div>
  </body>
</html>`;
  return { subject, html };
}

function codeHash(email: string, purpose: VerificationPurpose, code: string): Promise<string> {
  const pepper = (process.env.AUTH_CODE_PEPPER || "").trim();
  if (pepper.length < 32) {
    throw new Error("AUTH_CODE_PEPPER must be configured with at least 32 characters");
  }
  return crypto.subtle
    .digest(
      "SHA-256",
      new TextEncoder().encode(`${email}|${purpose}|${code}|${pepper}`),
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

  const mail = verificationMail(code, purpose);
  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from,
      to: [to],
      subject: mail.subject,
      html: mail.html,
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
  const mail = verificationMail(code, purpose);
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
    Subject: mail.subject,
    HtmlBody: mail.html,
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
  // Validate the server-side secret before sending mail, so a deployment with
  // an incomplete environment never sends a code it cannot safely verify.
  await codeHash(normalizedEmail, purpose, "000000");
  const now = Math.floor(Date.now() / 1000);
  const db = await getDb();
  const recent = await db
    .select({ id: verificationCodes.id, createdAt: verificationCodes.createdAt })
    .from(verificationCodes)
    .where(
      and(
        eq(verificationCodes.email, normalizedEmail),
        eq(verificationCodes.purpose, purpose),
        gt(verificationCodes.createdAt, now - RESEND_INTERVAL_SECONDS),
      ),
    )
    .limit(1);
  if (recent.length > 0) {
    const elapsed = Math.max(0, now - recent[0].createdAt);
    throw new VerificationRateLimitError(Math.max(1, RESEND_INTERVAL_SECONDS - elapsed));
  }

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
