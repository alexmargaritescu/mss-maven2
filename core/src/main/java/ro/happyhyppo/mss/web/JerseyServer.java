package ro.happyhyppo.mss.web;

import static org.eclipse.jetty.servlet.ServletContextHandler.NO_SESSIONS;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JerseyServer implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(JerseyServer.class);

    private List<String> resources = new ArrayList<>();

    public JerseyServer() {
        this.resources.add("com.nexog.mss.web.rest");
    }

    public void addResource(String resource) {
        logger.info("Adding resource " + resource);
        this.resources.add(resource);
    }

    public static void main(String[] args) {
    }

    @Override
    public void run() {
        int port = 8080;
        try {
            port = Integer.parseInt(System.getProperty("jersey.port", "8080"));
        } catch (Exception e) {
            logger.warn("Cannot use Jersey port from system properties: " + e.getMessage());
        }
        logger.info("Using Jersey port " + port);
        Server server = new Server(port);
        ServletContextHandler servletContextHandler = new ServletContextHandler(NO_SESSIONS);
        servletContextHandler.setContextPath("/");
        server.setHandler(servletContextHandler);
        ServletHolder servletHolder = servletContextHandler.addServlet(ServletContainer.class, "/api/*");
        servletHolder.setInitOrder(0);
        servletHolder.setInitParameter("jersey.config.server.provider.packages", String.join(",", resources));
        try {
            server.start();
        } catch (Exception ex) {
            logger.error("Error occurred while starting Jetty", ex);
            server.destroy();
        }
    }

}
