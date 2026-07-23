/*
 * 双星 APP（双子星动漫）搜索源
 * 协议还原自 XS/spider.jar: com.github.catvod.spider.App99。
 * 实现 config()/categories()/search()/searchFiltered()/detail()/play()。
 * 全站即动漫：categories() 暴露站点全部分类，type 统一『动漫』。
 *
 * 加解密/签名 helper（encryptBody/decryptBody/requestHeaders/apiPost）为上次已离线自测通过的
 * 版本，本次做外科式增强、未改动：AES-256-CBC 随机 IV + zlib(inflate) + sha256 签名。
 */

var EXT = (typeof ext !== 'undefined' && ext) ? ext : {};
var HOST = (EXT.host || 'http://175.178.65.250:19987/app/bn').replace(/\/+$/, '');
var APPKEY = EXT.appkey || 'f66f65db127e48449f073c2c6eb0f993';
var VERSION_NAME = EXT.versionName || '6.4.5';
var APP_NAME = EXT.name || '双子星动漫';
var BUILD_SIGNATURE = EXT.buildSignature || '054FA8DDA4319C6B6A9B954CA5777541C993F00B1B0BD4394F7EDE48184C4594';
var BUILD_NUMBER = EXT.buildNumber || '2003';
var PACKAGE_NAME = EXT['package'] || 'com.yingfu.mobile.android.pgsp';
var LOGIN_PATH = EXT.LoginPath || '/app/log';
var HEADER_VERSION = EXT.version || VERSION_NAME;
var UA = EXT.ua || 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6299.95 Safari/537.36';
var TIMEOUT = 25000;

var RANDOM_COUNTER = 0;
var UUID = makeUuid();
var AES_KEY = UUID.replace(/-/g, '');
var TOKEN = '';
var SESSION_READY = false;

// systemInit 下发并缓存（对齐 App99.init 的 g["player"]/g["parses"]/g["categories"]）：
//  PLAYER     线路配置对象，按线路 code 为键，值含 {code,name,type,parseUrl}
//  PARSES     解析器数组（parser_api），每项 {id,...}，type!=0 线路走 /app/vodParser 时用
//  CATEGORIES 站点分类数组（categorys.data），每项 {id,name,type_extend:{class,areas,lang,years}}
var PLAYER = null;
var PARSES = null;
var CATEGORIES = null;

// 分类 key 前缀：categories() 下发的分类 key 形如 '@'+分类id，search() 见此前缀走分类浏览
// （对齐 xifan.js 的 '@' 约定，避免与真实搜索关键词混淆）。
var CAT_PREFIX = '@';

// 分类名黑名单：systemInit categorys.data 里混有「公告」「动漫资讯」等非影片栏目，
// categories() 里按名称包含匹配剔除（只留可浏览的动漫分类，用户要求）。
var CATEGORY_NAME_BLOCKLIST = ['\u516C\u544A', '\u8D44\u8BAF'];

function trim(s) {
    return s == null ? '' : String(s).replace(/^\s+|\s+$/g, '');
}

function config() {
    return JSON.stringify({ browseOnly: false });
}

function randomHex(bytes) {
    var out = '';
    while (out.length < bytes * 2) {
        out += md5(String(timestamp()) + ':' + Math.random() + ':' + (RANDOM_COUNTER++));
    }
    return out.substring(0, bytes * 2);
}

function makeUuid() {
    var h = randomHex(16);
    return h.substring(0, 8) + '-' + h.substring(8, 12) + '-' + h.substring(12, 16) + '-' + h.substring(16, 20) + '-' + h.substring(20, 32);
}

function nonce() {
    return crypto.base64.encode(randomHex(16), { input: 'hex' });
}

function encryptBody(plain) {
    var ivHex = randomHex(16);
    var cipherHex = crypto.aes.encrypt(plain, AES_KEY, {
        mode: 'CBC',
        padding: 'PKCS5',
        keyFormat: 'utf8',
        iv: ivHex,
        ivFormat: 'hex',
        input: 'utf8',
        output: 'hex'
    });
    return crypto.base64.encode(ivHex + cipherHex, { input: 'hex' });
}

function decryptBody(encoded) {
    if (!encoded) return '';
    try {
        var rawHex = crypto.hex.encode(encoded, { input: 'base64' });
        if (!rawHex || rawHex.length <= 32) return '';
        var ivHex = rawHex.substring(0, 32);
        var cipherHex = rawHex.substring(32);
        var decryptedBase64 = crypto.aes.decrypt(cipherHex, AES_KEY, {
            mode: 'CBC',
            padding: 'PKCS5',
            keyFormat: 'utf8',
            iv: ivHex,
            ivFormat: 'hex',
            input: 'hex',
            output: 'base64'
        });
        try {
            var inflated = crypto.inflate(decryptedBase64, { input: 'base64', output: 'utf8' });
            if (inflated) return inflated;
        } catch (ignored) {
        }
        return crypto.aes.decrypt(cipherHex, AES_KEY, {
            mode: 'CBC',
            padding: 'PKCS5',
            keyFormat: 'utf8',
            iv: ivHex,
            ivFormat: 'hex',
            input: 'hex',
            output: 'utf8'
        });
    } catch (e) {
        log('[shuangxing99] decrypt failed: ' + e);
        return '';
    }
}

