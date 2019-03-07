package com.flightstats.hub.callback;

import com.flightstats.hub.callback.model.RequestObject;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class CacheObject {

    private final Map<String, List<String>> cacheRequestObject = new ConcurrentHashMap<>();

    public void put(RequestObject requestObject) {
        String webhookName = requestObject.getName();

        synchronized (this) {
            if (cacheRequestObject.containsKey(webhookName)) {
                cacheRequestObject.get(webhookName).addAll(requestObject.getUris());
            } else {
                cacheRequestObject.put(
                        requestObject.getName(),
                        new ArrayList<>(requestObject.getUris()));
            }
        }
    }

    public List<String> get(String webhookName) {
        return cacheRequestObject.get(webhookName);
    }

    public boolean contains(String webhookName) {
        return cacheRequestObject.containsKey(webhookName);
    }
}
