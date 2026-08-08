/*
 * 薯条 APP 源（薯条影视 / api=csp_AppDrama）——【仅动漫】
 * 协议还原自 XS/spider.jar: com.github.catvod.spider.AppDrama。
 *
 * 薯条是综合影视站（电影/剧/综艺/动漫），本源按需求只做动漫：
 *   · categories() 只暴露「动漫」一个分类（key=动漫 typeId1）+ 其筛选维度；
 *   · search() 关键词搜索只保留动漫结果（按 DramaBean.type==动漫 typeId 或 clazz 含 动漫/动画/番 过滤）。
 *
 * 实现：config / categories / search / searchFiltered / detail / play 全量。
 * 底层复用已过离线自测的手写 protobuf（ProtoWriter/ProtoReader/decodeProto）
 * + RSA 握手换动态公钥（handshake）+ 多层 AES(ECB/CBC) + iso-8859-1 字节透传，未改动。
 *
 * 关键端点（均见 AppDrama.java）：
 *   分类列表  GET  /api/v3/drama/getCategory?orderBy=type_id   e() 明文JSON头（decrypt="0" 免解密）
 *   分类浏览  POST /api/proto/v5/drama/category                g() proto（typeId1+筛选）→ DramaBeanPage
 *   搜索      POST /api/proto/v5/drama/search                  g() proto → DramaBeanPage（客户端过滤动漫）
 *   详情      POST /api/proto/v5/drama/getDetail               g() proto → DramaDetailBean
 *   取流      POST /api/proto/v5/videoUsableUrl                g() proto → ParsePlayUrlBean{playUrl,headers}
 */

var EXT = (typeof ext !== 'undefined' && ext) ? ext : {};
var HOST = (EXT.host || '').replace(/\/+$/, '');
var SITE = EXT.site || 'https://dyttandroid-1372779881.cos.ap-guangzhou.myqcloud.com/app_dyttandroid.txt';
var PUBLIC_KEY = EXT.publicKey || 'MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCduNEnfxGaLuQRk5ABzXHhPV43zi00sCHjLo8BYc+Wi6xXm2b4v0i28Sq4WlNCKhseft9fz8kO/qLr6/022o1RcuOU7e4GFL3U9WnNODwRBYSYWd+K8nqpI/tAUDmZEBGRWqjrc7x6aMl3A+xpnWkLbPCLsuhbuuUE3tv09oeOpwIDAQAB';
var DATA_KEY = EXT.dataKey || 'A1VACZJWDKRZY1P3MFV0DDRAZ3F3PT0=';
var DATA_IV = EXT.dataIv || 'OC1A06E197EF10CF3F6058CA7A803B5E';
var PACKAGE_NAME = EXT.pkg || 'com.st.standroid';
var APP_NAME = EXT.appName || '薯条影视';
var VERSION = EXT.version || '5.0.0.1';
var PARAM_KEY = 'ed5fdsgucxumegqa';
var TIMEOUT = 25000;

var DYNAMIC_KEY = '';
var HANDSHAKE_TRIED = false;
var ANDROID_ID = '';
var ALNUM = '1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz';

