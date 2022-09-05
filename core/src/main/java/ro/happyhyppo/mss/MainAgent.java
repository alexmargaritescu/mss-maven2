package ro.happyhyppo.mss;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.happyhyppo.mss.file.FileWatcher;
import ro.happyhyppo.mss.internal.Authority;
import ro.happyhyppo.mss.internal.HttpServer;
import ro.happyhyppo.mss.internal.SnmpAgent;
import ro.happyhyppo.mss.internal.TrapManager;
import ro.happyhyppo.mss.net.NetworkElement;
import ro.happyhyppo.mss.web.JerseyServer;

public class MainAgent {

    private static final Logger LOG = LoggerFactory.getLogger(MainAgent.class);

    public static String USER_APP_DIR;

    public final String MOUNT_VIRTUAL_IP;

    public static final String NETWORK_JSON_FILE = "network.json";

    private static MainAgent instance;

    private Map<String, NetworkElement> elements;

    final AtomicInteger count = new AtomicInteger(1);

    private MainAgent() {
        elements = Collections.synchronizedMap(new HashMap<>());
        USER_APP_DIR = System.getProperty("mss.config");
        if (USER_APP_DIR == null) {
            USER_APP_DIR = System.getProperty("user.home") + File.separator + ".mss" + File.separator;
        } else {
            USER_APP_DIR = USER_APP_DIR + File.separator;
        }
        MOUNT_VIRTUAL_IP = System.getProperty("mss.mount");
    }

    public static synchronized MainAgent instance() {
        if (instance == null) {
            instance = new MainAgent();
        }
        return instance;
    }

    public Collection<NetworkElement> listElements() {
        synchronized (elements) {
            return elements.values();
        }
    }

    public NetworkElement findElement(String ipAddress) {
        return elements.get(ipAddress);
    }

    public NetworkElement addElement(String ipAddress, int port, String readCommunity, String writeCommunity,
            String className, int http, String trapManagerIpAddress, int trapManagerPort) {
        synchronized (elements) {
            if (elements.containsKey(ipAddress)) {
                LOG.error("Network element with IP " + ipAddress + " already exists!");
                return null;
            }
            NetworkElement element = createElement(ipAddress, port, readCommunity, writeCommunity, className, http);
            elements.put(ipAddress, element);
            try {
                TrapManager trapManager = new TrapManager(trapManagerIpAddress, trapManagerPort, null);
                new SnmpAgent(element, trapManager).run();
                if (element.getHttp() > 0) {
                    new Thread(new HttpServer(element)).start();
                }
            } catch (Exception e) {
                LOG.error("Failed to start SNMP Agent for " + element.getIpAddress(), e);
            }
            return element;
        }
    }

    public NetworkElement removeElement(String ipAddress) {
        return elements.remove(ipAddress);
    }

    private NetworkElement createElement(String ipAddress, int port, String readCommunity, String writeCommunity,
            String className, int http) {
        int id = count.getAndIncrement();
        NetworkElement element = null;
        try {
            Class<?> neClass = Class.forName(className);
            Constructor<?> constructor = neClass
                    .getConstructor(new Class[] { int.class, String.class, int.class, String.class, String.class });
            element = (NetworkElement) constructor
                    .newInstance(new Object[] { id, ipAddress, port, readCommunity, writeCommunity });
            LOG.info("Equipment " + ipAddress + " instantiated as " + className);
        } catch (Exception e) {
            LOG.warn("Could not instantiate " + className + ", using default", e);
            element = new NetworkElement(id, ipAddress, port, readCommunity, writeCommunity);
        }
        element.setHttp(http);
        return element;
    }