function requestHeaders(encodedBody, now, requestNonce) {
    return {
        'User-Agent': UA,
        'Accept': 'application/json',
        'Content-Type': 'application/json',
        'client_type': 'android',
        'uuid': UUID,
        'timestamp': now,
        'sign': sha256(encodedBody + ':' + now + ':' + requestNonce + ':' + TOKEN + ':' + APPKEY),
        'nonce': requestNonce,
        'appkey': APPKEY,
        'version': HEADER_VERSION,
        'api_version': 'v1'
    };
}

function apiPost(path, data) {
    try {
        var now = String(timestamp());
        var requestNonce = nonce();
        var body = data || {};
        body.timestamp = now;
        body.nonce = requestNonce;
        var encodedBody = encryptBody(JSON.stringify(body));
        var raw = post(
            HOST + path,
            encodedBody,
            JSON.stringify({ headers: requestHeaders(encodedBody, now, requestNonce), timeout: TIMEOUT })
        );
        return parseJson(decryptBody(raw));
    } catch (e) {
        log('[shuangxing99] request failed ' + path + ': ' + e);
        return null;
    }
}

function login() {
    var now = timestamp();
    var response = apiPost(LOGIN_PATH, {
        os: 'android',
        name: 'xiaomi',
        version: '15',
        sdkInt: 32,
        device: 'xiaomi',
        brand: 'xiaomi',
        manufacturer: 'xiaomi',
        product: 'b0q',
        hardware: 'xiaomi',
        isPhysicalDevice: true,
        androidId: 'V417IR',
        bootloader: 'unknown',
        display: 'V417IR release-keys',
        host: 'a11-gz01-test',
        tags: 'release-keys',
        type: 'user',
        finger: 'xiaomi/b0q/b0q:15/V619IR/613:user/release-keys',
        app: {
            version: VERSION_NAME,
            name: APP_NAME,
            'package': PACKAGE_NAME,
            buildNumber: BUILD_NUMBER,
            buildSignature: BUILD_SIGNATURE,
            install: now,
            update: now
        },
        did: makeUuid(),
        apiVersion: 'v2',
        channel: '',
        token: ''
    }) || {};
    if (response.userInfo && response.userInfo.user_token) {
        TOKEN = String(response.userInfo.user_token);
        SESSION_READY = true;
    }
}

function ensureSession() {
    if (SESSION_READY) return;
    // systemInit 用空 token 签名（对齐 App99：init 里 this.b 尚为空），
    // 顺带把 player / parser_api / categorys.data 缓存下来给 categories()/detail()/play() 用。
    var sys = apiPost('/app/systemInit', {
        v: VERSION_NAME,
        n: APP_NAME,
        s: BUILD_SIGNATURE,
        pl: '1',
        apiVersion: 'v2',
        token: ''
    });
    if (sys) {
        if (sys.player) PLAYER = sys.player;
        if (sys.parser_api) PARSES = sys.parser_api;
        if (sys.categorys && sys.categorys.data) CATEGORIES = sys.categorys.data;
    }
    if (!TOKEN) login();
}

function guessType(u) {
    var l = (u || '').toLowerCase();
    if (l.indexOf('.m3u8') >= 0) return 'm3u8';
    if (l.indexOf('.mp4') >= 0) return 'mp4';
    return 'auto';
}

function mapItems(list) {
    var out = [];
    if (!list || !list.length) return out;
    for (var i = 0; i < list.length; i++) {
        var item = list[i] || {};
        if (item.id == null || !item.name) continue;
        out.push({
            id: String(item.id),
            name: String(item.name),
            pic: item.pic || '',
            remarks: item.remarks || '',
            year: item.year || '',
            // 全站即动漫（铁律：只做动漫）——type 统一『动漫』，不再用 class 当类型标签
            type: '\u52A8\u6F2B',
            desc: item.blurb || ''
        });
    }
    return out;
}

// ───────────────────────── 分类 / 筛选 ─────────────────────────

// 往 groups 里追加一个筛选维度（写法 A）：values 为 systemInit type_extend 里的字符串数组，
// 首项补「全部」(v:'')。空数组不生成维度（避免露出空筛选行）。
function addFilterGroup(groups, key, name, values) {
    if (!values || !values.length) return;
    var opts = [{ n: '\u5168\u90E8', v: '' }];
    for (var i = 0; i < values.length; i++) {
        var s = values[i];
        if (s == null || s === '') continue;
        opts.push({ n: String(s), v: String(s) });
    }
    if (opts.length > 1) groups.push({ key: key, name: name, value: opts });
}

