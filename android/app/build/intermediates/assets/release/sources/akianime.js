/*
 * AkiAnime（Aki动漫 akianime.com） JS 源
 * version: 1.2.0
 *
 * 站点：https://www.akianime.com （dsn2 模板定制 maccms，走 Cloudflare）
 * 反爬：首访 ?cckey= 两跳重定向 + 下发 PHPSESSID / _ok9_ cookie。
 *       GET 请求靠 OkHttp followRedirects + 共享 CookieJar 自动通过；
 *       但 POST（分类接口）必须先有 cookie，故 dsApi 前先 ensureCookie() 拿一次首页。
 *
 * 列表：POST /index.php/ds_api/vod  →  JSON {code:1,list,pagecount,total}
 *       有效筛选：class(剧情) / year(年份) / by(排序 time|hits|score) / page(翻页)；
 *       tid/area 实测无效（单一大类），不使用。
 * 关键词搜索：GET /bgmsearch/{wd}-…-.html（服务端渲染，仅第 1 页）。
 * 详情：GET /bgmdetail/{ID}.html（ID 混淆，如 PEcDDE）。
 * 播放：/bgmplay/{ID}-{线路}-{集}.html 内 player_aaaa.url——
 *       明文 m3u8/mp4/flv 直接返回（秒开，无防盗链）；
 *       Doki- 加密线路走站点自带外部解析器：读 playerconfig.js 的 player_list[from].parse，
 *       GET 解析器页拿内嵌 config{url,key,time} → POST 同目录 api_config.php 换真实直链；
 *       解析器拿不到再退回 WebView 嗅探（先嗅解析器页、后嗅播放页）。
 *
 * v1.2.0：加密线路补「外部解析器直取」（原先只有嗅探，常失败/慢）；
 *         列表接口 cookie 失效自愈（返回非 JSON 时重新预热重试一次）。
 */

var EXT  = (typeof ext !== 'undefined' && ext) ? ext : {};
var SITE = (EXT.site || 'https://www.akianime.com').replace(/\/+$/, '');
var UA_STR = (typeof UA !== 'undefined' && UA.chrome) ? UA.chrome
    : 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36';
var API = SITE + '/index.php/ds_api/vod';
var TIMEOUT = 20000;

// ─────────────────────────────────────────────── 工具
function trim(s) { return s == null ? '' : String(s).replace(/^\s+|\s+$/g, ''); }