    private void start() {
        LOG.info("Starting main agent");
        File netwworkJsonFile = new File(USER_APP_DIR + NETWORK_JSON_FILE);
        InputStream inputStream = null;
        TrapManager trapManager = null;
        JerseyServer jerseyServer = new JerseyServer();
        try {
            inputStream = new FileInputStream(netwworkJsonFile);
            JsonReader jsonReader = Json.createReader(inputStream);
            JsonObject networkJson = jsonReader.readObject();
            JsonArray restResourcesJson = networkJson.getJsonArray("restResources");
            if (restResourcesJson != null) {
                restResourcesJson.forEach(value -> {
                    jerseyServer.addResource(((JsonString)value).getString());
                });
            }
            JsonObject trapManagerJson = networkJson.getJsonObject("trapManager");
            if (trapManagerJson != null) {
                String managerAddress = trapManagerJson.getString("ipAddress", "127.0.0.1");
                int managerPort = trapManagerJson.getInt("port", 162);
                String trap = trapManagerJson.getString("trap", null);
                trapManager = new TrapManager(managerAddress, managerPort, trap);
            } else {
                trapManager = new TrapManager("127.0.0.1", 162, null);
            }
            JsonArray elementsJson = networkJson.getJsonArray("elements");
            if (elements == null) {
                LOG.warn("No network elements found");
            }
            FileWatcher fileWatcher = new FileWatcher(elements);
            elementsJson.forEach(value -> {
                JsonObject elementJson = value.asJsonObject();
                String ipAddress = elementJson.getString("ipAddress", null);
                if (ipAddress == null) {
                    LOG.warn("Missing ipAddress in " + netwworkJsonFile);
                } else {
                    int port = elementJson.getInt("port", 161);
                    JsonObject communityJson = elementJson.getJsonObject("community");
                    String readCommunity = null;
                    String writeCommunity = null;
                    if (communityJson != null) {
                        readCommunity = communityJson.getString("read", "public");
                        writeCommunity = communityJson.getString("write", "private");
                    } else {
                      // backwards compatibility
                      readCommunity = elementJson.getString("readCommunity", "public");
                      writeCommunity = elementJson.getString("writeCommunity", "private");
                    }
                    // SNMPv3
                    JsonObject authorityJson = elementJson.getJsonObject("authority");
                    Authority authority = null;
                    if (authorityJson != null) {
                        if (authorityJson.get("securityName") == null) {
                            LOG.error("Missing securityName for " + ipAddress);
                            return;
                        }
                        String securityLevel = authorityJson.get("securityLevel") == null ? null : authorityJson.getString("securityLevel");
                        String authPassphrase = authorityJson.get("authPassphrase") == null ? null : authorityJson.getString("authPassphrase");
                        String privPassphrase = authorityJson.get("privPassphrase") == null ? null : authorityJson.getString("privPassphrase");
                        authority = new Authority(authorityJson.getString("securityName"), securityLevel, authPassphrase, privPassphrase);
                    }
                    String className = elementJson.getString("class", "com.nexog.mss.net.NetworkElement");
                    int http = elementJson.getInt("http", 0);
                    String version = elementJson.getString("version", null);
                    NetworkElement element = createElement(ipAddress, port, readCommunity, writeCommunity, className, http);
                    element.setVersion(version);
                    element.setAuthority(authority);
                    elements.put(ipAddress, element);
                    fileWatcher.checksum(ipAddress);
                }
            });
            fileWatcher.start();
        } catch (FileNotFoundException e) {
            LOG.warn("Missing file " + e.getMessage());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    LOG.warn("Error closing stream " + e.getMessage());
                }
            }
        }
        for (Iterator<NetworkElement> iterator = elements.values().iterator(); iterator.hasNext();) {
            NetworkElement element = iterator.next();
            if (MOUNT_VIRTUAL_IP != null) {
                String cmd = "ifconfig " + MOUNT_VIRTUAL_IP + ":" + element.getId() + " " + element.getIpAddress() + " netmask 255.255.0.0";
                LOG.info("Running " + cmd);
                try {
                    Process p = Runtime.getRuntime().exec(cmd);
                    p.waitFor();
                    Thread.sleep(500);
                } catch (IOException e) {
                    LOG.error(e.getMessage());
                    continue;
                } catch (InterruptedException e) {
                    LOG.error(e.getMessage());
                }
            }
            try {
                new SnmpAgent(element, trapManager).run();
                if (element.getHttp() > 0) {
                    new Thread(new HttpServer(element)).start();
                }
            } catch (Exception e) {
                LOG.error("Failed to start SNMP Agent for " + element.getIpAddress(), e);
            }
        }
        // REST APIs
        new Thread(jerseyServer).start();
    }

    public static void main(String[] args) {
        MainAgent.instance().start();
    }
}
