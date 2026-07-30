# 聚映项目索引与协作规则

本文件是所有开发者和自动化 Agent 进入项目后的第一入口。开始修改前先阅读本文件，再根据任务打开对应文档。

## 1. 项目一句话说明

聚映是一个多来源影片元数据检索和临时播放入口平台，提供 Web/PWA 界面；服务端不保存视频文件，只处理来源适配、元数据、剧集映射和临时播放结果。

## 2. 当前状态

- 13 个来源全部实现 Native Adapter（9 可用 + 4 禁用）：Lanerc、AuvFun、次元城 Cycapp、金牌 Jinpai、三秋 Sanqiu、瓜子 Guazi、双星 Shuangxing、稀饭动漫 Xifanacg、咕咕动漫 Gugu 均已实测搜索/详情/播放链路贯通。
- 4 个源已禁用：云帆 YZX（假流）、动漫巴士 Dmbus（522 离线）、动漫在线 Lmm85（CF 403）、AkiAnime（DNS 封锁）。
- 播放架构：代理模式（6 源）+ 浏览器直连（4 源），代理发送完整浏览器指纹头。
- 数据模型已扩展：新增 `tags: string[]`、`status: string` 字段，支持题材标签、连载状态筛选。
- 片库支持 5 维筛选（分类/题材/年份/状态/排序），搜索与筛选已分离。
- 搜索结果显示在首页独立弹窗，不跳转片库。
- 首页已并行化（`mapWithConcurrency`），响应时间从 39s → 12s。
- **增量目录缓存**（`catalog-cache.ts`）：启动预热 8 页/源，浏览直读缓存 <100ms，缓存不足自动回退深度搜索。
- **模糊搜索**：逐字分解+渐进前缀回退+字符重叠匹配，输错字也能找到作品。

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
| `app/api/comments/route.ts` | 评论云端存储（阿里云 OSS，公开读、服务端签名写） |

### 来源和基础设施

| 文件 | 责任 |
|---|---|
| `app/lib/sources.ts` | 来源注册表（13 源，9 启用 4 禁用） |
| `app/lib/adapters/types.ts` | Adapter 统一契约（含 tags/status） |
| `app/lib/adapters/native.ts` | Native Adapter（Lanerc/AuvFun/Cycapp/Jinpai/Sanqiu） |
| `app/lib/adapters/remote.ts` | 远程 HTTP Adapter（YZX/Xifanacg/Gugu） |
| `app/lib/adapters/extra-sources.ts` | 新增 Native Adapter（Dmbus/Lmm85/AkiAnime/Shuangxing/Guazi） |
| `app/lib/adapters/index.ts` | Adapter 注册聚合 |
| `app/lib/catalog.ts` | 规范作品 ID、来源变体、剧集合并、跨源元数据补充 |
| `app/lib/catalog-cache.ts` | 增量目录缓存（启动预热8页/源，合并去重，10min刷新，浏览直读） |
| `app/lib/fanout.ts` | 有界并发 |
| `app/lib/cache.ts` | TTL 缓存、singleflight、条件缓存 |
| `app/api/proxy/stream/route.ts` | 播放流代理（m3u8 重写、浏览器指纹转发） |
| `app/api/cover/route.ts` | 封面防盗链代理 |
| `config/source-manifests.json` | 来源运行时、状态和已知主机清单 |
| `.github/workflows/android-release.yml` | Android 正式签名构建与 GitHub/OSS/COS 三渠道发布 |
| `scripts/release/android_release.py` | Android 版本校验、更新清单生成与云对象上传 |
| `android/UPDATE_DISTRIBUTION.md` | Android 签名、云凭据和更新发布操作手册 |
| `C:\Users\songz\Desktop\public-work\remote_sources\*.js` | 本地审核过的来源脚本 |

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

## 12. 近期已完成的账号与云端改动（2026-07）

以下内容是当前仓库已经完成、后续 Agent 必须视为现状的变更记录。

### 12.1 Android 本地使用模式（当前默认）

