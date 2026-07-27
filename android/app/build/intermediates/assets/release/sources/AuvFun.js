/*
 * AES-128-ECB 加密 API + sign 鉴权 · 首页板块（homeSections）真还原（2026-07-01 Node 端到端验证）
 * version: 2.0.0
 *
 * 协议（GET，sign 鉴权，部分接口响应是 "base64"(AES-128-ECB 加密) 字符串）：
 *   - sign = base64url( MD5(time + path + apiSecret) )[:22]，另带 time=<秒+60>。UA=Dart。
 *   - 栏目  /app/tab/getList                         → data[]{id,title,sort}  4 个：推荐/日漫/国漫/4K
 *   - 板块  /app/video/getList?tabId=<栏目id>        → data[]{title,type,videoList[]}  ★首页板块结构
 *           （type=1 banner 轮播；type!=1 普通板块；每板块 5~7 部，不分页）
 *   - 搜索  /app/video/search?keyWord=&page=&size=   → data[]（真分页）
 *   - 详情  /app/video/getDetail?videoId=            → data{...,episodeList[]{id,title}}
 *   - 取流  /app/episode/jx?videoTitle=&episodeId=&deviceId= → data.resolutionList[]{name,url}+playHeader
 *           选集 flag = videoId@episodeId@base64url(videoTitle)，play() 拆开调取流接口。
 *
 *  封面 pic 大量是豆瓣图床(img*.doubanio.com)，带防盗链：无 Referer 必 418。
 *    已在 App 端 Coil 加载器统一为 doubanio/douban 主机补 Referer=https://movie.douban.com/（见 LanercApp）。
 *  play 取流接口 /episode/jx 服务端可能间歇性返回「正在维护」，非本源逻辑问题。
 */

var BASE       = 'http://85.209.230.191:8003';
var API_PREFIX = '/app';
var AES_KEY    = 'zhuhongleipeipei';     // 16 字节
var API_SECRET = "zhl's river app";       // 15 字节
var DEVICE_ID  = '4822e35123b5312b';
var UA_DART    = 'Dart/3.11 (dart:io)';
var CHROME_UA  = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36';

// 多 BASE 备用 (jinpai 风格), 漫闪当前只一个 IP, 留作未来扩展
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
    return [BASE];
})();

// /tab/getList 偶发失败时的兜底栏目（2026-07-01 实测 id），保证首页/分类不至于全空
var FALLBACK_TABS = [
    { id: '3740c6fc9f992bd660303d2a23f6ebb5', title: '推荐', sort: 1 },
    { id: 'd1832ba165d0538f8c72ea09e84fd413', title: '日漫', sort: 2 },
    { id: 'b7cbe964263375d9d825e452deb16a61', title: '国漫', sort: 3 },
    { id: '6ee3bcd148d1dcb98550d00b93232f24', title: '4K',   sort: 4 }
];

// ============================================================
// 工具函数 (与 jinpai 同名同语义)
// ============================================================
function trim(s)   { return s == null ? '' : String(s).replace(/^\s+|\s+$/g, ''); }
function rstrip(s) { return trim(s).replace(/\/+$/, ''); }
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
    if (u.indexOf('.mp4')  >= 0) return 'mp4';
    if (u.indexOf('.flv')  >= 0) return 'flv';
    return 'auto';
}
// 分辨率原始档位名 -> 前台友好名（供「清晰度切换」展示）
function resName(raw) {
    switch (String(raw == null ? '' : raw).toLowerCase()) {
        case '8k':     return '8K';
        case '4k':
        case 'uhd':    return '4K';
        case '2k':     return '2K';
        case 'super':  return '超清';
        case 'fullhd': return '1080P';
        case 'high':   return '高清';
        case 'normal': return '标清';
        case 'low':    return '流畅';
        default:       return raw ? String(raw) : '默认';
    }
}

