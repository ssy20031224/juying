package com.juying.app.source

import android.content.Context
import com.juying.app.engine.NetworkClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Supplies non-playback metadata that many video sources omit. Results are
 * accepted only when the returned Chinese/original title matches the requested
 * title, so fuzzy API results cannot silently relabel an unrelated video.
 */
class AnimeMetadataRepository(context: Context) {
    private val client = NetworkClient.create(context).newBuilder().cache(null).build()
    private val memoryCache = ConcurrentHashMap<String, AnimeMetadata>()

    fun lookup(title: String): AnimeMetadata? {
        val key = normalizeDiscoveryTitle(title)
        if (key.isBlank()) return null
        memoryCache[key]?.let { return it }

        val payload = JSONObject()
            .put("keyword", title.trim())
            .put("filter", JSONObject().put("type", org.json.JSONArray().put(2)))
            .toString()
            .toRequestBody(JSON)
        val request = Request.Builder()
            .url("https://api.bgm.tv/v0/search/subjects")
            .header("User-Agent", "Juying/1.2 (Android anime metadata enrichment)")
            .header("Accept", "application/json")
            .post(payload)
            .build()

        val result = runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val data = JSONObject(response.body?.string().orEmpty()).optJSONArray("data")
                    ?: return@use null
                (0 until minOf(data.length(), 12))
                    .mapNotNull { index -> data.optJSONObject(index) }
                    .mapNotNull { subject ->
                        val nameCn = subject.optString("name_cn")
                        val originalName = subject.optString("name")
                        val match = maxOf(
                            metadataTitleMatch(key, normalizeDiscoveryTitle(nameCn)),
                            metadataTitleMatch(key, normalizeDiscoveryTitle(originalName))
                        )
                        if (match <= 0) return@mapNotNull null

                        val tags = buildList {
                            subject.optJSONArray("meta_tags")?.let { values ->
                                repeat(values.length()) { add(values.optString(it)) }
                            }
                            subject.optJSONArray("tags")?.let { values ->
                                repeat(minOf(values.length(), 30)) {
                                    values.optJSONObject(it)?.optString("name")
                                        ?.takeIf(String::isNotBlank)
                                        ?.let(::add)
                                }
                            }
                            subject.optString("platform").takeIf(String::isNotBlank)?.let(::add)
                        }.distinct()
                        val score = subject.optJSONObject("rating")
                            ?.optDouble("score", 0.0)
                            ?.takeIf { it > 0.0 }
                            ?.let { String.format(java.util.Locale.US, "%.1f", it) }
                            .orEmpty()
                        val year = subject.optString("date").take(4)
                            .takeIf { it.matches(Regex("\\d{4}")) }
                            .orEmpty()
                        match to AnimeMetadata(
                            score = score,
                            year = year,
                            platform = subject.optString("platform"),
                            tags = tags
                        )
                    }
                    .maxByOrNull { it.first }
                    ?.second
            }
        }.getOrNull()

        result?.let { memoryCache[key] = it }
        return result
    }

    private fun metadataTitleMatch(expected: String, candidate: String): Int {
        if (candidate.isBlank()) return 0
        if (expected == candidate) return 100
        val shorter = minOf(expected.length, candidate.length)
        val difference = kotlin.math.abs(expected.length - candidate.length)
        return if (shorter >= 5 && difference <= 4 &&
            (expected.contains(candidate) || candidate.contains(expected))) {
            80 - difference
        } else 0
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

data class AnimeMetadata(
    val score: String,
    val year: String,
    val platform: String,
    val tags: List<String>
)
