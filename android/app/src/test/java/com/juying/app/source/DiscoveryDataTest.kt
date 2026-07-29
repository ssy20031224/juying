package com.juying.app.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryDataTest {
    @Test
    fun `schedule only uses explicit weekday and time`() {
        val result = buildScheduleEntries(
            listOf(
                SourceItem(id = "1", title = "A", status = "更新至10集|周三22:10"),
                SourceItem(id = "2", title = "B", status = "连载中"),
                SourceItem(id = "3", title = "C", status = "周五 已完结")
            )
        )

        assertEquals(1, result.size)
        assertEquals(2, result.single().weekdayIndex)
        assertEquals("22:10", result.single().airTime)
        assertEquals("更新至10集", result.single().episodeText)
    }

    @Test
    fun `hot ranking preserves source section order without inventing metrics`() {
        val first = SourceItem(id = "1", title = "第一")
        val second = SourceItem(id = "2", title = "第二")
        val result = buildRankingEntries(
            sections = listOf(HomeSection("热门动漫", "", items = listOf(first, second))),
            allItems = emptyList(),
            kind = RankingKind.HOT
        )

        assertEquals(listOf("第一", "第二"), result.map { it.item.title })
        assertEquals(listOf(1, 2), result.map { it.sourcePosition })
    }

    @Test
    fun `score ranking requires a real numeric score`() {
        val result = buildRankingEntries(
            sections = emptyList(),
            allItems = listOf(
                SourceItem(id = "1", title = "低分", score = "7.2"),
                SourceItem(id = "2", title = "无分"),
                SourceItem(id = "3", title = "高分", score = "9.4")
            ),
            kind = RankingKind.SCORE
        )

        assertEquals(listOf("高分", "低分"), result.map { it.item.title })
        assertTrue(result.all { it.item.score.isNotBlank() })
    }
}
