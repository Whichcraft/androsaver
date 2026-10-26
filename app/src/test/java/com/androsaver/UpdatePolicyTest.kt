package com.androsaver

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    @Test fun signingFlagsCoverEverySupportedApiFamily() {
        (21..27).forEach { assertEquals(0x40, UpdatePackagePolicy.signatureFlags(it)) }
        listOf(28, 29, 35).forEach { assertEquals(0x08000000, UpdatePackagePolicy.signatureFlags(it)) }
    }

    @Test fun newClientsPreferPinnedAssetsAndStillAcceptOldManifests() {
        val rolling = url("stable")
        val pinned = url("build-stable-${"a".repeat(40)}")
        assertEquals(rolling, UpdateDownloadPolicy.select(rolling, null, "stable"))
        assertEquals(pinned, UpdateDownloadPolicy.select(rolling, pinned, "stable"))
    }

    @Test fun pinnedDownloadsCannotCrossChannelsOriginsOrPaths() {
        val rolling = url("stable")
        listOf(url("build-dev-channel-${"a".repeat(40)}"), url("build-stable-short"),
            url("build-stable-${"a".repeat(40)}").replace("github.com", "example.invalid"),
            url("build-stable-${"a".repeat(40)}") + "?token=placeholder",
            url("build-stable-${"a".repeat(40)}").replace("https:", "http:")
        ).forEach { assertNull(UpdateDownloadPolicy.select(rolling, it, "stable")) }
    }

    private fun url(tag: String) = "https://github.com/Whichcraft/androsaver/releases/download/$tag/androsaver.apk"
}
