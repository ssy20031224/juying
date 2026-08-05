# 项目变更记录

## 2026-08-05：登录密码可见、评分空态、时间格式与分类页优化

- 登录/注册/找回密码弹窗的密码与确认密码输入框右侧新增小眼睛切换按钮（👁/🙈），点击后明文显示密码，再点恢复掩码。
- 排行榜与领奖台不再显示“来源未提供评分”：没有有效评分时评分行整体隐藏，不占位。
- 消息通知与评论区时间显示统一：2 天内（今天/昨天/前天）保留相对时间；超过 2 天显示 `MM/dd HH:mm`（如 `08/02 18:32`）；非今年消息加年份前缀（如 `2025/08/02 18:32`），统一按北京时间格式化。
- 动漫分类排行榜移除“日漫剧场版”“国漫剧场版”，只保留“日漫TV番剧”与“国漫动画”；同步删除 `AnimeRankingCategory` 剧场版枚举、Lanerc `class=22` 剧场版抓取与关键词搜索分支。
- 首页分类页签调整为“精选 | 日漫 | 国漫 | 剧场版”，新增“国漫”；“日漫”“国漫”“剧场版”各自进入独立分类首页：轮播图只展示当前分类作品（角标显示分类名）、下方 3x3 热门推荐网格（9 部）、推荐栏右侧“查看更多”进入对应分类片库。
- 验证：Android `:app:compileDebugKotlin`、`:app:testDebugUnitTest`（34/34）、`:app:assembleDebug` 通过，`git diff --check` 无错误。


## 2026-08-02：注册登录与验证码限流修复

- 修复 Android 登录邮箱错误沿用本地历史值、注册昵称和邮箱预填的问题；登录、注册、找回密码切换时均使用空白表单。
- 注册和找回密码增加两次密码不一致的前端红色提示；注册必须主动填写昵称。
- 验证码发送成功后显示 60 秒倒计时并禁用重复发送；服务端同步返回 HTTP 429 与 `Retry-After`，避免绕过客户端滥用邮件接口。
- 修复 Workers Free 10ms CPU 限额下 PBKDF2 导致验证码已消耗但注册/登录 HTTP 500 的问题；密码改为随机盐 HMAC-SHA256 并叠加独立服务端 Pepper，注册与找回密码均在消耗验证码前完成密码签名。

## 2026-08-02：账户设置、云端头像、评论与反馈闭环

- Android 默认账号 API 切换为已上线的 `https://api.songxiang.online`，登录、注册、验证码、头像、评论和同步不再误连旧域名。
- 验证码邮件升级为聚映品牌化中文模板，按注册、修改邮箱和重置密码显示不同说明，并突出 10 分钟有效期与防诈骗提示。
- 个人页头像改为点击相册上传阿里云 OSS，移除可见的本地预设头像和“本地使用模式”横栏；页首改为“头像 + 昵称/用户名 + 普通用户 + 设置入口”。
- 设置页按外观、账户和通用功能分组；修改密码、修改邮箱拆为独立页面，修改密码新增原密码校验接口。
- 评论改为服务端确认成功后才上屏，失败保留草稿并显示真实错误；新增 D1 反馈表和登录后建议反馈入口。
- 新增免责声明与系统分享入口；分享内容指向聚映最新发布页。
- 验证：根项目 ESLint 无错误、`npm test` 3/3 通过；Android `:app:testDebugUnitTest`、`:app:assembleDebug` 通过。D1 反馈表已应用到生产库，Worker 已部署，自定义域名的账号、评论、修改密码和反馈路由均返回预期响应。

## 2026-08-02：低成本 D1 账号后端对齐

