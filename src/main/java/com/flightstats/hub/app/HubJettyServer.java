package com.flightstats.hub.app;

import com.flightstats.hub.ws.ChannelWSEndpoint;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.servlet.GuiceFilter;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.DispatcherType;
import javax.websocket.server.ServerContainer;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.EnumSet;
import java.util.EventListener;

import static com.google.common.base.Preconditions.checkState;

public class HubJettyServer {

    private final static Logger logger = LoggerFactory.getLogger(HubJettyServer.class);

    private final EventListener guice;
    private Server server;

    public HubJettyServer(EventListener guice) {
        this.guice = guice;
    }

    public void start() {
        checkState(server == null, "Server has already been started");
        try {
            server = new Server();
            HttpConfiguration httpConfig = new HttpConfiguration();
            SslContextFactory sslContextFactory = getSslContextFactory();
            if (null != sslContextFactory) {
                httpConfig.addCustomizer(new SecureRequestCustomizer());
            }
            ConnectionFactory connectionFactory = new HttpConnectionFactory(httpConfig);

            ServerConnector serverConnector = new ServerConnector(server, sslContextFactory, connectionFactory);
            serverConnector.setHost(HubProperties.getProperty("http.bind_ip", "0.0.0.0"));
            serverConnector.setPort(HubProperties.getProperty("http.bind_port", 8080));
            serverConnector.setIdleTimeout(HubProperties.getProperty("http.idle_timeout", 30 * 1000));

            server.setConnectors(new Connector[]{serverConnector});

            ServletContextHandler context = new ServletContextHandler(server, "/", ServletContextHandler.SESSIONS);
            ServerContainer wsContainer = WebSocketServerContainerInitializer.configureContext(context);
            wsContainer.addEndpoint(ChannelWSEndpoint.class);

            context.addEventListener(guice);
            context.addFilter(GuiceFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));

            server.start();
        } catch (Exception e) {
            logger.error("Exception in JettyServer: " + e.getMessage(), e);
            throw new RuntimeException("Failure in JettyServer: " + e.getMessage(), e);
        }
    }

    private SslContextFactory getSslContextFactory() throws IOException {
        SslContextFactory sslContextFactory = null;
        if (HubProperties.getProperty("app.encrypted", false)) {
            logger.info("starting hub with ssl!");
            sslContextFactory = new SslContextFactory();
            sslContextFactory.setKeyStorePath(getKeyStorePath());
            String keyStorePasswordPath = HubProperties.getProperty("app.keyStorePasswordPath", "/etc/ssl/key");
            URL passwordUrl = new File(keyStorePasswordPath).toURI().toURL();
            String password = Resources.readLines(passwordUrl, Charsets.UTF_8).get(0);
            sslContextFactory.setKeyStorePassword(password);
        }
        return sslContextFactory;
    }

    static String getKeyStorePath() throws UnknownHostException {
        String path = HubProperties.getProperty("app.keyStorePath", "/etc/ssl/") + HubHost.getLocalName() + ".jks";
        logger.info("using key store path: {}", path);
        return path;
    }

    public void halt() {
        try {
            if (server != null) {
                server.stop();
            }
        } catch (Exception e) {
            logger.warn("Exception while stopping JettyServer: " + e.getMessage(), e);
        }
    }
}