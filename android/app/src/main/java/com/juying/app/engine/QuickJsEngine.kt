package com.juying.app.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import app.cash.quickjs.QuickJs
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.juying.app.source.SourceLogManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

interface HostApiNative {
    fun request(url: String, optsJson: String?): String
    fun post(url: String, bodyStr: String?, optsJson: String?): String
    fun post2(url: String, bodyStr: String?, optsJson: String?): String
    fun md5(input: String): String
    fun sha1(input: String): String
    fun sha256(input: String): String
    fun sha512(input: String): String
    fun hmac(input: String, key: String, algo: String?): String
    fun base64Decode(input: String): String
    fun timestamp(): Double
    fun encodeUri(input: String): String
    fun decodeUri(input: String): String
    fun parseJson(input: String): String
    fun match(html: String, pattern: String, group: Int): String
    fun matchAll(html: String, pattern: String): String
    fun getItem(key: String, defaultValue: String?): String
    fun setItem(key: String, value: String)
    fun log(msg: String)

    fun aesEncrypt(plain: String, key: String, optsJson: String?): String
    fun aesDecrypt(cipher: String, key: String, optsJson: String?): String
    fun rsaEncrypt(plain: String, key: String, optsJson: String?): String
    fun rsaDecrypt(cipher: String, key: String, optsJson: String?): String
    fun hexEncode(data: String, optsJson: String?): String
    fun hexDecode(data: String, optsJson: String?): String
    fun base64Encode(data: String, optsJson: String?): String
    fun base64DecodeOpts(data: String, optsJson: String?): String
    fun inflate(data: String, optsJson: String?): String
    fun sniffMedia(url: String, optsJson: String?): String
}

class QuickJsEngine(private val context: Context) {

    private val gson = Gson()
    private val client = NetworkClient.create(context)
    private val storage = mutableMapOf<String, MutableMap<String, String>>()

    fun loadSource(localFile: String): SourceExports {
        val sourceKey = localFile.removeSuffix(".js").substringAfterLast("/")
        val assetPath = if (localFile.startsWith("sources/")) localFile else "sources/$localFile"
        val code = try {
            context.assets.open(assetPath).bufferedReader().readText()
        } catch (e: Exception) {
            android.util.Log.e("QuickJsEngine", "Failed to open asset $assetPath: ${e.message}")
            ""
        }
        return loadSourceFromCode(sourceKey, localFile, code)
    }

    fun loadSourceFromCode(sourceKey: String, localFile: String, code: String): SourceExports {
        val cleanCode = code.removePrefix("\uFEFF").trim()
        if (cleanCode.isEmpty()) throw IllegalStateException("Empty script for $sourceKey")
        val store = storage.getOrPut(sourceKey) { mutableMapOf() }
        val exports = SourceExports(sourceKey, gson)

        // Create and initialize QuickJS on the source's dedicated thread
        exports.executor.submit(java.util.concurrent.Callable {
            val qjs = QuickJs.create()
            val hostImpl = createHost(sourceKey, store, exports)
            qjs.set("HostNative", HostApiNative::class.java, hostImpl)
            qjs.evaluate(BRIDGE_SCRIPT, "bridge.js")
            qjs.evaluate(cleanCode, localFile)
            exports.setQuickJs(qjs)
            android.util.Log.i("QuickJsEngine", "[$sourceKey] JS engine initialized successfully")
        }).get(20, TimeUnit.SECONDS)

        return exports
    }