// ============================================================
// 精简版 AES-128-ECB 解密 (仅 decrypt + PKCS7 + Base64)
// 移植自 aes-js, 已裁剪到仅含必需部分
// ============================================================
var AES_DEC = (function () {
    var SI = [
        0x52,0x09,0x6a,0xd5,0x30,0x36,0xa5,0x38,0xbf,0x40,0xa3,0x9e,0x81,0xf3,0xd7,0xfb,
        0x7c,0xe3,0x39,0x82,0x9b,0x2f,0xff,0x87,0x34,0x8e,0x43,0x44,0xc4,0xde,0xe9,0xcb,
        0x54,0x7b,0x94,0x32,0xa6,0xc2,0x23,0x3d,0xee,0x4c,0x95,0x0b,0x42,0xfa,0xc3,0x4e,
        0x08,0x2e,0xa1,0x66,0x28,0xd9,0x24,0xb2,0x76,0x5b,0xa2,0x49,0x6d,0x8b,0xd1,0x25,
        0x72,0xf8,0xf6,0x64,0x86,0x68,0x98,0x16,0xd4,0xa4,0x5c,0xcc,0x5d,0x65,0xb6,0x92,
        0x6c,0x70,0x48,0x50,0xfd,0xed,0xb9,0xda,0x5e,0x15,0x46,0x57,0xa7,0x8d,0x9d,0x84,
        0x90,0xd8,0xab,0x00,0x8c,0xbc,0xd3,0x0a,0xf7,0xe4,0x58,0x05,0xb8,0xb3,0x45,0x06,
        0xd0,0x2c,0x1e,0x8f,0xca,0x3f,0x0f,0x02,0xc1,0xaf,0xbd,0x03,0x01,0x13,0x8a,0x6b,
        0x3a,0x91,0x11,0x41,0x4f,0x67,0xdc,0xea,0x97,0xf2,0xcf,0xce,0xf0,0xb4,0xe6,0x73,
        0x96,0xac,0x74,0x22,0xe7,0xad,0x35,0x85,0xe2,0xf9,0x37,0xe8,0x1c,0x75,0xdf,0x6e,
        0x47,0xf1,0x1a,0x71,0x1d,0x29,0xc5,0x89,0x6f,0xb7,0x62,0x0e,0xaa,0x18,0xbe,0x1b,
        0xfc,0x56,0x3e,0x4b,0xc6,0xd2,0x79,0x20,0x9a,0xdb,0xc0,0xfe,0x78,0xcd,0x5a,0xf4,
        0x1f,0xdd,0xa8,0x33,0x88,0x07,0xc7,0x31,0xb1,0x12,0x10,0x59,0x27,0x80,0xec,0x5f,
        0x60,0x51,0x7f,0xa9,0x19,0xb5,0x4a,0x0d,0x2d,0xe5,0x7a,0x9f,0x93,0xc9,0x9c,0xef,
        0xa0,0xe0,0x3b,0x4d,0xae,0x2a,0xf5,0xb0,0xc8,0xeb,0xbb,0x3c,0x83,0x53,0x99,0x61,
        0x17,0x2b,0x04,0x7e,0xba,0x77,0xd6,0x26,0xe1,0x69,0x14,0x63,0x55,0x21,0x0c,0x7d
    ];
    var SBOX = [
        0x63,0x7c,0x77,0x7b,0xf2,0x6b,0x6f,0xc5,0x30,0x01,0x67,0x2b,0xfe,0xd7,0xab,0x76,
        0xca,0x82,0xc9,0x7d,0xfa,0x59,0x47,0xf0,0xad,0xd4,0xa2,0xaf,0x9c,0xa4,0x72,0xc0,
        0xb7,0xfd,0x93,0x26,0x36,0x3f,0xf7,0xcc,0x34,0xa5,0xe5,0xf1,0x71,0xd8,0x31,0x15,
        0x04,0xc7,0x23,0xc3,0x18,0x96,0x05,0x9a,0x07,0x12,0x80,0xe2,0xeb,0x27,0xb2,0x75,
        0x09,0x83,0x2c,0x1a,0x1b,0x6e,0x5a,0xa0,0x52,0x3b,0xd6,0xb3,0x29,0xe3,0x2f,0x84,
        0x53,0xd1,0x00,0xed,0x20,0xfc,0xb1,0x5b,0x6a,0xcb,0xbe,0x39,0x4a,0x4c,0x58,0xcf,
        0xd0,0xef,0xaa,0xfb,0x43,0x4d,0x33,0x85,0x45,0xf9,0x02,0x7f,0x50,0x3c,0x9f,0xa8,
        0x51,0xa3,0x40,0x8f,0x92,0x9d,0x38,0xf5,0xbc,0xb6,0xda,0x21,0x10,0xff,0xf3,0xd2,
        0xcd,0x0c,0x13,0xec,0x5f,0x97,0x44,0x17,0xc4,0xa7,0x7e,0x3d,0x64,0x5d,0x19,0x73,
        0x60,0x81,0x4f,0xdc,0x22,0x2a,0x90,0x88,0x46,0xee,0xb8,0x14,0xde,0x5e,0x0b,0xdb,
        0xe0,0x32,0x3a,0x0a,0x49,0x06,0x24,0x5c,0xc2,0xd3,0xac,0x62,0x91,0x95,0xe4,0x79,
        0xe7,0xc8,0x37,0x6d,0x8d,0xd5,0x4e,0xa9,0x6c,0x56,0xf4,0xea,0x65,0x7a,0xae,0x08,
        0xba,0x78,0x25,0x2e,0x1c,0xa6,0xb4,0xc6,0xe8,0xdd,0x74,0x1f,0x4b,0xbd,0x8b,0x8a,
        0x70,0x3e,0xb5,0x66,0x48,0x03,0xf6,0x0e,0x61,0x35,0x57,0xb9,0x86,0xc1,0x1d,0x9e,
        0xe1,0xf8,0x98,0x11,0x69,0xd9,0x8e,0x94,0x9b,0x1e,0x87,0xe9,0xce,0x55,0x28,0xdf,
        0x8c,0xa1,0x89,0x0d,0xbf,0xe6,0x42,0x68,0x41,0x99,0x2d,0x0f,0xb0,0x54,0xbb,0x16
    ];
    var RCON = [0x01,0x02,0x04,0x08,0x10,0x20,0x40,0x80,0x1b,0x36];

    function xtime(a) { return ((a << 1) ^ (((a >> 7) & 1) * 0x1b)) & 0xff; }

    // 16 字节 key -> 11 轮 round keys (44 个 32-bit word)
    function expandKey(key) {
        var Nk = 4, Nr = 10, Nb = 4;
        var w = new Array(Nb * (Nr + 1) * 4);
        for (var i = 0; i < Nk * 4; i++) w[i] = key[i];
        for (var i = Nk; i < Nb * (Nr + 1); i++) {
            var t = [w[(i - 1) * 4], w[(i - 1) * 4 + 1], w[(i - 1) * 4 + 2], w[(i - 1) * 4 + 3]];
            if (i % Nk === 0) {
                t = [SBOX[t[1]] ^ RCON[i / Nk - 1], SBOX[t[2]], SBOX[t[3]], SBOX[t[0]]];
            }
            for (var j = 0; j < 4; j++) w[i * 4 + j] = w[(i - Nk) * 4 + j] ^ t[j];
        }
        return w;
    }

    function decryptBlock(blk, rk) {
        var s = blk.slice();
        // initial AddRoundKey (last round key)
        for (var i = 0; i < 16; i++) s[i] ^= rk[160 + i];

        for (var r = 9; r >= 1; r--) {
            // InvShiftRows
            var t = [
                s[0], s[13], s[10], s[7],
                s[4], s[1],  s[14], s[11],
                s[8], s[5],  s[2],  s[15],
                s[12],s[9],  s[6],  s[3]
            ];
            // InvSubBytes
            for (var i = 0; i < 16; i++) s[i] = SI[t[i]];
            // AddRoundKey
            for (var i = 0; i < 16; i++) s[i] ^= rk[r * 16 + i];
            // InvMixColumns
            for (var c = 0; c < 4; c++) {
                var a = s[c * 4], b = s[c * 4 + 1], cc = s[c * 4 + 2], d = s[c * 4 + 3];
                var a2 = xtime(a), b2 = xtime(b), c2 = xtime(cc), d2 = xtime(d);
                var a4 = xtime(a2), b4 = xtime(b2), c4 = xtime(c2), d4 = xtime(d2);
                var a8 = xtime(a4), b8 = xtime(b4), c8 = xtime(c4), d8 = xtime(d4);
                var ae = a2 ^ a4 ^ a8, be = b2 ^ b4 ^ b8, ce = c2 ^ c4 ^ c8, de = d2 ^ d4 ^ d8;
                var ab = a8 ^ a2 ^ a,  bb = b8 ^ b2 ^ b,  cb = c8 ^ c2 ^ cc, db = d8 ^ d2 ^ d;
                var ad = a8 ^ a4 ^ a,  bd = b8 ^ b4 ^ b,  cd = c8 ^ c4 ^ cc, dd = d8 ^ d4 ^ d;
                var a9 = a8 ^ a,       b9 = b8 ^ b,       c9 = c8 ^ cc,      d9 = d8 ^ d;
                s[c * 4]     = (ae ^ bb ^ cd ^ d9) & 0xff;
                s[c * 4 + 1] = (a9 ^ be ^ cb ^ dd) & 0xff;
                s[c * 4 + 2] = (ad ^ b9 ^ ce ^ db) & 0xff;
                s[c * 4 + 3] = (ab ^ bd ^ c9 ^ de) & 0xff;
            }
        }
        // final round: InvShiftRows + InvSubBytes + AddRoundKey
        var t = [
            s[0], s[13], s[10], s[7],
            s[4], s[1],  s[14], s[11],
            s[8], s[5],  s[2],  s[15],
            s[12],s[9],  s[6],  s[3]
        ];
        for (var i = 0; i < 16; i++) s[i] = SI[t[i]] ^ rk[i];
        return s;
    }

    function str2bytes(s) {
        var b = []; for (var i = 0; i < s.length; i++) b.push(s.charCodeAt(i) & 0xff);
        return b;
    }
    function bytes2utf8(b, len) {
        var out = '', i = 0;
        while (i < len) {
            var c = b[i++];
            if (c < 0x80) { out += String.fromCharCode(c); }
            else if (c < 0xc0) { /* skip */ }
            else if (c < 0xe0) { out += String.fromCharCode(((c & 0x1f) << 6) | (b[i++] & 0x3f)); }
            else if (c < 0xf0) { out += String.fromCharCode(((c & 0x0f) << 12) | ((b[i++] & 0x3f) << 6) | (b[i++] & 0x3f)); }
            else {
                var cp = ((c & 0x07) << 18) | ((b[i++] & 0x3f) << 12) | ((b[i++] & 0x3f) << 6) | (b[i++] & 0x3f);
                cp -= 0x10000;
                out += String.fromCharCode(0xd800 + (cp >> 10), 0xdc00 + (cp & 0x3ff));
            }
        }
        return out;
    }
    function b64decode(s) {
        var alpha = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
        var lookup = {};
        for (var i = 0; i < 64; i++) lookup[alpha.charAt(i)] = i;
        s = String(s).replace(/[^A-Za-z0-9+/=]/g, '');
        var out = [];
        var L = s.length;
        for (var i = 0; i < L; i += 4) {
            var b1 = lookup[s.charAt(i)], b2 = lookup[s.charAt(i + 1)];
            var c3 = s.charAt(i + 2), c4 = s.charAt(i + 3);
            var b3 = (c3 === '=' || c3 === '') ? -1 : lookup[c3];
            var b4 = (c4 === '=' || c4 === '') ? -1 : lookup[c4];
            out.push(((b1 << 2) | (b2 >> 4)) & 0xff);
            if (b3 !== -1) out.push((((b2 & 0x0f) << 4) | (b3 >> 2)) & 0xff);
            if (b4 !== -1) out.push((((b3 & 0x03) << 6) | b4) & 0xff);
        }
        return out;
    }

    return {
        // 解密 base64 字符串 -> UTF-8 plaintext
        decryptBase64: function (b64, keyStr) {
            var key = str2bytes(keyStr || AES_KEY);
            var rk = expandKey(key);
            var cipher = b64decode(b64);
            if (cipher.length === 0 || cipher.length % 16 !== 0) {
                throw new Error('cipher length not multiple of 16: ' + cipher.length);
            }
            var out = [];
            for (var i = 0; i < cipher.length; i += 16) {
                var blk = decryptBlock(cipher.slice(i, i + 16), rk);
                for (var j = 0; j < 16; j++) out.push(blk[j]);
            }
            // PKCS7 unpad
            var pad = out[out.length - 1];
            if (pad < 1 || pad > 16) throw new Error('invalid PKCS7 pad: ' + pad);
            for (var k = out.length - pad; k < out.length; k++) {
                if (out[k] !== pad) throw new Error('PKCS7 pad mismatch');
            }
            return bytes2utf8(out, out.length - pad);
        }
    };
})();

