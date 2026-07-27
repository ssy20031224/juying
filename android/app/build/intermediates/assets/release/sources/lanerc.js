/* Lanerc 旧版宿主兼容源  version: 1.0.7 */

// 2026-07-20 修复：该 COS 发现地址已失联（域名无法解析/被黑洞），此前放在解析首位会把
// 首次调用拖慢十几秒，撞上宿主 8s 首拉预算被误判成坏源。现改为「回退站优先」（见
// _resolveHost），本地址仅在回退站失败时用 3s 短超时兜底尝试，保留为将来换域名的自愈通道。
var _LANERC_DISCOVERY = 'https://anime999x-1366475786.cos.ap-guangzhou.myqcloud.com/apis.json';
var _LANERC_FALLBACK_HOST = 'http://lol.jngaoke.cn/';
/** 回退站探测与域名发现请求的短超时（毫秒）：失联地址快速失败，不拖慢首次调用。 */
var _LANERC_PROBE_TIMEOUT_MS = 3000;
var _LANERC_STALE_HOST = 'https://server.jngaoke.cn/';
var _LANERC_AUTH_FALLBACK = 'com.clggjv.xcjfmd.ffo';
var _LANERC_DECRYPT_KEY = '8f81c2519e3b661834219e7142000093';
var _lanercExt = typeof ext === 'object' && ext ? ext : {};
var _lanercHost = '';
var _lanercHome = null;
var _lanercRuntime = null;

/**
 * 兼容旧 Rhino：避免依赖 String.prototype.trim。
 */
function _legacyTrim(value) {
    return value === null || value === undefined ? '' : String(value).replace(/^\s+|\s+$/g, '');
}

/**
 * 兼容旧 Rhino：避免依赖 Array.isArray。
 */
function _legacyIsArray(value) {
    return Object.prototype.toString.call(value) === '[object Array]';
}

/**
 * 兼容精简宿主：安全判断对象自有字段。
 */
function _legacyOwn(object, key) {
    return object !== null && object !== undefined &&
        Object.prototype.hasOwnProperty.call(object, key);
}

/**
 * 输出统一的中文日志，日志异常不能影响数据源主流程。
 */
function _lanercLog(message) {
    try {
        log('[Lanerc旧版源] ' + message);
    } catch (error) {
        // 某些最老宿主没有日志实现，直接忽略即可。
    }
}

/**
 * 把基础地址整理为恰好带一个结尾斜杠的形式。
 */
function _normalizeHost(host) {
    var value = _legacyTrim(host);
    if (!value) return '';
    return value.replace(/\/+$/, '') + '/';
}

/**
 * 识别在线配置仍在下发、但证书已过期的旧业务站点。
 */
function _isStaleLanercHost(host) {
    return _normalizeHost(host).toLowerCase() === _LANERC_STALE_HOST;
}

/**
 * 安全解析 JSON；已经是对象的值直接返回。
 */
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

/**
 * 读取旧版源的接口解密配置；未配置 key 时明确返回空对象。
 */
function _decryptOptions() {
    var config = _lanercExt.decrypt;
    if (typeof config === 'string') config = _safeParse(config, {});
    if (!config || typeof config !== 'object') return {};
    return config;
}

/**
 * 参考 manshan.js 的自包含结构，实现旧宿主可用的精简 AES-256-ECB 解密器。
 * 仅包含当前协议需要的 Base64、PKCS7 和 UTF-8 解码，不依赖宿主 crypto。
 */
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

    /** 在 AES 有限域中执行乘二。 */
    function _aesXtime(value) {
        return ((value << 1) ^ (((value >> 7) & 1) * 0x1b)) & 0xff;
    }

    /** 将 32 字节密钥扩展为 AES-256 的 15 组轮密钥。 */
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

    /** 执行一轮逆向行移位和逆向字节替换。 */
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

    /** 就地执行 AES 逆向列混合。 */
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

    /** 使用已扩展的 AES-256 轮密钥解密单个 16 字节块。 */
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

    /** 把 ASCII 密钥字符串转换为字节数组。 */
    function _aesTextBytes(value) {
        var result = [];
        for (var index = 0; index < value.length; index += 1) result.push(value.charCodeAt(index) & 0xff);
        return result;
    }

    /** 在不依赖 atob 的旧宿主中解析标准 Base64。 */
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

    /** 把解密后的 UTF-8 字节还原为宿主字符串。 */
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
        /** 解密标准 Base64 编码的 AES-256-ECB/PKCS7 密文。 */
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

