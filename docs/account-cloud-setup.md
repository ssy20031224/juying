# 账号云服务配置

## 初期推荐：Cloudflare Worker + D1（无固定数据库月费）

当前仓库根目录已经包含完整的 D1 账号实现：`db/schema.ts`、`drizzle/*.sql` 与
`app/api/auth`、`app/api/sync`、`app/api/comments`。D1 存放账号、会话、验证码、
收藏、播放进度、离线缓存索引及评论；不会存储视频、本地文件路径或临时播放地址。

Sites 项目的逻辑绑定名必须保持为 `DB`：

```json
{
  "d1": "DB",
  "r2": null
}
```

生产环境至少配置：

```text
ACCOUNT_AUTH_ENABLED=true
COMMENTS_POSTING_ENABLED=true
COMMENTS_REQUIRE_ACCOUNT=true
AUTH_CODE_PEPPER=<至少 32 字符的稳定随机密钥>
```

`AUTH_CODE_PEPPER` 一旦产生用户验证码后必须保持稳定，只能存放在服务端 Secret，
不得写入 Git、APK 或公开配置。D1 路线仍使用阿里云 DirectMail 发送验证码、阿里云
OSS 保存头像；公告、更新清单和来源脚本继续使用公开只读 OSS 对象。

Android Release 构建时将账号地址指向公开部署的 Worker/Sites 域名：

```text
LANERC_ACCOUNT_API_BASE=https://<公开 API 域名>
```

如果 Sites 仍是仅所有者可访问，Android 的匿名注册/登录请求会被访问层拦截；面向
App 用户提供账号服务时，该 API 所在站点必须允许公开访问，账号接口自身再使用
Bearer Token 保护用户数据。

## 国内全托管备选

如果后续大陆网络稳定性要求高，可将同一套 API 迁到阿里云国内地域：

- API：函数计算 FC、SAE 或 ECS。
- 数据库：RDS MySQL 8.0，与 API 放在同一地域、同一 VPC，并使用内网连接。
- 头像：阿里云 OSS，目录为 `avatars/<userId>/...`。
- 验证码邮件：阿里云邮件推送 DirectMail。
- Android API 域名：使用已备案的国内域名和 HTTPS。

国内独立服务位于 `aliyun-api/`，包含 RDS MySQL 初始化脚本、账号 API、DirectMail、OSS
头像上传和 Dockerfile。原 Next.js/D1 路由暂时保留为兼容层，但 Android 默认使用
`https://api.songxiang.online`。初期 D1 方案不需要启动该 MySQL 服务；将来迁移时 Android
只需更换 API Base URL，客户端协议不变。

Android 可通过 Gradle 参数覆盖 API 地址：

```text
LANERC_ACCOUNT_API_BASE=https://api.songxiang.online
```

账号、邮箱验证、同步和登录后评论已恢复；生产环境保持：

```text
ACCOUNT_AUTH_ENABLED=true
COMMENTS_POSTING_ENABLED=true
```

维护时可显式改为 `false`。客户端的本地观看记录、收藏和离线缓存始终不依赖账号服务。
同步内容仅包含收藏快照、观看进度和设备缓存索引；不会上传视频文件、本地文件路径或临时播放 URL。

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

## 公告与来源脚本 OSS 目录

```text
config/announcement.json
source-scripts/AuvFun.js
source-scripts/lanerc.js
source-scripts/jinpai.js
...
```

公告对象键可用 `ANNOUNCEMENT_OBJECT_KEY` 修改。Android 可用 Gradle 参数覆盖公告地址和脚本公开目录：

```text
LANERC_ANNOUNCEMENT_URLS=https://api.lanerc.app/api/announcement
LANERC_SOURCE_SCRIPT_BASE_URL=https://<bucket-cname>/source-scripts
```

脚本对象应设置 JavaScript 内容类型、公开只读和 ETag；OSS 写权限只授予发布流水线，APK 内不放 AccessKey。
