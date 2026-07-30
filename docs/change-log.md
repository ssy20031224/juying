# 项目变更记录

## 2026-07-30：Android 当年季度新番榜、排期与刷新反馈

- 首页入口、排行榜和排期表统一按北京时间选择当年季度：显示当前季度与同一年内的上一季度；例如 2026-07-30 只显示 2026 年 7 月与 4 月，不提前展示 10 月，也不混入 2025 年 10 月。
- 当 `/app/rank` 尚未发布当前季度分组时，只用“当前年份片库 ∩ 实时周表”，并排除已经明确归属上一季度的标题来补建当前季度；使用首页真实热门顺序优先排序，不伪造热度或评分。
- 排行榜改为季度页签；前三名使用等宽领奖台并统一标题区、评分区的高度和对齐，无有效评分时明确显示“暂无评分”。
- 排期表增加季度页签，在季度作品集合内按周一至周日展示；星期、时间和集数仍只使用远程明确字段。
- 榜单与排期刷新现在显示加载动画、完成/失败提示和北京时间秒级更新时间，连续点击时也会给出“正在刷新”反馈。
- 视频卡片不再直接展示“状态待确认”：无法可靠判定完结/连载时，依次展示明确集数、来源状态、年份或“已收录”，同时保留内部 `UNKNOWN`，不把未知状态猜成连载或完结。
- 新增北京时间季度窗口、季度标签解析和周表简写状态单元测试。

---

## 2026-07-30：Android 本地记录、横屏双栏与播放器恢复修复

- 修复本地使用模式仍被账号状态拦截的问题：收藏可直接写入本机，播放地址解析成功后立即记录本机历史；历史不再持久化可能过期的临时/签名播放 URL。
- 剧集播放解析改为单请求状态机：切集会取消上一条解析，设置 20 秒上限，并对无剧集、无适配器、空地址、超时和媒体加载错误显示可重试错误，不再永久停在“等待解析播放地址”。
- 系统自动旋转到横屏时采用播放器约 2/3、简介/来源/选集约 1/3 的双栏布局；用户主动点击全屏按钮时仍进入真正全屏，退出后把方向控制交还给系统。
- 内嵌播放器补齐真实剧集列表与选集回调，后台返回后仍可使用上一集、下一集和选集。
- 亮度/音量拖动与长按快退/倍速增加互斥；松手、手势取消、锁屏和退后台都会在 `finally`/生命周期回调中停止临时快退并恢复原倍速。
- 非画中画状态进入后台或锁屏时不再依赖 `isPlaying` 判断，始终清理临时手势并暂停；播放结束后再次点击播放会从头恢复。
- 新增播放器交互策略单元测试，覆盖自然横屏/显式全屏、本地模式收藏和拖动/长按互斥。
- 验证：19 项 Android 单元测试、`:app:assembleDebug` 与 `:app:lintDebug` 全部通过。

---

## 2026-07-29：Android 原生移植榜单/周表与自定义更新说明

- 新增 `LanercDiscoveryRepository`，原生移植已审计 `lanerc_rank.js`、`lanerc_week.js` 的只读数据契约：请求 `/app/rank`、`/app/week`，恢复自定义 Base64 字符表并使用 AES-256-ECB/PKCS5 解码。
- 榜单热门/人气保留后端季度组原序；评分榜不照搬原脚本的“第三组”假语义，改为合并季度数据后按真实 `vod_score` 降序。
- 周表使用服务端 `week_list.mon..sun`、`vod_total`、`vod_remarks`，显示真实星期、更新时间和当前集数；远程失败时回退到来源明确字段，不按卡片位置猜测。
- 远程榜单/周表使用独立 OkHttp 客户端和独立 10 分钟内存缓存，不共享 QuickJS、`ResultCache`、播放地址缓存、Cookie、连接池或 Media3 状态；打开播放器不会发起发现数据网络请求。
- 播放器相关推荐优先采用来源 `related(id)`；来源未提供时，才使用已经载入的榜单/周表条目按题材交集、年份和真实评分确定性排序，不随机伪造。
- 更新清单兼容 `notes`、`releaseNotes`、`release_notes`、`updateContent`、`changelog` 等自定义说明字段；更新弹窗改为可滚动完整显示，不再截断为 8 行。
- 用户主动点击“检测更新”时会绕过旧的 HTTP 清单缓存，避免已经推送新版本却仍显示上一次的标题或更新内容。
- Android Release 支持手动填写 `release_title`/`release_notes`；标签推送还可读取 `.github/android-release-title.txt`、`.github/android-release-notes.md` 或 annotated tag 内容，生成的 `update.json` 与 GitHub Release 共用同一份说明。

