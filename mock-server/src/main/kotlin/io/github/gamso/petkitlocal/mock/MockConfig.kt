package io.github.gamso.petkitlocal.mock

import java.time.ZoneId

/**
 * Everything the mock can be told at start. Each field has an environment variable
 * (`PETKIT_MOCK_<NAME>`) and a command-line flag (`--<name>=<value>`); the flag wins.
 *
 * Timings are in real seconds. A test sets them to 0 so a cleaning or a visit completes at once.
 */
data class MockConfig(
    val host: String = "0.0.0.0",
    val port: Int = 8097,
    /** The one password. Empty means none is set: every `/app/v1` route answers `password_not_set`. */
    val password: String = "demo",
    /** Whether `/mock/...` (the control API, no auth) is served. */
    val controlEnabled: Boolean = true,
    /** Seconds between two scenario steps; 0 turns the scenario engine off. */
    val scenarioIntervalSec: Double = 20.0,
    /** Seeds both the seven days of history and the scenario engine. */
    val seed: Long = 42,
    /** How long a device stays busy (cleaning, dumping, feeding...) after an action. */
    val busySec: Double = 6.0,
    /** How long a scenario visit keeps the pet inside before its summary arrives. */
    val visitSec: Double = 8.0,
    /** How long a recording takes to be "assembled" after the visit card first appears. */
    val videoDelaySec: Double = 5.0,
    /** First login lockout; each further failure doubles it. 0 disables the lockout. */
    val lockoutBaseSec: Double = 1.0,
    /** The longest lockout. */
    val lockoutMaxSec: Double = 900.0,
    /** Protocol-level WebSocket ping interval, and the `heartbeat_sec` of `hello`. */
    val heartbeatSec: Double = 25.0,
    /** Messages waiting for one `/ws` client before it is closed with 1013. */
    val wsQueueLimit: Int = 256,
    /** The server's local timezone: days, meal times and one-off feeds are cut in it. */
    val timezone: ZoneId = ZoneId.of("Europe/Paris"),
    /** `server_version` in `/info`. */
    val serverVersion: String = "mock-0.1.0",
    /**
     * A second port serving the same API over HTTPS with a self-signed certificate (0: none), as a
     * petkit-local behind a reverse proxy with its own certificate would (`tls/MockTls.kt`).
     */
    val tlsPort: Int = 0,
    /** The names and addresses the certificate is for: what the app connects to. */
    val tlsNames: List<String> = listOf("localhost", "127.0.0.1", "10.0.2.2"),
) {
    companion object {
        private const val ENV_PREFIX = "PETKIT_MOCK_"

        /** The config from `PETKIT_MOCK_*` variables, then `--name=value` arguments over them. */
        fun from(
            args: Array<String>,
            env: Map<String, String> = System.getenv(),
        ): MockConfig {
            val values = mutableMapOf<String, String>()
            env.forEach { (k, v) ->
                if (k.startsWith(ENV_PREFIX)) values[k.removePrefix(ENV_PREFIX).lowercase().replace('_', '-')] = v
            }
            var i = 0
            while (i < args.size) {
                val arg = args[i]
                require(arg.startsWith("--")) { "unexpected argument: $arg" }
                val body = arg.removePrefix("--")
                if ('=' in body) {
                    values[body.substringBefore('=')] = body.substringAfter('=')
                } else {
                    require(i + 1 < args.size) { "missing value for $arg" }
                    values[body] = args[++i]
                }
                i++
            }
            return fromValues(values)
        }

        private fun fromValues(values: Map<String, String>): MockConfig {
            val d = MockConfig()
            val known =
                setOf(
                    "host",
                    "port",
                    "password",
                    "control",
                    "scenario-interval",
                    "seed",
                    "busy",
                    "visit",
                    "video-delay",
                    "lockout-base",
                    "lockout-max",
                    "heartbeat",
                    "ws-queue-limit",
                    "timezone",
                    "server-version",
                    "tls-port",
                    "tls-names",
                )
            val unknown = values.keys - known
            require(unknown.isEmpty()) { "unknown option(s): ${unknown.sorted().joinToString()}" }

            fun dbl(
                name: String,
                default: Double,
            ) = values[name]?.toDouble()?.also { require(it >= 0) { "$name must be >= 0" } } ?: default
            return MockConfig(
                host = values["host"] ?: d.host,
                port = values["port"]?.toInt() ?: d.port,
                password = values["password"] ?: d.password,
                controlEnabled = values["control"]?.toBooleanStrict() ?: d.controlEnabled,
                scenarioIntervalSec = dbl("scenario-interval", d.scenarioIntervalSec),
                seed = values["seed"]?.toLong() ?: d.seed,
                busySec = dbl("busy", d.busySec),
                visitSec = dbl("visit", d.visitSec),
                videoDelaySec = dbl("video-delay", d.videoDelaySec),
                lockoutBaseSec = dbl("lockout-base", d.lockoutBaseSec),
                lockoutMaxSec = dbl("lockout-max", d.lockoutMaxSec),
                heartbeatSec = dbl("heartbeat", d.heartbeatSec),
                wsQueueLimit = values["ws-queue-limit"]?.toInt() ?: d.wsQueueLimit,
                timezone = values["timezone"]?.let { ZoneId.of(it) } ?: d.timezone,
                serverVersion = values["server-version"] ?: d.serverVersion,
                tlsPort = values["tls-port"]?.toInt() ?: d.tlsPort,
                tlsNames =
                    values["tls-names"]
                        ?.split(',')
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?.also { require(it.isNotEmpty()) { "tls-names needs at least one name" } }
                        ?: d.tlsNames,
            )
        }
    }
}
