package org.petkitlocal.core.e2e

import java.io.IOException
import org.json.JSONObject

/**
 * The mock server's control API, under `/mock` (mock-server/README.md, "Control API"): what makes
 * the mock's household change under a test, which the real server cannot be made to do. A test
 * calling it is [MockOnly]. The address, the password and API clients are [TestServer]'s, for
 * whichever server the tests run against.
 */
object MockServer {
    /** Back to the seeded household: devices, pets, history and password. Ends every session. */
    fun reset() {
        control("POST", "reset")
    }

    /** The certificates the mock's HTTPS port can present (mock-server/README.md, "Control API"). */
    enum class Certificate(val key: String) {
        /** The server's own, for 10.0.2.2: what the owner trusts. */
        PRIMARY("primary"),

        /** Another one for the same names: a renewal, or someone in the middle. */
        RENEWED("renewed"),

        /** One that names another host only. */
        WRONG_HOST("wrong-host"),
    }

    /** Makes the HTTPS port present [certificate] from its next connection on; a reset goes back to the primary. */
    fun useCertificate(certificate: Certificate) {
        control("PUT", "tls", JSONObject().put("certificate", certificate.key).toString())
    }

    /** [certificate]'s SHA-256 fingerprint, `AB:01:...`, as the app shows it. */
    fun sha256(certificate: Certificate): String =
        JSONObject(control("GET", "tls")).getJSONObject("certificates").getString(certificate.key)

    /**
     * Calls the control API: [path] is relative to `/mock` (`devices/1002/visit`, or empty for
     * `GET /mock` itself), [jsonBody] the request's JSON, if any. Returns the response body; throws
     * [IOException] for a non-2xx answer or an unreachable server.
     */
    fun control(method: String, path: String, jsonBody: String? = null): String {
        check(TestServer.kind == TestServer.Kind.MOCK) {
            "The control API is the mock's: a test calling it is @MockOnly."
        }
        val root = TestServer.baseUrl.newBuilder().addPathSegment("mock").build()
        return TestServer.call(root, method, path, jsonBody)
    }
}
