package org.ntust.app.tigerduck.mail.index

import org.ntust.app.tigerduck.mail.imap.MailSession
import org.ntust.app.tigerduck.mail.model.MailSummary

/** Runs [block] on the connection the mail screens share, as MailRepository's own calls do. */
internal interface MailSessionRunner {
    suspend fun <T> run(stillWanted: () -> Boolean, block: (MailSession) -> T): T
}

/** How far a download into the search index has come: [done] of the [total] mails it lacked. */
data class MailIndexProgress(val done: Int, val total: Int)

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
    /** What [folder]'s index lacks, newest first, as [plan] found it for [owner] under [uidValidity]. */
    class Plan internal constructor(
        val folder: String,
        internal val owner: String,
        internal val uidValidity: Long,
        internal val generation: Int,
        val missing: List<Long>,
    )

    /**
     * Opens [folder]'s index at the server's UIDVALIDITY, drops the mail the server no longer has,
     * and says what it lacks, newest first: with [newOnly], only mail newer than any it has, which a
     * search catches up on without waiting for the first download's backlog. Null for no account,
     * or one that left meanwhile.
     */
    suspend fun plan(folder: String, newOnly: Boolean = false, stillWanted: () -> Boolean = { true }): Plan? {
        val owner = owner() ?: return null
        val started = generation()
        val current = { stillWanted() && generation() == started }
        val (uidValidity, onServer) = sessions.run(current) { s ->
            // The UIDs first: the session then answers the UIDVALIDITY from the folder they opened.
            val uids = s.undeletedUids(folder)
            s.uidValidity(folder) to uids
        }
        if (!index.open(owner, folder, uidValidity, current)) return null
        val indexed = index.uids(owner, folder)
        index.remove(owner, folder, indexed - onServer.toSet())
        val newest = if (newOnly) indexed.maxOrNull() ?: 0L else 0L
        val missing = onServer.filter { it !in indexed && it > newest }.sortedDescending()
        return Plan(folder, owner, uidValidity, started, missing)
    }

    /**
     * Downloads [plan]'s mail into the index, [limit] mails at most, telling [onAdded] how many
     * each batch added. Says whether it got through all it was given.
     */
    suspend fun fill(
        plan: Plan,
        limit: Int = Int.MAX_VALUE,
        stillWanted: () -> Boolean = { true },
        onAdded: (Int) -> Unit = {},
    ): Boolean {
        val current = { stillWanted() && generation() == plan.generation }
        for (batch in plan.missing.take(limit).chunked(BATCH)) {
            val entries = sessions.run(current) { s ->
                val texts = s.fetchSearchTexts(plan.folder, batch)
                s.fetchByUids(plan.folder, batch).map {
                    MailSearchIndex.Entry(it.uid, senderOf(it), it.subject, texts[it.uid].orEmpty())
                }
            }
            if (!index.add(plan.owner, plan.folder, plan.uidValidity, entries, current)) return false
            // The batch is done with, mail since deleted included, which the next run will not see.
            onAdded(batch.size)
        }
        return plan.missing.size <= limit
    }

    /** [plan] and [fill] in one: what a search catches up on before it looks. */
    suspend fun update(
        folder: String,
        limit: Int = Int.MAX_VALUE,
        newOnly: Boolean = false,
        stillWanted: () -> Boolean = { true },
    ): Boolean {
        val plan = plan(folder, newOnly, stillWanted) ?: return false
        return fill(plan, limit, stillWanted)
    }

    companion object {
        /** Mails fetched per hold of the connection: few, so a page the user opens waits seconds at most. */
        const val BATCH = 5

        /** The sender as a search reads it, the name and the address both. */
        fun senderOf(summary: MailSummary): String =
            listOfNotNull(summary.from?.name, summary.from?.address).joinToString(" ")
    }
}
