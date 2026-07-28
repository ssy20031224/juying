# 聚映阿里云账号 API

这是面向中国大陆 Android 用户的独立账号服务，不依赖 Cloudflare D1。它可以作为容器部署到
阿里云 SAE、ECS 或函数计算自定义容器，并通过 VPC 内网连接 RDS MySQL。

## 云资源

1. 在同一地域、同一 VPC 创建 RDS MySQL 8.0 与 SAE/FC/ECS。
2. 创建 `lanerc` 数据库和最小权限数据库账号。
3. 执行 `migrations/001_init.sql`。
4. 在 DirectMail 创建并验证发信域名、发信地址。
5. 创建 OSS Bucket 和仅允许头像目录读写的 RAM 用户。
6. 复制 `env.example` 为 `.env` 或配置到云服务环境变量中，禁止提交真实密钥。

OSS 可配置 `ALIYUN_OSS_ENDPOINT`（推荐在阿里云内网部署时使用内网 Endpoint）或
`ALIYUN_OSS_REGION`，两者至少填写一项；临时 RAM 凭证可额外填写
`ALIYUN_OSS_SECURITY_TOKEN`。

## 本地检查

```bash
npm ci
npm run check
npm start
```

健康检查：

```text
GET /health
```

所有业务错误和 404 都返回 JSON，因此 Android 不会再把 HTML 错误页强制解析成 `JSONObject`。

## 反向代理

建议使用已备案的 `https://api.lanerc.app`，由 ALB、Nginx 或 SAE/FC 自定义域名转发到容器
`3001` 端口。Android 通过构建参数 `LANERC_ACCOUNT_API_BASE` 指向该域名。

## 权限

- RDS：API 仅使用业务数据库账号，不使用高权限管理员账号。
- DirectMail：独立 RAM 用户，仅授予邮件发送权限。
- OSS：独立 RAM 用户，仅授予 `avatars/*` 所需权限。
- 不要在 APK、GitHub 或客户端日志中写入任何云密钥。
