package com.androsaver

internal object UpdatePackagePolicy {
    // PackageManager constants are inlined; keep selection testable without Android.
    fun signatureFlags(apiLevel: Int): Int = if (apiLevel >= 28) 0x08000000 else 0x00000040
}
