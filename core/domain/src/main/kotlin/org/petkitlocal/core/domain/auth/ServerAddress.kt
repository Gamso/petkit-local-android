package org.petkitlocal.core.domain.auth

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * Where a petkit-local server is, normalised: a lowercase scheme and host, no default port, no
 * trailing slash, no query, and the path prefix of a reverse proxy kept (`https://example.com/petkit`).
 *
 * @property baseUrl the server's root, without `/app/v1`, e.g. `https://pets.example.com`.
 * @property plainHttpOnLan the address is plain HTTP, which is only accepted on a private LAN
 *   (SPEC.md §3); the sign-in screen warns about it.
 */
data class ServerAddress(val baseUrl: String, val host: String, val plainHttpOnLan: Boolean) {
    /** The base of the phone-app API, with its trailing slash: `https://pets.example.com/app/v1/`. */
    val apiBaseUrl: String get() = "$baseUrl$API_PATH/"

    companion object {
        const val API_PATH = "/app/v1"
    }
}

/** Why a typed address cannot be used. */
enum class AddressProblem {
    /** Nothing was typed. */
    Empty,

    /** Not an address: spaces, no host, a bad port, a user name in it. */
    Malformed,

    /** A scheme other than `http` or `https`. */
    UnsupportedScheme,

    /** Plain `http` to a host that is not on a private LAN (SPEC.md §8). */
    HttpsRequired,
}

/** The result of [ServerAddresses.normalise]. */
sealed interface AddressCheck {
    data class Valid(val address: ServerAddress) : AddressCheck

    data class Invalid(val problem: AddressProblem) : AddressCheck
}

/**
 * Turns what the user typed into a [ServerAddress] (SPEC.md §5.1, "normalise the address").
 *
 * - No scheme: `https`, except for a private LAN host, where petkit-local usually listens on plain
 *   HTTP; `http` is then assumed, with the same warning as if it had been typed.
 * - A pasted API URL (`.../app/v1/devices`) is cut back to the server's root.
 * - Plain `http` is refused unless the host is on a private LAN ([isPrivateLanHost]).
 */
object ServerAddresses {
    private val schemeRegex = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
    private val ipv4Regex = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
    private const val MAX_PORT = 65_535
    private const val HTTP = "http"
    private const val HTTPS = "https"
    private const val DEFAULT_HTTP_PORT = 80
    private const val DEFAULT_HTTPS_PORT = 443

    /** Name suffixes that only resolve inside a home network (mDNS, RFC 8375, common routers). */
    private val lanSuffixes = listOf(".local", ".lan", ".home.arpa", ".internal")

    fun normalise(input: String): AddressCheck {
        val text = input.trim()
        val typedScheme = schemeRegex.find(text)?.value?.removeSuffix("://")?.lowercase(Locale.ROOT)
        val uri = if (text.isEmpty() || text.any(Char::isWhitespace)) null else parse(typedScheme, text)
        val host = uri?.host?.lowercase(Locale.ROOT)?.trimEnd('.')?.takeIf { it.isNotEmpty() }
        val lan = host != null && isPrivateLanHost(host)
        val scheme = typedScheme ?: if (lan) HTTP else HTTPS
        val problem = problemOf(text, uri, host, scheme, lan)
        if (problem != null || uri == null ||
            host == null
        ) {
            return AddressCheck.Invalid(problem ?: AddressProblem.Malformed)
        }

        val baseUrl = "$scheme://$host${portSuffix(scheme, uri.port)}${prefixPath(uri.rawPath.orEmpty())}"
        return AddressCheck.Valid(ServerAddress(baseUrl = baseUrl, host = host, plainHttpOnLan = scheme == HTTP))
    }

    private fun problemOf(text: String, uri: URI?, host: String?, scheme: String, lan: Boolean): AddressProblem? =
        when {
            text.isEmpty() -> AddressProblem.Empty
            uri == null || host == null || uri.rawUserInfo != null -> AddressProblem.Malformed
            uri.port != -1 && uri.port !in 1..MAX_PORT -> AddressProblem.Malformed
            scheme != HTTP && scheme != HTTPS -> AddressProblem.UnsupportedScheme
            scheme == HTTP && !lan -> AddressProblem.HttpsRequired
            else -> null
        }

    /** No port for the scheme's default one. */
    private fun portSuffix(scheme: String, port: Int): String = when {
        port == -1 -> ""
        scheme == HTTP && port == DEFAULT_HTTP_PORT -> ""
        scheme == HTTPS && port == DEFAULT_HTTPS_PORT -> ""
        else -> ":$port"
    }

    /**
     * Whether [host] can only be on the user's own network: a private, loopback or link-local IP
     * (IPv4 10/8, 172.16/12, 192.168/16, 127/8, 169.254/16; IPv6 ::1, fc00::/7, fe80::/10),
     * `localhost`, a single-label name, or a home-network suffix (`.local`, `.lan`, `.home.arpa`,
     * `.internal`). A public name that resolves to a LAN address is not recognised: HTTPS is
     * then required, which is the safe side.
     */
    fun isPrivateLanHost(host: String): Boolean {
        val name = host.lowercase(Locale.ROOT).trimEnd('.')
        ipv4Regex.matchEntire(name)?.let { match -> return isPrivateIpv4(match.groupValues.drop(1)) }
        if (name.startsWith("[") || name.contains(':')) return isPrivateIpv6(name.removePrefix("[").removeSuffix("]"))
        return name == "localhost" || !name.contains('.') || lanSuffixes.any(name::endsWith)
    }

    /** Without a typed scheme, `https` stands in for the parse only; [normalise] picks the real one. */
    private fun parse(typedScheme: String?, text: String): URI? = try {
        URI(if (typedScheme == null) "$HTTPS://$text" else text)
    } catch (_: URISyntaxException) {
        null
    }

    /** The reverse-proxy prefix: the path without trailing slashes and without a pasted `/app/v1...`. */
    private fun prefixPath(rawPath: String): String {
        val path = rawPath.trimEnd('/')
        val api = path.lowercase(Locale.ROOT).indexOf(ServerAddress.API_PATH)
        val isApiSegment = api >= 0 && path.length.let { end ->
            val after = api + ServerAddress.API_PATH.length
            after == end || path[after] == '/'
        }
        return (if (isApiSegment) path.substring(0, api) else path).trimEnd('/')
    }

    @Suppress("MagicNumber") // The private ranges of RFC 1918 and RFC 3927, written as they are known.
    private fun isPrivateIpv4(parts: List<String>): Boolean {
        val octets = parts.map { it.toInt() }
        if (octets.any { it > 255 }) return false
        val (a, b) = octets
        return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254)
    }

    @Suppress("MagicNumber") // fc00::/7 and fe80::/10, as masks of the first 16 bits.
    private fun isPrivateIpv6(address: String): Boolean {
        val plain = address.substringBefore('%')
        if (plain == "::1") return true
        val first = plain.substringBefore(':').takeIf { it.isNotEmpty() }?.toIntOrNull(16) ?: return false
        return (first and 0xfe00) == 0xfc00 || (first and 0xffc0) == 0xfe80
    }
}
