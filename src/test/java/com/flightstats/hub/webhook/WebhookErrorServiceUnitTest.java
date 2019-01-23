package com.flightstats.hub.webhook;

import com.flightstats.hub.dao.ChannelService;
import com.flightstats.hub.webhook.error.WebhookError;
import com.flightstats.hub.webhook.error.WebhookErrorPruner;
import com.flightstats.hub.webhook.error.WebhookErrorRepository;
import org.junit.Test;

import java.util.List;
import java.util.stream.IntStream;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WebhookErrorServiceUnitTest {
    private final ChannelService channelService = mock(ChannelService.class);
    private final WebhookErrorPruner errorPruner = mock(WebhookErrorPruner.class);
    private final WebhookErrorRepository errorRepo = mock(WebhookErrorRepository.class);

    @Test
    public void testAdd() {
        String webhookName = "webhookName";
        String errorMessage = "someError";

        List<WebhookError> webhookErrors = setupErrorMocks(webhookName, 12);
        List<WebhookError> errorsToDelete = webhookErrors.subList(0, 1);
        when(errorPruner.pruneErrors(webhookName, webhookErrors)).thenReturn(errorsToDelete);

        WebhookErrorService webhookErrorService = new WebhookErrorService(errorRepo, errorPruner, channelService);

        webhookErrorService.add(webhookName, errorMessage);
        verify(errorRepo).add(webhookName, errorMessage);
        verify(errorPruner).pruneErrors(webhookName, webhookErrors);
    }

    @Test
    public void testGet() {
        String webhookName = "webhookName";

        List<WebhookError> webhookErrors = setupErrorMocks(webhookName, 6);
        List<WebhookError> errorsToDelete = newArrayList(webhookErrors.get(1), webhookErrors.get(3), webhookErrors.get(5));
        when(errorPruner.pruneErrors(webhookName, webhookErrors)).thenReturn(errorsToDelete);

        WebhookErrorService webhookErrorService = new WebhookErrorService(errorRepo, errorPruner, channelService);

        List<String> errors = webhookErrorService.lookup(webhookName);

        assertEquals(newArrayList("0 message", "2 message", "4 message"), errors);
    }

    private List<WebhookError> setupErrorMocks(String webhookName, int numberOfErrors) {
        List<WebhookError> webhookErrors = IntStream.range(0, numberOfErrors)
                .mapToObj(number -> WebhookError.builder()
                        .name("error" + number)
                        .data(number + " message")
                        .build())
                .collect(toList());
        when(errorRepo.getErrors(webhookName)).thenReturn(webhookErrors);
        return webhookErrors;
    }
}
