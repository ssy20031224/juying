package com.juying.app.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceManagerTitleTest {
    @Test
    fun displaySuffixDoesNotSplitTheSameWorkAcrossSources() {
        val plain = SourceManager.normalizeTitle("刚毕业就末日：万亿开局当神豪")
        val dynamicComic = SourceManager.normalizeTitle("刚毕业就末日：万亿开局当神豪 动态漫画")
        val seasonSuffix = SourceManager.normalizeTitle("刚毕业就末日：万亿开局当神豪（动态漫画第1季）")

        assertEquals(plain, dynamicComic)
        assertEquals(plain, seasonSuffix)
        assertTrue(
            SourceManager.titlesLikelyMatch(
                "刚毕业就末日：万亿开局当神豪 动态漫画",
                "刚毕业就末日：万亿开局当神豪"
            )
        )
        assertEquals(
            listOf(
                "刚毕业就末日：万亿开局当神豪 动态漫画",
                "刚毕业就末日：万亿开局当神豪"
            ),
            SourceManager.detailSearchVariants("刚毕业就末日：万亿开局当神豪 动态漫画")
        )
        assertFalse(SourceManager.titlesLikelyMatch("斗罗大陆", "斗罗大陆外传神界传说"))
    }

    @Test
    fun identifiesKnownTranscodingPlaceholderPathsWithoutReadingSignedQuery() {
        assertTrue(isLikelyTranscodingPlaceholderUrl("https://cdn.example.com/loading/wait.mp4?token=secret"))
        assertTrue(isLikelyTranscodingPlaceholderUrl("https://cdn.example.com/media/transcoding/index.m3u8"))
        assertFalse(isLikelyTranscodingPlaceholderUrl("https://cdn.example.com/vod/index.m3u8?wait=token"))
    }
}
