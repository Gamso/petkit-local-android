package org.petkitlocal.core.e2e

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Prepares each test that talks to the mock server:
 *
 * - grants the app the local network permission, which Android 17 requires before an app can
 *   reach a private address such as 10.0.2.2 (the app under test must declare it; the debug
 *   build does);
 * - resets the mock ([MockServer.reset]), so every test starts from the seeded household with no
 *   session, whatever the test before it changed.
 *
 * A mock that cannot be reached fails the test at once, saying how to start it, rather than
 * leaving each call to time out on its own.
 *
 * ```
 * @get:Rule val mock = MockServerRule()
 * ```
 */
class MockServerRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            grantLocalNetworkAccess()
            try {
                MockServer.reset()
            } catch (e: IOException) {
                throw AssertionError(
                    "The mock server at ${MockServer.baseUrl} could not be reset. Start it with " +
                        "`docker compose -f e2e/docker-compose.yml up --build`, or pass its address " +
                        "as the instrumentation argument ${MockServer.URL_ARGUMENT}.",
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

    private companion object {
        /** Android 17, the first release that enforces local network protection. */
        const val LOCAL_NETWORK_PROTECTION_SDK = 37
        const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
