package org.ntust.app.tigerduck.data.sharing

import org.ntust.app.tigerduck.network.CourseService
import java.time.Instant

/**
 * A class table share code read from a QR code: whose class table it is and the course codes of
 * each term it shares. The iOS app reads and writes the same codes: ours in the packed layout both
 * apps agreed on 2026-10-19 ([PackedClassTableCode]), under the share host ([PREFIX]), and TAT's.
 */
data class ClassTableShareCode(
    val format: Format,
    /** Null only for a TAT code whose sharer left it blank. */
    val studentId: String?,
    /** To the minute; only TigerDuck codes carry it. */
    val sharedAt: Instant?,
    val terms: List<Term>,
) {
    enum class Format {
        TIGER_DUCK,

        /** TAT's code with class times, which the decoder checks and drops. */
        TAT2,

        /** TAT's older code, with no class times. */
        TAT1,
    }

    data class Term(
        /** The app's semester code, such as `1151`, or `114H` for a summer term. */
        val semester: String,
        val courseNos: List<String>,
    )

    companion object {
        private const val SHARE_HOST = "HTTPS://SHARE.TIGERDUCK.APP"

        /**
         * Where every share link lives, in the upper case that keeps a QR code in alphanumeric
         * mode: `HTTPS://SHARE.TIGERDUCK.APP/<TYPE>/<content>`, so a later version can share
         * other things beside class tables. Codes written on tigerduck.app before the move are
         * not read (decided for both apps): that format never reached a release of this app.
         */
        const val SHARE_ROOT = "$SHARE_HOST/"

        /** A class table's share code: the type `CLASSTABLE`, then the packed layout's version and payload. */
        const val PREFIX = SHARE_ROOT + "CLASSTABLE/"

        /**
         * Reads any share code the app accepts, ignoring surrounding whitespace and letter case,
         * and throws a [ClassTableShareCodeError] for anything else.
         */
        fun decode(text: String): ClassTableShareCode {
            val code = text.trim { it in TRIMMED }.uppercase()
            return when {
                code.startsWith(PREFIX) -> PackedClassTableCode.decode(code)
                isOnShareHost(code) -> throw if (code.startsWith(SHARE_ROOT) && namesType(code.substring(SHARE_ROOT.length))) {
                    ClassTableShareCodeError.UnknownType()
                } else {
                    ClassTableShareCodeError.Malformed()
                }
                code.startsWith(TAT_PREFIX) -> decodeTat(code)
                else -> throw ClassTableShareCodeError.UnknownPrefix()
            }
        }

        /** The share host alone, or with a path, query or fragment after it; not a host that only starts the same. */
        private fun isOnShareHost(code: String): Boolean =
            code == SHARE_HOST || code.startsWith(SHARE_HOST) && code[SHARE_HOST.length] in "/?#"

        /** Whether [path] starts with a type, as a newer app may write one: letters or digits, then `/`. */
        private fun namesType(path: String): Boolean = isUpperAlphanumerics(path.substringBefore('/', ""))

        /**
         * Our code for [terms]: one of the user's terms, or every term that has courses. Course
         * codes outside NTUST's shape are left out, and so is a term left without courses; null
         * when no course is left, the student ID cannot be written, or the table is past the
         * layout's limits ([PackedClassTableCode.encode]).
         */
        fun encode(studentId: String, sharedAt: Instant, terms: List<Term>): String? {
            val id = studentId.uppercase()
            if (!isUpperAlphanumerics(id)) return null
            val semesters = mutableSetOf<String>()
            val kept = terms.mapNotNull { term ->
                val semester = term.semester.uppercase()
                val courseNos = term.courseNos.map { it.uppercase() }.filter(CourseService::isCourseNo).toSortedSet().toList()
                if (!isSemester(semester) || courseNos.isEmpty() || !semesters.add(semester)) null
                else Term(semester, courseNos)
            }
            if (kept.isEmpty()) return null
            return PackedClassTableCode.encode(id, sharedAt, kept)
        }

        // --- Shared rules ---

        // A course code's shape is the app's one rule, CourseService.isCourseNo.

        /** The ROC year and the term: `1151`, `1142`, `114H`. */
        internal fun isSemester(text: String): Boolean =
            text.length == 4 && isDigits(text.substring(0, 3)) && isUpperAlphanumerics(text.substring(3))

        /**
         * One term's course codes. An empty or repeated one, which no encoder writes, makes the
         * code malformed; one outside NTUST's shape is an invalid course code.
         */
        internal fun checkedCourseNos(items: List<String>): List<String> {
            if (items.any { it.isEmpty() } || items.toSet().size != items.size) {
                throw ClassTableShareCodeError.Malformed()
            }
            items.firstOrNull { !CourseService.isCourseNo(it) }?.let { throw ClassTableShareCodeError.InvalidCourseCode(it) }
            return items
        }

        internal fun isAscii(text: String): Boolean = text.all { it < '\u0080' }

        internal fun isDigits(text: String): Boolean = text.isNotEmpty() && text.all { it in '0'..'9' }

        internal fun isUpperAlphanumerics(text: String): Boolean =
            text.isNotEmpty() && text.all { it in '0'..'9' || it in 'A'..'Z' }

        /**
         * What Apple's `CharacterSet.whitespacesAndNewlines` holds, which the iOS decoder trims,
         * so a code reads the same on both platforms.
         */
        private const val TRIMMED =
            "\t\n\u000B\u000C\r \u0085\u00A0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007" +
                "\u2008\u2009\u200A\u200B\u2028\u2029\u202F\u205F\u3000"
    }
}

/**
 * Why a share code was refused. A scanner skips an [UnknownPrefix]; the import screen asks for a
 * newer app for an [UnknownVersion] or an [UnknownType], and shows one message for the rest,
 * which stay apart for tests and logs.
 */
sealed class ClassTableShareCodeError : Exception() {
    /** Not a share code, such as another app's QR code. */
    class UnknownPrefix : ClassTableShareCodeError()

    /** A share code in a format version this app does not know yet. */
    class UnknownVersion : ClassTableShareCodeError()

    /** A share link of a type this app does not know yet, which a newer one writes. */
    class UnknownType : ClassTableShareCodeError()

    class Malformed : ClassTableShareCodeError()

    class ChecksumMismatch : ClassTableShareCodeError()

    /** A TAT code's course code outside NTUST's shape; every course ours decodes to has that shape. */
    data class InvalidCourseCode(val courseNo: String) : ClassTableShareCodeError()
}
