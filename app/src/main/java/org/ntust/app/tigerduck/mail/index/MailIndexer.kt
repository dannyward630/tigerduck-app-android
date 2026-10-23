package org.ntust.app.tigerduck.mail.index

import org.ntust.app.tigerduck.mail.imap.MailSession
import org.ntust.app.tigerduck.mail.model.MailSummary

/** Runs [block] on the connection the mail screens share, as MailRepository's own calls do. */
internal interface MailSessionRunner {
    suspend fun <T> run(stillWanted: () -> Boolean, block: (MailSession) -> T): T
}

/**
 * Brings the [MailSearchIndex] up to the server's mail, a folder at a time and [BATCH] mails at a
 * time. Each batch holds the shared connection once, so a page the user opens waits on one batch
 * at most.
 */
internal class MailIndexer(
    private val index: MailSearchIndex,
    private val sessions: MailSessionRunner,
    /** Who the index is for: the signed-in account's address, null while signed out. */
    private val owner: () -> String?,
    /** Moved by every sign-in and sign-out, which a run that outlives one has no business writing past. */
    private val generation: () -> Int,
) {
    /**
     * Brings [folder]'s index up to the server: drops the mail it no longer has, and adds what is
     * missing, newest first, [limit] mails at most; with [newOnly], only mail newer than any it
     * has, which a search catches up on without waiting for the first download's backlog. Says
     * whether nothing is left to add.
     */
    suspend fun update(
        folder: String,
        limit: Int = Int.MAX_VALUE,
        newOnly: Boolean = false,
        stillWanted: () -> Boolean = { true },
    ): Boolean {
        val owner = owner() ?: return false
        val started = generation()
        val current = { stillWanted() && generation() == started }
        val (uidValidity, onServer) = sessions.run(current) { s ->
            // The UIDs first: the session then answers the UIDVALIDITY from the folder they opened.
            val uids = s.undeletedUids(folder)
            s.uidValidity(folder) to uids
        }
        if (!index.open(owner, folder, uidValidity, current)) return false
        val indexed = index.uids(owner, folder)
        index.remove(owner, folder, indexed - onServer.toSet())
        val newest = if (newOnly) indexed.maxOrNull() ?: 0L else 0L
        val missing = onServer.filter { it !in indexed && it > newest }.sortedDescending()
        for (batch in missing.take(limit).chunked(BATCH)) {
            val entries = sessions.run(current) { s ->
                val texts = s.fetchSearchTexts(folder, batch)
                s.fetchByUids(folder, batch).map { MailSearchIndex.Entry(it.uid, senderOf(it), it.subject, texts[it.uid].orEmpty()) }
            }
            if (!index.add(owner, folder, uidValidity, entries, current)) return false
        }
        return missing.size <= limit
    }

    companion object {
        /** Mails fetched per hold of the connection: few, so a page the user opens waits seconds at most. */
        const val BATCH = 5

        /** The sender as a search reads it, the name and the address both. */
        fun senderOf(summary: MailSummary): String =
            listOfNotNull(summary.from?.name, summary.from?.address).joinToString(" ")
    }
}