// ============================================================
// 鉴权 + base64url
// ============================================================
function b64url(hexStr) {
    // 输入是 md5 hex (32 字符) -> 转 16 字节 -> base64url 不带 = -> 取前 22
    var bytes = '';
    for (var i = 0; i < hexStr.length; i += 2) {
        bytes += String.fromCharCode(parseInt(hexStr.substr(i, 2), 16));
    }
    var alpha = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
    var out = '', i, l = bytes.length;
    for (i = 0; i + 3 <= l; i += 3) {
        var n = (bytes.charCodeAt(i) << 16) | (bytes.charCodeAt(i + 1) << 8) | bytes.charCodeAt(i + 2);
        out += alpha[(n >> 18) & 63] + alpha[(n >> 12) & 63] + alpha[(n >> 6) & 63] + alpha[n & 63];
    }
    var rem = l - i;
    if (rem === 1) {
        var n = bytes.charCodeAt(i) << 16;
        out += alpha[(n >> 18) & 63] + alpha[(n >> 12) & 63];
    } else if (rem === 2) {
        var n = (bytes.charCodeAt(i) << 16) | (bytes.charCodeAt(i + 1) << 8);
        out += alpha[(n >> 18) & 63] + alpha[(n >> 12) & 63] + alpha[(n >> 6) & 63];
    }
    return out.substr(0, 22);
}

