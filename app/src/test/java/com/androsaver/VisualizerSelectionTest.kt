package com.androsaver

import com.androsaver.visualizer.VisualizerSelection
import org.junit.Assert.*
import org.junit.Test

class VisualizerSelectionTest {
    @Test fun removedSelectionsFallBackToTheCurrentRegistryAndNewEffectsAreEnabled() {
        val all=setOf("Cube","Tunnel")
        assertEquals(all,VisualizerSelection.enabled(setOf("Morphogenesis","Mandelbox"),all+setOf("Morphogenesis","Mandelbox"),all))
        assertEquals(setOf("Cube"),VisualizerSelection.enabled(setOf("Cube","Mandelbox"),all,all))
        assertEquals(all,VisualizerSelection.enabled(setOf("Cube"),setOf("Cube"),all))
    }

    @Test fun manualAndAutoStartupNeverSelectADisabledFirstEffect() {
        listOf("off", "auto", "unknown").forEach { mode ->
            assertEquals(4, VisualizerSelection.initialIndex(mode, listOf(4, 7)))
        }
    }

    @Test fun randomStartupOnlyUsesEnabledEffects() {
        repeat(20) { assertTrue(VisualizerSelection.initialIndex("random", listOf(4, 7)) in listOf(4, 7)) }
        assertEquals(0, VisualizerSelection.initialIndex("off", emptyList()))
    }
}
