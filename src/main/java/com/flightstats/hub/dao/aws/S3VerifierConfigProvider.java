package com.flightstats.hub.dao.aws;

import com.flightstats.hub.app.HubProperties;
import com.google.inject.Provider;

public class S3VerifierConfigProvider implements Provider<S3VerifierConfig> {
    @Override
    public S3VerifierConfig get() {
        return S3VerifierConfig.builder()
                .enabled(HubProperties.getProperty("s3Verifier.run", true))
                .baseTimeoutMinutes(HubProperties.getProperty("s3Verifier.baseTimeoutMinutes", 2))
                .offsetMinutes(HubProperties.getProperty("s3Verifier.offsetMinutes", 15))
                .channelThreads(getChannelThreads())
                .queryThreads(getQueryThreads())
                .endpointUrlGenerator(channelName -> HubProperties.getAppUrl() + "internal/s3Verifier/" + channelName)
                .build();
    }

    private int getChannelThreads() {
        return HubProperties.getProperty("s3Verifier.channelThreads", 3);
    }

    private int getQueryThreads() {
        return getChannelThreads() * 2;
    }
}
