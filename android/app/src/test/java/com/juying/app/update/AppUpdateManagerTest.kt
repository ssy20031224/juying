package com.juying.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AppUpdateManagerTest {
    @Test
    fun parsesCustomUpdateTitleAndReleaseNotesAlias() {
        val info = parseUpdateManifest(
            """
            {
              "versionCode": 12015,
              "versionName": "1.2.15",
              "releaseTitle": "自定义更新标题",
              "releaseNotes": ["新增真实周表", "修复播放失败"],
              "apkUrl": "https://download.example.com/juying.apk"
            }
            """.trimIndent(),
            "test"
        )

        assertEquals("自定义更新标题", info.title)
        assertEquals("新增真实周表\n修复播放失败", info.notes)
        assertEquals(listOf("https://download.example.com/juying.apk"), info.apkUrls)
    }

    @Test
    fun parsesNestedUpdateContent() {
        val info = parseUpdateManifest(
            """
            {
              "update": {
                "version_code": 12016,
                "version": "1.2.16",
                "updateTitle": "本周版本说明",
                "updateContent": "这里显示发布者自定义的完整内容",
                "downloadUrl": "https://download.example.com/juying.bin",
                "sha_256": "abc"
              }
            }
            """.trimIndent(),
            "test"
        )

        assertEquals(12016, info.versionCode)
        assertEquals("1.2.16", info.versionName)
        assertEquals("本周版本说明", info.title)
        assertEquals("这里显示发布者自定义的完整内容", info.notes)
        assertEquals("abc", info.sha256)
    }

    @Test
    fun newestManifestWinsWithoutLosingPublisherNotesToAnOlderMirror() {
        val old = AppUpdateInfo(12015, "1.2.15", "旧标题", "旧内容", listOf("https://a/old.apk"))
        val current = AppUpdateInfo(
            12016,
            "1.2.16",
            "自定义标题",
            "自定义内容",
            listOf("https://a/new.apk"),
            manifestRevision = 2,
        )
        val staleRevision = current.copy(title = "缓存标题", notes = "缓存内容", manifestRevision = 1)

        val selected = selectBestUpdateCandidate(listOf(old, current, staleRevision))
        assertNotNull(selected)
        assertEquals("自定义标题", selected?.title)
        assertEquals("自定义内容", selected?.notes)
    }
}
