package io.github.gamso.petkitlocal.mock.tls

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 * The self-signed certificates the mock's HTTPS port can present, made at start with the JDK's own
 * `keytool` (no dependency, and nothing secret in the repository), and the one it presents now.
 *
 * Three, so the app's end-to-end tests can see what an owner would:
 * - [Certificate.PRIMARY], the server's certificate, for [names];
 * - [Certificate.RENEWED], another certificate for the same names: a renewal, or someone in the middle;
 * - [Certificate.WRONG_HOST], one for `wrong-host.invalid` only.
 *
 * Switching ([use]) replaces the TLS context, so no session made under the last certificate can
 * be resumed without a new handshake, and tells [onSwitch] listeners, so the front closes the
 * connections already open: the next request meets the new certificate.
 */
class MockTls(
    val names: List<String>,
    private val dir: Path = Files.createTempDirectory("petkit-mock-tls"),
) {
    enum class Certificate(
        val key: String,
    ) {
        PRIMARY("primary"),
        RENEWED("renewed"),
        WRONG_HOST("wrong-host"),
        ;

        companion object {
            fun of(key: String): Certificate? = entries.firstOrNull { it.key == key }
        }
    }

    private val stores: Map<Certificate, KeyStore> = Certificate.entries.associateWith(::generate)
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile var current: Certificate = Certificate.PRIMARY
        private set

    @Volatile var context: SSLContext = contextFor(Certificate.PRIMARY)
        private set

    fun certificate(which: Certificate): X509Certificate = stores.getValue(which).getCertificate(ALIAS) as X509Certificate

    /** `AB:01:...`, as the app shows it before the owner trusts it. */
    fun sha256(which: Certificate): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(certificate(which).encoded)
            .joinToString(":") { "%02X".format(it) }

    fun use(which: Certificate) {
        synchronized(this) {
            current = which
            context = contextFor(which)
        }
        listeners.forEach { it() }
        log.info("TLS now presents the {} certificate ({})", which.key, sha256(which))
    }

    fun onSwitch(listener: () -> Unit) {
        listeners += listener
    }

    private fun contextFor(which: Certificate): SSLContext {
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keys.init(stores.getValue(which), PASSWORD)
        return SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }
    }

    private fun generate(which: Certificate): KeyStore {
        val file = dir.resolve("${which.key}.p12")
        val san =
            when (which) {
                Certificate.WRONG_HOST -> "dns:wrong-host.invalid"
                else -> names.joinToString(",") { if (isAddress(it)) "ip:$it" else "dns:$it" }
            }
        val cn = if (which == Certificate.WRONG_HOST) "wrong-host.invalid" else names.first()
        val command =
            listOf(
                keytool(),
                "-genkeypair",
                "-alias",
                ALIAS,
                "-keyalg",
                "EC",
                "-groupname",
                "secp256r1",
                "-sigalg",
                "SHA256withECDSA",
                "-dname",
                "CN=$cn, O=petkit-local mock",
                "-ext",
                "SAN=$san",
                "-validity",
                VALIDITY_DAYS,
                "-keystore",
                file.toString(),
                "-storetype",
                "PKCS12",
                "-storepass",
                String(PASSWORD),
                "-keypass",
                String(PASSWORD),
            )
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(KEYTOOL_TIMEOUT_SEC, TimeUnit.SECONDS) && process.exitValue() == 0) {
            "keytool could not make the ${which.key} certificate: $output"
        }
        return KeyStore.getInstance("PKCS12").apply { Files.newInputStream(file).use { load(it, PASSWORD) } }
    }

    private fun keytool(): String {
        val bin = Path.of(System.getProperty("java.home"), "bin")
        val windows = bin.resolve("keytool.exe")
        return (if (Files.exists(windows)) windows else bin.resolve("keytool")).toString()
    }

    private fun isAddress(name: String) = name.all { it.isDigit() || it == '.' } || ':' in name

    private companion object {
        val log = LoggerFactory.getLogger(MockTls::class.java)!!
        const val ALIAS = "server"
        val PASSWORD = "petkit-mock".toCharArray()
        const val VALIDITY_DAYS = "3650"
        const val KEYTOOL_TIMEOUT_SEC = 30L
    }
}
