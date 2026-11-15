package org.petkitlocal.core.network

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Integration: Media3 reading a clip through the app's client, as the History player does. The
 * bearer header goes in the header only, and a seek is a `Range` request answered with 206.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class MediaDataSourcesTest {
    private val server = MockWebServer()
    private lateinit var base: HttpUrl

    @Before
    fun setUp() {
        server.start()
        base = server.url("/petkit/app/v1/")
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `a clip read from a position asks for that range, with the bearer header and no token in the URL`() {
        server.enqueue(
            MockResponse.Builder()
                .code(206)
                .addHeader("Content-Range", "bytes 4-9/10")
                .addHeader("Content-Type", "video/mp4")
                .body("456789")
                .build(),
        )
        val client = PetkitHttpClient.okHttpClient({ base }, { "secret-token" })
        val source = MediaDataSources.authenticated(client).createDataSource()
        val url = base.resolve("media/t5/clip.mp4").toString()

        val length = source.open(DataSpec.Builder().setUri(url).setPosition(4).build())
        val bytes = ByteArray(6)
        var read = 0
        while (read < bytes.size) read += source.read(bytes, read, bytes.size - read)
        source.close()

        assertEquals(6L, length)
        assertEquals("456789", bytes.decodeToString())
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/petkit/app/v1/media/t5/clip.mp4", request.url.encodedPath)
        assertNull(request.url.encodedQuery)
        assertEquals("Bearer secret-token", request.headers["Authorization"])
        assertEquals("bytes=4-", request.headers["Range"])
    }
}
