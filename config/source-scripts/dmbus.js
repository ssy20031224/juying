/*
 * 动漫巴士（dmbus / dm84）JS 源 —— HTML 正则解析 + hhjx 播放器解密
 * 站点：https://dmbus.cc   （备用域名 dm84.tv / dm84.vip→dmbus.cc，统一用手机 UA）
 * version: 2.0.0  (2026-06-03 全量重写，基于 mydiy 模板真实页面结构 + hhjx api.php 解密)
 *
 * 页面结构（mydiy 模板）：
 *   首页              /                       多张 card：热播/国产/日本/欧美/电影，每张含一个 ul.v_list
 *   分类列表          /list-{1..4}.html        翻页 /list-{id}-{page}.html
 *   筛选(类型/年份/排序) /show-{type}--{sort}-{class}--{year}-{page}.html
 *   关键词搜索        /s----------.html?wd=KEYWORD
 *   详情              /v/{id}.html             选集链接 /p/{id}-{line}-{ep}.html
 *   播放页            /p/{id}-{line}-{ep}.html  内嵌 iframe → hhjx.hhplayer.com/index.php?url=HEX
 *
 * 取流链路（无需 WebView，纯接口解密）：
 *   1) 抓 /p 播放页 → iframe src（hhjx index.php?url=HEX）
 *   2) 抓 index.php → 取 var url / var t / var key=OKOK("base64")
 *   3) OKOK 解码出 key（确定性变换，已在本文件复刻）
 *   4) POST hhjx /api.php  body=url&t&key&act=0&play=1 → JSON {code:200,url:真实地址}
 *   解密失败时兜底 sniffMedia(iframe) 走 WebView 嗅探。
 *
 * 分类 type_id：1=国漫(国产动漫) 2=日漫(日本动漫) 3=欧美 4=电影
 */

// ─────────────────────────────────────────────── 配置
var EXT     = (typeof ext !== 'undefined' && ext) ? ext : {};
var SITE    = (EXT.site || 'https://dmbus.cc').replace(/\/+$/, '');
var UA_M    = (typeof UA !== 'undefined' && UA.iphone) ? UA.iphone
            : 'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1';
var TIMEOUT = 15000;

// 分类 tab（id ↔ 名称）
var TYPES = [
    { id: '1', name: '国漫' },
    { id: '2', name: '日漫' },
    { id: '3', name: '欧美' },
    { id: '4', name: '电影' }
];

// 各分类「类型/剧情」筛选项（取自站点 list 页 list_filter；只放已确认的，未列分类只给排序+年份）
var CLASS_OPTS = {
    '1': ['玄幻', '穿越', '动态漫画', '热血', '搞笑', '恋爱', '奇幻', '武侠', '战斗', '悬疑', '日常', '格斗'],
    '2': ['冒险', '奇幻', '战斗', '后宫', '热血', '励志', '搞笑', '校园', '机战', '悬疑', '治愈', '百合', '恐怖', '泡面番', '恋爱', '推理']
};

// ─────────────────────────────────────────────── 工具
function trim(s) { return s == null ? '' : String(s).replace(/^\s+|\s+$/g, ''); }

