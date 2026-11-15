package io.github.gamso.petkitlocal.mock

import io.github.gamso.petkitlocal.mock.http.mockModule
import io.github.gamso.petkitlocal.mock.scenario.Scenario
import io.github.gamso.petkitlocal.mock.state.World
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory

/** `petkit-local-mock [--port=8097] [--password=demo] ...`; see README.md for every option. */
fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("petkit-local-mock")
    val config = MockConfig.from(args)
    val world = World(config)
    val scenario = Scenario(world, config.scenarioIntervalSec)
    val server =
        embeddedServer(Netty, port = config.port, host = config.host) {
            mockModule(world, scenario)
        }
    scenario.start()
    log.info(
        "petkit-local mock on http://{}:{}/app/v1 (password {}, control API {}, catalog from petkit-local {})",
        config.host,
        config.port,
        if (config.password.isEmpty()) "not set" else "set",
        if (config.controlEnabled) "on /mock" else "off",
        world.catalog.source.take(12),
    )
    server.start(wait = true)
}