- Android 客户端当前不要求登录即可使用。
- 观看历史和收藏直接读取本机 `StorageManager`，不再因为未登录显示登录拦截页。
- 离线缓存始终是设备本地能力，不依赖账号。
- 账号登录、注册、邮箱验证码、密码找回、修改邮箱、云端同步和云端头像入口当前暂时停用。
- 账号相关原实现保留，不得删除；恢复时修改 `MainActivity.kt` 中的 `TEMP_ACCOUNT_AUTH_DISABLED`。
- 评论发送当前停用，但已有评论仍可读取展示；恢复发送时修改 `TEMP_COMMENT_POSTING_DISABLED` 并同步开启服务端开关。
- 弹幕发送当前停用，播放器设置和显示开关保留；恢复发送时修改 `EmbeddedVideoPlayer.kt` 中的 `TEMP_DANMAKU_POSTING_DISABLED`。
- 暂停原因和恢复方式必须保留在代码注释中，不能通过删除原逻辑实现“关闭”。

关键文件：

| 文件 | 当前责任 |
|---|---|
| `android/app/src/main/java/com/juying/app/MainActivity.kt` | 本地历史/收藏、账号入口临时关闭、评论只读 |
| `android/app/src/main/java/com/juying/app/ui/EmbeddedVideoPlayer.kt` | 弹幕发送入口临时关闭；播放控制和弹幕设置仍保留 |
| `android/app/src/main/java/com/juying/app/source/AccountRepository.kt` | 账号 API 客户端实现，当前不由默认流程调用 |
| `android/app/src/main/java/com/juying/app/source/CommentRepository.kt` | 评论读取/写入 API 客户端；当前客户端写入入口停用 |

### 12.2 阿里云国内账号服务

为了适配中国大陆网络环境，曾实现独立的 `aliyun-api/` 服务，默认设计为：

- Node.js 20 + Express。
- 阿里云 RDS MySQL 8.0 保存用户、会话、验证码、收藏、观看进度、评论和设备缓存索引。
- 阿里云 DirectMail 发送注册、修改邮箱和密码重置验证码。
- 阿里云 OSS 保存用户头像。
- Android 默认账号 API 地址为 `https://api.lanerc.app`，可用 Gradle 参数 `LANERC_ACCOUNT_API_BASE` 覆盖。
- OSS 配置同时支持 `ALIYUN_OSS_ENDPOINT` 或 `ALIYUN_OSS_REGION`，并支持 `ALIYUN_OSS_SECURITY_TOKEN`。
- `aliyun-api/migrations/001_init.sql` 只负责建表，不要求业务账号拥有创建数据库权限。
- 原 Next.js/D1 账号 API 作为兼容实现保留，但当前服务端账号接口默认关闭。

当前服务端开关：

```text
ACCOUNT_AUTH_ENABLED=false
COMMENTS_POSTING_ENABLED=false
```

将两个值改为 `true` 才允许服务端账号接口和评论写入。仅修改服务端开关不能恢复 Android UI，还必须同步恢复 Android 中对应的临时常量。

### 12.3 账号数据模型与接口

账号服务已实现以下接口，当前按开关停用：

- `/api/auth/request-code`
- `/api/auth/register`
- `/api/auth/login`
- `/api/auth/me`
- `/api/auth/logout`
- `/api/auth/change-email`
- `/api/auth/reset-password`
- `/api/auth/avatar`
- `/api/auth/nickname`
- `/api/sync`
- `/api/comments`（GET 读取保留，POST 写入默认关闭）

密码使用 PBKDF2-SHA-256，验证码和会话令牌只保存哈希值。任何新实现不得把明文密码、验证码、会话令牌或云密钥写入日志、APK、GitHub 或持久化业务快照。

### 12.4 弹幕现状与边界

- 当前来源的统一 `Episode` 模型只提供剧集和播放线路，没有统一的弹幕地址、弹幕 ID 或授权写入接口。
- 当前播放器不得伪造“发送成功”，也不得向未知第三方接口发送弹幕。
- 如后续接入外部弹幕，必须先确认来源授权和接口协议，再增加“弹幕读取地址/时间轴解析/显示过滤/发送授权”完整链路。
- 在没有明确外部弹幕接口前，保持发送关闭，播放功能不能依赖弹幕服务。

### 12.5 Git 与 Sites 状态

