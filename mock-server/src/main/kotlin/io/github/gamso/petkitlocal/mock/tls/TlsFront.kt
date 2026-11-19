package io.github.gamso.petkitlocal.mock.tls

import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLSocket

/**
 * The mock's HTTPS port: a TLS terminator in front of its own plain HTTP port, as a reverse proxy
 * with a self-signed certificate would be in front of petkit-local. Each connection is decrypted
 * with the certificate [tls] presents at the time it is accepted and relayed byte for byte to
 * [upstreamPort], WebSocket upgrades included.
 *
 * When [tls] switches certificate, every open connection is closed, so the app's next request
 * opens a new one and meets the new certificate rather than reusing a pooled connection.
 */
class TlsFront(
    private val tls: MockTls,
    host: String,
    port: Int,
    private val upstreamPort: Int,
) : Closeable {
    private val server = ServerSocket().apply { bind(InetSocketAddress(host, port)) }
    private val open = ConcurrentHashMap.newKeySet<Socket>()

    /** The port it listens on: the one asked for, or the one the system chose for 0. */
    val port: Int get() = server.localPort

    init {
        tls.onSwitch { closeConnections() }
        Thread.ofVirtual().name("tls-accept").start { acceptLoop() }
    }

    private fun acceptLoop() {
        while (!server.isClosed) {
            val raw =
                try {
                    server.accept()
                } catch (_: IOException) {
                    return // closed
                }
            Thread.ofVirtual().start { serve(raw) }
        }
    }

    private fun serve(raw: Socket) {
        val secure =
            tls.context.socketFactory.createSocket(raw, raw.inetAddress.hostAddress, raw.port, true) as SSLSocket
        secure.useClientMode = false
        open += secure
        try {
            secure.startHandshake()
            Socket(InetAddress.getLoopbackAddress(), upstreamPort).use { upstream ->
                open += upstream
                val down = Thread.ofVirtual().start { pump(upstream.getInputStream(), secure.outputStream, secure) }
                pump(secure.inputStream, upstream.getOutputStream(), upstream)
                down.join()
                open -= upstream
            }
        } catch (e: IOException) {
            // A client refusing the certificate ends here, which is the point of the exercise.
            log.debug("TLS connection from {} ended: {}", raw.inetAddress.hostAddress, e.message)
        } finally {
            open -= secure
            secure.closeQuietly()
        }
    }

    /** Copies until either side ends, then closes [sink] so the other direction ends too. */
    private fun pump(
        source: InputStream,
        sink: OutputStream,
        sinkSocket: Socket,
    ) {
        try {
            source.copyTo(sink, BUFFER)
        } catch (_: IOException) {
            // Either side went away.
        } finally {
            sinkSocket.closeQuietly()
        }
    }

    private fun closeConnections() {
        open.forEach { it.closeQuietly() }
        open.clear()
    }

    override fun close() {
        server.closeQuietly()
        closeConnections()
    }

    private fun Closeable.closeQuietly() {
        try {
            close()
        } catch (_: IOException) {
            // Already closed.
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(TlsFront::class.java)!!
        const val BUFFER = 16 * 1024
    }
}