---

## 2026-07-29：Android 来源推荐、真实周表与来源榜单优化

- Android QuickJS 合约新增可选 `related(id)`，播放器详情页只在当前来源真实返回相关推荐时展示横向竖版卡片，不使用随机首页作品兜底。
- 详情缓存继续支持快速首屏，但命中缓存后会在后台重新拉取详情并更新状态与剧集数，避免最近更新或完结作品持续显示旧数据。
- 状态优先级调整为“完结 > 未开播 > 更新中 > 连载中”；同时出现“连载中、更新至XX集”时展示为“更新中”。
- 片库固定三列改为最小 120dp 的自适应网格，手机、平板和横屏按可用宽度自动调整列数。
- 周表删除按首页列表平均切分、`index + 12` 集和固定 `23:30` 的伪数据；现在只收录来源状态/标签中明确给出星期的作品，并展示来源提供的时间和集数。
- 排行榜删除按列表序号生成的热度分与播放量；热门/人气榜保留来源首页对应分区的原始顺序，评分榜只接受真实数值评分。前三名使用领奖台，其余使用紧凑排行列表。
- 首页额外保留来源原始分区供榜单使用；无首页数据时的关键词兜底明确标记为“内容推荐”，不再冒充热门榜。
- 本阶段尚未接入逆向应用的远程榜单/周表；其后已由上方“原生移植榜单/周表”变更以隔离 Repository 接入。
- 新增周表解析、来源榜单和状态优先级单元测试。

---

## 2026-07-29：Android 卡片/详情状态与离线播放修复

- 视频卡片题材改为按来源顺序最多显示 4 项；超过 4 项显示 `...`，不再固定只显示 2 项。
- 播放器下方“动漫”区域改为“作品名 + 题材 + 年份 + 实际集数”；点击“简介”后在播放器下方显示独立资料层并覆盖选集内容，资料层展示评分（有数据时）、年份、状态、集数、类型和完整简介。
- 新增统一 Android 状态解析：完结标记优先于更新文本；显式区分“已完结 / 连载中 / 更新中 / 未开播 / 状态待确认”；裸集数和空状态不再被误判为连载，`0集` 也不再被误判为完结。
- Android 详情解析补齐 `tags/status/score`，并用卡片摘要补足来源详情缺失的元数据；状态筛选改用同一套规范化规则。
- 离线播放器的数据源从纯 HTTP 改为 Media3 `DefaultDataSource`，支持 `file://` 本地 MP4 和本地 HLS。
- HLS 下载改为整集完整性校验：分片、加密密钥和初始化段全部下载并改写为本地相对路径后才标记完成；不同剧集使用独立分片前缀，避免互相覆盖；删除和空间统计只处理当前剧集资源。
- Android 前端隐藏自定义 JS 导入、数据源诊断日志、账号昵称、账户邮箱和账户密码卡片；底层实现保留，便于后续受控恢复。
- 新增 8 个状态解析单元测试。
- 验证：`android\gradlew.bat :app:compileDebugKotlin`、`android\gradlew.bat :app:testDebugUnitTest` 通过。

---

## 2026-07-28：修复阿里云 OSS 更新下载回退 GitHub

- 确认默认 OSS Endpoint 对 `.apk` 返回 `ApkDownloadForbidden`，导致客户端实际回退 GitHub。
- 阿里云发布对象改为 `.bin` 和 `application/octet-stream`；客户端仍保存为本地 `.apk` 并执行 SHA-256 校验。
- 腾讯云 COS 和 GitHub Release 继续发布标准 `.apk`。

## 2026-07-28：Android 播放器横屏/画中画/手势修复与评论云端存储

