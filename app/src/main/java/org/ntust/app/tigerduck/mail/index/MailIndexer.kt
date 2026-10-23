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
    /** Who the index is for now; null while signed out. */
    private val owner: () -> Owner?,
    /**
     * Whether the index is still [Owner]'s: no sign-in, sign-out or deletion of what it holds since
     * it was read, which a run that outlives one has no business writing past.
     */
    private val stillOwner: (Owner) -> Boolean,
) {
    /**
     * Whom a run downloads for: the signed-in account's [address], read together with the
     * [generation] it signed in at, so a run never pairs one account's address with the sign-in
     * of the next, whose credentials its requests would then go out under; and the index as it
     * was then, which deleting what it holds moves on to the next [epoch].
     */
    data class Owner(val address: String, val generation: Int, val epoch: Int)

    /** What [folder]'s index lacks, newest first, as [plan] found it for [owner] under [uidValidity]. */
    class Plan internal constructor(
        val folder: String,
        internal val owner: Owner,
        internal val uidValidity: Long,
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
        val current = { stillWanted() && stillOwner(owner) }
        val (uidValidity, onServer) = sessions.run(current) { s ->
            // The UIDs first: the session then answers the UIDVALIDITY from the folder they opened.
            val uids = s.undeletedUids(folder)
            s.uidValidity(folder) to uids
        }
        if (!index.open(owner.address, folder, uidValidity, current)) return null
        val indexed = index.uids(owner.address, folder)
        index.remove(owner.address, folder, indexed - onServer.toSet(), current)
        val newest = if (newOnly) indexed.maxOrNull() ?: 0L else 0L
        val missing = onServer.filter { it !in indexed && it > newest }.sortedDescending()
        return Plan(folder, owner, uidValidity, missing)
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
        val current = { stillWanted() && stillOwner(plan.owner) }
        for (batch in plan.missing.take(limit).chunked(BATCH)) {
            // Read and filed in one hold of the connection. A mark-read or a move holds it too, so
            // it reaches the server before the batch reads the mail, or the index once the batch
            // is in: never in between, where the batch's older answer would undo it.
            val added = sessions.run(current) { s ->
                // Recreated since the plan, the folder's UIDs name other mail: the next run plans
                // it again, which empties its index.
                if (s.uidValidity(plan.folder) != plan.uidValidity) return@run false
                val texts = s.fetchSearchTexts(plan.folder, batch)
                val entries = s.fetchByUids(plan.folder, batch).map {
                    MailSearchIndex.Entry(it.uid, senderOf(it), it.subject, texts[it.uid].orEmpty(), it)
                }
                index.add(plan.owner.address, plan.folder, plan.uidValidity, entries, current)
            }
            if (!added) return false
            // The batch is done with, mail since deleted included, which the next run will not see.
            onAdded(batch.size)
        }
        return plan.missing.size <= limit
    }

    /**
     * Brings the flags of the mail [plan]'s folder has downloaded to the server's: a mail read,
     * answered or deleted on another device or the web shows so on the phone's copy too.
     */
    suspend fun refreshFlags(plan: Plan, stillWanted: () -> Boolean = { true }) {
        val current = { stillWanted() && stillOwner(plan.owner) }
        val downloaded = index.uids(plan.owner.address, plan.folder).toList()
        for (chunk in downloaded.chunked(FLAGS_BATCH)) {
            // Read and filed in one hold of the connection, as [fill]'s batches are, and for the
            // same UIDVALIDITY: the UIDs of a folder recreated since name other mail.
            val applied = sessions.run(current) { s ->
                if (s.uidValidity(plan.folder) != plan.uidValidity) return@run false
                index.updateFlags(plan.owner.address, plan.folder, plan.uidValidity, s.refreshFlags(plan.folder, chunk), current)
                true
            }
            if (!applied) return
        }
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
        /**
         * Mails fetched per hold of the connection: two FETCHes, so a page the user opens waits a
         * second or two at most.
         */
        const val BATCH = 20

        /** Mails whose flags one FETCH asks for: flags are a few bytes a mail. */
        const val FLAGS_BATCH = 500

        /** The sender as a search reads it, the name and the address both. */
        fun senderOf(summary: MailSummary): String =
            listOfNotNull(summary.from?.name, summary.from?.address).joinToString(" ")
    }
}
