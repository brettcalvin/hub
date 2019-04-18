package com.flightstats.hub.util;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Builder
public class IntegrationUdpServer {
    private int port;

    private final Map<String, String> store = new HashMap<>();
    private final AtomicBoolean listening = new AtomicBoolean(true);

    public CompletableFuture<Map<String, String>> getServerFuture(CountDownLatch startupCountDownLatch, ExecutorService executorService) {
        return  CompletableFuture.supplyAsync(() -> {
            try {
                DatagramSocket serverSocket = new DatagramSocket(port);
                startupCountDownLatch.countDown();
                while (listening.get()) {
                    log.info("udp server listening on PORT: {}", port);
                    byte[] data = new byte[70];
                    DatagramPacket receivePacket = new DatagramPacket(data, data.length);
                    serverSocket.receive(receivePacket);

                    String result = listen(receivePacket);
                    addValueToStore(result);
                }
            } catch (Exception e) {
                log.error("udp server error listening on port %s", port, e);
            }
            return store;
        }, executorService);
    }

    private String listen(DatagramPacket receivePacket) throws IOException {
        InputStream inputStream = new ByteArrayInputStream(receivePacket.getData());
        try (
            InputStreamReader streamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(streamReader)) {

            return reader
                    .lines()
                    .findFirst()
                    .map(String::trim)
                    .orElse("");
        }
    }

    private void addValueToStore(String currentResult) {
        String key = currentResult.substring(0, currentResult.indexOf(":"));
        store.put(key, currentResult);
    }

    public Map<String, String> getResult () {
        return store;
    }

    public void stop() {
        this.listening.set(false);
    }
}
