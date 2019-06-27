package com.flightstats.hub.metrics;

public class MetricNames {
    public static final String LIFECYCLE_STARTUP_START = "lifecycle.startup.start";
    public static final String LIFECYCLE_STARTUP_COMPLETE = "lifecycle.startup.complete";
    public static final String LIFECYCLE_SHUTDOWN_START = "lifecycle.shutdown.start";
    public static final String LIFECYCLE_SHUTDOWN_COMPLETE = "lifecycle.shutdown.complete";

    public static final String WEBHOOK_LEADERSHIP_START = "webhook.leader.start";
    public static final String WEBHOOK_LEADERSHIP_COMPLETE = "webhook.leader.stop";
}
