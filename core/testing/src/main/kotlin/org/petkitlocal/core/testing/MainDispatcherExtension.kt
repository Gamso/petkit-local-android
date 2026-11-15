package org.petkitlocal.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Runs `Dispatchers.Main` (what `viewModelScope` uses) on [dispatcher] for each test, so
 * ViewModel tests run on the JVM. Register with `@JvmField @RegisterExtension`.
 */
@OptIn(ExperimentalCoroutinesApi::class) // setMain/resetMain: the documented way, still marked
class MainDispatcherExtension(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) :
    BeforeEachCallback,
    AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        Dispatchers.setMain(dispatcher)
    }

    override fun afterEach(context: ExtensionContext) {
        Dispatchers.resetMain()
    }
}