- 当前代码唯一主线为 GitHub 仓库 `https://github.com/ssy20031224/juying.git` 的 `main`。
- 已移除本地 `site/main` 跟踪分支；后续提交和交付统一推送 GitHub。
- `.openai/hosting.json` 仍可能作为历史 Sites 元数据存在，但不作为当前代码交付主线；不要重新引入 Sites 分支或依赖。
- 根 `.gitignore` 已忽略所有层级的 `node_modules`；依赖目录只能通过 `package-lock.json` 复现，不能提交。

### 12.6 更新分发与播放器改动

- Android 更新检查和 APK 下载优先使用阿里云 OSS，其次腾讯云 COS，再尝试自建站点，最后才回退 GitHub Releases。
- 同一版本即使清单来自 GitHub，也会先按国内云地址顺序尝试 APK 下载。
- 阿里云默认 OSS Endpoint 会拒绝直接分发 `.apk`；发布脚本必须把阿里云对象保存为 `.bin`，客户端校验后再以本地 `.apk` 安装。不要改回 OSS `.apk` 对象，除非已切换到验证可用的 CNAME/CDN 域名。
- 播放器左侧长按为连续快退回放，右侧长按为临时倍速播放；松手后恢复长按前的用户倍速。
- 亮度/音量纵向拖动必须与长按快退/倍速互斥；手势取消、锁屏或非画中画退后台必须清理临时倍速与快退任务。
- 倍速菜单已包含 `3.0x`，低内存或画质增强限制仍由播放器现有规则决定。
- 播放器控制层已补齐上一集、播放/暂停、下一集、进度和画中画相关控制，并处理后台/画中画生命周期。
- 系统自然横屏使用播放器 2/3 + 信息/来源/选集 1/3；只有用户主动点击全屏按钮时隐藏侧栏并占满窗口，退出全屏不得强制锁回竖屏。
- 剧集解析必须有超时和统一失败收口，同一时间只允许最新选集请求更新播放器；空剧集、适配器缺失或空播放地址不能停在无按钮的等待态。
- 本地模式的历史/收藏不得检查登录对象；历史不得持久化临时或带签名播放 URL。
- 清晰度、画面比例、硬件画质增强、全屏和横竖屏 OSD 逻辑已经保留在播放器内；修改时不能让“设置”按钮误打开其他面板。
- 播放源加载和换源应继续遵循“来源失败隔离、切换源后重新解析”的现有状态机，不得通过账号或评论服务阻塞播放。
- 当前真实弹幕通道尚未接入；播放器只保留弹幕显示设置和未来接入位置，禁止把本地临时文本伪装为已发送的远端弹幕。

本次修改验证：

- Android：`.\gradlew.bat :app:compileDebugKotlin` 通过。
- Android：`.\gradlew.bat :app:assembleDebug` 通过，产物为 `android/app/build/outputs/apk/debug/app-debug.apk`。
- `git diff --check` 通过；尚未创建新版本 Tag 或推送，等待用户确认实际设备播放效果。

## 13. 近期提交记录

以下提交对应本轮账号、云端和临时停用改动：

| 提交 | 内容 |
|---|---|
| `f191cb9` | 恢复跨设备云端历史基础能力 |
| `52187f1` | 完成验证账号流程与 OSS 头像 |
| `cb98e92` | 新增阿里云 RDS 账号服务初版 |
| `a9e56b3` | 清理误加入 Git 索引的 `aliyun-api/node_modules`，补充环境模板与 RDS 脚本 |
| `062bbe2` | 增加云端昵称修改、OSS Endpoint 兼容 |
| `e053547` | 临时关闭账号和评论写入，恢复本地历史/收藏使用 |

`cb98e92` 的当前仓库快照已由 `a9e56b3` 清理依赖文件；未经明确授权，不要对已推送历史执行强制重写。

## 14. 近期验证记录

最近一次账号与临时停用改动完成后已验证：

- `aliyun-api`: `npm run check` 通过。
- `aliyun-api`: `npm test` 通过，2/2。
- 根项目：`npm run build` 通过。
- 根项目：`npm test` 通过，3/3。
- Android：`.\gradlew.bat :app:assembleDebug` 成功。
- Git 工作区干净，`HEAD` 与 `origin/main` 一致。

