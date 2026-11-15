package org.petkitlocal.core.network

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient

/**
 * Where Media3 reads `GET /media/{path}` (clips, and later the live HLS fallback): through the
 * app's one OkHttp client, so every request carries the bearer header to the user's server and
 * nowhere else, never a token in the URL (SPEC.md §8). Seeking asks for a `Range` from the
 * position sought, which the server answers with 206.
 */
object MediaDataSources {
    @OptIn(UnstableApi::class) // OkHttpDataSource is still marked unstable in Media3.
    fun authenticated(client: OkHttpClient): DataSource.Factory = OkHttpDataSource.Factory(client)
}