function sign(path, ts) {
    // sign = base64url( MD5(time + path + apiSecret) )[:22]
    return b64url(md5(String(ts) + path + API_SECRET));
}

function hdr() {
    return { 'User-Agent': UA_DART };
}

// ============================================================
// 请求 / 响应处理
// ============================================================
var RESOLVED = '';
function host() {
    if (RESOLVED) return RESOLVED;
    for (var i = 0; i < HOSTS.length; i++) {
        var h = rstrip(HOSTS[i]); if (!h) continue;
        var ts = nowSec() + 60;
        var path = API_PREFIX + '/init/getServerTime';
        var url  = h + path + '?sign=' + sign(path, ts) + '&time=' + ts;
        var body = request(url, JSON.stringify({ headers: hdr(), timeout: 8000 })) || '';
        if (body && (body.charAt(0) === '{' || body.charAt(0) === '"')) {
            RESOLVED = h; return h;
        }
    }
    RESOLVED = rstrip(HOSTS[0] || BASE);
    return RESOLVED;
}

function nowSec() {
    if (typeof timestamp === 'function') return Math.floor(timestamp() / 1000);
    if (typeof Date !== 'undefined')     return Math.floor(new Date().getTime() / 1000);
    return 0;
}

function buildQuery(q) {
    var keys = []; for (var k in q) if (q.hasOwnProperty(k)) keys.push(k);
    keys.sort();
    var parts = [];
    for (var i = 0; i < keys.length; i++) {
        var k = keys[i], v = q[k];
        if (v == null) continue;
        parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(String(v)));
    }
    return parts.join('&');
}