- **横屏裁切修复**：播放页容器竖屏保持 16:9、横屏改为 `fillMaxSize()` 精确占满可见区域，修复部分机型横屏时顶部栏与进度条下方控制行被裁切；全屏统一处理系统栏与刘海屏 `SHORT_EDGES`，控制层避让挖孔区域。
- **画中画治理**：`onUserLeaveHint` 仅当播放器在屏且正在播放时才进入小窗（首页等页面不再误触发）；小窗支持播放/暂停、上/下集 RemoteAction；小窗内隐藏播放器控制层；退出播放器组合时自动恢复竖屏与系统栏。
- **手势与播放体验**：长按左侧改为连续快退回放、右侧倍速播放，松手均恢复长按前倍速；倍速选项新增 3X；修复「默认/铺满/裁剪/拉伸」快捷切换与弹窗标注不一致；锁屏/退后台（ON_STOP）自动暂停，修复后台播放导致的进度冻结与按钮状态颠倒；播放器中央新增 上一集/播放暂停/下一集 三键；弹幕输入区改为真实打字发送，倍速与清晰度弹窗改为胶囊网格样式；播放画面改 TextureView，修复部分机型“点开黑屏、切全屏才出画面”。
- **硬件画质增强真实生效**：新增 `media3-effect`，开启后通过 GPU 视频效果管线提升对比度与亮度；修复扫描线动画中途关闭时冻结的问题（改为向左回退滑出）。
- **更新国内优先**：更新清单与 APK 下载地址按 阿里云 OSS → 腾讯云 COS → 自建站点 排序，GitHub 仅作最后回退，解决国内更新/下载慢。
- **评论云端存储**：新增 `app/api/comments/route.ts`（GET/POST），评论以 `comments/<mediaKey>.json` 存于阿里云 OSS；读取走公开地址、写入仅服务端持钥签名 PUT；Android 新增 `CommentRepository` 接入，失败回退本机并明确提示，不影响播放链路。

验证：`gradlew :app:assembleDebug` 构建通过；`eslint app/api/comments/route.ts` 无告警。

## 2026-07-27：Android 三渠道自动签名构建与更新发布

- 新增 GitHub Actions Android Release 工作流，支持版本标签和手动触发，自动构建正式签名 APK、生成 SHA-256 与更新清单、保存构建产物并创建 GitHub Release。
- 新增阿里云 OSS、腾讯云 COS 可选上传通道；完全未配置时自动跳过，云上传异常不会阻止 GitHub Release 兜底发布。
- Android Gradle 构建支持从受保护属性注入版本、签名文件和国内更新清单 URL；keystore 与云密钥不进入仓库。
- 新增 `scripts/release/android_release.py`，统一验证语义版本、生成稳定 `versionCode`、生成 `update.json`，并隔离各云渠道上传错误。
- 重写 Android 更新分发文档，记录最小权限、GitHub Secrets/Variables、正式签名、触发发布和国内 CDN 配置方法。

## 2026-07-26：Android 多源播放失败隔离与诊断修复

- 完整审计并语法校验 `remote_sources/` 的 14 个 JS 源脚本；将损坏的 Android 内置 `guazi.js`、`sanqiu.js` 资产与已校验远程版本同步，避免 APK 冷启动时因语法错误丢源。
- `RemoteSourceFetcher.getScript()` 在缓存缺失/失效时按源 URL 有界拉取并原子写缓存，消除异步 `syncAll()` 与首次初始化之间的竞态。
- Android 播放结果现在合并脚本返回的 `userAgent/ua/origin/cookie`；`referer:"never"` 不会误发为 Referer；ExoPlayer 的重建键包含 URL、类型、Referer 和 headers，避免同 URL 不同鉴权头复用旧播放器。
- 带 `auth_key/expires/deadline/token/signature` 等短时签名参数的播放地址不再写入通用播放缓存，避免过期 CDN URL 间歇性 403/404。
- QuickJS 的 `sniffMedia` 从永久 `null` 升级为带 UA/Referer 的静态媒体 URL 嗅探，并提供 `sniffAllMedia` 兼容入口；无法处理动态 WebView 播放页时记录结构化原因。
- Coil 封面请求支持豆瓣 Referer/UA 和脚本 `@Referer/@User-Agent` 元数据，封面失败写入 `SourceLogManager`。
- 播放解析空结果、播放器错误、静态嗅探失败均按源/阶段记录，可在现有源诊断界面中区分 API、解析、CDN/播放器和图片问题。

