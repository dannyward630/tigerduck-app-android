package org.ntust.app.tigerduck.wear

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ntust.app.tigerduck.wear.WearLocaleSwitch.Step

class WearLocaleSwitchTest {

    @Test
    fun `the language already showing needs nothing, whatever its case`() {
        assertEquals(Step.Keep, WearLocaleSwitch.step(wanted = "zh-Hant-TW", showing = "zh-hant-tw", triedFor = null))
    }

    @Test
    fun `no language from the phone needs nothing`() {
        assertEquals(Step.Keep, WearLocaleSwitch.step(wanted = null, showing = "en-US", triedFor = null))
        assertEquals(Step.Keep, WearLocaleSwitch.step(wanted = " ", showing = "en-US", triedFor = null))
    }

    @Test
    fun `another language recreates`() {
        assertEquals(Step.Recreate, WearLocaleSwitch.step(wanted = "zh-Hant-TW", showing = "en-US", triedFor = null))
        assertEquals(Step.Recreate, WearLocaleSwitch.step(wanted = "en", showing = "en-US", triedFor = null))
    }

    @Test
    fun `a language recreated for once is not recreated for again, so it cannot loop`() {
        // The new instance still shows the old language: its start-up copy of the tag was stale,
        // or the platform would not take the language. Another recreate would end the same way.
        assertEquals(Step.Stay, WearLocaleSwitch.step(wanted = "en", showing = "en-US", triedFor = "en"))
        assertEquals(Step.Stay, WearLocaleSwitch.step(wanted = "zh-Hant-TW", showing = "en-US", triedFor = "zh-hant-tw"))
    }

    @Test
    fun `a new language after one that stayed recreates again`() {
        assertEquals(Step.Recreate, WearLocaleSwitch.step(wanted = "ja", showing = "en-US", triedFor = "en"))
    }
}
