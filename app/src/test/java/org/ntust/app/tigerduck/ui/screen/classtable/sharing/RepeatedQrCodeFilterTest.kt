package org.ntust.app.tigerduck.ui.screen.classtable.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A code kept in front of the camera is passed on once the import takes it, and again only after
 * it left the picture; one the import refused, as while a dialog is over the scanner, is passed
 * on again at the next frame.
 */
class RepeatedQrCodeFilterTest {

    @Test
    fun `a code read on frame after frame passes once`() {
        val filter = RepeatedQrCodeFilter()

        assertTrue(filter.isNew("A", 0))
        assertFalse(filter.isNew("A", 33))
        assertFalse(filter.isNew("A", 1_000))
        assertFalse(filter.isNew("A", 1_900))
    }

    @Test
    fun `a code out of view for over a second passes again, and other codes pass meanwhile`() {
        val filter = RepeatedQrCodeFilter()

        assertTrue(filter.isNew("A", 0))
        assertTrue(filter.isNew("B", 500))
        assertTrue(filter.isNew("A", 1_001))
        assertFalse(filter.isNew("B", 1_400))
    }

    @Test
    fun `a code the import refused passes again on the next frame, and once taken is held`() {
        val filter = RepeatedQrCodeFilter()

        assertTrue(filter.isNew("A", 0))
        filter.refused("A")
        assertTrue(filter.isNew("A", 33))
        assertFalse(filter.isNew("A", 66))
    }

    @Test
    fun `a code in view while Enter link is open scans as soon as the dialog closes`() {
        val filter = RepeatedQrCodeFilter()
        var dialogOpen = true
        val taken = mutableListOf<String>()
        // As the camera hands codes over: on another thread, which a direct executor stands in for.
        val handOff = QrCodeHandOff(filter, post = Runnable::run) { text ->
            if (dialogOpen) false else true.also { taken += text }
        }

        for (frame in 0 until 60) handOff.read("A", frame * 33L)
        assertTrue(taken.isEmpty())

        dialogOpen = false
        handOff.read("A", 60 * 33L)
        assertEquals(listOf("A"), taken)

        // Taken: held while it stays in view, so cancelling its confirm step does not reopen it.
        for (frame in 61 until 120) handOff.read("A", frame * 33L)
        assertEquals(listOf("A"), taken)
    }
}
