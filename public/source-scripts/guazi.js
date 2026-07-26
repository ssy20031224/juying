/*
 * 瓜子影视（“瓜子｜影视”）JS 源
 * 原型：TVBox spider  csp_Gz360，2026-07-17 按 ls125781003/tbapi1 瓜子影视.py 对齐当前线上协议
 * version: 2.0.0
 *   2.0.0：站点/App 大版本升级，旧协议整体失效，按 py 参考重写鉴权与握手——
 *     · 头部升到新 App 版本（Version=2604028 / Ver=3.0.3.2 / 新包名 + code/deviceId/lang/api-ver）；
 *     · token 不再写死：首次用随机设备 signUp + refresh 动态领取，进程内缓存（切源/重启自然重领）；
 *     · keys 不再写死：每次请求用站点 RSA 公钥现加密 {iv,key}（PKCS1）得到，签名随之同步；
 *     · 多域名容灾：主 apinew.uozvr.com + 4 个备用，失败自动轮换；
 *     · request_key 改大写 hex（对齐 py，签名/请求体同源一致）；
 *     · 播放头补 Referer（CDN 防盗链）。
 *   1.1.1：detail 结果进程内缓存，避免简介回填/播放/重进对同一剧反复重拉选集（保留）。
 *
 * ⚠️ 站点是综合影视站（电影/国产剧/动漫/综艺/短剧），本源【只出动漫】：
 *   - 首页/分类：固定 tid=3，sub 走动漫二级类（30中国动漫 / 31日本动漫 / 33欧美动漫）；
 *     ⚠️ sub 不能传 0（实测 sub=0 会返回综艺等杂项），所以三个 tab 各自钉死 sub。
 *   - 搜索：findMoreVod 是全站搜索，结果按 t_id===4（动漫）二次过滤，
 *           电影(t_id=1)/电视剧(2)/综艺(3)/短剧(64)一律丢弃 —— 跨源搜索、自动换源也只命中动漫。
 *
 * 协议（加密 POST，全程 form-urlencoded）：
 *   - 请求体每个接口是一段明文 JSON，先 AES-128-CBC/PKCS5（key=ENC_KEY、iv=ENC_IV，客户端自选）加密、
 *     输出【大写 hex】作为表单字段 request_key。
 *   - keys = 站点 RSA 公钥(RSA/ECB/PKCS1) 加密 {"iv":ENC_IV,"key":ENC_KEY} 的 base64（服务端私钥解出后
 *     即知客户端用的 key/iv，再解 request_key）；PKCS1 每次密文不同，故每请求现算、签名随之取同一份。
 *   - 签名 signature = MD5("token_id=,token=<token>,phone_type=1,request_key=<hex>,app_id=1,"
 *     + "time=<秒>,keys=<keys>" + salt).toUpperCase()。
 *   - 表单字段：token / token_id(空) / phone_type=1 / time(秒) / phone_model / keys / request_key
 *     / signature / app_id=1 / ad_version=1；请求头带 code/deviceId/lang/Version/PackageName/Ver/api-ver/Referer/UA。
 *   - 响应体 { code, msg, data:{ keys, response_key } }：
 *       ① keys 用内置【客户端】RSA 私钥（RSA/ECB/PKCS1）解密 → {key, iv}（各 16 字节 ASCII）；
 *       ② response_key 是 hex 密文，AES-128-CBC/PKCS5（上一步 key/iv）解出明文业务 JSON。
 *   - 鉴权：先 /App/Authentication/Device/signUp（new_key=随机设备,old_key 固定）领 token+app_user_id，
 *     再 /App/Authentication/Authenticator/refresh 刷新拿最终 token；token 失效（业务解密空）时清空重领一次。
 *   - 列表 /App/IndexList/indexList → {list:[{vod_id,vod_name,vod_pic,vod_year,vod_area,d_type,new_continue,t_id}]}
 *     详情 /App/IndexPlay/playInfo → {vodInfo:{...}}；选集 /App/Resource/Vurl/show → {list:[{title,play:{<分辨率>:{param,show_type}}}]}
 *     （show_type==2 不可用要跳过，实测多为 1080 直链）；播放 /App/Resource/VurlDetail/showOne → {url} 直链 m3u8。
 */

// ── 配置（默认值取自 py 参考，ext 注入时覆盖）──
function cfg(k, d) {
    try { if (typeof ext !== 'undefined' && ext && ext[k] != null && String(ext[k]) !== '') return String(ext[k]); } catch (e) {}
    return d;
}