/**
 * 严格按应用原始顺序恢复 Lanerc 的自定义 Base64 字符表。
 * 后五步依赖前五步产生的占位字符，不能合并成无序映射。
 */
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

/**
 * 对接口返回的自定义 Base64 密文做 AES 解密并还原 JSON 对象。
 * 默认使用 APK 已确认参数，同时允许 ext.decrypt 覆盖协议配置。
 */
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

/**
 * 识别 Lanerc 的 code=201 加密响应，成功时替换为明文业务对象。
 */
function _decodeApiResponse(value) {
    var response = value;
    if (!response || typeof response !== 'object' || _legacyIsArray(response)) return response;
    if (Number(response.code) === 201 && typeof response.data === 'string') {
        var decrypted = _decryptApiData(response.data);
        return decrypted || {};
    }
    return response;
}

/**
 * 生成旧版 request/post 接受的字符串配置。
 * 2026-07-20：新增 timeoutMs 按次覆盖（宿主 JsHttp 认 options.timeout 毫秒字段），
 * 供发现/探测请求传短超时；不传时沿用 ext.timeout 的旧行为。
 */
function _requestOptions(isPost, timeoutMs) {
    var headers = { Accept: 'application/json' };
    if (isPost) headers['Content-Type'] = 'application/json';
    var options = { headers: headers };
    var timeout = Number(timeoutMs || _lanercExt.timeout || 0);
    if (timeout > 0 && isFinite(timeout)) options.timeout = timeout;
    return JSON.stringify(options);
}

/**
 * 使用旧版同步 request 发起 GET，并将响应解析为对象。
 * 2026-07-20：透传可选 timeoutMs（见 _requestOptions），业务调用不传时行为不变。
 */
function _requestJson(url, timeoutMs) {
    try {
        return _decodeApiResponse(_safeParse(request(url, _requestOptions(false, timeoutMs)), {}));
    } catch (error) {
        _lanercLog('GET失败：' + url + '；' + String(error));
        return {};
    }
}

/**
 * 使用旧版同步 post 发送 JSON，并将响应解析为对象。
 */
function _postJson(url, body) {
    try {
        return _decodeApiResponse(_safeParse(post(url, JSON.stringify(body || {}), _requestOptions(true)), {}));
    } catch (error) {
        _lanercLog('POST失败：' + url + '；' + String(error));
        return {};
    }
}

/**
 * 递归查找配置或响应中的指定字段，最多下探十二层。
 */
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

/**
 * 解析业务基础地址：ext.host 优先，其次静态回退站，最后在线发现。
 *
 * 2026-07-20 修复：老顺序是「在线发现 → 静态回退」，而内置 COS 发现地址已失联
 * （域名解析失败/被黑洞），首次调用要白等发现请求超时才落到回退站，撞上宿主 8s
 * 首拉预算被误判成坏源。现在反过来：先用 3s 短超时探测实测存活的回退站
 * lol.jngaoke.cn（探到的首页数据顺手喂给 _getHome 复用，探测零浪费）；回退站真挂
 * 了才用发现地址（同样 3s 短超时）自愈到新域名，失联地址只保留为末位自愈通道。
 */
