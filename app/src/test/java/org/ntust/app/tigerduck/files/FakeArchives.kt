package org.ntust.app.tigerduck.files

import java.io.File

/**
 * This zip, with its entry [name] flagged as encrypted, in its local header and in the central
 * directory: no zip writer at hand writes an encrypted entry, and the flag is all a reader sees
 * before it would need the password.
 */
internal fun File.markedEncrypted(name: String): File = apply {
    val bytes = readBytes()
    val target = name.toByteArray()
    fun short(at: Int) = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)
    fun named(at: Int, length: Int) = length == target.size && target.indices.all { bytes[at + it] == target[it] }
    var marked = 0
    for (at in 0 until bytes.size - 4) {
        val signature = short(at) or (short(at + 2) shl 16)
        when {
            // Local file header: flags at 6, name length at 26, name at 30.
            signature == 0x04034B50 && named(at + 30, short(at + 26)) -> { bytes[at + 6] = (bytes[at + 6].toInt() or 1).toByte(); marked++ }
            // Central directory header: flags at 8, name length at 28, name at 46.
            signature == 0x02014B50 && named(at + 46, short(at + 28)) -> { bytes[at + 8] = (bytes[at + 8].toInt() or 1).toByte(); marked++ }
        }
    }
    check(marked == 2) { "$name found $marked times" }
    writeBytes(bytes)
}

/**
 * A V7 tar, as tar wrote before POSIX: each header with no `ustar` mark, only its checksum to
 * show it's a header. Made by hand, since tar writers today all mark theirs.
 */
internal fun v7Tar(vararg files: Pair<String, ByteArray>): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    for ((name, data) in files) {
        val header = ByteArray(512)
        fun put(at: Int, text: String) = text.toByteArray(Charsets.US_ASCII).copyInto(header, at)
        put(0, name)
        put(100, "0000644\u0000")
        put(108, "0000000\u0000")
        put(116, "0000000\u0000")
        put(124, "%011o\u0000".format(data.size))
        put(136, "%011o\u0000".format(1_700_000_000L))
        header[156] = '0'.code.toByte()
        // The checksum counts its own field as spaces.
        for (at in 148 until 156) header[at] = ' '.code.toByte()
        put(148, "%06o\u0000 ".format(header.sumOf { it.toInt() and 0xFF }))
        out.write(header)
        out.write(data)
        out.write(ByteArray((512 - data.size % 512) % 512))
    }
    out.write(ByteArray(1024))
    return out.toByteArray()
}
