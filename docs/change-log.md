# 项目变更记录

本文件记录会改变系统结构、数据模型、来源能力、用户流程或部署方式的主要变更。小型文案和纯样式调整可以合并记录。

## 2026-07-23：架构文档基线

- 新增 `docs/system-architecture.md`，明确当前真实能力、来源状态、缓存边界、播放器现状和技术债。
- 新增 `docs/product-gap-roadmap.md`，规划多源合并、完整播放器、分类、榜单、排期、弹幕和设备缓存。
- 新增根目录 `AGENTS.md`，作为项目索引和后续变更规则。
- 明确当前主要可用来源为 Lanerc，其余来源分为“代码存在但未配置”和“运行时未实现”。
- 明确分享、播放设置和离线缓存等现有视觉入口尚未完成业务闭环。
- 已校验索引中的文件全部存在；`npm test` 3 项通过，`npm run lint` 无错误（保留远程封面 `<img>` 的既有性能警告）。

## 2026-07-23：响应式网站与 PWA 第一版

- 完成桌面首页、移动端首页、片库、详情、个人页和播放器。
- 首页能加载 Lanerc 实时轮播、热门、日漫和剧场版数据。
- 完成搜索、详情、剧集和临时播放 URL 链路。
- 加入设备本地收藏和最近打开记录。
- 加入 Manifest 和 Service Worker，支持 PWA 安装。
- Service Worker 不缓存 API、封面、播放地址和视频。

## 2026-07-23：缓存与并发基础

- 首页元数据缓存 15 分钟。
- 搜索结果缓存 5 分钟。
- 详情缓存 30 分钟。
- 临时播放结果缓存 15 秒。
- 相同缓存 Key 使用 singleflight 合并并发请求。
- 搜索来源默认最多并发 4 个。

## 2026-07-23：Lanerc Native Adapter

- 根据已保存的 Lanerc JS 行为实现 TypeScript Native Adapter。
- 完成域名发现、响应解码、首页、搜索、详情和播放解析。
- 不再依赖通用 `LANERC_DISCOVERY_URL`、`LANERC_FALLBACK_URL`、`LANERC_ALLOWED_HOSTS` 参数。
- 实测首页、搜索、28 集详情和第一集临时播放地址可返回。

## 2026-07-23：来源变体和移动端播放器核心

- 新增 `app/lib/catalog.ts`，用规范作品 ID、`SourceVariant` 和 `CanonicalEpisode` 保留同一作品的来源与剧集线路。
- 搜索聚合不再只递增 `sourceCount`，现在返回完整 `variants[]`。
- 新增 `POST /api/media/detail`，并行合并一个作品的多个来源详情和同一集的多个播放候选。
- 新增 `app/components/player/MediaPlayer.tsx`，替换原生控件作为主控制层。
- 播放器已接入上下集、播放器内选集、换源面板、倍速、画中画、全屏、分享、收藏、进度记录和缓存禁用态。
- 弹幕开关和设置面板已接入；由于当前没有授权弹幕数据源，显示“暂无弹幕通道”。
- 已在 390px 移动端验证 Lanerc 真实作品详情、剧集播放、倍速面板、弹幕设置面板和换源面板。
- `npm run lint` 通过（仅保留远程封面 `<img>` 的性能提示），`npm test` 通过：构建和 3 项渲染/API 回归测试均通过。

## 2026-07-23：接入本地完整来源 JS 的 Native 默认流程

- 确认原始脚本位于 `C:\Users\songz\Desktop\public-work\remote_sources\`，包含 13 个来源 JS、来源清单和远程配置。
- 按本地脚本核对并移植 AuvFun、Cycapp、Jinpai、Sanqiu 的主链路；`config/categories/searchFiltered` 已纳入下一步统一目录 API，不再把这些来源的环境变量当作必填启动条件。
- 将 AuvFun、Cycapp、Jinpai、Sanqiu 的默认主机和脚本内运行参数移入服务端 Native Adapter；环境变量保留为授权部署时的可选覆盖。
- 实测 `/api/home` 已返回 AuvFun 4 个分区和 Sanqiu 最新动漫，Lanerc 仍正常返回；Cycapp 当前上游返回 401，Jinpai 仍需继续健康验证。
- 前端首页不再额外截断每个来源分区，展示 Adapter 返回的完整分区结果；来源接口自身的分页上限仍需通过后续目录 API 扩展。