// 动漫分类缓存（getCategory 解析后填充）：typeId1 + 筛选维度（写法 A）。
var ANIME_TYPE_ID = '';
var ANIME_NAME = '动漫';
var ANIME_FILTERS = null;
var ANIME_RESOLVED = false;
// 动漫 tab 的稳定 key（不随动态 typeId 变）：categories/search/searchFiltered 都认它，
// getCategory 拉不到 typeId 也能正常点进「动漫」tab（browseCategory 再懒解析真 typeId）。
var ANIME_CAT_KEY = '动漫';
// getCategory 里各筛选维度 → /drama/category 提交字段名的映射（见 AppDrama.categoryContent）。
var FILTER_KEYS = ['class', 'lang', 'area', 'year', 'extend_sort'];
var FILTER_LABEL = { 'class': '类型', 'lang': '语言', 'area': '地区', 'year': '年份', 'extend_sort': '排序' };
// getCategory 拿不到 converUrl 筛选时的兜底筛选（地区/年份），保证「动漫」tab 始终有筛选条。
var FALLBACK_FILTERS = (function () {
    var years = [{ n: '全部', v: '' }];
    for (var y = 2026; y >= 2015; y--) years.push({ n: String(y), v: String(y) });
    return [
        { key: 'area', name: '地区', value: [
            { n: '全部', v: '' }, { n: '中国大陆', v: '中国大陆' }, { n: '日本', v: '日本' },
            { n: '美国', v: '美国' }, { n: '其他', v: '其他' }
        ] },
        { key: 'year', name: '年份', value: years }
    ];
})();
// 首页精选分区（走 area 桶，全部限定动漫 typeId）。
var HOME_BUCKETS = [
    { title: '最新动漫', key: ANIME_CAT_KEY, area: '' },
    { title: '日本动漫', key: ANIME_CAT_KEY, area: '日本' },
    { title: '国产动漫', key: ANIME_CAT_KEY, area: '中国大陆' }
];

function trim(s) {
    return s == null ? '' : String(s).replace(/^\s+|\s+$/g, '');
}

function config() {
    return JSON.stringify({ browseOnly: false });
}

function randomText(length) {
    var out = '';
    for (var i = 0; i < length - 1; i++) {
        out += ALNUM.charAt(Math.floor(Math.random() * ALNUM.length));
    }
    return out + '=';
}

function androidId() {
    if (ANDROID_ID) return ANDROID_ID;
    ANDROID_ID = getItem('shutiao_aid') || '';
    if (!ANDROID_ID) {
        while (ANDROID_ID.length < 16) {
            ANDROID_ID += Math.floor(Math.random() * 16).toString(16);
        }
        ANDROID_ID = ANDROID_ID.substring(0, 16);
        setItem('shutiao_aid', ANDROID_ID);
    }
    return ANDROID_ID;
}

function utf8Encode(value) {
    var text = String(value == null ? '' : value);
    var out = '';
    for (var i = 0; i < text.length; i++) {
        var code = text.charCodeAt(i);
        if (code < 0x80) {
            out += String.fromCharCode(code);
        } else if (code < 0x800) {
            out += String.fromCharCode(0xC0 | (code >> 6), 0x80 | (code & 0x3F));
        } else if (code < 0xD800 || code >= 0xE000) {
            out += String.fromCharCode(0xE0 | (code >> 12), 0x80 | ((code >> 6) & 0x3F), 0x80 | (code & 0x3F));
        } else {
            i++;
            var pair = text.charCodeAt(i);
            var point = 0x10000 + ((code & 0x3FF) << 10) + (pair & 0x3FF);
            out += String.fromCharCode(
                0xF0 | (point >> 18),
                0x80 | ((point >> 12) & 0x3F),
                0x80 | ((point >> 6) & 0x3F),
                0x80 | (point & 0x3F)
            );
        }
    }
    return out;
}

function utf8Decode(binary) {
    var out = '';
    var i = 0;
    while (i < binary.length) {
        var first = binary.charCodeAt(i++) & 0xFF;
        if (first < 0x80) {
            out += String.fromCharCode(first);
        } else if (first < 0xE0) {
            var second = binary.charCodeAt(i++) & 0x3F;
            out += String.fromCharCode(((first & 0x1F) << 6) | second);
        } else if (first < 0xF0) {
            var third1 = binary.charCodeAt(i++) & 0x3F;
            var third2 = binary.charCodeAt(i++) & 0x3F;
            out += String.fromCharCode(((first & 0x0F) << 12) | (third1 << 6) | third2);
        } else {
            var fourth1 = binary.charCodeAt(i++) & 0x3F;
            var fourth2 = binary.charCodeAt(i++) & 0x3F;
            var fourth3 = binary.charCodeAt(i++) & 0x3F;
            var point = (((first & 0x07) << 18) | (fourth1 << 12) | (fourth2 << 6) | fourth3) - 0x10000;
            out += String.fromCharCode(0xD800 + (point >> 10), 0xDC00 + (point & 0x3FF));
        }
    }
    return out;
}

