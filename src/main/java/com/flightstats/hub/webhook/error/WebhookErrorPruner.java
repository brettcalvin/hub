package com.flightstats.hub.webhook.error;

import com.flightstats.hub.util.TimeUtil;
import org.joda.time.DateTime;

import javax.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

public class WebhookErrorPruner {
    private static final int MAX_SIZE = 10;
    private static final Duration MAX_AGE = Duration.ofDays(1);

    private final WebhookErrorStateService webhookErrorStateService;

    @Inject
    public WebhookErrorPruner(WebhookErrorStateService webhookErrorStateService) {
        this.webhookErrorStateService = webhookErrorStateService;
    }

    public List<WebhookError> pruneErrors(String webhook, List<WebhookError> errors) {
        List<WebhookError> errorsToRemove = getErrorsToRemove(errors);

        errorsToRemove.forEach(error -> webhookErrorStateService.delete(webhook, error.getName()));

        return errorsToRemove;
    }

    private List<WebhookError> getErrorsToRemove(List<WebhookError> errors) {
        return IntStream.range(0, errors.size())
                .filter(errorIndex -> shouldRemoveError(errors, errorIndex))
                .mapToObj(errors::get)
                .collect(toList());
    }

    private boolean shouldRemoveError(List<WebhookError> errors, int errorIndex) {
        DateTime cutoffTime = TimeUtil.now().minus(MAX_AGE.toMillis());
        int maxErrorIndexToDelete = errors.size() - MAX_SIZE;
        return errorIndex < maxErrorIndexToDelete || errors.get(errorIndex).getCreationTime().isBefore(cutoffTime);
    }
}