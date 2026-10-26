package com.androsaver

import org.junit.Assert.*
import org.junit.Test

class SourceSetupValidationTest {
    @Test fun onlyBareHostsAndIpAddressesAreAccepted() {
        for (value in listOf("https://server.invalid", "user@server.invalid", "server.invalid:443",
            "server.invalid?key=value", "server.invalid#fragment", "server.invalid/path", "a b.invalid", "server\\path")) {
            assertNull(value, SourceSetupValidation.host(value))
        }
        assertEquals("server.invalid", SourceSetupValidation.host(" SERVER.INVALID "))
        assertEquals("192.0.2.1", SourceSetupValidation.host("192.0.2.1"))
        assertEquals("[::1]", SourceSetupValidation.host("[::1]"))
        assertEquals("[::1]", SourceSetupValidation.host("::1"))
    }
}