function ProtoWriter() {
    this.bytes = [];
}

ProtoWriter.prototype.varint = function (value) {
    var n = Number(value);
    if (!isFinite(n) || n < 0) n = 0;
    n = Math.floor(n);
    while (n > 127) {
        this.bytes.push((n % 128) | 0x80);
        n = Math.floor(n / 128);
    }
    this.bytes.push(n);
};

ProtoWriter.prototype.tag = function (field, wire) {
    this.varint(field * 8 + wire);
};

ProtoWriter.prototype.string = function (field, value) {
    var binary = utf8Encode(value);
    this.tag(field, 2);
    this.varint(binary.length);
    for (var i = 0; i < binary.length; i++) {
        this.bytes.push(binary.charCodeAt(i) & 0xFF);
    }
};

ProtoWriter.prototype.number = function (field, value) {
    this.tag(field, 0);
    this.varint(value);
};

ProtoWriter.prototype.build = function () {
    var out = '';
    for (var i = 0; i < this.bytes.length; i += 8192) {
        out += String.fromCharCode.apply(null, this.bytes.slice(i, i + 8192));
    }
    return out;
};

function ProtoReader(binary) {
    this.binary = binary || '';
    this.position = 0;
}

ProtoReader.prototype.byte = function () {
    return this.binary.charCodeAt(this.position++) & 0xFF;
};

ProtoReader.prototype.varint = function () {
    var result = 0;
    var shift = 0;
    var current;
    do {
        if (this.position >= this.binary.length || shift > 56) return 0;
        current = this.byte();
        result += (current & 0x7F) * Math.pow(2, shift);
        shift += 7;
    } while (current & 0x80);
    return result;
};

function decodeProto(binary) {
    var reader = new ProtoReader(binary);
    var out = {};
    while (reader.position < reader.binary.length) {
        var key = reader.varint();
        var field = Math.floor(key / 8);
        var wire = key & 7;
        var value;
        if (!field) break;
        if (wire === 0) {
            value = reader.varint();
        } else if (wire === 2) {
            var length = reader.varint();
            if (length < 0 || reader.position + length > reader.binary.length) break;
            value = reader.binary.slice(reader.position, reader.position + length);
            reader.position += length;
        } else if (wire === 1) {
            reader.position += 8;
            value = 0;
        } else if (wire === 5) {
            reader.position += 4;
            value = 0;
        } else {
            break;
        }
        if (out[field] === undefined) out[field] = value;
        else if (Array.isArray(out[field])) out[field].push(value);
        else out[field] = [out[field], value];
    }
    return out;
}

function protoList(message, field) {
    var value = message[field];
    if (value === undefined) return [];
    return Array.isArray(value) ? value : [value];
}

function protoString(message, field) {
    var value = message[field];
    return typeof value === 'string' ? utf8Decode(value) : '';
}

function protoNumber(message, field) {
    return typeof message[field] === 'number' ? message[field] : 0;
}

function aesEcbBase64(plain, key) {
    return crypto.aes.encrypt(plain, key, {
        mode: 'ECB',
        padding: 'PKCS7',
        keyFormat: 'utf8',
        input: 'utf8',
        output: 'base64'
    });
}

function aesCbcHex(plain, key) {
    return crypto.aes.encrypt(plain, key, {
        mode: 'CBC',
        padding: 'PKCS7',
        keyFormat: 'utf8',
        iv: key,
        ivFormat: 'utf8',
        input: 'utf8',
        output: 'hex'
    });
}

function rsaBase64(plain, key) {
    return crypto.rsa.encrypt(plain, key, { padding: 'PKCS1', output: 'base64' });
}

