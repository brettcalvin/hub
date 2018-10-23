package com.flightstats.hub.dao.aws;

import com.flightstats.hub.app.HubProperties;
import com.flightstats.hub.app.HubServices;
import com.flightstats.hub.cluster.CuratorLock;
import com.flightstats.hub.cluster.LastContentPath;
import com.flightstats.hub.cluster.Leadership;
import com.flightstats.hub.cluster.Lockable;
import com.flightstats.hub.cluster.ZooKeeperState;
import com.flightstats.hub.dao.ChannelService;
import com.flightstats.hub.dao.ContentDao;
import com.flightstats.hub.dao.QueryResult;
import com.flightstats.hub.exception.FailedQueryException;
import com.flightstats.hub.metrics.ActiveTraces;
import com.flightstats.hub.metrics.MetricsService;
import com.flightstats.hub.metrics.Traces;
import com.flightstats.hub.model.ChannelConfig;
import com.flightstats.hub.model.ChannelContentKey;
import com.flightstats.hub.model.ContentKey;
import com.flightstats.hub.model.MinutePath;
import com.flightstats.hub.model.TimeQuery;
import com.flightstats.hub.spoke.SpokeStore;
import com.flightstats.hub.util.HubUtils;
import com.flightstats.hub.util.RuntimeInterruptedException;
import com.flightstats.hub.util.Sleeper;
import com.flightstats.hub.util.TimeUtil;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import org.apache.curator.framework.CuratorFramework;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Singleton
public class S3Verifier {

    private final static Logger logger = LoggerFactory.getLogger(S3Verifier.class);
    public final static String LAST_SINGLE_VERIFIED = "/S3VerifierSingleLastVerified/";
    private final static String LEADER_PATH = "/S3VerifierSingleService";
    public final static String MISSING_ITEM_METRIC_NAME = "s3.verifier.missing";

    private final ContentDao spokeWriteContentDao;
    private final ContentDao s3SingleContentDao;
    private final S3WriteQueue s3WriteQueue;
    private final ChannelService channelService;
    private final LastContentPath lastContentPath;
    private final Client httpClient;
    private final ZooKeeperState zooKeeperState;
    private final CuratorFramework curator;
    private final MetricsService metricsService;
    private final int offsetMinutes;
    private final String appUrl;
    private int writeSpokeStoreTtlMinutes;
    private final ExecutorService channelThreadPool;
    private final ExecutorService queryThreadPool;


    @Inject
    public S3Verifier(@Named(ContentDao.WRITE_CACHE) ContentDao spokeWriteContentDao,
                      @Named(ContentDao.SINGLE_LONG_TERM) ContentDao s3SingleContentDao,
                      S3WriteQueue s3WriteQueue,
                      ChannelService channelService,
                      LastContentPath lastContentPath,
                      Client httpClient,
                      ZooKeeperState zooKeeperState,
                      CuratorFramework curator,
                      MetricsService metricsService,
                      HubProperties hubProperties)
    {
        this.spokeWriteContentDao = spokeWriteContentDao;
        this.s3SingleContentDao = s3SingleContentDao;
        this.s3WriteQueue = s3WriteQueue;
        this.channelService = channelService;
        this.lastContentPath = lastContentPath;
        this.httpClient = httpClient;
        this.zooKeeperState = zooKeeperState;
        this.curator = curator;
        this.metricsService = metricsService;
        this.offsetMinutes = hubProperties.getProperty("s3Verifier.offsetMinutes", 15);
        this.appUrl = hubProperties.getAppUrl();
        this.writeSpokeStoreTtlMinutes = hubProperties.getSpokeTtlMinutes(SpokeStore.WRITE);

        int channelThreads = hubProperties.getProperty("s3Verifier.channelThreads", 3);
        this.channelThreadPool = Executors.newFixedThreadPool(channelThreads, new ThreadFactoryBuilder().setNameFormat("S3VerifierChannel-%d").build());
        this.queryThreadPool = Executors.newFixedThreadPool(channelThreads * 2, new ThreadFactoryBuilder().setNameFormat("S3VerifierQuery-%d").build());

        if (hubProperties.getProperty("s3Verifier.run", true)) {
            HubServices.register(new S3ScheduledVerifierService(), HubServices.TYPE.AFTER_HEALTHY_START, HubServices.TYPE.PRE_STOP);
        }
    }

    private void verifySingleChannels() {
        try {
            logger.info("Verifying Single S3 data");
            Iterable<ChannelConfig> channels = channelService.getChannels();
            for (ChannelConfig channel : channels) {
                if (channel.isSingle() || channel.isBoth()) {
                    channelThreadPool.submit(() -> {
                        String name = Thread.currentThread().getName();
                        Thread.currentThread().setName(name + "|" + channel.getDisplayName());
                        String url = appUrl + "internal/s3Verifier/" + channel.getDisplayName();
                        logger.debug("calling {}", url);
                        ClientResponse post = null;
                        try {
                            post = httpClient.resource(url).post(ClientResponse.class);
                            logger.debug("response from post {}", post);
                        } finally {
                            HubUtils.close(post);
                            Thread.currentThread().setName(name);
                        }
                    });
                }
            }
            logger.info("Completed Verifying Single S3 data");
        } catch (Exception e) {
            logger.error("Error: ", e);
        }
    }

    void verifyChannel(String channelName) {
        DateTime now = TimeUtil.now();
        ChannelConfig channel = channelService.getChannelConfig(channelName, false);
        if (channel == null) {
            return;
        }
        VerifierRange range = getSingleVerifierRange(now, channel);
        if (range != null) {
            verifyChannel(range);
        }
    }

