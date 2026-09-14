package com.omargarcia.blocky

import com.omargarcia.blocky.utils.UpdateChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun testIsNewerVersion_patchUpdate() {
        assertTrue(UpdateChecker.isNewerVersion("1.0.18", "v1.0.19"))
        assertTrue(UpdateChecker.isNewerVersion("1.0.18", "1.0.19"))
    }

    @Test
    fun testIsNewerVersion_minorUpdate() {
        assertTrue(UpdateChecker.isNewerVersion("1.0.19", "v1.1.0"))
        assertTrue(UpdateChecker.isNewerVersion("1.0.19", "1.1.0"))
    }

    @Test
    fun testIsNewerVersion_majorUpdate() {
        assertTrue(UpdateChecker.isNewerVersion("1.9.9", "v2.0.0"))
        assertTrue(UpdateChecker.isNewerVersion("1.0.18", "2.0.0"))
    }

    @Test
    fun testIsNewerVersion_sameVersion() {
        assertFalse(UpdateChecker.isNewerVersion("1.0.18", "v1.0.18"))
        assertFalse(UpdateChecker.isNewerVersion("1.0.18", "1.0.18"))
        assertFalse(UpdateChecker.isNewerVersion("v1.0.19", "1.0.19"))
    }

    @Test
    fun testIsNewerVersion_olderVersion() {
        assertFalse(UpdateChecker.isNewerVersion("1.0.19", "v1.0.18"))
        assertFalse(UpdateChecker.isNewerVersion("2.0.0", "v1.9.9"))
    }

    @Test
    fun testIsNewerVersion_withSuffixOrPrerelease() {
        assertTrue(UpdateChecker.isNewerVersion("1.0.18", "v1.0.19-beta1"))
        assertFalse(UpdateChecker.isNewerVersion("1.0.19", "v1.0.19-rc2"))
    }

    @Test
    fun testIsNewerVersion_differentSegmentLengths() {
        assertTrue(UpdateChecker.isNewerVersion("1.0", "1.0.1"))
        assertFalse(UpdateChecker.isNewerVersion("1.0.1", "1.0"))
    }

    @Test
    fun testIsNewerVersion_invalidOrEmpty() {
        assertFalse(UpdateChecker.isNewerVersion("", "v1.0.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.0.0", ""))
        assertFalse(UpdateChecker.isNewerVersion("invalid", "invalid"))
    }
}
