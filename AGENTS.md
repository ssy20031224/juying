# 聚映项目索引与协作规则

本文件是所有开发者和自动化 Agent 进入项目后的第一入口。开始修改前先阅读本文件，再根据任务打开对应文档。

## 1. 项目一句话说明

聚映是一个多来源影片元数据检索和临时播放入口平台，提供 Web/PWA 界面；服务端不保存视频文件，只处理来源适配、元数据、剧集映射和临时播放结果。

## 2. 当前状态

- 当前主要可自动工作的真实来源：Lanerc、AuvFun、Sanqiu；四个 Native 来源均已按本地 JS 的完整契约移植，AuvFun/Jinpai/Sanqiu 的环境变量为可选覆盖。
- Cycapp、Jinpai 仍需继续做来源健康验证；Cycapp 当前接口返回 401，不应通过伪造凭据绕过。
- 其余 8 个来源：只在注册表中登记，JS Worker/Browser Worker 尚未实现。
- 前端已有首页、搜索、片库、详情、自定义移动端播放器、收藏和历史。
- 搜索结果已保留 `variants[]`；详情通过 `/api/media/detail` 合并同一作品的多来源剧集和线路。
- 分类、排行榜、番剧排期、真实多源换源、弹幕和设备端视频缓存尚未实现。
- 数据库 Schema 当前为空；收藏和历史只存在用户浏览器。

完整现状以 `docs/system-architecture.md` 为准。

## 3. 必读文档索引

| 文档 | 什么时候阅读 | 内容 |
|---|---|---|
| `docs/system-architecture.md` | 任何功能修改前 | 当前真实架构、模块、API、来源状态、缓存和已知限制 |
| `docs/product-gap-roadmap.md` | 开发新功能前 | 目标数据模型、播放器规格、分类/榜单/排期和实施顺序 |
| `docs/backend-adaptation-plan.md` | 修改来源适配时 | Native、JS Worker、Browser Worker 的适配计划 |
| `docs/lanerc-architecture-review.md` | 修改 Lanerc 或参考其架构时 | Lanerc 已分析的模块、播放和并发模式 |
| `docs/mobile-app-shell.md` | 修改 PWA/Android 外壳时 | 当前移动端发布形态和原生层边界 |
| `docs/change-log.md` | 完成大功能后 | 主要功能和架构变更记录 |
| `config/source-manifests.json` | 新增/调整来源时 | 来源脚本、运行时、状态和已知主机清单 |

## 4. 代码索引

### 前端

| 文件 | 责任 |
|---|---|
| `app/page.tsx` | 当前页面和客户端状态；计划拆分 |
| `app/globals.css` | 桌面/移动端布局和播放器样式 |
| `app/layout.tsx` | 页面元数据和 PWA Manifest |
| `public/manifest.webmanifest` | PWA 配置 |
| `public/sw.js` | 只缓存应用壳，不缓存媒体 |

### API

| 文件 | 责任 |
|---|---|
| `app/api/home/route.ts` | 来源首页分区 |
| `app/api/search/route.ts` | 有界并行多源搜索和基础去重 |
| `app/api/detail/route.ts` | 单来源详情和剧集 |
| `app/api/media/detail/route.ts` | 多来源详情聚合、规范剧集和换源线路 |
| `app/api/play/route.ts` | 临时播放地址解析 |

### 来源和基础设施

| 文件 | 责任 |
|---|---|
| `app/lib/sources.ts` | 来源注册表 |
| `app/lib/adapters/types.ts` | Adapter 统一契约 |
| `app/lib/adapters/native.ts` | Native Adapter |
| `app/lib/catalog.ts` | 规范作品 ID、来源变体和剧集合并 |
| `app/lib/fanout.ts` | 有界并发 |
| `app/lib/cache.ts` | TTL 缓存和 singleflight |
| `config/source-manifests.json` | 来源运行时和状态元数据 |
| `C:\Users\songz\Desktop\public-work\remote_sources\*.js` | 本地审核过的来源脚本；作为 Adapter 的行为依据，不在 API 主进程直接执行 |
| `worker/index.ts` | Cloudflare Worker/vinext 入口 |
| `db/schema.ts` | 未来目录、进度和用户数据表；当前为空 |

### 播放器