## 2026-07-25：统一 JS 引擎架构 + P0 搜索性能/准确性深度优化 + 片库加载体验

### 统一 JS 引擎架构（Web + Android 共享一份代码）
- **删除 5000+ 行手写 TypeScript 适配器**（`native.ts`/`remote.ts`/`extra-sources.ts`），改为直接执行 `remote_sources/*.js` 原始脚本
- 新增 `app/lib/js-engine.ts`：Node.js `vm` 沙箱 + `sync-request` 同步 HTTP + 完整 `crypto.aes/rsa/hex/base64/inflate` 宿主函数，100% 兼容 QuickJS API
- 新增 `app/lib/adapters/js-source.ts`：将 JS 引擎输出包装为 `SourceAdapter` 接口
- 重写 `app/lib/adapters/index.ts`：循环 `sources.ts` 自动 `createJsAdapter()`，加源只需加一行配置
- **成果**：Web（Next.js API）和 Android（QuickJS）运行同一份 JS 脚本，新增/更新源无需写 TypeScript
- 新增源：`shutiao.js`（薯条影视，Protobuf 协议 + 动态 genre 筛选），从 Lanerc CDN 抓取

### P0 搜索速度优化
- **筛选速度**：每源翻页 8→2（40-60条），超时 8000→4000ms，genre 激活时仅查 4 个支持源（9→4），首筛 30s→2-4s
- **关键词搜索**：`fuzzySearchSource` 前缀回退 6 轮→2 轮，单次超时 5000→3000ms，总预算 8000ms，前缀 0 结果立即终止
- **缓存优化**：关键词搜索结果全量缓存 1min（空结果也缓存），筛选结果缓存 3min
- **关键速度对比**：`/api/search?q=葬送的芙利连` 从 30s → 2-6s，重复搜索 <10ms

### P0 搜索准确性优化
- **分类 key 识别**：`search("日漫")` 不再走关键词搜索，改为识别为分类 key → 走 `searchFiltered` 分类浏览（影响 Lanerc/AuvFun/Cycapp/Jinpai/Sanqiu 5 源）
- **标题相关性过滤**：查询 ≥3 字时剔除标题不含 2 字连续子串且字符重叠不足的无关条目（如"泡芙小姐"不再匹配"葬送的芙利连"）
- **相关性排序**：关键词搜索优先按标题匹配度（完全→前缀→包含→2字子串数），再按年份/多源数
- **合并后客户端二次过滤**：kind/year/genre 三个维度全部补齐 post-merge filter，防止不支持某维度的源污染结果
- **来源条数修正**：仅显示过滤后有实际匹配结果的源及真实条数

### Adapter 兜底机制补齐（对照 JS 源码逐行审计）
- **AuvFun**：Hex 32 位 key 检测（`listByTab` 替代关键词搜索）、home 数据 flatMap 去重
- **Cycapp**：纯数字 TID 检测、默认空关键词 tid 从 21→20（剧场版→TV动画）
- **Jinpai**：TAB_AREA key 检测（动漫/日本/大陆）、首页 1 专区→4 专区布局
- **Sanqiu**：TAB_AREA key 检测、`zxki.cn` 封面代理替换为 `meilinvps.com`
- **Xifanacg**：播放不发送 Referer（主线-1 联通沃盘之前遇 Referer 即 400）
- **Guazi**：详情结果 LRU 缓存（80 条，避免重复 RSA 加密调用）
- **Lanerc**：category key 缓存 + search 路由

### 片库加载体验
- 骨架屏卡片（shimmer 流光动画）+ 转圈 spinner + "加载中..." 浮层
- 筛选栏加载期间半透明 + 禁止点击

### 验证
- `npm run build` 0 error；13 JS 源全部自动加载；RSC HMR 兼容（`sync-request` 改用 `createRequire` 动态加载）

---

## 2026-07-25：播放器体验深度优化、OSD/Toast 定时修护、适配器挂载修正与分页栏排版重构

