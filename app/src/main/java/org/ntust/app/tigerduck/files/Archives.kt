package org.ntust.app.tigerduck.files

import org.apache.commons.compress.MemoryLimitException
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.archivers.zip.ZipMethod
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.channels.ClosedByInterruptException
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.file.StandardOpenOption

/** The kinds of archive the viewer reads, told by their first bytes ([Archives.format]). */
enum class ArchiveFormat { ZIP, SEVEN_Z, TAR, GZIP, BZIP2, XZ }

/** Why an archive, or a file in one, isn't shown. */
enum class ArchiveProblem {
    /** Encrypted. */
    PASSWORD,

    /** Packed with a method the app doesn't read, or an archive of a kind it doesn't. */
    UNSUPPORTED,

    /** Larger than the limits, packed far tighter than real files are (a bomb, likely), or too slow to read. */
    TOO_LARGE,

    /** More entries than the app reads at all. */
    TOO_MANY,

    /** A link, with nothing of its own to show. */
    LINK,

    /** Damaged, or not what its first bytes say. */
    BROKEN,
}

class ArchiveProblemException(val problem: ArchiveProblem, cause: Throwable? = null) : IOException(problem.name, cause)

/**
 * One entry of an archive, as the viewer lists it: [index] finds it again, [path] is shown
 * (with `/` between folders), and [problem] says why it can't be opened, if it can't.
 */
data class ArchiveItem(
    val index: Int,
    val path: String,
    val size: Long?,
    val modified: Long?,
    val isDirectory: Boolean,
    val problem: ArchiveProblem?,
)

/** An archive's entries, the first [ArchiveLimits.maxItems] of them: [truncated] when there were more. */
data class ArchiveListing(val format: ArchiveFormat, val items: List<ArchiveItem>, val truncated: Boolean)

/**
 * An archive to read, [size] bytes, opened afresh by [open] for each pass. [name] names a file
 * packed without one: a 7z's unnamed entry, or the one file in a `.gz`.
 */
class ArchiveSource(val name: String, val size: Long, val open: () -> SeekableByteChannel) {
    companion object {
        fun of(file: File) = ArchiveSource(file.name, file.length()) { FileChannel.open(file.toPath(), StandardOpenOption.READ) }
    }
}

/**
 * What one listing or one file taken out may cost. Every byte count is of what's actually read,
 * never of the sizes an archive claims.
 */
data class ArchiveLimits(
    /** The most entries listed. */
    val maxItems: Int = 5_000,
    /** The most a zip's directory may declare; more, and it isn't read at all. */
    val maxZipEntries: Long = 100_000,
    /** The largest file taken out. */
    val maxEntryBytes: Long = 256L shl 20,
    /** How many times its packed size a file taken out may grow to, past [ratioFreeBytes]. */
    val maxRatio: Long = 100,
    val ratioFreeBytes: Long = 16L shl 20,
    /** The most decompressed bytes one pass over a compressed tar reads, to list it or reach a file. */
    val maxScanBytes: Long = 2L shl 30,
    /** The most memory, in KiB, a 7z or an xz may ask its decoder for. */
    val maxMemoryKiB: Int = 256 shl 10,
    val timeLimitMillis: Long = 60_000,
    val now: () -> Long = System::currentTimeMillis,
)

/**
 * Reads the archives the file viewer shows: ZIP, 7z, and tar, plain or in gzip, bzip2 or xz, or
 * one file compressed on its own. Lists what's inside, and takes out one file at a time into a
 * folder of the caller's, under a name of the app's that keeps only the extension: no path
 * an archive claims is ever written.
 */
object Archives {

