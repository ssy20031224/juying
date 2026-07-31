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

        assertEquals(plain, dynamicComic)
    }

    @Test
    fun identifiesKnownTranscodingPlaceholderPathsWithoutReadingSignedQuery() {
        assertTrue(isLikelyTranscodingPlaceholderUrl("https://cdn.example.com/loading/wait.mp4?token=secret"))
        assertTrue(isLikelyTranscodingPlaceholderUrl("https://cdn.example.com/media/transcoding/index.m3u8"))
        assertFalse(isLikelyTranscodingPlaceholderUrl("https://cdn.example.com/vod/index.m3u8?wait=token"))
    }
}
