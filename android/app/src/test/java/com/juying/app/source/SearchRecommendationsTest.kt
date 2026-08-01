package com.juying.app.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRecommendationsTest {
    @Test
    fun `recent frequent searches rank related tags before generic pool`() {
        val now = System.currentTimeMillis()
        val result = buildSearchRecommendations(
            searchHistory = listOf(
                SearchHistoryEntry("异世界冒险", count = 5, lastSearchedAt = now),
                SearchHistoryEntry("恋爱", count = 1, lastSearchedAt = now - 86_400_000L)
            ),
            watchHistory = emptyList(),
            candidates = listOf(
                SourceItem(title = "普通校园", tags = listOf("校园")),
                SourceItem(title = "异世界勇者", tags = listOf("异世界", "冒险")),
                SourceItem(title = "恋爱物语", tags = listOf("恋爱"))
            ),
            limit = 3
        )

        assertEquals("异世界勇者", result.first().title)
        assertTrue(result.none { it.title == "异世界冒险" })
    }

    @Test
    fun `no history fallback rotates instead of pinning one card forever`() {
        val candidates = (1..12).map { SourceItem(id = "$it", title = "作品$it", sourceKey = "s") }
        val first = buildSearchRecommendations(emptyList(), emptyList(), candidates, 6, rotationSeed = 1)
        val second = buildSearchRecommendations(emptyList(), emptyList(), candidates, 6, rotationSeed = 2)

        assertNotEquals(first.map { it.title }, second.map { it.title })
    }
}