function deviceParams() {
    var uuid = '';
    while (uuid.length < 32) uuid += Math.floor(Math.random() * 16).toString(16).toUpperCase();
    uuid = uuid.substring(0, 32);
    return {
        country: 'CN',
        vName: VERSION,
        cpuId: 'MT6893Z%2FCZA',
        young: 0,
        facturer: 'Xiaomi',
        pkg: PACKAGE_NAME,
        uuid: uuid,
        resolution: '1080x2272',
        mac: '02%3A00%3A00%3A00%3A00%3A00',
        abid: '397',
        model: 'M2012K11AC',
        plat: 'android',
        udid: uuid,
        dpi: '440',
        net: '1',
        lang: 'zh',
        brand: 'Xiaomi',
        density: '2.75',
        appName: APP_NAME,
        cpu: 'arm64-v8a',
        chid: '10000',
        carrier: '%E8%81%94%E9%80%9A',
        _vOsCode: 33,
        vOs: '13',
        v: 1,
        tenantId: '',
        vApp: String(VERSION).replace(/\./g, ''),
        device: 0,
        androidID: androidId()
    };
}

function protoHeaders() {
    var key = DYNAMIC_KEY || PUBLIC_KEY;
    var params = deviceParams();
    var now = timestamp();
    var random = randomText(16);
    var splitSign = aesEcbBase64(String(now) + random, DATA_IV);
    params.sig = rsaBase64(String(now) + random + params.vApp, key);
    params.random_str = random;
    params.timestamp = now;
    params.sig2 = splitSign.substring(0, 8);
    params.sig3 = splitSign.substring(8);
    return {
        'User-Agent': 'okhttp/3.12.1',
        'Accept': 'application/x-protobuf',
        'Content-Type': 'application/x-protobuf; charset=iso-8859-1',
        'publicParams': JSON.stringify({ paramsData: aesCbcHex(JSON.stringify(params), PARAM_KEY) })
    };
}

// e() 等价：明文 JSON 接口（getCategory）用的头。device params 直接 CBC(PARAM_KEY)，无 sig/握手。
function jsonHeaders() {
    var params = deviceParams();
    return {
        'User-Agent': 'okhttp/3.12.1',
        'Accept': 'application/json',
        'Content-Type': 'application/json; charset=utf-8',
        'publicParams': JSON.stringify({ paramsData: aesCbcHex(JSON.stringify(params), PARAM_KEY) })
    };
}

function queryString(params) {
    var pairs = [];
    for (var key in params) {
        if (!params.hasOwnProperty(key)) continue;
        var value = params[key];
        if (value == null || String(value) === '') continue;
        pairs.push(key + '=' + value);
    }
    return pairs.join('&');
}

function secureRequest(params) {
    var now = timestamp();
    var random = randomText(8);
    var encrypted = random + aesEcbBase64(queryString(params) + now, DATA_KEY);
    var writer = new ProtoWriter();
    writer.string(1, encrypted.substring(0, 20));
    writer.string(2, encrypted.substring(20));
    writer.string(3, randomText(20));
    writer.number(4, now);
    writer.string(5, random);
    return writer.build();
}

function rsaRequest() {
    var now = timestamp();
    var random = randomText(16);
    var writer = new ProtoWriter();
    writer.number(1, now);
    writer.string(2, rsaBase64(String(now) + random, PUBLIC_KEY));
    writer.string(3, randomText(16));
    writer.string(4, random);
    writer.string(5, randomText(16));
    return writer.build();
}

function resolveHost() {
    if (HOST || !SITE) return;
    try {
        var response = request(SITE, JSON.stringify({ timeout: 8000 }));
        var data = parseJson(response) || {};
        if (data.domain) HOST = trim(data.domain).replace(/\/+$/, '');
    } catch (e) {
        log('[shutiao] resolve host failed: ' + e);
    }
}

function handshake() {
    if (!HOST) return false;
    try {
        var response = http.post2(
            HOST + '/api/v5/find/app/zone',
            rsaRequest(),
            JSON.stringify({ headers: protoHeaders(), charset: 'iso-8859-1', timeout: TIMEOUT })
        );
        if (!response || !response.body) return false;
        var envelope = decodeProto(response.body);
        if (typeof envelope[3] !== 'string') return false;
        var keyParts = decodeProto(envelope[3]);
        DYNAMIC_KEY = protoString(keyParts, 2) + protoString(keyParts, 3) + protoString(keyParts, 4) + protoString(keyParts, 5);
        return !!DYNAMIC_KEY;
    } catch (e) {
        log('[shutiao] handshake failed: ' + e);
        return false;
    }
}