### 播放器体验与 Bug 修复 (P0)
- **播放卡顿根治**：在 `page.tsx` 中使用 `useCallback` 稳定 `setNotice` 函数引用，并在 `MediaPlayer.tsx` 中隔离外部函数依赖，彻底解决父组件重渲染导致 `hls.js` 被频繁销毁和重载的视频卡顿问题。
- **暂停按键精准响应**：将播放器单击/双击手势专门绑定在 `<video>` 视频画面本身，并给底部控制栏全量控件添加 `stopPropagation()`，消除点击暂停按键时冒泡触发背景单击导致刚暂停又被重新播放的逻辑反转 Bug。
- **OSD 提示准确性**：修复 `togglePlay()` OSD 提示反转 Bug（暂停显示`已暂停`，恢复播放显示`播放`），并优化快捷键监听依赖，防止手势监听器高频重绑。
- **底部 Toast 自动隐藏**：在 `page.tsx` 中为 `notice-toast` 增加 3.5 秒定时自动清理逻辑，解决“已续播到 02:08”等提示框常驻在屏幕下方遮挡视线的问题。
- **窗口比例与黑框匹配**：修改 `globals.css` 播放器弹窗样式，弹窗模式采用响应式 `width: min(1280px, 95vw)` 与标准 `16:9` 比例紧密贴合画面，全屏模式下保持 `100vw × 100vh` 铺满。

### 源适配器加载与路径寻找修复 (P0)
- **多源 Native / Remote Adapter 全量挂载**：在 `app/lib/adapters/index.ts` 中补齐 `nativeAdapters` 与 `remoteAdapters` 导入，优先使用高性能 TypeScript 原生适配器（包含 Lanerc、AuvFun、金牌、次元城、三秋、云帆、稀饭、咕咕等），根治了仅走通用 JS 沙盒而找不到脚本导致的 `Source file not found` 提示与系统回退。
- **JS 沙盒路径智能定位**：在 `js-engine.ts` 中实现 `findSourceDir()`，多路径自动匹配根目录下 `C:/Users/songz/Desktop/public-work/remote_sources` 脚本目录，并在 `js-source.ts` 添加安全容错。

### 页面 UI 布局修护
- **分页栏横向流式布局**：重构 `globals.css` 中 `.pagination-bar` 冲突选择器，去除多余的单列 `display: grid` 覆盖，强制恢复为横向 Flexbox 排版：`[ 首页 ] [ 上一页 ] [ 1 ] [ 2 ] [ 3 ] [ 4 ] [ 5 ] [ 下一页 ] [ 尾页 ]`。

### 验证结果
- 自动化构建验证：`npm run build` 0 错误 0 警告，全量端到端表现正常。

---

## 2026-07-24 (晚间2)：P0+P1 性能优化 + 增量目录缓存 + 播放过期自动重解析

### 性能优化（P0）
- **并发提升**：搜索 6→9，详情 3→5，首页 4→6
- **剧集截断**：detail 面板默认显示200集，超大剧集（1400+）点击"加载全部"展开
- **播放过期自动刷新**：视频 `<onError>` 自动重请求一次播放地址（`autoRetryRef`），过期URL秒级恢复
- **Edge 缓存头**：筛选结果 `public, s-maxage=120~300, stale-while-revalidate=600~3600`

### 增量目录缓存（P1）
- 新增 `app/lib/catalog-cache.ts`：启动时从9源预拉8页×20条→合并去重→内存缓存，每10分钟后台刷新
- `/api/search` 浏览路径（无关键词）直读缓存：筛选年/分类/题材 **27-147ms**（原16-30秒，快100-500倍）
- 缓存不足（<10条）自动回退深度多页搜索
- 关键词搜索超时10s→5s，response time 3-5s

### 前端优化
- 题材扩展：9→27个（热血/奇幻/战斗/穿越/后宫/恋爱/校园/日常/治愈/搞笑/悬疑/科幻/冒险/魔法/机战/推理/运动/音乐/偶像/职场/历史/美食/萌系/百合/耽美/泡面番）
- 年份动态生成：2003～{当前年} → 更早
- 分类补齐：动态漫画、特摄
- 片库分页栏显示真实总数（`total`）

### 实测
| 操作 | 优化前 | 优化后 |
|---|---|---|
| year=2022 筛选 | 16-30s | **27ms** |
| year=2025 筛选 | 5s | **147ms** |
| kind=日漫 | — | **29ms** |
| 关键词搜索 | 5s | **2-3s** |
| npm run lint | 0 errors, 34 warnings |
| npm test | 3/3 pass |

