package org.petkitlocal.core.e2e

import java.util.ServiceLoader

/**
 * The app under test, as [TestServerRule] needs it: a way to sign it out on the phone before each
 * test, so every test starts signed out, with no pinned certificate and no cached data of a
 * session, whatever the test before it left.
 *
 * The server's reset alone cannot promise that. The app learns that its session ended only by
 * asking the server, and a test can leave it unable to: CertificateTrustFlowTest pins the mock's
 * renewed certificate and ends signed in, the reset puts the mock back on its first certificate,
 * and from then on the pin refuses every connection to it. The app stayed on Home under the
 * changed-certificate question, and every later test timed out waiting for the sign-in screen
 * (issue #111).
 *
 * The module whose tests launch the app provides it as a [ServiceLoader] service: a class with a
 * no-argument constructor, named in its `src/androidTest/resources/META-INF/services/` file
 * `org.petkitlocal.core.e2e.AppUnderTest`. Without one, the rule only resets the server.
 */
interface AppUnderTest {
    /**
     * Signs the app out on the phone, as its sign-out does, whether or not the server can be
     * reached or the session still stands there, and returns once it is signed out. Throws when
     * it cannot, so the test fails at once, saying so, instead of waiting for the sign-in screen.
     */
    fun signOut()

    companion object {
        /** The service the test APK provides, or null when it provides none. */
        val installed: AppUnderTest? by lazy {
            ServiceLoader.load(AppUnderTest::class.java, AppUnderTest::class.java.classLoader).firstOrNull()
        }
    }
}
