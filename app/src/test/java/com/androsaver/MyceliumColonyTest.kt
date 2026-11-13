package com.androsaver

import com.androsaver.visualizer.modes.MyceliumColony
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioural checks for the psysuals 3.21 hyphal web: the colony is grown,
 * its pools stay bounded, packets travel and hand over, spores germinate and
 * silence still carries light.
 */
class MyceliumColonyTest {

    @Test fun colonyGrowsAWebWithinItsPools() {
        val colony = MyceliumColony(1920, 1080, seed = 11)
        assertEquals(3, colony.bodyCount)
        assertTrue("the seed colony grows filaments", colony.liveEdgeTotal > 20)
        repeat(400) { colony.update(1f, 0.4f, 0.3f, 0.2f, 0.4f, false, false) }
        assertTrue(colony.liveEdgeTotal <= MyceliumColony.MAX_EDGES)
        assertTrue(colony.liveNodeTotal <= MyceliumColony.MAX_NODES)
        assertTrue(colony.tipCount <= MyceliumColony.MAX_TIPS)
        assertTrue(colony.pulseCount <= MyceliumColony.PULSE_TARGET)
        assertTrue(colony.sporeCount <= MyceliumColony.MAX_SPORES)
    }

    @Test fun packetsTravelAlongTheFilaments() {
        val colony = MyceliumColony(1920, 1080, seed = 5)
        repeat(120) { colony.update(1f, 0.6f, 0.4f, 0.3f, 0.8f, true, false) }
        assertTrue("the colony carries light", colony.pulseCount > 0)
        val before = (0 until colony.pulseCount).map { colony.pulseEdge(it) to colony.pulseT(it) }
        repeat(60) { colony.update(1f, 0.6f, 0.4f, 0.3f, 0.8f, true, false) }
        val after = (0 until colony.pulseCount).map { colony.pulseEdge(it) to colony.pulseT(it) }
        assertTrue("packets move or hand over", before != after)
    }

    @Test fun silenceStillCarriesLight() {
        val colony = MyceliumColony(1920, 1080, seed = 7)
        var pulses = 0
        repeat(400) { frame ->
            colony.update(1f, 0f, 0f, 0f, 0f, false, true)
            pulses = maxOf(pulses, colony.pulseCount)
        }
        assertTrue("the colony keeps its own heartbeat", pulses > 0)
    }

    @Test fun sporesGerminateIntoNewFilaments() {
        val colony = MyceliumColony(1920, 1080, seed = 13)
        colony.update(1f, 0.8f, 0.4f, 0.3f, 0.9f, true, false)
        repeat(900) { colony.update(1f, 0.2f, 0.2f, 0.2f, 0.2f, false, false) }
        assertTrue("spores take root", colony.germinations > 0)
    }

    @Test fun resizeKeepsTheNetworkAndRescalesIt() {
        val colony = MyceliumColony(1920, 1080, seed = 3)
        val nodes = colony.liveNodeTotal
        val edges = colony.liveEdgeTotal
        colony.resize(1280, 720)
        assertEquals(nodes, colony.liveNodeTotal)
        assertEquals(edges, colony.liveEdgeTotal)
        repeat(120) { colony.update(1f, 0.3f, 0.3f, 0.2f, 0.3f, false, false) }
        assertTrue("the colony keeps growing after a resize", colony.liveEdgeTotal > 0)
    }
}