// 多域名：ext 可用 'hosts'（逗号分隔整表覆盖）或 'host'（单域名置顶）覆盖，否则用 py 参考主备表。
var HOSTS = (function () {
    function norm(h) { return trim(h).replace(/\/+$/, ''); }
    var def = [
        'https://apinew.uozvr.com',
        'https://api.w32z7vtd.com',
        'https://api.6a7nnf7.com',
        'https://api.umygrx3.com',
        'https://api.rmedphk.com'
    ];
    var raw = cfg('hosts', '');
    if (raw) {
        var arr = raw.split(',').map(norm).filter(function (x) { return !!x; });
        if (arr.length) return arr;
    }
    var single = cfg('host', '');
    if (single) {
        var first = norm(single), out = [first];
        for (var i = 0; i < def.length; i++) if (def[i] !== first) out.push(def[i]);
        return out;
    }
    return def;
})();

var ENC_KEY  = cfg('encKey', 'OITxa5OqAYjhswxx');
var ENC_IV   = cfg('encIv', 'rCMNwZASNBKZ8mXV');
var SALT     = cfg('salt', '*&zvdvdvddbfikkkumtmdwqppp?|4Y!s!2br');
// 站点 RSA 公钥（X.509 SPKI，base64）：加密外发 keys 用。
var RSA_PUB  = cfg('rsaPub', 'MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDUM5+/y8sPsWkd1/RQS64X259EUwxFXFE5HlA65MqrxnPs0JqoSRojSDy5QhwvROlaD6TwRQHKMY2OAZ6SnQeUJsChTEFIR9qUkwrs3/MVUMxjsv6JS6Oe/juclyJGTgVmDhB55EafXsD0SQYVj/QXXsxR6ewR5E2kL52yAAD4yQIDAQAB');
// 客户端 RSA 私钥（PKCS#8，base64）：解密响应 data.keys 用。
var RSA_PRIV = cfg('rsaPriv', 'MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGAe6hKrWLi1zQmjTT1ozbE4QdFeJGNxubxld6GrFGximxfMsMB6BpJhpcTouAqywAFppiKetUBBbXwYsYU1wNr648XVmPmCMCy4rY8vdliFnbMUj086DU6Z+/oXBdWU3/b1G0DN3E9wULRSwcKZT3wj/cCI1vsCm3gj2R5SqkA9Y0CAwEAAQKBgAJH+4CxV0/zBVcLiBCHvSANm0l7HetybTh/j2p0Y1sTXro4ALwAaCTUeqdBjWiLSo9lNwDHFyq8zX90+gNxa7c5EqcWV9FmlVXr8VhfBzcZo1nXeNdXFT7tQ2yah/odtdcx+vRMSGJd1t/5k5bDd9wAvYdIDblMAg+wiKKZ5KcdAkEA1cCakEN4NexkF5tHPRrR6XOY/XHfkqXxEhMqmNbB9U34saTJnLWIHC8IXys6Qmzz30TtzCjuOqKRRy+FMM4TdwJBAJQZFPjsGC+RqcG5UvVMiMPhnwe/bXEehShK86yJK/g/UiKrO87h3aEu5gcJqBygTq3BBBoH2md3pr/W+hUMWBsCQQChfhTIrdDinKi6lRxrdBnn0Ohjg2cwuqK5zzU9p/N+S9x7Ck8wUI53DKm8jUJE8WAG7WLj/oCOWEh+ic6NIwTdAkEAj0X8nhx6AXsgCYRql1klbqtVmL8+95KZK7PnLWG/IfjQUy3pPGoSaZ7fdquG8bq8oyf5+dzjE/oTXcByS+6XRQJAP/5ciy1bL3NhUhsaOVy55MHXnPjdcTX0FaLi+ybXZIfIQ2P4rb19mVq1feMbCXhz+L1rG8oat5lYKfpe8k83ZA==');
// 设备注册用固定 old_key（py 参考 DEVICE_OLD_KEY）。
var DEVICE_OLD_KEY = cfg('deviceOldKey', 'aLFBMWpxBrIDAD1Si/KVvm41');

