package com.flightstats.hub.webhook;

import com.flightstats.hub.model.ContentKey;
import com.flightstats.hub.model.MinutePath;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WebhookTest {

    private Webhook webhook;

    @BeforeEach
    void setUp() {
        webhook = Webhook.builder()
                .channelUrl("url").callbackUrl("end").build();
    }

    @Test
    void testSimple() {
        Webhook webhook = Webhook.fromJson(this.webhook.toJson());
        assertEquals("end", webhook.getCallbackUrl());
        assertEquals("url", webhook.getChannelUrl());
        assertNull(webhook.getName());
    }

    @Test
    void testTag() {
        String tagUrl = "http://hub/tag/allTheThings";
        Webhook aWebhook = Webhook.builder()
                .tagUrl(tagUrl)
                .callbackUrl("end").build();
        Webhook webhook = Webhook.fromJson(aWebhook.toJson());
        assertEquals("allTheThings", webhook.getTagFromTagUrl());
        assertEquals(tagUrl, webhook.getTagUrl());
    }

    @Test
    void testWithName() {
        Webhook webhook = this.webhook.withName("wither");
        webhook = Webhook.fromJson(webhook.toJson());
        assertEquals("end", webhook.getCallbackUrl());
        assertEquals("url", webhook.getChannelUrl());
        assertEquals("wither", webhook.getName());
    }

    @Test
    void testFromJson() {
        Webhook cycled = Webhook.fromJson(webhook.toJson());
        assertEquals(webhook, cycled);
    }

    @Test
    void testJsonStartItem() {
        ContentKey key = new ContentKey();
        String json = "{\"callbackUrl\":\"end\",\"channelUrl\":\"url\",\"startItem\":\"" +
                "http://hub/channel/stuff/" + key.toUrl() +
                "\"}";
        Webhook cycled = Webhook.fromJson(json);
        assertEquals(webhook, cycled);
        assertEquals(key, cycled.getStartingKey());
        String toJson = cycled.toJson();
        assertNotNull(toJson);
    }

    @Test
    void testJsonContentPath() {
        MinutePath key = new MinutePath();
        String json = "{\"callbackUrl\":\"end\",\"channelUrl\":\"url\"," +
                "\"startItem\":\"http://hub/channel/stuff/" + key.toUrl() +
                "\"}";
        Webhook cycled = Webhook.fromJson(json);
        assertEquals(webhook, cycled);
        assertEquals(key, cycled.getStartingKey());
        String toJson = cycled.toJson();
        assertNotNull(toJson);
    }

    @Test
    void testWithDefaults() {
        assertNull(webhook.getParallelCalls());
        assertNull(webhook.getBatch());
        webhook = webhook.withDefaults();
        assertEquals(1L, (long) webhook.getParallelCalls());
        assertEquals("SINGLE", webhook.getBatch());
    }

    @Test
    void testAllowedToChange() {
        Webhook hubA = Webhook.builder().name("name")
                .channelUrl("http://hubA/channel/name")
                .callbackUrl("url").build().withDefaults();
        Webhook hubB = Webhook.builder().name("name")
                .channelUrl("http://hubB/channel/name")
                .callbackUrl("url").build().withDefaults();

        assertTrue(hubA.allowedToChange(hubB));

        assertTrue(hubA.isChanged(hubB));

    }

    @Test
    void testChannelUrlChange() {
        Webhook hubA = Webhook.builder().name("name")
                .channelUrl("http://hubA/channel/name")
                .callbackUrl("url").build().withDefaults();

        Webhook hubC = Webhook.builder().name("name")
                .channelUrl("http://hubC/channel/nameC")
                .callbackUrl("url").build().withDefaults();

        assertFalse(hubA.allowedToChange(hubC));

        assertTrue(hubA.isChanged(hubC));

    }

    @Test
    void testStartingKey() {
        Webhook withDefaultsA = this.webhook.withDefaults();
        Webhook withDefaultsB = this.webhook.withDefaults();
        assertEquals(withDefaultsA, withDefaultsB);
        Webhook withStartingKey = withDefaultsB.withStartingKey(new ContentKey());
        assertEquals(withDefaultsA, withStartingKey);
        assertFalse(withDefaultsA.isChanged(withDefaultsA));
        assertFalse(withDefaultsB.isChanged(withDefaultsB));
    }

    @Test
    void testIsTagPrototype() {
        Webhook withDefaultsA = this.webhook.withDefaults();
        assertFalse(withDefaultsA.isTagPrototype());
        Webhook twh = Webhook.builder().name("name")
                .callbackUrl("url")
                .tagUrl("http://hub.com/tag/twh")
                .build().withDefaults();
        assertTrue(twh.isTagPrototype());
    }

    @Test
    void testSecondaryMetricsReporting() {
        Webhook withDefaults = this.webhook.withDefaults();
        assertFalse(withDefaults.isSecondaryMetricsReporting());
        String json = "{ \"secondaryMetricsReporting\": true }";
        Webhook newWebhook = Webhook.fromJson(json, Optional.of(withDefaults));
        assertTrue(newWebhook.isSecondaryMetricsReporting());
    }
}
