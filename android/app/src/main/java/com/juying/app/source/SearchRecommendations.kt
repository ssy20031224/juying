package com.juying.app.source

import kotlin.math.ln

/**
 * Builds device-local search recommendations from real search frequency,
 * recency and watch metadata. No profile is uploaded and no fabricated heat
 * value is attached to a card.
 */
fun buildSearchRecommendations(
    searchHistory: List<SearchHistoryEntry>,
    watchHistory: List<HistoryItem>,
    candidates: List<SourceItem>,
    limit: Int = 6,
    rotationSeed: Int = 0
): List<SourceItem> {
    if (limit <= 0) return emptyList()
    val uniqueCandidates = candidates
        .filter { it.title.isNotBlank() }
        .distinctBy { normalizeRecommendationText(it.title) }
    if (uniqueCandidates.isEmpty()) return emptyList()

    val searchedTitles = searchHistory
        .mapTo(HashSet()) { normalizeRecommendationText(it.query) }
    val now = System.currentTimeMillis()
    val watchedSignals = watchHistory.take(12)
        .flatMap { itemSignals(it.item) }
        .toSet()

    val ranked = uniqueCandidates.mapNotNull { candidate ->
        val normalizedTitle = normalizeRecommendationText(candidate.title)
        if (normalizedTitle.isBlank() || normalizedTitle in searchedTitles) return@mapNotNull null
        val signals = itemSignals(candidate)
        var score = 0.0

        searchHistory.take(10).forEachIndexed { index, history ->
            val query = normalizeRecommendationText(history.query)
            if (query.isBlank()) return@forEachIndexed
            val ageDays = ((now - history.lastSearchedAt).coerceAtLeast(0L) / 86_400_000.0)
            val recency = 1.0 / (1.0 + ageDays / 14.0)
            val frequency = 1.0 + ln(history.count.coerceAtLeast(1).toDouble())
            val positionWeight = 1.0 / (1.0 + index * 0.16)
            val weight = recency * frequency * positionWeight
            val querySignals = textSignals(history.query)
            val overlap = querySignals.intersect(signals).size
            if (normalizedTitle.contains(query) || query.contains(normalizedTitle)) score += 150.0 * weight
            score += overlap * 28.0 * weight
            candidate.tags.forEach { tag ->
                val normalizedTag = normalizeRecommendationText(tag)
                if (normalizedTag.isNotBlank() && query.contains(normalizedTag)) score += 36.0 * weight
            }
        }

        if (watchedSignals.isNotEmpty()) {
            score += watchedSignals.intersect(signals).size * 8.0
        }
        score += (candidate.score.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.0) * 0.35
        candidate to score
    }

    val related = ranked
        .filter { it.second > 0.0 }
        .sortedWith(
            compareByDescending<Pair<SourceItem, Double>> { it.second }
                .thenBy { normalizeRecommendationText(it.first.title) }
        )
        .map(Pair<SourceItem, Double>::first)

    // With no usable personal signal, rotate a stable, source-diverse fallback
    // instead of repeatedly returning the first card in the same pool.
    val fallback = ranked
        .sortedWith(
            compareBy<Pair<SourceItem, Double>> {
                stableRotationKey(it.first, rotationSeed)
            }.thenBy { normalizeRecommendationText(it.first.title) }
        )
        .map(Pair<SourceItem, Double>::first)

    val combined = (related + fallback)
        .distinctBy { normalizeRecommendationText(it.title) }
        .take(limit)

    // "换一批"：按 seed 整体轮转展示顺序（related 存在时其顺序固定，
    // 不轮转的话每次点击结果完全一样，用户感知为"无反应"）。
    if (combined.size <= 1) return combined
    val offset = (rotationSeed % combined.size + combined.size) % combined.size
    return combined.drop(offset) + combined.take(offset)
}

private fun itemSignals(item: SourceItem): Set<String> = textSignals(
    listOf(item.title, item.kind, item.tags.joinToString(" "), item.description)
        .joinToString(" ")
)

private fun textSignals(value: String): Set<String> {
    val normalized = normalizeRecommendationText(value)
    if (normalized.isBlank()) return emptySet()
    val chunks = value.lowercase()
        .split(Regex("[\\s,，/|·・:：!！?？.。\\-—_()（）\\[\\]【】]+"))
        .map(::normalizeRecommendationText)
        .filter { it.length >= 2 }
    return buildSet {
        add(normalized)
        chunks.forEach { chunk ->
            add(chunk)
            if (chunk.length >= 3) {
                for (index in 0 until chunk.length - 1) add(chunk.substring(index, index + 2))
            }
        }
    }
}

private fun normalizeRecommendationText(value: String): String = value.lowercase()
    .replace(Regex("[\\s,，/|·・:：!！?？.。\\-—_()（）\\[\\]【】]+"), "")
    .trim()

private fun stableRotationKey(item: SourceItem, seed: Int): Int {
    val value = "${item.sourceKey}:${item.id}:${normalizeRecommendationText(item.title)}:$seed"
    return value.hashCode().xor(seed * 1103515245)
}