// 握手只为「升级到动态公钥」，best-effort：失败也绝不阻断后续请求。
// protoHeaders() 在 DYNAMIC_KEY 为空时回退用 PUBLIC_KEY 签名，实测服务端对 PUBLIC_KEY 签名的
// category/search/getDetail/videoUsableUrl 同样放行（code=200 出数据）；而 /api/v5/find/app/zone
// 握手端点在真机上常年返回「RSA解密失败」。旧实现 `if(!DYNAMIC_KEY && !handshake()) return []`
// 会因握手必败而直接返回空 → 整源不出任何数据。HANDSHAKE_TRIED 保证一次会话只试一次、不反复白打。
function ensureHandshake() {
    if (DYNAMIC_KEY || HANDSHAKE_TRIED) return;
    HANDSHAKE_TRIED = true;
    try { handshake(); } catch (e) { log('[shutiao] handshake err(ignored): ' + e); }
}

function protoPost(path, params) {
    try {
        var response = http.post2(
            HOST + path,
            secureRequest(params),
            JSON.stringify({ headers: protoHeaders(), charset: 'iso-8859-1', timeout: TIMEOUT })
        );
        if (!response || !response.body) return '';
        var envelope = decodeProto(response.body);
        return typeof envelope[3] === 'string' ? envelope[3] : '';
    } catch (e) {
        log('[shutiao] request failed ' + path + ': ' + e);
        return '';
    }
}

function mapDrama(binary) {
    var drama = decodeProto(binary);
    var pic = '';
    if (typeof drama[2] === 'string') {
        // DramaCoverImageBean: path=1, thumbnail_path=2
        var cover = decodeProto(drama[2]);
        pic = protoString(cover, 2) || protoString(cover, 1);
    }
    // DramaBean 字段号：id=3 name=5 type=8(分类type_id,int) remark=13 year=14(int) clazz=15
    var year = protoNumber(drama, 14);
    var clazz = protoString(drama, 15);
    return {
        id: String(protoNumber(drama, 3)),
        name: protoString(drama, 5),
        pic: pic,
        remarks: protoString(drama, 13),
        year: year ? String(year) : '',
        type: clazz,                 // 展示用分类名（如「动漫」）
        desc: '',
        _typeId: protoNumber(drama, 8)   // 供动漫过滤，parseList 会忽略下划线字段
    };
}

/** 是否动漫：命中动漫分类 type_id，或分类名(clazz)含 动漫/动画/番。 */
function isAnimeItem(item) {
    if (!item) return false;
    if (ANIME_TYPE_ID && String(item._typeId) === String(ANIME_TYPE_ID)) return true;
    return /动漫|动画|番/.test(item.type || '');
}

var MEDIA_RE = /\.(mp4|m3u8|flv|mkv|avi|ts|mov|mpd|m4a|wmv)(\?.*)?$/i;
function isMediaUrl(u) {
    return MEDIA_RE.test(String(u == null ? '' : u));
}
function guessType(u) {
    var l = String(u == null ? '' : u).toLowerCase();
    if (l.indexOf('.m3u8') >= 0) return 'm3u8';
    if (l.indexOf('.mp4') >= 0) return 'mp4';
    return 'auto';
}

function mapPage(binary) {
    var out = [];
    if (!binary) return out;
    var page = decodeProto(binary);
    var dramas = protoList(page, 1);
    for (var i = 0; i < dramas.length; i++) {
        if (typeof dramas[i] !== 'string') continue;
        var item = mapDrama(dramas[i]);
        if (item.id !== '0' && item.name) out.push(item);
    }
    return out;
}

