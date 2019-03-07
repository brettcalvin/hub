package com.flightstats.hub.testcase;

import com.flightstats.hub.BaseTest;
import com.flightstats.hub.callback.CallbackServer;
import com.flightstats.hub.client.CallbackResourceClient;
import com.flightstats.hub.client.ChannelItemResourceClient;
import com.flightstats.hub.client.ChannelResourceClient;
import com.flightstats.hub.client.WebhookResourceClient;
import com.flightstats.hub.model.Channel;
import com.flightstats.hub.model.ChannelItem;
import com.flightstats.hub.model.Webhook;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Slf4j
public class WebhookLifecycleTest extends BaseTest {

    private static final String EMPTY_STRING = "";

    private ChannelItemResourceClient channelItemResourceClient;
    private ChannelResourceClient channelResourceClient;
    private WebhookResourceClient webhookResourceClient;
    private CallbackResourceClient callbackResourceClient;
    private CallbackServer callbackServer;

    private String channelName;
    private String webhookName;

    private List<String> channelItemsPosted;

    @Before
    public void setup() {
        this.channelItemResourceClient = getHttpClient(ChannelItemResourceClient.class);
        this.channelResourceClient = getHttpClient(ChannelResourceClient.class);
        this.webhookResourceClient = getHttpClient(WebhookResourceClient.class);
        this.callbackResourceClient = getHttpClient(CallbackResourceClient.class);
        this.callbackServer = injector.getInstance(CallbackServer.class);

        this.callbackServer.start();

        this.channelName = generateRandomString();
        this.webhookName = generateRandomString();
    }

    @Test
    @SneakyThrows
    public void testWebhookWithNoStartItem() {
        final String data = "{\"webhook\": \"test1\", \"startitem\":\"false\"}";

        createChannel();
        addWebhook(buildWebhook(EMPTY_STRING));

        List<String> channelItems = addItemsToChannel(data);
        verifyWebhookCallback(channelItems);
    }

    // @Test
    @SneakyThrows
    public void testWebhookWithStartItem() {
        final String data = "{\"webhook\": \"test2\", \"startitem\":\"true\"}";

        createChannel();
        List<String> channelItems = addItemsToChannel(data);

        addWebhook(buildWebhook(channelItems.get(4)));
        verifyWebhookCallback(channelItems.subList(5, channelItems.size()));
    }

    @SneakyThrows
    private void verifyWebhookCallback(List<String> channelItems) {
        Call<String> call = callbackResourceClient.get(callbackServer.getUrl() + "/" + this.webhookName);
        await().atMost(Duration.TWO_MINUTES).until(() -> {
            Response<String> response = call.clone().execute();
            channelItemsPosted = parseResponse(response.body());
            return response.code() == OK.getStatusCode()
                    && channelItemsPosted.size() == channelItems.size();
        });

        Collections.sort(channelItems);
        Collections.sort(channelItemsPosted);

        log.info("channelItemsPosted {} ", channelItemsPosted);
        log.info("channelItems {} ", channelItems);

        assertTrue(channelItems.equals(channelItemsPosted));
    }

    private List<String> parseResponse(String body) {
        if (!isBlank(body)) {
            String parsedString = body.replace("[", EMPTY_STRING)
                    .replace("]", EMPTY_STRING);
            List<String> postedItems = Arrays.asList(parsedString.split(","));
            postedItems.replaceAll(String::trim);
            return postedItems;
        }
        return Collections.EMPTY_LIST;
    }

    @SneakyThrows
    private void createChannel() {
        log.info("Channel name {} ", channelName);

        Call<Object> call = channelResourceClient.create(Channel.builder().name(channelName).build());
        Response<Object> response = call.execute();

        assertEquals(CREATED.getStatusCode(), response.code());
    }

    @SneakyThrows
    private void addWebhook(Webhook webhook) {
        log.info("Webhook name {} ", webhookName);

        Call<com.flightstats.hub.model.Webhook> call = webhookResourceClient.create(webhookName, webhook);
        Response<com.flightstats.hub.model.Webhook> response = call.execute();

        assertEquals(CREATED.getStatusCode(), response.code());
    }

    private List<String> addItemsToChannel(Object data) {
        final List<String> channelItems = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            channelItems.add(addItemToChannel(data));
            log.info("Added channel item {} ", channelItems.get(i));
        }
        return channelItems;
    }

    @SneakyThrows
    private String addItemToChannel(Object data) {
        Call<ChannelItem> call = channelItemResourceClient.add(channelName, data);
        Response<ChannelItem> response = call.execute();

        assertEquals(CREATED.getStatusCode(), response.code());
        assertNotNull(response);
        assertNotNull(response.body());

        return response.body().get_links().getSelf().getHref();
    }

    private Webhook buildWebhook(String startItem) {
        Webhook.WebhookBuilder builder = Webhook.builder()
                .channelUrl(retrofit.baseUrl() + "channel/" + channelName)
                .callbackUrl(callbackServer.getUrl())
                .parallelCalls(2)
                .batch("single");

        if (!isBlank(startItem)) {
            builder.startItem(startItem);
        }

        return builder.build();
    }

    @After
    @SneakyThrows
    public void cleanup() {

        this.channelResourceClient.delete(channelName).execute();
        this.webhookResourceClient.delete(webhookName).execute();

        this.callbackServer.stop();
    }

}