function decodeEntities(s) {
    if (!s) return '';
    return String(s)
        .replace(/&nbsp;/gi, ' ').replace(/&amp;/gi, '&')
        .replace(/&lt;/gi, '<').replace(/&gt;/gi, '>')
        .replace(/&quot;/gi, '"').replace(/&#0?39;/g, "'").replace(/&apos;/gi, "'")
        .replace(/&#x([0-9a-fA-F]+);/g, function (m, h) { try { return String.fromCharCode(parseInt(h, 16)); } catch (e) { return m; } })
        .replace(/&#(\d+);/g, function (m, d) { try { return String.fromCharCode(parseInt(d, 10)); } catch (e) { return m; } });
}

function stripTags(s) { return s ? String(s).replace(/<[^>]+>/g, '') : ''; }

function guessType(u) {
    var l = (u || '').toLowerCase();
    if (l.indexOf('.m3u8') >= 0) return 'm3u8';
    if (l.indexOf('.mp4') >= 0)  return 'mp4';
    if (l.indexOf('.flv') >= 0)  return 'flv';
    if (l.indexOf('.mkv') >= 0)  return 'mkv';
    return 'auto';
}

function yearOpts() {
    var out = [{ n: '全部', v: '' }];
    var y = (new Date()).getFullYear();
    for (var i = 0; i < 12; i++) out.push({ n: String(y - i), v: String(y - i) });
    return out;
}

// GET（手机 UA + Referer）
function req(url, referer) {
    try {
        return request(url, JSON.stringify({
            headers: {
                'User-Agent': UA_M,
                'Referer': referer || (SITE + '/'),
                'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
            },
            timeout: TIMEOUT
        })) || '';
    } catch (e) { log('[dmbus] req err ' + url + ' :: ' + e); return ''; }
}

function reqPath(path, referer) { return req(SITE + path, referer); }

// 解析一段含 ul.v_list 的 HTML，抽出影片条目
function parseList(html) {
    var out = [];
    if (!html) return out;
    var arr = parseJson(matchAll(html,
        '<a href="/v/(\\d+)\\.html" class="cover lazy" data-bg="([^"]*)"[^>]*>[\\s\\S]*?<a class="title" href="/v/\\d+\\.html" title="([^"]*)">[\\s\\S]*?<span class="desc">([^<]*)</span>'
    )) || [];
    for (var i = 0; i < arr.length; i++) {
        var m = arr[i];          // [整体, id, pic, name, remark]
        var name = trim(decodeEntities(m[3]));
        if (!name) continue;
        out.push({
            id:      m[1],
            name:    name,
            pic:     trim(m[2]),
            type:    '',
            year:    '',
            remarks: trim(decodeEntities(m[4])),
            desc:    ''
        });
    }
    return out;
}

function dedup(list) {
    var seen = {}, out = [];
    for (var i = 0; i < list.length; i++) {
        var id = list[i].id;
        if (!id || seen[id]) continue;
        seen[id] = 1; out.push(list[i]);
    }
    return out;
}

// ─────────────────────────────────────────────── 契约入口
function categories() {
    var ys = yearOpts();
    var sorts = [{ n: '最新', v: 'time' }, { n: '最热', v: 'hits' }, { n: '评分', v: 'score' }];
    var out = [{ key: '', title: '推荐' }];
    for (var i = 0; i < TYPES.length; i++) {
        var t = TYPES[i];
        var filters = [{ key: 'sort', name: '排序', value: sorts }];
        if (CLASS_OPTS[t.id]) {
            var cv = [{ n: '全部', v: '' }];
            var cs = CLASS_OPTS[t.id];
            for (var j = 0; j < cs.length; j++) cv.push({ n: cs[j], v: cs[j] });
            filters.push({ key: 'class', name: '类型', value: cv });
        }
        filters.push({ key: 'year', name: '年份', value: ys });
        out.push({ key: t.id, title: t.name, filters: filters });
    }
    return JSON.stringify(out);
}

function homeSections() {
    var html = reqPath('/');
    var out = [];
    if (html) {
        // 每个分区 card 里有一个 a.c_title 标题 + 紧随的 ul.v_list
        var chunks = html.split('class="c_title"');
        for (var i = 1; i < chunks.length; i++) {
            var c = chunks[i];
            var title = trim(match(c, '^[^>]*>([^<]+)</a>', 1));
            if (!title) continue;                               // 「热门动漫类型」用 </h3> 收尾，跳过
            var listHtml = match(c, '<ul class="v_list[^"]*">([\\s\\S]*?)</ul>', 1);
            if (!listHtml) continue;
            var items = parseList(listHtml);
            if (items.length) out.push({ title: title, key: '', items: items.slice(0, 12) });
        }
    }
    return JSON.stringify(out);
}

function search(keyword, page) {
    page = page || 1;
    var key = trim(keyword || '');
    // 首页推荐：聚合首页所有分区条目
    if (!key) return JSON.stringify(dedup(parseList(reqPath('/'))));
    // 分类 tab：list-{id}
    if (/^[1-4]$/.test(key)) {
        var lu = SITE + (page > 1 ? '/list-' + key + '-' + page + '.html' : '/list-' + key + '.html');
        return JSON.stringify(parseList(req(lu)));
    }
    // 关键词搜索
    var su = SITE + '/s----------.html?wd=' + encodeUri(key) + (page > 1 ? ('&page=' + page) : '');
    return JSON.stringify(parseList(req(su)));
}

function searchFiltered(category, filtersJson, page) {
    var f = parseJson(filtersJson) || {};
    var type = (category && /^[1-4]$/.test(category)) ? category : '1';
    var sort = f.sort || '';
    var cls  = f['class'] ? encodeUri(f['class']) : '';
    var year = (f.year && f.year !== '全部') ? f.year : '';
    page = page || 1;
    // 无任何筛选时退化成普通分类列表（翻页更稳）
    if (!sort && !cls && !year) return search(type, page);
    // /show-{type}-{}-{sort}-{class}-{}-{year}-{page}.html
    var fields = [type, '', sort, cls, '', year, (page > 1 ? String(page) : '')];
    var url = SITE + '/show-' + fields.join('-') + '.html';
    return JSON.stringify(parseList(req(url)));
}

function detail(id) {
    var out = { id: id, name: '', pic: '', type: '', year: '', remarks: '', desc: '', episodes: [] };
    var html = reqPath('/v/' + id + '.html');
    if (!html) return JSON.stringify(out);

    out.name = trim(decodeEntities(
        match(html, '<h1 class="v_title"><a[^>]*>([^<]+)</a>', 1) ||
        match(html, 'og:title" content="《([^》]+)》', 1) || ''
    ));
    out.pic     = trim(match(html, 'og:image"\\s*content="([^"]+)"', 1) || match(html, '<div class="cover"><img src="([^"]+)"', 1) || '');
    out.year    = trim(match(html, 'og:video:release_date" content="([^"]+)"', 1) || '');
    out.remarks = trim(decodeEntities(match(html, 'class="v_desc"><span class="desc">([^<]*)<', 1) || ''));
    out.type    = trim((match(html, 'og:video:class" content="([^"]*)"', 1) || '').split(',').slice(0, 2).join(' '));
    out.desc    = trim(decodeEntities(stripTags(
        match(html, '<div id="intro"><p>([\\s\\S]*?)</p>', 1) ||
        match(html, 'og:description" content="([^"]*)"', 1) || ''
    )));

    // 线路名（tab_control play_from 里的若干 li）
    var ctrl = match(html, '<ul class="tab_control play_from">([\\s\\S]*?)</ul>', 1) || '';
    var lineNames = [];
    var ln = parseJson(matchAll(ctrl, '<li[^>]*>([^<]+)</li>')) || [];
    for (var a = 0; a < ln.length; a++) lineNames.push(trim(ln[a][1]));

    // 选集：/p/{vod}-{line}-{ep}.html（按线路号分组，组内按集号升序）
    var eps = parseJson(matchAll(html, '<a href="/p/(\\d+)-(\\d+)-(\\d+)\\.html"[^>]*>([^<]+)</a>')) || [];
    var byLine = {}, order = [];
    for (var i = 0; i < eps.length; i++) {
        var vod = eps[i][1], lineNo = eps[i][2], ep = eps[i][3], label = trim(eps[i][4]);
        if (!byLine[lineNo]) { byLine[lineNo] = []; order.push(lineNo); }
        byLine[lineNo].push({ name: label || ep, url: '/p/' + vod + '-' + lineNo + '-' + ep + '.html', ep: parseInt(ep, 10) || 0 });
    }
    for (var k = 0; k < order.length; k++) {
        var lineNo2 = order[k];
        var list = byLine[lineNo2];
        list.sort(function (x, y) { return x.ep - y.ep; });
        var route = lineNames[k] || ('线路' + lineNo2);
        for (var j = 0; j < list.length; j++) {
            out.episodes.push({ name: list[j].name, url: list[j].url, route: route });
        }
    }
    return JSON.stringify(out);
}

// hhjx 播放器 index.php 里的 OKOK：atob 后按 token 表贪婪还原（与站点 JS 一致）
function okok(t) {
    var ee = {
        "0Oo0o0Oo": "a", "1O0bO001": "b", "1OoCcO1": "c", "3O0dO0O3": "d", "4OoEeO4": "e", "5O0fO0O5": "f",
        "6OoGgO6": "g", "7O0hO0O7": "h", "8OoIiO8": "i", "9O0jO0O9": "j", "0OoKkO0": "k", "1O0lO0O1": "l",
        "2OoMmO2": "m", "3O0nO0O3": "n", "4OoOoO4": "o", "5O0pO0O5": "p", "6OoQqO6": "q", "7O0rO0O7": "r",
        "8OoSsO8": "s", "9O0tOoO9": "t", "0OoUuO0": "u", "1O0vO0O1": "v", "2OoWwO2": "w", "3O0xO0O3": "x",
        "4OoYyO4": "y", "5O0zO0O5": "z",
        "0OoAAO0": "A", "1O0BBO1": "B", "2OoCCO2": "C", "3O0DDO3": "D", "4OoEEO4": "E", "5O0FFO5": "F",
        "6OoGGO6": "G", "7O0HHO7": "H", "8OoIIO8": "I", "9O0JJO9": "J", "0OoKKO0": "K", "1O0LLO1": "L",
        "2OoMMO2": "M", "3O0NNO3": "N", "4OoOOO4": "O", "5O0PPO5": "P", "6OoQQO6": "Q", "7O0RRO7": "R",
        "8OoSSO8": "S", "9O0TTO9": "T", "0OoUO0": "U", "1O0VVO1": "V", "2OoWWO2": "W", "3O0XXO3": "X",
        "4OoYYO4": "Y", "5O0ZZO5": "Z"
    };
    var o = '';
    try { o = base64Decode(t); } catch (e) { return ''; }
    var n = '';
    for (var i = 0; i < o.length; i++) {
        var l = o.charAt(i);
        for (var k in ee) {
            if (ee.hasOwnProperty(k) && o.substr(i, k.length) === k) { l = ee[k]; i += k.length - 1; break; }
        }
        n += l;
    }
    return n;
}

// 把可能「无主机」的地址补成完整 http(s) 地址。
//  - 完整 http(s):// 原样返回
//  - 协议相对 //host/path → 补 https:
//  - 主机相对 /path → 拼上 base 的 origin（hhjx 解密接口返回的 /cache/iqiyi/xxx.m3u8 就是这种，
//    浏览器按播放器页面 origin 解析才能放；播放内核拿到裸 /path 会当本地文件打开 → 权限拒绝播不了）
function absUrl(u, base) {
    if (!u) return u;
    if (/^https?:\/\//i.test(u)) return u;
    if (/^\/\//.test(u)) return 'https:' + u;
    if (/^\//.test(u) && base) return base + u;
    return u;
}

function play(flag) {
    var res = { url: '', type: 'auto', referer: '' };
    var playUrl = /^https?:/i.test(flag) ? flag : (SITE + flag);

    try {
        var html = req(playUrl, SITE + '/');
        var iframe = match(html, '<iframe[^>]*src="([^"]+)"', 1) || '';
        iframe = iframe.replace(/&amp;/g, '&');
        if (iframe && /^\/\//.test(iframe)) iframe = 'https:' + iframe;

        // 标准链路：hhjx index.php → api.php 解密
        if (iframe && iframe.indexOf('/index.php?url=') >= 0) {
            var base = match(iframe, '^(https?://[^/]+)', 1) || '';
            var pl = req(iframe, SITE + '/');
            var u  = match(pl, 'var url\\s*=\\s*"([^"]+)"', 1);
            var t  = match(pl, 'var t\\s*=\\s*"([^"]+)"', 1);
            var kb = match(pl, 'var key\\s*=\\s*OKOK\\("([^"]+)"\\)', 1);
            if (base && u && t && kb) {
                var key = okok(kb);
                var body = 'url=' + encodeUri(u) + '&t=' + encodeUri(t) + '&key=' + encodeUri(key) + '&act=0&play=1';
                var resp = post(base + '/api.php', body, JSON.stringify({
                    headers: {
                        'User-Agent': UA_M,
                        'Referer': iframe,
                        'Origin': base,
                        'X-Requested-With': 'XMLHttpRequest',
                        'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
                    },
                    timeout: TIMEOUT
                }));
                var dj = parseJson(resp) || {};
                if (dj.code == 200 && dj.url) {
                    var real = String(dj.url).replace(/\\\//g, '/');
                    // 关键修复：api.php 可能返回主机相对路径（/cache/iqiyi/xxx.m3u8），补成完整地址，
                    // 否则播放内核把裸 /path 当本地文件 → EACCES 播不了（浏览器能放是因为按 origin 解析）。
                    real = absUrl(real, base);
                    if (dj.ext === 'link') {
                        // 返回的是嵌套播放页 → WebView 嗅探
                        var h2 = sniffMedia(real, { patterns: ['\\.m3u8(\\?|$)', '\\.mp4(\\?|$)'], userAgent: UA_M, referer: iframe, timeout: TIMEOUT });
                        if (h2 && h2.ok && h2.url) { res.url = h2.url; res.type = guessType(h2.url); res.referer = h2.referer || ''; if (h2.headers) res.headers = h2.headers; return JSON.stringify(res); }
                    } else {
                        res.url = real;
                        res.type = guessType(real);
                        if (dj.referer && dj.referer !== 'never') res.referer = dj.referer;
                        // 同源 /cache 资源多半要带 Referer 防盗链：接口没给 referer 时兜底用播放器页面，
                        // 与浏览器请求该 m3u8 时携带的 Referer 一致。
                        else if (base && real.indexOf(base) === 0) res.referer = iframe;
                        return JSON.stringify(res);
                    }
                } else {
                    log('[dmbus] api.php no url, resp=' + (resp ? String(resp).substring(0, 160) : ''));
                }
            }
        }

        // 兜底：直接 WebView 嗅探 iframe / 播放页
        var hit = sniffMedia(iframe || playUrl, {
            patterns: ['\\.m3u8(\\?|$)', '\\.mp4(\\?|$)'],
            userAgent: UA_M, referer: SITE + '/', timeout: TIMEOUT, autoPlay: true
        });
        if (hit && hit.ok && hit.url) { res.url = hit.url; res.type = guessType(hit.url); res.referer = hit.referer || ''; if (hit.headers) res.headers = hit.headers; }
    } catch (e) { log('[dmbus] play err: ' + e); }

    return JSON.stringify(res);
}