function callApi(path, query) {
    var ts = nowSec() + 60;
    var full = (path.indexOf('/app') === 0) ? path : (API_PREFIX + path);
    var qs = { };
    if (query) for (var k in query) if (query.hasOwnProperty(k) && query[k] != null) qs[k] = query[k];
    qs.sign = sign(full, ts);
    qs.time = ts;
    var url = host() + full + '?' + buildQuery(qs);
    var raw = request(url, JSON.stringify({ headers: hdr(), timeout: 15000 })) || '';
    return parseResp(raw);
}

function parseResp(raw) {
    var s = (raw == null ? '' : String(raw)).replace(/^\s+|\s+$/g, '');
    if (!s) return null;
    if (s.charAt(0) === '{' || s.charAt(0) === '[') {
        return parseJson(s);
    }
    if (s.charAt(0) === '"' && s.charAt(s.length - 1) === '"') {
        var b64 = s.substr(1, s.length - 2);
        try {
            var plain = AES_DEC.decryptBase64(b64, AES_KEY);
            if (plain.charAt(0) === '{' || plain.charAt(0) === '[') {
                return parseJson(plain);
            }
            return plain;
        } catch (e) {
            return { _decrypt_error: String(e), _raw: s.substr(0, 200) };
        }
    }
    return { _raw: s.substr(0, 200) };
}

// ============================================================
// 业务字段映射
// ============================================================
function mapVideoBrief(v) {
    if (!v) return null;
    var title = clean(v.title || v.douBanTitle || '');
    if (!title) return null;
    return {
        id:      String(v.id || ''),
        name:    title,
        pic:     trim(v.pic),                 // 多为豆瓣图床，App 端 Coil 已统一补防盗链 Referer
        type:    typeOf(v.area),
        year:    yearStr(v.year),
        remarks: clean(v.remarks || ''),
        desc:    clean(v.description || '')
    };
}

// videoList -> VideoItem[]（按 id 去重）
function mapList(arr) {
    var out = [], seen = {};
    if (!arr) return out;
    for (var i = 0; i < arr.length; i++) {
        var b = mapVideoBrief(arr[i]);
        if (!b || !b.id || seen[b.id]) continue;
        seen[b.id] = 1; out.push(b);
    }
    return out;
}

// banner / hero 横版位专用：优先用横图 thumb（豆瓣 l/m 原始比例，接近 16:9），
// 回退竖图 pic。漫闪官方首页轮播用的就是 thumb；竖版海报塞进横版位会被裁成中间一条。
function mapListBanner(arr) {
    var out = [], seen = {};
    if (!arr) return out;
    for (var i = 0; i < arr.length; i++) {
        var v = arr[i]; if (!v) continue;
        var b = mapVideoBrief(v);
        if (!b || !b.id || seen[b.id]) continue;
        var t = trim(v.thumb);
        if (t) b.pic = t;
        seen[b.id] = 1; out.push(b);
    }
    return out;
}