// 分类名是否命中黑名单（含即剔除）：公告 / 动漫资讯 等非影片栏目不进分类 tab。
function isBlockedCategory(name) {
    for (var i = 0; i < CATEGORY_NAME_BLOCKLIST.length; i++) {
        if (name.indexOf(CATEGORY_NAME_BLOCKLIST[i]) >= 0) return true;
    }
    return false;
}

// categories()：暴露双子星站点全部分类（来自 systemInit categorys.data），每类带
// 类型/地区/语言/年份筛选（写法 A）。首项「推荐」= 空 key，走 search('') 拉推荐列表。
// 「公告」「动漫资讯」等非影片栏目按名称剔除（见 CATEGORY_NAME_BLOCKLIST）。
function categories() {
    ensureSession();
    var arr = [{ key: '', title: '\u63A8\u8350' }];
    var cats = CATEGORIES || [];
    for (var i = 0; i < cats.length; i++) {
        var c = cats[i] || {};
        if (c.id == null || !c.name) continue;
        if (isBlockedCategory(String(c.name))) continue;   // 剔除 公告 / 动漫资讯 等非影片栏目
        var entry = { key: CAT_PREFIX + String(c.id), title: String(c.name) };
        var te = c.type_extend || {};
        var groups = [];
        addFilterGroup(groups, 'class', '\u7C7B\u578B', te['class']);
        addFilterGroup(groups, 'area', '\u5730\u533A', te.areas);
        addFilterGroup(groups, 'lang', '\u8BED\u8A00', te.lang);
        addFilterGroup(groups, 'year', '\u5E74\u4EFD', te.years);
        if (groups.length) entry.filters = groups;
        arr.push(entry);
    }
    return JSON.stringify(arr);
}

// 分类浏览：POST /vod/search + pid + isCategory（对齐 App99 categoryContent/homeContent）。
// filters 为已选筛选 {class,area,lang,year}；App99 原生只按 pid 取分类、并不下发这些维度，
// 这里在用户选了具体值时附带发送——服务端支持即生效，不支持则被忽略退化为按分类浏览
// （与原 App 行为一致）。⚠ 需真机联网复核筛选维度是否真被服务端消费。
function categoryBrowse(pid, page, filters) {
    ensureSession();
    var body = {
        kw: '',
        page: page || 1,
        limit: 21,
        pid: String(pid),
        orderBy: 'time',
        isCategory: 1,
        token: TOKEN
    };
    if (filters) {
        if (filters['class']) body['class'] = filters['class'];
        if (filters.area) body.area = filters.area;
        if (filters.lang) body.lang = filters.lang;
        if (filters.year) body.year = filters.year;
    }
    var result = apiPost('/vod/search', body) || {};
    return mapItems(result.data);
}

function search(keyword, page) {
    var key = trim(keyword);
    // 分类浏览：'@'+分类id（categories() 下发的 key）→ /vod/search + pid
    if (key.charAt(0) === CAT_PREFIX) {
        return JSON.stringify(categoryBrowse(key.substring(1), page, null));
    }
    // 推荐/精选（空关键词）：对齐 App99 homeContent 用 pid='1' 拉推荐列表，
    // 避免首页判空触发「坏源自动跳源」。
    if (!key) {
        return JSON.stringify(categoryBrowse('1', page, null));
    }
    ensureSession();
    var result = apiPost('/vod/search', {
        kw: key,
        page: page || 1,
        limit: 21,
        orderBy: 'vod_hits_month',
        sort: 'desc',
        token: TOKEN
    }) || {};
    return JSON.stringify(mapItems(result.data));
}

// searchFiltered：分类 tab 里选了筛选后调用。category = categories() 下发的 key（'@'+pid）。
function searchFiltered(category, filtersJson, page) {
    var cat = trim(category);
    var pid = cat.charAt(0) === CAT_PREFIX ? cat.substring(1) : (cat || '1');
    var f = parseJson(filtersJson) || {};
    return JSON.stringify(categoryBrowse(pid, page, f));
}

// ───────────────────────── 详情 / 播放 ─────────────────────────

