package com.flightstats.hub.channel;

import com.flightstats.hub.test.Integration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ChannelContentResourceTest {

    @BeforeAll
    static void setUpClass() throws Exception {
        Integration.startAwsHub();
    }

    @Test
    void testHttpURLConnection() {
        String accept = "text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2";
        assertFalse(ChannelContentResource.contentTypeIsNotCompatible(accept, MediaType.WILDCARD_TYPE));
    }

    @Test
    void testMost() {
        String accept = "text/html, image/gif, image/jpeg, */*; q=.2";
        assertFalse(ChannelContentResource.contentTypeIsNotCompatible(accept, MediaType.WILDCARD_TYPE));
    }

}