var UA       = cfg('ua', 'Lavf/57.83.100');           // API 请求头 UA（新版 App 用 Lavf）
// 播放器拉流 UA：必须用原 App 的 Lavf（CDN 防盗链只放行它）；默认浏览器 UA 会被 302/限速 → 拉流失败。
var PLAY_UA  = cfg('playUa', 'Lavf/57.83.100');
// 播放器拉流 Referer：CDN 防盗链校验（py 参考 header 固定此值）。
var PLAY_REF = cfg('playReferer', 'http://WJiZxLXA2.com/');
var CODE     = cfg('code', 'GZ0369');
var PKG      = cfg('package', 'com.ae06aebdbb.y286327f5a.ofe849883320260517');
var VERSION  = cfg('version', '2604028');
var VER      = cfg('ver', '3.0.3.2');
var PHONE_MODEL = cfg('phoneModel', 'xiaomi-25031');
// 可选：ext 直接注入 token / token_id 时跳过自动注册（应急用）。
var TOKEN_OVERRIDE    = cfg('token', '');
var TOKEN_ID_OVERRIDE = cfg('tokenId', '');

// ───────────────────────── 设备身份 / token（进程内缓存）─────────────────────────
// 说明：同一源在其激活生命周期内 context 复用，这些模块级变量跨 categories/search/detail/play 保留；
// 切源 / App 重启会重建 context → 重新随机设备并 signUp（与 py 参考「每进程一设备」等效）。
var _hostIdx   = 0;
var _deviceId  = '';
var _deviceKey = '';
var _token     = '';
var _tokenId   = '';
// 注册失败冷却：上次 signUp+refresh 全域名皆空的时刻(ms)。站点整体不可用时若不记失败态，
// 每次 api() 都会重跑 signUp+refresh×全部域名再加业务×全部域名（单次 detail 放大 ~30 个请求）。
// 冷却期内 ensureToken 直接快速失败不重试注册，到点自动恢复。
var _lastRegFailAt = 0;
var REG_FAIL_COOLDOWN_MS = 60000;

function randHex(n) {
    var s = '', c = '0123456789ABCDEF';
    for (var i = 0; i < n; i++) s += c.charAt(Math.floor(Math.random() * 16));
    return s;
}
// 生成一台全新设备（deviceId 走 header，new_key 走 signUp 设备身份）
function regenDevice() {
    _deviceId  = String(864150060000000 + Math.floor(Math.random() * 10000));
    _deviceKey = randHex(40);
}
// 保证 header 至少有一台设备（含 ext 注入 token、跳过注册的路径）
function ensureDevice() { if (!_deviceKey) regenDevice(); }

// ───────────────────────── 工具 ─────────────────────────
function nowSec() { return String(Math.floor(timestamp() / 1000)); }
function trim(s) { return s == null ? '' : String(s).replace(/^\s+|\s+$/g, ''); }
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
    if (/日本|日韩|韩/.test(area)) return '日漫';
    if (/欧美|美国|英|法|德|加拿大|俄/.test(area)) return '欧美';
    return '国漫';
}
// d_type 二级分类优先（30国漫/31日漫/33欧美），无则按地区猜
function subType(dType, area) {
    var t = String(dType || '');
    if (t === '31') return '日漫';
    if (t === '33') return '欧美';
    if (t === '30') return '国漫';
    return typeOf(area);
}
function guessType(u) {
    u = (u || '').toLowerCase();
    if (u.indexOf('.m3u8') >= 0) return 'm3u8';
    if (u.indexOf('.mp4') >= 0) return 'mp4';
    if (u.indexOf('.flv') >= 0) return 'flv';
    return 'auto';
}

// ───────────────────────── 加解密 ─────────────────────────
// 明文 JSON → AES-128-CBC/PKCS5 加密 → 大写 hex（request_key，对齐 py .hex().upper()）
function encReq(plain) {
    var hex = crypto.aes.encrypt(plain, ENC_KEY, {
        mode: 'CBC', padding: 'PKCS5',
        keyFormat: 'utf8', ivFormat: 'utf8', iv: ENC_IV,
        input: 'utf8', output: 'hex'
    }) || '';
    return hex.toUpperCase();
}

// keys：站点 RSA 公钥 PKCS1 加密 {"iv":..,"key":..} → base64（每次现算，PKCS1 密文随机）
function buildKeys() {
    return crypto.rsa.encrypt(JSON.stringify({ iv: ENC_IV, key: ENC_KEY }), RSA_PUB, {
        padding: 'PKCS1', input: 'utf8', output: 'base64'
    }) || '';
}

