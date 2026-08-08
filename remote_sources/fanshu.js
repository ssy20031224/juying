/*
 * 番薯动漫 Lanerc QuickJS 源
 * version: 1.2.2
 *
 * 协议：yoapp.php?action=...&token=...
 * - 业务请求：X-Yoapp-* 签名头，token 放在查询串中
 * - device_secret：bootstrap HMAC，token 放在 X-API-TOKEN 请求头
 * - 列表/详情/播放的 data 使用 AES-128-ECB + AES-256-CBC 双层解密
 */

var FANSHU_TOKEN = 'yoapp_a682c34e5cc0e0c38b4f749475074db7281791ad';
var FANSHU_VERSION = '1.2.2';
var FANSHU_GUARD = '79f6c737ae6db8e81bb0be941c2834bd42577a1a5c36c3dc';
var FANSHU_APP_SIGNATURE = '1F83BDCA0957AADDCD3CE53088D60FD228ADC463EBA105495E4EBAE6962B54D6';
var FANSHU_SALT = '8124fb976064d07a5c6af58c771f1c87';
var FANSHU_DEFAULT_DEVICE_ID = '5o3344d1-9975-4b9a-80c6-f7cc2af972cc';
/*
 * 2026-08-05 对照番薯 APK 运行态确认：
 * - NPM 动态域名配置当前下发 yoapp-cf.fsapi.shop / yoapp-do.fsapi.shop；
 * - bytegooty.com / fsapi.me 是旧直连域名；
 * - 直连失败后 APK 还会走 Supabase proxy，不能只保留两个旧域名。
 *
 * 顺序与 manshan.js 一致：当前主域优先，ext 只追加，旧域最后兜底。
 */
var FANSHU_PRIMARY_HOSTS = [
    'https://yoapp-cf.fsapi.shop/yoapp.php',
    'https://yoapp-do.fsapi.shop/yoapp.php',
    'https://tpewkabdkhkwdwnwcorg.supabase.co/functions/v1/proxy/yoapp-cf.fsapi.shop/yoapp.php',
    'https://tpewkabdkhkwdwnwcorg.supabase.co/functions/v1/proxy/yoapp-do.fsapi.shop/yoapp.php'
];
var FANSHU_LEGACY_HOSTS = [
    'https://yoapp.bytegooty.com/yoapp.php',
    'https://yoapp.fsapi.me/yoapp.php',
    'https://tpewkabdkhkwdwnwcorg.supabase.co/functions/v1/proxy/yoapp.bytegooty.com/yoapp.php',
    'https://tpewkabdkhkwdwnwcorg.supabase.co/functions/v1/proxy/yoapp.fsapi.me/yoapp.php'
];
var FANSHU_HTTP_TIMEOUT = 4500;
var FANSHU_DEAD_HOST_RETRY_MS = 120000;
var FANSHU_HOME_CACHE_FRESH_MS = 10 * 60 * 1000;
var FANSHU_HOME_CACHE_KEY = 'fanshu_home_sections_v2';

/* 抓包中得到的短期兜底密钥。过期后会自动通过 device_secret 刷新。 */
var FANSHU_FALLBACK_BASE_SECRET = '91ac98b2ac2d289e331c2b82e1de3ef9b51c7fe3e18d15f1e419ca315c14d373';
var FANSHU_FALLBACK_EXPIRES_AT = 1785942862;

var FANSHU_EXT = (function () {
    try {
        if (typeof ext === 'undefined' || !ext) return {};
        if (typeof ext === 'object') return ext;
        if (typeof ext === 'string') {
            var s = ext.replace(/^\s+|\s+$/g, '');
            if (s.charAt(0) === '{') {
                var parsed = (typeof parseJson === 'function') ? parseJson(s) : JSON.parse(s);
                if (parsed && typeof parsed === 'object') return parsed;
            }
            return { host: s };
        }
    } catch (e) {}
    return {};
})();

var FANSHU_DEVICE_ID = String(FANSHU_EXT.deviceId || FANSHU_EXT.device_id || FANSHU_DEFAULT_DEVICE_ID);
var FANSHU_HOSTS = null;
var FANSHU_ACTIVE_HOST = '';
var FANSHU_DEAD_HOSTS = {};
var FANSHU_SECRET = null;
var FANSHU_NONCE_SEQ = 0;
var FANSHU_HOME = null;
var FANSHU_HOME_SAVED_AT = 0;
var FANSHU_HOME_FAILED_AT = 0;
var FANSHU_DETAIL_CACHE = {};

try {
    if (typeof log === 'function') log('[fanshu] loaded v' + FANSHU_VERSION + ' (http options=json)');
} catch (e) {}

function _trim(s) {
    return s == null ? '' : String(s).replace(/^\s+|\s+$/g, '');
}

function _parse(s) {
    if (s == null || s === '') return null;
    if (typeof s === 'object') return s;
    try {
        return typeof parseJson === 'function' ? parseJson(String(s)) : JSON.parse(String(s));
    } catch (e) {
        try { return JSON.parse(String(s)); } catch (e2) { return null; }
    }
}