// ============================================================
// 栏目 / 列表
// ============================================================
var TABS_CACHE = null;
function fetchTabsCached() {
    if (TABS_CACHE) return TABS_CACHE;
    var tabs = [];
    try {
        var j = callApi('/tab/getList') || {};
        var arr = j.data || [];
        for (var i = 0; i < arr.length; i++) {
            var o = arr[i] || {};
            if (!o.id || !o.title) continue;
            tabs.push({ id: String(o.id), title: String(o.title), sort: o.sort || 0 });
        }
        tabs.sort(function (a, b) { return a.sort - b.sort; });
    } catch (e) {}
    TABS_CACHE = tabs.length ? tabs : FALLBACK_TABS;
    return TABS_CACHE;
}

// 「推荐」栏目 id（找不到取第一个）
function recommendId() {
    var tabs = fetchTabsCached();
    for (var i = 0; i < tabs.length; i++) if (tabs[i].title === '推荐') return tabs[i].id;
    return tabs.length ? tabs[0].id : '';
}

// 取某栏目 getList 的全部分区, 摊平成一维影片列表(去重)
function flattenTab(tabId) {
    if (!tabId) return [];
    var j = callApi('/video/getList', { tabId: tabId }) || {};
    var data = j.data || [];
    var out = [], seen = {};
    for (var i = 0; i < data.length; i++) {
        var vl = (data[i] || {}).videoList || [];
        for (var k = 0; k < vl.length; k++) {
            var b = mapVideoBrief(vl[k]);
            if (!b || !b.id || seen[b.id]) continue;
            seen[b.id] = 1; out.push(b);
        }
    }
    return out;
}

// 栏目内浏览：getList 不分页, 摊平后客户端切片(每页 20)
function listByTab(tabId, page) {
    var all = flattenTab(tabId);
    page = page || 1;
    var size = 20;
    return all.slice((page - 1) * size, page * size);
}

function searchByKeyword(kw, page) {
    var size = 20;
    var j = callApi('/video/search', { keyWord: kw, page: page || 1, size: size }) || {};
    return mapList(j.data || []);
}

// ============================================================
// 契约入口
// ============================================================

// 首页 tab：推荐(空 key 走 homeSections) + 各栏目(key=栏目 id)
function categories() {
    var tabs = fetchTabsCached();
    var cats = [{ key: '', title: '推荐' }];
    for (var i = 0; i < tabs.length; i++) {
        var t = tabs[i];
        if (!t.id || !t.title || t.title === '推荐') continue;
        cats.push({ key: t.id, title: t.title });
    }
    return JSON.stringify(cats);
}

// 首页板块：还原漫闪布局。
//  - hero 轮播 = banner 段前 5（横图 thumb，"轮播还是原来的"）
//  - 紧跟 hero 的横滑卡 = 「最近更新」（按用户要求把这条横滑位换成最近更新）
//  - 其余 = 豆瓣高分 / 记忆深刻 / 简单的快乐 等竖版网格
// 机制：HomeScreen.buildSectionedUi 把第一段「前 5 做 hero、其余做横滑」，
//      所以第一段 items 拼成 [banner 前5] + [最近更新]，title 用「最近更新」。
function homeSections() {
    var recId = recommendId();
    if (!recId) return '[]';
    var j = callApi('/video/getList', { tabId: recId }) || {};
    var data = j.data || [];

    var bannerItems = [], recentItems = [], rest = [];
    for (var i = 0; i < data.length; i++) {
        var sec = data[i] || {};
        var title = trim(sec.title);
        var isBanner = (sec.type === 1) || title.toLowerCase() === 'banner';
        if (isBanner && !bannerItems.length) {
            bannerItems = mapListBanner(sec.videoList);        // hero 横图
        } else if (title === '最近更新' && !recentItems.length) {
            recentItems = mapList(sec.videoList);              // 横滑卡（竖版海报）
        } else {
            rest.push({ title: title || '推荐', items: mapList(sec.videoList) });
        }
    }

    var out = [];
    // 第一段：hero(banner 前5) + 横滑(最近更新)，由 buildSectionedUi 自动拆分
    var first = bannerItems.slice(0, 5).concat(recentItems);
    if (first.length) out.push({ title: '最近更新', key: '', items: first });
    // banner 不足 5 张时兜底：直接用 banner 全部当第一段（避免 hero 卷进最近更新）
    for (var k = 0; k < rest.length; k++) {
        if (rest[k].items.length) out.push({ title: rest[k].title, key: '', items: rest[k].items.slice(0, 12) });
    }
    return JSON.stringify(out);
}

function search(keyword, page) {
    page = page || 1;
    var key = trim(keyword);
    if (!key) return JSON.stringify(listByTab(recommendId(), page));   // 精选页降级 / 推荐摊平
    if (/^[0-9a-f]{32}$/.test(key)) return JSON.stringify(listByTab(key, page)); // 栏目 tab
    return JSON.stringify(searchByKeyword(key, page));                  // 关键词搜索(真分页)
}