---

## 2026-07-24 (晚间)：4 源播放修复 + Jinpai 多域名容灾

### 修复
- **AuvFun `play()`**：补充 `playHeader` 中的 CDN 防盗链请求头（`User-Agent`/`Referer`/`Origin`/`Cookie`），之前只传了 referer 导致 quark.cn CDN 拒绝请求
- **Shuangxing（双星）`play()`**：三级容错 — parsers 为空时回退原始 URL → `parseUrl` 白名单过滤完所有 parser 后无限制重试 → 最终回退原始 HTTP 链接，避免解析器不可用时 502 崩溃
- **Jinpai（金牌）**：新增 `resolveHost()` 多域名轮换机制（6 个域名），探测首个可达域名后缓存使用，不再硬编码单域名

### 全量验证
- 13 源全部测试 search→detail→play 端到端：9 个启用源全部返回可播放 URL，4 个禁用源保持禁用
- `npm run lint` 0 error；`npm test` 3/3 通过

---

## 2026-07-24：13 源全量 Native Adapter 落地 + 播放代理修复 + 元数据增强 + 片库筛选重构

### 新增来源适配器（5 个从 JS Worker 转为 Native）
- **瓜子 GuaziAdapter**：RSA PKCS1 + AES-128-CBC + MD5 签名 + token 注册/刷新，多域名轮换
- **双星 ShuangxingAdapter**：AES-256-CBC 随机 IV + zlib inflate + sha256 签名 + 设备注册登录
- **动漫巴士 DmbusAdapter**：HTML 爬取 + hhjx 播放器 OKOK 密钥解码
- **路漫漫 Lmm85Adapter**：HTML 爬取 + smart_token 反采集 + player_aaaa 直链解析
- **AkiAnime AkiAnimeAdapter**：ds_api JSON POST + bgmsearch HTML + 外部解析器

### 已有适配器修复
- **Cycapp（次元城）**：UA 补全 `Chrome/128.0.6613.36 Electron/32.0.1`（之前返回 401）
- **Jinpai（金牌）**：时间戳从秒改为毫秒（之前返回 `code:122008`）；年份提取增加 `vodPubdate` 兜底
- **Lanerc/AuvFun/Jinpai 播放策略**：从代理改为浏览器直连，绕开 CDN 对服务器 IP 的 TLS 指纹封锁

### 播放代理 (`/api/proxy/stream`)
- `rewriteM3u8` 传递 `ua` 参数到每个 TS 分片 URL（瓜子 CDN 要求 Lavf UA）
- 移除自动 `Origin` 头（避免 moedot.net 403）；403/400 自动无 Referer 重试
- 网络错误自动重试；AuvFun 播放缓存设为 0（quark auth_key 秒级过期）
- 动态转发用户浏览器指纹：`sec-ch-ua`、`sec-ch-ua-platform`、`sec-fetch-*`、`Accept-Language`

### 封面代理 (`/api/cover`)
- doubanio 418 自动去除 Sec-Fetch 头重试

### 数据模型增强
- `SourceItem` 新增 `tags: string[]`、`status: string`
- `item()` 函数多字段兜底提取：年份（year/vod_year/vodYear/vodPubdate）、标签（type/vod_class/vodArea）、状态（remarks/vodRemarks/serialDesc）
- `mergeSearchItems` 跨源年份合并：无年份作品自动匹配已有年份的同名作品
- `mergeSearchItems` / `mergeDetails`：tags/status 跨源聚合
- 详情页返回年评/标签/状态

### 片库筛选重构
- 搜索与筛选分离：首页搜索框，片库只做筛选（`fetchLibraryWith` 不再发送 `q`）
- 新增状态筛选行（连载中/已完结）+ 题材扩展至 27 个标签
- 题材筛选：严格匹配 `kind + tags[]`，标题含词不算（如"疑似后宫"不会被归为后宫）
- 年份筛选走服务端关键词搜索兜底

### 排序稳定性
- 四级 tiebreaker：主键 → 二级 → 标题(zh-CN) → canonicalId
- 先合并再排序：sourceCount 使用合并后的真实多源覆盖数
- score 排序降级为 hot（无源提供真实评分）
- 首页跨源补充年份/分类/状态

