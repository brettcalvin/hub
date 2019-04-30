package com.flightstats.hub.metrics;

import com.flightstats.hub.config.AppProperty;
import com.flightstats.hub.config.PropertyLoader;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TracesTest {

    @Test
    public void testLimit() {
        Traces traces = new Traces(new AppProperty(PropertyLoader.getInstance()),"start");
        for (int i = 0; i < 1000; i++) {
            traces.add("" + i);
        }
        List<String> output = new ArrayList<>();
        traces.outputTraces(output::add);
        assertEquals(102, output.size());
    }

}