- 将 Cloudflare Worker + D1 确认为初期账号主方案，复用现有用户、会话、验证码、收藏、播放进度、设备缓存索引和评论表，不要求购买 RDS。
- D1 同步接口支持 Android 的 `replaceDeviceCache` 协议，仅替换当前设备的缓存索引，不误删同一账号其他设备的数据。
- 验证码哈希不再使用开发期默认 Pepper；生产环境缺少至少 32 字符的 `AUTH_CODE_PEPPER` 时拒绝发信，避免生成不可安全校验的验证码。
- 新增根目录 `.env.example`，集中列出 D1 账号、阿里云 DirectMail 与 OSS 所需配置。

## 2026-08-01：稳定分类榜、账号云同步、可配置更新说明与公告

- 动漫分类榜不再拼接首页逐源加载中的可变列表；切换分类只读已完成快照，用户点击右上角刷新后才重新请求，并在全部来源搜索/详情评分补全结束后一次性提交。
- 评分只接受来源或标题精确匹配的动漫资料库返回的真实数值；缺失时明确显示“来源未提供评分”。国漫剧场版通过全部启用来源的精确搜索、详情与公开动漫元数据交叉分类补齐，不再把检索词直接当作分类证据；ADB 已验证《中国惊奇先生大电影》等真实条目与评分且静置不重排。
- 恢复 Android 登录、注册、验证码、昵称、邮箱、密码重置和自定义头像入口；阿里云服务默认启用账号与登录后评论写入。历史、追番及离线缓存索引可同步，服务端仍不接收视频文件、本地路径或临时播放地址。
- 远端来源脚本优先从可配置阿里云 OSS 目录热更新，保留原 CDN 与 APK 内置脚本回退；脚本下载、缓存和播放器解析状态继续隔离。
- 更新检查会读取全部清单并按版本号、`manifestRevision` 与发布者 URL 优先级选择，不再被第一个旧镜像固定文案覆盖；手动检查使用毫秒级缓存破除参数。
- 新增 OSS 驱动公告接口、启动公告弹窗和首页轮播下方公告条，支持今日内或 7 天内不再弹出。
- 验证：Android `:app:compileDebugKotlin`、`:app:testDebugUnitTest`、`:app:assembleDebug` 通过；`aliyun-api` 语法检查和 2 项安全测试通过。

## 2026-08-01：Android Anime4K、拖动预览、分类榜与搜索推荐

- 画质增强升级为 Media3 GPU 帧管线承载的 Anime4K 风格 GLSL：提供关闭、性能优先和高质量三档，执行纹理放大、边缘检测与动画线条恢复，并明确提示持续 GPU 占用、发热、耗电、掉帧风险及“并非真正 4K 片源”。
- 画质模式切换会保留媒体项、播放进度和播放/暂停状态后重建帧处理管线；GPU/驱动不兼容时自动关闭增强并恢复播放，不触发数据源换源，避免实验功能造成播放失败。
- 横向拖动进度时在播放器顶栏下方显示目标关键帧缩略图及“目标时间 / 总时长”，拖动方向锁定继续与亮度、音量手势互斥；缩略帧提取限频并在后台线程执行。
- 动漫分类排行榜改用 Lanerc 的明确目录分类：`class=20` 日漫 TV、`class=22` 日漫剧场版、`class=24` 国漫；国漫再按剧场版证据拆分。分类器拒绝真人日剧及中日地域冲突，不再用搜索词给稀疏条目强行贴分类。
- 搜索历史持久化搜索次数和最后搜索时间，搜索达到 3 次显示“常搜”；“猜你想看”综合最近/常搜词、观看历史、标题与标签相关度排序，无历史时使用真实候选池稳定轮换。
- 验证：Android 全量 Debug 单测和 `:app:assembleDebug` 通过；ADB 覆盖安装验证了独立搜索页、“常搜”标签、个性化猜你想看、日漫 TV/剧场版榜，以及 Anime4K 性能档在播放中启用后继续播放且无帧处理错误。

---

## 2026-07-31：Android 相关推荐返回栈与画中画生命周期修复

