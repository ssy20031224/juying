/*
 * 路漫漫在线动漫（lmm85） JS 源
 * 站点：https://www.lmm85.com  （苹果CMS V10 · jable 模板）
 * version: 1.2.2  （修"嗅探超时 cands=0"：sniff 加 autoPlay:true —— jable 模板播放器要点海报才起播，
 *                  不开 autoPlay 起播脚本不注入、播放器永不发 m3u8 请求、嗅探纯被动监听必超时。
 *                  配合 App 端 JsSniffer 注入脚本新增「直读 window.player_aaaa.url」兜底，
 *                  即使起播按钮没点中，也能从苹果 CMS 解码后的全局变量直接拿 m3u8）
 * version: 1.2.1  （修"嗅探不到"：站点全站开 Cloudflare 盾后，嗅探不再伪装桌面 UA（UA 与 WebView
 *                  指纹不一致会被 CF 判伪装、挑战永远过不去），timeout 15s→20s 给挑战自解留时间；
 *                  需配合 App 端 JsSniffer 修复——主文档 403/503 不再快退）
 *
 * 说明：
 *  - 首页/分类/详情/播放页都是普通 GET，可直接抓取解析。
 *  - 关键词搜索 /vod/search 被站点的 smart_token 反采集脚本（jsjiami 混淆）拦"身份验证"页。
 *    本源还原了算法：token = md5(unix秒 + SALT)，POST /index.php/ajax/smart_verify 通过后，
 *    同一 Cookie 会话内搜索直接放行。SALT 轮换导致失效时，自动退化为"客户端搜索"
 *    （抓最近更新/番剧/电影列表按片名过滤）。可用 ext.smartSalt 覆盖 SALT。
 *  - 站点开了 Cloudflare 盾（managed challenge）：真实浏览器可自动过非交互挑战；OkHttp/curl
 *    这类非浏览器 TLS 指纹基本必被拦成 "Just a moment" 403（与 IP 关系不大）。所以 direct
 *    直链路径被挑战时会失败（isBlocked 识别后返回空），自动退化到 sniff 嗅探兜底——WebView
 *    是真 Chromium，保持默认 UA 时能自己把挑战跑过去，拿到 cf_clearance 后续就畅通。
 *  - 取流方式按"线路（sid）"配置，见下方 PLAY_MODE / DEFAULT_PLAY_MODE：
 *      'direct' 只静态抓 player_aaaa 直链；'sniff' 只跑 WebView 嗅探；'auto' 先直链失败再嗅探。
 *    解决"同一站点不同线路取流方式不同"——A 线路是直链、B 线路必须嗅探时分别指定即可。
 *    可被后台 ext.playMode 覆盖（{"1":"direct","2":"sniff"} 这种 {线路sid:模式} 形式）。
 */

var SITE = (function () {
    var s = (typeof ext !== 'undefined' && ext && ext.site) ? String(ext.site) : 'https://www.lmm85.com';
    return s.replace(/\/+$/, '');
})();

// 统一请求头：普通桌面 Chrome UA + 站内 Referer，超时 15s。
var REQ_OPTS = JSON.stringify({
    ua: 'chrome',
    timeout: 15000,
    headers: { 'Referer': SITE + '/' }
});

// 关键词搜索的 smart_token 反采集：
//   token = md5(ts + SALT)，POST 到 VERIFY_PATH，返回 {"code":1} 即本会话验证通过，
//   之后同一 Cookie 会话内的搜索请求直接放行（站点用 PHPSESSID 记验证状态）。
//   SALT 来自站点验证脚本（jsjiami 混淆，按日期轮换）。轮换后这里失效会自动退化为客户端搜索。
var SMART_SALT  = (typeof ext !== 'undefined' && ext && ext.smartSalt) ? String(ext.smartSalt) : 'Lmm2026@VipS3cr3t!Kx9PqZ';
var VERIFY_PATH = '/index.php/ajax/smart_verify';