// 响应解密：data.keys 用客户端 RSA 私钥解出 {key,iv}，再 AES 解 data.response_key（hex）
function decResp(resp) {
    var j = parseJson(resp);
    if (!j || !j.data) return '';
    var data = j.data;
    if (!data.keys || !data.response_key) return '';
    var kj = crypto.rsa.decrypt(data.keys, RSA_PRIV, { padding: 'PKCS1', input: 'base64', output: 'utf8' });
    var ko = parseJson(kj) || {};
    if (!ko.key || !ko.iv) return '';
    return crypto.aes.decrypt(data.response_key, ko.key, {
        mode: 'CBC', padding: 'PKCS5',
        keyFormat: 'utf8', ivFormat: 'utf8', iv: ko.iv,
        input: 'hex', output: 'utf8'
    }) || '';
}

// ───────────────────────── 传输 / 鉴权 ─────────────────────────
function curHost() { return HOSTS[_hostIdx % HOSTS.length]; }
function rotateHost() { _hostIdx = (_hostIdx + 1) % HOSTS.length; }

// 单次加密 POST 到当前域名，返回解密后的明文业务 JSON 字符串（失败返回 ''）。不做 token 保障，避免递归。
function rawApi(path, obj) {
    var host = curHost();
    var time = nowSec();
    var rk = encReq(JSON.stringify(obj));
    var keys = buildKeys();
    var sign = md5('token_id=,token=' + _token + ',phone_type=1,request_key=' + rk +
        ',app_id=1,time=' + time + ',keys=' + keys + SALT).toUpperCase();
    var form = 'token=' + encodeUri(_token) +
        '&token_id=&phone_type=1&time=' + time +
        '&phone_model=' + encodeUri(PHONE_MODEL) +
        '&keys=' + encodeUri(keys) +
        '&request_key=' + encodeUri(rk) +
        '&signature=' + sign +
        '&app_id=1&ad_version=1';
    var headers = {
        'User-Agent': UA,
        'code': CODE,
        'deviceId': _deviceId,
        'lang': 'zh_cn',
        'Cache-Control': 'no-cache',
        'Content-Type': 'application/x-www-form-urlencoded',
        'Version': VERSION,
        'PackageName': PKG,
        'Ver': VER,
        'api-ver': VER,
        'Referer': host
    };
    var resp = post(host + path, form, JSON.stringify({ headers: headers, timeout: 20000 }));
    return decResp(resp);
}

// 带域名轮换：当前域名失败则换下一个，全表试一遍仍空才返回 ''。
function rawApiAll(path, obj) {
    for (var i = 0; i < HOSTS.length; i++) {
        var s = rawApi(path, obj);
        if (s) return s;
        rotateHost();
    }
    return '';
}

// 领取 / 刷新 token：每次都用【全新设备】signUp 再 refresh。
// 实测：signUp 对全新随机设备稳定成功（返回 token + app_user_id）；同一设备重注册会「用户已存在」、
// 本站 signIn 亦不可靠，故重领时换新设备 signUp 最稳（与 py「每进程一新设备」同理，去掉 signIn 分支）。
function doRegister() {
    regenDevice();
    var r = parseJson(rawApiAll('/App/Authentication/Device/signUp',
        { new_key: _deviceKey, old_key: DEVICE_OLD_KEY, phone_type: 1, code: '' })) || {};
    if (r.token) {
        _token = String(r.token);
        if (r.app_user_id != null && String(r.app_user_id) !== '') _tokenId = String(r.app_user_id);
    }
    var r2 = parseJson(rawApiAll('/App/Authentication/Authenticator/refresh', {})) || {};
    if (r2.token) {
        _token = String(r2.token);
        if (r2.app_user_id != null && String(r2.app_user_id) !== '') _tokenId = String(r2.app_user_id);
    }
    // 记录注册结果：拿到 token 清失败态；没拿到记失败时刻，进入冷却（见 _lastRegFailAt 注释）。
    _lastRegFailAt = _token ? 0 : timestamp();
}

// 确保有可用 token（ext 注入 token 时直接采用）。以 _token 存在为闸，避免注册死循环。
function ensureToken() {
    if (TOKEN_OVERRIDE) {
        _token = TOKEN_OVERRIDE;
        if (TOKEN_ID_OVERRIDE) _tokenId = TOKEN_ID_OVERRIDE;
        return;
    }
    if (_token) return;
    // 注册失败冷却期内不再重试注册：保持空 token 快速失败（api() 会就地返回 ''），
    // 避免站点不可用时每次调用都重跑 signUp+refresh×全部域名。
    if (_lastRegFailAt && timestamp() - _lastRegFailAt < REG_FAIL_COOLDOWN_MS) return;
    doRegister();
}

