package org.ntust.app.tigerduck.ui.screen.files

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The archive viewer's cache folder: what it keeps when files taken out pass its limit. */
class ArchiveCacheTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `past its limit the folder drops the files taken out longest ago, never the newest`() {
        val folder = temp.newFolder("session")
        val files = (0 until 4).map { index ->
            folder.resolve("item-$index.bin").apply {
                writeBytes(ByteArray(100))
                setLastModified(1_000_000L + index * 1_000)
            }
        }

        ArchiveCache.trim(folder, newest = files[3], maxBytes = 250)

        assertEquals(listOf("item-2.bin", "item-3.bin"), folder.list()!!.sorted())
    }

    @Test
    fun `the newest is kept even when it alone passes the limit`() {
        val folder = temp.newFolder("session")
        val big = folder.resolve("item-0.bin").apply { writeBytes(ByteArray(500)) }

        ArchiveCache.trim(folder, newest = big, maxBytes = 250)

        assertEquals(listOf("item-0.bin"), folder.list()!!.toList())
    }
}
