import "dotenv/config";

function required(name) {
  const value = String(process.env[name] || "").trim();
  if (!value) throw new Error(`Missing required environment variable: ${name}`);
  return value;
}

export const config = {
  port: Number(process.env.PORT || 3001),
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
    region: required("ALIYUN_OSS_REGION"),
    bucket: required("ALIYUN_OSS_BUCKET"),
    publicBaseUrl: required("ALIYUN_OSS_PUBLIC_BASE_URL").replace(/\/+$/, ""),
  },
};
