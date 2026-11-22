package org.petkitlocal.core.data.video

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** The snapshot goes to MediaStore pending, is written whole, and only then shown. */
@RunWith(RobolectricTestRunner::class)
class MediaStoreSnapshotRepositoryTest {
    private lateinit var media: FakeMediaStore
    private val repository = MediaStoreSnapshotRepository(ApplicationProvider.getApplicationContext())

    @Before
    fun setUp() {
        media = Robolectric.setupContentProvider(FakeMediaStore::class.java, MediaStore.AUTHORITY)
    }

    @Test
    fun `a snapshot is written in Pictures, pending until its bytes are all there`() = runTest {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)

        assertTrue(repository.save(jpeg, "T5 2026-11-16 20.15.03.jpg"))

        val inserted = media.inserted.single()
        assertEquals("T5 2026-11-16 20.15.03.jpg", inserted.getAsString(MediaStore.Images.Media.DISPLAY_NAME))
        assertEquals("image/jpeg", inserted.getAsString(MediaStore.Images.Media.MIME_TYPE))
        assertEquals("Pictures/Patoune", inserted.getAsString(MediaStore.Images.Media.RELATIVE_PATH))
        assertEquals(1, inserted.getAsInteger(MediaStore.Images.Media.IS_PENDING))
        assertArrayEquals(jpeg, media.file.readBytes())
        assertEquals(0, media.updated.single().getAsInteger(MediaStore.Images.Media.IS_PENDING))
    }

    @Test
    fun `nothing is saved when the store refuses the entry or the file`() = runTest {
        media.refuseInsert = true
        assertFalse(repository.save(byteArrayOf(1), "a.jpg"))

        media.refuseInsert = false
        media.refuseFile = true
        assertFalse(repository.save(byteArrayOf(1), "b.jpg"))
        // The half-made entry is removed again.
        assertEquals(1, media.deleted)
    }

    /** MediaStore as far as the repository uses it: one row, one file. */
    class FakeMediaStore : ContentProvider() {
        val inserted = mutableListOf<ContentValues>()
        val updated = mutableListOf<ContentValues>()
        var deleted = 0
        var refuseInsert = false
        var refuseFile = false
        lateinit var file: File

        override fun onCreate() = true

        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            if (refuseInsert) return null
            inserted += ContentValues(values)
            return ContentUris.withAppendedId(uri, inserted.size.toLong())
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
            if (refuseFile) return null
            file = File.createTempFile("snapshot", ".jpg").apply { deleteOnExit() }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
        }

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int {
            updated += ContentValues(values)
            return 1
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            deleted++
            return 1
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null

        override fun getType(uri: Uri): String = "image/jpeg"
    }
}
