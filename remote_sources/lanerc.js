 
























var _LANERC_DISCOVERY = 'https://anime999x-1366475786.cos.ap-guangzhou.myqcloud.com/apis.json';
var _LANERC_FALLBACK_HOST = 'http://lol.jngaoke.cn/';
 
var _LANERC_PROBE_TIMEOUT_MS = 3000;
 
var _LANERC_WARNING_PLAYLIST_TIMEOUT_MS = 3000;
var _LANERC_BLOCK_WARNING_PLAYLIST = true;
var _LANERC_STALE_HOST = 'https://server.jngaoke.cn/';
var _LANERC_AUTH_FALLBACK = 'com.clggjv.xcjfmd.ffo';
var _LANERC_DECRYPT_KEY = '8f81c2519e3b661834219e7142000093';
 
var _LANERC_BUILD_SIGNATURE = '74322D4D62B9F4A986DFA8973EE70EBC034E74551B8715C755EDD9ED18E6820B';
 
var _LANERC_QUERY_SIGN_SECRET = '7d3cb4d6e7fbc7c9';


var _LANERC_API_UA = 'Dart/3.5 (dart:io)';
var _lanercExt = typeof ext === 'object' && ext ? ext : {};
var _lanercHost = '';
var _lanercHome = null;
var _lanercRuntime = null;


var _lanercLastDetail = null;



function _legacyTrim(value) {
    return value === null || value === undefined ? '' : String(value).replace(/^\s+|\s+$/g, '');
}



function _legacyIsArray(value) {
    return Object.prototype.toString.call(value) === '[object Array]';
}



function _legacyOwn(object, key) {
    return object !== null && object !== undefined &&
        Object.prototype.hasOwnProperty.call(object, key);
}



function _lanercLog(message) {
    try {
        log('[Lanerc旧版源] ' + message);
    } catch (error) {
        
    }
}



function _normalizeHost(host) {
    var value = _legacyTrim(host);
    if (!value) return '';
    return value.replace(/\/+$/, '') + '/';
}



function _isStaleLanercHost(host) {
    return _normalizeHost(host).toLowerCase() === _LANERC_STALE_HOST;
}



function _safeParse(value, fallback) {
    if (value === null || value === undefined || value === '') return fallback;
    if (typeof value === 'object') return value;
    try {
        var parsed = parseJson(String(value));
        return parsed === null || parsed === undefined ? fallback : parsed;
    } catch (error) {
        return fallback;
    }
}



function _decryptOptions() {
    var config = _lanercExt.decrypt;
    if (typeof config === 'string') config = _safeParse(config, {});
    if (!config || typeof config !== 'object') return {};
    return config;
}



