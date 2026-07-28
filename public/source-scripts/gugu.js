/*
 * 咕咕｜动漫（gugu） JS 源
 * 还原自 TVBox csp_AppGet（spider.jar 反编译：com.github.catvod.spider.AppGet）。
 * version: 1.0.0
 *
 * 协议要点（逐段对照 AppGet.java / merge.m.a 加解密 / merge.k.b·k.c HTTP）：
 *   1) 所有业务接口都是 POST {BASE}/api.php{path}，body = 明文 JSON / 表单串，
 *      Content-Type 用 application/x-www-form-urlencoded（gugu 后端 JSON 与表单均可，统一走表单）。
 *      请求头额外带：app-user-device-id / app-version-code / app-api-verify-time / app-ui-mode。
 *   2) 响应是 {code,data,...}，其中 data 是 base64 密文：AES/CBC/PKCS7(dataKey,dataIv) 解开即明文 JSON。
 *   3) 播放地址 url 在详情里是「AES/CBC/PKCS5 加密 → base64」后塞进 parse_api 串，
 *      播放时按 playerContent 树解析；最常见路线是带 app-api-verify-sign 的 vodParse。
 *
 * 本源 dataKey == dataIv == 'nKfZ8KX6JTNWRzTD'（16B AES-128），BASE = https://www.gugu3.com。
 * 实测分类：0=全部 / 6=番剧 / 21=剧场版 / 23=特摄。
 * TVBox 配置里没有 deviceId/version/ua/token，所以这些头按原样发空串。
 */

// ─────────────────────────────────────────────── 配置（ext 覆盖，否则用内置默认）
var EXT       = (typeof ext !== 'undefined' && ext) ? ext : {};
var BASE      = (EXT.url || 'https://www.gugu3.com').replace(/\/+$/, '');
var DATA_KEY  = EXT.dataKey || 'nKfZ8KX6JTNWRzTD';
var DATA_IV   = EXT.dataIv  || 'nKfZ8KX6JTNWRzTD';
var DEVICE_ID = EXT.deviceId || '';
var VERSION   = EXT.version  || '';
var TOKEN     = EXT.token    || '';
var UA_API    = EXT.ua || 'okhttp/3.14.9';
var UA_WEB    = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';
var TIMEOUT   = 20000;

// AES/CBC：解密=base64→utf8，加密=utf8→base64（均默认）。PKCS7 在 JCE 里同 PKCS5。iv 即 key。
var AES = { mode: 'CBC', padding: 'PKCS7', keyFormat: 'utf8', ivFormat: 'utf8', iv: DATA_IV };

// ─────────────────────────────────────────────── 运行期缓存（懒加载）
var HOME_DONE   = false;
var TYPES       = [];     // [{id, name, filters}] 分类 tab（不含「全部」）
var RECOMMEND   = [];     // initV119 的 recommend_list（gugu 通常为空，banner_list 才有）
var BANNER      = [];     // initV119 的 banner_list（首页轮播位）
var ALL_ID      = '0';    // 「全部」分类 id，用作「推荐」默认 + 首页最新行

// 服务端筛选维度 → 中文标签（还原 AppGet.createFilterItem）。
var FILTER_LABEL = { 'class': '类型', 'area': '地区', 'lang': '语言', 'year': '年份', 'sort': '排序' };
var FILTER_KEYS  = ['class', 'area', 'lang', 'year', 'sort'];

// ─────────────────────────────────────────────── 工具
function trim(s) { return s == null ? '' : String(s).replace(/^\s+|\s+$/g, ''); }
function nowSec() { return Math.floor(new Date().getTime() / 1000); }
function stripTags(s) { return s ? String(s).replace(/<[^>]+>/g, '') : ''; }

function decData(b64) {
    var s = trim(b64);
    if (!s) return '';
    try { return crypto.aes.decrypt(s, DATA_KEY, AES); } catch (e) { return ''; }
}
function encUrl(plain) {
    try { return crypto.aes.encrypt(plain == null ? '' : String(plain), DATA_KEY, AES); } catch (e) { return ''; }
}

