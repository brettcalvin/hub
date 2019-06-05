package com.flightstats.hub.system.config;

import com.flightstats.hub.client.CallbackClientFactory;
import com.flightstats.hub.client.HubClientFactory;
import com.flightstats.hub.system.service.CallbackService;
import com.flightstats.hub.system.service.ChannelService;
import com.flightstats.hub.system.service.WebhookService;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;

import javax.inject.Singleton;
import java.util.Properties;

@Slf4j
public class GuiceModule extends AbstractModule {
    private static final String PROPERTY_FILE_NAME = "system-test-hub.properties";

    @Override
    protected void configure() {
        Properties properties = new PropertiesLoader().loadProperties(PROPERTY_FILE_NAME);
        Names.bindProperties(binder(), properties);

        bind(HubClientFactory.class);
        bind(CallbackClientFactory.class);
        bind(ChannelService.class);
        bind(WebhookService.class);
        bind(CallbackService.class);
    }

    @Singleton
    @Named("hub")
    @Provides
    public Retrofit retrofitHub(@Named(PropertyNames.HUB_URL_TEMPLATE) String hubBaseUrl,
                                @Named(PropertyNames.HELM_RELEASE_NAME) String releaseName) {
        return new Retrofit.Builder()
                .baseUrl(String.format(hubBaseUrl, releaseName))
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .client(new OkHttpClient.Builder().build())
                .build();
    }

    @Singleton
    @Named("callback")
    @Provides
    public Retrofit retrofitCallback(@Named(PropertyNames.CALLBACK_URL_TEMPLATE) String callbackUrl,
                                     @Named(PropertyNames.HELM_RELEASE_NAME) String releaseName) {
        return new Retrofit.Builder()
                .baseUrl(String.format(callbackUrl, releaseName))
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .client(new OkHttpClient.Builder().build())
                .build();
    }
}
