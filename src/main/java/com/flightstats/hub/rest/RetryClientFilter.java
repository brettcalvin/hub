package com.flightstats.hub.rest;

import com.flightstats.hub.config.AppProperties;
import com.flightstats.hub.config.SystemProperties;
import com.flightstats.hub.util.Sleeper;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.ClientFilter;
import lombok.extern.slf4j.Slf4j;

import java.net.UnknownHostException;

/**
 * RetryClientFilter assumes that connection issues may be transient, so retry is a good idea.
 */
@Slf4j
public class RetryClientFilter extends ClientFilter {

    private SystemProperties systemProperties;

    public RetryClientFilter(SystemProperties systemProperties){
        this.systemProperties = systemProperties;
    }

    public ClientResponse handle(ClientRequest clientRequest) throws ClientHandlerException {
        int maxRetries = systemProperties.getHttpMaxRetries();
        int sleep = systemProperties.getHttpSleep();
        ClientHandlerException lastCause = null;
        int attempt = 0;
        while (attempt < maxRetries) {
            attempt++;
            try {
                ClientResponse response = getNext().handle(clientRequest);
                if (response.getStatus() >= 500) {
                    log.info("500 level response {}  attempt={}", response, attempt);
                    if (attempt >= maxRetries) {
                        return response;
                    }
                } else {
                    return response;
                }
            } catch (ClientHandlerException e) {
                if (e.getCause() == null) {
                    throw e;
                }
                if (UnknownHostException.class.isAssignableFrom(e.getCause().getClass())) {
                    throw e;
                }
                lastCause = e;

                log.info("exception {} retry count {} ", clientRequest.getURI().toString(), attempt);
                log.debug(clientRequest.getURI().toString() + " stacktrace ", e);
            }
            Sleeper.sleep((int) (sleep * Math.pow(2, attempt)));
        }
        String msg = "Connection retries limit " + maxRetries + " exceeded for uri " + clientRequest.getURI();
        log.warn(msg);
        throw lastCause;
    }
}

