/*
 * 金牌 APP 源（金牌影视 / api=csp_Jpys）——【仅动漫，保留首页】
 * 原型：TVBox spider csp_Jpys（com.github.catvod.spider.Jpys），新 /api/mw-movie/ 接口。
 * 对齐同仓库实测版 admin/sources/jinpai.js（2026-06-13 实测）：首页写法一致，只是屏蔽其它大类、只出动漫。
 *
 * ⚠️ 站点是综合影视站（电影/剧/综艺/动漫，typeId1 = 1/2/4/3），本源【只出动漫】：
 *   - 首页/分类：固定 type1=4（动漫），不请求其它大类；保留「推荐」首页 tab（key=""）；
 *   - 搜索：searchByWord 结果按 typeId1===4 二次过滤，同名电影/剧集/综艺一律丢弃，
 *           跨源搜索、自动换源也只会命中动漫。
 *
 * 协议（全部 GET，明文 JSON）：
 *   - 多备用域名，首个可达者生效（ext 可覆盖：逗号分隔字符串 / {hosts:[]} / {host:''}）。
 *   - 鉴权头：sign = sha1(md5(<query 明文> + "&key=<KEY>&t=<毫秒>"))，另带 T=<毫秒>、Deviceid="Deviceid"。
 *   - 列表 /video/list?type1=4&pageNum=&area=&year=          → data.list[]
 *     搜索 /video/searchByWord?keyword=&pageNum=1&pageSize=8 → data.result.list[]（含 typeId1）
 *     详情 /video/detail?id=                                  → data{...,episodeList[]{name,nid}}
 *     播放 /v2/video/episode/url?id=&nid=                     → data.list[]{resolutionName,resolution,url}（多档）
 *   - 选集 flag 编成 "id@nid"，play() 拆开再调播放接口。
 *   - 多清晰度：data.list 每项一档（蓝光/高清/标清…），play() 全量下发 res.resolutions=[{name,url,type}]
 *     供前台「清晰度切换」；默认起播最高档（与反编译取 data.list[0] 行为一致）。
 *   - 播放地址按源站要求带 Origin/Referer = 站点域名 + Chrome UA。
 */

var KEY = 'cb808529bae6b6be45ecfab29a4889bc';
var CHROME_UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36';

function trim(s) { return s == null ? '' : String(s).replace(/^\s+|\s+$/g, ''); }
function rstrip(s) { return trim(s).replace(/\/+$/, ''); }

function config() {
    return JSON.stringify({ browseOnly: false });
}

// 备用域名池：优先用注入的 ext（字符串逗号分隔 / {hosts:[]} / {host:''}），否则内置默认。
var HOSTS = (function () {
    try {
        if (typeof ext !== 'undefined' && ext) {
            if (typeof ext === 'string' && ext.indexOf('http') >= 0) {
                return ext.split(',').map(rstrip).filter(function (x) { return !!x; });
            }
            if (ext.hosts && ext.hosts.length) return ext.hosts.map(rstrip);
            if (ext.host) return [rstrip(ext.host)];
        }
    } catch (e) {}
    return ['https://y2s52n7.com', 'https://m.hkybqufgh.com', 'https://m.sizhengxt.com',
            'https://m.9zhoukj.com', 'https://m.jiabaide.cn', 'https://www.hkybqufgh.com'];
})();

