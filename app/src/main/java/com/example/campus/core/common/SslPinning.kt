package com.example.campus.core.common

import okhttp3.CertificatePinner

/**
 * SSL 证书固定（Certificate Pinning）配置。
 *
 * 作用：把 HTTPS 请求限定在预置的服务器证书公钥指纹上，即使攻击者伪造了 CA 签发的证书，
 * 只要指纹不匹配请求就会被拒绝，从而防止中间人攻击。
 *
 * 启用条件（由 [isEnabled] 统一约束）：
 * 1. BASE_URL 必须为 https 协议（本地 HTTP 联调 10.0.2.2 不启用）；
 * 2. 必须配置了至少一条 [PinRule]（无规则时固定不生效，避免误拦截）。
 *
 * 部署到 HTTPS 后，把 [DEFAULT_RULES] 填成真实值即可，指纹获取方式：
 * ```
 * openssl s_client -connect your-api.com:443 </dev/null 2>/dev/null \
 *   | openssl x509 -noout -pubkey \
 *   | openssl pkey -pubin -outform der \
 *   | openssl dgst -sha256 -binary | base64
 * ```
 */
object SslPinning {

    /** 一条固定规则：host 与若干 sha256 指纹。 */
    data class PinRule(val host: String, val sha256Pins: List<String>)

    /** 生产环境的证书固定规则，部署 HTTPS 后填入真实 host 与指纹。 */
    private val DEFAULT_RULES: List<PinRule> = emptyList()

    /** 是否应启用证书固定：仅当存在规则且 BASE_URL 为 https 时。 */
    fun isEnabled(baseUrl: String, rules: List<PinRule> = DEFAULT_RULES): Boolean =
        rules.isNotEmpty() && baseUrl.startsWith("https", ignoreCase = true)

    /** 根据规则构建 [CertificatePinner]，无规则时返回一个不拦截任何请求的空实例。 */
    fun buildPinner(rules: List<PinRule> = DEFAULT_RULES): CertificatePinner {
        val builder = CertificatePinner.Builder()
        rules.forEach { rule ->
            rule.sha256Pins.forEach { pin -> builder.add(rule.host, pin) }
        }
        return builder.build()
    }
}
