package io.github.gamso.petkitlocal.mock

import io.github.gamso.petkitlocal.mock.http.mockModule
import io.github.gamso.petkitlocal.mock.scenario.Scenario
import io.github.gamso.petkitlocal.mock.state.World
import io.github.gamso.petkitlocal.mock.tls.TlsFront
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory

/** `petkit-local-mock [--port=8097] [--password=demo] [--tls-port=8443] ...`; see README.md for every option. */
fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("petkit-local-mock")
    val config = MockConfig.from(args)
    val world = World(config)
    val scenario = Scenario(world, config.scenarioIntervalSec)
    val server =
        embeddedServer(Netty, port = config.port, host = config.host) {
            mockModule(world, scenario)
        }
    // The same API over HTTPS with a self-signed certificate, for the app's certificate trust.
    val tlsFront = world.tls?.let { TlsFront(it, config.host, config.tlsPort, upstreamPort = config.port) }
    scenario.start()
    log.info(
        "petkit-local mock on http://{}:{}/app/v1 (password {}, control API {}, catalog from petkit-local {})",
        config.host,
        config.port,
        if (config.password.isEmpty()) "not set" else "set",
        if (config.controlEnabled) "on /mock" else "off",
        world.catalog.source.take(12),
    )
    world.tls?.let { tls ->
        log.info(
            "and on https://{}:{}/app/v1 with a self-signed certificate for {} (SHA-256 {})",
            config.host,
            tlsFront?.port,
            tls.names.joinToString(),
            tls.sha256(tls.current),
        )
    }
    server.start(wait = true)
}