    private fun createHost(sourceKey: String, store: MutableMap<String, String>, exports: SourceExports): HostApiNative {
        return object : HostApiNative {
            override fun request(url: String, optsJson: String?): String {
                return syncHttp("GET", url, null, parseOpts(optsJson))
            }

            override fun post(url: String, bodyStr: String?, optsJson: String?): String {
                return syncHttp("POST", url, bodyStr, parseOpts(optsJson))
            }

            override fun post2(url: String, bodyStr: String?, optsJson: String?): String {
                return try {
                    val opts = parseOpts(optsJson)
                    val charsetName = opts["charset"] ?: "utf-8"
                    val charset = try { java.nio.charset.Charset.forName(charsetName) } catch (_: Exception) { Charsets.ISO_8859_1 }
                    val bodyBytes = bodyStr?.toByteArray(charset)
                    val respBytes = syncHttpBytes("POST", url, bodyBytes, opts)
                    val bodyText = String(respBytes, charset)
                    val json = com.google.gson.JsonObject()
                    json.addProperty("body", bodyText)
                    json.addProperty("statusCode", 200)
                    gson.toJson(json)
                } catch (e: Exception) {
                    val json = com.google.gson.JsonObject()
                    json.addProperty("body", "")
                    json.addProperty("statusCode", 500)
                    gson.toJson(json)
                }
            }

            override fun md5(input: String): String = digest(input, "MD5")
            override fun sha1(input: String): String = digest(input, "SHA-1")
            override fun sha256(input: String): String = digest(input, "SHA-256")
            override fun sha512(input: String): String = digest(input, "SHA-512")
            override fun hmac(input: String, key: String, algo: String?): String {
                return hmacHash(input, key, algo ?: "HmacSHA256")
            }
            override fun base64Decode(input: String): String {
                return try {
                    String(Base64.decode(input, Base64.DEFAULT), Charsets.UTF_8)
                } catch (_: Exception) { "" }
            }

            override fun timestamp(): Double = System.currentTimeMillis().toDouble()

            override fun encodeUri(input: String): String {
                return try { URLEncoder.encode(input, "UTF-8") } catch (_: Exception) { input }
            }

            override fun decodeUri(input: String): String {
                return try { URLDecoder.decode(input, "UTF-8") } catch (_: Exception) { input }
            }

            override fun parseJson(input: String): String {
                return try {
                    val element = JsonParser.parseString(input)
                    gson.toJson(element)
                } catch (_: Exception) { "" }
            }

            override fun match(html: String, pattern: String, group: Int): String {
                return try {
                    val regex = Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    regex.find(html)?.groups?.get(group)?.value ?: ""
                } catch (_: Exception) { "" }
            }

            override fun matchAll(html: String, pattern: String): String {
                return try {
                    val regex = Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    val list = regex.findAll(html).map { m ->
                        if (m.groupValues.size > 1) m.groupValues.drop(1) else listOf(m.value)
                    }.toList()
                    gson.toJson(list)
                } catch (_: Exception) { "[]" }
            }

            override fun getItem(key: String, defaultValue: String?): String {
                return store[key] ?: defaultValue ?: ""
            }

            override fun setItem(key: String, value: String) {
                store[key] = value
            }

            override fun log(msg: String) {
                android.util.Log.d("QuickJS-Log", "[$sourceKey] $msg")
                com.juying.app.source.SourceLogManager.info(sourceKey, "JS", msg)
            }

            override fun aesEncrypt(plain: String, key: String, optsJson: String?): String {
                return aesCrypt(true, plain, key, optsJson)
            }

            override fun aesDecrypt(cipher: String, key: String, optsJson: String?): String {
                return aesCrypt(false, cipher, key, optsJson)
            }

            override fun rsaEncrypt(plain: String, key: String, optsJson: String?): String {
                return rsaCrypt(true, plain, key, optsJson)
            }

            override fun rsaDecrypt(cipher: String, key: String, optsJson: String?): String {
                return rsaCrypt(false, cipher, key, optsJson)
            }

            override fun hexEncode(data: String, optsJson: String?): String {
                val opts = parseOpts(optsJson)
                val bytes = toBytes(data, opts["input"] ?: "utf8")
                return fromBytes(bytes, "hex")
            }

            override fun hexDecode(data: String, optsJson: String?): String {
                val opts = parseOpts(optsJson)
                val bytes = data.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                return fromBytes(bytes, opts["output"] ?: "utf8")
            }

            override fun base64Encode(data: String, optsJson: String?): String {
                val opts = parseOpts(optsJson)
                val bytes = toBytes(data, opts["input"] ?: "utf8")
                return Base64.encodeToString(bytes, Base64.NO_WRAP)
            }

            override fun base64DecodeOpts(data: String, optsJson: String?): String {
                val opts = parseOpts(optsJson)
                val bytes = Base64.decode(data, Base64.DEFAULT)
                return fromBytes(bytes, opts["output"] ?: "utf8")
            }

            override fun inflate(data: String, optsJson: String?): String {
                return inflateData(data, optsJson)
            }

            override fun sniffMedia(url: String, optsJson: String?): String {
                return sniffMediaHttp(sourceKey, url, parseOpts(optsJson))
            }
        }
    }  // end createHost

