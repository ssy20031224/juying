# 账号云服务配置

## 国内生产环境建议

面向中国大陆 Android 用户时，账号接口建议部署在阿里云国内地域：

- API：函数计算 FC、SAE 或 ECS。
- 数据库：RDS MySQL 8.0，与 API 放在同一地域、同一 VPC，并使用内网连接。
- 头像：阿里云 OSS，目录为 `avatars/<userId>/...`。
- 验证码邮件：阿里云邮件推送 DirectMail。
- Android API 域名：使用已备案的国内域名和 HTTPS。

当前仓库的数据访问层仍保留 D1 兼容实现。正式面向国内用户发布账号功能前，应将 Drizzle
SQLite schema 迁移为 MySQL schema，并把 API 部署到阿里云后再修改 Android 的 `API_BASE`。

## 邮件推送环境变量

```text
ALIYUN_DM_ACCESS_KEY_ID=
ALIYUN_DM_ACCESS_KEY_SECRET=
ALIYUN_DM_ACCOUNT_NAME=
ALIYUN_DM_FROM_ALIAS=聚映
AUTH_CODE_PEPPER=
```

请为 DirectMail 使用单独的 RAM 用户，只授予邮件发送权限，不要复用 OSS 的高权限密钥。

## OSS 头像环境变量

```text
ALIYUN_OSS_ACCESS_KEY_ID=
ALIYUN_OSS_ACCESS_KEY_SECRET=
ALIYUN_OSS_ENDPOINT=
ALIYUN_OSS_BUCKET=
ALIYUN_OSS_PUBLIC_BASE_URL=
```

头像支持 JPEG、PNG、WebP、GIF，单文件最大 5 MB。客户端不保存 OSS 密钥，只上传到服务端接口。
