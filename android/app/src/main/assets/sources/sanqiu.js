/*
 * 三秋影视（com.sunshine.tv / “三秋｜APP”）JS 源
 * 原型：TVBox spider  csp_App3Q（com.github.catvod.spider.App3Q），2026-06-13 实测还原
 * version: 1.0.0
 *
 * ⚠️ 站点本身是综合影视站（电影/剧集/动漫/综艺），本源【只出动漫】：
 *   - 首页/分类：固定走 /api.php/app/filter/vod?type_name=动漫，压根不请求影视分类；
 *   - 搜索：/api.php/app/search/index 的结果按 type_name==='动漫' 二次过滤，
 *           同名电影/电视剧/综艺一律丢弃 —— 跨源搜索、自动换源也只会命中动漫。
 *
 * 协议（2026-06-13 实测）：
 *   - 全部 GET，响应是明文 JSON（无加密）；请求头需带 x-sign 签名：
 *       sign = SHA-256("finger=<F>&id=com.sunshine.tv&nonce=<n>&sk=SK-thanks&time=<秒>&v=4")
 *              取 hex 大写（字段名按字母序拼接）
 *   - 列表/搜索项含 vod_id/vod_name/vod_pic/vod_remarks/vod_year/vod_area/type_name；
 *     filter 接口的 vod_area/vod_class 是数组，search 接口是逗号字符串（已兼容）。
 *   - 详情 get_detail：data[0] + 顶层 vodplayer[]（from→show 线路美化名映射）；
 *     vod_play_from 用 $$$ 分隔线路，vod_play_url 用 $$$ 分隔线路、# 分隔集、“集名$flag”。
 *   - 播放：每集是 flag（非直链），play() 调 /api.php/app/decode/url 解析出真实 m3u8
 *     （实测直接返回带 auth_key 的 .m3u8，无需 token/referer）。
 */

var SITE = (typeof ext !== 'undefined' && ext && ext.host)
    ? String(ext.host).replace(/\/+$/, '')
    : 'https://asd123sx23xdacsx.top';

var SIGN_FINGER = 'SF-C3B2B41F6EFFFF9869176CF68F6790E8F07506FC88632C94B4F5F0430D5498CA';
var SIGN_AID    = 'com.sunshine.tv';
var SIGN_SK     = 'SK-thanks';
var SIGN_VER    = '4';
var ANIME_TYPE  = '动漫';   // 站点“动漫”大类的 type_name，本源的过滤基准