    fun format(head: ByteArray): ArchiveFormat? = when {
        head.startsWith(0x50, 0x4B, 0x03, 0x04) || head.startsWith(0x50, 0x4B, 0x05, 0x06) ||
            head.startsWith(0x50, 0x4B, 0x07, 0x08) -> ArchiveFormat.ZIP
        head.startsWith(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> ArchiveFormat.SEVEN_Z
        head.startsWith(0x1F, 0x8B) -> ArchiveFormat.GZIP
        head.startsWith(0x42, 0x5A, 0x68) -> ArchiveFormat.BZIP2
        head.startsWith(0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00) -> ArchiveFormat.XZ
        isTar(head, head.size) -> ArchiveFormat.TAR
        else -> null
    }

    fun list(source: ArchiveSource, limits: ArchiveLimits = ArchiveLimits()): ArchiveListing {
        val deadline = limits.now() + limits.timeLimitMillis
        return guarded {
            when (val format = formatOf(source)) {
                ArchiveFormat.ZIP -> listZip(source, limits, deadline)
                ArchiveFormat.SEVEN_Z -> list7z(source, limits, deadline)
                else -> listStreamed(source, format, limits, deadline)
            }
        }
    }

    /** Takes [item] out of [source] into [into] and returns the file; nothing is left there if it fails. */
    fun extract(source: ArchiveSource, item: ArchiveItem, into: File, limits: ArchiveLimits = ArchiveLimits()): File {
        item.problem?.let { throw ArchiveProblemException(it) }
        if (item.isDirectory) throw ArchiveProblemException(ArchiveProblem.UNSUPPORTED)
        val deadline = limits.now() + limits.timeLimitMillis
        val extension = ArchiveNames.extension(item.path)
        val target = File(into, "item-${item.index}" + if (extension.isEmpty()) "" else ".$extension")
        val partial = File(into, "${target.name}.part")
        try {
            guarded {
                partial.outputStream().use { out ->
                    when (val format = formatOf(source)) {
                        ArchiveFormat.ZIP -> extractZip(source, item, out, limits, deadline)
                        ArchiveFormat.SEVEN_Z -> extract7z(source, item, out, limits, deadline)
                        else -> extractStreamed(source, format, item, out, limits, deadline)
                    }
                }
            }
            if (!partial.renameTo(target)) throw IOException("Couldn't keep the file taken out")
            return target
        } finally {
            partial.delete()
        }
    }

    // ZIP

    private fun listZip(source: ArchiveSource, limits: ArchiveLimits, deadline: Long): ArchiveListing =
        openZip(source, limits).use { zip ->
            val items = ArrayList<ArchiveItem>()
            var truncated = false
            for ((index, entry) in zip.entries.asSequence().withIndex()) {
                limits.check(deadline)
                val path = ArchiveNames.normalize(zipName(entry))
                if (path.isEmpty()) continue
                if (items.size == limits.maxItems) {
                    truncated = true
                    break
                }
                items += ArchiveItem(
                    index = index,
                    path = path,
                    size = entry.size.takeIf { it >= 0 && !entry.isDirectory },
                    modified = entry.time.takeIf { it > 0 },
                    isDirectory = entry.isDirectory,
                    problem = zipProblem(zip, entry),
                )
            }
            ArchiveListing(ArchiveFormat.ZIP, items, truncated)
        }

    private fun extractZip(source: ArchiveSource, item: ArchiveItem, out: OutputStream, limits: ArchiveLimits, deadline: Long) {
        openZip(source, limits).use { zip ->
            val entry = zip.entries.asSequence().elementAtOrNull(item.index) ?: throw ArchiveProblemException(ArchiveProblem.BROKEN)
            zipProblem(zip, entry)?.let { throw ArchiveProblemException(it) }
            val packed = entry.compressedSize.takeIf { it > 0 } ?: source.size
            zip.getInputStream(entry).use { copyLimited(it, out, packed, limits, deadline) }
        }
    }

    private fun openZip(source: ArchiveSource, limits: ArchiveLimits): ZipFile {
        val channel = source.open()
        try {
            val declared = declaredZipEntries(channel)
            if (declared != null && declared > limits.maxZipEntries) throw ArchiveProblemException(ArchiveProblem.TOO_MANY)
            channel.position(0)
            return ZipFile.builder()
                .setSeekableByteChannel(channel)
                .setUseUnicodeExtraFields(true)
                .setIgnoreLocalFileHeader(true)
                .get()
        } catch (e: Throwable) {
            channel.close()
            throw e
        }
    }

    private fun zipName(entry: ZipArchiveEntry): String {
        // A name the archive also wrote as Unicode, which the reader has checked against the original.
        if (entry.nameSource == ZipArchiveEntry.NameSource.UNICODE_EXTRA_FIELD) return entry.name
        val raw = entry.rawName ?: return entry.name
        return ArchiveNames.zipName(raw, utf8 = entry.generalPurposeBit.usesUTF8ForNames())
    }

    private fun zipProblem(zip: ZipFile, entry: ZipArchiveEntry): ArchiveProblem? = when {
        entry.isDirectory -> null
        entry.generalPurposeBit.usesEncryption() || entry.method == ZipMethod.AES_ENCRYPTED.code -> ArchiveProblem.PASSWORD
        entry.isUnixSymlink -> ArchiveProblem.LINK
        // Zstandard needs a native library the app doesn't ship.
        entry.method in ZSTD_METHODS || !zip.canReadEntryData(entry) -> ArchiveProblem.UNSUPPORTED
        else -> null
    }

    /**
     * The entry count a zip's end of central directory declares, read before its directory is,
     * so a zip of a million entries is turned away before they're all in memory. Null when
     * there's no such record, which the zip reader then reports.
     */
    internal fun declaredZipEntries(channel: SeekableByteChannel): Long? {
        val size = channel.size()
        val tailSize = minOf(size, END_RECORD_SIZE + 0xFFFFL).toInt()
        val tail = read(channel, size - tailSize, tailSize) ?: return null
        for (at in tailSize - END_RECORD_SIZE.toInt() downTo 0) {
            if (tail.getInt(at) != END_SIGNATURE) continue
            val entries = (tail.getShort(at + 10).toInt() and 0xFFFF).toLong()
            if (entries != 0xFFFFL) return entries
            // A Zip64 count: its locator sits just before, and points at the record that holds it.
            val locator = read(channel, size - tailSize + at - 20, 20) ?: return entries
            if (locator.getInt(0) != ZIP64_LOCATOR_SIGNATURE) return entries
            val recordAt = locator.getLong(8)
            if (recordAt !in 0..(size - 56)) return entries
            val record = read(channel, recordAt, 56) ?: return entries
            return if (record.getInt(0) == ZIP64_END_SIGNATURE) record.getLong(32) else entries
        }
        return null
    }

    private fun read(channel: SeekableByteChannel, at: Long, count: Int): ByteBuffer? {
        if (at < 0 || count <= 0) return null
        val buffer = ByteBuffer.allocate(count).order(ByteOrder.LITTLE_ENDIAN)
        channel.position(at)
        while (buffer.hasRemaining()) if (channel.read(buffer) < 0) return null
        return buffer.flip() as ByteBuffer
    }

    private val ZSTD_METHODS = setOf(ZipMethod.ZSTD.code, ZipMethod.ZSTD_DEPRECATED.code)

    private const val END_RECORD_SIZE = 22L
    private const val END_SIGNATURE = 0x06054B50
    private const val ZIP64_LOCATOR_SIGNATURE = 0x07064B50
    private const val ZIP64_END_SIGNATURE = 0x06064B50

    // 7z

    private fun list7z(source: ArchiveSource, limits: ArchiveLimits, deadline: Long): ArchiveListing =
        open7z(source, limits).use { sevenZ ->
            val items = ArrayList<ArchiveItem>()
            var truncated = false
            for ((index, entry) in sevenZ.entries.withIndex()) {
                limits.check(deadline)
                // An anti-item deletes a file from an earlier archive: nothing to show.
                if (entry.isAntiItem) continue
                val path = ArchiveNames.normalize(entry.name ?: defaultName(source))
                if (path.isEmpty()) continue
                if (items.size == limits.maxItems) {
                    truncated = true
                    break
                }
                items += ArchiveItem(
                    index = index,
                    path = path,
                    size = entry.size.takeIf { !entry.isDirectory },
                    modified = if (entry.hasLastModifiedDate) entry.lastModifiedDate.time else null,
                    isDirectory = entry.isDirectory,
                    // Whether a file needs a password, or a method the app lacks, shows when it's read.
                    problem = if (!entry.isDirectory && isWindowsOrUnixLink(entry.hasWindowsAttributes, entry.windowsAttributes)) ArchiveProblem.LINK else null,
                )
            }
            ArchiveListing(ArchiveFormat.SEVEN_Z, items, truncated)
        }

    private fun extract7z(source: ArchiveSource, item: ArchiveItem, out: OutputStream, limits: ArchiveLimits, deadline: Long) {
        open7z(source, limits).use { sevenZ ->
            val entry = sevenZ.entries.elementAtOrNull(item.index) ?: throw ArchiveProblemException(ArchiveProblem.BROKEN)
            if (entry.isDirectory || entry.isAntiItem) throw ArchiveProblemException(ArchiveProblem.UNSUPPORTED)
            if (!entry.hasStream()) return
            // A file in a solid block is reached by reading the ones before it: those bytes count too.
            sevenZ.getInputStream(entry).use { copyLimited(it, out, source.size, limits, deadline) }
        }
    }

    private fun open7z(source: ArchiveSource, limits: ArchiveLimits): SevenZFile {
        val channel = source.open()
        try {
            return SevenZFile.builder()
                .setSeekableByteChannel(channel)
                .setDefaultName(defaultName(source))
                .setUseDefaultNameForUnnamedEntries(true)
                .setMaxMemoryLimitKiB(limits.maxMemoryKiB)
                .get()
        } catch (e: Throwable) {
            channel.close()
            throw e
        }
    }

    /** A 7z entry stored as a link: by its Unix mode, kept in the high half, or as a Windows reparse point. */
    private fun isWindowsOrUnixLink(has: Boolean, attributes: Int): Boolean {
        if (!has) return false
        val unix = attributes and UNIX_EXTENSION != 0 && (attributes ushr 16) and 0xF000 == 0xA000
        return unix || attributes and REPARSE_POINT != 0
    }

    private const val UNIX_EXTENSION = 0x8000
    private const val REPARSE_POINT = 0x400

    // tar, and gzip, bzip2 or xz around a tar or a file of its own

    private fun listStreamed(source: ArchiveSource, format: ArchiveFormat, limits: ArchiveLimits, deadline: Long): ArchiveListing =
        streamed(source, format, limits, deadline) { pass ->
            val tar = pass.tar ?: return@streamed ArchiveListing(
                format,
                listOf(ArchiveItem(0, pass.singleName, null, pass.singleModified, isDirectory = false, problem = null)),
                truncated = false,
            )
            val items = ArrayList<ArchiveItem>()
            var truncated = false
            var index = 0
            while (true) {
                limits.check(deadline)
                val entry = tar.nextEntry ?: break
                val path = ArchiveNames.normalize(entry.name)
                val at = index++
                if (path.isEmpty()) continue
                if (items.size == limits.maxItems) {
                    truncated = true
                    break
                }
                items += ArchiveItem(
                    index = at,
                    path = path,
                    size = entry.size.takeIf { !entry.isDirectory && it >= 0 },
                    modified = entry.lastModifiedDate?.time?.takeIf { it > 0 },
                    isDirectory = entry.isDirectory,
                    problem = tarProblem(entry),
                )
            }
            ArchiveListing(format, items, truncated)
        }

    private fun extractStreamed(
        source: ArchiveSource,
        format: ArchiveFormat,
        item: ArchiveItem,
        out: OutputStream,
        limits: ArchiveLimits,
        deadline: Long,
    ) {
        streamed(source, format, limits, deadline) { pass ->
            val tar = pass.tar
            if (tar == null) {
                copyLimited(pass.stream, out, source.size, limits, deadline)
                return@streamed
            }
            var index = 0
            while (true) {
                limits.check(deadline)
                val entry = tar.nextEntry ?: throw ArchiveProblemException(ArchiveProblem.BROKEN)
                if (index++ != item.index) continue
                tarProblem(entry)?.let { throw ArchiveProblemException(it) }
                if (entry.isDirectory) throw ArchiveProblemException(ArchiveProblem.UNSUPPORTED)
                copyLimited(tar, out, source.size, limits, deadline)
                return@streamed
            }
        }
    }

    private fun tarProblem(entry: TarArchiveEntry): ArchiveProblem? = when {
        entry.isSymbolicLink || entry.isLink -> ArchiveProblem.LINK
        entry.isCharacterDevice || entry.isBlockDevice || entry.isFIFO -> ArchiveProblem.UNSUPPORTED
        else -> null
    }

    /** One pass over a streamed archive: a tar's entries, or the one file a compressor holds. */
    private class Pass(val stream: InputStream, val tar: TarArchiveInputStream?, val singleName: String, val singleModified: Long?)

    private inline fun <T> streamed(
        source: ArchiveSource,
        format: ArchiveFormat,
        limits: ArchiveLimits,
        deadline: Long,
        block: (Pass) -> T,
    ): T = source.open().use { channel ->
        val raw = BufferedInputStream(Channels.newInputStream(channel), BUFFER)
        var packedName: String? = null
        var packedModified: Long? = null
        val unpacked: InputStream = when (format) {
            ArchiveFormat.TAR -> raw
            ArchiveFormat.GZIP -> GzipCompressorInputStream(raw, true).also { gzip ->
                packedName = gzip.metaData.fileName
                packedModified = gzip.metaData.modificationTime.takeIf { it > 0 }
            }
            ArchiveFormat.BZIP2 -> BZip2CompressorInputStream(raw, true)
            ArchiveFormat.XZ -> XZCompressorInputStream(raw, true, limits.maxMemoryKiB)
            else -> throw ArchiveProblemException(ArchiveProblem.UNSUPPORTED)
        }
        // A plain tar costs nothing to pass over; a compressed one costs every byte it holds.
        val counted = BufferedInputStream(Counted(unpacked, limits, deadline, if (format == ArchiveFormat.TAR) null else limits.maxScanBytes), BUFFER)
        counted.use {
            val tar = if (format == ArchiveFormat.TAR || startsAsTar(counted)) TarArchiveInputStream(counted, "UTF-8") else null
            val name = packedName?.let(ArchiveNames::normalize)?.substringAfterLast('/')?.takeIf { it.isNotEmpty() } ?: defaultName(source)
            block(Pass(counted, tar, name, packedModified))
        }
    }

    private fun startsAsTar(stream: BufferedInputStream): Boolean {
        stream.mark(TAR_HEADER)
        val head = ByteArray(TAR_HEADER)
        var read = 0
        while (read < head.size) {
            val n = stream.read(head, read, head.size - read)
            if (n < 0) break
            read += n
        }
        stream.reset()
        return isTar(head, read)
    }

    private fun isTar(head: ByteArray, length: Int): Boolean =
        length >= TAR_MAGIC_AT + 5 && "ustar".indices.all { head[TAR_MAGIC_AT + it] == "ustar"[it].code.toByte() }

    private const val TAR_HEADER = 512
    private const val TAR_MAGIC_AT = 257

    /** Counts what's read through it, and stops a pass past [limit] bytes or the deadline. */
    private class Counted(input: InputStream, private val limits: ArchiveLimits, private val deadline: Long, private val limit: Long?) :
        FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int {
            limits.check(deadline)
            return super.read().also { if (it >= 0) counted(1) }
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            limits.check(deadline)
            return super.read(b, off, len).also { if (it > 0) counted(it.toLong()) }
        }

        override fun skip(n: Long): Long {
            limits.check(deadline)
            return super.skip(n).also { if (it > 0) counted(it) }
        }

        private fun counted(bytes: Long) {
            count += bytes
            if (limit != null && count > limit) throw ArchiveProblemException(ArchiveProblem.TOO_LARGE)
        }
    }

