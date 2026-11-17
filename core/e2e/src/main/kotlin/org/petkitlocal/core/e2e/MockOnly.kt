package org.petkitlocal.core.e2e

/**
 * A test, or every test of a class, that runs against the mock server only: it drives the mock's
 * control API ([MockServer]) or relies on its seeded household (a Pura Max with id 1002, a pet
 * called Mochi). Against the real server ([TestServer.Kind.REAL]) [TestServerRule] skips it,
 * reporting [reason], rather than letting it fail.
 *
 * A test without it must pass against both: it reads what the server has from the server.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class MockOnly(val reason: String)