function trim(s) { return s == null ? '' : String(s).replace(/^\s+|\s+$/g, ''); }
// 去 HTML 标签 + 解码常见实体（站点 vod_name/vod_content 里夹带 <p>、&#039; 等）
function clean(s) {
    if (s == null) return '';
    return trim(String(s)
        .replace(/<[^>]+>/g, '')
        .replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&')
        .replace(/&quot;/g, '"').replace(/&#0?39;/g, "'").replace(/&#x27;/gi, "'")
        .replace(/&lt;/g, '<').replace(/&gt;/g, '>')
        .replace(/&#(\d+);/g, function (m, d) { return String.fromCharCode(parseInt(d, 10)); }));
}

// filter 接口返回数组、search 接口返回逗号串，统一拍平成字符串
function asStr(v) {
    if (v == null) return '';
    if (typeof v === 'object' && v.length != null) return Array.prototype.join.call(v, ',');
    return String(v);
}
function yearStr(y) { y = parseInt(y, 10); return (y && y > 1900) ? String(y) : ''; }

// 封面救援：站点部分封面走 api.zxki.cn 图片代理，对“未授权”客户端只回一张
// “未授权”水印图（2026-06-13 实测：302 跳到 cdn.lewz.cn 的水印 PNG）。真实图藏在
// ?url= 参数里（多为豆瓣图，本身有防盗链、裸请求 403/418，Coil 不带 Referer 取不到）。
// 同站另有 cms.meilinvps.com/img.php 代理对未授权客户端直接回真图（服务端已处理防盗链），
// 故把 zxki 封面改投这个代理。其余封面（含已是 meilinvps / 直链）原样保留。
var MEILIN_PROXY = 'https://cms.meilinvps.com/img.php?url=';
function fixPic(pic) {
    pic = trim(pic);
    if (!pic) return '';
    var m = match(pic, 'zxki\\.cn/api/imgfdl\\?url=(.+)$', 1);
    if (m) return MEILIN_PROXY + m;   // m 是已编码好的内层真实图 URL，直接拼接
    return pic;
}
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

// 每次请求重算签名（time/nonce 实时生成，避免被服务端判过期）
function headers() {
    var t = String(Math.floor(timestamp() / 1000));
    var n = String(Math.floor(Math.random() * 999) + 1);
    var raw = 'finger=' + SIGN_FINGER + '&id=' + SIGN_AID + '&nonce=' + n +
              '&sk=' + SIGN_SK + '&time=' + t + '&v=' + SIGN_VER;
    return {
        'user-agent':     'okhttp/4.12.0',
        'x-ave':          SIGN_VER,
        'x-aid':          SIGN_AID,
        'x-time':         t,
        'x-nonc':         n,
        'x-sign':         String(sha256(raw)).toUpperCase(),
        'x-device-id':    '0b4328287a5d953e',
        'x-device-brand': 'OnePlus',
        'x-device-model': 'HD1900',
        'x-update-id':    '73dc2ffc-8350-c022-fac9-da982c95f513'
    };
}
function apiGet(path) {
    return request(SITE + path, JSON.stringify({ headers: headers(), timeout: 20000 })) || '';
}

function mapItem(it) {
    return {
        id:      String(it.vod_id),
        name:    clean(it.vod_name),
        pic:     fixPic(it.vod_pic),
        type:    typeOf(asStr(it.vod_area)),
        year:    yearStr(it.vod_year),
        remarks: clean(it.vod_remarks),
        desc:    ''
    };
}

// 动漫片库分页拉取（永远带 type_name=动漫，保证只出动漫）
function filterVod(area, year, sort, page) {
    page = page || 1;
    sort = sort || 'hits';
    var p = '/api.php/app/filter/vod?type_name=' + encodeUri(ANIME_TYPE) + '&page=' + page + '&sort=' + sort;
    if (area) p += '&area=' + encodeUri(area);
    if (year) p += '&year=' + encodeUri(String(year));
    var j = parseJson(apiGet(p)) || {};
    var list = j.data || [];
    var out = [];
    for (var i = 0; i < list.length; i++) out.push(mapItem(list[i]));
    return out;
}

// 关键词搜索：结果按 type_name 只留动漫
function searchIndex(wd, page) {
    page = page || 1;
    var j = parseJson(apiGet('/api.php/app/search/index?wd=' + encodeUri(wd) + '&page=' + page + '&limit=15')) || {};
    var list = j.data || [];
    var out = [];
    for (var i = 0; i < list.length; i++) {
        if (trim(list[i].type_name) !== ANIME_TYPE) continue;
        out.push(mapItem(list[i]));
    }
    return out;
}

// 首页 tab 的 key → filter 的 area 取值（'' = 不限地区）
var TAB_AREA = { '动漫': '', '日本': '日本', '大陆': '大陆' };

function yearOpts() {
    var o = [{ n: '全部', v: '' }];
    for (var y = (new Date()).getFullYear(); y >= 2000; y--) o.push({ n: String(y), v: String(y) });
    return o;
}
var SORT_OPTS = [{ n: '最热', v: 'hits' }, { n: '最新', v: 'time' }, { n: '评分', v: 'score' }];
var AREA_OPTS = [{ n: '全部', v: '' }, { n: '日本', v: '日本' }, { n: '大陆', v: '大陆' },
                 { n: '欧美', v: '欧美' }, { n: '韩国', v: '韩国' }];

function categories() {
    return JSON.stringify([
        { key: '',     title: '推荐' },
        { key: '动漫', title: '全部', filters: [
            { key: 'area', name: '地区', value: AREA_OPTS },
            { key: 'year', name: '年份', value: yearOpts() },
            { key: 'sort', name: '排序', value: SORT_OPTS }
        ] },
        { key: '日本', title: '日番', filters: [
            { key: 'year', name: '年份', value: yearOpts() },
            { key: 'sort', name: '排序', value: SORT_OPTS }
        ] },
        { key: '大陆', title: '国漫', filters: [
            { key: 'year', name: '年份', value: yearOpts() },
            { key: 'sort', name: '排序', value: SORT_OPTS }
        ] }
    ]);
}

function search(keyword, page) {
    page = page || 1;
    var key = trim(keyword);
    if (!key) return JSON.stringify(filterVod('', '', 'time', page));   // 推荐 = 最新动漫
    if (TAB_AREA.hasOwnProperty(key)) return JSON.stringify(filterVod(TAB_AREA[key], '', 'hits', page));
    return JSON.stringify(searchIndex(key, page));
}

function searchFiltered(category, filtersJson, page) {
    var f = parseJson(filtersJson) || {};
    var cat = trim(category);
    var area = TAB_AREA.hasOwnProperty(cat) ? TAB_AREA[cat] : '';
    if (!area && f.area) area = f.area;   // “全部”tab 用用户选的地区
    return JSON.stringify(filterVod(area, f.year || '', f.sort || 'hits', page || 1));
}

function detail(id) {
    var out = { id: String(id), name: '', pic: '', desc: '', type: '', remarks: '', year: '', episodes: [] };
    var j = parseJson(apiGet('/api.php/app/vod/get_detail?vod_id=' + encodeUri(String(id)))) || {};
    var d = (j.data || [])[0] || {};
    out.name    = clean(d.vod_name);
    out.pic     = fixPic(d.vod_pic);
    out.desc    = clean(d.vod_content);
    out.remarks = clean(d.vod_remarks);
    out.year    = yearStr(d.vod_year);
    out.type    = typeOf(asStr(d.vod_area));

    var showMap = {};
    var vp = j.vodplayer || [];
    for (var v = 0; v < vp.length; v++) showMap[trim(vp[v].from)] = trim(vp[v].show);

    var froms = String(d.vod_play_from || '').split('$$$');
    var lines = String(d.vod_play_url || '').split('$$$');
    for (var li = 0; li < lines.length; li++) {
        var from  = trim(froms[li] || ('line' + li));
        var route = showMap[from] || from;
        var eps = String(lines[li]).split('#');
        for (var ei = 0; ei < eps.length; ei++) {
            var seg = eps[ei];
            if (!seg) continue;
            var idx = seg.indexOf('$');
            var epName = idx >= 0 ? trim(seg.substring(0, idx)) : ('第' + (ei + 1) + '集');
            var flag   = idx >= 0 ? trim(seg.substring(idx + 1)) : trim(seg);
            if (!flag) continue;
            // 把线路 from 编进 url，play() 解析时要靠它调 decode 接口；用 @@ 分隔避免与 flag 内字符冲突
            out.episodes.push({ name: epName, url: flag + '@@' + from, route: route });
        }
    }
    return JSON.stringify(out);
}

function play(flag) {
    var res = { url: '', type: 'auto' };
    var parts = String(flag || '').split('@@');
    var real = trim(parts[0]);
    var from = trim(parts[1] || '');

    // 已经是直链就直接用
    if (/^https?:\/\//i.test(real) || /\.(m3u8|mp4|flv|mkv|avi|mov)/i.test(real)) {
        res.url = real; res.type = guessType(real);
        return JSON.stringify(res);
    }
    // flag → decode 接口换真实地址（偶发空响应，重试 3 次）
    for (var i = 0; i < 3; i++) {
        var body = apiGet('/api.php/app/decode/url/?url=' + encodeUri(real) + '&vodFrom=' + encodeUri(from));
        if (body) {
            var j = parseJson(body) || {};
            var u = trim(j.data);
            if (u && /^https?:/i.test(u)) {
                res.url = u; res.type = guessType(u);
                return JSON.stringify(res);
            }
        }
    }
    return JSON.stringify(res);
}