function abs(u) {
    if (!u) return '';
    if (/^https?:\/\//.test(u)) return u;
    return SITE + (u.charAt(0) === '/' ? '' : '/') + u;
}

function stripTags(s) { return s ? String(s).replace(/<[^>]+>/g, '') : ''; }

function decodeEntities(s) {
    if (!s) return '';
    s = String(s)
        .replace(/&nbsp;/gi, ' ').replace(/&amp;/gi, '&')
        .replace(/&lt;/gi, '<').replace(/&gt;/gi, '>')
        .replace(/&quot;/gi, '"').replace(/&apos;/gi, "'").replace(/&#0?39;/g, "'");
    s = s.replace(/&#x([0-9a-fA-F]+);/g, function (m, h) { try { return String.fromCharCode(parseInt(h, 16)); } catch (e) { return m; } });
    s = s.replace(/&#(\d+);/g, function (m, d) { try { return String.fromCharCode(parseInt(d, 10)); } catch (e) { return m; } });
    return s;
}

function guessType(u) {
    var l = (u || '').toLowerCase();
    if (l.indexOf('.m3u8') >= 0) return 'm3u8';
    if (l.indexOf('.mp4') >= 0)  return 'mp4';
    if (l.indexOf('.flv') >= 0)  return 'flv';
    return 'auto';
}

function yearOpts(n) {
    var out = [{ n: '全部', v: '' }];
    var y = (new Date()).getFullYear();
    for (var i = 0; i < (n || 10); i++) out.push({ n: String(y - i), v: String(y - i) });
    return out;
}

function HDR() {
    return JSON.stringify({ headers: { 'User-Agent': UA_STR, 'Referer': SITE + '/' }, timeout: TIMEOUT });
}
function req(url) { return request(url, HDR()) || ''; }

// 先 GET 一次首页过 cckey、把 cookie 存进共享 CookieJar，供后续 POST 使用。
// warm 只在「确实拿到过站点响应」时才置位，拿不到不闩死——下次调用会再试，避免首刷网络抖动后一直空。
var COOKIE_READY = false;
function ensureCookie(force) {
    if (COOKIE_READY && !force) return;
    try {
        var h = request(SITE + '/', HDR());
        if (h) COOKIE_READY = true;   // 有响应才算预热成功
    } catch (e) {}
}

// POST 列表接口。cckey 反爬：cookie 缺失/过期时服务端 302 回带 cckey 的地址，
// 跟随重定向后 body 会是 HTML（非 JSON）。检测到非 JSON 就强制重新预热 cookie 再打一次。
function dsApiRaw(params) {
    var parts = [];
    for (var k in params) {
        if (params.hasOwnProperty(k)) parts.push(encodeUri(k) + '=' + encodeUri(params[k] == null ? '' : String(params[k])));
    }
    var opt = JSON.stringify({
        headers: {
            'User-Agent': UA_STR, 'Referer': SITE + '/',
            'X-Requested-With': 'XMLHttpRequest',
            'Content-Type': 'application/x-www-form-urlencoded'
        },
        timeout: TIMEOUT
    });
    return post(API, parts.join('&'), opt) || '';
}
function looksJson(s) {
    if (!s) return false;
    var t = s.replace(/^\s+/, '');
    return t.charAt(0) === '{' || t.charAt(0) === '[';
}
function dsApi(params) {
    ensureCookie(false);
    var body = dsApiRaw(params);
    if (!looksJson(body)) {   // cookie 过期/首刷抖动 → 重新预热后重试一次
        ensureCookie(true);
        body = dsApiRaw(params);
    }
    return body;
}

function vodReq(cls, year, by, page) {
    return dsApi({ mid: 1, tid: 20, 'class': cls || '', area: '', year: year || '', by: by || 'time', page: page || 1 });
}

// 解析 ds_api JSON 列表
function parseApiList(jsonStr) {
    var out = [];
    var j = parseJson(jsonStr) || {};
    var list = j.list || [];
    for (var i = 0; i < list.length; i++) {
        var v = list[i];
        var id = match(String(v.url || ''), '/bgmdetail/([^/.]+)\\.html', 1) || String(v.vod_id || '');
        if (!id) continue;
        out.push({
            id:      id,
            name:    decodeEntities(trim(v.vod_name || '')),
            pic:     abs(v.vod_pic || ''),
            type:    '番剧',
            year:    v.vod_year ? String(v.vod_year) : '',
            remarks: decodeEntities(trim(v.vod_remarks || '')),
            desc:    decodeEntities(stripTags(v.vod_blurb || '')).replace(/\s+/g, ' ').trim()
        });
    }
    return out;
}

// bgmsearch 14 段 URL（仅用于关键词搜索）：段1=关键词
function buildSearch(wd) {
    var seg = ['', '', '', '', '', '', '', '', '', '', '', '', '', ''];
    seg[0] = wd || '';
    var parts = [];
    for (var i = 0; i < seg.length; i++) parts.push(encodeUri(seg[i]));
    return SITE + '/bgmsearch/' + parts.join('-') + '.html';
}

// 解析 bgmsearch 列表卡片（slide-info 结构）
function parseHtmlList(html) {
    var out = [], seen = {};
    if (!html) return out;
    var arr = parseJson(matchAll(html,
        'data-src="(/upload/[^"]+)"[\\s\\S]*?/bgmdetail/([^"/]+?)\\.html"[^>]*>\\s*<h3[^>]*>([^<]+)</h3>[\\s\\S]*?slide-info-remarks[^>]*>([^<]*)<'
    )) || [];
    for (var i = 0; i < arr.length; i++) {
        var m = arr[i], id = m[2];
        if (!id || seen[id]) continue;
        seen[id] = 1;
        out.push({
            id: id, name: decodeEntities(m[3]).trim(), pic: abs(m[1]),
            type: '番剧', year: '', remarks: decodeEntities(stripTags(m[4])).trim(), desc: ''
        });
    }
    return out;
}

function pickDesc(html) {
    var d = match(html, '<em[^>]*>简介[：:\\s]*</em>([\\s\\S]*?)</(?:div|p|span)>', 1)
         || match(html, '<div class="[^"]*juqing[^"]*"[^>]*>([\\s\\S]*?)</div>', 1)
         || match(html, 'class="check"[^>]*>([\\s\\S]*?)</div>', 1);
    d = decodeEntities(stripTags(d || '')).replace(/\s+/g, ' ').trim();
    if (/^(暂无简介|暂无剧情介绍|剧情简介暂缺)/.test(d)) d = '';
    if (d.length > 300) d = d.substring(0, 300);
    return d;
}

// 线路名清洗：去掉「不要相信视频里的广告」等话术 + 尾部分隔符
function cleanLine(s) {
    s = decodeEntities(stripTags(s || '')).replace(/\u00a0/g, '').trim();
    s = s.replace(/(不要相信|请不要|切勿相信|视频里的广告|广告|更新至|提示).*$/, '').trim();
    s = s.replace(/[\-—－|｜·、,]+$/, '').trim();
    return s || '线路';
}

// ─────────────────────────────────────────────── 外部解析器（Doki- 加密线路）
// 把相对地址按某个页面 URL 求绝对地址（api_config.php 相对解析页目录）
function joinUrl(base, rel) {
    if (!rel) return '';
    if (/^https?:\/\//.test(rel)) return rel;
    var m = /^(https?:\/\/[^\/]+)(\/[^?#]*)?/.exec(String(base).split('#')[0].split('?')[0]);
    if (!m) return rel;
    var origin = m[1], path = m[2] || '/';
    if (rel.charAt(0) === '/') return origin + rel;
    return origin + path.substring(0, path.lastIndexOf('/') + 1) + rel;
}

// playerconfig.js 里的 player_list：from → {ps, parse}。ps=1 表示要外部解析器。缓存一次。
var PLAYER_CFG = null;
function playerCfg() {
    if (PLAYER_CFG) return PLAYER_CFG;
    PLAYER_CFG = {};
    try {
        var js = req(SITE + '/static/js/playerconfig.js?t=' + timestamp());
        var block = match(js, 'player_list\\s*=\\s*(\\{[\\s\\S]*?\\})\\s*,\\s*MacPlayerConfig\\.downer_list', 1);
        var obj = block ? parseJson(block) : null;
        if (obj) PLAYER_CFG = obj;
    } catch (e) { log('[akianime] playerCfg err: ' + e); }
    return PLAYER_CFG;
}
function parserFor(from) {
    if (!from) return '';
    var e = playerCfg()[from];
    return (e && (e.ps === '1' || e.ps === 1) && e.parse) ? e.parse : '';
}

// 解析加密 token：GET 解析器页拿 config{url,key,time} → POST api_config.php 换真实直链。
// 返回 { page, url }：url 空表示没解出（page 交给嗅探兜底）。
function resolveByParser(token, from) {
    var parse = parserFor(from);
    if (!parse) return null;
    var pageUrl = parse + encodeUri(token);
    var html = req(pageUrl);
    if (!html) return { page: pageUrl, url: '' };

    // 1) mac 解析器通用套路：var config = { url, key, time } + 同目录 api_config.php
    var cfg = parseJson(match(html, 'var\\s+config\\s*=\\s*(\\{[\\s\\S]*?\\})', 1) || '');
    if (cfg && cfg.url) {
        var api = joinUrl(pageUrl, 'api_config.php');
        var body = 'url=' + encodeUri(cfg.url) + '&time=' + encodeUri(cfg.time || '') +
                   '&key=' + encodeUri(cfg.key || '') + '&title=';
        var opt = JSON.stringify({
            headers: {
                'User-Agent': UA_STR, 'Referer': pageUrl,
                'X-Requested-With': 'XMLHttpRequest',
                'Content-Type': 'application/x-www-form-urlencoded'
            }, timeout: TIMEOUT
        });
        var r = parseJson(post(api, body, opt) || '') || {};
        if (String(r.code) === '200' && r.url) return { page: pageUrl, url: r.url };
    }
    // 2) 解析器页直接内嵌明文流
    var direct = match(html, '(https?:[^"\'\\s\\\\]+\\.(?:m3u8|mp4|flv|m4s)[^"\'\\s\\\\]*)', 1);
    if (direct) return { page: pageUrl, url: direct.split('\\/').join('/') };

    return { page: pageUrl, url: '' };
}

// ─────────────────────────────────────────────── 分类（剧情 tab + 年份/排序筛选）
var CAT_TABS = [
    ['', '推荐'], ['校园', '校园'], ['恋爱', '恋爱'], ['异世界', '异世界'],
    ['战斗', '热血'], ['日常', '日常'], ['治愈', '治愈'], ['奇幻', '奇幻'],
    ['后宫', '后宫'], ['冒险', '冒险'], ['魔法', '魔法'], ['原创', '原创']
];
var BY_OPTS = [{ n: '最新', v: 'time' }, { n: '最热', v: 'hits' }, { n: '评分', v: 'score' }];

function categories() {
    var ys = yearOpts(10);
    var out = [];
    for (var i = 0; i < CAT_TABS.length; i++) {
        out.push({
            key: CAT_TABS[i][0], title: CAT_TABS[i][1],
            filters: [
                { key: 'year', name: '年份', value: ys },
                { key: 'by',   name: '排序', value: BY_OPTS }
            ]
        });
    }
    return JSON.stringify(out);
}

function searchFiltered(category, filtersJson, page) {
    var f = parseJson(filtersJson) || {};
    var cls = trim(category);                                   // '' = 推荐（全部）
    var by = f.by || (cls === '' ? 'hits' : 'time');            // 推荐默认热门，剧情默认最新
    var year = (f.year && f.year !== '全部') ? String(f.year) : '';
    return JSON.stringify(parseApiList(vodReq(cls, year, by, page || 1)));
}

// ─────────────────────────────────────────────── 契约入口
function search(keyword, page) {
    page = page || 1;
    var kw = trim(keyword || '');
    if (!kw) return JSON.stringify(parseApiList(vodReq('', '', 'time', page)));   // 空 → 最近更新（可翻页）
    if (page > 1) return '[]';                                                    // 关键词走 bgmsearch，仅第 1 页
    return JSON.stringify(parseHtmlList(req(buildSearch(kw))));
}

function homeSections() {
    var rows = [['', 'time', '最近更新'], ['', 'hits', '人气热门'], ['', 'score', '高分推荐'], ['异世界', 'time', '异世界']];
    var out = [];
    for (var i = 0; i < rows.length; i++) {
        var items = parseApiList(vodReq(rows[i][0], '', rows[i][1], 1));
        if (items.length) out.push({ title: rows[i][2], key: rows[i][0], items: items.slice(0, 12) });
    }
    return JSON.stringify(out);
}

function detail(id) {
    var out = { id: id, name: '', pic: '', type: '', year: '', remarks: '', desc: '', episodes: [] };
    var html = req(abs('/bgmdetail/' + id + '.html'));
    if (!html) return JSON.stringify(out);

    out.name    = decodeEntities(match(html, 'detail-info[^>]*">\\s*<h3[^>]*>([^<]+)<', 1) || '').trim();
    out.pic     = abs(match(html, 'data-src="(/upload/[^"]+)"', 1) || '');
    out.year    = match(html, '/bgmsearch/-+(\\d{4})\\.html', 1) || '';
    out.type    = decodeEntities(match(html, '类型\\s*:</strong>\\s*<a[^>]*>([^<]+)<', 1) || '').trim();
    out.remarks = decodeEntities(match(html, 'slide-info-remarks cor5">([^<]+)<', 1) || '').trim();
    out.desc    = pickDesc(html);

    // 线路 tab 名（swiper-slide + badge 集数）
    var tabs = parseJson(matchAll(html,
        'swiper-slide[^>]*>(?:<i[^>]*></i>)?(?:&nbsp;|\\s)*([^<]+?)<span class="badge">(\\d+)</span>'
    )) || [];

    // 只取当前影片的剧集（限定 id，排除相关推荐区其它片的 /bgmplay 链接）
    var idRe = id.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    var eps = parseJson(matchAll(html, '/bgmplay/' + idRe + '-(\\d+)-(\\d+)\\.html"[^>]*>([^<]+)<')) || [];

    // 线路号可能不连续（如只有 line3）；按首次出现顺序依次对应 tab 名
    var lineOrder = [], lineSeen = {};
    for (var i0 = 0; i0 < eps.length; i0++) {
        var ln = eps[i0][1];
        if (!lineSeen[ln]) { lineSeen[ln] = 1; lineOrder.push(ln); }
    }
    var lineNames = {};
    for (var t = 0; t < lineOrder.length; t++) {
        lineNames[lineOrder[t]] = (tabs[t] && tabs[t][1]) ? cleanLine(tabs[t][1]) : ('线路' + (t + 1));
    }

    // 按 line-ep 去重（PC/wap 两套 DOM）
    var seen = {};
    for (var e = 0; e < eps.length; e++) {
        var m = eps[e], line = m[1], ep = m[2];
        var k = line + '-' + ep;
        if (seen[k]) continue;
        seen[k] = 1;
        out.episodes.push({
            name:  decodeEntities(m[3]).trim() || ('第' + ep + '集'),
            url:   '/bgmplay/' + id + '-' + line + '-' + ep + '.html',
            route: lineNames[line] || ('线路' + line)
        });
    }
    return JSON.stringify(out);
}

function play(flag) {
    var res = { url: '', type: 'auto', referer: SITE + '/' };
    var pageUrl = /^https?:/i.test(flag) ? flag : abs(flag);
    var html = req(pageUrl);

    // player_aaaa：优先整体 parseJson（正确解码 \/ 与 \uXXXX，url 路径可能含中文）
    // 用 [^<] 而非 [\s\S] 限定在同一行内，避免大页面正则回溯
    var pj = parseJson(match(html, 'player_aaaa\\s*=\\s*(\\{[^<]*\\})', 1) || '') || {};
    var u = pj.url || '';
    if (!u) {   // 兜底：正则取字符串再手动解码
        u = match(html, 'player_aaaa[\\s\\S]*?"url"\\s*:\\s*"([^"]*)"', 1) || '';
        u = u.replace(/\\u([0-9a-fA-F]{4})/g, function (_, h) { return String.fromCharCode(parseInt(h, 16)); }).split('\\/').join('/');
    }
    // maccms 兼容：encrypt=1 urldecode / encrypt=2 base64+urldecode（现站点是 0，稳妥保留）
    if (u) {
        if (String(pj.encrypt) === '1') { try { u = decodeUri(u); } catch (e) {} }
        else if (String(pj.encrypt) === '2') { try { u = decodeUri(base64Decode(u)); } catch (e) {} }
    }

    // 1) 明文直链 → 直接返回（秒开）。url 路径若含中文需编码，否则播放器请求会失败
    if (/^https?:\/\//.test(u) && /\.(m3u8|mp4|flv|m4s)(\?|#|$)/i.test(u)) {
        if (/[^\x00-\x7F]/.test(u)) { try { u = encodeURI(u); } catch (e) {} }
        res.url = u;
        res.type = guessType(u);
        res.headers = JSON.stringify({ 'User-Agent': UA_STR, 'Referer': SITE + '/' });
        return JSON.stringify(res);
    }

    // 2) Doki- 等加密 token → 优先走站点自带的外部解析器（GET 解析页拿 config → POST api_config.php），
    //    比嗅探快且稳；拿到直链直接返回。referer 用「never」意图：解析出的云直链多不校验防盗链。
    var parsed = (u && !/^https?:\/\//.test(u)) ? resolveByParser(u, pj.from) : null;
    if (parsed && parsed.url) {
        var pu = parsed.url;
        if (/[^\x00-\x7F]/.test(pu)) { try { pu = encodeURI(pu); } catch (e) {} }
        res.url = pu;
        res.type = guessType(pu);
        res.referer = '';
        res.headers = JSON.stringify({ 'User-Agent': UA_STR });
        return JSON.stringify(res);
    }

    // 3) 兜底：隐藏 WebView 跑页面 JS 后嗅探真实流。优先嗅探解析器页（真流在那产生），
    //    没有解析器页则退回原播放页。
    var sniffUrl = (parsed && parsed.page) ? parsed.page : pageUrl;
    try {
        var hit = sniffMedia(sniffUrl, {
            patterns:  ['\\.m3u8(\\?|$)', '\\.mp4(\\?|$)', '\\.flv(\\?|$)'],
            referer:   SITE + '/',
            userAgent: UA_STR,
            autoPlay:  true,
            timeout:   15000
        });
        if (hit && hit.ok && hit.url) {
            res.url = hit.url;
            res.type = guessType(hit.url);
            res.referer = hit.referer || (SITE + '/');
            var hh = { 'User-Agent': hit.ua || UA_STR };
            if (hit.referer) hh['Referer'] = hit.referer;
            if (hit.cookie)  hh['Cookie']  = hit.cookie;
            res.headers = JSON.stringify(hh);
            return JSON.stringify(res);
        }
    } catch (e) { log('[akianime] sniff err: ' + e); }

    return JSON.stringify(res);
}