// ─── 取流方式（按线路 sid 配置）──────────────────────────────────────
//   play 的 flag 形如 "id_sid_nid"，其中 sid = 线路序号（与详情页线路 tab 顺序一致）。
//   每条线路可单独指定取流方式，解决"同源不同线路：A 直链 / B 必须嗅探"：
//     'direct' 只静态解析 player_aaaa 直链（最快，地址即真实 m3u8 时用）
//     'sniff'  只跑 WebView 嗅探（直链是网页/二次生成/需播放器跑 JS 时用）
//     'auto'   先直链，拿不到再嗅探兜底（默认，最稳）
//   未在 PLAY_MODE 里列出的线路一律走 DEFAULT_PLAY_MODE。
//   后台可用 ext.playMode 覆盖，形如 {"1":"direct","2":"sniff"}（键=线路sid，值=模式）。
var DEFAULT_PLAY_MODE = 'auto';
var PLAY_MODE = (function () {
    // 默认全 auto；确认某线路固定走某方式时在这里按 sid 写死，例如：
    //   var base = { '1': 'direct', '2': 'sniff' };
    var base = {};
    try {
        if (typeof ext !== 'undefined' && ext && ext.playMode) {
            var ov = (typeof ext.playMode === 'string') ? (parseJson(ext.playMode) || {}) : ext.playMode;
            for (var k in ov) { if (ov.hasOwnProperty(k)) base[String(k)] = String(ov[k]).toLowerCase(); }
        }
    } catch (e) { /* ext.playMode 解析失败就用默认 */ }
    return base;
})();

// 分类名 → 站点路径。type/* 是分类页，label/* 是标签聚合页（两者翻页规则不同）。
var TYPE_MAP = {
    '番剧':       'type/dongman',
    '日本动漫':   'type/ribendongman',
    '国产动漫':   'type/guochandongman',
    '欧美动漫':   'type/oumeidongman',
    '动态漫画':   'type/dongtaiman',
    '动画电影':   'type/dianying',
    '日本特摄剧': 'type/teshepian',
    '最近更新':   'label/new',
    '热门':       'label/hot'
};

// ───────────────────────────────────────────── 工具函数

function trim(s) { return s == null ? '' : String(s).replace(/^\s+|\s+$/g, ''); }

