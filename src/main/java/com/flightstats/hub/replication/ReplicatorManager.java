package com.flightstats.hub.replication;

import com.flightstats.hub.cluster.WatchManager;
import com.flightstats.hub.cluster.Watcher;
import com.flightstats.hub.dao.ChannelService;
import com.flightstats.hub.model.ChannelConfig;
import com.flightstats.hub.util.HubUtils;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.inject.Inject;
import org.apache.curator.framework.api.CuratorEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.flightstats.hub.app.HubServices.TYPE;
import static com.flightstats.hub.app.HubServices.register;

/**
 * Replication is moving from one Hub into another Hub
 * in Replication, we will presume we are moving forward in time
 * <p>
 * Secnario:
 * Producers are inserting Items into a Hub channel
 * HubA is setup to Replicate a channel from HubB
 * Replication starts at the item after now, and then stays up to date, with some minimal amount of lag.
 * Lag is a minimum of 'app.stable_seconds'.
 */
public class ReplicatorManager {
    public static final String REPLICATED = "replicated";
    private static final String REPLICATOR_WATCHER_PATH = "/replicator/watcher";
    private final static Logger logger = LoggerFactory.getLogger(ReplicatorManager.class);

    private final ChannelService channelService;
    private final HubUtils hubUtils;
    private final WatchManager watchManager;
    private final Map<String, ChannelReplicator> replicatorMap = new HashMap<>();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Inject
    public ReplicatorManager(ChannelService channelService, HubUtils hubUtils, WatchManager watchManager) {
        this.channelService = channelService;
        this.hubUtils = hubUtils;
        this.watchManager = watchManager;
        register(new ReplicatorService(), TYPE.FINAL_POST_START, TYPE.PRE_STOP);
    }

    private class ReplicatorService extends AbstractIdleService {

        @Override
        protected void startUp() throws Exception {
            startReplicator();
        }

        @Override
        protected void shutDown() throws Exception {
            stopped.set(true);
        }

    }

    public void startReplicator() {
        logger.info("starting");
        ReplicatorManager replicator = this;
        watchManager.register(new Watcher() {
            @Override
            public void callback(CuratorEvent event) {
                executor.submit(replicator::replicateChannels);
            }

            @Override
            public String getPath() {
                return REPLICATOR_WATCHER_PATH;
            }
        });

        executor.submit(replicator::replicateChannels);
    }

    private synchronized void replicateChannels() {
        if (stopped.get()) {
            logger.info("replication stopped");
            return;
        }
        logger.info("replicating channels");
        Set<String> replicators = new HashSet<>();
        Iterable<ChannelConfig> replicatedChannels = channelService.getChannels(REPLICATED);
        for (ChannelConfig channel : replicatedChannels) {
            logger.info("replicating channel {}", channel.getName());
            try {
                replicators.add(channel.getName());
                if (replicatorMap.containsKey(channel.getName())) {
                    ChannelReplicator replicator = replicatorMap.get(channel.getName());
                    if (!replicator.getChannel().getReplicationSource().equals(channel.getReplicationSource())) {
                        logger.info("changing replication source from {} to {}",
                                replicator.getChannel().getReplicationSource(), channel.getReplicationSource());
                        replicator.stop();
                        startReplication(channel);
                    }
                } else {
                    startReplication(channel);
                }
            } catch (Exception e) {
                logger.warn("error trying to replicate " + channel, e);
            }
        }
        Set<String> toStop = new HashSet<>(replicatorMap.keySet());
        toStop.removeAll(replicators);
        logger.info("stopping replicators {}", toStop);
        for (String nameToStop : toStop) {
            logger.info("stopping {}", nameToStop);
            ChannelReplicator replicator = replicatorMap.remove(nameToStop);
            replicator.stop();
        }
    }

    private void startReplication(ChannelConfig channel) {
        logger.debug("starting replication of " + channel);
        try {
            ChannelReplicator channelReplicator = new ChannelReplicator(channel, hubUtils);
            channelReplicator.start();
            replicatorMap.put(channel.getName(), channelReplicator);
        } catch (Exception e) {
            logger.warn("unable to start replication " + channel, e);
        }
    }

    public void notifyWatchers() {
        watchManager.notifyWatcher(REPLICATOR_WATCHER_PATH);
    }

}
