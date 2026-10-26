package com.androsaver

import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class StartupErrorFragmentTest {
    @Test fun fallbackFragmentMeetsAndroidXTransactionAndRestorationRequirements() {
        val type = SettingsActivity.StartupErrorFragment::class.java
        assertTrue(Modifier.isPublic(type.modifiers))
        assertTrue(Modifier.isStatic(type.modifiers))
        assertTrue(Modifier.isPublic(type.getConstructor().modifiers))
    }
}