function searchFiltered(category, filtersJson, page) {
    var cat = trim(category);
    if (/^[0-9a-f]{32}$/.test(cat)) return JSON.stringify(listByTab(cat, page || 1));
    return search(cat, page);
}

// ============================================================
// 选集 flag 编解码（videoTitle 用 base64url 随 flag 透传给 play）
// ============================================================
function utf8ToB64url(s) {
    var bytes = [];
    for (var i = 0; i < s.length; i++) {
        var c = s.charCodeAt(i);
        if (c < 0x80) {
            bytes.push(c);
        } else if (c < 0x800) {
            bytes.push(0xc0 | (c >> 6), 0x80 | (c & 0x3f));
        } else if (c < 0xd800 || c >= 0xe000) {
            bytes.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 0x3f), 0x80 | (c & 0x3f));
        } else {
            i++;
            var cp = 0x10000 + (((c & 0x3ff) << 10) | (s.charCodeAt(i) & 0x3ff));
            bytes.push(0xf0 | (cp >> 18), 0x80 | ((cp >> 12) & 0x3f),
                       0x80 | ((cp >> 6) & 0x3f), 0x80 | (cp & 0x3f));
        }
    }
    var alpha = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
    var out = '', n, k;
    for (k = 0; k + 3 <= bytes.length; k += 3) {
        n = (bytes[k] << 16) | (bytes[k + 1] << 8) | bytes[k + 2];
        out += alpha[(n >> 18) & 63] + alpha[(n >> 12) & 63] + alpha[(n >> 6) & 63] + alpha[n & 63];
    }
    var rem = bytes.length - k;
    if (rem === 1) {
        n = bytes[k] << 16;
        out += alpha[(n >> 18) & 63] + alpha[(n >> 12) & 63];
    } else if (rem === 2) {
        n = (bytes[k] << 16) | (bytes[k + 1] << 8);
        out += alpha[(n >> 18) & 63] + alpha[(n >> 12) & 63] + alpha[(n >> 6) & 63];
    }
    return out;
}
function b64urlToUtf8(s) {
    var alpha = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
    var lookup = {};
    for (var i = 0; i < 64; i++) lookup[alpha.charAt(i)] = i;
    s = String(s).replace(/[^A-Za-z0-9_\-]/g, '');
    var bytes = [];
    for (var k = 0; k < s.length; k += 4) {
        var b1 = lookup[s.charAt(k)],     b2 = lookup[s.charAt(k + 1)];
        var c3 = s.charAt(k + 2),         c4 = s.charAt(k + 3);
        var b3 = c3 === '' ? -1 : lookup[c3];
        var b4 = c4 === '' ? -1 : lookup[c4];
        bytes.push(((b1 << 2) | (b2 >> 4)) & 0xff);
        if (b3 !== -1) bytes.push((((b2 & 0x0f) << 4) | (b3 >> 2)) & 0xff);
        if (b4 !== -1) bytes.push((((b3 & 0x03) << 6) | b4) & 0xff);
    }
    var out = '', i2 = 0;
    while (i2 < bytes.length) {
        var c = bytes[i2++];
        if (c < 0x80) { out += String.fromCharCode(c); }
        else if (c < 0xe0) { out += String.fromCharCode(((c & 0x1f) << 6) | (bytes[i2++] & 0x3f)); }
        else if (c < 0xf0) { out += String.fromCharCode(((c & 0x0f) << 12) | ((bytes[i2++] & 0x3f) << 6) | (bytes[i2++] & 0x3f)); }
        else {
            var cp = ((c & 0x07) << 18) | ((bytes[i2++] & 0x3f) << 12) | ((bytes[i2++] & 0x3f) << 6) | (bytes[i2++] & 0x3f);
            cp -= 0x10000;
            out += String.fromCharCode(0xd800 + (cp >> 10), 0xdc00 + (cp & 0x3ff));
        }
    }
    return out;
}

