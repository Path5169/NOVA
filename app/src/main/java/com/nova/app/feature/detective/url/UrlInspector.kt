package com.nova.app.feature.detective.url

import com.nova.app.feature.detective.DetectiveFinding
import com.nova.app.feature.detective.DetectiveVerdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.IDN
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class UrlInspectionResult(
    val verdict: DetectiveVerdict,
    val scheme: String,
    val host: String,
    val subdomain: String?,
    val registrableDomain: String,
    val port: Int,
    val path: String,
    val queryParams: List<Pair<String, String>>,
    val isHttps: Boolean,
    val findings: List<DetectiveFinding>,
    val redirectChain: List<String>,
    val certificateIssuer: String?,
    val explanation: String
)

/**
 * Parses and inspects a URL using only information the URL itself and (optionally) a direct
 * HTTPS handshake legitimately expose — no third-party threat-intel lookups, no claim of
 * "malicious" without concrete evidence. Every finding is something NOVA can point to directly
 * in the URL's structure or connection.
 *
 * A subset of well-known lookalike/brand terms is used only to flag *possible* impersonation
 * patterns for the user's own review — never as a definitive verdict.
 */
object UrlInspector {

    private val suspiciousBrandHints = listOf(
        "paypal", "google", "apple", "amazon", "microsoft", "bank", "facebook", "instagram", "netflix"
    )

