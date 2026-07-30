package com.juying.app.source

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaStatusResolverTest {
    @Test
    fun `updating wins over generic serializing marker`() {
        val result = resolveMediaStatus(SourceItem(status = "连载中 更新至12集"))

        assertEquals(MediaReleaseState.UPDATING, result.state)
        assertEquals("更新至12集", result.episodeText)
    }

    @Test
    fun completedMarkersTakePriorityOverUpdateText() {
        val result = resolveMediaStatus(
            SourceItem(status = "更新至12集 已完结"),
            episodeCount = 12
        )

        assertEquals(MediaReleaseState.FINISHED, result.state)
        assertEquals("全12集", result.episodeText)
    }

    @Test
    fun fullEpisodeCountIsCompleted() {
        val result = resolveMediaStatus(SourceItem(status = "全24集"))

        assertEquals(MediaReleaseState.FINISHED, result.state)
        assertEquals("全24集", result.episodeText)
    }

    @Test
    fun explicitUpdateIsUpdating() {
        val result = resolveMediaStatus(SourceItem(status = "更新至第7集"))

        assertEquals(MediaReleaseState.UPDATING, result.state)
        assertEquals("更新至7集", result.episodeText)
    }

    @Test
    fun weeklyPipeShorthandIsUpdating() {
        val result = resolveMediaStatus(SourceItem(status = "15｜周六17:45更"))

        assertEquals(MediaReleaseState.UPDATING, result.state)
        assertEquals("更新至15集", result.episodeText)
    }

    @Test
    fun serializingUsesLoadedDetailCount() {
        val result = resolveMediaStatus(
            SourceItem(status = "连载中"),
            episodeCount = 8
        )

        assertEquals(MediaReleaseState.SERIALIZING, result.state)
        assertEquals("更新至8集", result.episodeText)
    }

    @Test
    fun bareEpisodeNumberDoesNotPretendToBeSerializing() {
        val result = resolveMediaStatus(SourceItem(status = "12集"))

        assertEquals(MediaReleaseState.UNKNOWN, result.state)
        assertEquals("12集", result.episodeText)
    }

    @Test
    fun zeroEpisodeTextIsNotCompleted() {
        val result = resolveMediaStatus(SourceItem(status = "0集"))

        assertEquals(MediaReleaseState.UNKNOWN, result.state)
    }

    @Test
    fun blankStatusStaysUnknown() {
        val result = resolveMediaStatus(SourceItem())

        assertEquals(MediaReleaseState.UNKNOWN, result.state)
        assertEquals("状态待确认", result.displayText)
    }

    @Test
    fun oneEpisodeMovieIsCompleted() {
        val result = resolveMediaStatus(
            SourceItem(kind = "剧场版"),
            episodeCount = 1
        )

        assertEquals(MediaReleaseState.FINISHED, result.state)
        assertEquals("正片", result.episodeText)
    }
}