function _resolveHost() {
    if (_lanercHost) return _lanercHost;
    _lanercHost = _normalizeHost(_lanercExt.host);
    if (_lanercHost) return _lanercHost;

    var fallbackProbe = _payload(_requestJson(_LANERC_FALLBACK_HOST + 'app/home', _LANERC_PROBE_TIMEOUT_MS));
    if (fallbackProbe && typeof fallbackProbe === 'object' &&
        (_legacyOwn(fallbackProbe, 'vod_list') || _legacyOwn(fallbackProbe, 'banner') || _legacyOwn(fallbackProbe, 'hot_list'))) {
        _lanercHome = fallbackProbe;
        _lanercHost = _LANERC_FALLBACK_HOST;
        return _lanercHost;
    }

    _lanercLog('静态回退站探测失败，尝试在线域名发现');
    var configUrl = String(_lanercExt.configUrl || _LANERC_DISCOVERY);
    var discovery = _requestJson(configUrl, _LANERC_PROBE_TIMEOUT_MS);
    var discoveredHost = _normalizeHost(_findDeep(discovery, 'domain'));
    if (_isStaleLanercHost(discoveredHost)) {
        _lanercLog('在线配置仍为证书过期旧站点，改用静态回退地址');
        discoveredHost = '';
    }
    _lanercHost = discoveredHost;
    if (!_lanercHost) {
        _lanercLog('域名发现失败，使用静态回退地址');
        _lanercHost = _LANERC_FALLBACK_HOST;
    }
    return _lanercHost;
}

/**
 * 调用 Lanerc 业务 GET 接口。
 */
function _apiGet(path) {
    return _requestJson(_resolveHost() + String(path || '').replace(/^\/+/, ''));
}

/**
 * 调用 Lanerc 业务 POST 接口。
 */
function _apiPost(path, body) {
    return _postJson(_resolveHost() + String(path || '').replace(/^\/+/, ''), body);
}

/**
 * 逐层移除常见的 data 包裹，但保留业务对象和数组本身。
 */
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

// 豆瓣图床封面防盗链 UA（2026-07-20 图片不显示修复）：img3 节点对 okhttp UA 即使带 Referer 也 403，须浏览器 UA。
var _LANERC_PIC_UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36';

/**
 * 给豆瓣图床封面拼防盗链头后缀（宿主 PicUrl 协议 @Referer=/@User-Agent=，见 App PicUrl.kt）。
 * 豆瓣图床无 Referer 一律 418、img3 节点还要求非-okhttp UA，拼上两个头后 App 用净地址发请求 +
 * 附头即可过防盗链，不依赖 App 侧 ImageLoader 拦截器。只对 doubanio 域名拼（其余图床原样返回）；
 * 已带后缀的不重复拼。
 */
function _lanercCoverPic(pic) {
    var url = String(pic || '');
    if (!url) return '';
    if (url.indexOf('@Referer=') !== -1 || url.indexOf('@User-Agent=') !== -1 || url.indexOf('@Headers=') !== -1) return url;
    if (url.indexOf('doubanio.com') === -1) return url;
    return url + '@Referer=https://movie.douban.com/@User-Agent=' + _LANERC_PIC_UA;
}

/**
 * 按字段优先级取第一个非空值。
 */
function _firstValue(object, keys) {
    var source = object || {};
    for (var index = 0; index < keys.length; index += 1) {
        var value = source[keys[index]];
        if (value !== null && value !== undefined && value !== '') return value;
    }
    return '';
}

/**
 * 把不同接口的视频条目统一为宿主视频卡片。
 */
function _card(item, sectionTitle) {
    var source = item || {};
    var id = _firstValue(source, ['id', 'vod_id']);
    var name = _firstValue(source, ['vod_name', 'name', 'title']);
    if (id === '' || name === '') return null;
    return {
        id: String(id),
        name: String(name),
        pic: _lanercCoverPic(_firstValue(source, ['vod_pic', 'pic', 'image', 'cover'])),
        type: String(sectionTitle || _firstValue(source, ['vod_class', 'vod_type']) || ''),
        year: String(_firstValue(source, ['vod_year', 'year']) || ''),
        remarks: String(_firstValue(source, ['vod_remarks', 'vod_sub', 'vod_tag', 'vod_isend']) || ''),
        desc: String(_firstValue(source, ['vod_blurb', 'desc']) || '')
    };
}