| 文件 | 责任 |
|---|---|
| `app/components/player/MediaPlayer.tsx` | 自定义播放控制、选集、换源、倍速、画中画、全屏、分享、收藏和进度 |
| `app/components/player/types.ts` | 播放会话和规范作品/剧集的前端类型 |

## 5. 架构不变量

任何修改都必须遵守：

1. 服务端不保存视频文件或切片。
2. 临时播放 URL 只做短时缓存，不进入日志和持久数据库。
3. 未审计远程 JS 不得在 API 主进程直接执行。
4. 每个来源必须通过统一 Adapter 合约返回规范化数据。
5. 来源失败必须隔离，不能拖垮其他来源。
6. 密钥、签名参数和来源凭据只放服务端环境变量。
7. 不实现绕过会员、付费、地区、防盗链、CAPTCHA 或登录控制。
8. 只接入拥有授权或明确允许使用的来源。
9. UI 按钮必须连接真实状态；未完成的能力要显示“未启用/不支持”，不能伪装成功。
10. 视频离线缓存只能发生在用户设备且来源允许时，不能改变“服务端不存片”的边界。

## 6. 来源状态术语

禁止只用一个 `enabled` 表示所有状态。文档和后续代码应区分：

- `registered`：已登记。
- `adapterImplemented`：Adapter 已实现。
- `configured`：运行所需配置已存在。
- `healthy`：最近健康检查成功。
- `searchVerified`：搜索已验证。
- `detailVerified`：详情/剧集已验证。
- `playVerified`：播放解析已验证。
- `disabled`：人工停用。

只有 `playVerified` 才能在产品中标记为“可播放来源”。

## 7. 大功能完成后的强制记录

以下变化属于“大功能”：

- 新增或完成一个来源 Adapter。
- 修改统一数据模型或 API 合约。
- 新增分类、榜单、排期、弹幕、缓存、账号或播放器核心功能。
- 修改缓存、并发、熔断、数据库或部署架构。
- 拆分主要模块或改变目录结构。

完成后必须：

1. 更新 `docs/change-log.md`，记录日期、功能、影响和验证结果。
2. 更新 `docs/system-architecture.md` 中已经发生变化的现状。
3. 更新 `docs/product-gap-roadmap.md` 的状态或下一步。
4. 若文件职责改变，更新本 `AGENTS.md` 的代码索引。
5. 若来源状态改变，更新 `config/source-manifests.json`。
6. 运行 `npm run lint`、`npm test`；涉及 UI 时额外验证桌面和移动端。

## 8. 修改来源 Adapter 的流程

1. 确认授权和允许的访问边界。
2. 阅读 `C:\Users\songz\Desktop\public-work\remote_sources\` 对应的完整 JS 和 `config/source-manifests.json`。
3. 选择 Native、JS Worker 或 Browser Worker。
4. 建立不含密钥的固定响应 Fixture。
5. 完整覆盖来源脚本的 config/categories/home/search/searchFiltered/detail/play 契约，再做规范化。
6. 添加超时、响应大小限制和错误分类。
7. 添加契约测试。
8. 在来源状态中分别记录实现、配置和验证结果。
9. 更新架构文档和变更记录。

## 9. 播放器开发顺序

不要先堆视觉按钮。按以下顺序：

1. 规范作品、剧集和来源变体模型。
2. 真实多源换源和来源健康提示。
3. 播放状态机和进度存储。
4. 上下集、选集、倍速、清晰度。
5. 画中画、全屏和移动端手势。
6. 分享。
7. 弹幕通道和设置。
8. 来源允许时的设备端缓存。

详细规格见 `docs/product-gap-roadmap.md`。

## 10. 常用验证命令

```powershell
npm run lint
npm test
npm run build
npm run dev
```

测试时至少检查：

- `/api/home`
- `/api/search?q=测试词`
- `/api/detail?source=...&id=...`
- `POST /api/play?source=...`
- 桌面首页
- 390px 左右移动端首页
- 详情选集
- 播放器打开和错误状态

## 11. 当前优先任务

1. 建立持久化规范目录，避免每次详情都重新聚合。
2. 接入更多已授权来源并完成健康/播放契约测试。
3. 拆分 `app/page.tsx` 的目录、详情和个人领域。
4. 建立统一分类目录、热度榜和番剧排期。
5. 补齐播放器清晰度重解析、横屏手势和真实来源健康切换。
6. 分批完成 JS Worker 和 Browser Worker 来源。
7. 最后接入有明确来源的弹幕和设备端缓存。
