package org.ntust.app.tigerduck.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ntust.app.tigerduck.files.FileOpening.InApp
import org.ntust.app.tigerduck.files.FileOpening.OtherApp
import org.ntust.app.tigerduck.files.FileOpening.ShareOnly
import java.io.ByteArrayInputStream

class FileViewingTest {

    private fun opening(type: String, name: String) = FileViewing.opening(type, name)

    @Test
    fun `a pdf, an image, a video, a sound, text and markdown open here`() {
        assertEquals(InApp(ViewerKind.PDF), opening("application/pdf", "week 3.pdf"))
        assertEquals(InApp(ViewerKind.IMAGE), opening("image/jpeg", "board.jpg"))
        assertEquals(InApp(ViewerKind.IMAGE), opening("image/heic", "board.heic"))
        assertEquals(InApp(ViewerKind.VIDEO), opening("video/quicktime", "demo.mov"))
        assertEquals(InApp(ViewerKind.VIDEO), opening("video/mp4", "demo.mp4"))
        assertEquals(InApp(ViewerKind.AUDIO), opening("audio/mp4", "lecture.m4a"))
        assertEquals(InApp(ViewerKind.AUDIO), opening("audio/ogg", "lecture.ogg"))
        assertEquals(InApp(ViewerKind.TEXT), opening("text/plain", "notes.txt"))
        assertEquals(InApp(ViewerKind.TEXT), opening("application/json", "data.json"))
        assertEquals(InApp(ViewerKind.TEXT), opening("text/x-java-source", "Main.java"))
        assertEquals(InApp(ViewerKind.MARKDOWN), opening("text/markdown", "README.md"))
    }

    @Test
    fun `word and powerpoint files open here, the old binary formats in another app`() {
        assertEquals(
            InApp(ViewerKind.DOCX),
            opening("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "report.docx"),
        )
        assertEquals(
            InApp(ViewerKind.PPTX),
            opening("application/vnd.openxmlformats-officedocument.presentationml.presentation", "slides.pptx"),
        )
        assertEquals(OtherApp, opening("application/msword", "report.doc"))
        assertEquals(OtherApp, opening("application/vnd.ms-powerpoint", "slides.ppt"))
        assertEquals(
            OtherApp,
            opening("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "grades.xlsx"),
        )
    }

    @Test
    fun `wma and anything else go to another app`() {
        assertEquals(OtherApp, opening("audio/x-ms-wma", "song.wma"))
        assertEquals(OtherApp, opening("application/zip", "code.zip"))
        assertEquals(OtherApp, opening("application/vnd.android.package-archive", "app.apk"))
    }

    @Test
    fun `web pages, svg and xml never render here, by type or by name`() {
        assertEquals(ShareOnly, opening("text/html", "page.html"))
        assertEquals(ShareOnly, opening("application/xhtml+xml", "page.xhtml"))
        assertEquals(ShareOnly, opening("image/svg+xml", "logo.svg"))
        assertEquals(ShareOnly, opening("text/xml", "data.xml"))
        assertEquals(ShareOnly, opening("application/xml", "data.xml"))
        // Named as a page, whatever type it was sent with.
        assertEquals(ShareOnly, opening("text/plain", "page.html"))
        assertEquals(ShareOnly, opening("image/png", "logo.svg"))
    }

    @Test
    fun `a generic type opens by the name's extension`() {
        assertEquals(InApp(ViewerKind.PDF), opening("application/octet-stream", "week 3.pdf"))
        assertEquals(InApp(ViewerKind.DOCX), opening("document/unknown", "report.docx"))
        assertEquals(InApp(ViewerKind.TEXT), opening("", "notes.log"))
        assertEquals(OtherApp, opening("application/octet-stream", "blob.bin"))
    }

    @Test
    fun `a declared type is believed over the name, as attachments are`() {
        // An installer named like a picture still goes to other apps, which decide for themselves.
        assertEquals(OtherApp, opening("application/vnd.android.package-archive", "photo.png"))
        // A picture named like a pdf is shown as the picture it says it is; if it isn't, the viewer says so.
        assertEquals(InApp(ViewerKind.IMAGE), opening("image/png; name=x", "scan.pdf"))
    }

    @Test
    fun `text is read up to the limit, and says when there is more`() {
        val small = TextPreviews.read(ByteArrayInputStream("hello".toByteArray()), limitBytes = 10)
        assertEquals(TextPreview("hello", truncated = false), small)

        val big = TextPreviews.read(ByteArrayInputStream("0123456789abc".toByteArray()), limitBytes = 10)
        assertEquals(TextPreview("0123456789", truncated = true), big)
    }

    @Test
    fun `a character cut at the limit is left out, not garbled`() {
        // "中" is 3 bytes in UTF-8: 4 bytes of "a中中" end inside the first one.
        val preview = TextPreviews.read(ByteArrayInputStream("a中中".toByteArray()), limitBytes = 5)
        assertEquals(TextPreview("a中", truncated = true), preview)
    }

    @Test
    fun `big5 text, which school systems still send, reads as big5`() {
        val bytes = "課程大綱".toByteArray(charset("Big5"))
        assertEquals("課程大綱", TextPreviews.read(ByteArrayInputStream(bytes)).text)
    }

    @Test
    fun `a byte order mark picks the encoding, and is left out`() {
        val utf8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "ok".toByteArray()
        assertEquals("ok", TextPreviews.read(ByteArrayInputStream(utf8)).text)
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "ok".toByteArray(Charsets.UTF_16LE)
        assertEquals("ok", TextPreviews.read(ByteArrayInputStream(utf16)).text)
    }

    @Test
    fun `the page shown is the one at the top, counted from one`() {
        assertEquals(1 to 12, PdfPages.position(firstVisible = 0, count = 12))
        assertEquals(12 to 12, PdfPages.position(firstVisible = 11, count = 12))
        assertEquals(0 to 0, PdfPages.position(firstVisible = 0, count = 0))
    }

    @Test
    fun `a page is drawn as tall as its shape at the screen's width, and rendered sharper`() {
        // A4 in points: 595 × 842.
        assertEquals(1528, PdfPages.displayHeight(pageWidth = 595, pageHeight = 842, width = 1080))
        assertEquals(1620, PdfPages.renderWidth(viewWidth = 1080))
        assertEquals(PdfPages.MAX_RENDER_WIDTH, PdfPages.renderWidth(viewWidth = 4000))
        assertTrue(PdfPages.renderWidth(viewWidth = 0) > 0)
        assertFalse(PdfPages.displayHeight(pageWidth = 0, pageHeight = 842, width = 1080) < 0)
    }
}