    // Shared

    private fun formatOf(source: ArchiveSource): ArchiveFormat = source.open().use { channel ->
        val head = ByteBuffer.allocate(TAR_HEADER)
        while (head.hasRemaining()) if (channel.read(head) < 0) break
        format(head.array().copyOf(head.position())) ?: throw ArchiveProblemException(ArchiveProblem.UNSUPPORTED)
    }

    private fun defaultName(source: ArchiveSource): String {
        val name = source.name.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        return (if (dot > 0) name.substring(0, dot) else name).ifEmpty { "file" }
    }

    /** Copies [input] to [output], stopping past the size limit or [packed] × the ratio limit. */
    internal fun copyLimited(input: InputStream, output: OutputStream, packed: Long, limits: ArchiveLimits, deadline: Long) {
        val buffer = ByteArray(BUFFER)
        var written = 0L
        val ratioLimit = packed.coerceAtLeast(1).let { if (it > Long.MAX_VALUE / limits.maxRatio) Long.MAX_VALUE else it * limits.maxRatio }
        while (true) {
            limits.check(deadline)
            val read = input.read(buffer)
            if (read < 0) return
            written += read
            if (written > limits.maxEntryBytes || (written > limits.ratioFreeBytes && written > ratioLimit)) {
                throw ArchiveProblemException(ArchiveProblem.TOO_LARGE)
            }
            output.write(buffer, 0, read)
        }
    }

