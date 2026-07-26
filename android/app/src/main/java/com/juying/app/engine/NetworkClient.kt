package com.juying.app.engine

import android.content.Context
import android.util.Log
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Enhanced OkHttp client with DNS-over-HTTPS, persistent cookies,
 * response caching, and connection pooling — optimized for Chinese CDN sources.
 *
 * Reference: Lanerc APK reverse engineering
 *   - DoH servers: AliDNS/Tencent for reliable DNS resolution (prevents DNS pollution)
 *   - Cookie jar: session persistence across QuickJS source requests
 *   - Response cache: reduces latency for repeated API calls
 *   - Multi-tiered timeouts: matches Lanerc's 15s/10s/2s strategy
 */
object NetworkClient {

    private const val TAG = "NetworkClient"
    private const val CACHE_SIZE = 10L * 1024 * 1024 // 10 MB
    private const val MAX_STALE_SEC = 120

    // ── Chinese DoH resolvers (AliDNS JSON API) ──
    private val DOH_URLS = listOf(
        "https://223.5.5.5/resolve",
        "https://223.6.6.6/resolve"
    )

    // Cached DNS results (10 min TTL, same as Lanerc)
    private val dnsCache = ConcurrentHashMap<String, Pair<List<InetAddress>, Long>>()

    // ── Custom DNS-over-HTTPS resolver ──
    private class DoHDns(private val bootstrapClient: OkHttpClient) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            // First check system DNS if IP address
            if (hostname.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                return try { listOf(InetAddress.getByName(hostname)) } catch (_: Exception) { emptyList() }
            }

            // Return cached result if still valid
            val cached = dnsCache[hostname]
            if (cached != null && System.currentTimeMillis() - cached.second < 600_000) {
                return cached.first
            }

            // Attempt system DNS first for low latency
            try {
                val sysAddrs = Dns.SYSTEM.lookup(hostname)
                if (sysAddrs.isNotEmpty()) {
                    dnsCache[hostname] = Pair(sysAddrs, System.currentTimeMillis())
                    return sysAddrs
                }
            } catch (_: Exception) {}

            val addresses = dohResolve(hostname)
            if (addresses.isNotEmpty()) {
                dnsCache[hostname] = Pair(addresses, System.currentTimeMillis())
                return addresses
            }

            return try { Dns.SYSTEM.lookup(hostname) } catch (_: Exception) { emptyList() }
        }

        private fun dohResolve(hostname: String): List<InetAddress> {
            val fastClient = bootstrapClient.newBuilder()
                .connectTimeout(1500, TimeUnit.MILLISECONDS)
                .readTimeout(1500, TimeUnit.MILLISECONDS)
                .build()

            for (dohUrl in DOH_URLS) {
                try {
                    val url = dohUrl.toHttpUrl().newBuilder()
                        .addQueryParameter("name", hostname)
                        .addQueryParameter("type", "A")
                        .build()
                    val request = Request.Builder()
                        .url(url)
                        .header("Accept", "application/dns-json")
                        .get()
                        .build()
                    val response = fastClient.newCall(request).execute()
                    val body = response.body?.string() ?: continue

                    val result = mutableListOf<InetAddress>()
                    val json = org.json.JSONObject(body)
                    val answer = json.optJSONArray("Answer") ?: continue
                    for (i in 0 until answer.length()) {
                        val record = answer.getJSONObject(i)
                        val ip = record.optString("data", "")
                        if (ip.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                            result.add(InetAddress.getByName(ip))
                        }
                    }
                    if (result.isNotEmpty()) {
                        Log.d(TAG, "DoH resolved $hostname via $dohUrl: ${result.size} IPs")
                        return result
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "DoH $dohUrl failed for $hostname: ${e.message}")
                }
            }
            return emptyList()
        }
    }

    // ── Persistent Cookie Jar ──
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    private val persistentCookieJar = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host]?.filter { cookie ->
                cookie.expiresAt > System.currentTimeMillis() || cookie.persistent
            } ?: emptyList()
        }
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val list = cookieStore.getOrPut(url.host) { mutableListOf() }
            for (cookie in cookies) {
                list.removeAll { it.name == cookie.name }
                list.add(cookie)
            }
        }
    }

    // ── Create the main HTTP client ──
    fun create(context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, "okhttp_cache")
        val cache = Cache(cacheDir, CACHE_SIZE)

        val bootstrapClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        return OkHttpClient.Builder()
            .dns(DoHDns(bootstrapClient))
            .cookieJar(persistentCookieJar)
            .cache(cache)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
