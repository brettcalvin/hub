package com.flightstats.hub.dao.aws;

import com.flightstats.hub.dao.ContentDaoUtil;
import com.flightstats.hub.model.BulkContent;
import com.flightstats.hub.model.Content;
import com.flightstats.hub.model.ContentKey;
import com.flightstats.hub.spoke.SpokeWriteContentDao;
import com.flightstats.hub.test.IntegrationTestSetup;
import com.flightstats.hub.util.StringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpokeS3SingleContentDaoTest {

    private final static Logger logger = LoggerFactory.getLogger(SpokeS3SingleContentDaoTest.class);

    private static S3SingleContentDao s3SingleContentDao;
    private static SpokeWriteContentDao spokeWriteContentDao;

    @BeforeAll
    static void setUpClass() {
        IntegrationTestSetup integrationTestSetup = IntegrationTestSetup.run();
        s3SingleContentDao = integrationTestSetup.getInstance(S3SingleContentDao.class);
        spokeWriteContentDao = integrationTestSetup.getInstance(SpokeWriteContentDao.class);
    }

    @Test
    void testSpokeReadWrite() throws Exception {
        String channel = "testSpokeReadWrite";
        byte[] bytes = StringUtils.randomAlphaNumeric(10 * 1024).getBytes();
        Content firstContent = Content.builder()
                .withContentKey(new ContentKey())
                .withContentType("stuff")
                .withData(bytes)
                .build();
        firstContent.packageStream();

        ContentKey key = spokeWriteContentDao.insert(channel, firstContent);
        Content spokeContent = spokeWriteContentDao.get(channel, key);
        logger.info("spoke {}", spokeContent);
        spokeContent.packageStream();
        ContentKey s3Key = s3SingleContentDao.insert(channel, spokeContent);
        assertEquals(key, s3Key);
        Content s3Content = s3SingleContentDao.get(channel, key);
        logger.info("s3 {}", s3Content);
        ContentDaoUtil.compare(spokeContent, s3Content, bytes);
    }

    @Test
    void testBulkSpokeReadWrite() throws Exception {
        String channel = "testBulkSpokeReadWrite";
        List<Content> items = Arrays.asList(ContentDaoUtil.createContent(), ContentDaoUtil.createContent(), ContentDaoUtil.createContent());
        BulkContent bulkContent = BulkContent.builder().channel(channel).isNew(false).build();
        bulkContent.getItems().addAll(items);

        SortedSet<ContentKey> bulkInserted = spokeWriteContentDao.insert(bulkContent);
        logger.info("spoke  inserted {}", bulkInserted);

        Content firstContent = items.get(0);
        ContentKey firstKey = firstContent.getContentKey().get();
        Content spokeContent = spokeWriteContentDao.get(channel, firstKey);
        spokeContent.packageStream();
        ContentKey s3Key = s3SingleContentDao.insert(channel, spokeContent);
        assertEquals(firstKey, s3Key);
        Content s3Content = s3SingleContentDao.get(channel, firstKey);
        logger.info("s3 {}", s3Content);
        ContentDaoUtil.compare(spokeContent, s3Content, firstKey.toString().getBytes());
    }

}