function _json(o) {
    return JSON.stringify(o == null ? {} : o);
}

function _sha256(s) {
    return sha256(String(s == null ? '' : s));
}

function _rfc3986(s) {
    return encodeURIComponent(String(s == null ? '' : s)).replace(/[!'()*]/g, function (c) {
        return '%' + c.charCodeAt(0).toString(16).toUpperCase();
    });
}

function _queryPairs(obj) {
    var out = [], key, value, values, i;
    obj = obj || {};
    for (key in obj) {
        if (!Object.prototype.hasOwnProperty.call(obj, key)) continue;
        value = obj[key];
        if (value == null) continue;
        values = Array.isArray(value) ? value : [value];
        for (i = 0; i < values.length; i++) {
            if (values[i] == null) continue;
            out.push([String(key), String(values[i])]);
        }
    }
    return out;
}

function _queryString(obj) {
    var pairs = _queryPairs(obj), out = [], i;
    for (i = 0; i < pairs.length; i++) {
        out.push(_rfc3986(pairs[i][0]) + '=' + _rfc3986(pairs[i][1]));
    }
    return out.join('&');
}

function _canonicalQuery(obj) {
    var pairs = _queryPairs(obj).filter(function (p) {
        return p[0].toLowerCase() !== 'token' && p[0].toLowerCase() !== 'sign';
    });
    pairs.sort(function (a, b) {
        return a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : a[1] < b[1] ? -1 : a[1] > b[1] ? 1 : 0;
    });
    var out = [], i;
    for (i = 0; i < pairs.length; i++) {
        out.push(_rfc3986(pairs[i][0]) + '=' + _rfc3986(pairs[i][1]));
    }
    return out.join('&');
}

function _nowMs() {
    try {
        if (typeof timestamp === 'function') return Number(timestamp());
    } catch (e) {}
    return new Date().getTime();
}

function _nowSec() {
    return Math.floor(_nowMs() / 1000);
}

function _nonce() {
    FANSHU_NONCE_SEQ += 1;
    return _sha256(FANSHU_DEVICE_ID + ':' + _nowMs() + ':' + FANSHU_NONCE_SEQ).substring(0, 32);
}

function _hmacHex(key, message) {
    return crypto.hmac('SHA-256', String(key), String(message), {
        keyFormat: 'utf8',
        output: 'hex'
    });
}