var _LANERC_AES_FALLBACK = (function () {
    var inverseSbox = [
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
    var sbox = [
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
    var roundConstants = [0x01,0x02,0x04,0x08,0x10,0x20,0x40,0x80,0x1b,0x36];

     
    function _aesXtime(value) {
        return ((value << 1) ^ (((value >> 7) & 1) * 0x1b)) & 0xff;
    }

     
    function _aesExpandKey(key) {
        var keyWords = 8;
        var rounds = 14;
        var totalWords = 4 * (rounds + 1);
        var expanded = new Array(totalWords * 4);
        var index;
        for (index = 0; index < keyWords * 4; index += 1) expanded[index] = key[index];
        for (var word = keyWords; word < totalWords; word += 1) {
            var previous = (word - 1) * 4;
            var value = [expanded[previous], expanded[previous + 1], expanded[previous + 2], expanded[previous + 3]];
            if (word % keyWords === 0) {
                value = [
                    sbox[value[1]] ^ roundConstants[word / keyWords - 1],
                    sbox[value[2]],
                    sbox[value[3]],
                    sbox[value[0]]
                ];
            } else if (word % keyWords === 4) {
                value = [sbox[value[0]], sbox[value[1]], sbox[value[2]], sbox[value[3]]];
            }
            for (index = 0; index < 4; index += 1) {
                expanded[word * 4 + index] = expanded[(word - keyWords) * 4 + index] ^ value[index];
            }
        }
        return expanded;
    }

     
    function _aesInverseShiftAndSubstitute(state) {
        var shifted = [
            state[0], state[13], state[10], state[7],
            state[4], state[1], state[14], state[11],
            state[8], state[5], state[2], state[15],
            state[12], state[9], state[6], state[3]
        ];
        for (var index = 0; index < 16; index += 1) shifted[index] = inverseSbox[shifted[index]];
        return shifted;
    }

     
    function _aesInverseMixColumns(state) {
        for (var column = 0; column < 4; column += 1) {
            var offset = column * 4;
            var a = state[offset];
            var b = state[offset + 1];
            var c = state[offset + 2];
            var d = state[offset + 3];
            var a2 = _aesXtime(a), b2 = _aesXtime(b), c2 = _aesXtime(c), d2 = _aesXtime(d);
            var a4 = _aesXtime(a2), b4 = _aesXtime(b2), c4 = _aesXtime(c2), d4 = _aesXtime(d2);
            var a8 = _aesXtime(a4), b8 = _aesXtime(b4), c8 = _aesXtime(c4), d8 = _aesXtime(d4);
            var a14 = a2 ^ a4 ^ a8, b14 = b2 ^ b4 ^ b8, c14 = c2 ^ c4 ^ c8, d14 = d2 ^ d4 ^ d8;
            var a11 = a8 ^ a2 ^ a, b11 = b8 ^ b2 ^ b, c11 = c8 ^ c2 ^ c, d11 = d8 ^ d2 ^ d;
            var a13 = a8 ^ a4 ^ a, b13 = b8 ^ b4 ^ b, c13 = c8 ^ c4 ^ c, d13 = d8 ^ d4 ^ d;
            var a9 = a8 ^ a, b9 = b8 ^ b, c9 = c8 ^ c, d9 = d8 ^ d;
            state[offset] = (a14 ^ b11 ^ c13 ^ d9) & 0xff;
            state[offset + 1] = (a9 ^ b14 ^ c11 ^ d13) & 0xff;
            state[offset + 2] = (a13 ^ b9 ^ c14 ^ d11) & 0xff;
            state[offset + 3] = (a11 ^ b13 ^ c9 ^ d14) & 0xff;
        }
    }

     
    function _aesDecryptBlock(block, expanded) {
        var rounds = 14;
        var state = block.slice();
        var index;
        for (index = 0; index < 16; index += 1) state[index] ^= expanded[rounds * 16 + index];
        for (var round = rounds - 1; round >= 1; round -= 1) {
            state = _aesInverseShiftAndSubstitute(state);
            for (index = 0; index < 16; index += 1) state[index] ^= expanded[round * 16 + index];
            _aesInverseMixColumns(state);
        }
        state = _aesInverseShiftAndSubstitute(state);
        for (index = 0; index < 16; index += 1) state[index] ^= expanded[index];
        return state;
    }

     
    function _aesTextBytes(value) {
        var result = [];
        for (var index = 0; index < value.length; index += 1) result.push(value.charCodeAt(index) & 0xff);
        return result;
    }

     
    function _aesBase64Bytes(value) {
        var alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
        var lookup = {};
        var output = [];
        var index;
        for (index = 0; index < 64; index += 1) lookup[alphabet.charAt(index)] = index;
        var input = String(value || '').replace(/[^A-Za-z0-9+/=]/g, '');
        for (index = 0; index < input.length; index += 4) {
            var first = lookup[input.charAt(index)];
            var second = lookup[input.charAt(index + 1)];
            var thirdChar = input.charAt(index + 2);
            var fourthChar = input.charAt(index + 3);
            var third = thirdChar === '=' || thirdChar === '' ? -1 : lookup[thirdChar];
            var fourth = fourthChar === '=' || fourthChar === '' ? -1 : lookup[fourthChar];
            output.push(((first << 2) | (second >> 4)) & 0xff);
            if (third !== -1) output.push((((second & 0x0f) << 4) | (third >> 2)) & 0xff);
            if (fourth !== -1) output.push((((third & 0x03) << 6) | fourth) & 0xff);
        }
        return output;
    }

     
    function _aesUtf8Text(bytes, length) {
        var output = '';
        var index = 0;
        while (index < length) {
            var code = bytes[index++];
            if (code < 0x80) {
                output += String.fromCharCode(code);
            } else if (code < 0xe0) {
                output += String.fromCharCode(((code & 0x1f) << 6) | (bytes[index++] & 0x3f));
            } else if (code < 0xf0) {
                output += String.fromCharCode(
                    ((code & 0x0f) << 12) | ((bytes[index++] & 0x3f) << 6) | (bytes[index++] & 0x3f)
                );
            } else {
                var point = ((code & 0x07) << 18) | ((bytes[index++] & 0x3f) << 12) |
                    ((bytes[index++] & 0x3f) << 6) | (bytes[index++] & 0x3f);
                point -= 0x10000;
                output += String.fromCharCode(0xd800 + (point >> 10), 0xdc00 + (point & 0x3ff));
            }
        }
        return output;
    }

    return {
         
        decryptBase64: function (base64, keyText) {
            var key = _aesTextBytes(String(keyText || ''));
            if (key.length !== 32) throw new Error('内置 AES-256 要求 32 字节密钥');
            var cipher = _aesBase64Bytes(base64);
            if (!cipher.length || cipher.length % 16 !== 0) throw new Error('AES 密文长度不是 16 的倍数');
            var expanded = _aesExpandKey(key);
            var plain = [];
            for (var offset = 0; offset < cipher.length; offset += 16) {
                var block = _aesDecryptBlock(cipher.slice(offset, offset + 16), expanded);
                for (var index = 0; index < 16; index += 1) plain.push(block[index]);
            }
            var padding = plain[plain.length - 1];
            if (padding < 1 || padding > 16) throw new Error('AES PKCS7 padding 无效');
            for (var padIndex = plain.length - padding; padIndex < plain.length; padIndex += 1) {
                if (plain[padIndex] !== padding) throw new Error('AES PKCS7 padding 不一致');
            }
            return _aesUtf8Text(plain, plain.length - padding);
        }
    };
})();



function _restoreLanercAlphabet(ciphertext) {
    return String(ciphertext || '')
        .replace(/1/g, '!')
        .replace(/5/g, '@')
        .replace(/9/g, '#')
        .replace(/\//g, '*')
        .replace(/-/g, '&')
        .replace(/!/g, '9')
        .replace(/@/g, '1')
        .replace(/#/g, '5')
        .replace(/\*/g, '+')
        .replace(/&/g, '/');
}



function _decryptApiData(ciphertext) {
    var config = _decryptOptions();
    var key = _firstValue(config, ['key']) || _LANERC_DECRYPT_KEY;
    try {
        var input = String(ciphertext || '');
        var inputFormat = String(config.input || 'base64');
        if (inputFormat === 'base64') {
            input = _restoreLanercAlphabet(input);
            while (input.length % 4) input += '=';
        }
        var options = {
            mode: String(config.mode || 'ECB'),
            padding: String(config.padding || 'PKCS5'),
            input: inputFormat,
            output: String(config.output || 'utf8')
        };
        if (config.iv !== null && config.iv !== undefined && config.iv !== '') {
            options.iv = String(config.iv);
        }
        if (config.keyFormat) options.keyFormat = String(config.keyFormat);
        if (config.ivFormat) options.ivFormat = String(config.ivFormat);
        var plain;
        if (typeof crypto !== 'undefined' && crypto.aes && typeof crypto.aes.decrypt === 'function') {
            plain = crypto.aes.decrypt(input, String(key), options);
        } else {
            if (options.mode !== 'ECB' || options.input !== 'base64' || options.output !== 'utf8') {
                throw new Error('内置 AES 仅支持 ECB、Base64 输入和 UTF-8 输出');
            }
            plain = _LANERC_AES_FALLBACK.decryptBase64(input, String(key));
        }
        var parsed = _safeParse(plain, null);
        if (!parsed || typeof parsed !== 'object') {
            _lanercLog('接口解密结果不是 JSON 对象');
            return null;
        }
        return parsed;
    } catch (error) {
        _lanercLog('接口 AES 解密失败：' + String(error));
        return null;
    }
}



function _decodeApiResponse(value) {
    var response = value;
    if (!response || typeof response !== 'object' || _legacyIsArray(response)) return response;
    if (Number(response.code) === 201 && typeof response.data === 'string') {
        var decrypted = _decryptApiData(response.data);
        return decrypted || {};
    }
    return response;
}



function _lanercApiUserAgent() {
    var candidate = _legacyTrim(_lanercExt.userAgent);
    return /^Dart\//i.test(candidate) ? candidate : _LANERC_API_UA;
}



function _requestOptions(isPost, timeoutMs) {
    var headers = { Accept: 'application/json' };
    headers['User-Agent'] = _lanercApiUserAgent();
    if (isPost) headers['Content-Type'] = 'application/json';
    var options = { headers: headers };
    var timeout = Number(timeoutMs || _lanercExt.timeout || 0);
    if (timeout > 0 && isFinite(timeout)) options.timeout = timeout;
    return JSON.stringify(options);
}



function _requestJson(url, timeoutMs) {
    try {
        return _decodeApiResponse(_safeParse(request(url, _requestOptions(false, timeoutMs)), {}));
    } catch (error) {
        _lanercLog('GET失败：' + url + '；' + String(error));
        return {};
    }
}



function _postJson(url, body) {
    try {
        return _decodeApiResponse(_safeParse(post(url, JSON.stringify(body || {}), _requestOptions(true)), {}));
    } catch (error) {
        _lanercLog('POST失败：' + url + '；' + String(error));
        return {};
    }
}



function _findDeep(value, key, depth) {
    var level = depth || 0;
    if (!value || typeof value !== 'object' || level > 12) return '';
    if (_legacyOwn(value, key)) return value[key];
    for (var name in value) {
        if (!_legacyOwn(value, name)) continue;
        var found = _findDeep(value[name], key, level + 1);
        if (found !== '' && found !== null && found !== undefined) return found;
    }
    return '';
}



/*
 * 播放接口在不同版本里出现过 play_url / playUrl / url 三种字段名，
 * 播放请求头也有 playHeader / play_header / headers 三种写法。不要把
 * 这些兼容逻辑塞进 play()，否则接口升级时很容易又把结果解析丢掉。
 */
function _findDeepAny(value, keys) {
    var names = _legacyIsArray(keys) ? keys : [];
    for (var index = 0; index < names.length; index += 1) {
        var found = _findDeep(value, String(names[index] || ''));
        if (found !== '' && found !== null && found !== undefined) return found;
    }
    return '';
}



function _normalizePlayText(value) {
    var text = _legacyTrim(value);
    if (!text) return '';
    /* JSON 接口常把 URL 写成 https:\/\/... 或 HTML 实体。 */
    text = text
        .replace(/\\\//g, '/')
        .replace(/\\u0026/gi, '&')
        .replace(/&amp;/gi, '&');
    if (/^https?%3a%2f%2f/i.test(text)) {
        try { text = decodeUri(text); } catch (error) { }
    }
    if (/^\/\//.test(text)) text = 'https:' + text;
    return text;
}



function _playUrlFromResponse(response) {
    var source = response;
    if (typeof source === 'string') {
        var parsedSource = _safeParse(source, source);
        if (parsedSource === source) return _normalizePlayText(source);
        source = parsedSource;
    }
    /* 某些旧节点把 data 再序列化了一层：{data:"{\\"play_url\\":...}"}。 */
    for (var depth = 0; depth < 3 && source && typeof source === 'object'; depth += 1) {
        if (typeof source.data !== 'string') break;
        var decoded = _safeParse(source.data, null);
        if (!decoded || typeof decoded !== 'object') break;
        source = decoded;
    }
    var value = _findDeepAny(source, [
        'play_url', 'playUrl', 'playurl', 'video_url', 'videoUrl',
        'm3u8_url', 'm3u8Url', 'url', 'src'
    ]);
    if (value && typeof value === 'object') {
        value = _findDeepAny(value, ['url', 'play_url', 'playUrl', 'src', 'value']);
    }
    return _normalizePlayText(value);
}



function _playHeadersFromResponse(response) {
    var source = response;
    if (typeof source === 'string') source = _safeParse(source, source);
    for (var depth = 0; depth < 3 && source && typeof source === 'object'; depth += 1) {
        if (typeof source.data !== 'string') break;
        var decoded = _safeParse(source.data, null);
        if (!decoded || typeof decoded !== 'object') break;
        source = decoded;
    }
    var value = _findDeepAny(source, [
        'play_header', 'playHeader', 'play_headers', 'playHeaders',
        'http_headers', 'httpHeaders', 'headers'
    ]);
    if (typeof value === 'string') value = _safeParse(value, null);
    if (!value || typeof value !== 'object' || _legacyIsArray(value)) return {};
    var result = {};
    for (var key in value) {
        if (_legacyOwn(value, key) && value[key] !== null && value[key] !== undefined) {
            result[String(key)] = String(value[key]);
        }
    }
    return result;
}



function _playResult(response, playUrl) {
    var result = { url: playUrl, type: _mediaType(playUrl) };
    var headers = _playHeadersFromResponse(response);
    var source = response;
    if (typeof source === 'string') source = _safeParse(source, source);
    if (result.type === 'auto') {
        var responseType = String(_findDeepAny(source, ['type', 'format', 'mime', 'mime_type']) || '').toLowerCase();
        if (responseType.indexOf('m3u8') !== -1 || responseType.indexOf('hls') !== -1) result.type = 'm3u8';
        else if (responseType.indexOf('mp4') !== -1) result.type = 'mp4';
    }
    var referer = _normalizePlayText(_findDeepAny(source, ['referer', 'referrer']));
    var userAgent = _normalizePlayText(_findDeepAny(source, ['user_agent', 'userAgent', 'ua']));

    /* file.jngaoke.cn 的取流地址需要从站点页带 Referer；如果接口没有
       下发头，给一个与请求端一致的最小默认值，避免播放器二次请求被 403。 */
    if (!Object.keys(headers).length && /^https?:\/\/file\.jngaoke\.cn\//i.test(playUrl)) {
        headers.Referer = _resolveHost();
        headers['User-Agent'] = _lanercApiUserAgent();
    }
    if (Object.keys(headers).length) result.headers = headers;
    if (referer) result.referer = referer;
    if (userAgent) result.userAgent = userAgent;
    return result;
}



function _upgradeHost(host) {
    return host ? String(host).replace(/^http:\/\//i, 'https://') : host;
}



function _resolveHost() {
    if (_lanercHost) return _lanercHost;
    _lanercHost = _upgradeHost(_normalizeHost(_lanercExt.host));
    if (_lanercHost) return _lanercHost;

    var fallbackProbe = _payload(_requestJson(_upgradeHost(_LANERC_FALLBACK_HOST) + 'app/home', _LANERC_PROBE_TIMEOUT_MS));
    if (fallbackProbe && typeof fallbackProbe === 'object' &&
        (_legacyOwn(fallbackProbe, 'vod_list') || _legacyOwn(fallbackProbe, 'banner') || _legacyOwn(fallbackProbe, 'hot_list'))) {
        _lanercHome = fallbackProbe;
        _lanercHost = _upgradeHost(_LANERC_FALLBACK_HOST);
        return _lanercHost;
    }

    _lanercLog('静态回退站探测失败，尝试在线域名发现');
    var configUrl = String(_lanercExt.configUrl || _LANERC_DISCOVERY);
    var discovery = _requestJson(configUrl, _LANERC_PROBE_TIMEOUT_MS);
    var discoveredHost = _upgradeHost(_normalizeHost(_findDeep(discovery, 'domain')));
    if (_isStaleLanercHost(discoveredHost)) {
        _lanercLog('在线配置仍为证书过期旧站点，改用静态回退地址');
        discoveredHost = '';
    }
    _lanercHost = discoveredHost;
    if (!_lanercHost) {
        _lanercLog('域名发现失败，使用静态回退地址');
        _lanercHost = _upgradeHost(_LANERC_FALLBACK_HOST);
    }
    return _lanercHost;
}



function _apiGet(path) {
    return _requestJson(_resolveHost() + String(path || '').replace(/^\/+/, ''));
}



function _lanercSignedApiPath(path, seconds, nonce) {
    var cleanPath = String(path || '').replace(/^\/+/, '');
    var timeValue = seconds === null || seconds === undefined
        ? Math.floor(Number(timestamp()) / 1000)
        : Math.floor(Number(seconds));
    var randomValue = nonce === null || nonce === undefined ? '' : String(nonce);
    var alphabet = 'abcdefghijklmnopqrstuvwxyz0123456789';
    while (randomValue.length < 6) {
        randomValue += alphabet.charAt(Math.floor(Math.random() * alphabet.length));
    }
    randomValue = randomValue.slice(0, 6);
    var digest = md5('/' + cleanPath + '@' + timeValue + '@' + randomValue + '@' + _LANERC_QUERY_SIGN_SECRET);
    return cleanPath + '?sign=' + timeValue + '-' + randomValue + '-' + String(digest).toLowerCase();
}



function _apiPost(path, body) {
    return _postJson(_resolveHost() + String(path || '').replace(/^\/+/, ''), body);
}



function _payload(value) {
    var current = value;
    var count = 0;
    while (
        current &&
        typeof current === 'object' &&
        !_legacyIsArray(current) &&
        _legacyOwn(current, 'data') &&
        current.data !== null &&
        current.data !== undefined &&
        count < 4
    ) {
        current = current.data;
        count += 1;
    }
    return current || {};
}


var _LANERC_PIC_UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36';



function _lanercCoverPic(pic) {
    var url = String(pic || '');
    if (!url) return '';
    if (url.indexOf('@Referer=') !== -1 || url.indexOf('@User-Agent=') !== -1 || url.indexOf('@Headers=') !== -1) return url;
    if (url.indexOf('doubanio.com') === -1) return url;
    return url + '@Referer=https://movie.douban.com/@User-Agent=' + _LANERC_PIC_UA;
}



function _firstValue(object, keys) {
    var source = object || {};
    for (var index = 0; index < keys.length; index += 1) {
        var value = source[keys[index]];
        if (value !== null && value !== undefined && value !== '') return value;
    }
    return '';
}



function _cardRemarks(source) {
    var text = _legacyTrim(String(_firstValue(source, ['vod_remarks', 'vod_sub', 'vod_tag']) || ''));
    var flag = text === '0' || text === '1' ? text : String(_firstValue(source, ['vod_isend']));
    if (text === '' || text === '0' || text === '1') {
        if (flag === '1') return '已完结';
        if (flag === '0') return '连载中';
        return '';
    }
    return text;
}

 
function _sortName(value) {
    var key = String(value === null || value === undefined ? '' : value);
    if (!key) return '';
    var home = _getHome();
    var groups = _legacyIsArray(home.vod_list) ? home.vod_list : [];
    for (var index = 0; index < groups.length; index += 1) {
        var group = groups[index] || {};
        if (String(group.sort_id) === key) return String(group.sort_name || '');
    }
    return '';
}



function _typeText(source) {
    var value = _firstValue(source || {}, ['vod_class', 'vod_type']);
    var text = _legacyTrim(String(value === null || value === undefined ? '' : value));
    if (!text) return '';
    if (/^\d+$/.test(text)) return _sortName(text);
    return text;
}



function _card(item, sectionTitle) {
    var source = item || {};
    var id = _firstValue(source, ['id', 'vod_id']);
    var name = _firstValue(source, ['vod_name', 'name', 'title']);
    if (id === '' || name === '') return null;
    return {
        id: String(id),
        name: String(name),
        pic: _lanercCoverPic(_firstValue(source, ['vod_pic', 'pic', 'image', 'cover'])),
        type: String(sectionTitle || '') || _typeText(source),
        year: String(_firstValue(source, ['vod_year', 'year']) || ''),
        remarks: _cardRemarks(source),
        desc: String(_firstValue(source, ['vod_blurb', 'desc']) || '')
    };
}



function _cards(items, sectionTitle) {
    var list = _legacyIsArray(items) ? items : [];
    var result = [];
    for (var index = 0; index < list.length; index += 1) {
        var item = _card(list[index], sectionTitle);
        if (item) result.push(item);
    }
    return result;
}



function _optionText(value) {
    if (value === null || value === undefined) return '';
    if (typeof value === 'object') {
        return _legacyTrim(_firstValue(value, ['n', 'name', 'title', 'v', 'value']) || '');
    }
    return _legacyTrim(value);
}



function _options(value) {
    var source = value;
    if (typeof source === 'string') {
        var textValue = _legacyTrim(source);
        var parsed = _safeParse(textValue, null);
        source = _legacyIsArray(parsed) ? parsed : (textValue ? textValue.split(/[,/]/) : []);
    }
    if (!_legacyIsArray(source)) source = source === null || source === undefined || source === '' ? [] : [source];

    var result = ['全部'];
    var seen = { '全部': true };
    for (var index = 0; index < source.length; index += 1) {
        var option = _optionText(source[index]);
        if (!option || seen[option]) continue;
        seen[option] = true;
        result.push(option);
    }
    return result;
}



function _filterOptions(value) {
    var options = _options(value);
    var result = [];
    for (var index = 0; index < options.length; index += 1) {
        var name = String(options[index]);
        result.push({ n: name, v: name === '全部' ? '' : name });
    }
    return result;
}



function _getHome() {
    if (_lanercHome !== null) return _lanercHome;
    _lanercHome = _payload(_apiGet('app/home'));
    if (!_lanercHome || typeof _lanercHome !== 'object') _lanercHome = {};
    return _lanercHome;
}



function _buildHomeSections() {
    var home = _getHome();
    var sections = [];
    var banner = _cards(home.banner, '推荐');
    var hot = _cards(home.hot_list, '热门');
    if (banner.length) sections.push({ title: '轮播', key: '__hero__', items: banner });
    if (hot.length) sections.push({ title: '热门', key: '', items: hot });

    var vodList = _legacyIsArray(home.vod_list) ? home.vod_list : [];
    for (var index = 0; index < vodList.length; index += 1) {
        var group = vodList[index] || {};
        var title = String(group.sort_name || '分类');
        var key = String(group.sort_id === null || group.sort_id === undefined ? title : group.sort_id);
        var items = _cards(group.vods, title);
        if (items.length) sections.push({ title: title, key: key, items: items });
    }
    return sections;
}



function _flattenHome() {
    var sections = _buildHomeSections();
    var ordered = [];
    var index;
    for (index = 0; index < sections.length; index += 1) {
        if (sections[index].title === '热门') ordered.push(sections[index]);
    }
    for (index = 0; index < sections.length; index += 1) {
        if (sections[index].title !== '热门' && sections[index].key !== '__hero__') ordered.push(sections[index]);
    }
    for (index = 0; index < sections.length; index += 1) {
        if (sections[index].key === '__hero__') ordered.push(sections[index]);
    }

    var cards = [];
    var seen = {};
    for (index = 0; index < ordered.length; index += 1) {
        var items = ordered[index].items || [];
        for (var itemIndex = 0; itemIndex < items.length; itemIndex += 1) {
            var item = items[itemIndex];
            if (seen[item.id]) continue;
            seen[item.id] = true;
            cards.push(item);
        }
    }
    return cards;
}



function homeSections() {
    try {
        return JSON.stringify(_buildHomeSections());
    } catch (error) {
        _lanercLog('首页分区转换失败：' + String(error));
        return '[]';
    }
}



function search(keyword, page) {
    try {
        var word = _legacyTrim(keyword);
        if (!word) return JSON.stringify(_flattenHome());
        if (_isCategoryKey(word)) return JSON.stringify(_filteredPage(word, {}, page || 1));
        var data = _payload(_apiGet('app/vod/search?keyword=' + encodeUri(word)));
        return JSON.stringify(_cards(data.search_vods, ''));
    } catch (error) {
        _lanercLog('搜索失败：' + String(error));
        return '[]';
    }
}



function categories() {
    try {
        var home = _getHome();
        var groups = _legacyIsArray(home.vod_list) ? home.vod_list : [];
        var result = [{ key: '', title: '推荐', name: '推荐' }];
        for (var index = 0; index < groups.length; index += 1) {
            var group = groups[index] || {};
            var title = String(group.sort_name || '分类');
            var key = String(group.sort_id === null || group.sort_id === undefined ? title : group.sort_id);
            result.push({
                key: key,
                title: title,
                name: title,
                filters: [
                    { key: 'class', name: '', value: _filterOptions(group.type_class) },
                    { key: 'year', name: '', value: _filterOptions(group.type_year) },
                    {
                        key: 'sort',
                        name: '',
                        value: [{ n: '按时间', v: '' }, { n: '按评分', v: 'vod_score' }]
                    }
                ]
            });
        }
        return JSON.stringify(result);
    } catch (error) {
        _lanercLog('分类转换失败：' + String(error));
        return '[]';
    }
}



function _filterValue(value) {
    if (value === null || value === undefined || value === '全部') return '';
    return String(value);
}



function _isCategoryKey(value) {
    var key = String(value || '');
    var home = _getHome();
    var groups = _legacyIsArray(home.vod_list) ? home.vod_list : [];
    for (var index = 0; index < groups.length; index += 1) {
        var group = groups[index] || {};
        var groupKey = String(group.sort_id === null || group.sort_id === undefined ? '' : group.sort_id);
        if (groupKey === key) return true;
    }
    return false;
}



function _filteredPage(category, filters, page) {
    var source = filters || {};
    var classValue = _filterValue(source['class'] || source.type || '');
    var yearValue = _filterValue(source.year || '');
    var sortValue = _filterValue(source.sort || '');
    if (sortValue === '按评分') sortValue = 'vod_score';
    if (sortValue === '按时间') sortValue = '';
    var url = 'app/vod/filter?page=' + encodeUri(String(page || 1)) +
        '&class_id=' + encodeUri(_filterValue(category)) +
        '&vod_class=' + encodeUri(classValue) +
        '&year=' + encodeUri(yearValue) +
        '&sort_by=' + encodeUri(sortValue);
    var data = _payload(_apiGet(url));
    return _cards(data.filter_vods, '');
}



function searchFiltered(category, filtersJson, page) {
    try {
        if (_filterValue(category) === '') return JSON.stringify(_flattenHome());
        var filters = _safeParse(filtersJson, {}) || {};
        return JSON.stringify(_filteredPage(category, filters, page || 1));
    } catch (error) {
        _lanercLog('分类筛选失败：' + String(error));
        return '[]';
    }
}



function _loadRuntimeConfig() {
    if (_lanercRuntime !== null) return _lanercRuntime;
    var data = _apiGet('app/config?platform=android');
    _lanercRuntime = {
        sign: String(_findDeep(data, 'sign') || ''),
        auth: String(_findDeep(data, 'auth') || '')
    };
    return _lanercRuntime;
}



function _runtimeValues(flagData) {
    var flag = flagData && typeof flagData === 'object' ? flagData : {};
    var config = _loadRuntimeConfig();
    var sign = _firstValue(flag, ['sign']);
    if (sign === '') sign = _firstValue(_lanercExt, ['sign']);
    if (sign === '') sign = _LANERC_BUILD_SIGNATURE;
    var auth = _firstValue(flag, ['auth']);
    if (auth === '') auth = _firstValue(_lanercExt, ['auth']);
    if (auth === '') auth = config.auth;
    if (auth === '') auth = _LANERC_AUTH_FALLBACK;
    return { sign: String(sign || ''), auth: String(auth) };
}



function _videoItems(value) {
    if (_legacyIsArray(value)) return value;
    if (value === null || value === undefined || value === '') return [];
    if (typeof value === 'string') {
        var parsed = _safeParse(value, null);
        if (_legacyIsArray(parsed)) return parsed;
        return String(value).split('#');
    }
    return [value];
}



function _episodePart(value, fallbackName) {
    if (value && typeof value === 'object') {
        var objectVid = _firstValue(value, [
            'vid', 'video_id', 'videoId', 'episode_id', 'episodeId', 'id', 'url', 'value'
        ]);
        return {
            name: String(_firstValue(value, ['name', 'title']) || fallbackName || ''),
            vid: String(objectVid || ''),
            raw: String(_firstValue(value, [
                'raw', 'url', 'vid', 'video_id', 'videoId', 'episode_id', 'episodeId', 'id', 'value'
            ]) || '')
        };
    }
    var raw = value === null || value === undefined ? '' : String(value);
    var parts = raw.split('$');
    return {
        name: String(parts[0] || fallbackName || ''),
        vid: String(parts.length > 1 ? parts[1] : raw),
        raw: raw
    };
}



function _sortPlayLines(left, right) {
    var leftSort = Number(left && left.sort);
    var rightSort = Number(right && right.sort);
    if (!isFinite(leftSort)) leftSort = 0;
    if (!isFinite(rightSort)) rightSort = 0;
    return leftSort - rightSort;
}

 
function _isMainLine(line) {
    var name = String((line && (line.name || line.title)) || '');
    return /LC\s*-?\s*Main/i.test(name);
}



function _episodes(playList, runtime) {
    var lines = _legacyIsArray(playList) ? playList.slice() : [];
    lines.sort(_sortPlayLines);
    
    
    
    var mainLines = [];
    var otherLines = [];
    for (var splitIndex = 0; splitIndex < lines.length; splitIndex += 1) {
        (_isMainLine(lines[splitIndex]) ? mainLines : otherLines).push(lines[splitIndex]);
    }
    lines = mainLines.concat(otherLines);
    var usedRouteNames = {};
    var result = [];
    for (var lineIndex = 0; lineIndex < lines.length; lineIndex += 1) {
        var line = lines[lineIndex] || {};
        
        
        var lineName = _legacyTrim(String(line.name || line.title || '').replace(/[\[【（(].*$/, ''));
        if (!lineName) lineName = '线路' + (lineIndex + 1);
        
        
        var usedCount = usedRouteNames[lineName] || 0;
        usedRouteNames[lineName] = usedCount + 1;
        if (usedCount > 0) lineName = lineName + ' ' + (usedCount + 1);
        var player = String(line.player || '');
        var videos = _videoItems(line.video);
        for (var videoIndex = 0; videoIndex < videos.length; videoIndex += 1) {
            var part = _episodePart(videos[videoIndex], '第' + (videoIndex + 1) + '集');
            if (!part.vid) continue;
            var flag = {
                raw: part.raw,
                vid: part.vid,
                player: player,
                sign: runtime.sign,
                auth: runtime.auth
            };
            result.push({ name: part.name, url: JSON.stringify(flag), route: lineName });
        }
    }
    return result;
}



function detail(id) {
    var contentId = id === null || id === undefined ? '' : String(id);
    try {
        var data = _payload(_apiGet('app/getvod/' + encodeUri(contentId)));
        var info = data.video_play_info && typeof data.video_play_info === 'object' ? data.video_play_info : data;
        var runtime = _runtimeValues({});
        
        _lanercLastDetail = { id: contentId, classId: _legacyTrim(String(_firstValue(info, ['vod_type']) || '')) };
        return JSON.stringify({
            id: String(_firstValue(info, ['id', 'vod_id']) || contentId),
            name: String(_firstValue(info, ['vod_name', 'name', 'title']) || ''),
            pic: _lanercCoverPic(_firstValue(info, ['vod_pic', 'pic', 'image', 'cover'])),
            desc: String(_firstValue(info, ['vod_blurb', 'desc', 'vod_content']) || ''),
            type: _typeText(info),
            year: String(_firstValue(info, ['vod_year', 'year']) || ''),
            remarks: String(_firstValue(info, ['vod_sub', 'vod_remarks']) || ''),
            score: String(_firstValue(info, ['vod_score', 'score']) || ''),
            episodes: _episodes(data.video_play_list, runtime)
        });
    } catch (error) {
        _lanercLog('详情转换失败：' + String(error));
        return JSON.stringify({ id: contentId, name: '', pic: '', desc: '', episodes: [] });
    }
}



function _resolveContentClass(contentId) {
    if (_lanercLastDetail && String(_lanercLastDetail.id) === contentId) return _lanercLastDetail.classId;
    var data = _payload(_apiGet('app/getvod/' + encodeUri(contentId)));
    var info = data.video_play_info && typeof data.video_play_info === 'object' ? data.video_play_info : data;
    return _legacyTrim(String(_firstValue(info, ['vod_type']) || ''));
}



function related(id) {
    var contentId = id === null || id === undefined ? '' : String(id);
    try {
        if (!contentId) return '[]';
        var classId = _resolveContentClass(contentId);
        if (!classId) return '[]';
        var cards = _filteredPage(classId, {}, 1);
        var out = [];
        for (var index = 0; index < cards.length; index += 1) {
            if (String(cards[index].id) === contentId) continue;
            out.push(cards[index]);
            if (out.length >= 20) break;
        }
        return JSON.stringify(out);
    } catch (error) {
        _lanercLog('相关推荐获取失败：' + String(error));
        return '[]';
    }
}



function _mediaType(url) {
    var value = String(url || '');
    if (/\.m3u8(?:$|[?#])/i.test(value)) return 'm3u8';
    if (/\.mp4(?:$|[?#])/i.test(value)) return 'mp4';
    return 'auto';
}



function _shouldBlockLanercWarningPlaylist() {
    var value = _lanercExt.blockWarningPlaylist;
    if (value === null || value === undefined || value === '') return _LANERC_BLOCK_WARNING_PLAYLIST;
    return value === true || value === 1 || String(value).toLowerCase() === 'true';
}



function _isLanercWarningPlaylist(url) {
    var value = String(url || '');
    if (!/^https?:\/\/file\.jngaoke\.cn\/.*\.m3u8(?:$|[?#])/i.test(value)) return false;
    try {
        var playlist = String(request(
            value,
            _requestOptions(false, _LANERC_WARNING_PLAYLIST_TIMEOUT_MS)
        ) || '');
        var pattern = /#EXTINF:\s*([0-9]+(?:\.[0-9]+)?)/ig;
        var count = 0;
        var duration = 0;
        var matched;
        while ((matched = pattern.exec(playlist)) !== null) {
            count += 1;
            duration += Number(matched[1]);
        }
        return count >= 10 && duration >= 179 && duration <= 181;
    } catch (error) {
        _lanercLog('防盗提示片检测失败：' + String(error));
        return false;
    }
}



function play(flag) {
    try {
        var parsed = _safeParse(flag, null);
        var flagData = parsed && typeof parsed === 'object' && !_legacyIsArray(parsed) ? parsed : {};
        var rawFlag = flag === null || flag === undefined ? '' : String(flag);
        var vid = _firstValue(flagData, ['vid']);
        if (vid === '') vid = rawFlag;
        var runtime = _runtimeValues(flagData);
        var body = {
            vid: String(vid || ''),
            player: String(_firstValue(flagData, ['player']) || ''),
            sign: runtime.sign,
            auth: runtime.auth
        };
        if (!body.vid) return JSON.stringify({ url: '', type: 'auto' });
        var response = _apiPost(_lanercSignedApiPath('app/proxyx3x'), body);
        var playUrl = _playUrlFromResponse(response);
        if (_shouldBlockLanercWarningPlaylist() && _isLanercWarningPlaylist(playUrl)) {
            _lanercLog('检测到 180 秒防盗提示片，重新签名请求一次');
            response = _apiPost(_lanercSignedApiPath('app/proxyx3x'), body);
            playUrl = _playUrlFromResponse(response);
            if (_isLanercWarningPlaylist(playUrl)) {
                var warningMessage = '检测到防盗提示片，当前线路不可播放';
                _lanercLog(warningMessage);
                return JSON.stringify({
                    url: '',
                    type: 'auto',
                    error: warningMessage,
                    _server_msg: warningMessage
                });
            }
        }
        return JSON.stringify(_playResult(response, playUrl));
    } catch (error) {
        _lanercLog('播放解析失败：' + String(error));
        return JSON.stringify({ url: '', type: 'auto' });
    }
}