function fallbackSearch(keyword, page) {
    try {
        var url = HOST + '/api/v3/debug/drama/search?searchKeys=' + encodeURIComponent(keyword) +
            '&page=' + (page || 1) + '&pagesize=21';
        var raw = request(url, JSON.stringify({ headers: { 'User-Agent': 'okhttp/3.12.1' }, timeout: TIMEOUT }));
        var response = parseJson(raw) || {};
        var list = response.data && response.data.list ? response.data.list : [];
        var out = [];
        for (var i = 0; i < list.length; i++) {
            var drama = list[i] || {};
            if (drama.id == null || !drama.name) continue;
            var cover = drama.coverImage || {};
            out.push({
                id: String(drama.id),
                name: String(drama.name),
                pic: cover.thumbnailPath || cover.path || '',
                remarks: drama.remark || '',
                year: drama.year || '',
                type: drama.clazz || '',
                desc: drama.brief || ''
            });
        }
        return out;
    } catch (e) {
        log('[shutiao] fallback search failed: ' + e);
        return [];
    }
}

// 只保留动漫。ANIME_TYPE_ID 已知 → 按分类 type_id 精确过滤；未知 → 退化为 clazz 名正则。
function filterAnime(items) {
    var out = [];
    for (var i = 0; i < items.length; i++) {
        if (isAnimeItem(items[i])) out.push(items[i]);
    }
    return out;
}

/* ───────────────────────────── 分类（仅动漫） ───────────────────────────── */

// GET /api/v3/drama/getCategory：明文 JSON，解析出动漫分类 typeId1 + 其筛选维度。
// 只解析一次，结果缓存进模块变量（并持久化 typeId 便于下次直接命中）。
function resolveAnimeCategory() {
    if (ANIME_RESOLVED) return;
    ANIME_RESOLVED = true;
    resolveHost();
    if (!HOST) { ANIME_RESOLVED = false; return; }
    // 先吃持久化缓存（仅 typeId；筛选项每次现拉，站点会调整）
    if (!ANIME_TYPE_ID) ANIME_TYPE_ID = getItem('shutiao_anime_type') || '';
    try {
        var raw = request(
            HOST + '/api/v3/drama/getCategory?orderBy=type_id',
            JSON.stringify({ headers: jsonHeaders(), timeout: TIMEOUT })
        );
        var data = (parseJson(raw) || {}).data;
        if (!data || !data.length) return;
        for (var i = 0; i < data.length; i++) {
            var cat = data[i] || {};
            var name = trim(cat.name);
            if (!name || name === '公告') continue;
            if (!/动漫|动画|番/.test(name)) continue;
            ANIME_TYPE_ID = String(cat.id);
            ANIME_NAME = name;
            ANIME_FILTERS = parseCategoryFilters(cat.converUrl);
            setItem('shutiao_anime_type', ANIME_TYPE_ID);
            return;
        }
    } catch (e) {
        log('[shutiao] resolve anime category failed: ' + e);
    }
}

// converUrl 是一段 JSON 字符串，形如 {"class":"动作,喜剧","area":"日本,大陆","year":"2024,2023",...}。
// 分隔符：线上实测各维度用「,」逗号分隔（如 class="情感,科幻,热血,…"），反编译 spec 记的是
// 「|」(字节124)——两者取其一即可能出现，这里同时兼容 , 与 |（谁都不命中时整串当单选项）。
function parseCategoryFilters(converUrl) {
    var cu = trim(converUrl);
    if (!cu) return null;
    var obj = parseJson(cu);
    if (!obj) return null;
    var filters = [];
    for (var i = 0; i < FILTER_KEYS.length; i++) {
        var k = FILTER_KEYS[i];
        var val = trim(obj[k]);
        if (!val) continue;
        var parts = val.split(/[|,]/);
        var options = [{ n: '全部', v: '' }];
        for (var j = 0; j < parts.length; j++) {
            var p = trim(parts[j]);
            if (p) options.push({ n: p, v: p });
        }
        if (options.length > 1) {
            filters.push({ key: k, name: FILTER_LABEL[k] || k, value: options });
        }
    }
    return filters.length ? filters : null;
}

