package org.ntust.app.tigerduck.debug

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerSamplesTest {

    private val extensions = mapOf("video/quicktime" to "mov", "application/pdf" to "pdf")
    private val types = extensions.entries.associate { (type, extension) -> extension to type }

    private fun name(displayName: String?, mimeType: String? = null) =
        ViewerSamples.pickedName(displayName, mimeType) { extensions[it] }

    private fun type(providerType: String?, name: String) = ViewerSamples.pickedType(providerType, name) { types[it] }

    @Test
    fun `the index reads group, name, type and size, under its header`() {
        val text = "group\tname\tmime\tbytes\nVideo\th264.mov\tvideo/quicktime\t99748\n\nEdge cases\tzero-bytes.pdf\tapplication/pdf\t0\n"

        assertEquals(
            listOf(
                ViewerSample("Video", "h264.mov", "video/quicktime", 99_748),
                ViewerSample("Edge cases", "zero-bytes.pdf", "application/pdf", 0),
            ),
            ViewerSamples.parseIndex(text),
        )
    }

    @Test
    fun `a gzip sample is stored under another name, which the asset merge leaves alone`() {
        assertEquals("archive.tar.gz.sample", ViewerSamples.assetName("archive.tar.gz"))
        assertEquals("readme.txt.gz.sample", ViewerSamples.assetName("readme.txt.gz"))
        assertEquals("archive.tar", ViewerSamples.assetName("archive.tar"))
        assertEquals("h264.mov", ViewerSamples.assetName("h264.mov"))
    }

    @Test
    fun `a picked file keeps its own name`() {
        assertEquals("IMG_0042.MOV", name("IMG_0042.MOV"))
        assertEquals("作業 1.pdf", name("作業 1.pdf"))
    }

    @Test
    fun `a picked name never reaches outside the copy's folder`() {
        assertEquals("x.mov", name("../../files/x.mov"))
        assertEquals("c.txt", name("a\\b\\c.txt"))
        assertEquals("picked.mov", name("..", "video/quicktime"))
    }

    @Test
    fun `a picked name loses control characters`() {
        assertEquals("ab.pdf", name("a\u0000\nb.pdf"))
    }

    @Test
    fun `a picked file with no name is named for its type`() {
        assertEquals("picked.mov", name(null, "video/quicktime"))
        assertEquals("picked.pdf", name("  ", "application/pdf"))
        assertEquals("picked", name(null, "application/x-unknown"))
    }

    @Test
    fun `a long picked name is cut short, its extension kept`() {
        val picked = name("a".repeat(300) + ".mov")

        assertEquals(120, picked.length)
        assertEquals(".mov", picked.takeLast(4))
    }

    @Test
    fun `a picked file opens as the type its provider gives`() {
        assertEquals("video/quicktime", type("video/quicktime", "clip.mp4"))
        assertEquals("application/octet-stream", type("application/octet-stream", "clip.mov"))
    }

    @Test
    fun `with no type from its provider, the name's extension gives it`() {
        assertEquals("video/quicktime", type(null, "clip.MOV"))
        assertEquals("application/pdf", type(" ", "a.pdf"))
        assertEquals("application/octet-stream", type(null, "a.unknown"))
    }
}