    private val BRIDGE_SCRIPT = """
            globalThis.module = { exports: {} };
            globalThis.exports = globalThis.module.exports;

            function log(msg) { HostNative.log(String(msg || '')); }
            function encodeUri(v) { return HostNative.encodeUri(String(v || '')); }
            function encodeURI(v) { return HostNative.encodeUri(String(v || '')); }
            function encodeURIComponent(v) { return HostNative.encodeUri(String(v || '')); }
            function decodeUri(v) { return HostNative.decodeUri(String(v || '')); }
            function decodeURI(v) { return HostNative.decodeUri(String(v || '')); }
            function decodeURIComponent(v) { return HostNative.decodeUri(String(v || '')); }
            function request(url, opts) {
                var optsStr = typeof opts === 'object' ? JSON.stringify(opts) : (opts || null);
                return HostNative.request(String(url || ''), optsStr);
            }
            function post(url, body, opts) {
                var bodyStr = typeof body === 'object' ? JSON.stringify(body) : (body !== undefined && body !== null ? String(body) : '');
                var optsStr = typeof opts === 'object' ? JSON.stringify(opts) : (opts || null);
                return HostNative.post(String(url || ''), bodyStr, optsStr);
            }
            globalThis.http = {
                post2: function(url, body, opts) {
                    var bodyStr = typeof body === 'object' ? JSON.stringify(body) : (body !== undefined && body !== null ? String(body) : '');
                    var optsStr = typeof opts === 'object' ? JSON.stringify(opts) : (opts || null);
                    var rawJson = HostNative.post2(String(url || ''), bodyStr, optsStr);
                    try { return JSON.parse(rawJson); } catch(e) { return { body: '', statusCode: 500 }; }
                },
                get: function(url, opts) {
                    var optsStr = typeof opts === 'object' ? JSON.stringify(opts) : (opts || null);
                    return HostNative.request(String(url || ''), optsStr);
                },
                post: function(url, body, opts) {
                    var bodyStr = typeof body === 'object' ? JSON.stringify(body) : (body !== undefined && body !== null ? String(body) : '');
                    var optsStr = typeof opts === 'object' ? JSON.stringify(opts) : (opts || null);
                    return HostNative.post(String(url || ''), bodyStr, optsStr);
                }
            };
            function md5(v) { return HostNative.md5(String(v || '')); }
            function sha1(v) { return HostNative.sha1(String(v || '')); }
            function sha256(v) { return HostNative.sha256(String(v || '')); }
            function sha512(v) { return HostNative.sha512(String(v || '')); }
            function hmac(v, key, algo) { return HostNative.hmac(String(v || ''), String(key || ''), algo || null); }
            function base64Decode(v) { return HostNative.base64Decode(String(v || '')); }
            function timestamp() { return HostNative.timestamp(); }
            function parseJson(v) {
                if (v === null || v === undefined || v === '') return null;
                if (typeof v === 'object') return v;
                try { return JSON.parse(String(v)); } catch(e) { return null; }
            }

            globalThis.log = log;
            globalThis.encodeUri = encodeUri;
            globalThis.encodeURI = encodeURI;
            globalThis.encodeURIComponent = encodeURIComponent;
            globalThis.decodeUri = decodeUri;
            globalThis.decodeURI = decodeURI;
            globalThis.decodeURIComponent = decodeURIComponent;
            globalThis.request = request;
            globalThis.post = post;
            globalThis.md5 = md5;
            globalThis.sha1 = sha1;
            globalThis.sha256 = sha256;
            globalThis.sha512 = sha512;
            globalThis.hmac = hmac;
            globalThis.base64Decode = base64Decode;
            globalThis.timestamp = timestamp;
            globalThis.parseJson = parseJson;

            globalThis.console = {
                log: function(msg) { HostNative.log(String(msg || '')); },
                error: function(msg) { HostNative.log('[ERROR] ' + String(msg || '')); },
                warn: function(msg) { HostNative.log('[WARN] ' + String(msg || '')); },
                info: function(msg) { HostNative.log('[INFO] ' + String(msg || '')); }
            };

            globalThis.match = function(html, pattern, group) { return HostNative.match(String(html || ''), String(pattern || ''), group || 0); };
            globalThis.matchAll = function(html, pattern) {
                var resStr = HostNative.matchAll(String(html || ''), String(pattern || ''));
                try { return JSON.parse(resStr); } catch(e) { return []; }
            };
            globalThis.getItem = function(k, def) { return HostNative.getItem(String(k), def || null); };
            globalThis.setItem = function(k, v) { HostNative.setItem(String(k), String(v)); };

            globalThis.crypto = {
                aes: {
                    encrypt: function(plain, key, opts) {
                        var optsStr = typeof opts === 'object' ? JSON.stringify(opts) : (opts || null);
                        return HostNative.aesEncrypt(String(plain || ''), String(key || ''), optsStr);
                    },
                    decrypt: function(cipher, key, opts) {
                        var optsStr = typeof opts === 'object' ? JSON.stringify(opts) : (opts || null);
                        return HostNative.aesDecrypt(String(cipher || ''), String(key || ''), optsStr);
                    }
                },
                rsa: {
                    encrypt: function(plain, key, opts) {
                        var optsStr = typeof opts === 'object' ? JSON.stringify(opts) : (opts || null);
                        return HostNative.rsaEncrypt(String(plain || ''), String(key || ''), optsStr);
                    },
                    decrypt: function(cipher, key, opts) {
                        var optsStr = typeof opts === 'object' ? JSON.stringify(opts) : (opts || null);
                        return HostNative.rsaDecrypt(String(cipher || ''), String(key || ''), optsStr);
                    }
                },
                hex: {
                    encode: function(data, opts) {
                        var optsStr = typeof opts === 'object' ? JSON.stringify(opts) : (opts || null);
                        return HostNative.hexEncode(String(data || ''), optsStr);
                    },
                    decode: function(data, opts) {
                        var optsStr = typeof opts === 'object' ? JSON.stringify(opts) : (opts || null);
                        return HostNative.hexDecode(String(data || ''), optsStr);
                    }
                },
                base64: {
                    encode: function(data, opts) {
                        var optsStr = typeof opts === 'object' ? JSON.stringify(opts) : (opts || null);
                        return HostNative.base64Encode(String(data || ''), optsStr);
                    },
                    decode: function(data, opts) {
                        var optsStr = typeof opts === 'object' ? JSON.stringify(opts) : (opts || null);
                        return HostNative.base64DecodeOpts(String(data || ''), optsStr);
                    }
                },
                inflate: function(data, opts) {
                    var optsStr = typeof opts === 'object' ? JSON.stringify(opts) : (opts || null);
                    return HostNative.inflate(String(data || ''), optsStr);
                }
            };
            globalThis.sniffMedia = function(url, opts) {
                var optsStr = typeof opts === 'object' ? JSON.stringify(opts) : (opts || null);
                var raw = HostNative.sniffMedia(String(url || ''), optsStr);
                return parseJson(raw);
            };
            globalThis.sniffAllMedia = function(url, opts) {
                var one = globalThis.sniffMedia(url, opts);
                return one ? [one] : [];
            };
            globalThis.ext = {};
            globalThis.UA = {};
        """.trimIndent()