function decodeEntities(s) {
    if (!s) return '';
    s = String(s)
        .replace(/&nbsp;/gi, ' ').replace(/&amp;/gi, '&').replace(/&lt;/gi, '<').replace(/&gt;/gi, '>')
        .replace(/&quot;/gi, '"').replace(/&apos;/gi, "'").replace(/&#0?39;/g, "'")
        .replace(/&ldquo;/gi, '\u201c').replace(/&rdquo;/gi, '\u201d')
        .replace(/&lsquo;/gi, '\u2018').replace(/&rsquo;/gi, '\u2019')
        .replace(/&middot;/gi, '\u00b7').replace(/&mdash;/gi, '\u2014').replace(/&hellip;/gi, '\u2026');
    s = s.replace(/&#x([0-9a-fA-F]+);/g, function (m, h) { try { return String.fromCharCode(parseInt(h, 16)); } catch (e) { return m; } });
    s = s.replace(/&#(\d+);/g, function (m, d) { try { return String.fromCharCode(parseInt(d, 10)); } catch (e) { return m; } });
    return s;
}
function limitCats(s, n) {
    if (!s) return '';
    var parts = String(s).split(/[\s,，、\/\uff0f|\uff5c\u00b7]+/);
    var out = [], max = n || 2;
    for (var i = 0; i < parts.length && out.length < max; i++) { var p = trim(parts[i]); if (p) out.push(p); }
    return out.join(' ');
}
function guessType(u) {
    var l = (u || '').toLowerCase();
    if (l.indexOf('.m3u8') >= 0) return 'm3u8';
    if (l.indexOf('.mp4') >= 0)  return 'mp4';
    return 'auto';
}

// ─────────────────────────────────────────────── HTTP（POST 表单串，响应 data 解密）
function apiHeaders() {
    var h = {
        'User-Agent': UA_API,
        'Content-Type': 'application/x-www-form-urlencoded',
        'app-user-device-id': DEVICE_ID,
        'app-version-code': VERSION,
        'app-api-verify-time': '' + nowSec(),
        'app-ui-mode': 'light'
    };
    if (TOKEN) h['app-user-token'] = TOKEN;
    return h;
}

// 把对象序列化成 application/x-www-form-urlencoded 表单串。
function toForm(obj) {
    var parts = [];
    for (var k in obj) { if (obj.hasOwnProperty(k)) parts.push(encodeUri(k) + '=' + encodeUri(obj[k] == null ? '' : String(obj[k]))); }
    return parts.join('&');
}

// POST {BASE}/api.php{path}（path 可带 ?query），body = 表单串；返回解密后的明文 JSON 串。
function apiPost(path, params) {
    try {
        var body = (typeof params === 'string') ? params : toForm(params || {});
        var resp = post(BASE + '/api.php' + path, body, JSON.stringify({ headers: apiHeaders(), timeout: TIMEOUT }));
        var rj = parseJson(resp) || {};
        var data = rj.data || '';
        if (!data) { log('[gugu] empty data: ' + path); return ''; }
        return decData(data);
    } catch (e) { log('[gugu] apiPost err ' + path + ': ' + e); return ''; }
}

// ─────────────────────────────────────────────── 数据映射
function toItem(v) {
    return {
        id: (v.vod_id == null) ? '' : String(v.vod_id),
        name: decodeEntities(v.vod_name || ''),
        pic: v.vod_pic || '',
        type: limitCats(v.vod_class || v.type_name || '', 2),
        year: v.vod_year || '',
        remarks: decodeEntities(v.vod_remarks || ''),
        desc: ''
    };
}
function mapList(arr) {
    var out = [];
    if (!arr) return out;
    for (var i = 0; i < arr.length; i++) { if (arr[i] && arr[i].vod_id != null) out.push(toItem(arr[i])); }
    return out;
}

// 把 initV119 里某分类的 filter_type_list 转成 TVBox 数组式筛选：
//   [{key, name, value:[{n,v}, ...]}]；class/area/lang/year 的「全部」提交空值；sort 无「全部」时补一个「默认」(空值)。
function buildFilters(filterTypeList) {
    var out = [];
    if (!filterTypeList) return out;
    for (var i = 0; i < filterTypeList.length; i++) {
        var f = filterTypeList[i] || {};
        var name = f.name || '';
        if (!FILTER_LABEL[name]) continue;      // 只收已知维度
        var lst = f.list || [];
        var values = [], hasReset = false;
        for (var j = 0; j < lst.length; j++) {
            var opt = trim(lst[j]);
            if (!opt) continue;
            var v = (opt === '全部') ? '' : opt;
            if (v === '') hasReset = true;
            values.push({ n: opt, v: v });
        }
        if (!values.length) continue;
        if (!hasReset) values.unshift({ n: '默认', v: '' });   // 给 sort 这类没「全部」的维度补中性默认项
        out.push({ key: name, name: FILTER_LABEL[name], value: values });
    }
    return out;
}

// ─────────────────────────────────────────────── 初始化（initV119：分类 + 筛选 + 推荐 + 轮播）
function ensureHome() {
    if (HOME_DONE) return;
    try {
        var hj = parseJson(apiPost('/getappapi.index/initV119', '')) || {};
        var tl = hj.type_list || [];
        // 服务器限流 / 网络失败导致 initV119 拿不到数据时，type_list 为空：
        // 此时不置位 HOME_DONE，让下一次 categories()/homeSections()/search() 自动重试。
        // 修复：之前在请求“之前”就 HOME_DONE=true，首请求被高防 RST 后整个会话分类永久为空，
        //       用户换 IP 也不会刷新（引擎状态卡住，除非重启 App 重载脚本）。
        if (!tl.length) return;
        RECOMMEND = mapList(hj.recommend_list);
        BANNER    = mapList(hj.banner_list);
        for (var i = 0; i < tl.length; i++) {
            var t = tl[i];
            var name = t.type_name || '';
            // 跟随原 App 过滤掉广告/敏感分类
            if (name.indexOf('正版QQ群') >= 0 || name === '伦理' || name === '福利' || name === '小影院') continue;
            var id = (t.type_id == null) ? '' : String(t.type_id);
            if (!id) continue;
            // 「全部」(type_id=0) 不单列 tab —— 「推荐」已映射到它
            if (id === '0' || name === '全部') { ALL_ID = id; continue; }
            TYPES.push({ id: id, name: name || ('分类' + id), filters: buildFilters(t.filter_type_list) });
        }
        HOME_DONE = true;
    } catch (e) { log('[gugu] ensureHome err: ' + e); }
}

function isTypeId(key) {
    ensureHome();
    for (var i = 0; i < TYPES.length; i++) if (TYPES[i].id === key) return true;
    return false;
}

// 分类筛选页（typeFilterVodList）。extra = 选中的 {class/area/lang/year/sort}（仅非空项提交，服务端筛选）。
function typeFilter(typeId, page, extra) {
    var p = page || 1;
    var body = { type_id: String(typeId), page: String(p) };
    if (extra) {
        for (var i = 0; i < FILTER_KEYS.length; i++) {
            var k = FILTER_KEYS[i], v = extra[k];
            if (v != null && String(v) !== '') body[k] = String(v);
        }
    }
    var rj = parseJson(apiPost('/getappapi.index/typeFilterVodList?page=' + p, body)) || {};
    return mapList(rj.recommend_list);
}

// ─────────────────────────────────────────────── 契约入口
function categories() {
    ensureHome();
    var out = [{ key: '', title: '推荐' }];
    for (var i = 0; i < TYPES.length; i++) {
        var t = TYPES[i], c = { key: t.id, title: t.name };
        if (t.filters && t.filters.length) c.filters = t.filters;   // 服务端筛选（类型/地区/语言/年份/排序）
        out.push(c);
    }
    return JSON.stringify(out);
}

function homeSections() {
    ensureHome();
    var out = [];
    // 优先用轮播位/推荐位当热门，二者皆空时退回「全部」最新一行
    var firstItems = BANNER.length ? BANNER : (RECOMMEND.length ? RECOMMEND : typeFilter(ALL_ID, 1));
    if (firstItems.length) out.push({ title: '热门推荐', key: '', items: firstItems.slice(0, 12) });
    var rows = Math.min(TYPES.length, 4);
    for (var i = 0; i < rows; i++) {
        try {
            var items = typeFilter(TYPES[i].id, 1);
            if (items.length) out.push({ title: TYPES[i].name, key: TYPES[i].id, items: items.slice(0, 12) });
        } catch (e) { log('[gugu] home row err: ' + e); }
    }
    return JSON.stringify(out);
}

// search 同时承担分类浏览与关键词搜索：
//   空 key → 「全部」分类；纯数字且命中 type_id → 分类页；否则关键词搜索。
function search(keyword, page) {
    page = page || 1;
    var key = trim(keyword || '');
    ensureHome();
    if (!key) return JSON.stringify(typeFilter(ALL_ID, page));
    if (/^\d+$/.test(key) && isTypeId(key)) return JSON.stringify(typeFilter(key, page));
    var rj = parseJson(apiPost('/getappapi.index/searchList', { type_id: 0, keywords: key, page: page })) || {};
    return JSON.stringify(mapList(rj.search_list));
}

// 选了筛选条 → 直接走服务端 typeFilterVodList（class/area/lang/year/sort）。
function searchFiltered(category, filtersJson, page) {
    ensureHome();
    var f = parseJson(filtersJson) || {};
    // 非分类 tab（如「推荐」空 key 或关键词）→ 退回普通逻辑
    if (!(category && /^\d+$/.test(category) && isTypeId(category))) return search(category, page);
    return JSON.stringify(typeFilter(category, page, f));
}

function detail(id) {
    var out = { id: id, name: '', pic: '', type: '', year: '', remarks: '', desc: '', episodes: [] };
    try {
        var dj = parseJson(apiPost('/getappapi.index/vodDetail', { vod_id: String(id) })) || {};
        var d = dj.vod || {};
        out.name    = decodeEntities(d.vod_name || '');
        out.pic     = d.vod_pic || '';
        out.type    = limitCats(d.vod_class || '', 2);
        out.year    = d.vod_year || '';
        out.remarks = decodeEntities(d.vod_remarks || '');
        out.desc    = trim(decodeEntities(stripTags(d.vod_content || '')));

        var lines = dj.vod_play_list || [];
        for (var i = 0; i < lines.length; i++) {
            var ln = lines[i] || {};
            var pinfo = ln.player_info || {};
            var show = (pinfo.show || ('线路' + (i + 1)));
            var parse = pinfo.parse || '';
            var urls = ln.urls || [];
            for (var j = 0; j < urls.length; j++) {
                var ep = urls[j] || {};
                // flag 塞够 play() 所需：parse_api_url / parse / 集url / token
                var flag = JSON.stringify({
                    p: ep.parse_api_url || '',
                    parse: parse,
                    u: ep.url || '',
                    t: ep.token || '',
                    nid: ep.nid || ''
                });
                out.episodes.push({ name: ep.name || ('' + (j + 1)), url: flag, route: show });
            }
        }
    } catch (e) { log('[gugu] detail err: ' + e); }
    return JSON.stringify(out);
}

// edu：把 url=...&token 之间的值做 url 编码（base64 里有 +//= 需转义，否则被表单解析破坏）。
function edu(s) {
    return String(s).replace(/(url=)([\s\S]*?)(?=&token)/, function (m, a, b) { return a + encodeUri(b); });
}
// eduAesDecode：把 &url=<密文> 解回明文（playerContent 第 3 分支用）。
function eduAesDecode(s) {
    return String(s).replace(/(&url=)([\s\S]*?)(?=&token)/, function (m, a, b) {
        var d = '';
        try { d = crypto.aes.decrypt(b, DATA_KEY, AES); } catch (e) { d = b; }
        return a + (d || b);
    });
}

// vodParse：带 app-api-verify-sign 的 POST，响应 data 解密后取 .json.url（还原 AppGet.c）。
function vodParse(bodyStr) {
    try {
        var ts = '' + nowSec();
        var h = {
            'User-Agent': UA_API,
            'Connection': 'Keep-Alive',
            'Content-Type': 'application/x-www-form-urlencoded',
            'app-version-code': VERSION,
            'app-ui-mode': 'light',
            'app-user-device-id': DEVICE_ID,
            'app-api-verify-time': ts,
            'app-api-verify-sign': encUrl(ts)
        };
        if (TOKEN) h['app-user-token'] = TOKEN;
        var resp = post(BASE + '/api.php/getappapi.index/vodParse', bodyStr, JSON.stringify({ headers: h, timeout: TIMEOUT }));
        var dec = decData((parseJson(resp) || {}).data || '');
        var obj = parseJson(dec) || {};
        var jf = obj.json;
        if (typeof jf === 'string') { var jo = parseJson(jf) || {}; return jo.url || ''; }
        if (jf && jf.url) return jf.url;
        return obj.url || '';
    } catch (e) { log('[gugu] vodParse err: ' + e); return ''; }
}

function playResult(url, ua) {
    return JSON.stringify({ url: url, type: guessType(url), referer: '', headers: JSON.stringify({ 'User-Agent': ua || UA_WEB }) });
}

function play(flag) {
    var empty = JSON.stringify({ url: '', type: 'auto', referer: '' });
    try {
        var f = parseJson(flag) || {};
        var p = f.p || '', parse = f.parse || '', u = f.u || '', t = f.t || '';

        // 构造 strEduAesDecode：parse_api_url 是 http 直接用；否则拼 parse_api=...&url=<enc>&token=
        var s = /^https?:/i.test(p) ? p : ('parse_api=' + parse + '&url=' + encUrl(u) + '&token=' + t);

        // 1) http 解析地址且带 ?url=/?key=：GET 取 .url（明文 JSON 或正则）
        if (/^https?:/i.test(s) && (s.indexOf('?url=') >= 0 || s.indexOf('?key=') >= 0)) {
            var body = trim(request(s, JSON.stringify({ headers: { 'User-Agent': UA_WEB }, timeout: TIMEOUT })));
            var real = '';
            if (body.charAt(0) === '{') real = (parseJson(body) || {}).url || '';
            else { var m = body.match(/"url"\s*:\s*"([^"]+)"/); if (m) real = m[1]; }
            if (real) return playResult(real, UA_WEB);
        }
        // 2) 本身就是直链媒体
        if (/(m3u8|mp4|mkv)/i.test(s)) return playResult(s, UA_API);
        // 3) parse_api html / ?url= / ?key=：解回明文后 GET (parse + url) 取 .data.url
        if (s.indexOf('?url=') >= 0 || s.indexOf('?key=') >= 0 || s.indexOf('html') >= 0) {
            var s2 = eduAesDecode(s);
            var mm = s2.match(/parse_api=([\s\S]*?)(?=&token)/);
            if (mm) {
                var resp = request(mm[1], JSON.stringify({ headers: { 'User-Agent': UA_WEB }, timeout: TIMEOUT }));
                var dj = parseJson(resp) || {};
                var real2 = (dj.data && dj.data.url) || dj.url || '';
                if (real2) return playResult(real2, UA_WEB);
            }
        }
        // 4) 兜底：vodParse（最常见的非直链路线）
        var real3 = vodParse(edu(s));
        if (real3) return playResult(real3, UA_WEB);

        log('[gugu] play unresolved, s=' + s.substring(0, 120));
    } catch (e) { log('[gugu] play err: ' + e); }
    return empty;
}