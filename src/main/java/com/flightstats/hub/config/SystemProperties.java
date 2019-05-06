package com.flightstats.hub.config;

import javax.inject.Inject;

public class SystemProperties {

    private final PropertiesLoader propertiesLoader;

    @Inject
    public SystemProperties(PropertiesLoader propertiesLoader) {
        this.propertiesLoader = propertiesLoader;
    }

    public int getGcSchedulerDelayInMinutes() {
        return this.propertiesLoader.getProperty("hub.gcMinutes", 60);
    }

    public boolean isGcEnabled() {
        return this.propertiesLoader.getProperty("hub.runGC", false);
    }

    public int getHttpConnectTimeoutInSec() {
        return this.propertiesLoader.getProperty("http.connect.timeout.seconds", 30);
    }

    public int getHttpReadTimeoutInSec() {
        return this.propertiesLoader.getProperty("http.read.timeout.seconds", 120);
    }

    public int getHttpMaxRetries() {
        return this.propertiesLoader.getProperty("http.maxRetries", 8);
    }

    public int getHttpSleep() {
        return this.propertiesLoader.getProperty("http.sleep", 1000);
    }

    public int getHttpBindPort() {
        return this.propertiesLoader.getProperty("http.bind_port", 8080);
    }

    public String getHttpBindIp() {
        return this.propertiesLoader.getProperty(    "http.bind_ip", "0.0.0.0");
    }

    public int getHttpIdleTimeInMillis() {
        return this.propertiesLoader.getProperty(    "http.idle_timeout", 30 * 1000);
    }

}
