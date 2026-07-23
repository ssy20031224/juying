# 聚映：多源检索与播放入口

## 项目文档

- 开发入口与文件索引：`AGENTS.md`
- 当前真实架构：`docs/system-architecture.md`
- 功能缺口与路线图：`docs/product-gap-roadmap.md`
- 多源后端适配：`docs/backend-adaptation-plan.md`
- Lanerc 架构评审：`docs/lanerc-architecture-review.md`
- 移动端 App 外壳：`docs/mobile-app-shell.md`
- 主要变更记录：`docs/change-log.md`

这是一个可安装到 Android 的 PWA/响应式网站原型。它只聚合已授权来源的检索结果和临时播放地址，不下载、不存储、不转发媒体文件。

## 本地运行

```powershell
npm install
npm run dev
```

不配置环境变量时，首页使用演示数据。接入已获授权的 Lanerc 兼容 API 时设置：

```powershell
npm run dev
```

服务端只调用：

- `GET /app/vod/search?keyword=...`
- `GET /app/getvod/{id}`
- `GET /app/config?platform=android`
- `POST /app/proxyx3x`

`/api/search`、`/api/detail` 和 `/api/play` 只返回结构化结果或来源方返回的播放地址，不代理 HLS 分片，也不写入视频文件。

## 发布前检查

- 只添加你拥有授权、许可或明确允许程序化访问的来源。
- 不要把账号令牌、Cookie、签名密钥提交到代码仓库。
- 为每个来源设置域名白名单、请求超时、速率限制和错误隔离。
- 不绕过 DRM、验证码、登录限制、地理限制或版权访问控制。
- 根据来源方条款处理 Referer、Cookie 和临时地址的有效期。

## 移动端

浏览器打开网站后选择“添加到主屏幕”即可安装为 PWA。后续如需原生 Android 包，可使用同一套页面封装为 Trusted Web Activity，并继续让播放器直连来源方。
