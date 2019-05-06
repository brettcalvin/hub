package com.flightstats.hub.cluster;

import com.flightstats.hub.config.PropertiesLoader;
import com.flightstats.hub.config.SpokeProperties;
import com.flightstats.hub.spoke.SpokeStore;
import com.flightstats.hub.test.Integration;
import org.apache.curator.framework.CuratorFramework;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpokeDecommissionClusterTest {

    private static final SpokeProperties spokeProperties = new SpokeProperties(PropertiesLoader.getInstance());
    private static CuratorFramework curator;
    private static SpokeDecommissionCluster cluster;

    @BeforeClass
    public static void setUpClass() throws Exception {
        curator = Integration.startZooKeeper();
        cluster = new SpokeDecommissionCluster(curator,
                new SpokeProperties(PropertiesLoader.getInstance()));
    }

    @After
    public void afterTest() throws Exception {
        cluster.doNotRestart();
    }

    @Test
    public void testDecommission() throws Exception {
        cluster.decommission();
        assertTrue(cluster.withinSpokeExists());
        assertFalse(cluster.doNotRestartExists());

        assertEquals(spokeProperties.getTtlMinutes(SpokeStore.WRITE), cluster.getDoNotRestartMinutes(), 1);

        cluster.doNotRestart();
        assertFalse(cluster.withinSpokeExists());
        assertTrue(cluster.doNotRestartExists());
    }
}