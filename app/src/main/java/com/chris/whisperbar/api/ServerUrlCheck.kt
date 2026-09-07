package com.chris.whisperbar.api

import java.net.URI

/**
 * Prueft eine eingetippte Base-URL. Rein (ohne Android). Gespeichert wird bei jedem Tastendruck;
 * das Ergebnis steuert `isError`/Hinweistext des Felds und ueber `SetupFacts.urlValid`, ob der
 * Assistent den Zugang als fertig zaehlt.
 *
 * Unverschluesseltes http:// ist nur zu privaten Adressen sinnvoll (LAN, VPN, Tailscale):
 * die Network-Security-Config erlaubt Klartext global, weil Android keine IP-Bereiche
 * in <domain> kennt — die Warnung hier ist der Ausgleich dafuer.
 */
object ServerUrlCheck {

    enum class Severity { ERROR, WARNING }

    /** [Severity.ERROR] = Zugang gilt als unvollstaendig (Assistent bleibt offen), [Severity.WARNING] = nur Hinweis. */
    data class Problem(val severity: Severity, val message: String)

    const val MSG_INVALID = "Ungültige URL"
    const val MSG_SCHEME = "URL muss mit http:// oder https:// beginnen"
    const val MSG_NO_HOST = "Kein Host in der URL"
    const val MSG_HTTPS_REQUIRED = "Dieser Anbieter braucht https://"
    const val MSG_PUBLIC_HTTP = "Unverschlüsselt über das Internet — https:// oder VPN (Tailscale) verwenden"
    const val MSG_V1 = "Base-URL endet normalerweise auf /v1"

    private val PRIVATE_SUFFIXES = listOf(".local", ".lan", ".home.arpa", ".internal")

    /** localhost, RFC-1918, Link-Local, Tailscale-CGNAT (100.64/10), IPv6-ULA/Loopback, mDNS-Endungen. */
    fun isPrivateHost(host: String): Boolean {
        val h = host.trim().lowercase().removePrefix("[").removeSuffix("]")
        if (h.isEmpty()) return false
        if (h == "localhost" || PRIVATE_SUFFIXES.any { h.endsWith(it) }) return true
        if (h.contains(':')) {
            return h == "::1" || h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe80:")
        }
        val p = h.split('.').map { it.toIntOrNull() ?: return false }
        if (p.size != 4 || p.any { it !in 0..255 }) return false
        return p[0] == 10 || p[0] == 127 ||
            (p[0] == 172 && p[1] in 16..31) ||
            (p[0] == 192 && p[1] == 168) ||
            (p[0] == 169 && p[1] == 254) ||
            (p[0] == 100 && p[1] in 64..127)
    }

    /** null = in Ordnung. Der /v1-Hinweis gilt nur fuer den eigenen Server — Cloud-URLs kommen aus dem Katalog. */
    fun check(baseUrl: String, provider: Provider): Problem? {
        val trimmed = baseUrl.trim()
        if (trimmed.isEmpty()) return Problem(Severity.ERROR, MSG_INVALID)
        val scheme = trimmed.substringBefore("://", "").lowercase()
        if (scheme != "http" && scheme != "https") return Problem(Severity.ERROR, MSG_SCHEME)
        val u = runCatching { URI(trimmed) }.getOrNull()
            ?: return Problem(Severity.ERROR, MSG_INVALID)
        val host = u.host
        if (host.isNullOrBlank()) return Problem(Severity.ERROR, MSG_NO_HOST)
        if (scheme == "http") {
            if (!provider.allowsHttp) return Problem(Severity.ERROR, MSG_HTTPS_REQUIRED)
            if (!isPrivateHost(host)) return Problem(Severity.WARNING, MSG_PUBLIC_HTTP)
        }
        if (provider.isCustom && !u.path.orEmpty().trimEnd('/').endsWith("/v1")) {
            return Problem(Severity.WARNING, MSG_V1)
        }
        return null
    }
}