真实阿里云联调仍需要部署环境中的 RDS、DirectMail、OSS 和 HTTPS API 域名配置；没有这些配置时，不得把账号功能标记为已上线。

## 15. 2026-07-28 首帧渲染与清晰度切换修复

- `juying_player_view.xml` 继续使用 `TextureView`；播放器现在先完成 TextureView 绑定和一次布局请求，再将 `playWhenReady` 设为 `true`，避免“有声音、无画面，切全屏后才出现”的首帧时序问题。
- `SourceManager` 解析播放脚本返回的 `resolutions` 数组为 `PlayResult.qualities`，不再丢弃源脚本已经提供的多档播放 URL。
- 清晰度弹窗优先显示源实际提供的档位；切换具体档位时复用当前来源请求头、尽量保留播放位置并替换 Media3 media source。没有多档 URL 的普通直连视频只显示 `Auto` 并明确提示不可切换；自适应 HLS 仍可使用视频尺寸约束。
- 本次播放器变更只影响首帧绑定和清晰度链路，不改变来源解析、换源和播放错误回退状态机。

### 15.1 首帧问题第二轮定位

- 不得再用 `Player.Listener.onRenderedFirstFrame()` 作为开始播放的触发器；它是渲染结果事件，不是 Surface 就绪事件。
- 播放器应等待 `PlayerView.videoSurfaceView` 已附着、尺寸非零且 TextureView 可用（或 Surface 有效）后再设置 `playWhenReady=true`。
- Media3 `setVideoEffects()` 第一次调用必须发生在 `prepare()` 之前。播放器构建时先用空列表初始化效果管线；默认未开启画质增强时，不再于首轮 Compose effect 中重复异步重配视频渲染器。
- 诊断日志统一使用 `JuyingPlayerDiag`，仅记录播放状态、视频尺寸、Surface 类型/尺寸和来源 host，不记录带签名参数的完整播放 URL、Cookie 或令牌。
- 复现时通过 `adb logcat -s JuyingPlayerDiag:* EmbeddedPlayer:*` 区分：没有 `video-size` 是视频轨道/解码问题；有 `video-size` 但 Surface 未 ready 是视图生命周期问题；Surface ready 且有 `first-frame-rendered` 仍黑屏才是设备合成层或 TextureView 输出问题。

## 16. 2026-07-29 Android 榜单、排期与相关推荐

- `android/app/src/main/java/com/juying/app/source/DiscoveryData.kt`：从来源可验证字段构建 Android 来源榜和周表；禁止生成虚构热度、播放量、集数或更新时间。
- `SourceAdapter`/`SourceExports` 的 `related(id)` 是可选来源合约；来源未实现或返回空列表时，播放器保持真实空状态。
- 逆向取证样本保存于 `C:\Users\songz\Desktop\public-work\lanerc_analysis\remote_scripts\lanerc_rank.js` 与 `lanerc_week.js`；真实入口名是 `lanerc_rank.js`/`lanerc_week.js`，不是文件名 `ranking.js`/`calendar.js`。
- Android 只在 `LanercDiscoveryRepository` 中原生实现经过审计的只读 `/app/rank`、`/app/week` 数据契约，不下载或执行远程 JS。该 Repository 必须继续使用独立网络客户端和独立内存 TTL，不得引用 `ResultCache`、`SourceAdapter.play()`、QuickJS executor 或播放器状态。
- 远程榜单/周表失败必须保持本地来源回退；不能让发现数据异常转换成详情解析或播放失败。
- 季度推荐以 `Asia/Shanghai` 为唯一时间基准，只能选择当前年份的当前季度与同年上一季度；禁止提前展示未来季度或回退到上一年份季度。远程缺失当前季度时，只能用当前年份片库与实时周表的交集补建，并排除已明确归属同年上一季度的标题。
- 卡片模型可以保留 `MediaStatus.UNKNOWN`，但前端不得展示“状态待确认”；显示层只能回退到明确集数、来源状态、年份或中性的“已收录”，不得把未知状态猜成连载或完结。
- 更新面板显示 `update.json` 的发布者自定义标题和完整说明；发布工作流中的手动输入、可选 `.github/android-release-*.{txt,md}`、annotated tag 和默认文案按优先级生成同一份清单/Release 内容。