### 性能优化
- `/api/home` 从串行 `for...of` 改为 `mapWithConcurrency` 并行（39s → 12s）
- 搜索结果 < 20 条不缓存（`Cache-Control: no-cache` + `cachedConditional` threshold=20）
- 请求日志写入 `logs/search-requests.log`

### 源状态更新
- 可用 9 源：Lanerc, AuvFun, 次元城, 金牌, 瓜子, 双星, 稀饭动漫, 咕咕, 三秋
- 禁用 4 源：云帆（假流）、动漫巴士（522）、路漫漫（CF）、AkiAnime（DNS 封锁）
- 全部 runtime → `"native"`，`source-manifests.json` 同步更新

### 验证结果
- `npm test` 3/3 通过；9 源 search→detail→play 全链路均可解析播放 URL
- 5 源代理 200（cycapp/guazi/shuangxing/xifanacg/gugu）+ 4 源直连

---

## 2026-07-23：前端交互重构、React Key 规范与播放器容错增强

- **React Key 冲突排查与消除**：规范全站组件（`page.tsx`、`MediaPlayer.tsx`）在列表 `map` 时的 Key 绑定，在所有渲染节点采用组合唯一键（`${item.sourceKey}-${item.id}-${index}`），彻底解决 `yzx-dm` 等重名 Key 的警告问题。
- **“查看全部 / 查看更多”与分类筛选重构**：点击首页分区的“查看更多”或顶部“查看全部”时，支持直达专区并激活分类 tag 筛选；在片库（Library）视图增加全套分类 Filter Pills（包含热门推荐、国漫、日漫、科幻、奇幻、剧情、悬疑等）与一键清空重置，增强可交互性。
- **播放器 `NotSupportedError` 安全容错**：在 `MediaPlayer.tsx` 中拦截与包裹 `video.play()` 的 Promise 异常与 `activeUrl` 前置防空校验；发生解码受阻或受设备限制时自动转换组件内 `mediaError`，避免 Uncaught Promise Rejection 影响控制台，并友善弹框引导用户【换源】。
- **海报 Cover 封面增强**：优化 `/api/cover` 图片防盗链代理头；`Cover` 组件优先处理 `http:` 资源代理，代理失败时渲染深色炫彩渐变占位卡片（含分类 Tag 与高清首字缩写），消除缺图的不良视觉感。
- **AGENTS.md 架构约束对齐**：通过 `npm run lint`（0 error）与 `npm test`（Build 成功、契约测试通过）。

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

## 2026-07-29：Lanerc 远程榜单/周表脚本取证（后续原生移植）

- 从已启动的 Lanerc v1.0.6（实际包名 `com.clggjv.xcjfmd.ffo`）读取安装 APK，确认当前 Flutter 版本二进制只保留 `/app/config`、`/app/rank`、`/app/week` 接口字符串；没有把 `ranking.js` 或 `calendar.js` 打进 APK。
- 解密旧版远程配置后确认真实代码地址为 `lanerc_rank.js` 与 `lanerc_week.js`，脚本保存于 `C:\Users\songz\Desktop\public-work\lanerc_analysis\remote_scripts\`，仅作为逆向参考样本。
- `lanerc_rank.js`：请求 `/app/rank`，解码自定义 Base64 字母表和 AES-256-ECB/PKCS5；后端返回季度分组，三个 tab 固定映射第 0/1/2 组（热门/人气/评分），评分 tab 并不按分数重新排序；每页 30 条，结果 memo 10 分钟。
- `lanerc_week.js`：请求 `/app/week`，一次返回 `week_list.mon..sun`；从 `vod_total` 取集数，从 `vod_remarks` 正则提取 `HH:mm`，整周 memo 10 分钟，`calendarDay()` 复用同一份缓存。
- 两个脚本会对豆瓣封面附加 Referer 和浏览器 User-Agent，且主站失败后才尝试发现域名。由于脚本依赖第三方后端、加密密钥和未审计远程执行环境，本项目未直接执行或嵌入脚本；Android 仅移植其“真实字段优先、失败保持空状态、缓存有 TTL”的可验证行为。
