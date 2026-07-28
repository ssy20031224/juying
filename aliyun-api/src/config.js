import "dotenv/config";

function required(name) {
  const value = String(process.env[name] || "").trim();
  if (!value) throw new Error(`Missing required environment variable: ${name}`);
  return value;
}

function optional(name) {
  return String(process.env[name] || "").trim();
}

const ossEndpoint = optional("ALIYUN_OSS_ENDPOINT");
const ossRegion = optional("ALIYUN_OSS_REGION");
if (!ossEndpoint && !ossRegion) {
  throw new Error("Missing required environment variable: ALIYUN_OSS_ENDPOINT or ALIYUN_OSS_REGION");
}

export const config = {
  port: Number(process.env.PORT || 3001),
  // TEMP: 账号登录/注册默认关闭，设置 ACCOUNT_AUTH_ENABLED=true 可恢复。
  accountAuthEnabled: process.env.ACCOUNT_AUTH_ENABLED === "true",
  // TEMP: 评论写入默认关闭，GET 读取仍可用。
  commentsPostingEnabled: process.env.COMMENTS_POSTING_ENABLED === "true",
  publicApiOrigin: String(process.env.PUBLIC_API_ORIGIN || "").replace(/\/+$/, ""),
  mysql: {
    host: required("MYSQL_HOST"),
    port: Number(process.env.MYSQL_PORT || 3306),
    database: required("MYSQL_DATABASE"),
    user: required("MYSQL_USER"),
    password: required("MYSQL_PASSWORD"),
    connectionLimit: Number(process.env.MYSQL_CONNECTION_LIMIT || 10),
  },
  authCodePepper: required("AUTH_CODE_PEPPER"),
  directMail: {
    accessKeyId: required("ALIYUN_DM_ACCESS_KEY_ID"),
    accessKeySecret: required("ALIYUN_DM_ACCESS_KEY_SECRET"),
    accountName: required("ALIYUN_DM_ACCOUNT_NAME"),
    fromAlias: String(process.env.ALIYUN_DM_FROM_ALIAS || "聚映"),
  },
  oss: {
    accessKeyId: required("ALIYUN_OSS_ACCESS_KEY_ID"),
    accessKeySecret: required("ALIYUN_OSS_ACCESS_KEY_SECRET"),
    endpoint: ossEndpoint,
    region: ossRegion,
    bucket: required("ALIYUN_OSS_BUCKET"),
    publicBaseUrl: required("ALIYUN_OSS_PUBLIC_BASE_URL").replace(/\/+$/, ""),
    stsToken: optional("ALIYUN_OSS_SECURITY_TOKEN"),
  },
};
