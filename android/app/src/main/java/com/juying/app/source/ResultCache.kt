package com.juying.app.source

import android.util.LruCache

/**
 * Multi-level in-memory result cache with per-source adaptive TTLs.
 *
 * ## Source-specific Play URL analysis (from JS reverse engineering):
 *
 * | Source      | Play flow                                           | Resolved URL type     | Safe cache TTL |
 * |-------------|-----------------------------------------------------|-----------------------|----------------|
 * | AuvFun      | /episode/jx → resolutionList[]{url}                 | Direct CDN m3u8/mp4   | 10 min         |
 * | lanerc      | Discovery probe → /api/vod/play → direct URL        | Direct CDN m3u8       | 5 min (host changes) |
 * | gugu        | AES flag → vodParse POST → data.json.url            | Third-party parser URL| 10 min         |
 * | guazi       | RSA+AES → /VurlDetail/showOne → {url}               | Direct CDN m3u8       | 10 min         |
 * | shuangxing  | AES-256+zlib+SHA256 → /vodParser → direct URL       | Direct CDN m3u8       | 10 min         |
 * | shutiao     | RSA handshake → /videoUsableUrl → {playUrl,headers} | Direct CDN URL        | 10 min         |
 * | jinpai      | Token auth → resolve → direct URL                  | Direct CDN m3u8       | 10 min         |
 * | dmbus       | Direct or iframe parser URL                         | Third-party parser    | 5 min          |
 * | akianime    | Standard API → direct URL                          | Direct CDN m3u8       | 10 min         |
 * | lmm85       | Standard API → direct URL                          | Direct CDN m3u8       | 10 min         |
 * | sanqiu      | Standard API → direct URL                          | Direct CDN m3u8       | 10 min         |
 * | xifanacg    | Standard API → direct URL                          | Direct CDN m3u8       | 10 min         |
 * | yzx         | Standard API → direct URL                          | Direct CDN m3u8       | 10 min         |
 * | cycapp      | Standard API → direct URL                          | Direct CDN m3u8       | 10 min         |
 *
 * CDN signed URLs (most sources) are typically valid 1–6 hours, so 10-minute cache is conservative & safe.
 * Sources with discovery-based hosts (lanerc, dmbus) use shorter 5-min TTL since host may rotate.
 *
 * Thread-safe: LruCache is synchronized; all public methods safe from any thread.
 */
object ResultCache {

    // ── TTL constants ──────────────────────────────────────────────────────────
    private const val SEARCH_TTL      = 5  * 60_000L   // 5 min
    private const val DETAIL_TTL      = 30 * 60_000L   // 30 min
    private const val PLAY_TTL        = 10 * 60_000L   // 10 min  (default)
    private const val PLAY_TTL_SHORT  = 5  * 60_000L   // 5 min   (discovery/proxy sources)
    private const val HOME_TTL        = 15 * 60_000L   // 15 min

    // Sources that use host-discovery or third-party proxies — shorter play URL TTL
    private val SHORT_PLAY_TTL_SOURCES = setOf("lanerc", "dmbus")

    // ── Cache entry wrapper ────────────────────────────────────────────────────
    private data class Entry<T>(val value: T, val ts: Long = System.currentTimeMillis()) {
        fun fresh(ttl: Long) = System.currentTimeMillis() - ts < ttl
    }

    // ── LRU caches ────────────────────────────────────────────────────────────
    // Search: keyed by "search:<keyword>:<activeSource>"
    private val searchCache = object : LruCache<String, Entry<List<SourceItem>>>(40) {}
    // Detail: keyed by "<sourceKey>:<itemId>"
    private val detailCache = object : LruCache<String, Entry<DetailResult>>(60) {}
    // Play URL: keyed by "<sourceKey>:<flagStr.take(200)>"
    private val playCache   = object : LruCache<String, Entry<PlayResult>>(120) {}
    // Home sections per adapter: keyed by "<sourceKey>"
    private val homeCache   = object : LruCache<String, Entry<List<HomeSection>>>(14) {}

    // ── Search ────────────────────────────────────────────────────────────────
    fun getSearch(key: String): List<SourceItem>? =
        searchCache[key]?.takeIf { it.fresh(SEARCH_TTL) }?.value

    fun putSearch(key: String, items: List<SourceItem>) {
        if (items.isNotEmpty()) searchCache.put(key, Entry(items))
    }

    // ── Detail ────────────────────────────────────────────────────────────────
    fun getDetail(key: String): DetailResult? =
        detailCache[key]?.takeIf { it.fresh(DETAIL_TTL) }?.value

