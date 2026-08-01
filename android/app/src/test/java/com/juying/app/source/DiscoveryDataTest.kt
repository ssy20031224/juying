package com.juying.app.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

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

    @Test
    fun `beijing july window contains current year july and april only`() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply {
            clear()
            set(2026, Calendar.JULY, 30, 12, 0, 0)
        }

        val seasons = beijingSeasonWindow(calendar.timeInMillis)

        assertEquals(
            listOf(AnimeSeason(2026, 7), AnimeSeason(2026, 4)),
            seasons
        )
        assertTrue(seasons.none { it.month == 10 || it.year != 2026 })
    }

    @Test
    fun `beijing january window never imports previous year october`() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply {
            clear()
            set(2026, Calendar.JANUARY, 10, 12, 0, 0)
        }

        assertEquals(
            listOf(AnimeSeason(2026, 1)),
            beijingSeasonWindow(calendar.timeInMillis)
        )
    }

    @Test
    fun `season label parser supports chinese and numeric month names`() {
        assertEquals(4, seasonMonthFromLabel("四月新番"))
        assertEquals(7, seasonMonthFromLabel("2026年7月新番"))
        assertEquals(10, seasonMonthFromLabel("十月新番"))
    }

    @Test
    fun `anime ranking categories distinguish region and theatrical format`() {
        val japaneseTv = SourceItem(title = "日漫TV", kind = "日漫 TV")
        val japaneseMovie = SourceItem(title = "日漫电影", tags = listOf("日漫", "剧场版"))
        val chineseTv = SourceItem(title = "国漫TV", kind = "国产动画")
        val chineseMovie = SourceItem(title = "国漫电影", tags = listOf("国漫", "动画电影"))
        val sections = listOf(
            HomeSection(
                title = "来源热门",
                key = "hot",
                items = listOf(japaneseTv, japaneseMovie, chineseTv, chineseMovie)
            )
        )

        AnimeRankingCategory.entries.forEach { category ->
            val result = buildAnimeCategoryRanking(emptyList(), sections, category)
            assertEquals(1, result.size)
        }
        assertEquals(
            "日漫TV",
            buildAnimeCategoryRanking(
                emptyList(),
                sections,
                AnimeRankingCategory.JAPANESE_TV
            ).single().item.title
        )
        assertEquals(
            "国漫电影",
            buildAnimeCategoryRanking(
                emptyList(),
                sections,
                AnimeRankingCategory.CHINESE_MOVIE
            ).single().item.title
        )
    }

    @Test
    fun `category search label cannot relabel sparse or conflicting metadata`() {
        val sparseMovie = SourceItem(title = "只有标题的动画电影", kind = "动漫")
        val entry = SourceRankingEntry(
            item = sparseMovie,
            sourceSection = "国漫剧场版 · 测试源",
            sourcePosition = 1
        )

        assertTrue(
            buildAnimeCategoryRanking(
                rankedEntries = listOf(entry.copy(sourceSection = "搜索:国漫剧场版 · 测试源")),
                sections = emptyList(),
                category = AnimeRankingCategory.CHINESE_MOVIE
            ).isEmpty()
        )

        val japaneseDrama = SourceItem(title = "日本电视剧", kind = "日剧 真人版")
        assertTrue(
            matchesAnimeRankingCategory(
                japaneseDrama,
                "日漫TV番剧",
                AnimeRankingCategory.JAPANESE_TV
            ).not()
        )
    }
}