// 业务加密 POST：保障 token → 轮换域名请求；若拿到 token 却仍解密空（多半 token 失效），清空重领一次再试。
function api(path, obj) {
    ensureDevice();
    ensureToken();
    // 注册失败（含冷却期内快速失败）没拿到 token：业务请求必被拒，直接返回空，别再×域名空转。
    if (!_token) return '';
    var s = rawApiAll(path, obj);
    if (!s && _token && !TOKEN_OVERRIDE) {
        _token = ''; _tokenId = '';
        doRegister();
        // 重领失败（已进入冷却）就不再空转一轮域名了
        s = _token ? rawApiAll(path, obj) : '';
    }
    return s;
}
// 同 api()，但直接返回解析好的对象（失败返回 {}）
function apiJson(path, obj) { return parseJson(api(path, obj)) || {}; }

// ───────────────────────── 列表 / 搜索 ─────────────────────────
function mapItem(it) {
    return {
        id:      String(it.vod_id),
        name:    clean(it.vod_name),
        pic:     trim(it.vod_pic),
        type:    subType(it.d_type, it.vod_area),
        year:    yearStr(it.vod_year),
        remarks: clean(it.new_continue || it.vod_continu),
        desc:    ''
    };
}

// 动漫片库（tid 恒为 3，sub 必须是 30/31/33），可带 area/year/sort 过滤
function listVod(sub, f, page) {
    f = f || {};
    var j = apiJson('/App/IndexList/indexList', {
        tid: '3',
        page: String(page || 1),
        sort: f.sort || 'd_id',
        area: f.area || '0',
        sub: String(sub),
        year: f.year || '0',
        pageSize: '30'
    });
    return (j.list || []).map(mapItem);
}

// 关键词搜索：全站搜索后按 t_id===4 只留动漫（findMoreVod 不分页）
function searchWord(wd) {
    var j = apiJson('/App/Index/findMoreVod', { keywords: wd, order_val: '1' });
    return (j.list || [])
        .filter(function (it) { return String(it.t_id) === '4'; })
        .map(mapItem);
}

// ───────────────────────── 分类筛选项（取自 dex homeContent 动漫 tid=3 的 filters）──
var AREA_OPTS = [
    { n: '全部', v: '0' }, { n: '大陆', v: '大陆' }, { n: '日本', v: '日本' },
    { n: '香港', v: '香港' }, { n: '台湾', v: '台湾' }, { n: '韩国', v: '韩国' },
    { n: '欧美', v: '俄罗斯,加拿大,德国,意大利,法国,欧美,美国,英国,西班牙' },
    { n: '其他', v: '其他,印度,新加坡,马来西亚' }
];
var SORT_OPTS = [{ n: '综合', v: 'd_id' }, { n: '最新', v: 'd_addtime' }, { n: '最热', v: 'd_score' }];
function yearOpts() {
    var o = [{ n: '全部', v: '0' }];
    for (var y = (new Date()).getFullYear(); y >= 2015; y--) o.push({ n: String(y), v: String(y) });
    return o;
}

// 首页 tab key 即动漫 sub：''(默认)→31 日番 / '30'→国漫 / '33'→欧美
function subOf(cat) {
    cat = trim(cat);
    if (cat === '30') return '30';
    if (cat === '33') return '33';
    return '31';
}

// ───────────────────────── 契约入口 ─────────────────────────
function categories() {
    var fil = [
        { key: 'area', name: '地区', value: AREA_OPTS },
        { key: 'year', name: '年份', value: yearOpts() },
        { key: 'sort', name: '排序', value: SORT_OPTS }
    ];
    return JSON.stringify([
        { key: '',   title: '日番', filters: fil },
        { key: '30', title: '国漫', filters: fil },
        { key: '33', title: '欧美', filters: fil }
    ]);
}

function search(keyword, page) {
    page = page || 1;
    var key = trim(keyword);
    if (!key) return JSON.stringify(listVod('31', {}, page));
    if (key === '30' || key === '31' || key === '33') return JSON.stringify(listVod(key, {}, page));
    // 关键词搜索不分页，翻页直接空
    return page > 1 ? '[]' : JSON.stringify(searchWord(key));
}

function searchFiltered(category, filtersJson, page) {
    var f = parseJson(filtersJson) || {};
    return JSON.stringify(listVod(subOf(category), f, page || 1));
}

