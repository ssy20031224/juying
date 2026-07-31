# 囧次元 1.5.8.0 互操作分析摘要

分析对象：实体机已安装包 `com.tudou.tool`，版本号 `1.5.8.0`（84）。

## 已确认的公开结构

- 客户端为 Flutter AOT 应用，首页顶级频道为：推荐、日漫、国漫、动漫电影、其他动漫。
- 顶级/子频道入口：`/app/channel/?top-level=true`、`/app/channel/`。
- 列表、搜索、详情入口：`/app/video/list`、`/app/video/search`、`/app/video/detail`。
- 剧集/播放流程包含：`/app/video/play`，最终播放地址另经 `/app/playaddr/v4/client` 解析。
- 排期/更新时间入口：`/app/video_update_list/`。
- 数据模型中可确认 `area`、`year`、`status`、`score`、`total`、`page`、`limit`、`offset`、
  `vod_name`、`vod_pic`、`desc` 等字段，以及 `GTimeLineItem`、`GVideoDetailItem`、
  `GVideoSourceItem`、`GChannelItem`。
- 首页卡片直接展示服务端时间线信息，例如“5｜周五 01:28 更”。这说明“更新到几集”和
  “星期几几点更新”来自排期/时间线数据，不应仅由播放列表长度猜测。
- 相关推荐没有发现独立的 `related` 路径；从客户端模型和请求入口判断，较可能由详情响应中的
  推荐数据或同频道列表生成。这一点是推断，并非已验证协议。

## 实机抓包确认的调用顺序

本次抓包仅记录接口结构，不保存或复用认证头、设备标识和加密响应内容。

1. 启动时请求升级、视频配置、顶级频道、横幅和公告。
2. 首页的四个主体分区分别请求：
   - `channel=1&sort=weight&limit=6&page=1`
   - `channel=2&sort=weight&limit=6&page=1`
   - `channel=3&sort=weight&limit=6&page=1`
   - `channel=26&sort=weight&limit=6&page=1`
3. 热门列表使用 `channel=0&sort=hits&limit=6&page=1`，说明热门是服务端按点击/热度字段排序，
   不是客户端随机抽取。
4. 搜索先调用 `/app/video/key` 获取关键词候选，再用完整标题调用 `/app/video/search`。
5. 进入目标详情后按顺序调用 `/app/video/detail?id=113165`、同频道最新列表
   `channel=2&sort=addtime&limit=10&page=1`，后者很可能就是详情页“热门推荐/相关推荐”的候选池。
6. 播放第 1 集时调用 `/app/video/play-connect`，随后调用
   `/app/video/play?id=113165&play=mp4&part=第1集`，弹幕后续按时间窗口分段请求。

目标《刚毕业就末日：万亿开局当神豪·动态漫》的详情页实测返回 2025 年、大陆、奇幻/搞笑、
评分 9.0、更新至 333 集并标注“每日 10:00 更”。这再次证明状态、已更新集数和更新时间应作为
三个独立字段处理，不能由标题或剧集数组长度互相推断。

## 第二轮分类、排期与播放抓包

### 频道与排序

| 客户端栏目 | `channel` |
| --- | ---: |
| 全部/热门聚合 | 0 |
| 日漫 | 1 |
| 国漫 | 2 |
| 动漫电影 | 3 |
| 其他动漫 | 26 |

列表统一使用 `/app/video/list`，已实测的排序映射为：

| 界面排序 | `sort` |
| --- | --- |
| 最新 | `addtime` |
| 最热 | `hits` |
| 高分 | `gold` |

完整列表参数形式为：

```text
/app/video/list?channel={频道}&type={题材}&area={地区}&year={年份}&sort={排序}&limit=30&page={页码}
```

题材、地区和年份都由 `/app/channel/{频道}` 返回的筛选配置驱动，不应硬编码为标题关键词。
日漫频道实测包含日本/其他地区以及搞笑、经典、热血、催泪、治愈、猎奇、励志、战斗、后宫、
机战、恋爱、百合、科幻、奇幻等题材。

### 排期表

排期表按北京时间日期请求：

```text
/app/video_update_list/{yyyy-MM-dd}?limit=500&page=1
```

客户端切换星期时逐日加载对应日期，而不是从首页列表推算。抓包覆盖了
2026-07-31 至 2026-08-09。排期结果应保留视频 ID、当前集数、更新时间和状态字段，再与详情
可播剧集交叉校验。

### 详情、相关推荐与播放

- 详情：`/app/video/detail?id={videoId}`
- 相关推荐候选：`/app/video/list?channel={当前频道}&sort=addtime&limit=10&page=1`
- 播放连接：`POST /app/video/play-connect`
- 剧集播放：`POST /app/video/play?id={videoId}&play={线路}&part={剧集名}`
- 弹幕按 60 秒窗口请求 `/app/danmu`

本轮实际播放了 ID `113230` 与 `113282`。过滤广告后，真实媒体流量落在
`v3.toutiaovod.com` 和 `v26.douyinvod.com`；它们是播放接口返回后的媒体 CDN，并不是可用于
搜索、详情或选集的业务 API。清晰度切换仍由加密的播放响应决定，网络层只能确认媒体域名和
流量变化，不能从 TLS 密文可靠还原清晰度名称与每档 URL。切换清晰度时没有出现新的业务 API
路径，仅出现新的媒体 TLS 连接，因此各档地址很可能已经包含在加密播放响应或主播放列表中。

### 已过滤的广告与遥测

以下流量未纳入业务协议：

- 快手广告素材域名 `*.adukwai.com` 与 `open.e.kuaishou.com`
- Sigmob 的 `tm.sigmob.cn`、`dc.sigmob.cn`
- 广告联盟的 `/api/v1/league/ad/getAd`、`/heartbeat`、`/report`
- `qhimg.com`、爱奇艺图片 CDN、Sina 图片 CDN 等卡片/广告图片
- 崩溃上报、日志、公告任务和设备统计接口

过滤依据同时使用目标应用、请求路径、域名用途、发起时间和上下行流量特征，不能只按“大流量”
判断，否则快手视频广告会被误认成正片。

## 可借鉴的数据规则

1. 频道决定日漫、国漫、剧场版等大类，题材标签只用于二级筛选，不能用标题关键词代替频道。
2. 排期表使用 `video_update_list` 的更新时间线；实际可播集数仍以详情/播放源返回的剧集为准。
3. 状态优先级应为：明确完结标记 > 待播 > 更新中/连载中 > 未知。裸集数不能证明仍在连载。
4. 首页推荐、列表、排期和详情应共享稳定的视频 ID；标题仅作为跨来源恢复手段。
5. 播放源解析失败时应保留来源名称和候选来源，不能把整个来源选择区清空。

## 未移植的部分

该应用的当前业务域名由运行时配置获得，请求包含 `timestamp`、`nonce`、`sign`、设备标识和
认证流程；实机抓包中的频道、搜索、详情和播放响应也都是加密文本。最终取流还使用单独的播放
地址服务。静态 APK 和网络流量都无法证明这些接口是可复用的公开 API。
因此本项目没有复制其私有签名、密钥、账号鉴权或播放解析协议，也没有制作依赖其私有后端的
转载源脚本。若对方提供公开 API 文档和授权，可按本项目 `SourceAdapter` 的
`homeSections/search/detail/related/play` 契约实现独立适配器。
