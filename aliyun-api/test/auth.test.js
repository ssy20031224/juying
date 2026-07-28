import assert from "node:assert/strict";
import test, { after } from "node:test";

Object.assign(process.env, {
  MYSQL_HOST: "127.0.0.1",
  MYSQL_DATABASE: "lanerc_test",
  MYSQL_USER: "test",
  MYSQL_PASSWORD: "test",
  AUTH_CODE_PEPPER: "test-pepper",
  ALIYUN_DM_ACCESS_KEY_ID: "test",
  ALIYUN_DM_ACCESS_KEY_SECRET: "test",
  ALIYUN_DM_ACCOUNT_NAME: "test@example.com",
  ALIYUN_OSS_ACCESS_KEY_ID: "test",
  ALIYUN_OSS_ACCESS_KEY_SECRET: "test",
  ALIYUN_OSS_REGION: "oss-cn-hangzhou",
  ALIYUN_OSS_BUCKET: "test",
  ALIYUN_OSS_PUBLIC_BASE_URL: "https://example.com",
});

const auth = await import("../src/auth.js");
const { pool } = await import("../src/db.js");

after(async () => {
  await pool.end();
});

test("password hashes verify without storing plaintext", async () => {
  const encoded = await auth.hashPassword("StrongPass123!");
  assert.match(encoded, /^pbkdf2\$sha256\$/);
  assert.equal(await auth.verifyPassword("StrongPass123!", encoded), true);
  assert.equal(await auth.verifyPassword("WrongPass123!", encoded), false);
});

test("account input validation is normalized", () => {
  assert.equal(auth.normalizeEmail(" User@Example.COM "), "user@example.com");
  assert.equal(auth.isValidEmail("user@example.com"), true);
  assert.equal(auth.isStrongPassword("StrongPass123!"), true);
  assert.equal(auth.isStrongPassword("password"), false);
});