// 详情结果进程内缓存：detail() 会被「搜索页简介回填 DescEnricher + 类型回填 TypeEnricher +
// 播放页 + 历史重进同一剧」对同一 id 反复调用，而每次都要重拉 playInfo + Vurl/show（选集），
// 既慢又费流量（本源是加密 POST + RSA 解密，比普通源贵得多）。缓存后同一剧在本源生命周期内
// 只拉一次（切源 / App 重启自然失效）。仅缓存「拿到选集」的成功结果，失败（episodes 空）不缓存，
// 以便下次重试。
var _detailCache = {};
var _detailOrder = [];
function detail(id) {
    var key = String(id);
    if (_detailCache.hasOwnProperty(key)) return _detailCache[key];
    var out = { id: key, name: '', pic: '', desc: '', type: '', remarks: '', year: '', episodes: [] };
    // playInfo 业务体要带 token/token_id，须在构造请求体【前】先领到 token（否则冷 context 首个调用是 detail 时
    // 参数先求值会捕获空串）；api() 内部虽也会 ensureToken，但那发生在请求体求值之后，故这里显式提前。
    ensureDevice();
    ensureToken();
    var d = apiJson('/App/IndexPlay/playInfo',
        { token_id: _tokenId, vod_id: String(id), mobile_time: nowSec(), token: _token }).vodInfo || {};
    out.name    = clean(d.vod_name);
    out.pic     = trim(d.vod_pic);
    out.desc    = clean(d.vod_use_content);
    out.remarks = clean(d.new_continue);
    out.year    = yearStr(d.vod_year);
    out.type    = typeOf(d.vod_area);

    // 选集：按分辨率分组成线路（一档分辨率 = 一条线路），show_type==2 不可用要跳过
    var list = apiJson('/App/Resource/Vurl/show', { vurl_cloud_id: '2', vod_d_id: String(id) }).list || [];
    var lineMap = {}, order = [];
    for (var i = 0; i < list.length; i++) {
        var ep = list[i] || {};
        var play = ep.play || {};
        var epTitle = clean(ep.title) || ('第' + (i + 1) + '集');
        for (var res in play) {
            if (!play.hasOwnProperty(res)) continue;
            var pv = play[res] || {};
            if (String(pv.show_type) === '2' || !pv.param) continue;
            if (!lineMap[res]) { lineMap[res] = []; order.push(res); }
            lineMap[res].push({ name: epTitle, url: trim(pv.param) });
        }
    }
    // 分辨率从高到低排，默认线路即最高清（对象 key 遍历是数值升序，不排会让 480P 当默认）
    order.sort(function (a, b) {
        var na = parseInt(a, 10), nb = parseInt(b, 10);
        return (isNaN(nb) ? -1 : nb) - (isNaN(na) ? -1 : na);
    });
    for (var oi = 0; oi < order.length; oi++) {
        var resKey = order[oi];
        var route = /^\d+$/.test(resKey) ? (resKey + 'P') : resKey;   // 480P / 720P / 1080P
        var eps = lineMap[resKey];
        for (var ei = 0; ei < eps.length; ei++) {
            out.episodes.push({ name: eps[ei].name, url: eps[ei].url, route: route });
        }
    }
    var json = JSON.stringify(out);
    // 拿到选集才缓存：episodes 为空多半是网络 / 风控失败，缓存会让后续永远空、连播放都进不去，故跳过。
    if (out.episodes.length > 0) {
        _detailCache[key] = json;
        _detailOrder.push(key);
        if (_detailOrder.length > 80) delete _detailCache[_detailOrder.shift()];
    }
    return json;
}

function play(flag) {
    var res = { url: '', type: 'auto' };
    var param = trim(flag);
    if (!param) return JSON.stringify(res);

    // param 形如 vod_d_id=x&vurl_id=y&domain_type=8&resolution=1080&type=play → 拆成 JSON 当请求体
    var obj = {};
    var kvs = param.split('&');
    for (var i = 0; i < kvs.length; i++) {
        var eq = kvs[i].indexOf('=');
        if (eq < 0) continue;
        obj[kvs[i].substring(0, eq)] = kvs[i].substring(eq + 1);
    }
    var url = trim(apiJson('/App/Resource/VurlDetail/showOne', obj).url);
    if (!url) return JSON.stringify(res);
    res.url = url;
    res.type = guessType(url);
    res.userAgent = PLAY_UA;   // 折叠进播放 header 的 User-Agent，过 CDN 防盗链
    res.referer = PLAY_REF;    // CDN 防盗链 Referer（py 参考固定值）
    return JSON.stringify(res);
}