    private fun ArchiveLimits.check(deadline: Long) {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Stopped")
        if (now() > deadline) throw ArchiveProblemException(ArchiveProblem.TOO_LARGE)
    }

    /** [block], its failures told as an [ArchiveProblem]; being stopped stays what it is. */
    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: ArchiveProblemException) {
        throw e
    } catch (e: InterruptedIOException) {
        throw e
    } catch (e: ClosedByInterruptException) {
        throw e
    } catch (e: PasswordRequiredException) {
        throw ArchiveProblemException(ArchiveProblem.PASSWORD, e)
    } catch (e: MemoryLimitException) {
        throw ArchiveProblemException(ArchiveProblem.TOO_LARGE, e)
    } catch (e: UnsupportedZipFeatureException) {
        val problem = if (e.feature == UnsupportedZipFeatureException.Feature.ENCRYPTION) ArchiveProblem.PASSWORD else ArchiveProblem.UNSUPPORTED
        throw ArchiveProblemException(problem, e)
    } catch (e: IOException) {
        // Commons Compress names a 7z method it lacks, PPMd for one, only in its message.
        val problem = if (e.message?.startsWith("Unsupported compression method") == true) ArchiveProblem.UNSUPPORTED else ArchiveProblem.BROKEN
        throw ArchiveProblemException(problem, e)
    } catch (e: LinkageError) {
        // A method whose decoder lives in a library the app doesn't ship.
        throw ArchiveProblemException(ArchiveProblem.UNSUPPORTED, e)
    } catch (e: OutOfMemoryError) {
        throw ArchiveProblemException(ArchiveProblem.TOO_LARGE, e)
    } catch (e: RuntimeException) {
        // A damaged archive can trip the readers' own checks.
        throw ArchiveProblemException(ArchiveProblem.BROKEN, e)
    }

    private const val BUFFER = 64 shl 10

    private fun ByteArray.startsWith(vararg prefix: Int) =
        size >= prefix.size && prefix.indices.all { this[it].toInt() and 0xFF == prefix[it] }
}