    VerifierRange getSingleVerifierRange(DateTime now, ChannelConfig channel) {
        VerifierRange range = new VerifierRange(channel);
        MinutePath spokeTtlTime = getSpokeTtlPath(now);
        now = channelService.getLastUpdated(channel.getDisplayName(), new MinutePath(now)).getTime();
        DateTime start = now.minusMinutes(1);
        range.endPath = new MinutePath(start);
        MinutePath defaultStart = new MinutePath(start.minusMinutes(offsetMinutes));
        range.startPath = (MinutePath) lastContentPath.get(channel.getDisplayName(), defaultStart, LAST_SINGLE_VERIFIED);
        if (channel.isLive() && range.startPath.compareTo(spokeTtlTime) < 0) {
            range.startPath = spokeTtlTime;
        }
        return range;
    }

    private void verifyChannel(VerifierRange range) {
        String channelName = range.channel.getDisplayName();
        SortedSet<ContentKey> keysToAdd = getMissing(range.startPath, range.endPath, channelName, s3SingleContentDao, new TreeSet<>());
        logger.debug("verifyChannel.starting {}", range);
        for (ContentKey key : keysToAdd) {
            logger.trace("found missing {} {}", channelName, key);
            metricsService.increment(MISSING_ITEM_METRIC_NAME);
            s3WriteQueue.add(new ChannelContentKey(channelName, key));
        }
        logger.debug("verifyChannel.completed {}", range);
        lastContentPath.updateIncrease(range.endPath, range.channel.getDisplayName(), LAST_SINGLE_VERIFIED);
    }

    private SortedSet<ContentKey> getMissing(MinutePath startPath, MinutePath endPath, String channelName, ContentDao s3ContentDao,
                                             SortedSet<ContentKey> foundCacheKeys) {
        QueryResult queryResult = new QueryResult(1);
        SortedSet<ContentKey> longTermKeys = new TreeSet<>();
        TimeQuery.TimeQueryBuilder builder = TimeQuery.builder()
                .channelName(channelName)
                .startTime(startPath.getTime())
                .unit(TimeUtil.Unit.MINUTES);
        long timeout = 1;
        if (endPath != null) {
            Duration duration = new Duration(startPath.getTime(), endPath.getTime());
            timeout += duration.getStandardDays();
            builder.limitKey(ContentKey.lastKey(endPath.getTime()));
        }
        TimeQuery timeQuery = builder.build();
        try {
            CountDownLatch latch = new CountDownLatch(2);
            runInQueryPool(ActiveTraces.getLocal(), latch, () -> {
                SortedSet<ContentKey> spokeKeys = spokeWriteContentDao.queryByTime(timeQuery);
                foundCacheKeys.addAll(spokeKeys);
                queryResult.addKeys(spokeKeys);
            });
            runInQueryPool(ActiveTraces.getLocal(), latch, () -> longTermKeys.addAll(s3ContentDao.queryByTime(timeQuery)));
            latch.await(timeout, TimeUnit.MINUTES);
            queryResult.getContentKeys().removeAll(longTermKeys);
            if (queryResult.getContentKeys().size() > 0) {
                logger.info("missing items {} {}", channelName, queryResult.getContentKeys());
            }
            if (queryResult.hadSuccess()) {
                return queryResult.getContentKeys();
            }
            throw new FailedQueryException("unable to query spoke");
        } catch (InterruptedException e) {
            throw new RuntimeInterruptedException(e);
        }
    }

    private void runInQueryPool(Traces traces, CountDownLatch countDownLatch, Runnable runnable) {
        queryThreadPool.submit(() -> {
            ActiveTraces.setLocal(traces);
            try {
                runnable.run();
            } finally {
                countDownLatch.countDown();
            }
        });
    }

    private MinutePath getSpokeTtlPath(DateTime now) {
        return new MinutePath(now.minusMinutes(writeSpokeStoreTtlMinutes - 2));
    }

    class VerifierRange {

        MinutePath startPath;
        MinutePath endPath;
        ChannelConfig channel;

        VerifierRange(ChannelConfig channel) {
            this.channel = channel;
        }

        public String toString() {
            return "com.flightstats.hub.dao.aws.S3Verifier.VerifierRange(startPath=" + this.startPath + ", endPath=" + this.endPath + ", channel=" + this.channel + ")";
        }
    }

    private class S3ScheduledVerifierService extends AbstractScheduledService implements Lockable {

        @Override
        protected void runOneIteration() {
            CuratorLock curatorLock = new CuratorLock(curator, zooKeeperState, LEADER_PATH);
            curatorLock.runWithLock(this, 1, TimeUnit.SECONDS);
        }

        protected Scheduler scheduler() {
            return Scheduler.newFixedDelaySchedule(0, offsetMinutes, TimeUnit.MINUTES);
        }

        @Override
        public void takeLeadership(Leadership leadership) {
            logger.info("taking leadership");
            while (leadership.hasLeadership()) {
                long start = System.currentTimeMillis();
                verifySingleChannels();
                long sleep = TimeUnit.MINUTES.toMillis(offsetMinutes) - (System.currentTimeMillis() - start);
                logger.debug("sleeping for {} ms", sleep);
                Sleeper.sleep(Math.max(0, sleep));
                logger.debug("waking up after sleep");
            }
            logger.info("lost leadership");
        }
    }
}
