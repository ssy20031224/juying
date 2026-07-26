/*
 * 稀饭动漫（anime.xifanacg.com） JS 源
 * 苹果 CMS · dsn2 模板 · bangumi/watch 路由 · 纯 HTML 抓取
 * version: 1.2.1
 *
 * 1.2.1：删 search() 里 '完结' 的死分支（TYPE_MAP 已含 '完结'，上一行即命中）
 * 1.2.0：+homeSections（首页分区，去掉兜底布局的"热门更新→最近更新"死链更多）；
 *        play() 返回 startSec:5（本源所有视频固定 5 秒起播）
 *
 * 实测（2026-06-11，iPhone UA）：
 *   - 列表/详情：/bangumi/{id}.html
 *   - 播放：/watch/{id}/{sid}/{nid}.html → player_aaaa encrypt=0 直出 mp4/m3u8。
 *     ⚠️ 主线-1（apn.moedot.net → 302 联通沃盘签名直链）对带 Referer 的请求直接 400，
 *     三条线路实测都不需要 Referer → play() 返回 referer:'never' 明确禁发；
 *     备用线 m3u8 路径含未编码中文（/新番/…）→ 返回前转义非 ASCII 字符。
 *   - 分类：/type/1 连载新番 · /type/2 完结旧番 · /type/3 剧场版 · /type/21 美漫；分页 /type/1-2.html
 *   - 筛选：/show/{tid}/... 页面是 AJAX 壳（HTML 里没有条目），真实数据来自
 *     POST /index.php/ds_api/vod {type,class,year,letter,by,page}（必须带 X-Requested-With 头，
 *     普通 GET 被模板防盗链拦截）→ JSON {code,list:[{url,vod_name,vod_pic,...}],pagecount}
 *   - 搜索：/search.html?wd= 是 ds-verify 验证码页（抓不到）；改用免验证码的
 *           JSON 联想接口 /index.php/ajax/suggest?mid=1&wd=，其 id 即 bangumi id
 */

var SITE     = 'https://anime.xifanacg.com';
var REQ_OPT  = JSON.stringify({ ua: 'iphone', timeout: 15000 });
var REFERER  = SITE + '/';
var SNIFF_UA = (typeof UA !== 'undefined' && UA.iphone) || 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1';

var TYPE_MAP = { '日漫': '1', '完结': '2', '剧场版': '3', '欧美': '21' };
var APP_TYPE = { '1': '日漫', '2': '日漫', '3': '剧场版', '21': '欧美' };

