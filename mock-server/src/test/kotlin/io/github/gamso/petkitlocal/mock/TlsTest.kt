package io.github.gamso.petkitlocal.mock

import io.github.gamso.petkitlocal.mock.tls.MockTls
import io.github.gamso.petkitlocal.mock.tls.TlsFront
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread

/**
 * The HTTPS port (`--tls-port`): self-signed certificates made with keytool, relayed to the plain
 * port, switched from the control API. Certificates are made once for the class: keytool is slow.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TlsTest {
    private val tls = MockTls(listOf("localhost", "127.0.0.1", "10.0.2.2"))

    /** A plain HTTP server that answers every request with `ok`, standing in for the mock's API. */
    private val upstream = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private val front = TlsFront(tls, "127.0.0.1", 0, upstream.localPort)

    init {
        thread(isDaemon = true) {
            while (!upstream.isClosed) {
                val socket = runCatching { upstream.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    socket.use {
                        // The request's head, up to its blank line; the answer does not depend on it.
                        it
                            .getInputStream()
                            .bufferedReader()
                            .lineSequence()
                            .takeWhile { line -> line.isNotEmpty() }
                            .count()
                        it.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
                    }
                }
            }
        }
    }

    @AfterAll
    fun tearDown() {
        front.close()
        upstream.close()
    }

    /** What a client trusting only [trusted] gets: the certificate presented and the answer. */
    private fun fetch(trusted: X509Certificate = tls.certificate(tls.current)): Pair<X509Certificate, String> {
        var presented: X509Certificate? = null
        val manager =
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<X509Certificate>,
                    authType: String,
                ) = Unit

                override fun checkServerTrusted(
                    chain: Array<X509Certificate>,
                    authType: String,
                ) {
                    presented = chain[0]
                    if (!chain[0].encoded.contentEquals(trusted.encoded)) throw java.security.cert.CertificateException("not pinned")
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(manager), null) }
        val socket = context.socketFactory.createSocket(Socket("127.0.0.1", front.port), "localhost", front.port, true) as SSLSocket
        socket.use {
            it.startHandshake()
            it.outputStream.write("GET /app/v1/info HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
            val answer = it.inputStream.bufferedReader().readText()
            return presented!! to answer
        }
    }

    private fun sha256(certificate: X509Certificate) =
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString(":") { "%02X".format(it) }

    @Test
    fun `the HTTPS port relays to the plain one, presenting the certificate the control API chose`() {
        tls.use(MockTls.Certificate.PRIMARY)
        val (first, answer) = fetch()
        assertEquals(tls.sha256(MockTls.Certificate.PRIMARY), sha256(first))
        assertTrue(answer.startsWith("HTTP/1.1 200") && answer.endsWith("ok"), answer)

        tls.use(MockTls.Certificate.RENEWED)
        val (renewed, _) = fetch()
        assertEquals(tls.sha256(MockTls.Certificate.RENEWED), sha256(renewed))
        assertNotEquals(sha256(first), sha256(renewed))

        // A client that pinned the first certificate is refused the renewed one.
        assertThrows<SSLHandshakeException> { fetch(trusted = first) }
        tls.use(MockTls.Certificate.PRIMARY)
    }

    @Test
    fun `the certificates name what the app connects to, except the wrong-host one`() {
        val names = { which: MockTls.Certificate -> tls.certificate(which).subjectAlternativeNames.map { it[1].toString() } }
        assertEquals(listOf("localhost", "127.0.0.1", "10.0.2.2"), names(MockTls.Certificate.PRIMARY))
        assertEquals(names(MockTls.Certificate.PRIMARY), names(MockTls.Certificate.RENEWED))
        assertEquals(listOf("wrong-host.invalid"), names(MockTls.Certificate.WRONG_HOST))
    }

    @Test
    fun `the control API says which certificate is presented, switches it, and a reset goes back`() =
        mockTest(testConfig().copy(tlsPort = 1)) { world, api ->
            val before = api.client.control(HttpMethod.Get, "/tls")
            assertEquals(200, before.status)
            assertEquals("primary", before["certificate"].jsonPrimitive.content)
            assertEquals(world.tls!!.sha256(MockTls.Certificate.PRIMARY), before["sha256"].jsonPrimitive.content)
            assertEquals(3, before["certificates"].jsonObject.size)

            val switched = api.client.control(HttpMethod.Put, "/tls", """{"certificate":"renewed"}""")
            assertEquals("renewed", switched["certificate"].jsonPrimitive.content)
            assertEquals(400, api.client.control(HttpMethod.Put, "/tls", """{"certificate":"other"}""").status)

            api.client.control(HttpMethod.Post, "/reset")
            assertEquals(
                "primary",
                api.client
                    .control(HttpMethod.Get, "/tls")["certificate"]
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun `without an HTTPS port, the control API says so`() =
        mockTest { _, api ->
            val r = api.client.control(HttpMethod.Get, "/tls")
            assertEquals(404, r.status)
            assertEquals("tls_off", r.error)
        }
}