// 是否「动漫分类」的 key：稳定 key「动漫」、空串（首页推荐）、或动态解析出的真 typeId 都算。
function isAnimeCatKey(key) {
    var k = trim(key);
    if (!k || k === ANIME_CAT_KEY) return true;
    return !!(ANIME_TYPE_ID && k === String(ANIME_TYPE_ID));
}

// 保留「推荐」首页 tab（key=""）+「动漫」tab（key=稳定值「动漫」，不用动态 typeId 当 key，
// 避免 getCategory 拉不到 typeId 时 tab 点不动）。筛选优先用 getCategory 拉到的，拉不到用兜底。
function categories() {
    resolveAnimeCategory();
    var filters = (ANIME_FILTERS && ANIME_FILTERS.length) ? ANIME_FILTERS : FALLBACK_FILTERS;
    return JSON.stringify([
        { key: '', title: '推荐' },
        { key: ANIME_CAT_KEY, title: ANIME_NAME || '动漫', filters: filters }
    ]);
}

// 分类浏览：POST /api/proto/v5/drama/category（typeId1=动漫 + 可选筛选）→ DramaBeanPage。
// filterMap 里的键是 getCategory 维度名（class/lang/area/year/extend_sort）。
function browseCategory(page, filterMap) {
    resolveHost();
    if (!HOST) return [];
    resolveAnimeCategory();
    ensureHandshake();
    var f = filterMap || {};
    var params = {
        pagesize: '21',
        typeId1: ANIME_TYPE_ID,   // 拉不到就空，服务端可能返回全站 → 下方兜一层动漫过滤
        page: String(page || 1),
        vodOrderBy: trim(f['extend_sort']) || '最新',
        vodArea: trim(f['area']),
        vodLang: trim(f['lang']),
        vodClass: trim(f['class']),
        vodYear: trim(f['year'])
    };
    var result = protoPost('/api/proto/v5/drama/category', params);
    var items = mapPage(result);
    // typeId1 已限定动漫；万一 typeId 未解析出来（空）则本地兜一层动漫过滤
    return ANIME_TYPE_ID ? items : filterAnime(items);
}

// 首页「精选」分区：多行横滑（最新/日本/国产动漫），全部限定动漫。key 用稳定「动漫」对齐 tab。
function homeSections() {
    resolveHost();
    if (!HOST) return JSON.stringify([]);
    var out = [];
    for (var i = 0; i < HOME_BUCKETS.length; i++) {
        var b = HOME_BUCKETS[i];
        var items = browseCategory(1, { area: b.area });
        if (items.length) out.push({ title: b.title, key: b.key, items: items.slice(0, 12) });
    }
    return JSON.stringify(out);
}

function search(keyword, page) {
    var key = trim(keyword);
    resolveHost();
    if (!HOST) return JSON.stringify([]);
    resolveAnimeCategory();
    // 空关键词 / 「动漫」tab key / 动态 typeId（首页 tab 无筛选浏览走 search(cat.key)）→ 分类浏览
    if (isAnimeCatKey(key)) {
        return JSON.stringify(browseCategory(page || 1, null));
    }
    // 真·关键词搜索：/drama/search 返回全站结果，客户端只留动漫
    ensureHandshake();
    var result = protoPost('/api/proto/v5/drama/search', {
        searchKeys: key,
        page: String(page || 1),
        pagesize: '21'
    });
    var items = filterAnime(mapPage(result));
    if (!items.length) items = filterAnime(fallbackSearch(key, page || 1));
    return JSON.stringify(items);
}

// 首页 tab 选了筛选 → searchFiltered(category, filtersJson, page)。category 是「动漫」稳定 key。
function searchFiltered(category, filtersJson, page) {
    resolveHost();
    if (!HOST) return JSON.stringify([]);
    resolveAnimeCategory();
    var f = parseJson(filtersJson) || {};
    return JSON.stringify(browseCategory(page || 1, f));
}

/* ───────────────────────────── 详情 / 取流 ───────────────────────────── */

