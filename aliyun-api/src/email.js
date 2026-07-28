import { createHash, randomInt, randomUUID } from "node:crypto";
import PopCore from "@alicloud/pop-core";
import { config } from "./config.js";
import { pool } from "./db.js";
import { nowSeconds } from "./auth.js";

const client = new PopCore({
  accessKeyId: config.directMail.accessKeyId,
  accessKeySecret: config.directMail.accessKeySecret,
  endpoint: "https://dm.aliyuncs.com",
  apiVersion: "2015-11-23",
});

function hashCode(email, purpose, code) {
  return createHash("sha256")
    .update(`${email}|${purpose}|${code}|${config.authCodePepper}`)
    .digest("base64url");
}

export async function issueCode(email, purpose) {
  const now = nowSeconds();
  const [recent] = await pool.execute(
    "SELECT id FROM verification_codes WHERE email = ? AND purpose = ? AND created_at > ? LIMIT 1",
    [email, purpose, now - 60],
  );
  if (recent.length > 0) throw new Error("请稍后再获取验证码");

  const code = String(randomInt(100000, 1000000));
  const purposeText = purpose === "register" ? "注册账号" : purpose === "change-email" ? "修改邮箱" : "重置密码";
  await client.request(
    "SingleSendMail",
    {
      AccountName: config.directMail.accountName,
      AddressType: 1,
      ReplyToAddress: true,
      ToAddress: email,
      FromAlias: config.directMail.fromAlias,
      Subject: `聚映账号${purposeText}验证码`,
      HtmlBody: `<h2>聚映账号安全验证</h2><p>本次操作：${purposeText}</p><p style="font-size:28px;letter-spacing:8px;font-weight:bold">${code}</p><p>验证码 10 分钟内有效。</p>`,
    },
    { method: "POST" },
  );
  await pool.execute(
    "INSERT INTO verification_codes (id, email, purpose, code_hash, expires_at, consumed_at, created_at) VALUES (?, ?, ?, ?, ?, NULL, ?)",
    [randomUUID(), email, purpose, hashCode(email, purpose, code), now + 600, now],
  );
}

export async function consumeCode(email, purpose, code, connection = pool) {
  const now = nowSeconds();
  const [rows] = await connection.execute(
    `SELECT id, code_hash FROM verification_codes
      WHERE email = ? AND purpose = ? AND expires_at > ? AND consumed_at IS NULL
      ORDER BY created_at DESC LIMIT 1`,
    [email, purpose, now],
  );
  const row = rows[0];
  if (!row || row.code_hash !== hashCode(email, purpose, code)) return false;
  await connection.execute("UPDATE verification_codes SET consumed_at = ? WHERE id = ?", [now, row.id]);
  return true;
}
