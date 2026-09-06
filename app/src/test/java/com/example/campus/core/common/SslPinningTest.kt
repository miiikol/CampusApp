package com.example.campus.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SSL 证书固定逻辑单元测试。
 *
 * 重点验证 [SslPinning.isEnabled] 的启用条件判断：
 * 1. 无规则时不启用（避免空壳固定拦截所有 HTTPS 请求）；
 * 2. http 协议不启用（本地联调 10.0.2.2 不触发固定）；
 * 3. https + 有规则才启用。
 * 另对 [SslPinning.buildPinner] 做构建冒烟测试。
 */
class SslPinningTest {

    // sha256/ 后为 32 字节的合法 base64 指纹（43 个 'A' + 1 个 '='）
    private val validSha256Pin = "sha256/" + "A".repeat(43) + "="
    private val sampleRules = listOf(
        SslPinning.PinRule("api.example.com", listOf(validSha256Pin))
    )

    @Test
    fun `isEnabled 无规则时返回 false`() {
        assertFalse(SslPinning.isEnabled("https://api.example.com", emptyList()))
    }

    @Test
    fun `isEnabled http 协议即使有规则也返回 false`() {
        assertFalse(SslPinning.isEnabled("http://api.example.com", sampleRules))
    }

    @Test
    fun `isEnabled https 且有规则返回 true`() {
        assertTrue(SslPinning.isEnabled("https://api.example.com", sampleRules))
    }

    @Test
    fun `isEnabled 忽略协议大小写`() {
        assertTrue(SslPinning.isEnabled("HTTPS://api.example.com", sampleRules))
    }

    @Test
    fun `buildPinner 空规则返回非空实例`() {
        assertNotNull(SslPinning.buildPinner(emptyList()))
    }

    @Test
    fun `buildPinner 含规则可正常构建`() {
        assertNotNull(SslPinning.buildPinner(sampleRules))
    }
}