// detail：POST /vod/detail → data。play_from（$$$ 分线路 code）+ play_url（$$$ 分线路、
// # 分集、$ 分「集名$地址」）。
//
// ⚠ flag 构造与 App99 的差异（关键）：App99 跑在 CatVod/TVBox 框架内，vod_play_url 的
// 每集是「显示名$播放flag」，框架点集时会**按首个 $ 切掉显示名**，只把后半段交给
// playerContent——所以 Java 里 split("@")[0] 拿到的其实是纯地址。而 Lanerc 是把
// episode.url **原样**传给 play(flag)（无框架级切名），故这里 flag 里**不含**「集名$」
// 前缀，直接拼「{地址/ID}@{线路code}@{片名}@{集号}」；集名放到 episode.name。
// 若照抄 Java 的「名$址@…」，play 会把「名$址」整段当地址/丢给解析器 → 播放失败。
// route = 线路显示名。
function detail(id) {
    ensureSession();
    var out = { id: String(id), name: '', pic: '', desc: '', type: '\u52A8\u6F2B', year: '', remarks: '', episodes: [] };
    var resp = apiPost('/vod/detail', {
        id: String(id),
        eps: '1',
        v: '2.0.0',
        pl: 1,
        token: TOKEN
    }) || {};
    var d = resp.data;
    if (!d) return JSON.stringify(out);
    out.name = d.name != null ? String(d.name) : '';
    out.pic = d.pic || '';
    out.year = d.year != null ? String(d.year) : '';
    out.remarks = d.remarks || '';
    out.desc = d.content || d.blurb || '';
    var vodName = out.name;
    // 线路 code → 显示名：systemInit player[*].{code,name}（对齐 App99 detailContent）
    var codeToName = {};
    if (PLAYER) {
        for (var pk in PLAYER) {
            var pv = PLAYER[pk] || {};
            var pc = trim(pv.code);
            if (pc) codeToName[pc] = trim(pv.name) || pc;
        }
    }
    var fromArr = String(d.play_from || '').split('$$$');
    var urlArr = String(d.play_url || '').split('$$$');
    for (var i = 0; i < urlArr.length; i++) {
        var code = i < fromArr.length ? trim(fromArr[i]) : '';
        var lineName = codeToName[code] || code || ('\u7EBF\u8DEF' + (i + 1));
        var epStrs = urlArr[i].split('#');
        for (var j = 0; j < epStrs.length; j++) {
            var seg = epStrs[j];
            if (!seg) continue;
            var dollar = seg.indexOf('$');
            var epName = dollar >= 0 ? seg.substring(0, dollar) : ('\u7B2C' + (j + 1) + '\u96C6');
            var epBody = dollar >= 0 ? seg.substring(dollar + 1) : seg;
            if (!epBody) continue;
            // 集号：从集名抽数字，无数字用 '1'（对齐 App99 的 \D+ 剔除逻辑，用于弹幕定位）
            var idx = epName.replace(/\D+/g, '');
            if (!idx) idx = '1';
            // flag = {地址/ID}@{线路code}@{片名}@{集号}（不含集名前缀，见上方 ⚠ 说明）
            var flag = epBody + '@' + code + '@' + vodName + '@' + idx;
            out.episodes.push({ name: epName, url: flag, route: lineName });
        }
    }
    return JSON.stringify(out);
}

// play：按 @ 拆 flag → [urlId, 线路code, 片名, 集号]。查 systemInit player[线路code]：
// type==0 直连（urlId 即地址）；type!=0 遍历 parser_api（受 player.parseUrl 白名单约束）
// POST /app/vodParser 取 data(http 直链)。type 判断用 Number(type||0)===0（对齐 Java optInt）。
function play(flag) {
    ensureSession();
    var f = String(flag || '');
    var at = f.split('@');
    var urlId = at[0] || '';
    var code = at.length > 1 ? at[1] : '';
    var res = { url: '', type: 'auto' };

    // 线路配置：优先按 code 直接取键；取不到再按 value.code 扫描兜底
    var pobj = null;
    if (PLAYER) {
        pobj = PLAYER[code];
        if (!pobj) {
            for (var pk in PLAYER) {
                var pv = PLAYER[pk] || {};
                if (trim(pv.code) === code) { pobj = pv; break; }
            }
        }
    }
    var type = pobj ? Number(pobj.type || 0) : 0;
    if (type === 0) {
        res.url = urlId;
        res.type = guessType(urlId);
        return JSON.stringify(res);
    }

    // parseUrl 白名单（逗号分隔的解析器 id）；空 = 允许全部解析器
    var allow = (pobj && pobj.parseUrl) ? String(pobj.parseUrl).split(',') : [];
    if (PARSES && PARSES.length) {
        for (var i = 0; i < PARSES.length; i++) {
            var parser = PARSES[i] || {};
            var pid = String(parser.id);
            if (allow.length && allow.indexOf(pid) < 0) continue;
            var r = apiPost('/app/vodParser', {
                id: Number(parser.id),
                url: urlId,
                token: TOKEN
            }) || {};
            var data = r.data;
            if (data && String(data).indexOf('http') === 0) {
                res.url = String(data);
                res.type = guessType(res.url);
                return JSON.stringify(res);
            }
        }
    }
    return JSON.stringify(res);
}