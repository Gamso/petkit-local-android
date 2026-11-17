package org.petkitlocal.core.e2e

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Prepares each test that talks to the server ([TestServer]):
 *
 * - skips a [MockOnly] test when the server is the real one: it is reported as skipped, with
 *   the annotation's reason, not failed;
 * - grants the app the local network permission, which Android 17 requires before an app can
 *   reach a private address such as 10.0.2.2 (the app under test must declare it; the debug
 *   build does), and Android 13's notification permission, so Home shows no banner asking for it
 *   and the flows see the screens as a user who allowed it does;
 * - resets the server ([TestServer.reset]), so every test starts with no session and the
 *   configured password, and on the mock from the seeded household, whatever the test before
 *   it changed.
 *
 * A server that cannot be reached fails the test at once, saying how to start it, rather than
 * leaving each call to time out on its own.
 *
 * ```
 * @get:Rule val server = TestServerRule()
 * ```
 */
class TestServerRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val mockOnly = description.getAnnotation(MockOnly::class.java)
                ?: description.testClass?.getAnnotation(MockOnly::class.java)
            if (mockOnly != null && TestServer.kind != TestServer.Kind.MOCK) {
                throw AssumptionViolatedException("Runs against the mock only: ${mockOnly.reason}")
            }
            grantLocalNetworkAccess()
            grantNotifications()
            try {
                TestServer.reset()
            } catch (e: IOException) {
                throw AssertionError(
                    "The ${TestServer.kind.name.lowercase()} server at ${TestServer.baseUrl} could not be reset. " +
                        "Start the mock with `docker compose -f e2e/docker-compose.yml up --build`, or the " +
                        "real server with `scripts/real-server.sh up`, and pass its address as the " +
                        "instrumentation argument ${TestServer.URL_ARGUMENT}.",
                    e,
                )
            }
            base.evaluate()
        }
    }

    private fun grantLocalNetworkAccess() {
        if (Build.VERSION.SDK_INT < LOCAL_NETWORK_PROTECTION_SDK) return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            ACCESS_LOCAL_NETWORK,
        )
    }

    private fun grantNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            POST_NOTIFICATIONS,
        )
    }

    private companion object {
        /** Android 17, the first release that enforces local network protection. */
        const val LOCAL_NETWORK_PROTECTION_SDK = 37
        const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
        const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    }
}