- 播放页从“相关推荐”继续打开作品时保存当前详情、选集、播放结果、换源列表和推荐列表快照；系统返回手势与播放器返回键会逐级恢复上一部作品，不再把返回目标错误写成 `player` 而卡在当前界面。
- 手动画中画统一通过 `MainActivity` 携带系统 `RemoteAction` 参数进入，小窗中央提供上一集、播放/暂停、下一集；系统自带展开按钮继续位于小窗右下区域。
- 画中画布局不再按普通自然横屏渲染“播放器 2/3 + 侧栏 1/3”，小窗只保留视频画面；小窗切换上下集导致播放器重建时仍保留 PiP 会话与展示恢复信息。
- 记录进入小窗前的显式全屏状态；从小窗展开时恢复原来的竖屏/全屏展示及系统栏状态。
- 删除 `onUserLeaveHint` 自动进入画中画行为。按 Home、最近任务或切换应用只进入普通后台，并在 Activity `ON_STOP` 且系统确认不在 PiP 时暂停播放；曾经手动使用过 PiP 也不会让后续后台操作再次自动弹出小窗。
- 新增 PiP 禁用横屏侧栏、真实 PiP/普通后台暂停分流的单元测试。
- 验证：31 项 Android 单元测试、`:app:assembleDebug`、`:app:lintDebug`、根项目 `npm run lint`（仅既有 warning）与 `npm test`（3/3）通过；Debug APK 已在 Android 模拟器覆盖安装并启动。

---

## 2026-07-31：Android 详情稳定性、搜索、记录与GPU锐化增强

- “追番”改为直接观察 `favoritesList` 的 Compose 状态，收藏成功后当前播放器页面立即切换为“已追番”，无需退出重进。
- 详情加载新增可取消的请求代次：旧作品迟到的空详情/空播放源不能覆盖新作品；来源内 ID 无效或聚合错位时，会在同一来源按规范化标题重新搜索详情，再并发尝试其他启用来源。
- 播放器亮度/音量与进度手势在超过阈值后锁定方向；纵向手势成立后，即使手指后续横向偏移也不会触发 Seek。
- 原“硬件画质增强”升级为真实 Media3 OpenGL 边缘锐化着色器，并辅以轻量对比度/饱和度处理；UI 明确标注其为 GPU 实时增强而非冒充厂商私有 NPU 模型的 AI 4K 超分。
- 日漫/国漫剧场版排行榜会按分类关键词并发搜索已启用来源，再结合原有来源榜和首页分区去重，修复仅靠稀疏 `kind/tags` 导致剧场版空榜的问题。
- 首页搜索框改为进入独立搜索页：支持返回、输入法回车搜索、搜索按钮、可单删/清空的历史搜索，以及来自真实热门榜/季度榜/首页内容且“换一批”不重复的热门搜索。
- 观看历史持久化播放位置与时长，按近一周、近一月、近半年、更早分组；卡片展示海报、剧集、百分比、北京时间与浅蓝进度条，并支持左滑单删。我的追番按最近观看区间分组并复用观看进度。

---

## 2026-07-30：Android 当年季度新番榜、排期与刷新反馈

- 首页“季度新番榜”“季度排期表”改为两个独立页面，按北京时间显示当前季度与同一年内的上一季度；例如 2026-07-30 只显示 2026 年 7 月与 4 月，不提前展示 10 月，也不混入 2025 年 10 月。
- 当 `/app/rank` 尚未发布当前季度分组时，只用“当前年份片库 ∩ 实时周表”，并排除已经明确归属上一季度的标题来补建当前季度；使用首页真实热门顺序优先排序，不伪造热度或评分。
- 底部“周表”恢复为本周更新番剧，不受季度筛选影响；独立季度排期页按季度展示整季片单，当前周表有明确资料时附带周几、时间和集数。
- 底部“排行榜”按“日漫TV、日漫剧场版、国漫、国漫剧场版”分类；首页季度新番榜不再跳转到该排行榜。
- 排行榜前三名使用等宽领奖台并统一标题区、评分区的高度和对齐，无有效评分时明确显示“暂无评分”。
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