    suspend fun inspect(rawUrl: String, checkRedirects: Boolean): UrlInspectionResult = withContext(Dispatchers.IO) {
        val normalized = if (!rawUrl.contains("://")) "https://$rawUrl" else rawUrl
        val uri = try { URI(normalized) } catch (e: Exception) { null }

        if (uri == null || uri.host == null) {
            return@withContext UrlInspectionResult(
                verdict = DetectiveVerdict.SUSPICIOUS_PATTERN,
                scheme = "", host = "", subdomain = null, registrableDomain = "",
                port = -1, path = "", queryParams = emptyList(), isHttps = false,
                findings = listOf(DetectiveFinding("Could not be parsed as a valid URL", positive = false)),
                redirectChain = emptyList(), certificateIssuer = null,
                explanation = "This text doesn't parse as a well-formed URL, so NOVA can't analyze its structure."
            )
        }

        val scheme = uri.scheme ?: "http"
        val host = uri.host
        val asciiHost = try { IDN.toASCII(host) } catch (e: Exception) { host }
        val labels = host.split(".")
        val registrableDomain = if (labels.size >= 2) labels.takeLast(2).joinToString(".") else host
        val subdomain = if (labels.size > 2) labels.dropLast(2).joinToString(".") else null
        val port = if (uri.port != -1) uri.port else if (scheme == "https") 443 else 80
        val path = uri.path.orEmpty()
        val queryParams = (uri.query ?: "").split("&").filter { it.isNotBlank() }.mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.isNotEmpty()) parts[0] to parts.getOrElse(1) { "" } else null
        }
        val isHttps = scheme.equals("https", ignoreCase = true)

        val findings = mutableListOf<DetectiveFinding>()

        // HTTPS usage
        if (isHttps) {
            findings.add(DetectiveFinding("Uses HTTPS", positive = true))
        } else {
            findings.add(DetectiveFinding("Does not use HTTPS", positive = false, detail = "Traffic to this site isn't encrypted in transit."))
        }

        // Punycode / IDN homograph pattern
        if (asciiHost.startsWith("xn--") || asciiHost.contains(".xn--")) {
            findings.add(DetectiveFinding("Domain uses internationalized (punycode) characters", positive = false,
                detail = "Punycode domains can visually resemble a different, trusted domain."))
        } else {
            findings.add(DetectiveFinding("Standard domain character set", positive = true))
        }

        // Excessive subdomain nesting, often used to bury a real domain
        if ((subdomain?.split(".")?.size ?: 0) >= 3) {
            findings.add(DetectiveFinding("Unusually long subdomain chain", positive = false,
                detail = "\"$subdomain.$registrableDomain\" — the real destination domain is \"$registrableDomain\"."))
        }

        // Brand name appearing outside the registrable domain (look-alike pattern)
        val brandInSubdomainOrPath = suspiciousBrandHints.firstOrNull { brand ->
            (subdomain?.contains(brand, ignoreCase = true) == true && !registrableDomain.contains(brand, ignoreCase = true)) ||
                (path.contains(brand, ignoreCase = true) && !registrableDomain.contains(brand, ignoreCase = true))
        }
        if (brandInSubdomainOrPath != null) {
            findings.add(DetectiveFinding(
                "Look-alike domain pattern", positive = false,
                detail = "\"$brandInSubdomainOrPath\" appears outside the actual domain ($registrableDomain), a pattern sometimes used to imitate a trusted site."
            ))
        }

        // '@' in URL — everything before it is ignored as the actual host by browsers
        if (normalized.substringAfter("://").contains("@")) {
            findings.add(DetectiveFinding("Contains an \"@\" before the host", positive = false,
                detail = "Browsers ignore everything before \"@\" when resolving the host — this can hide the real destination."))
        }

        // Non-standard port
        if (port != 443 && port != 80) {
            findings.add(DetectiveFinding("Uses a non-standard port ($port)", positive = false))
        }

        // Excessive hyphens (common in typosquatting)
        if (registrableDomain.count { it == '-' } >= 3) {
            findings.add(DetectiveFinding("Domain has an unusually high number of hyphens", positive = false))
        }

        var redirectChain = listOf(normalized)
        var certificateIssuer: String? = null

        if (checkRedirects) {
            try {
                var current = normalized
                val chain = mutableListOf(current)
                var connection: HttpURLConnection? = null
                repeat(5) { hop ->
                    val url = URL(current)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.requestMethod = "HEAD"
                    conn.connect()
                    connection = conn
                    val code = conn.responseCode
                    if (code in 300..399) {
                        val location = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (location != null) {
                            current = if (location.startsWith("http")) location else URI(current).resolve(location).toString()
                            chain.add(current)
                        } else return@repeat
                    } else {
                        conn.disconnect()
                        return@repeat
                    }
                }
                redirectChain = chain
                if (connection is HttpsURLConnection) {
                    certificateIssuer = (connection as HttpsURLConnection).serverCertificates
                        .firstOrNull()?.let { (it as? java.security.cert.X509Certificate)?.issuerX500Principal?.name }
                }
            } catch (e: Exception) {
                // No network / host unreachable / timeout — report what we already know, not a guess.
                findings.add(DetectiveFinding("Redirect check unavailable", positive = true,
                    detail = "Couldn't reach the host to trace redirects (no connection, or the host refused it)."))
            }

            if (redirectChain.size > 1) {
                findings.add(DetectiveFinding("Redirects to a different destination", positive = false,
                    detail = "${redirectChain.size - 1} redirect(s) observed. Final: ${redirectChain.last()}"))
            }
        }

        val negativeCount = findings.count { !it.positive }
        val verdict = when {
            negativeCount == 0 -> DetectiveVerdict.SAFE_LOOKING
            negativeCount <= 1 -> DetectiveVerdict.REVIEW
            else -> DetectiveVerdict.SUSPICIOUS_PATTERN
        }

        val explanation = when (verdict) {
            DetectiveVerdict.SAFE_LOOKING -> "This URL's structure looks ordinary: HTTPS, a standard domain, and nothing that resembles a known deception pattern."
            DetectiveVerdict.REVIEW -> "One thing about this URL is worth a second look before you trust it — see the findings above."
            DetectiveVerdict.SUSPICIOUS_PATTERN -> "Several structural patterns here are commonly seen in deceptive or look-alike links. This isn't proof the destination is harmful, but it's worth verifying the domain independently before entering any information."
        }

        UrlInspectionResult(
            verdict = verdict,
            scheme = scheme,
            host = host,
            subdomain = subdomain,
            registrableDomain = registrableDomain,
            port = port,
            path = path,
            queryParams = queryParams,
            isHttps = isHttps,
            findings = findings,
            redirectChain = redirectChain,
            certificateIssuer = certificateIssuer,
            explanation = explanation
        )
    }
}