/** How the viewer reads and shows the names in an archive. */
object ArchiveNames {

    /**
     * A zip entry's name from its bytes: UTF-8 when the zip says so, or when they read as UTF-8;
     * otherwise CP950, as Windows in Taiwan writes them without saying; failing both, CP437, the
     * zip format's own code page, in which any bytes read as something.
     */
    fun zipName(raw: ByteArray, utf8: Boolean): String {
        if (utf8) return String(raw, Charsets.UTF_8)
        return strict(raw, Charsets.UTF_8) ?: CP950?.let { strict(raw, it) } ?: String(raw, CP437)
    }

    /** [path] with `/` between its parts and without empty or `.` ones, as the viewer shows it. */
    fun normalize(path: String): String =
        path.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }.joinToString("/")

    /** The extension a file taken out keeps: letters and digits only, or none. */
    fun extension(path: String): String {
        val name = path.substringAfterLast('/')
        val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return extension.takeIf { it.length in 1..10 && it.all { c -> c in 'a'..'z' || c in '0'..'9' } } ?: ""
    }

    private fun strict(bytes: ByteArray, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun charset(vararg names: String): Charset? =
        names.firstNotNullOfOrNull { name -> runCatching { Charset.forName(name) }.getOrNull() }

    private val CP950: Charset? = charset("x-windows-950", "MS950", "windows-950", "Big5")
    private val CP437: Charset = charset("IBM437", "cp437") ?: Charsets.ISO_8859_1
}

