package org.ntust.app.tigerduck.shared.tls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinnedDomainsTest {

    @Test
    fun `a host under the school's domain is the school's`() {
        assertEquals("ntust.edu.tw", PinnedDomains.of("moodle2.ntust.edu.tw"))
        assertEquals("ntust.edu.tw", PinnedDomains.of("ssoam2.ntust.edu.tw"))
        assertEquals("ntust.edu.tw", PinnedDomains.of("ntust.edu.tw"))
    }

    @Test
    fun `the library host takes its own pins, the most specific domain`() {
        assertEquals("api.lib.ntust.edu.tw", PinnedDomains.of("api.lib.ntust.edu.tw"))
        assertEquals("api.lib.ntust.edu.tw", PinnedDomains.of("x.api.lib.ntust.edu.tw"))
        // Not under the library's domain, only the school's.
        assertEquals("ntust.edu.tw", PinnedDomains.of("lib.ntust.edu.tw"))
    }

    @Test
    fun `case and a trailing dot don't change the domain`() {
        assertEquals("ntust.edu.tw", PinnedDomains.of("Moodle2.NTUST.edu.tw."))
    }

    @Test
    fun `a host no pin-set covers has no domain`() {
        assertNull(PinnedDomains.of("api.tigerduck.app"))
        assertNull(PinnedDomains.of("r.xinshou.tw"))
        // A look-alike that only ends in the same letters.
        assertNull(PinnedDomains.of("notntust.edu.tw"))
    }

    @Test
    fun `a domain without subdomains covers only itself`() {
        val domains = listOf(PinnedDomains.Domain("example.test", includeSubdomains = false))
        assertEquals("example.test", PinnedDomains.of("example.test", domains))
        assertNull(PinnedDomains.of("www.example.test", domains))
    }
}