function detail(id) {
    var out = { id: String(id), name: '', pic: '', desc: '', type: '', remarks: '', year: '',
                actor: '', director: '', episodes: [] };
    var j = callApi('/video/getDetail', { videoId: id }) || {};
    var d = (j.data) || {};
    var b = mapVideoBrief(d) || {};
    out.name    = b.name    || '';
    out.pic     = b.pic     || '';
    out.desc    = b.desc    || clean(d.description || '');
    out.remarks = b.remarks || '';
    out.year    = b.year    || '';
    out.type    = b.type    || typeOf(d.area);
    out.actor   = clean(d.actor || '');
    out.director= clean(d.director || '');

    var title = out.name || clean(d.title || d.douBanTitle || '');
    var titleEnc = utf8ToB64url(title);

    var eps = d.episodeList || [];
    for (var i = 0; i < eps.length; i++) {
        var e = eps[i] || {};
        var eid = trim(e.id); if (!eid) continue;
        var name = clean(e.title || ('第' + (i + 1) + '集'));
        out.episodes.push({
            name:  name,
            url:   String(id) + '@' + eid + '@' + titleEnc,   // flag = videoId@episodeId@b64url(videoTitle)
            route: '在线播放'
        });
    }
    return JSON.stringify(out);
}

function play(flag) {
    var res = { url: '', type: 'auto' };
    var parts = String(flag || '').split('@');
    var vid   = trim(parts[0]);
    var eid   = trim(parts[1] || '');
    var title = parts[2] ? b64urlToUtf8(parts[2]) : '';
    if (!eid)   { res._note = 'missing episodeId in flag'; return JSON.stringify(res); }
    if (!title) { res._note = 'missing videoTitle in flag (re-open detail to re-encode)'; return JSON.stringify(res); }

    // /app/episode/jx?videoTitle=&episodeId=&deviceId=  ← 真实必填
    var j = callApi('/episode/jx', {
        videoTitle: title,
        episodeId:  eid,
        deviceId:   DEVICE_ID
    }) || {};

    var d = (j && j.data) || null;
    if (!d || j.code !== 200) {
        res._server_msg  = j && j.message ? String(j.message) : 'no data';
        res._server_code = j && (j.code != null) ? j.code : -1;
        return JSON.stringify(res);
    }

    // 选最佳分辨率：4k 优先。漫闪「4K」栏目视频的 resolutionList 含 name="4k" 的超清直链，
    // 旧 order 漏了它 → 退而选 super(≈1080p)，导致「官方有 4K、这里却不是 4K」。
    // 2026-07-01 Node 实测某 4K 视频 resolutionList = [4k, super, high, low]；uhd/8k/2k 为防御别名。
    var rs = d.resolutionList || [];
    var pick = null;
    var order = ['8k', '4k', 'uhd', '2k', 'super', 'fullHd', 'high', 'normal', 'low'];
    for (var oi = 0; oi < order.length && !pick; oi++) {
        for (var ri = 0; ri < rs.length; ri++) {
            if (rs[ri] && rs[ri].name === order[oi] && rs[ri].url) { pick = rs[ri]; break; }
        }
    }
    if (!pick && rs.length) pick = rs[0];
    var u = pick ? trim(pick.url || '') : '';
    if (!u) {
        res._server_msg = 'resolutionList empty';
        return JSON.stringify(res);
    }

    res.url  = u;
    res.type = guessType(u);

    // 用 jx 接口返回的 playHeader 作为播放请求头 (Cookie/Referer/UserAgent)
    var ph = d.playHeader || {};
    var hdrs = {
        'User-Agent': ph.UserAgent || ph['User-Agent'] || CHROME_UA,
        'Referer':    ph.Referer   || 'https://pan.quark.cn/',
        'Origin':     (ph.Referer ? ph.Referer.replace(/\/$/, '') : 'https://pan.quark.cn')
    };
    if (ph.Cookie) hdrs.Cookie = ph.Cookie;
    res.referer = hdrs.Referer;
    res.headers = JSON.stringify(hdrs);

    // 全部档位 → 前台「清晰度切换」列表（保留 API 顺序：4k/super/high/low 即高→低；去重）
    var resolutions = [], rseen = {};
    for (var qi = 0; qi < rs.length; qi++) {
        var q = rs[qi];
        if (!q || !q.url) continue;
        var qu = trim(q.url);
        if (!qu || rseen[qu]) continue;
        rseen[qu] = 1;
        resolutions.push({ name: resName(q.name), url: qu, type: guessType(qu) });
    }
    res.resolutions = resolutions;
    return JSON.stringify(res);
}

// ============================================================
// CommonJS / Node 测试导出 (App 不会执行这一块)
// ============================================================
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        categories: categories,
        homeSections: homeSections,
        search: search,
        searchFiltered: searchFiltered,
        detail: detail,
        play: play,
        _internal: {
            sign: sign,
            b64url: b64url,
            buildQuery: buildQuery,
            parseResp: parseResp,
            callApi: callApi,
            host: host,
            AES_DEC: AES_DEC,
            fetchTabsCached: fetchTabsCached,
            recommendId: recommendId,
            flattenTab: flattenTab,
            listByTab: listByTab,
            searchByKeyword: searchByKeyword
        }
    };
}
