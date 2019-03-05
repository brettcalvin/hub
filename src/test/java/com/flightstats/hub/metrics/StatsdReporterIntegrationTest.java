package com.flightstats.hub.metrics;

import com.flightstats.hub.dao.CachedDao;
import com.flightstats.hub.dao.CachedLowerCaseDao;
import com.flightstats.hub.dao.Dao;
import com.flightstats.hub.model.ChannelConfig;
import com.flightstats.hub.util.IntegrationUdpServer;
import com.flightstats.hub.webhook.Webhook;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class StatsdReporterIntegrationTest {
    private static String[] tags = { "tag1", "tag2" };
    private static Map<String, String> results;

    private static IntegrationUdpServer provideNewServer() {
        return IntegrationUdpServer.builder()
                .timeoutMillis(5000)
                .listening(true)
                .port(8123)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static StatsdReporter provideStatsDHandlers() {
        MetricsConfig metricsConfig = MetricsConfig.builder()
                .hostTag("test_host")
                .statsdPort(8123)
                .build();
        Dao<ChannelConfig> channelConfigDao = (Dao<ChannelConfig>) mock(CachedLowerCaseDao.class);
        Dao<Webhook> webhookDao =  (Dao<Webhook>) mock(CachedDao.class);
        StatsDFilter statsDFilter = new StatsDFilter(metricsConfig, channelConfigDao, webhookDao);
        statsDFilter.setOperatingClients();
        StatsDReporterProvider provider = new StatsDReporterProvider(statsDFilter, metricsConfig);
        return provider.get();
    }


    @BeforeClass
    public static void startMockStatsDServer() throws InterruptedException {
        StatsdReporter handlers = provideStatsDHandlers();
        IntegrationUdpServer udpServer = provideNewServer();
        TimeUnit.MILLISECONDS.sleep(300);
        handlers.count("countTest", 1, tags);
        handlers.increment("closeSocket", tags);
        results = udpServer.getResult();
    }

    @Test
    public void StatsDHandlersCount_metricShape() {
        assertEquals("hub.countTest:1|c|#tag2,tag1", results.get("hub.countTest"));
    }

}