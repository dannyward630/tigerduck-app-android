package org.ntust.app.tigerduck.mail.index

import org.ntust.app.tigerduck.mail.model.MailFlags
import org.ntust.app.tigerduck.mail.model.MailSummary

/**
 * [MailSearchIndex] in memory, for tests off the device: the same rules as the SQLite one, which
 * MailSearchIndexTest holds it to, with a match ignoring case for ASCII as SQLite's LIKE does.
 */
class InMemoryMailSearchIndex : MailSearchIndex {
    private var owner: String? = null
    private val validities = mutableMapOf<String, Long>()
    private val mail = mutableMapOf<String, MutableMap<Long, MailSearchIndex.Entry>>()

    /** Every write the index refused, as a test reads what a run that outlived its account tried. */
    var refusedWrites = 0
        private set

    @Synchronized
    override fun uidValidity(owner: String, folder: String): Long? = if (this.owner == owner) validities[folder] else null

    @Synchronized
    override fun open(owner: String, folder: String, uidValidity: Long, stillCurrent: () -> Boolean): Boolean {
        if (!stillCurrent()) {
            refusedWrites++
            return false
        }
        if (this.owner != owner) {
            clear()
            this.owner = owner
        }
        if (validities[folder] != uidValidity) {
            mail.remove(folder)
            validities[folder] = uidValidity
        }
        return true
    }

    @Synchronized
    override fun uids(owner: String, folder: String): Set<Long> =
        if (this.owner == owner) mail[folder]?.keys?.toSet().orEmpty() else emptySet()

    @Synchronized
    override fun add(
        owner: String,
        folder: String,
        uidValidity: Long,
        entries: List<MailSearchIndex.Entry>,
        stillCurrent: () -> Boolean,
    ): Boolean {
        if (!stillCurrent() || this.owner != owner || validities[folder] != uidValidity) {
            refusedWrites++
            return false
        }
        val box = mail.getOrPut(folder) { mutableMapOf() }
        entries.forEach { box[it.uid] = it }
        return true
    }

    @Synchronized
    override fun remove(owner: String, folder: String, uids: Collection<Long>) {
        if (this.owner == owner) mail[folder]?.keys?.removeAll(uids.toSet())
    }

    @Synchronized
    override fun search(owner: String, folder: String, query: String): List<Long> {
        if (query.isEmpty() || this.owner != owner) return emptyList()
        fun String.holds() = asciiLower().contains(query.asciiLower())
        return mail[folder].orEmpty().values
            .filter { it.sender.holds() || it.subject.holds() || it.body.holds() }
            .map { it.uid }
            .sortedDescending()
    }

    @Synchronized
    override fun summaries(owner: String, folder: String, uidValidity: Long, uids: Collection<Long>): Map<Long, MailSummary> {
        if (this.owner != owner || validities[folder] != uidValidity) return emptyMap()
        val box = mail[folder].orEmpty()
        return uids.mapNotNull { box[it]?.summary }.associateBy { it.uid }
    }

    @Synchronized
    override fun updateFlags(owner: String, folder: String, flags: Map<Long, MailFlags>) {
        if (this.owner != owner) return
        val box = mail[folder] ?: return
        flags.forEach { (uid, now) ->
            val entry = box[uid] ?: return@forEach
            if (now.deleted) box.remove(uid) else box[uid] = entry.copy(summary = entry.summary.copy(flags = now))
        }
    }

    @Synchronized
    override fun clear() {
        owner = null
        validities.clear()
        mail.clear()
    }

    private fun String.asciiLower() = map { if (it in 'A'..'Z') it + 32 else it }.joinToString("")
}

/** Records what was asked of the background download, which a test never runs. */
class RecordingIndexScheduler : MailIndexScheduler {
    var requests = 0
        private set
    var cancels = 0
        private set

    override fun request() {
        requests++
    }

    override fun cancel() {
        cancels++
    }
}