/** A folder's entry in the viewer's list. */
sealed interface ArchiveNode {
    val name: String

    data class Folder(val path: String, override val name: String) : ArchiveNode

    data class File(val item: ArchiveItem, override val name: String) : ArchiveNode
}

/** An archive's entries as folders: those its files sit in count, whether or not it lists them. */
object ArchiveTree {

    /** What's directly in [folder] (`""` for the top): its folders first, then its files, each by name. */
    fun children(items: List<ArchiveItem>, folder: String): List<ArchiveNode> {
        val prefix = if (folder.isEmpty()) "" else "$folder/"
        val folders = linkedMapOf<String, ArchiveNode.Folder>()
        val files = ArrayList<ArchiveNode.File>()
        for (item in items) {
            if (!item.path.startsWith(prefix) || item.path == folder) continue
            val rest = item.path.substring(prefix.length)
            val slash = rest.indexOf('/')
            when {
                slash >= 0 -> rest.substring(0, slash).let { name -> folders.getOrPut(name) { ArchiveNode.Folder(prefix + name, name) } }
                item.isDirectory -> folders.getOrPut(rest) { ArchiveNode.Folder(prefix + rest, rest) }
                else -> files += ArchiveNode.File(item, rest)
            }
        }
        val byName = compareBy<ArchiveNode>({ it.name.lowercase() }, { it.name })
        return folders.values.sortedWith(byName) + files.sortedWith(byName)
    }

    /** The folder [folder] is in, `""` for the top. */
    fun parent(folder: String): String = folder.substringBeforeLast('/', missingDelimiterValue = "")
}
