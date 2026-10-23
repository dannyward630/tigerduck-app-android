package org.ntust.app.tigerduck.mail.index

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ntust.app.tigerduck.mail.FakeMailServer
import org.ntust.app.tigerduck.mail.MailCredentials
import org.ntust.app.tigerduck.mail.MailError
import org.ntust.app.tigerduck.mail.imap.MailSession

/** [MailIndexer] over [FakeMailServer], its one session handed out as MailRepository hands out the shared one. */
class MailIndexerTest {
    private val server = FakeMailServer()
    private val index = InMemoryMailSearchIndex()
    private val session = server.factory().open(MailCredentials("b10000001", "pw"))

    private val sessions = object : MailSessionRunner {
        override suspend fun <T> run(stillWanted: () -> Boolean, block: (MailSession) -> T): T {
            if (!stillWanted()) throw MailError.Abandoned()
            return block(session)
        }
    }

    private val me = "b10000001@mail.ntust.edu.tw"

    @Test
    fun `a run whose account changed right after it was read writes nothing`() = runTest {
        server.deliver("期中考")
        var generation = 1
        // Another student signs in right after the account is read, before anything else is.
        val indexer = MailIndexer(
            index, sessions,
            owner = { MailIndexer.Owner(me, generation).also { generation++ } },
            stillOwner = { it.generation == generation },
        )

        runCatching { indexer.plan("INBOX")?.let { indexer.fill(it) } }

        assertTrue(index.uids(me, "INBOX").isEmpty())
    }
}