/**
 * 把业务数组批量转换为有效视频卡片。
 */
function _cards(items, sectionTitle) {
    var list = _legacyIsArray(items) ? items : [];
    var result = [];
    for (var index = 0; index < list.length; index += 1) {
        var item = _card(list[index], sectionTitle);
        if (item) result.push(item);
    }
    return result;
}

/**
 * 将分类配置转换为显示字符串。
 */
function _optionText(value) {
    if (value === null || value === undefined) return '';
    if (typeof value === 'object') {
        return _legacyTrim(_firstValue(value, ['n', 'name', 'title', 'v', 'value']) || '');
    }
    return _legacyTrim(value);
}

/**
 * 兼容数组、JSON 数组、逗号和斜杠分隔的筛选选项，并保证“全部”位于首项。
 */
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

/**
 * 将筛选字符串转换为 TVBox 显示值/提交值，全部选项提交空字符串。
 */
function _filterOptions(value) {
    var options = _options(value);
    var result = [];
    for (var index = 0; index < options.length; index += 1) {
        var name = String(options[index]);
        result.push({ n: name, v: name === '全部' ? '' : name });
    }
    return result;
}

/**
 * 获取并缓存当前上下文的首页数据。
 */
function _getHome() {
    if (_lanercHome !== null) return _lanercHome;
    _lanercHome = _payload(_apiGet('app/home'));
    if (!_lanercHome || typeof _lanercHome !== 'object') _lanercHome = {};
    return _lanercHome;
}

/**
 * 根据首页模型构造推荐、热门和分类分区。
 */
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

/**
 * 将首页按“热门、分类、轮播”顺序展开，并按内容 ID 去重。
 */
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

/**
 * 返回首页分区；异常时返回空数组 JSON。
 */
function homeSections() {
    try {
        return JSON.stringify(_buildHomeSections());
    } catch (error) {
        _lanercLog('首页分区转换失败：' + String(error));
        return '[]';
    }
}

/**
 * 搜索内容；空关键词时兼容旧版宿主并返回首页扁平列表。
 */
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

/**
 * 返回老格式分类筛选，适配不支持新版数组筛选结构的宿主。
 */
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

/**
 * 将老格式“全部”和值映射为接口实际提交值。
 */
function _filterValue(value) {
    if (value === null || value === undefined || value === '全部') return '';
    return String(value);
}

/**
 * 判断搜索参数是否为首页下发的真实分类 key。
 */
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

/**
 * 请求分类筛选的单个服务端页，并转换为宿主卡片。
 */
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

/**
 * 保留分类筛选，并按宿主传入页码返回单个服务端页。
 */
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

/**
 * 在当前 QuickJS 上下文中只读取一次播放运行配置。
 */
function _loadRuntimeConfig() {
    if (_lanercRuntime !== null) return _lanercRuntime;
    var data = _apiGet('app/config?platform=android');
    _lanercRuntime = {
        sign: String(_findDeep(data, 'sign') || ''),
        auth: String(_findDeep(data, 'auth') || '')
    };
    return _lanercRuntime;
}

/**
 * 按播放标记、ext、运行配置的顺序确定 sign/auth，并补齐包名授权。
 */
function _runtimeValues(flagData) {
    var flag = flagData && typeof flagData === 'object' ? flagData : {};
    var config = _loadRuntimeConfig();
    var sign = _firstValue(flag, ['sign']);
    if (sign === '') sign = _firstValue(_lanercExt, ['sign']);
    if (sign === '') sign = config.sign;
    var auth = _firstValue(flag, ['auth']);
    if (auth === '') auth = _firstValue(_lanercExt, ['auth']);
    if (auth === '') auth = config.auth;
    if (auth === '') auth = _LANERC_AUTH_FALLBACK;
    return { sign: String(sign || ''), auth: String(auth) };
}

/**
 * 将线路的 video 字段转换为播放项数组。
 */
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

/**
 * 解析“集名$vid”或对象形式的单个播放项。
 */