function trim(s) { return s == null ? '' : String(s).replace(/^\s+|\s+$/g, ''); }
function clean(s) {
    if (!s) return '';
    return trim(String(s)
        .replace(/<[^>]+>/g, '')
        .replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&')
        .replace(/&quot;/g, '"').replace(/&#0?39;/g, "'")
        .replace(/[\u3000\s]+/g, ' '));
}
function abs(u) {
    if (!u) return '';
    u = String(u).replace(/&amp;/g, '&');
    if (/^https?:/i.test(u)) return u;
    if (u.indexOf('//') === 0) return 'https:' + u;
    if (u.charAt(0) === '/') return SITE + u;
    return SITE + '/' + u;
}
function guessType(u) {
    var l = (u || '').toLowerCase();
    if (l.indexOf('.m3u8') >= 0) return 'm3u8';
    if (l.indexOf('.mp4') >= 0) return 'mp4';
    if (l.indexOf('.flv') >= 0) return 'flv';
    return 'auto';
}
// ── 分类筛选维度（2026-06-11 实测：类型仅 type 1/2 站点有配置，3/21 只有年份）──
var GENRES = ['搞笑','原创','轻小说改','恋爱','百合','漫改','校园','战斗','治愈','奇幻',
              '日常','青春','乙女向','悬疑','后宫','科幻','冒险','热血','异世界','游戏改',
              '音乐','偶像','美食','耽美'];

function strOpts(arr) {
    var out = [{ n: '全部', v: '' }];
    for (var i = 0; i < arr.length; i++) out.push({ n: arr[i], v: arr[i] });
    return out;
}
function yearOpts(minYear) {
    var out = [{ n: '全部', v: '' }];
    for (var y = (new Date()).getFullYear(); y >= minYear; y--) out.push({ n: String(y), v: String(y) });
    return out;
}
function showFilters(withClass) {
    var fs = [];
    if (withClass) fs.push({ key: 'class', name: '类型', value: strOpts(GENRES) });
    fs.push({ key: 'year', name: '年份', value: yearOpts(2004) });
    fs.push({ key: 'by',   name: '排序', value: [{ n: '默认', v: '' }, { n: '最新', v: 'time' }, { n: '最热', v: 'hits' }, { n: '评分', v: 'score' }] });
    return fs;
}
function isBlocked(html) {
    if (!html) return true;
    return html.indexOf('ds-verify') >= 0
        || html.indexOf('verify/index.html') >= 0
        || html.indexOf('请输入验证码') >= 0;
}

function parseList(html, typeName) {
    if (!html || isBlocked(html)) return [];
    var rows = parseJson(matchAll(html, 'href="/bangumi/(\\d+)\\.html"')) || [];
    var out = [], seen = {};
    for (var i = 0; i < rows.length; i++) {
        var id = rows[i][1];
        if (!id || seen[id]) continue;
        seen[id] = 1;
        var dl = '/bangumi/' + id + '\\.html';
        var name = match(html, dl + '"[^>]*title="([^"]+)"', 1);
        var pic  = match(html, dl + '"[\\s\\S]{0,360}?data-src="([^"]+)"', 1);
        var note = match(html, dl + '[\\s\\S]{0,360}?public-list-prb[^>]*>([^<]+)<', 1);
        var year = match(html, dl + '[\\s\\S]{0,500}?/search/year/((?:19|20)\\d{2})\\.html', 1);
        var nm = clean(name);
        if (!nm) continue;
        out.push({ id: id, name: nm, pic: abs(pic), type: typeName || '', year: year || '', remarks: clean(note), desc: '' });
        if (out.length >= 40) break;
    }
    return out;
}

function browseType(tid, page, typeName) {
    page = page || 1;
    var url = SITE + '/type/' + tid + (page > 1 ? '-' + page : '') + '.html';
    return parseList(request(url, REQ_OPT), typeName || APP_TYPE[tid] || '');
}

// 免验证码的 JSON 联想搜索：id 即 bangumi 详情 id
function suggestSearch(key) {
    var url = SITE + '/index.php/ajax/suggest?mid=1&limit=30&wd=' + encodeUri(key);
    var j = parseJson(request(url, REQ_OPT) || '') || {};
    var list = j.list || [];
    var out = [];
    for (var i = 0; i < list.length; i++) {
        var it = list[i];
        if (!it || it.id == null) continue;
        out.push({ id: String(it.id), name: clean(it.name), pic: abs(it.pic), type: '', year: '', remarks: '', desc: '' });
    }
    return out;
}

function categories() {
    return JSON.stringify([
        { key: '',       title: '推荐' },
        { key: '日漫',   title: '连载新番', filters: showFilters(true) },
        { key: '完结',   title: '完结旧番', filters: showFilters(true) },
        { key: '剧场版', title: '剧场版',   filters: showFilters(false) },
        { key: '欧美',   title: '美漫',     filters: showFilters(false) }
    ]);
}

function search(keyword, page) {
    page = page || 1;
    var key = trim(keyword);
    if (!key) return JSON.stringify(parseList(request(SITE + '/', REQ_OPT), ''));
    if (TYPE_MAP[key]) return JSON.stringify(browseType(TYPE_MAP[key], page, APP_TYPE[TYPE_MAP[key]] || key));
    if (page > 1) return '[]';                       // 联想接口不分页
    return JSON.stringify(suggestSearch(key));
}

// 筛选数据接口：/show 页面是 AJAX 壳，列表实际来自 ds_api/vod。
// 必须 POST + X-Requested-With 头（普通 GET 会被模板防盗链拦截，只回「短视主题」文案）。
// 接口失败返回 null，由调用方兜底。
function apiFiltered(tid, f, page) {
    var body = 'type=' + tid + '&page=' + (page || 1)
             + '&class='  + encodeUri(f['class'] || '')
             + '&year='   + encodeUri(f.year || '')
             + '&letter=' + encodeUri(f.letter || '')
             + '&by='     + (f.by || '');
    var opt = JSON.stringify({
        ua: 'iphone', timeout: 15000,
        headers: { 'X-Requested-With': 'XMLHttpRequest', 'Referer': SITE + '/show/' + tid + '.html' }
    });
    var j = parseJson(post(SITE + '/index.php/ds_api/vod', body, opt) || '') || {};
    if (j.code != 1 || !j.list) return null;
    var out = [];
    for (var i = 0; i < j.list.length; i++) {
        var it = j.list[i];
        var id = match(String(it.url || ''), '/bangumi/(\\d+)\\.html', 1);
        if (!id) continue;
        out.push({
            id:      id,
            name:    clean(it.vod_name),
            pic:     abs(it.vod_pic),
            type:    APP_TYPE[tid] || '',
            year:    String(f.year || ''),
            remarks: clean(it.vod_remarks || it.vod_serial || ''),
            desc:    clean(it.vod_blurb || '')
        });
    }
    return out;
}

function searchFiltered(category, filtersJson, page) {
    var f = parseJson(filtersJson) || {};
    var cat = trim(category);
    var tid = TYPE_MAP[cat] || '1';
    page = page || 1;
    var list = apiFiltered(tid, f, page);
    if (list === null) list = browseType(tid, page, cat || APP_TYPE[tid] || '');  // 接口失败兜底：无筛选浏览
    return JSON.stringify(list);
}

function detail(id) {
    var out = { id: id, name: '', pic: '', desc: '', type: '', remarks: '', year: '', episodes: [] };
    var html = request(SITE + '/bangumi/' + id + '.html', REQ_OPT) || '';
    if (!html) return JSON.stringify(out);

    out.name = clean(match(html, 'slide-info-title[^>]*>([^<]+)', 1)
                  || match(html, 'property="og:title"\\s+content="([^"]+)"', 1));
    out.pic  = abs(match(html, 'mask-this2[^>]*data-src="([^"]+)"', 1)
                || match(html, 'bangumi/' + id + '[\\s\\S]{0,400}?data-src="([^"]+)"', 1));
    out.desc = clean(match(html, 'name="description"\\s+content="([^"]{10,})"', 1));
    out.year = match(html, '/search/year/((?:19|20)\\d{2})\\.html', 1) || '';
    // 备注：主信息区第一个 slide-info-remarks（更新状态，如「10|周三22:10」）
    out.remarks = clean(match(html, 'slide-info-remarks cor5">([^<]+)<', 1));
    // 分类：「类型 :」行的 /search/class/ 链接，跳过档期(YYYY年M月)和"TV"取第一个真实题材
    var cls = parseJson(matchAll(html, '/search/class/[^"]*"[^>]*>([^<]+)</a>')) || [];
    for (var c = 0; c < cls.length; c++) {
        var cn = clean(cls[c][1]);
        if (!cn || /^(?:19|20)\d{2}年\d{1,2}月$/.test(cn) || cn === 'TV') continue;
        out.type = cn;
        break;
    }

    var tabs = parseJson(matchAll(html, 'swiper-slide"[^>]*>(?:<i[^>]*></i>)?&nbsp;([^<]+)<span class="badge"')) || [];
    var lineOrder = [];
    for (var t = 0; t < tabs.length; t++) lineOrder.push(clean(tabs[t][1]));

    var eps = parseJson(matchAll(html,
        'this-link"\\s+href="(/watch/' + id + '/(\\d+)/(\\d+)\\.html)"[^>]*>([^<]+)')) || [];
    var sidSeen = {}, sidIdx = 0;
    for (var i = 0; i < eps.length; i++) {
        var href = eps[i][1], sid = eps[i][2], epName = clean(eps[i][4]);
        if (!sidSeen[sid]) {
            sidSeen[sid] = lineOrder[sidIdx] || ('线路' + sid);
            sidIdx++;
        }
        out.episodes.push({ name: epName, url: href, route: sidSeen[sid] });
    }
    return JSON.stringify(out);
}

// 首页分区：用站点首页自带的「热乎の新番 / 刚上架の旧番」两个板块（一次请求按标题切块解析）。
// 声明 homeSections 后 App 首页走分区渲染 —— 顺带去掉了兜底布局里"热门更新→更多→最近更新"
// 那个死链接（本源没有"最近更新"分类，点进去永远是空的）；这里的"更多"都指向真实存在的 tab。
function homeSections() {
    var html = request(SITE + '/', REQ_OPT) || '';
    if (!html || isBlocked(html)) return '[]';
    var iHot = html.indexOf('热乎の新番');
    var iOld = html.indexOf('刚上架の旧番');
    var out = [];
    if (iHot >= 0) {
        var hotHtml = (iOld > iHot) ? html.substring(iHot, iOld) : html.substring(iHot);
        var hot = parseList(hotHtml, '日漫');
        if (hot.length) out.push({ title: '热乎の新番', key: '日漫', items: hot });
    }
    if (iOld >= 0) {
        var old = parseList(html.substring(iOld), '日漫');
        if (old.length) out.push({ title: '刚上架の旧番', key: '完结', items: old });
    }
    // 两块都没解析到 → 返回空数组，App 自动回退扁平片库
    return JSON.stringify(out);
}

// 播放页「相关推荐」：详情页自带"相关作品"区（dsn2 卡片），parseList 直接能解析；排除自身
function related(id) {
    var html = request(SITE + '/bangumi/' + id + '.html', REQ_OPT) || '';
    var list = parseList(html, '');
    var out = [];
    for (var i = 0; i < list.length; i++) if (list[i].id !== String(id)) out.push(list[i]);
    return JSON.stringify(out);
}

function play(flag) {
    // startSec: 本源所有视频固定 5 秒起播（App 播放层与全局"跳过片头"设置取较大者）
    var res = { url: '', type: 'auto', referer: REFERER, startSec: 5 };
    var f = String(flag || '');
    var page = /^https?:/i.test(f) ? f : SITE + (f.charAt(0) === '/' ? f : '/' + f);
    var html = request(page, REQ_OPT) || '';

    var url = '';
    var raw = match(html, 'player_aaaa\\s*=\\s*(\\{.*?\\})\\s*;?\\s*</script>', 1);
    if (raw) {
        var pj = parseJson(raw) || {};
        url = pj.url || '';
        var enc = pj.encrypt;
        if (url && (enc == 1)) url = decodeUri(url);
        else if (url && (enc == 2)) url = decodeUri(base64Decode(url));
    }
    if (!url) url = match(html, '"url"\\s*:\\s*"([^"]+\\.(?:m3u8|mp4|flv|mkv)[^"]*)"', 1) || '';
    if (url) url = url.replace(/\\\//g, '/');

    if (url && /\.(m3u8|mp4|flv|mkv)/i.test(url)) {
        // 三条线路的媒体主机都不需要 Referer；主线-1（apn.moedot.net → 302 联通沃盘签名直链）
        // 带 Referer 反而直接 400（2026-06-11 实测）→ referer:'never' 明确禁发。
        // 备用线 m3u8 路径含未编码中文（/新番/…），部分播放内核解析不了 → 转义非 ASCII 字符。
        res.url = url.replace(/[^\x00-\x7F]/g, function (c) { return encodeURIComponent(c); });
        res.type = guessType(url);
        res.referer = 'never';
        return JSON.stringify(res);
    }

    var hit = sniffMedia(page, { patterns: ['\\.m3u8(\\?|$)', '\\.mp4(\\?|$)'], userAgent: SNIFF_UA, referer: REFERER, timeout: 15000 });
    if (hit && hit.ok) { res.url = hit.url; res.type = guessType(hit.url); res.referer = hit.referer || REFERER; }
    else if (url) { res.url = url; res.type = guessType(url); }
    return JSON.stringify(res);
}