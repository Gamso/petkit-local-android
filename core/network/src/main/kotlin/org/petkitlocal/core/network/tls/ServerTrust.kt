package org.petkitlocal.core.network.tls

import java.net.InetAddress
import java.net.Socket
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.ConnectionPool

/**
 * The TLS side of the shared OkHttp client (SPEC.md §8): the phone's trust plus the owner's pin
 * ([PinningTrustManager]), for every client built on it (REST, Coil, Media3, the WebSockets).
 *
 * When the pin changes, [reset] makes sure nothing set up under the old one is used again: idle
 * pooled connections are closed, and the TLS context, whose session cache could resume a session
 * without checking the certificate again, is replaced.
 */
class ServerTrust(pins: CertificatePinStore, platform: X509TrustManager = platformTrustManager()) {
    val trustManager: X509TrustManager = PinningTrustManager(platform, pins)

    /** The client's pool, so [reset] can empty it. */
    val connectionPool: ConnectionPool = ConnectionPool()

    @Volatile private var context: SSLContext = newContext()

    /** Always the current context's factory, so the client never has to be rebuilt. */
    val socketFactory: SSLSocketFactory = CurrentContextSocketFactory { context.socketFactory }

    /** Closes idle connections, which writes to their sockets: call it off the main thread. */
    fun reset() {
        context = newContext()
        connectionPool.evictAll()
    }

    private fun newContext(): SSLContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trustManager), null)
    }

    companion object {
        /** The phone's own trust manager: on Android, the network security config's anchors. */
        fun platformTrustManager(): X509TrustManager {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as KeyStore?)
            return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
        }
    }
}

/** An [SSLSocketFactory] that hands every call to whichever factory [current] gives now. */
private class CurrentContextSocketFactory(private val current: () -> SSLSocketFactory) : SSLSocketFactory() {
    override fun getDefaultCipherSuites(): Array<String> = current().defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> = current().supportedCipherSuites

    override fun createSocket(): Socket = current().createSocket()

    override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket =
        current().createSocket(socket, host, port, autoClose)

    override fun createSocket(host: String, port: Int): Socket = current().createSocket(host, port)

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        current().createSocket(host, port, localHost, localPort)

    override fun createSocket(host: InetAddress, port: Int): Socket = current().createSocket(host, port)

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        current().createSocket(address, port, localAddress, localPort)
}