function _episodePart(value, fallbackName) {
    if (value && typeof value === 'object') {
        var objectVid = _firstValue(value, ['vid', 'url', 'value']);
        return {
            name: String(_firstValue(value, ['name', 'title']) || fallbackName || ''),
            vid: String(objectVid || ''),
            raw: String(_firstValue(value, ['raw', 'url', 'vid', 'value']) || '')
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

/**
 * 按线路 sort 字段排序，缺少 sort 时保持相对顺序。
 */
function _sortPlayLines(left, right) {
    var leftSort = Number(left && left.sort);
    var rightSort = Number(right && right.sort);
    if (!isFinite(leftSort)) leftSort = 0;
    if (!isFinite(rightSort)) rightSort = 0;
    return leftSort - rightSort;
}

/**
 * 把详情中的多线路播放项转换为宿主 episodes：每集带 route=线路名，
 * 宿主按 route 分组渲染线路切换 Tab（并支持播放失败自动换线）。
 * ⚠ 不要再用「线路1 第1集」这种拼名摊平的写法——所有集会挤在同一条线路里。
 */
function _episodes(playList, runtime) {
    var lines = _legacyIsArray(playList) ? playList.slice() : [];
    lines.sort(_sortPlayLines);
    var usedRouteNames = {};
    var result = [];
    for (var lineIndex = 0; lineIndex < lines.length; lineIndex += 1) {
        var line = lines[lineIndex] || {};
        // 线路名截掉站方拼在后面的括号提示语（如「LC - Main[如果一直加载请…]」），
        // 否则线路 Tab 会被撑成一长条；截完为空再落到「线路N」。
        var lineName = _legacyTrim(String(line.name || line.title || '').replace(/[\[【（(].*$/, ''));
        if (!lineName) lineName = '线路' + (lineIndex + 1);
        // 截断后撞名（两条线路仅括号内不同）会被宿主按 route 合并成一个 Tab、
        // 自动换线也会失效——重名时追加序号区分。
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

/**
 * 获取详情元数据与多线路选集；异常时返回可安全渲染的空详情。
 */
function detail(id) {
    var contentId = id === null || id === undefined ? '' : String(id);
    try {
        var data = _payload(_apiGet('app/getvod/' + encodeUri(contentId)));
        var info = data.video_play_info && typeof data.video_play_info === 'object' ? data.video_play_info : data;
        var runtime = _runtimeValues({});
        return JSON.stringify({
            id: String(_firstValue(info, ['id', 'vod_id']) || contentId),
            name: String(_firstValue(info, ['vod_name', 'name', 'title']) || ''),
            pic: _lanercCoverPic(_firstValue(info, ['vod_pic', 'pic', 'image', 'cover'])),
            desc: String(_firstValue(info, ['vod_blurb', 'desc', 'vod_content']) || ''),
            type: String(_firstValue(info, ['vod_type', 'vod_class']) || ''),
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

/**
 * 根据媒体地址后缀推断宿主播放器类型。
 */
function _mediaType(url) {
    var value = String(url || '');
    if (/\.m3u8(?:$|[?#])/i.test(value)) return 'm3u8';
    if (/\.mp4(?:$|[?#])/i.test(value)) return 'mp4';
    return 'auto';
}

/**
 * 调用原应用播放代理，把详情标记转换为最终媒体地址。
 */
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
        var response = _apiPost('app/proxy', body);
        var playUrl = String(_findDeep(response, 'play_url') || '');
        if (!playUrl || playUrl.indexOf('5de1db9f489ce649f17bf695cde3878c') !== -1) {
            response = _apiPost('app/proxyx3x', body);
            var altUrl = String(_findDeep(response, 'play_url') || '');
            if (altUrl && altUrl.indexOf('5de1db9f489ce649f17bf695cde3878c') === -1) {
                playUrl = altUrl;
            }
        }
        return JSON.stringify({ url: playUrl, type: _mediaType(playUrl) });
    } catch (error) {
        _lanercLog('播放解析失败：' + String(error));
        return JSON.stringify({ url: '', type: 'auto' });
    }
}