function _hmacBase64Url(key, message) {
    var b64 = crypto.hmac('SHA-256', String(key), String(message), {
        keyFormat: 'utf8',
        output: 'base64'
    });
    return String(b64 || '').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

function _signature(method, action, query, body, key, fixedTimestamp, fixedNonce) {
    var ts = fixedTimestamp == null ? String(_nowMs()) : String(fixedTimestamp);
    var nonce = fixedNonce || _nonce();
    var message = [
        String(method || 'GET').toUpperCase(),
        '/yoapp.php',
        String(action || ''),
        FANSHU_DEVICE_ID,
        ts,
        nonce,
        _sha256(_canonicalQuery(query || {})),
        _sha256(body || '')
    ].join('\n');
    return {
        timestamp: ts,
        nonce: nonce,
        sign: _hmacBase64Url(key, message),
        message: message
    };
}

function _hostList() {
    if (FANSHU_HOSTS) return FANSHU_HOSTS;
    var out = [], seen = {};
    function add(value) {
        var s = _trim(value);
        if (!s) return;
        if (/^\/?yoapp\.php/i.test(s)) s = 'https://' + s;
        if (!/^https?:\/\//i.test(s)) s = 'https://' + s;
        s = s.replace(/\/+$/, '');
        if (!/\/yoapp\.php$/i.test(s)) s += '/yoapp.php';
        if (!seen[s]) { seen[s] = 1; out.push(s); }
    }
    /* 当前 APK 主域固定放首位，避免 ext 中的旧域抢占优先级。 */
    for (var p = 0; p < FANSHU_PRIMARY_HOSTS.length; p++) add(FANSHU_PRIMARY_HOSTS[p]);
    var custom = FANSHU_EXT.hosts || FANSHU_EXT.host;
    if (Array.isArray(custom)) {
        for (var i = 0; i < custom.length; i++) add(custom[i]);
    } else if (custom) {
        String(custom).split(',').forEach(add);
    }
    for (var j = 0; j < FANSHU_LEGACY_HOSTS.length; j++) add(FANSHU_LEGACY_HOSTS[j]);
    FANSHU_HOSTS = out.length ? out : FANSHU_PRIMARY_HOSTS.concat(FANSHU_LEGACY_HOSTS);
    return FANSHU_HOSTS;
}

/*
 * QuickJS 的 HTTP 桥是同步的，不能像 APK 那样并发探测多个域名。
 * 记住已成功域名，并把刚失败的域名冷却 120 秒，避免每个契约函数
 * 都重新在失效节点上阻塞一整轮。
 */
function _hostOrder() {
    var all = _hostList(), out = [], seen = {}, now = _nowMs();
    function add(h) {
        if (!h || seen[h]) return;
        var failedAt = Number(FANSHU_DEAD_HOSTS[h] || 0);
        if (failedAt && now - failedAt < FANSHU_DEAD_HOST_RETRY_MS) return;
        seen[h] = 1;
        out.push(h);
    }
    add(FANSHU_ACTIVE_HOST);
    for (var i = 0; i < all.length; i++) add(all[i]);
    return out;
}

function _hostSucceeded(host) {
    FANSHU_ACTIVE_HOST = host;
    delete FANSHU_DEAD_HOSTS[host];
}

function _hostFailed(host, err) {
    if (err && err.auth) return;
    FANSHU_DEAD_HOSTS[host] = _nowMs();
    if (FANSHU_ACTIVE_HOST === host) FANSHU_ACTIVE_HOST = '';
}

function _headers(signature, secretRequest) {
    var h = {
        'User-Agent': 'Dart',
        'Accept': 'application/json',
        'Content-Type': 'application/json',
        'X-Yoapp-Device-Id': FANSHU_DEVICE_ID,
        'X-Yoapp-Timestamp': signature.timestamp,
        'X-Yoapp-Nonce': signature.nonce,
        'X-Yoapp-Sign': signature.sign,
        'X-App-Guard-543115e6': FANSHU_GUARD
    };
    if (secretRequest) h['X-API-TOKEN'] = FANSHU_TOKEN;
    return h;
}

function _http(method, url, body, headers) {
    var opts = { headers: headers, timeout: FANSHU_HTTP_TIMEOUT };
    var optsJson = _json(opts);
    if (typeof http !== 'undefined' && http) {
        try {
            /*
             * 当前 App 的 __JB.request2/post2 最终接收 String optionsJson。
             * 直接传对象会被桥接成无效字符串，导致全部自定义请求头丢失。
             */
            var r = method === 'POST'
                ? http.post2(url, body || '', optsJson)
                : http.request2(url, optsJson);
            if (r && typeof r === 'object') {
                return { status: Number(r.status || 0), body: String(r.body == null ? '' : r.body), ok: r.ok !== false };
            }
            throw new Error('http.request2 返回空响应');
        } catch (e) {
            /*
             * 增强桥已经真正发过请求，失败后不能再调用老 request/post 重放一次。
             * 原实现会让每个超时节点被请求两遍，两个旧域失效时看起来就是整源卡死。
             */
            throw e;
        }
    }
    if (method === 'POST' && typeof post === 'function') {
        return { status: 200, body: String(post(url, body || '', optsJson) || ''), ok: true };
    }
    if (typeof request === 'function') {
        return { status: 200, body: String(request(url, optsJson) || ''), ok: true };
    }
    throw new Error('当前环境没有 request/http 内置函数');
}

function _apiError(message, status, auth) {
    var e = new Error(String(message || '番薯接口请求失败'));
    e.status = status || 0;
    e.auth = !!auth;
    return e;
}

function _authText(s) {
    return /signature|sign|device.secret|device_secret|nonce|token|guard|密钥|签名|设备/i.test(String(s || ''));
}

function _decodeEnvelope(raw, status) {
    var outer = _parse(raw);
    if (!outer || typeof outer !== 'object') throw _apiError('番薯接口返回非法 JSON', status, false);
    if (outer.success === false) {
        var msg = outer.message || outer.msg || outer.error || outer.error_code || '接口返回失败';
        throw _apiError(msg, status, status === 401 || status === 403 || _authText(msg));
    }
    if (typeof outer.data === 'string' && outer.ek) {
        try {
            var kekHex = _sha256(FANSHU_SALT).substring(0, 32);
            var material = crypto.aes.decrypt(outer.ek, kekHex, {
                mode: 'ECB', padding: 'PKCS5', keyFormat: 'hex', input: 'base64', output: 'utf8'
            });
            if (!material) throw new Error('ek 解密结果为空');
            var keyHex = _sha256(material + FANSHU_SALT);
            var ivHex = _sha256(FANSHU_SALT + material).substring(0, 32);
            var plain = crypto.aes.decrypt(outer.data, keyHex, {
                mode: 'CBC', padding: 'PKCS5', keyFormat: 'hex', ivFormat: 'hex', iv: ivHex,
                input: 'base64', output: 'utf8'
            });
            if (!plain) throw new Error('data 解密结果为空');
            var decoded = _parse(plain);
            return decoded == null ? plain : decoded;
        } catch (e) {
            throw _apiError('番薯响应解密失败: ' + e, status, false);
        }
    }
    if (outer.data && typeof outer.data === 'object' && !outer.sections && !outer.play_sources) return outer.data;
    return outer;
}

function _storageKey() {
    return 'fanshu_secret_' + _sha256(FANSHU_DEVICE_ID).substring(0, 16);
}

function _readStoredSecret() {
    try {
        if (typeof getItem !== 'function') return null;
        var x = _parse(getItem(_storageKey(), ''));
        if (x && x.deviceId === FANSHU_DEVICE_ID && x.base && x.bound && Number(x.expiresAt) > _nowSec() + 30) return x;
    } catch (e) {}
    return null;
}

function _writeStoredSecret(x) {
    try {
        if (typeof setItem === 'function') setItem(_storageKey(), _json(x));
    } catch (e) {}
}

function _clearStoredSecret() {
    FANSHU_SECRET = null;
    try { if (typeof removeItem === 'function') removeItem(_storageKey()); } catch (e) {}
}

function _publicIp() {
    var endpoints = [
        'https://api64.ipify.org?format=json',
        'https://api.ipify.org?format=json',
        'https://ifconfig.me/ip'
    ];
    for (var i = 0; i < endpoints.length; i++) {
        try {
            var raw = _http('GET', endpoints[i], '', { 'User-Agent': 'Dart', 'Accept': 'application/json', 'Content-Type': 'application/json' }).body;
            var j = _parse(raw);
            var ip = j && j.ip ? j.ip : _trim(raw);
            if (ip && /^[0-9a-f:.]+$/i.test(ip)) return ip;
        } catch (e) {}
    }
    return '';
}

function _refreshSecret() {
    var ip = _publicIp();
    var body = _json({ device_id: FANSHU_DEVICE_ID, ip: base64Encode(ip) });
    /*
     * 部分 Lanerc 构建的增强 HTTP 桥会丢失 X-API-TOKEN 自定义头。
     * device_secret 同时接受查询串 token；头和查询串双带可兼容这些构建。
     * _canonicalQuery() 本来就排除 token，因此不会改变 APK 的签名消息。
     */
    var q = { action: 'device_secret', token: FANSHU_TOKEN };
    var last = null;
    var hosts = _hostOrder();
    for (var i = 0; i < hosts.length; i++) {
        var host = hosts[i];
        /* APK 的 device_secret 只发直连节点；Supabase proxy 仅兜底普通 API。 */
        if (host.indexOf('supabase.co/functions/v1/proxy/') >= 0) continue;
        try {
            var sig = _signature('POST', 'device_secret', q, body, _sha256(FANSHU_TOKEN + FANSHU_DEVICE_ID + FANSHU_SALT));
            var res = _http('POST', host + '?' + _queryString(q), body, _headers(sig, true));
            if (res.status && (res.status < 200 || res.status >= 300)) throw _apiError('device_secret HTTP ' + res.status + ': ' + res.body, res.status, res.status === 401 || res.status === 403);
            var d = _decodeEnvelope(res.body, res.status);
            if (!d || !d.device_secret) throw _apiError('device_secret 缺少 device_secret', res.status, true);
            var expires = Number(d.expires_at || (_nowSec() + Number(d.ttl || 86400)));
            var base = String(d.device_secret);
            var bound = _hmacHex(base, FANSHU_APP_SIGNATURE);
            FANSHU_SECRET = { deviceId: FANSHU_DEVICE_ID, base: base, bound: bound, expiresAt: expires };
            _writeStoredSecret(FANSHU_SECRET);
            _hostSucceeded(host);
            return FANSHU_SECRET;
        } catch (e) { last = e; _hostFailed(host, e); }
    }
    throw last || _apiError('device_secret 请求失败', 0, true);
}

function _loadSecret(force) {
    if (!force && FANSHU_SECRET && FANSHU_SECRET.expiresAt > _nowSec() + 30) return FANSHU_SECRET;
    if (!force) {
        var stored = _readStoredSecret();
        if (stored) { FANSHU_SECRET = stored; return stored; }
        if (FANSHU_DEVICE_ID === FANSHU_DEFAULT_DEVICE_ID && FANSHU_FALLBACK_BASE_SECRET && FANSHU_FALLBACK_EXPIRES_AT > _nowSec() + 30) {
            var bound = _hmacHex(FANSHU_FALLBACK_BASE_SECRET, FANSHU_APP_SIGNATURE);
            FANSHU_SECRET = { deviceId: FANSHU_DEVICE_ID, base: FANSHU_FALLBACK_BASE_SECRET, bound: bound, expiresAt: FANSHU_FALLBACK_EXPIRES_AT };
            return FANSHU_SECRET;
        }
    }
    return _refreshSecret();
}

function _callHost(host, action, params, key, method, body) {
    var query = { action: action, token: FANSHU_TOKEN }, k;
    params = params || {};
    for (k in params) if (Object.prototype.hasOwnProperty.call(params, k)) query[k] = params[k];
    var payload = body || '';
    var sig = _signature(method || 'GET', action, query, payload, key);
    var res = _http(method || 'GET', host + '?' + _queryString(query), payload, _headers(sig, false));
    if (res.status && (res.status < 200 || res.status >= 300)) {
        throw _apiError('HTTP ' + res.status + ': ' + res.body, res.status, res.status === 401 || res.status === 403 || _authText(res.body));
    }
    return _decodeEnvelope(res.body, res.status);
}

function _api(action, params) {
    var last = null, shouldRefresh = false, attempt;
    for (attempt = 0; attempt < 2; attempt++) {
        var secret;
        try { secret = _loadSecret(attempt > 0 || shouldRefresh); }
        catch (e) { last = e; break; }
        shouldRefresh = false;
        var hosts = _hostOrder();
        for (var i = 0; i < hosts.length; i++) {
            try {
                var value = _callHost(hosts[i], action, params, secret.bound, 'GET', '');
                _hostSucceeded(hosts[i]);
                return value;
            } catch (e2) {
                last = e2;
                if (e2 && e2.auth) shouldRefresh = true;
                else _hostFailed(hosts[i], e2);
            }
        }
        if (!shouldRefresh) break;
        _clearStoredSecret();
    }
    throw last || _apiError('番薯接口请求失败', 0, false);
}

function _safeCall(action, params) {
    try { return _api(action, params); }
    catch (e) {
        try { if (typeof log === 'function') log('[fanshu] ' + action + ': ' + e); } catch (e2) {}
        return null;
    }
}

function _clean(s) {
    if (s == null) return '';
    return _trim(String(s).replace(/<[^>]+>/g, '')
        .replace(/&nbsp;/gi, ' ').replace(/&amp;/gi, '&').replace(/&quot;/gi, '"')
        .replace(/&#0?39;/g, "'").replace(/&#x27;/gi, "'")
        .replace(/&lt;/gi, '<').replace(/&gt;/gi, '>')
        .replace(/&#(\d+);/g, function (m, n) { return String.fromCharCode(parseInt(n, 10)); }));
}

function _pic(v) {
    return _trim(v && (v.vod_pic_slide || v.vod_pic_thumb || v.vod_pic || v.pic || v.cover || v.image || ''));
}

function _typeName(typeId, fallback) {
    var id = String(typeId == null ? '' : typeId);
    if (id === '1') return 'TV番剧';
    if (id === '22') return '国产动漫';
    if (id === '3') return '剧场版';
    if (id === '20') return '4K分区';
    if (id === '21') return '欧美动漫';
    return _clean(fallback || '动漫');
}

function _card(v, fallbackType) {
    if (!v || typeof v !== 'object') return null;
    var id = v.vod_id == null ? (v.id == null ? '' : v.id) : v.vod_id;
    var name = v.vod_name || v.title || v.name || '';
    if (id == null || !String(id) || !name) return null;
    return {
        id: String(id),
        name: _clean(name),
        pic: _pic(v),
        type: _typeName(v.type_id, v.type_name || v.vod_type_name || v.type || fallbackType),
        year: v.vod_year == null ? (v.year == null ? '' : String(v.year)) : String(v.vod_year),
        remarks: _clean(v.vod_remarks || v.remarks || ''),
        desc: _clean(v.vod_blurb || v.vod_content || v.description || v.desc || '')
    };
}

function _mapList(rows, fallbackType) {
    var out = [], seen = {};
    rows = rows || [];
    for (var i = 0; i < rows.length; i++) {
        var c = _card(rows[i], fallbackType);
        if (!c || seen[c.id]) continue;
        seen[c.id] = 1;
        out.push(c);
    }
    return out;
}

function _extractRows(data) {
    if (!data) return [];
    if (Array.isArray(data)) return data;
    if (typeof data !== 'object') return [];
    var direct = ['list', 'items', 'results', 'videos', 'rows'];
    for (var i = 0; i < direct.length; i++) if (Array.isArray(data[direct[i]])) return data[direct[i]];
    var keys = [], k;
    for (k in data) if (Object.prototype.hasOwnProperty.call(data, k) && /^\d+$/.test(k)) keys.push(k);
    keys.sort(function (a, b) { return Number(a) - Number(b); });
    var out = [];
    for (var j = 0; j < keys.length; j++) if (data[keys[j]] && typeof data[keys[j]] === 'object' && data[keys[j]].vod_id != null) out.push(data[keys[j]]);
    if (out.length) return out;
    for (k in data) if (Object.prototype.hasOwnProperty.call(data, k) && data[k] && typeof data[k] === 'object' && data[k].vod_id != null) out.push(data[k]);
    return out;
}

function _readHomeCache() {
    try {
        if (typeof getItem !== 'function') return null;
        var raw = _parse(getItem(FANSHU_HOME_CACHE_KEY, ''));
        if (raw && raw.data && Array.isArray(raw.data.sections) && raw.data.sections.length) {
            return { data: raw.data, savedAt: Number(raw.savedAt || 0) };
        }
        /* 兼容 v1.1.x 直接保存 home_sections 对象的旧缓存。 */
        var legacy = _parse(getItem('fanshu_home_sections_v1', ''));
        if (legacy && Array.isArray(legacy.sections) && legacy.sections.length) {
            return { data: legacy, savedAt: 0 };
        }
    } catch (e) {}
    return null;
}

function _writeHomeCache(data, savedAt) {
    try {
        if (typeof setItem === 'function') {
            setItem(FANSHU_HOME_CACHE_KEY, _json({ savedAt: savedAt, data: data }));
        }
    } catch (e) {}
}

function _homeData() {
    var now = _nowMs();
    if (FANSHU_HOME && Array.isArray(FANSHU_HOME.sections) && FANSHU_HOME.sections.length) {
        if (now - FANSHU_HOME_SAVED_AT < FANSHU_HOME_CACHE_FRESH_MS ||
            now - FANSHU_HOME_FAILED_AT < FANSHU_DEAD_HOST_RETRY_MS) return FANSHU_HOME;
    }
    if (FANSHU_HOME && FANSHU_HOME._failedAt && now - FANSHU_HOME._failedAt < FANSHU_DEAD_HOST_RETRY_MS) return FANSHU_HOME;

    var cached = _readHomeCache();
    if (!FANSHU_HOME && cached && cached.savedAt > 0 && now - cached.savedAt < FANSHU_HOME_CACHE_FRESH_MS) {
        FANSHU_HOME = cached.data;
        FANSHU_HOME_SAVED_AT = cached.savedAt;
        return FANSHU_HOME;
    }

    var d = _safeCall('home_sections', { limit: 12 });
    if (d && Array.isArray(d.sections) && d.sections.length) {
        FANSHU_HOME = d;
        FANSHU_HOME_SAVED_AT = now;
        FANSHU_HOME_FAILED_AT = 0;
        _writeHomeCache(d, now);
        return FANSHU_HOME;
    }

    FANSHU_HOME_FAILED_AT = now;
    if (cached && cached.data && Array.isArray(cached.data.sections) && cached.data.sections.length) {
        FANSHU_HOME = cached.data;
        FANSHU_HOME_SAVED_AT = Number(cached.savedAt || 0);
        return FANSHU_HOME;
    }
    FANSHU_HOME = { sections: [], _failedAt: now };
    return FANSHU_HOME;
}

function _sectionClassItems(section) {
    var values = section && section.extend_class_items;
    if (!Array.isArray(values)) values = _trim(section && section.extend_class || '').split(/[,，]/);
    var out = [], seen = {};
    for (var i = 0; i < values.length; i++) {
        var x = _trim(values[i]);
        if (x && !seen[x]) { seen[x] = 1; out.push(x); }
    }
    return out;
}

function _sectionYearItems(section) {
    var values = section && section.extend_year_items;
    if (!Array.isArray(values)) values = _trim(section && section.extend_year || '').split(/[,，]/);
    var out = [], seen = {};
    for (var i = 0; i < values.length; i++) {
        var x = _trim(values[i]);
        if (/^\d{4}$/.test(x) && !seen[x]) { seen[x] = 1; out.push(x); }
    }
    return out;
}

function _filtersForSection(section) {
    var classes = _sectionClassItems(section), years = _sectionYearItems(section);
    if (!classes.length) classes = ['热血', '战斗', '奇幻', '恋爱', '校园', '搞笑', '日常', '异世界', '治愈', '冒险', '科幻'];
    if (!years.length) years = ['2026', '2025', '2024', '2023', '2022', '2021', '2020'];
    var classValues = [{ n: '全部', v: '' }], yearValues = [{ n: '全部', v: '' }];
    for (var i = 0; i < classes.length && i < 80; i++) classValues.push({ n: classes[i], v: classes[i] });
    for (var j = 0; j < years.length && j < 40; j++) yearValues.push({ n: years[j], v: years[j] });
    return [
        { key: 'class', name: '题材', value: classValues },
        { key: 'year', name: '年份', value: yearValues },
        { key: 'sort', name: '排序', value: [{ n: '最新', v: 'latest' }, { n: '最热', v: 'hot' }] }
    ];
}

function config() {
    return JSON.stringify({ browseOnly: false });
}

function categories() {
    /*
     * 分类框架必须立即返回，不能为了生成筛选项先请求 home_sections。
     * 番薯上游整体超时时，旧写法会让 App 连 tab 都等完整个域名回退链。
     * 若本 context 已成功取过首页，再用真实扩展项增强筛选；否则使用静态默认值。
     */
    var sections = FANSHU_HOME && Array.isArray(FANSHU_HOME.sections) ? FANSHU_HOME.sections : [], byId = {};
    for (var i = 0; i < sections.length; i++) byId[String(sections[i].type_id || '')] = sections[i];
    var defs = [
        { key: '', title: '精选' },
        { key: '1', title: 'TV番剧' },
        { key: '22', title: '国产动漫' },
        { key: '3', title: '剧场版' },
        { key: '20', title: '4K分区' },
        { key: '21', title: '欧美动漫' }
    ];
    for (var j = 1; j < defs.length; j++) defs[j].filters = _filtersForSection(byId[defs[j].key] || {});
    return JSON.stringify(defs);
}

function _listAction(action, params) {
    var d = _safeCall(action, params);
    return _mapList(_extractRows(d), d && d.type_name);
}

function search(keyword, page) {
    keyword = _trim(keyword);
    page = Number(page || 1);
    if (page < 1) page = 1;
    var list;
    if (keyword) {
        list = _listAction('search_videos', { keyword: keyword, page: page, page_size: 12 });
    } else {
        if (page > 1) return '[]';
        var sections = _homeData().sections || [], rows = [];
        for (var i = 0; i < sections.length; i++) {
            var sectionCards = _mapList(sections[i].data || [], sections[i].type_name);
            for (var j = 0; j < sectionCards.length; j++) rows.push(sectionCards[j]);
        }
        list = _mapList(rows);
        if (!list.length) list = _listAction('category_videos', { type_id: 1, page: 1, page_size: 12, class: '', year: '', sort: 'latest' });
    }
    return JSON.stringify(list);
}

function searchFiltered(category, filtersJson, page) {
    page = Number(page || 1);
    if (page < 1) page = 1;
    var f = _parse(filtersJson) || {};
    var typeId = _trim(category) || '1';
    var sort = String(f.sort || 'latest').toLowerCase() === 'hot' ? 'hot' : 'latest';
    var list = _listAction('category_videos', {
        type_id: typeId,
        page: page,
        page_size: 12,
        class: f.class == null ? '' : String(f.class),
        year: f.year == null ? '' : String(f.year),
        sort: sort
    });
    return JSON.stringify(list);
}

function homeSections() {
    var d = _homeData(), sections = d.sections || [], out = [];
    for (var i = 0; i < sections.length; i++) {
        var s = sections[i] || {}, items = _mapList(s.data || [], s.type_name);
        if (!items.length) continue;
        out.push({ title: _clean(s.type_name || _typeName(s.type_id, '动漫')), key: String(s.type_id || ''), items: items });
    }
    var hero = _safeCall('anime_carousel', { limit: 10 });
    var heroItems = _mapList(_extractRows(hero), '精选');
    if (heroItems.length) out.push({ title: '精选推荐', key: '__hero__', items: heroItems });
    return JSON.stringify(out);
}

function _getDetail(id) {
    id = String(id == null ? '' : id);
    if (!id) return null;
    if (FANSHU_DETAIL_CACHE[id]) return FANSHU_DETAIL_CACHE[id];
    var d = _safeCall('video_detail', { vod_id: id });
    if (d && d.data && typeof d.data === 'object' && !d.play_sources) d = d.data;
    if (d) FANSHU_DETAIL_CACHE[id] = d;
    return d;
}

function detail(id) {
    var d = _getDetail(id) || {};
    var vodId = d.vod_id == null ? String(id == null ? '' : id) : d.vod_id;
    var sources = Array.isArray(d.play_sources) ? d.play_sources : [];
    var episodes = [], seen = {};
    for (var i = 0; i < sources.length; i++) {
        var source = sources[i] || {}, route = _clean(source.display_name || source.player_name || source.name || source.from || '在线播放');
        var eps = Array.isArray(source.episodes) ? source.episodes : [];
        for (var j = 0; j < eps.length; j++) {
            var ep = eps[j] || {}, eid = ep.episode_id == null ? (ep.id == null ? '' : ep.id) : ep.episode_id;
            if (eid === '') continue;
            var flag = _json({ v: String(vodId), s: String(source.from || source.source || ''), e: eid, i: j });
            var key = route + '\n' + String(eid);
            if (seen[key]) continue;
            seen[key] = 1;
            episodes.push({ name: _clean(ep.name || ep.episode_name || ('第' + (j + 1) + '集')), url: flag, route: route });
        }
    }
    return JSON.stringify({
        id: String(vodId),
        name: _clean(d.vod_name || d.title || ''),
        pic: _pic(d),
        type: _typeName(d.type_id, d.type_name),
        year: d.vod_year == null ? '' : String(d.vod_year),
        remarks: _clean(d.vod_remarks || ''),
        desc: _clean(d.vod_content || d.vod_blurb || d.vod_desc || d.description || ''),
        episodes: episodes
    });
}

function _relatedFromObject(d, id) {
    var candidates = d && (d.related || d.recommendations || d.related_videos || d.similar || d.relate_list);
    if (candidates && !Array.isArray(candidates)) candidates = _extractRows(candidates);
    return _mapList(Array.isArray(candidates) ? candidates : [], d && d.type_name).filter(function (x) { return x.id !== String(id); });
}

function related(id) {
    var d = _getDetail(id) || {}, embedded = _relatedFromObject(d, id);
    if (embedded.length) return JSON.stringify(embedded.slice(0, 12));
    var typeId = d.type_id == null ? '1' : String(d.type_id);
    var list = _listAction('category_videos', { type_id: typeId, page: 1, page_size: 12, class: '', year: '', sort: 'hot' });
    return JSON.stringify(list.filter(function (x) { return x.id !== String(id); }).slice(0, 12));
}

function play(flag) {
    var f = _parse(flag) || {};
    var vodId = f.v == null ? '' : String(f.v);
    var source = f.s == null ? '' : String(f.s);
    var episodeId = f.e == null ? '' : f.e;
    if (!vodId || !source || episodeId === '') return JSON.stringify({ url: '', type: 'auto' });
    var d = _safeCall('video_play', {
        vod_id: vodId,
        source: source,
        episode_id: episodeId,
        episode_index: f.i == null ? 0 : f.i
    }) || {};
    var url = String(d.play_url || d.url || d.video_url || '');
    var responseHeaders = d.headers && typeof d.headers === 'object' ? d.headers : {};
    var referer = d.referer || responseHeaders.referer || responseHeaders.Referer || '';
    var ua = d.user_agent || responseHeaders.user_agent || responseHeaders['User-Agent'] || '';
    var headers = {}, k;
    for (k in responseHeaders) {
        if (!Object.prototype.hasOwnProperty.call(responseHeaders, k)) continue;
        if (/^(referer|user_agent)$/i.test(k)) continue;
        headers[k] = responseHeaders[k];
    }
    if (ua && !headers['User-Agent']) headers['User-Agent'] = ua;
    var lower = url.toLowerCase();
    var type = lower.indexOf('.m3u8') >= 0 ? 'm3u8' : lower.indexOf('.mp4') >= 0 ? 'mp4' : 'auto';
    var result = { url: url, type: type };
    if (referer) result.referer = referer;
    if (Object.keys(headers).length) result.headers = headers;
    return JSON.stringify(result);
}

function ranking(sort, page) {
    if (Number(page || 1) > 1) return '[]';
    var d = _safeCall('weekly_rankings', { limit: 50 });
    var rows = _extractRows(d), out = [];
    for (var i = 0; i < rows.length; i++) {
        var v = rows[i] || {}, card = _card(v, v.type_name);
        if (!card) continue;
        var tags = _clean(v.vod_class || v.tags || '').split(/[,，]/).filter(function (x) { return !!_trim(x); });
        var year = card.year || (v.date ? String(v.date).substring(0, 4) : '');
        var score = v.score == null ? (v.vod_score == null ? '' : v.vod_score) : v.score;
        var popularity = v.popularity == null ? (v.vod_hits_week == null ? (v.vod_hits == null ? '' : v.vod_hits) : v.vod_hits_week) : v.popularity;
        out.push({ id: card.id, name: card.name, nameJp: _clean(v.nameJp || v.vod_name_en || v.vod_en || v.original || ''), cover: card.pic, year: year, rank: v.rank == null ? i + 1 : Number(v.rank), score: score, popularity: popularity, tags: tags, summary: card.desc });
    }
    return JSON.stringify(out);
}

function _weekdayNumber(x) {
    if (typeof x === 'number' && x >= 1 && x <= 7) return x;
    var s = String(x == null ? '' : x);
    var m = /([1-7])/.exec(s);
    if (m) return Number(m[1]);
    var names = [
        ['周一', '星期一'], ['周二', '星期二'], ['周三', '星期三'], ['周四', '星期四'],
        ['周五', '星期五'], ['周六', '星期六'], ['周日', '星期日', '星期天']
    ];
    for (var i = 0; i < names.length; i++) {
        for (var j = 0; j < names[i].length; j++) if (s.indexOf(names[i][j]) >= 0) return i + 1;
    }
    return 1;
}

function _scheduleItem(v) {
    v = v || {};
    var remarks = _clean(v.vod_remarks || v.remarks || ''), air = v.airTime || v.air_time || v.time || '';
    if (!air && remarks) {
        var tm = /([0-2]?\d)点([0-5]?\d)分?/.exec(remarks);
        if (tm) air = ('0' + tm[1]).slice(-2) + ':' + ('0' + tm[2]).slice(-2);
    }
    var ep = v.episode == null ? v.episode_number : v.episode;
    if (ep == null && remarks) {
        var em = /第\s*(\d+)\s*集/.exec(remarks);
        if (em) ep = Number(em[1]);
    }
    var id = v.vod_id == null ? (v.id == null ? '' : v.id) : v.vod_id;
    var item = { id: String(id), name: _clean(v.vod_name || v.name || v.title || ''), cover: _pic(v), airTime: String(air || ''), episode: ep == null ? '' : Number(ep) || String(ep) };
    if (v.score != null || v.vod_score != null) item.score = v.score == null ? v.vod_score : v.score;
    if (remarks) item.remarks = remarks;
    return item;
}

function calendar() {
    var d = _safeCall('update_schedule', {}), sections = d && Array.isArray(d.sections) ? d.sections : [], days = [];
    for (var i = 0; i < sections.length; i++) {
        var s = sections[i] || {}, list = Array.isArray(s.data) ? s.data : (Array.isArray(s.list) ? s.list : []);
        days.push({ weekday: _weekdayNumber(s.weekday), list: list.map(_scheduleItem).filter(function (x) { return x.id && x.name; }) });
    }
    days.sort(function (a, b) { return a.weekday - b.weekday; });
    return JSON.stringify({ days: days });
}

/* CommonJS / Node 测试导出；Lanerc QuickJS 不会执行这一块。 */
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        config: config,
        categories: categories,
        homeSections: homeSections,
        search: search,
        searchFiltered: searchFiltered,
        detail: detail,
        related: related,
        play: play,
        ranking: ranking,
        calendar: calendar,
        _internal: {
            signature: _signature,
            hostList: _hostList,
            hostOrder: _hostOrder,
            decodeEnvelope: _decodeEnvelope,
            callHost: _callHost,
            api: _api
        }
    };
}