package com.flightstats.hub.callback;

import lombok.SneakyThrows;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import java.net.InetAddress;

public class CallbackServer {

    private static final String CALLBACK_PATH = "/callback";
    private static final int JETTY_PORT = 8090;
    private Server jettyServer;

    @SneakyThrows
    public void start() {

        ResourceConfig config = new ResourceConfig();
        config.packages("com.flightstats.hub.callback");

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        ServletHolder servletHolder = new ServletHolder(new ServletContainer(config));
        context.addServlet(servletHolder, "/*");
        servletHolder.setInitOrder(0);

        servletHolder.setInitParameter(
                "jersey.config.server.provider.classnames",
                CallbackResource.class.getCanonicalName());

        this.jettyServer = new Server(JETTY_PORT);
        this.jettyServer.setHandler(context);
        this.jettyServer.start();
    }

    @SneakyThrows
    public void stop() {
        this.jettyServer.stop();
    }

    @SneakyThrows
    private String getHostAddress() {
        return InetAddress.getLocalHost().getHostAddress();
    }

    public String getUrl() {
        return "http://" + getHostAddress() + ":" + JETTY_PORT + CALLBACK_PATH;
    }
}
