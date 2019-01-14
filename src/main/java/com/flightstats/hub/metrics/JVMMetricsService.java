package com.flightstats.hub.metrics;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.CachedThreadStatesGaugeSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.ScheduledReporter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import java.util.concurrent.TimeUnit;

@Singleton
public class JVMMetricsService {
    private final Logger logger = LoggerFactory.getLogger(JVMMetricsService.class);
    MetricRegistry metricsRegistry;
    ScheduledReporter influxdbReporter;
    MetricsConfig metricsConfig;

    @Inject
    public JVMMetricsService(MetricRegistry metricsRegistry, ScheduledReporter influxdbReporter, MetricsConfig metricsConfig) {
        this.metricsRegistry = metricsRegistry;
        this.influxdbReporter = influxdbReporter;
        this.metricsConfig = metricsConfig;
    }

    public void start() {
        if (metricsConfig.enabled()) {
            logger.info(
                    "starting jvm metrics service reporting to {} :// {} : {}",
                    metricsConfig.getInfluxdbProtocol(),
                    metricsConfig.getInfluxdbHost(),
                    metricsConfig.getInfluxdbPort());
            int intervalSeconds = metricsConfig.getReportingIntervalSeconds();
            String metricPrefix = "jvm_";
            metricsRegistry.register(metricPrefix + "gc", new GarbageCollectorMetricSet());
            metricsRegistry.register(metricPrefix + "thread", new CachedThreadStatesGaugeSet(intervalSeconds, TimeUnit.SECONDS));
            metricsRegistry.register(metricPrefix + "memory", new MemoryUsageGaugeSet());
            influxdbReporter.start(intervalSeconds, TimeUnit.SECONDS);

        } else {
            logger.info("not starting metrics collection for jvm: disabled");
        }
    }
}