// detail(id)：POST /api/proto/v5/drama/getDetail → DramaDetailBean。按线路(source_cn)给每集打 route。
function detail(id) {
    var vid = trim(id);
    var out = { id: vid, name: '', pic: '', desc: '', year: '', remarks: '', episodes: [] };
    resolveHost();
    if (!HOST) return JSON.stringify(out);
    ensureHandshake();
    var data = protoPost('/api/proto/v5/drama/getDetail', { id: vid });
    if (!data) return JSON.stringify(out);
    // DramaDetailBean 字段号：area=1 cover=2 id=4 intro=6 brief=7 name=9 director=12 tag=13
    //                        type=14 year=18 actor=25 remark=26 videos=29
    var d = decodeProto(data);
    out.name = protoString(d, 9);
    if (typeof d[2] === 'string') {
        var cover = decodeProto(d[2]);
        out.pic = protoString(cover, 2) || protoString(cover, 1);
    }
    var year = protoNumber(d, 18);
    out.year = year ? String(year) : '';
    out.remarks = protoString(d, 26);
    out.desc = protoString(d, 6) || protoString(d, 7);

    var videos = protoList(d, 29);
    for (var i = 0; i < videos.length; i++) {
        if (typeof videos[i] !== 'string') continue;
        // DramaVideoBean 字段号：title=2 path=4 source=9 source_cn=10
        var v = decodeProto(videos[i]);
        var title = protoString(v, 2);
        var path = protoString(v, 4);
        var source = protoString(v, 9);
        var sourceCn = protoString(v, 10) || '橘汁';
        if (!path) continue;
        var flag = path;
        // 非直链媒体后缀 → base64(JSON{vodPlayFrom,playUrl}) 作 play() 的 flag（同 AppDrama）
        if (!isMediaUrl(path)) {
            flag = crypto.base64.encode(JSON.stringify({ vodPlayFrom: source, playUrl: path }), { input: 'utf8' });
        }
        out.episodes.push({ name: title || ('第' + (i + 1) + '集'), url: flag, route: sourceCn });
    }
    return JSON.stringify(out);
}

// ParsePlayUrlBean.headers 是 proto map<string,string>（field 6，重复的 {1:key,2:value} 条目）。
function parseHeadersMap(bean) {
    var entries = protoList(bean, 6);
    var map = {};
    for (var i = 0; i < entries.length; i++) {
        if (typeof entries[i] !== 'string') continue;
        var e = decodeProto(entries[i]);
        var k = protoString(e, 1);
        var val = protoString(e, 2);
        if (k) map[k] = val;
    }
    return map;
}

// play(flag)：直链后缀直接返回；否则 base64→{vodPlayFrom,playUrl}→videoUsableUrl 取真实流 + 头。
function play(flag) {
    var f = String(flag == null ? '' : flag);
    if (isMediaUrl(f)) return JSON.stringify({ url: f, type: guessType(f) });

    var payload = null;
    try {
        payload = parseJson(crypto.base64.decode(f, { output: 'utf8' }));
    } catch (e) {
        payload = null;
    }
    if (!payload || !payload.playUrl) {
        // 兜底：解不出结构就当直链
        return JSON.stringify({ url: f, type: guessType(f) });
    }

    resolveHost();
    if (!HOST) return JSON.stringify({ url: '', type: 'auto' });
    ensureHandshake();
    var data = protoPost('/api/proto/v5/videoUsableUrl', {
        vodPlayFrom: payload.vodPlayFrom || '',
        playUrl: payload.playUrl
    });
    if (!data) return JSON.stringify({ url: '', type: 'auto' });
    // ParsePlayUrlBean：play_url=1, headers=6(map)
    var bean = decodeProto(data);
    var url = protoString(bean, 1);
    var res = { url: url, type: guessType(url) };
    var headers = parseHeadersMap(bean);
    var hasHeader = false, hj = {};
    for (var k in headers) {
        if (!headers.hasOwnProperty(k)) continue;
        hj[k] = headers[k];
        hasHeader = true;
        if (/^referer$/i.test(k)) res.referer = headers[k];
    }
    if (hasHeader) res.headers = JSON.stringify(hj);
    return JSON.stringify(res);
}