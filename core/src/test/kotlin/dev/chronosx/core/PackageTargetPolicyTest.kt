package dev.chronosx.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class PackageTargetPolicyTest {
    @Test
    fun `ordinary third party package is targetable`() {
        assertTrue(PackageTargetPolicy.isTargetable("com.example.clocklab"))
    }

    @Test
    fun `system packages are rejected`() {
        assertFalse(PackageTargetPolicy.isTargetable("android"))
        assertFalse(PackageTargetPolicy.isTargetable("com.android.settings"))
        assertFalse(PackageTargetPolicy.isTargetable("system"))
    }

    @Test
    fun `malformed package is rejected`() {
        assertFalse(PackageTargetPolicy.isTargetable("not a package"))
        assertFalse(PackageTargetPolicy.isTargetable("single"))
        assertFalse(PackageTargetPolicy.isTargetable("dev.chronosx"))
    }
}