    fun putDetail(key: String, result: DetailResult) {
        if (result.episodes.isNotEmpty()) detailCache.put(key, Entry(result))
    }

    // ── Play URL (source-aware TTL) ────────────────────────────────────────────
    /**
     * Retrieves a cached play result for the given key.
     * @param key Format: "<sourceKey>:<flagStr.take(200)>"
     */
    fun getPlay(key: String): PlayResult? {
        val entry = playCache[key] ?: return null
        // Determine TTL based on source key prefix
        val sourceKey = key.substringBefore(':')
        val ttl = if (sourceKey in SHORT_PLAY_TTL_SOURCES) PLAY_TTL_SHORT else PLAY_TTL
        return entry.takeIf { it.fresh(ttl) }?.value
    }

    /**
     * Also checks if the URL itself contains an embedded expiry timestamp that has already passed.
     * Handles patterns like: ?expires=1234567890, &deadline=..., &sign_time=...&t=...
     */
    fun putPlay(key: String, result: PlayResult) {
        // Signed CDN URLs are commonly valid for minutes, not the generic
        // ten-minute cache TTL. Re-resolve them instead of replaying a stale
        // auth_key/token URL that intermittently returns 403/404.
        if (result.url.isNotEmpty() && !isUrlExpired(result.url) && !isVolatileSignedUrl(result.url)) {
            playCache.put(key, Entry(result))
        }
    }

    fun invalidatePlay(key: String) { playCache.remove(key) }

    // ── Home (per source) ─────────────────────────────────────────────────────
    fun getHome(sourceKey: String): List<HomeSection>? =
        homeCache[sourceKey]?.takeIf { it.fresh(HOME_TTL) }?.value

    fun putHome(sourceKey: String, sections: List<HomeSection>) {
        if (sections.isNotEmpty()) homeCache.put(sourceKey, Entry(sections))
    }

    // ── URL expiry detection ──────────────────────────────────────────────────
    /**
     * Checks if a CDN URL has an embedded expiry timestamp that is already past.
     * Handles common signing patterns used by Chinese CDN providers:
     *   - Tencent COS:  ?sign=xxx (no inline expiry, managed server-side)
     *   - Alibaba CDN:  ?auth_key=<timestamp>-<rand>-<uid>-<md5>
     *   - qiniu CDN:    ?e=<unix_timestamp>  or  ?expires=<unix_timestamp>
     *   - Generic:      ?deadline=<unix_timestamp>, ?t=<unix_timestamp>
     */
    private fun isUrlExpired(url: String): Boolean {
        try {
            val nowSec = System.currentTimeMillis() / 1000
            // Pattern: ?e=<epoch> or &e=<epoch> (qiniu)
            val eMatch = Regex("[?&]e=(\\d{9,11})").find(url)
            if (eMatch != null) {
                val exp = eMatch.groupValues[1].toLongOrNull() ?: return false
                return nowSec > exp
            }
            // Pattern: ?expires=<epoch> or &expires=<epoch>
            val expMatch = Regex("[?&]expires=(\\d{9,11})").find(url)
            if (expMatch != null) {
                val exp = expMatch.groupValues[1].toLongOrNull() ?: return false
                return nowSec > exp
            }
            // Pattern: ?deadline=<epoch> or &deadline=<epoch>
            val dlMatch = Regex("[?&]deadline=(\\d{9,11})").find(url)
            if (dlMatch != null) {
                val exp = dlMatch.groupValues[1].toLongOrNull() ?: return false
                return nowSec > exp
            }
            // Alibaba auth_key: ?auth_key=<timestamp>-...
            val authMatch = Regex("[?&]auth_key=(\\d{9,11})-").find(url)
            if (authMatch != null) {
                val exp = authMatch.groupValues[1].toLongOrNull() ?: return false
                // auth_key timestamp is issue time; typical TTL is 1-24 hours, assume 2 hours safe
                return nowSec > (exp + 7200)
            }
        } catch (_: Exception) { /* ignore parse errors */ }
        return false
    }

    private fun isVolatileSignedUrl(url: String): Boolean {
        val query = url.substringAfter('?', "").lowercase()
        if (query.isBlank()) return false
        return Regex("(^|&)(auth_key|expires|expire|deadline|e|token|sign|signature|playtoken|t)=")
            .containsMatchIn(query)
    }

    // ── Utility ───────────────────────────────────────────────────────────────
    fun clear() {
        searchCache.evictAll()
        detailCache.evictAll()
        playCache.evictAll()
        homeCache.evictAll()
    }
}
