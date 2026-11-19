package org.petkitlocal.core.network.tls

import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Self-signed test certificates, with their keys, in `src/test/resources/tls/` (alias `server`,
 * password `petkit`), made with the JDK's keytool:
 *
 * ```
 * keytool -genkeypair -alias server -keyalg EC -groupname secp256r1 -sigalg SHA256withECDSA \
 *   -dname CN=petkit.home.lan -ext SAN=dns:petkit.home.lan,dns:localhost,ip:127.0.0.1 \
 *   -validity 36500 -keystore first.p12 -storetype PKCS12 -storepass petkit -keypass petkit
 * ```
 *
 * - `first` and `renewed`: two certificates for the same names (a renewal);
 * - `wrong-host`: for `wrong.invalid` only;
 * - `expired`: `-startdate 2020/01/01 -validity 30`.
 */
object TlsFixtures {
    const val FIRST = "first"
    const val RENEWED = "renewed"
    const val WRONG_HOST = "wrong-host"
    const val EXPIRED = "expired"
    private const val ALIAS = "server"
    private val PASSWORD = "petkit".toCharArray()

    fun keyStore(name: String): KeyStore = KeyStore.getInstance("PKCS12").apply {
        checkNotNull(TlsFixtures::class.java.getResourceAsStream("/tls/$name.p12")) { "no fixture $name" }
            .use { load(it, PASSWORD) }
    }

    fun certificate(name: String): X509Certificate = keyStore(name).getCertificate(ALIAS) as X509Certificate

    /** What a server presenting [name] needs, for MockWebServer's `useHttps`. */
    fun serverSocketFactory(name: String): SSLSocketFactory {
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keys.init(keyStore(name), PASSWORD)
        return SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }.socketFactory
    }

    /** A platform trust manager whose only anchors are [anchors]: a home CA installed on the phone. */
    fun trusting(vararg anchors: X509Certificate): X509TrustManager {
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            anchors.forEachIndexed { i, anchor -> setCertificateEntry("anchor$i", anchor) }
        }
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(store)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }
}

/** A [CertificatePinStore] a test sets, which records every refusal. */
class FakePins(var pin: CertificatePin? = null) : CertificatePinStore {
    val refused = mutableListOf<CertificateRefusedException>()

    override fun pinned(): CertificatePin? = pin

    override fun onRefused(refusal: CertificateRefusedException) {
        refused += refusal
    }
}