    private fun decodeUnicodeEscapes(s: String): String {
        // Fix URLs containing \u003d (=) etc. from JS JSON.stringify
        return s.replace("\\u003d", "=").replace("\\u0026", "&")
                .replace("\\u002f", "/").replace("\\u003f", "?")
    }

    /**
     * Lightweight Android equivalent of the reference app's WebView
     * sniffMedia bridge. It first handles pages that expose a media URL in
     * HTML/JSON. Dynamic JS players still need a WebView, but returning a
     * structured miss is much safer than the old null stub and lets sources
     * fall back cleanly while recording the reason.
     */
    private fun sniffMediaHttp(sourceKey: String, url: String, opts: Map<String, String>): String {
        val result = com.google.gson.JsonObject()
        if (url.isBlank()) {
            result.addProperty("ok", false)
            result.addProperty("error", "empty_url")
            return gson.toJson(result)
        }
        try {
            val headers = mutableMapOf<String, String>()
            opts["headers"]?.let { raw ->
                try {
                    JsonParser.parseString(raw).asJsonObject.entrySet()
                        .forEach { headers[it.key] = it.value.asString }
                } catch (_: Exception) {}
            }
            val referer = opts["referer"] ?: opts["referrer"]
            if (!referer.isNullOrBlank() && headers.keys.none { it.equals("Referer", true) }) {
                headers["Referer"] = referer
            }
            val ua = opts["userAgent"] ?: opts["ua"]
            if (!ua.isNullOrBlank() && headers.keys.none { it.equals("User-Agent", true) }) {
                headers["User-Agent"] = ua
            }
            val body = syncHttp("GET", url, null, mapOf(
                "headers" to gson.toJson(headers),
                "timeout" to (opts["timeout"] ?: "15000")
            ))
            val candidates = linkedSetOf<String>()
            val absolute = Regex("""https?://[^"'\\s<>]+(?:m3u8|mp4|m4v|mpd)(?:\?[^"'\\s<>]*)?""", RegexOption.IGNORE_CASE)
            absolute.findAll(body).forEach { candidates += it.value.trimEnd(')', ']', ',', ';') }
            val quoted = Regex("""["']([^"']+(?:m3u8|mp4|m4v|mpd)(?:\?[^"']*)?)["']""", RegexOption.IGNORE_CASE)
            quoted.findAll(body).forEach { match ->
                val raw = match.groupValues[1]
                try { candidates += java.net.URL(java.net.URL(url), raw).toString() } catch (_: Exception) {}
            }
            val picked = candidates.firstOrNull()
            val dynamic = if (picked == null) sniffMediaWebView(url, headers, opts) else null
            if (picked == null && dynamic == null) {
                result.addProperty("ok", false)
                result.addProperty("error", "media_url_not_found")
                result.addProperty("status", "static_sniff_only")
                SourceLogManager.error(sourceKey, "播放嗅探", "静态页面未找到媒体地址", url.take(240))
            } else {
                result.addProperty("ok", true)
                result.addProperty("url", picked)
                if (dynamic != null) result.addProperty("url", dynamic.url)
                result.addProperty("referer", referer ?: url)
                if (!ua.isNullOrBlank()) result.addProperty("ua", ua)
                if (dynamic != null && dynamic.headers.isNotEmpty()) headers.putAll(dynamic.headers)
                if (headers.isNotEmpty()) result.add("headers", JsonParser.parseString(gson.toJson(headers)))
            }
        } catch (e: Exception) {
            result.addProperty("ok", false)
            result.addProperty("error", e.message ?: "sniff_failed")
            SourceLogManager.error(sourceKey, "播放嗅探", "静态嗅探异常: ${e.message}", url.take(240))
        }
        return gson.toJson(result)
    }

