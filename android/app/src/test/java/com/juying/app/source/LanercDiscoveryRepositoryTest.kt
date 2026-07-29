package com.juying.app.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class LanercDiscoveryRepositoryTest {
    @Test
    fun decodesAuditedLanercEnvelope() {
        val key = "8f81c2519e3b661834219e7142000093"
        val plain = """{"rank_list":[{"name":"四月新番","vods":[]}]}"""
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "AES")
        )
        val standard = Base64.getEncoder()
            .encodeToString(cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8)))
            .trimEnd('=')
        val customAlphabet = standard
            .replace('9', '!')
            .replace('1', '@')
            .replace('5', '#')
            .replace('+', '*')
            .replace('/', '&')
            .replace('!', '1')
            .replace('@', '5')
            .replace('#', '9')
            .replace('*', '/')
            .replace('&', '-')
        val envelope = """{"code":201,"data":"$customAlphabet"}"""

        val decoded = LanercDiscoveryCodec.decodeEnvelope(envelope, key)

        assertEquals("四月新番", decoded.getAsJsonArray("rank_list")[0].asJsonObject["name"].asString)
    }

    @Test
    fun recommendationsPreferSharedTagsAndRealScore() {
        val seed = SourceItem(title = "目标", year = "2026", tags = listOf("奇幻", "冒险"))
        val candidates = listOf(
            SourceItem(title = "低相关高分", score = "9.8", tags = listOf("恋爱")),
            SourceItem(title = "高相关", score = "8.0", tags = listOf("奇幻", "冒险")),
            SourceItem(title = "中相关", score = "9.0", tags = listOf("奇幻"))
        )

        val result = buildDiscoveryRecommendations(seed, candidates)

        assertEquals(listOf("高相关", "中相关", "低相关高分"), result.map { it.title })
        assertTrue(result.all { it.title != seed.title })
    }
}
