package ro.happyhyppo.mss.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.happyhyppo.mss.net.NetworkElement;

public class SnmpAgent implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(SnmpAgent.class);

    private EngineImpl engine;

    private NetworkElement networkElement;

    public SnmpAgent(NetworkElement networkElement, TrapManager trapManager) throws Exception {
        this.networkElement = networkElement;
        engine = new EngineImpl(networkElement, trapManager);
        networkElement.setSnmpEngine(engine);
    }

    public void stop() {
        engine.shutdownAgent();
    }

    public void run() {
        LOG.info("Starting SNMP agent for " + networkElement.getIpAddress() + ":" + networkElement.getPort());
        engine.startAgent();
    }

}