    /**
     * Run dynamic player JavaScript in an off-screen WebView and intercept the
     * first media request. Static HTTP parsing cannot see URLs created by JS.
     */
    private fun sniffMediaWebView(
        url: String,
        headers: Map<String, String>,
        opts: Map<String, String>
    ): WebViewSniff? {
        val timeoutMs = (opts["timeout"]?.toLongOrNull() ?: 15000L).coerceIn(3000L, 30000L)
        val latch = CountDownLatch(1)
        val hit = AtomicReference<WebViewSniff?>(null)
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            val web = WebView(context.applicationContext)
            try {
                web.settings.javaScriptEnabled = true
                web.settings.domStorageEnabled = true
                web.settings.mediaPlaybackRequiresUserGesture = false
                val suppliedUa = opts["userAgent"] ?: opts["ua"]
                if (!suppliedUa.isNullOrBlank()) web.settings.userAgentString = suppliedUa
                val loadHeaders = headers.toMutableMap()
                val referer = opts["referer"] ?: opts["referrer"]
                if (!referer.isNullOrBlank() && loadHeaders.keys.none { it.equals("Referer", true) }) {
                    loadHeaders["Referer"] = referer
                }
                web.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                        val requestUrl = request.url.toString()
                        val lower = requestUrl.lowercase()
                        val media = lower.contains(".m3u8") || lower.contains(".mp4") ||
                            lower.contains(".m4v") || lower.contains(".flv") || lower.contains(".mpd")
                        if (media && hit.get() == null) {
                            hit.set(WebViewSniff(requestUrl, request.requestHeaders))
                            latch.countDown()
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                        if (request.isForMainFrame) latch.countDown()
                        super.onReceivedError(view, request, error)
                    }
                }
                web.loadUrl(url, loadHeaders)
                handler.postDelayed({
                    latch.countDown()
                    try { web.stopLoading(); web.destroy() } catch (_: Exception) {}
                }, timeoutMs)
            } catch (_: Exception) {
                try { web.destroy() } catch (_: Exception) {}
                latch.countDown()
            }
        }
        return try {
            latch.await(timeoutMs + 1500L, TimeUnit.MILLISECONDS)
            hit.get()
        } catch (_: InterruptedException) {
            null
        }
    }

    private data class WebViewSniff(val url: String, val headers: Map<String, String>)

    private fun syncHttp(method: String, url: String, body: String?, opts: Map<String, String>): String {
        val cleanUrl = decodeUnicodeEscapes(url)
        return try {
            val headers = mutableMapOf<String, String>()
            opts["headers"]?.let { h ->
                try {
                    val obj = JsonParser.parseString(h).asJsonObject
                    obj.entrySet().forEach { headers[it.key.lowercase()] = it.value.asString }
                } catch (_: Exception) {}
            }
            if (!headers.containsKey("user-agent")) headers["user-agent"] = "okhttp/3.15"
            // A few source servers emit malformed gzip/chunked responses when OkHttp
            // advertises transparent compression.  Request the wire body as-is so the
            // JS source receives a response instead of "Expected leading hex character".
            if (!headers.containsKey("accept-encoding")) headers["accept-encoding"] = "identity"

            val timeoutMs = opts["timeout"]?.toLongOrNull() ?: 3000L
            val requestClient = if (timeoutMs != 15000L) {
                client.newBuilder()
                    .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .build()
            } else {
                client
            }

            val request = Request.Builder().url(cleanUrl)
            headers.forEach { (k, v) ->
                val cleanV = v.replace("\r", "").replace("\n", "").trim()
                if (k.isNotBlank() && cleanV.isNotBlank()) {
                    try { request.addHeader(k, cleanV) } catch (_: Exception) {}
                }
            }

            if (method == "POST") {
                val mediaType = headers["content-type"] ?: "application/x-www-form-urlencoded"
                request.post((body ?: "").toRequestBody(mediaType.toMediaType()))
            } else {
                request.get()
            }

            val response = requestClient.newCall(request.build()).execute()
            response.body?.string() ?: ""
        } catch (e: Exception) {
            android.util.Log.e("QuickJsEngine", "HTTP $method $url failed: ${e.message}")
            ""
        }
    }

    private fun syncHttpBytes(method: String, url: String, bodyBytes: ByteArray?, opts: Map<String, String>): ByteArray {
        val cleanUrl = decodeUnicodeEscapes(url)
        return try {
            val headers = mutableMapOf<String, String>()
            opts["headers"]?.let { h ->
                try {
                    val obj = JsonParser.parseString(h).asJsonObject
                    obj.entrySet().forEach { headers[it.key.lowercase()] = it.value.asString }
                } catch (_: Exception) {}
            }
            if (!headers.containsKey("user-agent")) headers["user-agent"] = "okhttp/3.15"
            if (!headers.containsKey("accept-encoding")) headers["accept-encoding"] = "identity"

            val timeoutMs = opts["timeout"]?.toLongOrNull() ?: 3000L
            val requestClient = if (timeoutMs != 15000L) {
                client.newBuilder()
                    .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .build()
            } else {
                client
            }

            val request = Request.Builder().url(cleanUrl)
            headers.forEach { (k, v) ->
                val cleanV = v.replace("\r", "").replace("\n", "").trim()
                if (k.isNotBlank() && cleanV.isNotBlank()) {
                    try { request.addHeader(k, cleanV) } catch (_: Exception) {}
                }
            }

            if (method == "POST") {
                val mediaType = headers["content-type"] ?: "application/x-www-form-urlencoded"
                request.post((bodyBytes ?: ByteArray(0)).toRequestBody(mediaType.toMediaType()))
            } else {
                request.get()
            }

            val response = requestClient.newCall(request.build()).execute()
            response.body?.bytes() ?: ByteArray(0)
        } catch (e: Exception) {
            android.util.Log.e("QuickJsEngine", "HTTP $method $url failed: ${e.message}")
            ByteArray(0)
        }
    }

    private fun aesCrypt(encrypt: Boolean, plain: String, key: String, optsJson: String?): String {
        return try {
            val opts = parseOpts(optsJson)
            val mode = opts["mode"]?.uppercase() ?: "ECB"
            val keyFmt = opts["keyFormat"] ?: "utf8"
            val ivFmt = opts["ivFormat"] ?: "utf8"
            val inputFmt = opts["input"] ?: if (encrypt) "utf8" else "base64"
            val outputFmt = opts["output"] ?: if (encrypt) "base64" else "utf8"
            val padding = if ((opts["padding"] ?: "PKCS5") == "NoPadding") "NoPadding" else "PKCS5Padding"

            val inputBytes = toBytes(plain, inputFmt)
            val keyBytesList = mutableListOf<ByteArray>()

            // Try key formatted as specified (utf8/hex/base64)
            keyBytesList.add(toBytes(key, keyFmt))
            // If key is 32-char hex string, also try decoding as hex bytes
            if (key.length == 32 && key.all { it in "0123456789abcdefABCDEF" }) {
                try { keyBytesList.add(toBytes(key, "hex")) } catch (_: Exception) {}
            }

            var lastError: Exception? = null
            for (keyBytes in keyBytesList) {
                try {
                    val keyLen = when {
                        keyBytes.size >= 32 -> 32
                        keyBytes.size >= 24 -> 24
                        else -> 16
                    }
                    val keySpec = SecretKeySpec(keyBytes.take(keyLen).toByteArray(), "AES")
                    val algo = when (mode) {
                        "ECB" -> "AES/ECB/$padding"
                        "GCM" -> "AES/GCM/NoPadding"
                        else -> "AES/CBC/$padding"
                    }
                    val cipher = Cipher.getInstance(algo)
                    if (mode == "GCM") {
                        if (encrypt) {
                            var iv = (opts["iv"] ?: "").let { toBytes(it, ivFmt) }
                            if (iv.size < 12) iv = iv + ByteArray(12 - iv.size)
                            iv = iv.take(12).toByteArray()
                            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
                            return toOutput(iv + cipher.doFinal(inputBytes), outputFmt)
                        } else {
                            val iv = inputBytes.take(12).toByteArray()
                            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
                            return toOutput(cipher.doFinal(inputBytes.drop(12).toByteArray()), outputFmt)
                        }
                    } else {
                        val ivSpec = if (mode == "ECB") null
                        else IvParameterSpec(toBytes(opts["iv"] ?: "", ivFmt).let {
                            if (it.size < 16) it + ByteArray(16 - it.size) else it.take(16).toByteArray()
                        })
                        if (ivSpec != null) cipher.init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, keySpec, ivSpec)
                        else cipher.init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, keySpec)
                        return toOutput(cipher.doFinal(inputBytes), outputFmt)
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }
            if (lastError != null) {
                android.util.Log.e("AES", "aesCrypt failed for key: ${lastError.message}")
            }
            ""
        } catch (e: Exception) {
            android.util.Log.e("AES", "crypt failed: ${e.message}", e)
            ""
        }
    }

    private fun rsaCrypt(encrypt: Boolean, data: String, key: String, optsJson: String?): String {
        return try {
            val opts = parseOpts(optsJson)
            val inputFmt = opts["input"] ?: if (encrypt) "utf8" else "base64"
            val outputFmt = opts["output"] ?: if (encrypt) "base64" else "utf8"
            val bytes = toBytes(data, inputFmt)
            val cleanKey = key.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")

            val keyFactory = java.security.KeyFactory.getInstance("RSA")
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            val result = if (encrypt) {
                val pubKey = keyFactory.generatePublic(
                    java.security.spec.X509EncodedKeySpec(Base64.decode(cleanKey, Base64.DEFAULT))
                )
                cipher.init(Cipher.ENCRYPT_MODE, pubKey)
                cipher.doFinal(bytes)
            } else {
                val privKey = keyFactory.generatePrivate(
                    java.security.spec.PKCS8EncodedKeySpec(Base64.decode(cleanKey, Base64.DEFAULT))
                )
                cipher.init(Cipher.DECRYPT_MODE, privKey)
                cipher.doFinal(bytes)
            }
            toOutput(result, outputFmt)
        } catch (e: Exception) {
            android.util.Log.e("RSA", "crypt failed: ${e.message}", e)
            ""
        }
    }

    private fun inflateData(data: String, optsJson: String?): String {
        return try {
            val opts = parseOpts(optsJson)
            val inputFmt = opts["input"] ?: "base64"
            val outputFmt = opts["output"] ?: "utf8"
            val bytes = toBytes(data, inputFmt)
            val inflater = Inflater()
            inflater.setInput(bytes)
            val out = ByteArray(4096)
            val result = mutableListOf<Byte>()
            while (!inflater.finished()) {
                val len = inflater.inflate(out)
                if (len == 0) break
                for (i in 0 until len) {
                    result.add(out[i])
                }
            }
            inflater.end()
            toOutput(result.toByteArray(), outputFmt)
        } catch (_: Exception) {
            ""
        }
    }

    private fun hash(input: String, algo: String): String = digest(input, algo)

    private fun digest(input: String, algo: String): String {
        val md = MessageDigest.getInstance(algo)
        return md.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun hmacHash(input: String, key: String, algo: String): String {
        return try {
            val mac = Mac.getInstance(algo)
            mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), mac.algorithm))
            val result = mac.doFinal(input.toByteArray(Charsets.UTF_8))
            result.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) { "" }
    }

    private fun parseOpts(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val map = mutableMapOf<String, String>()
            val obj = JsonParser.parseString(json).asJsonObject
            obj.entrySet().forEach { map[it.key] = if (it.value.isJsonPrimitive) it.value.asString else it.value.toString() }
            map
        } catch (_: Exception) { emptyMap() }
    }

    private fun toBytes(data: String, fmt: String): ByteArray = when (fmt) {
        "hex" -> data.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        "base64" -> Base64.decode(data, Base64.DEFAULT)
        else -> data.toByteArray(Charsets.UTF_8)
    }

    private fun fromBytes(bytes: ByteArray, fmt: String): String = when (fmt) {
        "hex" -> bytes.joinToString("") { "%02x".format(it) }
        "base64" -> Base64.encodeToString(bytes, Base64.NO_WRAP)
        else -> String(bytes, Charsets.UTF_8)
    }

    private fun toOutput(bytes: ByteArray, fmt: String): String = fromBytes(bytes, fmt)

    fun close() {}
}

