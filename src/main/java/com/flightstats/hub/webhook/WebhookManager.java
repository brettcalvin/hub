package com.flightstats.hub.webhook;

import com.flightstats.hub.app.HubProvider;
import com.flightstats.hub.app.HubServices;
import com.flightstats.hub.cluster.LastContentPath;
import com.flightstats.hub.cluster.WatchManager;
import com.flightstats.hub.cluster.Watcher;
import com.flightstats.hub.dao.Dao;
import com.flightstats.hub.model.ContentPath;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.api.CuratorEvent;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.flightstats.hub.app.HubServices.register;

@Singleton
@Slf4j
public class WebhookManager {
    private static final String WATCHER_PATH = "/groupCallback/watcher";

    @Inject
    private WatchManager watchManager;
    @Inject
    @Named("Webhook")
    private Dao<Webhook> webhookDao;
    @Inject
    private LastContentPath lastContentPath;
    @Inject
    private ActiveWebhooks activeWebhooks;

    @Inject
    private WebhookErrorService webhookErrorService;
    @Inject
    private WebhookContentPathSet webhookInProcess;

    @Inject
    private InternalWebhookClient webhookClient;

    @Inject
    private WebhookStateReaper webhookStateReaper;

    @Inject
    public WebhookManager() {
        register(new WebhookIdleService(), HubServices.TYPE.AFTER_HEALTHY_START, HubServices.TYPE.PRE_STOP);
        register(new WebhookScheduledService(), HubServices.TYPE.AFTER_HEALTHY_START);
    }

    @VisibleForTesting
    WebhookManager(WatchManager watchManager,
                   Dao<Webhook> webhookDao,
                   LastContentPath lastContentPath,
                   ActiveWebhooks activeWebhooks,
                   WebhookErrorService webhookErrorService,
                   WebhookContentPathSet webhookInProcess,
                   InternalWebhookClient webhookClient) {
        this.watchManager = watchManager;
        this.webhookDao = webhookDao;
        this.lastContentPath = lastContentPath;
        this.activeWebhooks = activeWebhooks;
        this.webhookErrorService = webhookErrorService;
        this.webhookInProcess = webhookInProcess;
        this.webhookClient = webhookClient;
    }

    private void start() {
        log.info("starting");
        watchManager.register(new Watcher() {
            @Override
            public void callback(CuratorEvent event) {
                manageWebhooks(true);
            }

            @Override
            public String getPath() {
                return WATCHER_PATH;
            }

        });
        manageWebhooks(false);
    }

    private synchronized void manageWebhooks(boolean useCache) {
        Set<Webhook> daoWebhooks = new HashSet<>(webhookDao.getAll(useCache));
        for (Webhook daoWebhook : daoWebhooks) {
            manageWebhook(daoWebhook, false);
        }
    }

    void notifyWatchers(Webhook webhook) {
        manageWebhook(webhook, true);
    }

    @VisibleForTesting
    void manageWebhook(Webhook daoWebhook, boolean webhookChanged) {
        if (daoWebhook.getTagUrl() != null && !daoWebhook.getTagUrl().isEmpty()) {
            // tag webhooks are not processed like normal webhooks.
            // they are used as prototype definitions for new webhooks added
            // automatically when a new/existing channel is assigned a tag that is
            // associated with a tag webhook
            return;
        }
        String name = daoWebhook.getName();
        DLog.log("WHM is activeWebhook " + activeWebhooks.isActiveWebhook(name) + " for " + name);
        if (activeWebhooks.isActiveWebhook(name)) {
            log.debug("found existing v2 webhook {}", name);
            List<String> servers = new ArrayList<>(activeWebhooks.getServers(name));
            DLog.log("WHM found " + servers.size() + " active servers for " + name);
            if (servers.size() >= 2) {
                log.warn("found multiple servers! {}", servers);
                Collections.shuffle(servers);
                for (int i = 1; i < servers.size(); i++) {
                    webhookClient.remove(name, servers.get(i));
                }
            }
            if (servers.isEmpty()) {
                webhookClient.runOnServerWithFewestWebhooks(name);
            } else if (webhookChanged) {
                webhookClient.runOnOneServer(name, servers);
            }
        } else {
            log.debug("found new v2 webhook {}", name);
            webhookClient.runOnServerWithFewestWebhooks(name);
        }
    }

    private void notifyWatchers() {
        watchManager.notifyWatcher(WATCHER_PATH);
    }

    public void delete(String name) {
        DLog.log("WHM delete(" + name + ") calling /internal delete on " + activeWebhooks.getServers(name).size() + " servers");
        webhookClient.remove(name, activeWebhooks.getServers(name));
        DLog.log("WHM delete(" + name + ") clearing ZK state during delete");
        webhookStateReaper.delete(name);
    }

    public void getStatus(Webhook webhook, WebhookStatus.WebhookStatusBuilder statusBuilder) {
        statusBuilder.lastCompleted(lastContentPath.get(webhook.getName(), WebhookStrategy.createContentPath(webhook), WebhookLeader.WEBHOOK_LAST_COMPLETED));
        try {
            statusBuilder.errors(webhookErrorService.lookup(webhook.getName()));
            ArrayList<ContentPath> inFlight = new ArrayList<>(new TreeSet<>(webhookInProcess.getSet(webhook.getName(), WebhookStrategy.createContentPath(webhook))));
            statusBuilder.inFlight(inFlight);
        } catch (Exception e) {
            log.warn("unable to get status " + webhook.getName(), e);
            statusBuilder.errors(Collections.emptyList());
            statusBuilder.inFlight(Collections.emptyList());
        }
    }

    private class WebhookIdleService extends AbstractIdleService {

        @Override
        protected void startUp() throws Exception {
            start();
        }

        @Override
        protected void shutDown() throws Exception {
            HubProvider.getInstance(LocalWebhookManager.class).stopAllLocal();
            notifyWatchers();
        }

    }

    private class WebhookScheduledService extends AbstractScheduledService {
        @Override
        protected void runOneIteration() throws Exception {
            manageWebhooks(false);
        }

        @Override
        protected Scheduler scheduler() {
            return Scheduler.newFixedRateSchedule(1, 5, TimeUnit.MINUTES);
        }
    }
}