function clean(s) {
    if (s == null) return '';
    return trim(String(s)
        .replace(/<[^>]+>/g, '')
        .replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&')
        .replace(/&quot;/g, '"').replace(/&#0?39;/g, "'").replace(/&#x27;/gi, "'")
        .replace(/&lt;/g, '<').replace(/&gt;/g, '>')
        .replace(/&#(\d+);/g, function (m, d) { return String.fromCharCode(parseInt(d, 10)); }));
}
function yearStr(y) { y = parseInt(y, 10); return (y && y > 1900) ? String(y) : ''; }
function typeOf(area) {
    area = area || '';
    if (/日本|日韩/.test(area)) return '日漫';
    if (/欧美|美国/.test(area)) return '欧美';
    return '国漫';
}
function guessType(u) {
    u = (u || '').toLowerCase();
    if (u.indexOf('.m3u8') >= 0) return 'm3u8';
    if (u.indexOf('.mp4') >= 0) return 'mp4';
    if (u.indexOf('.flv') >= 0) return 'flv';
    return 'auto';
}
// 分辨率友好名：接口直接给中文档位名(蓝光/超清/高清/标清/4K…)，缺名时用数字分辨率兜底(1080→"1080P")
function resName(g) {
    var n = trim(g && g.resolutionName);
    if (n) return n;
    var r = parseInt(g && g.resolution, 10);
    return (r > 0) ? (r + 'P') : '默认';
}
// 档位排序权重(高→低)：先按档名档次(4K>蓝光>超清>高清>标清>流畅)，同档再按数字分辨率
function resRank(g) {
    var byName = { '4k': 6, '蓝光': 5, '超清': 4, '高清': 3, '标清': 2, '流畅': 1 };
    var base = byName[String(trim(g && g.resolutionName)).toLowerCase()] || 0;
    var num = parseInt(g && g.resolution, 10) || 0;
    return base * 100000 + num;
}

// sign = sha1(md5(raw))，raw 末尾固定拼 &key=&t=
function sign(raw) { return sha1(md5(raw)); }
function hdr(raw, t) {
    return { 'sign': sign(raw), 'T': t, 'Deviceid': 'Deviceid', 'User-Agent': 'okhttp/3.15' };
}

var RESOLVED = '';
// 选用首个能正常返回 JSON 的域名（带签名探一次 hotSearch），结果缓存
function host() {
    if (RESOLVED) return RESOLVED;
    for (var i = 0; i < HOSTS.length; i++) {
        var h = rstrip(HOSTS[i]);
        if (!h) continue;
        var t = String(timestamp());
        var body = request(h + '/api/mw-movie/anonymous/home/hotSearch',
            JSON.stringify({ headers: hdr('key=' + KEY + '&t=' + t, t), timeout: 8000 })) || '';
        if (body && body.charAt(0) === '{') { RESOLVED = h; return h; }
    }
    RESOLVED = rstrip(HOSTS[0] || '');
    return RESOLVED;
}

function api(path, raw, t) {
    return request(host() + path, JSON.stringify({ headers: hdr(raw, t), timeout: 15000 })) || '';
}

function mapItem(it) {
    var year = yearStr(it.vodYear);
    if (!year) year = yearStr(String(it.vodPubdate || '').substring(0, 4));
    return {
        id:      String(it.vodId),
        name:    clean(it.vodName),
        pic:     trim(it.vodPic),
        type:    typeOf(it.vodArea),
        year:    year,
        remarks: clean(it.vodRemarks),
        desc:    ''
    };
}

// 动漫片库（type1 恒为 4，保证只出动漫）
function listVod(area, year, page) {
    page = page || 1;
    var t = String(timestamp());
    var raw = 'area=' + (area || '') + '&pageNum=' + page + '&type1=4&year=' + (year || '') + '&key=' + KEY + '&t=' + t;
    var path = '/api/mw-movie/anonymous/video/list?type1=4&pageNum=' + page +
               '&area=' + encodeUri(area || '') + '&year=' + encodeUri(year || '');
    var j = parseJson(api(path, raw, t)) || {};
    var list = (j.data && j.data.list) || [];
    var out = [];
    for (var i = 0; i < list.length; i++) out.push(mapItem(list[i]));
    return out;
}

// 关键词搜索：只留 typeId1===4（动漫）
function searchWord(wd, page) {
    var t = String(timestamp());
    var raw = 'keyword=' + wd + '&pageNum=1&pageSize=8&key=' + KEY + '&t=' + t;
    var path = '/api/mw-movie/anonymous/video/searchByWord?keyword=' + encodeUri(wd) + '&pageNum=1&pageSize=8';
    var j = parseJson(api(path, raw, t)) || {};
    var list = (j.data && j.data.result && j.data.result.list) || [];
    var out = [];
    for (var i = 0; i < list.length; i++) {
        if (Number(list[i].typeId1) !== 4) continue;   // 非动漫丢弃
        out.push(mapItem(list[i]));
    }
    return out;
}

// 首页 tab key → 列表 area 取值（'' = 不限地区）
var TAB_AREA = { '动漫': '', '日本': '日本', '大陆': '中国大陆' };

// 年份选项（贴合源站动漫年份档位）
var YEAR_OPTS = (function () {
    var o = [{ n: '全部', v: '' }];
    for (var y = (new Date()).getFullYear(); y >= 2010; y--) o.push({ n: String(y), v: String(y) });
    o.push({ n: '2009~2000', v: '2009~2000' }, { n: '更早', v: '更早' });
    return o;
})();
var AREA_OPTS = [{ n: '全部', v: '' }, { n: '中国大陆', v: '中国大陆' },
                 { n: '日本', v: '日本' }, { n: '美国', v: '美国' }, { n: '其他', v: '其他' }];

// 保留首页「推荐」（key=""）+ 只出动漫的子分类（全部/日番/国漫）；不暴露电影/剧/综艺大类。
function categories() {
    return JSON.stringify([
        { key: '',     title: '推荐' },
        { key: '动漫', title: '全部', filters: [
            { key: 'area', name: '地区', value: AREA_OPTS },
            { key: 'year', name: '年份', value: YEAR_OPTS }
        ] },
        { key: '日本', title: '日番', filters: [
            { key: 'year', name: '年份', value: YEAR_OPTS }
        ] },
        { key: '大陆', title: '国漫', filters: [
            { key: 'year', name: '年份', value: YEAR_OPTS }
        ] }
    ]);
}

function search(keyword, page) {
    page = page || 1;
    var key = trim(keyword);
    if (!key) return JSON.stringify(listVod('', '', page));               // 推荐/首页 = 最新动漫
    if (TAB_AREA.hasOwnProperty(key)) return JSON.stringify(listVod(TAB_AREA[key], '', page));
    return JSON.stringify(searchWord(key, page));
}

function searchFiltered(category, filtersJson, page) {
    var f = parseJson(filtersJson) || {};
    var cat = trim(category);
    var area = TAB_AREA.hasOwnProperty(cat) ? TAB_AREA[cat] : '';
    if (!area && f.area) area = f.area;
    return JSON.stringify(listVod(area, f.year || '', page || 1));
}

// 首页「精选」分区（key="" 的推荐页用这个渲染成多行横滑）。全部走 type1=4，保证只出动漫、不掺其它大类。
// 分区 key 尽量对齐 categories() 的 tab（''/日本/大陆），方便前台点分区跳到对应 tab。
function homeSections() {
    var buckets = [
        { title: '最新动漫', key: '',   area: '' },
        { title: '日本番',   key: '日本', area: '日本' },
        { title: '国产动漫', key: '大陆', area: '中国大陆' },
        { title: '欧美动漫', key: '',   area: '美国' }
    ];
    var out = [];
    for (var i = 0; i < buckets.length; i++) {
        var b = buckets[i];
        var items = listVod(b.area, '', 1);
        if (items.length) out.push({ title: b.title, key: b.key, items: items.slice(0, 12) });
    }
    return JSON.stringify(out);
}

function detail(id) {
    var out = { id: String(id), name: '', pic: '', desc: '', type: '', remarks: '', year: '', episodes: [] };
    var t = String(timestamp());
    var raw = 'id=' + id + '&key=' + KEY + '&t=' + t;
    var j = parseJson(api('/api/mw-movie/anonymous/video/detail?id=' + encodeUri(String(id)), raw, t)) || {};
    var d = j.data || {};
    out.name    = clean(d.vodName);
    out.pic     = trim(d.vodPic);
    out.desc    = clean(d.vodContent || d.vodBlurb);
    out.remarks = clean(d.vodRemarks);
    out.year    = yearStr(d.vodYear);
    out.type    = typeOf(d.vodArea);

    var eps = d.episodeList || [];
    for (var i = 0; i < eps.length; i++) {
        var nid = trim(eps[i].nid);
        if (!nid) continue;
        var name = clean(eps[i].name) || ('第' + (i + 1) + '集');
        // 选集 flag = id@nid，play() 拆开调播放接口
        out.episodes.push({ name: name, url: String(id) + '@' + nid, route: '在线播放' });
    }
    return JSON.stringify(out);
}

function play(flag) {
    var res = { url: '', type: 'auto' };
    var parts = String(flag || '').split('@');
    var id = trim(parts[0]);
    var nid = trim(parts[1] || '');
    if (!id || !nid) return JSON.stringify(res);

    var t = String(timestamp());
    var raw = 'id=' + id + '&nid=' + nid + '&key=' + KEY + '&t=' + t;
    var body = api('/api/mw-movie/anonymous/v2/video/episode/url?id=' + encodeUri(id) + '&nid=' + encodeUri(nid), raw, t);
    var j = parseJson(body) || {};
    var list = (j.data && j.data.list) || [];

    // data.list 每项是一档清晰度（蓝光/高清/标清…）：全量收集，按档次高→低排序
    var gears = [];
    for (var i = 0; i < list.length; i++) {
        var g = list[i]; if (!g) continue;
        var gu = trim(g.url);
        if (!gu || !/^https?:/i.test(gu)) continue;
        gears.push({ name: resName(g), url: gu, type: guessType(gu), rank: resRank(g) });
    }
    gears.sort(function (a, b) { return b.rank - a.rank; });

    // 去重：switchResolution 按「档名」匹配 → 档名必须唯一；同 url 也只留一条
    var resolutions = [], useen = {}, nseen = {};
    for (var k = 0; k < gears.length; k++) {
        var gg = gears[k];
        if (useen[gg.url] || nseen[gg.name]) continue;
        useen[gg.url] = 1; nseen[gg.name] = 1;
        resolutions.push({ name: gg.name, url: gg.url, type: gg.type });
    }
    if (!resolutions.length) return JSON.stringify(res);

    // 默认起播最高档（排序后首位；与反编译取 data.list[0] 行为一致，蓝光在首位）
    var best = resolutions[0];
    res.url = best.url;
    res.type = best.type;
    res.referer = host();
    res.headers = JSON.stringify({ 'Origin': host(), 'Referer': host(), 'User-Agent': CHROME_UA });
    // ≥2 档才下发档位列表，前台据此显示「清晰度」按钮可切换
    if (resolutions.length >= 2) res.resolutions = resolutions;
    return JSON.stringify(res);
}