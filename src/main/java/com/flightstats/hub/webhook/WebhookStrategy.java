package com.flightstats.hub.webhook;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightstats.hub.model.ContentKey;
import com.flightstats.hub.model.ContentPath;
import com.flightstats.hub.model.MinutePath;
import com.flightstats.hub.model.SecondPath;
import com.flightstats.hub.util.TimeUtil;
import com.google.common.base.Optional;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

interface WebhookStrategy extends AutoCloseable {

    ContentPath getStartingPath();

    ContentPath getLastCompleted();

    void start(Webhook webhook, ContentPath startingKey);

    Optional<ContentPath> next() throws Exception;

    ObjectNode createResponse(ContentPath contentPath);

    ContentPath inProcess(ContentPath contentPath);

    static ContentPath createContentPath(Webhook webhook) {
        if (webhook.isSecond()) {
            return new SecondPath();
        }
        if (webhook.isMinute()) {
            return new MinutePath();
        }
        return new ContentKey(TimeUtil.now(), "initial");
    }

    static void close(AtomicBoolean shouldExit, ExecutorService executorService, BlockingQueue queue) {
        if (!shouldExit.get()) {
            shouldExit.set(true);
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
        if (queue != null) {
            queue.clear();
        }
    }
}