class SourceExports(private val sourceKey: String, private val gson: Gson) {

    @Volatile private var qjs: QuickJs? = null
    internal val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(null, runnable, "QuickJS-$sourceKey").apply { isDaemon = true }
    }

    @Synchronized
    fun setQuickJs(q: QuickJs) { this.qjs = q }

    private inline fun <T> evalSafe(crossinline block: QuickJs.() -> T): T {
        val instance = qjs ?: throw IllegalStateException("QuickJS not initialized for $sourceKey")
        val future = executor.submit(java.util.concurrent.Callable {
            instance.block()
        })
        return try {
            future.get(4, TimeUnit.SECONDS)
        } catch (e: Exception) {
            // Also cancel the underlying QuickJS/network task. Without this,
            // the single-thread source executor remains occupied after the UI
            // timeout and later requests queue behind a task nobody awaits.
            future.cancel(true)
            android.util.Log.w("QuickJsEngine", "[$sourceKey] evalSafe timeout or failed: ${e.message}")
            throw e
        }
    }

    private fun callFn(fnName: String, argsJs: String, fallback: String): String {
        return evalSafe {
            val expr = argsJs.ifBlank { "" }
            val js = if (expr.isNotEmpty()) {
                "typeof $fnName==='function'?$fnName($expr):'$fallback'"
            } else {
                "typeof $fnName==='function'?$fnName():'$fallback'"
            }
            try {
                qjs?.evaluate(js)?.toString() ?: fallback
            } catch (e: app.cash.quickjs.QuickJsException) {
                if (e.message?.contains("stack overflow") == true) {
                    android.util.Log.w("SourceExports", "[$sourceKey] QuickJS stack overflow in $fnName, returning $fallback")
                    fallback
                } else throw e
            }
        }
    }

    fun search(query: String, page: Int): String {
        val startTime = System.currentTimeMillis()
        return try {
            val q = gson.toJson(query)
            val res = callFn("search", "$q, $page", "[]")
            val duration = System.currentTimeMillis() - startTime
            if (res == "[]" || res.isBlank()) {
                com.juying.app.source.SourceLogManager.warn(sourceKey, "搜索", "搜索「$query」(页$page)未返回数据 (${duration}ms)")
            } else {
                com.juying.app.source.SourceLogManager.success(sourceKey, "搜索", "搜索「$query」(页$page)成功 (${duration}ms)", res.take(200))
            }
            res
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            android.util.Log.e("SourceExports", "[$sourceKey] search failed: ${e.message}", e)
            com.juying.app.source.SourceLogManager.error(sourceKey, "搜索", "搜索「$query」异常: ${e.message} (${duration}ms)", e.stackTraceToString().take(400))
            "[]"
        }
    }

    fun searchFiltered(category: String, filtersJson: String, page: Int): String {
        val startTime = System.currentTimeMillis()
        return try {
            val c = gson.toJson(category)
            val fJson = if (filtersJson.isBlank()) "{}" else filtersJson
            val res = callFn("searchFiltered", "$c, $fJson, $page", "[]")
            val duration = System.currentTimeMillis() - startTime
            if (res == "[]" || res.isBlank()) {
                com.juying.app.source.SourceLogManager.warn(sourceKey, "筛选", "分类「$category」无数据 (${duration}ms)")
            } else {
                com.juying.app.source.SourceLogManager.success(sourceKey, "筛选", "分类「$category」成功 (${duration}ms)", res.take(200))
            }
            res
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            android.util.Log.e("SourceExports", "searchFiltered failed: ${e.message}", e)
            com.juying.app.source.SourceLogManager.error(sourceKey, "筛选", "分类「$category」异常: ${e.message} (${duration}ms)")
            "[]"
        }
    }

    fun homeSections(): String {
        val startTime = System.currentTimeMillis()
        return try {
            val res = callFn("homeSections", "", "[]")
            val duration = System.currentTimeMillis() - startTime
            if (res == "[]" || res.isBlank()) {
                com.juying.app.source.SourceLogManager.warn(sourceKey, "首页", "首页分区无数据 (${duration}ms)")
            } else {
                com.juying.app.source.SourceLogManager.success(sourceKey, "首页", "首页分区成功 (${duration}ms)", res.take(200))
            }
            res
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            android.util.Log.e("SourceExports", "homeSections failed: ${e.message}", e)
            com.juying.app.source.SourceLogManager.error(sourceKey, "首页", "首页分区异常: ${e.message} (${duration}ms)")
            "[]"
        }
    }

    fun detail(id: String): String {
        val startTime = System.currentTimeMillis()
        return try {
            val i = gson.toJson(id)
            val res = callFn("detail", "$i", "{}")
            val duration = System.currentTimeMillis() - startTime
            com.juying.app.source.SourceLogManager.success(sourceKey, "详情", "获取详情 $id 成功 (${duration}ms)", res.take(200))
            res
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            android.util.Log.e("SourceExports", "detail failed: ${e.message}", e)
            com.juying.app.source.SourceLogManager.error(sourceKey, "详情", "获取详情 $id 异常: ${e.message} (${duration}ms)")
            "{}"
        }
    }

    fun related(id: String): String {
        val startTime = System.currentTimeMillis()
        return try {
            val i = gson.toJson(id)
            val res = callFn("related", i, "[]")
            val duration = System.currentTimeMillis() - startTime
            if (res == "[]" || res.isBlank()) {
                com.juying.app.source.SourceLogManager.warn(sourceKey, "相关推荐", "作品 $id 暂无相关推荐 (${duration}ms)")
            } else {
                com.juying.app.source.SourceLogManager.success(sourceKey, "相关推荐", "作品 $id 获取成功 (${duration}ms)", res.take(200))
            }
            res
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            android.util.Log.e("SourceExports", "related failed: ${e.message}", e)
            com.juying.app.source.SourceLogManager.error(sourceKey, "相关推荐", "作品 $id 获取异常: ${e.message} (${duration}ms)")
            "[]"
        }
    }

    fun play(flagJson: String): String {
        val startTime = System.currentTimeMillis()
        return try {
            // SourceAdapter already JSON-encodes primitive string flags. Do
            // not encode a JSON string literal a second time; that turns a
            // valid player URL into a URL containing leading quote characters.
            val expr = when {
                flagJson.isBlank() -> "\"\""
                flagJson.startsWith("{") || flagJson.startsWith("[") || flagJson.startsWith("\"") -> flagJson
                else -> gson.toJson(flagJson)
            }
            val res = callFn("play", expr, "{}")
            val duration = System.currentTimeMillis() - startTime
            com.juying.app.source.SourceLogManager.success(sourceKey, "播放解析", "解析成功 (${duration}ms)", res)
            res
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            android.util.Log.e("SourceExports", "play failed: ${e.message}", e)
            com.juying.app.source.SourceLogManager.error(sourceKey, "播放解析", "解析异常: ${e.message} (${duration}ms)")
            "{}"
        }
    }

    fun close() {
        try {
            executor.submit {
                synchronized(this@SourceExports) {
                    qjs?.close()
                    qjs = null
                }
            }.get(5, TimeUnit.SECONDS)
        } catch (_: Exception) {}
        try { executor.shutdownNow() } catch (_: Exception) {}
    }
}
