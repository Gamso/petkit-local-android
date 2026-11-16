package org.petkitlocal.core.data.video

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.petkitlocal.core.domain.repository.SnapshotRepository

/**
 * Snapshots in the phone's shared pictures, `Pictures/Petkit Local/`, through MediaStore: on
 * Android 10 and later (the app's minimum) an app adds its own files there with no storage
 * permission. The entry stays pending, hidden from the gallery, until the whole file is written,
 * and is removed again if writing fails.
 */
class MediaStoreSnapshotRepository @Inject constructor(@ApplicationContext private val context: Context) :
    SnapshotRepository {
    override suspend fun save(jpeg: ByteArray, fileName: String): Boolean = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val pending = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, JPEG)
            put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), pending)
            ?: return@withContext false
        try {
            val stream = resolver.openOutputStream(uri) ?: throw IOException("no stream for $uri")
            stream.use { it.write(jpeg) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            true
        } catch (_: IOException) {
            resolver.delete(uri, null, null)
            false
        }
    }

    companion object {
        const val JPEG = "image/jpeg"

        /** The app's own folder among the phone's pictures. */
        val RELATIVE_PATH = "${Environment.DIRECTORY_PICTURES}/Petkit Local"
    }
}
