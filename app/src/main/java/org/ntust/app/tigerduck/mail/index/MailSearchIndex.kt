package org.ntust.app.tigerduck.mail.index

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.Closeable

/**
 * Every mail's sender, subject and text, kept on the phone so a search runs here: the school's
 * server takes a search in ASCII only. It answers `NO [BADCHARSET]` to UTF-8 and Big5 alike, and
 * finds nothing for Chinese sent without a charset.
 *
 * The index belongs to one [owner], the signed-in mail account: a write from any other, one that
 * left while it was fetching, is refused, and the next account to [open] a folder starts it empty.
 */
interface MailSearchIndex {
    /** One mail as a search reads it; [sender] holds its name and address both. */
    data class Entry(val uid: Long, val sender: String, val subject: String, val body: String)

    /** The UIDVALIDITY [folder] was indexed under for [owner], or null if it has not been. */
    fun uidValidity(owner: String, folder: String): Long?

    /**
     * Makes [folder] [owner]'s at [uidValidity], emptying it when either differs from before, so a
     * recreated folder or another account starts over. False, with nothing changed, unless
     * [stillCurrent].
     */
    fun open(owner: String, folder: String, uidValidity: Long, stillCurrent: () -> Boolean): Boolean

    /** The UIDs of [folder] indexed for [owner]. */
    fun uids(owner: String, folder: String): Set<Long>

    /**
     * Adds [entries] to [folder], unless the folder is no longer [owner]'s at [uidValidity] or
     * [stillCurrent] says the account left. Says whether it added them.
     */
    fun add(owner: String, folder: String, uidValidity: Long, entries: List<Entry>, stillCurrent: () -> Boolean): Boolean

    /** Drops [uids], gone from the server, from [folder]. */
    fun remove(owner: String, folder: String, uids: Collection<Long>)

    /** The UIDs, newest first, of [owner]'s mail in [folder] whose sender, subject or text holds [query]. */
    fun search(owner: String, folder: String, query: String): List<Long>

    /** Empties it, for a sign-out. */
    fun clear()
}

/**
 * [MailSearchIndex] in SQLite, in the app's own storage, which no backup or device transfer copies
 * (data_extraction_rules.xml). [name] null keeps it in memory.
 */
class SqliteMailSearchIndex(context: Context, name: String? = DB_NAME) : MailSearchIndex, Closeable {

    private val helper = object : SQLiteOpenHelper(context, name, null, VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE owner (id TEXT NOT NULL)")
            db.execSQL("CREATE TABLE folders (name TEXT PRIMARY KEY NOT NULL, uid_validity INTEGER NOT NULL)")
            db.execSQL(
                "CREATE TABLE mail (folder TEXT NOT NULL, uid INTEGER NOT NULL, sender TEXT NOT NULL, " +
                    "subject TEXT NOT NULL, body TEXT NOT NULL, PRIMARY KEY (folder, uid)) WITHOUT ROWID",
            )
        }

        // Only a copy of the server's mail: a new layout starts over and fetches it again.
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = recreate(db)

        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = recreate(db)

        private fun recreate(db: SQLiteDatabase) {
            listOf("owner", "folders", "mail").forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
            onCreate(db)
        }
    }

    private val db: SQLiteDatabase get() = helper.writableDatabase

    private fun ownerLocked(): String? =
        db.rawQuery("SELECT id FROM owner", null).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun validityLocked(folder: String): Long? =
        db.rawQuery("SELECT uid_validity FROM folders WHERE name = ?", arrayOf(folder)).use {
            if (it.moveToFirst()) it.getLong(0) else null
        }

    private fun clearLocked() {
        db.delete("mail", null, null)
        db.delete("folders", null, null)
        db.delete("owner", null, null)
    }

    @Synchronized
    override fun uidValidity(owner: String, folder: String): Long? =
        if (ownerLocked() == owner) validityLocked(folder) else null

    @Synchronized
    override fun open(owner: String, folder: String, uidValidity: Long, stillCurrent: () -> Boolean): Boolean {
        if (!stillCurrent()) return false
        transaction {
            if (ownerLocked() != owner) {
                clearLocked()
                db.insert("owner", null, ContentValues().apply { put("id", owner) })
            }
            if (validityLocked(folder) != uidValidity) {
                db.delete("mail", "folder = ?", arrayOf(folder))
                db.insertWithOnConflict(
                    "folders", null,
                    ContentValues().apply { put("name", folder); put("uid_validity", uidValidity) },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
        }
        return true
    }

    @Synchronized
    override fun uids(owner: String, folder: String): Set<Long> {
        if (ownerLocked() != owner) return emptySet()
        return db.rawQuery("SELECT uid FROM mail WHERE folder = ?", arrayOf(folder)).use { c ->
            buildSet { while (c.moveToNext()) add(c.getLong(0)) }
        }
    }

    @Synchronized
    override fun add(
        owner: String,
        folder: String,
        uidValidity: Long,
        entries: List<MailSearchIndex.Entry>,
        stillCurrent: () -> Boolean,
    ): Boolean {
        if (!stillCurrent() || ownerLocked() != owner || validityLocked(folder) != uidValidity) return false
        transaction {
            entries.forEach { e ->
                db.insertWithOnConflict(
                    "mail", null,
                    ContentValues().apply {
                        put("folder", folder)
                        put("uid", e.uid)
                        put("sender", e.sender)
                        put("subject", e.subject)
                        put("body", e.body)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
        }
        return true
    }

    @Synchronized
    override fun remove(owner: String, folder: String, uids: Collection<Long>) {
        if (uids.isEmpty() || ownerLocked() != owner) return
        transaction { uids.forEach { db.delete("mail", "folder = ? AND uid = ?", arrayOf(folder, it.toString())) } }
    }

    @Synchronized
    override fun search(owner: String, folder: String, query: String): List<Long> {
        if (query.isEmpty() || ownerLocked() != owner) return emptyList()
        // LIKE ignores case for ASCII, as the server's search did; % and _ in what was typed are
        // matched as themselves.
        val pattern = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        return db.rawQuery(
            "SELECT uid FROM mail WHERE folder = ? AND (sender LIKE ?2 ESCAPE '\\' OR subject LIKE ?2 ESCAPE '\\' " +
                "OR body LIKE ?2 ESCAPE '\\') ORDER BY uid DESC",
            arrayOf(folder, pattern),
        ).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
    }

    @Synchronized
    override fun clear() = transaction { clearLocked() }

    @Synchronized
    override fun close() = helper.close()

    private inline fun transaction(block: () -> Unit) {
        db.beginTransaction()
        try {
            block()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        const val DB_NAME = "mail_search.db"
        private const val VERSION = 1
    }
}