function decodeEntities(s) {
    if (!s) return '';
    return s.replace(/&amp;/g, '&')
            .replace(/&lt;/g, '<')
            .replace(/&gt;/g, '>')
            .replace(/&quot;/g, '"')
            .replace(/&#0?39;/g, "'")
            .replace(/&apos;/g, "'")
            .replace(/&nbsp;/g, ' ');
}

function stripTags(s) {
    if (!s) return '';
    return s.replace(/<[^>]+>/g, '');
}

// 识别 Cloudflare 挑战页 / 站点"身份验证"反采集页。
function isBlocked(html) {
    if (!html) return false;
    if (html.indexOf('_cf_chl_opt') >= 0) return true;
    if (html.indexOf('Just a moment') >= 0) return true;
    if (html.indexOf('challenge-platform') >= 0) return true;
    if (html.indexOf('身份验证') >= 0 && html.indexOf('请稍候') >= 0) return true;
    // smart_token 验证页特征：标题"身份验证" + 体积极小
    if (html.indexOf('<title>身份验证') >= 0) return true;
    return false;
}

function fetchHtml(url) {
    var html = request(url, REQ_OPTS);
    if (!html) { log('[lmm85] empty body: ' + url); return ''; }
    if (isBlocked(html)) { log('[lmm85] blocked: ' + url); return ''; }
    return html;
}

// 列表/标签页的翻页 URL。
//   type/xxx     → 第1页 /type/xxx.html         第N页 /type/xxx_N.html
//   label/xxx    → 第1页 /label/xxx.html        第N页 /label/xxx/page/N.html
function pagedUrl(path, page) {
    page = page || 1;
    if (path.indexOf('label/') === 0) {
        return SITE + '/' + path + (page > 1 ? '/page/' + page : '') + '.html';
    }
    return SITE + '/' + path + (page > 1 ? '_' + page : '') + '.html';
}

// 解析列表页里的影片卡片。按 "img-box cover-md" 切块，逐卡解析避免跨卡串数据。
function parseCards(html, typeName) {
    var out = [];
    if (!html) return out;
    var chunks = html.split('img-box cover-md');
    for (var i = 1; i < chunks.length; i++) {
        var c = chunks[i];
        var id = match(c, '/detail/(\\d+)\\.html', 1);
        if (!id) continue;
        var pic = match(c, 'data-src="([^"]+)"', 1) || match(c, '<img[^>]+src="([^"]+)"', 1) || '';
        var remark = match(c, '<span class="label">([^<]*)</span>', 1) || '';
        var name = match(c, '<h6 class="title">\\s*<a[^>]*>([^<]+)</a>', 1) || '';
        name = decodeEntities(trim(name));
        if (!name) continue;
        out.push({
            id: id,
            name: name,
            pic: pic,
            type: typeName || '',
            year: '',
            remarks: trim(decodeEntities(remark)),
            desc: ''
        });
    }
    return out;
}

function listFromPath(path, page, typeName) {
    var html = fetchHtml(pagedUrl(path, page));
    return parseCards(html, typeName);
}

// type/* 分类下返回的条目，type 字段填分类名以便前端筛选；label/* 是混合内容，留空。
function typeNameFor(key) {
    var path = TYPE_MAP[key] || '';
    return path.indexOf('type/') === 0 ? key : '';
}

// ───────────────────────────────────────────── 首页分区（可选）

function homeSections() {
    var defs = [
        { title: '最近更新', key: '最近更新', path: 'label/new',            type: '' },
        { title: '热门影片', key: '热门',     path: 'label/hot',            type: '' },
        { title: '日本动漫', key: '日本动漫', path: 'type/ribendongman',    type: '日本动漫' },
        { title: '国产动漫', key: '国产动漫', path: 'type/guochandongman',  type: '国产动漫' },
        { title: '欧美动漫', key: '欧美动漫', path: 'type/oumeidongman',    type: '欧美动漫' },
        { title: '动画电影', key: '动画电影', path: 'type/dianying',        type: '动画电影' }
    ];
    var out = [];
    for (var i = 0; i < defs.length; i++) {
        var d = defs[i];
        var items = listFromPath(d.path, 1, d.type);
        if (items.length) out.push({ title: d.title, key: d.key, items: items.slice(0, 12) });
    }
    return JSON.stringify(out);
}

// ───────────────────────────────────────────── 分类 tab（可选）

function categories() {
    return JSON.stringify([
        { key: '',         title: '推荐' },
        { key: '最近更新', title: '最近更新' },
        { key: '日本动漫', title: '日本动漫' },
        { key: '国产动漫', title: '国产动漫' },
        { key: '欧美动漫', title: '欧美动漫' },
        { key: '动态漫画', title: '动态漫画' },
        { key: '动画电影', title: '动画电影' },
        { key: '热门',     title: '热门' }
    ]);
}

// ───────────────────────────────────────────── 搜索 / 分类

function search(keyword, page) {
    page = page || 1;
    var key = trim(keyword || '');

    // 空关键词 = 推荐，用"最近更新"作为默认片库
    if (!key) return JSON.stringify(listFromPath('label/new', page, ''));

    // 命中内置分类 → 直接抓对应分类页
    if (TYPE_MAP[key]) {
        return JSON.stringify(listFromPath(TYPE_MAP[key], page, typeNameFor(key)));
    }

    // 真实关键词：先走服务端搜索（自动过 smart_token），不可用再退化客户端搜索
    var arr = searchServer(key, page);
    if (arr !== null) return JSON.stringify(arr);
    return JSON.stringify(clientSearch(key, page));
}

// 计算并提交 smart_token，过站点搜索反采集。成功返回 true（本会话标记已验证）。
function solveSmartVerify(key) {
    try {
        var ts = Math.floor(timestamp() / 1000);
        var token = md5(ts + SMART_SALT);
        var resp = post(SITE + VERIFY_PATH, 'smart_token=' + token + '&ts=' + ts, JSON.stringify({
            ua: 'chrome',
            headers: {
                'X-Requested-With': 'XMLHttpRequest',
                'Content-Type': 'application/x-www-form-urlencoded',
                'Referer': SITE + '/vod/search.html?wd=' + encodeUri(key || '')
            }
        }));
        log('[lmm85] smart_verify: ' + resp);
        if (resp && resp.indexOf('"code":1') >= 0) return true;
    } catch (e) { log('[lmm85] smart_verify err: ' + e); }
    return false;
}

// 服务端关键词搜索。返回条目数组（可能为空=确实无结果）；被拦截且无法过验证时返回 null。
function searchServer(key, page) {
    page = page || 1;
    var url = SITE + '/vod/search/page/' + page + '/wd/' + encodeUri(key) + '.html';
    var html = request(url, REQ_OPTS);
    if (html && isBlocked(html)) {
        if (solveSmartVerify(key)) html = request(url, REQ_OPTS);
    }
    if (!html || isBlocked(html)) return null;
    return parseCards(html, '');
}

// 客户端搜索：抓最近更新/番剧/电影列表，按片名包含关键词过滤、按 id 去重。
// 不是全站搜索，但无需破解反采集脚本，永不失效。
function clientSearch(key, page) {
    if (page && page > 1) return [];          // 结果一次性返回，避免无限翻页
    var plan = [
        { path: 'label/new', pages: 3 },
        { path: 'type/dongman', pages: 2 },
        { path: 'type/dianying', pages: 1 }
    ];
    var seen = {}, out = [];
    for (var p = 0; p < plan.length; p++) {
        for (var pg = 1; pg <= plan[p].pages; pg++) {
            var items = listFromPath(plan[p].path, pg, '');
            if (!items.length) break;
            for (var i = 0; i < items.length; i++) {
                var it = items[i];
                if (seen[it.id]) continue;
                if (it.name && it.name.indexOf(key) >= 0) { seen[it.id] = 1; out.push(it); }
            }
        }
    }
    return out;
}

function searchFiltered(category, filtersJson, page) {
    var f = parseJson(filtersJson) || {};
    var arr = parseJson(search(category, page)) || [];
    if (f.year) {
        arr = arr.filter(function (it) { return !it.year || it.year === f.year; });
    }
    return JSON.stringify(arr);
}

// ───────────────────────────────────────────── 详情

function detail(id) {
    var url = SITE + '/detail/' + id + '.html';
    var html = fetchHtml(url);
    if (!html) {
        return JSON.stringify({ id: id, name: '', pic: '', desc: '', episodes: [] });
    }

    var name = decodeEntities(trim(match(html, '<h1 class="page-title">([^<]+)</h1>', 1)));
    var pic  = match(html, '<img class="url_img"[^>]*src="([^"]+)"', 1) || '';
    var typeName = decodeEntities(trim(match(html, '/type/[a-z0-9]+\\.html"\\s*title="([^"]+)"', 1)));
    var year = match(html, '/year/(\\d{4})\\.html', 1) || '';
    var remarks = decodeEntities(trim(match(html,
        '集数：</span>\\s*<div class="video-info-item">([^<]+)</div>', 1)));
    var desc = trim(stripTags(decodeEntities(match(html,
        '<div class="video-info-item video-info-content">([\\s\\S]*?)</div>', 1) || '')));

    // 线路名（聚合线路 / box聚合 …）
    var tabNames = [];
    var rawTabs = parseJson(matchAll(html, 'data-dropdown-value="([^"]+)"')) || [];
    for (var t = 0; t < rawTabs.length; t++) tabNames.push(decodeEntities(trim(rawTabs[t][1])));

    // 所有选集链接：/play/{id}_{sid}_{nid}.html  →  [whole, id, sid, nid, epName]
    var episodes = [];
    var rawEps = parseJson(matchAll(html,
        '/play/(\\d+)_(\\d+)_(\\d+)\\.html"[^>]*>\\s*<span>([^<]+)</span>')) || [];
    for (var e = 0; e < rawEps.length; e++) {
        var m = rawEps[e];
        var sid = m[2];
        var routeName = tabNames[parseInt(sid, 10) - 1] || ('线路' + sid);
        episodes.push({
            name: decodeEntities(trim(m[4])),
            url: m[1] + '_' + m[2] + '_' + m[3],   // 传给 play() 的 flag
            route: routeName
        });
    }

    return JSON.stringify({
        id: id,
        name: name,
        pic: pic,
        type: typeName,
        year: year,
        remarks: remarks,
        desc: desc,
        episodes: episodes
    });
}

// 相关推荐（可选）：详情页底部"猜你喜欢"用的同款卡片。
function related(id) {
    var html = fetchHtml(SITE + '/detail/' + id + '.html');
    var arr = parseCards(html, '');
    var out = [];
    for (var i = 0; i < arr.length; i++) {
        if (arr[i].id !== String(id)) out.push(arr[i]);
    }
    return JSON.stringify(out);
}

// ───────────────────────────────────────────── 播放解析

function guessType(u) {
    if (!u) return 'auto';
    var l = u.toLowerCase();
    if (l.indexOf('.m3u8') >= 0) return 'm3u8';
    if (l.indexOf('.mp4') >= 0) return 'mp4';
    return 'auto';
}

// 把 flag 还原成播放页 URL。
function playPageUrl(flag) {
    if (/^https?:/i.test(flag)) return flag;
    var f = String(flag).replace(/^\/+/, '').replace(/^play\//, '').replace(/\.html$/, '');
    return SITE + '/play/' + f + '.html';
}

// 从 flag（id_sid_nid）里取线路序号 sid，取不到返回 ''。
function sidOf(flag) {
    var f = String(flag).replace(/^\/+/, '').replace(/^play\//, '').replace(/\.html$/, '');
    var m = /^\d+_(\d+)_\d+/.exec(f);
    return m ? m[1] : '';
}

// 按线路决定取流方式：PLAY_MODE[sid] 优先，否则 DEFAULT_PLAY_MODE；非法值归一为 'auto'。
function playModeFor(flag) {
    var sid = sidOf(flag);
    var mode = (sid && PLAY_MODE[sid]) ? PLAY_MODE[sid] : DEFAULT_PLAY_MODE;
    mode = String(mode).toLowerCase();
    return (mode === 'direct' || mode === 'sniff') ? mode : 'auto';
}

// 去掉 m3u8/mp4 地址末尾非标准拼接的 &t=hls&ct=1 之类尾巴。
//   lmm85 的 player_aaaa.url 形如 "....m3u8&t=hls&ct=1"（注意是 & 不是 ?），
//   直接请求会 404；真正的播放地址是截到 .m3u8 为止。若已是 "?query" 形式则保留。
function cleanMedia(u) {
    if (!u) return u;
    var exts = ['.m3u8', '.mp4', '.flv', '.mkv'];
    for (var i = 0; i < exts.length; i++) {
        var idx = u.toLowerCase().indexOf(exts[i]);
        if (idx >= 0) {
            var after = u.charAt(idx + exts[i].length);
            if (after && after !== '?') return u.substring(0, idx + exts[i].length);
            return u;
        }
    }
    return u;
}

// 静态解析 player_aaaa 拿直链。
function staticPlay(pageUrl) {
    var html = fetchHtml(pageUrl);
    var conf = match(html, 'player_aaaa\\s*=\\s*(\\{[\\s\\S]*?\\})\\s*</script>', 1)
            || match(html, 'player_aaaa\\s*=\\s*(\\{[\\s\\S]*?\\});', 1);
    var obj = conf ? (parseJson(conf) || {}) : {};
    var raw = obj.url || '';
    var enc = obj.encrypt;
    if (raw) {
        if (enc === 1 || enc === '1') raw = decodeUri(raw);
        else if (enc === 2 || enc === '2') { try { raw = decodeUri(base64Decode(raw)); } catch (ex) {} }
    }
    return cleanMedia(raw);
}

// 直链：静态解析 player_aaaa 拿真实 m3u8（cleanMedia 已去掉 &t=hls&ct=1 尾巴）。命中返回结果对象，否则 null。
function tryDirect(pageUrl) {
    var raw = staticPlay(pageUrl);
    if (raw && /^https?:/i.test(raw)) {
        log('[lmm85] direct url: ' + raw);
        return { url: raw, type: guessType(raw), referer: '' };
    }
    return null;
}

// 嗅探：WebView 在播放页上下文里跑播放器，截获真实媒体请求并带回 Referer。命中返回结果对象，否则 null。
//   ⚠ 不要伪装桌面 UA：CF 挑战会比对 UA 与浏览器指纹，"桌面 Chrome UA + Android WebView"必被
//   判为伪装而挑战循环过不去；保持 WebView 默认 UA 才能自动过非交互挑战（cf_clearance 也绑定 UA）。
//   timeout 给 20s：挑战自解约 3~6s + 真页重载 + 播放器起播，15s 偏紧。
//   ⚠ 必须开 autoPlay：jable 模板播放器停在海报上等点击，不点就永远不发 m3u8 请求（嗅探纯被动监听
//   会一直 cands=0 超时）。autoPlay=true 才会注入起播脚本——点海报/起播按钮 + 强制 video.play()，
//   并直接读 window.player_aaaa.url（苹果 CMS 解码后的 m3u8 就在这个全局变量里，不依赖播放器起播）。
function trySniff(pageUrl) {
    var hit = sniffMedia(pageUrl, JSON.stringify({
        timeout: 20000,
        autoPlay: true,
        patterns: ['\\.m3u8(\\?|$)', '\\.mp4(\\?|$)', '/m3u8', '/playlist']
    }));
    if (hit && hit.ok && hit.url) {
        log('[lmm85] sniff hit: ' + hit.url);
        // headers = 嗅探用的 UA/cookie（拼好的 JSON 字符串），带进播放防 CDN UA 校验 403
        return { url: hit.url, type: guessType(hit.url), referer: hit.referer || '', headers: hit.headers || '' };
    }
    return null;
}

// 播放解析：取流方式按线路（sid）决定，见顶部 PLAY_MODE / DEFAULT_PLAY_MODE。
//   direct=只直链，sniff=只嗅探，auto=直链优先、失败嗅探兜底。
//   注：该站把 ts 切片伪装成 PNG（切片首部 PNG 头 + 真实 TS 数据），剥头由 App 播放层
//   （本地代理 MpvLocalPlaylistServer，已对 EXO/MPV 两内核生效）统一处理，源只管拿地址。
function play(flag) {
    var mode = playModeFor(flag);
    var pageUrl = playPageUrl(flag);
    log('[lmm85] play flag=' + flag + ' sid=' + sidOf(flag) + ' mode=' + mode);

    var r = null;
    if (mode === 'direct') {
        r = tryDirect(pageUrl);
    } else if (mode === 'sniff') {
        r = trySniff(pageUrl);
    } else {                       // auto：先直链，拿不到再嗅探
        r = tryDirect(pageUrl) || trySniff(pageUrl);
    }

    if (r) { r.mode = mode; return JSON.stringify(r); }
    log('[lmm85] play failed (mode=' + mode + ')');
    return JSON.stringify({ url: '', type: 'auto', mode: mode });
}