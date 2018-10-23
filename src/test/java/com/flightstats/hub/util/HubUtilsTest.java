package com.flightstats.hub.util;

import com.flightstats.hub.model.*;
import com.flightstats.hub.test.TestMain;
import com.google.inject.Injector;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.Assert.*;

public class HubUtilsTest {

    private final static Logger logger = LoggerFactory.getLogger(HubUtilsTest.class);

    private static final String HUT_TEST = "test_0_HubUtilsTest" + StringUtils.randomAlphaNumeric(6);
    private static HubUtils hubUtils;
    private static String channelUrl;

    @BeforeClass
    public static void setUpClass() throws Exception {
        Injector injector = TestMain.start();
        hubUtils = injector.getInstance(HubUtils.class);
        channelUrl = create();
    }

    /*
    Use this to test a remote hub instance.
    @BeforeClass
    public static void setUpClass() throws Exception {
        hubUtils = new HubUtils(null, HubModule.buildJerseyClient());
        channelUrl = create();
    }*/

    @Test
    public void testCreateInsert() {
        ChannelConfig channel = hubUtils.getChannel(channelUrl);
        assertNotNull(channel);
        assertEquals(HUT_TEST, channel.getName());
        assertEquals(120, channel.getTtlDays());

        String data = "some data " + System.currentTimeMillis();
        ContentKey key = insertItem(channelUrl, data);

        logger.info("key {}", key);
        assertNotNull(key);

        Content gotContent = hubUtils.get(channelUrl, key);
        assertNotNull(gotContent);
        assertEquals("text/plain", gotContent.getContentType().get());
        assertArrayEquals(data.getBytes(), gotContent.getData());
    }

    private ContentKey insertItem(String channelUrl, String data) {
        ByteArrayInputStream stream = new ByteArrayInputStream(data.getBytes());
        Content content = Content.builder()
                .withContentType("text/plain")
                .withStream(stream)
                .build();
        return hubUtils.insert(channelUrl, content);
    }

    private static String create() {
        ChannelConfig hut_test = ChannelConfig.builder().name(HUT_TEST).build();
        String channelUrl = "http://localhost:9080/channel/" + HUT_TEST;
        hubUtils.putChannel(channelUrl, hut_test);
        return channelUrl;
    }

    @Test
    public void testBulkInsert() {
        String data = "--abcdefg\r\n" +
                "Content-Type: text/plain\r\n" +
                " \r\n" +
                "message one\r\n" +
                "--abcdefg\r\n" +
                "Content-Type: text/plain\r\n" +
                " \r\n" +
                "message two\r\n" +
                "--abcdefg\r\n" +
                "Content-Type: text/plain\r\n" +
                " \r\n" +
                "message three\r\n" +
                "--abcdefg\r\n" +
                "Content-Type: text/plain\r\n" +
                " \r\n" +
                "message four\r\n" +
                "--abcdefg--";
        ByteArrayInputStream stream = new ByteArrayInputStream(data.getBytes());
        BulkContent bulkContent = BulkContent.builder()
                .channel(HUT_TEST)
                .contentType("multipart/mixed; boundary=abcdefg")
                .stream(stream)
                .build();
        Collection<ContentKey> keys = hubUtils.insert(channelUrl, bulkContent);
        assertEquals(4, keys.size());
        for (ContentKey key : keys) {
            Content gotContent = hubUtils.get(channelUrl, key);
            assertNotNull(gotContent);
        }
    }

    @Test
    public void testQuery() {
        SortedSet<ContentKey> keys = new TreeSet<>();
        for (int i = 0; i < 10; i++) {
            keys.add(insertItem(channelUrl, "testQuery " + System.currentTimeMillis()));
        }
        TimeQuery timeQuery = TimeQuery.builder()
                .startTime(keys.first().getTime())
                .unit(TimeUtil.Unit.HOURS)
                .stable(false)
                .build();
        Collection<ContentKey> foundKeys = hubUtils.query(channelUrl, timeQuery);
        logger.info("inserted {}", keys);
        logger.info("foundKeys {}", foundKeys);
        assertTrue(foundKeys.containsAll(keys));

        runDirectionQuery(keys, keys.first(), true);
        runDirectionQuery(keys, keys.last(), false);
    }

    private void runDirectionQuery(SortedSet<ContentKey> keys, ContentKey startKey, boolean next) {
        Collection<ContentKey> foundKeys;
        DirectionQuery nextQuery = DirectionQuery.builder()
                .startKey(startKey)
                .next(next)
                .stable(false)
                .count(9)
                .build();
        foundKeys = hubUtils.query(channelUrl, nextQuery);
        foundKeys.add(startKey);
        logger.info("inserted {}", keys);
        logger.info("foundKeys {}", foundKeys);
        assertTrue(foundKeys.containsAll(keys));

    }
}
