package ro.happyhyppo.mss.internal;

import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.management.comm.SnmpAdaptorServer;
import com.sun.management.comm.SnmpV3AdaptorServer;
import com.sun.management.internal.snmp.SnmpEngineImpl;
import com.sun.management.internal.snmp.SnmpSecuritySubSystem;
import com.sun.management.snmp.JdmkEngineFactory;
import com.sun.management.snmp.SnmpDefinitions;
import com.sun.management.snmp.SnmpEngineParameters;
import com.sun.management.snmp.SnmpMsg;
import com.sun.management.snmp.SnmpOid;
import com.sun.management.snmp.SnmpPdu;
import com.sun.management.snmp.SnmpPduBulk;
import com.sun.management.snmp.SnmpPduFactory;
import com.sun.management.snmp.SnmpPduFactoryBER;
import com.sun.management.snmp.SnmpPduRequest;
import com.sun.management.snmp.SnmpScopedPduRequest;
import com.sun.management.snmp.SnmpScopedPduBulk;
import com.sun.management.snmp.SnmpStatusException;
import com.sun.management.snmp.SnmpTooBigException;
import com.sun.management.snmp.SnmpV3Message;
import com.sun.management.snmp.SnmpVarBind;
import com.sun.management.snmp.SnmpVarBindList;
import com.sun.management.snmp.usm.SnmpUserSecurityModel;
import com.sun.management.snmp.usm.SnmpUsmLcd;

import ro.happyhyppo.mss.net.NetworkElement;

import com.sun.management.snmp.SnmpPduRequestType;

public class EngineImpl implements SnmpPduFactory {

    private static final Logger LOG = LoggerFactory.getLogger(SnmpPduFactory.class);

    private NetworkElement networkElement;

    private SnmpAdaptorServer snmpAdaptor;

    private SnmpPduFactory berFactory;

    private Map<Integer, SnmpPdu> reqMap = Collections.synchronizedMap(new HashMap<>());

    private VarBindFactory varBindFactory;

    private TrapManager trapManager;

    private Authority authority;

    EngineImpl(NetworkElement networkElement, TrapManager trapManager) throws Exception {
        this.networkElement = networkElement;
        int port = networkElement.getPort();
        InetAddress addr = InetAddress.getByName(networkElement.getIpAddress());
        authority = networkElement.getAuthority();
        if (authority == null) {
            // v1 and v2c
            snmpAdaptor = new SnmpAdaptorServer(false, port, addr);
        } else {
            // v3
            SnmpEngineParameters parameters = new SnmpEngineParameters();
            parameters.activateEncryption();
            snmpAdaptor = new SnmpV3AdaptorServer(parameters, new JdmkEngineFactory(), false, port, addr);
            SnmpEngineImpl snmpEngine = (SnmpEngineImpl)((SnmpV3AdaptorServer)snmpAdaptor).getEngine();
            SnmpSecuritySubSystem securitySubSystem = snmpEngine.getSecuritySubSystem();
            SnmpUserSecurityModel model = (SnmpUserSecurityModel)securitySubSystem.getModel(3);
            SnmpUsmLcd lcd = model.getLcd();
            int securityLevel = getSecurityLevel();
            if (securityLevel == SnmpDefinitions.authNoPriv) {
                if (authority.getAuthPassphrase() == null) {
                    LOG.error("Missing auth passphrase");
                    authority.setInvalid();
                }
            } else if (securityLevel == SnmpDefinitions.authPriv) {
                if (authority.getAuthPassphrase() == null) {
                    LOG.error("Missing auth passphrase");
                    authority.setInvalid();
                }
                if (authority.getPrivPassphrase() == null) {
                    LOG.error("Missing priv passphrase");
                    authority.setInvalid();
                }
            }
            lcd.addUser(snmpEngine.getEngineId(), authority.getSecurityName(), authority.getSecurityName(),
                    "usmHMACMD5AuthProtocol", authority.getAuthPassphrase(), "usmDESPrivProtocol",
                    authority.getPrivPassphrase(), securityLevel, false);
        }
        berFactory = new SnmpPduFactoryBER();
        snmpAdaptor.setPduFactory(this);
        snmpAdaptor.setBufferSize(4096);
        snmpAdaptor.setMaxActiveClientCount(1);
        varBindFactory = new VarBindFactory();
        this.trapManager = trapManager;
    }

    private int getSecurityLevel() {
        int securityLevel = SnmpDefinitions.authPriv;
        if ("noAuthNoPriv".equals(authority.getSecurityLevel())) {
            securityLevel = SnmpDefinitions.noAuthNoPriv;
        } else if ("authNoPriv".equals(authority.getSecurityLevel())) {
            securityLevel = SnmpDefinitions.authNoPriv;
        } else if ("authPriv".equals(authority.getSecurityLevel())) {
            securityLevel = SnmpDefinitions.authPriv;
        } else {
            LOG.error("Invalid security level: " + authority.getSecurityLevel() + " using authPriv");
        }
        return securityLevel;
    }

    public void shutdownAgent() {
        if (snmpAdaptor.isActive()) {
            while (snmpAdaptor.getActiveClientCount() > 0) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // don't care
                }
            }
            snmpAdaptor.stop();
        }
    }

    public void startAgent() {
        if (!snmpAdaptor.isActive()) {
            if (authority == null || authority.isValid()) {
                snmpAdaptor.start();
                if ("cold".equalsIgnoreCase(trapManager.getBootTrap())) {
                    sendColdStartTrap();
                }
            } else {
                LOG.error("SNMP engine not started!");
            }
        }
    }

    private SnmpPdu getCachedSnmpPdu(SnmpPdu pdu) {
        SnmpPdu myPdu;
        myPdu = reqMap.remove(pdu.requestId);
        if (myPdu != null) {
            return myPdu;
        } else {
            return pdu;
        }
    }

    /**
     * This method is called when a PDU is sent.
     */
    @Override
    public SnmpMsg encodeSnmpPdu(SnmpPdu pdu, int maxPktSize) throws SnmpStatusException, SnmpTooBigException {
        if (authority != null && pdu.version != 3) {
            // do not allow other version than v3
            throw new SnmpStatusException(SnmpStatusException.noAccess);
        }
        if (!(pdu.type == 164 || pdu.type == 166 || pdu.type == 167)) {
            Thread.currentThread().setName(networkElement.getIpAddress());
        }
        return berFactory.encodeSnmpPdu(getCachedSnmpPdu(pdu), maxPktSize);
    }

    /**
     * This method is called when a PDU is received.
     */
    @Override
    public SnmpPdu decodeSnmpPdu(SnmpMsg msg) throws SnmpStatusException {
        Thread.currentThread().setName(networkElement.getIpAddress());
        if (msg.version == 3) {
            SnmpV3Message v3msg = (SnmpV3Message)msg;
            if (v3msg.contextEngineId == null || v3msg.contextEngineId.length == 0) {
                // autodiscovery
                return null;
            }
        }
        return this.decodeSnmpPdu(berFactory.decodeSnmpPdu(msg));
    }

    private SnmpPdu decodeSnmpPdu(SnmpPdu pdu) throws SnmpStatusException {
        VarBind[] varBindListIn = varBindFactory.decodeVarBindList(pdu.varBindList, pdu.type);
        VarBind[] varBindListOut = new VarBind[0];
        if (pdu.version == 3) {
            //TODO do we do anything here?
        } else {
            byte[] comm = (pdu instanceof SnmpPduBulk) ? ((SnmpPduBulk) pdu).community
                    : ((SnmpPduRequest) pdu).community;
            String commmunity = new String(comm);
            if (pdu.type == 160 || pdu.type == 161 || pdu.type == 166) {
                if (!networkElement.getCommunity().canRead(commmunity)) {
                    throw new SnmpStatusException(SnmpStatusException.noAccess);
                }
            } else if (pdu.type == 163) {
                if (!networkElement.getCommunity().canWrite(commmunity)) {
                    throw new SnmpStatusException(SnmpStatusException.noAccess);
                }
            }
        }
        SnmpPdu myPdu = null;
        if (pdu.type == 160) { // get
            myPdu = getResponsePdu(pdu);
            varBindListOut = networkElement.getVarBindList(varBindListIn);
            for (int i = 0; i < varBindListOut.length; i++) {
                if (varBindListOut[i].isNoSuchInstance() && pdu.version == 0) {
                    varBindListOut = varBindListIn;
                    break;
                }
            }
        } else if (pdu.type == 161) { // get-next
            myPdu = getResponsePdu(pdu);
            varBindListOut = networkElement.getNextVarBindList(varBindListIn);
        } else if (pdu.type == 165) { // bulk
            myPdu = getResponsePdu(pdu);
            int maxRepetitions = pdu.version == 3 ? ((SnmpScopedPduBulk) pdu).getMaxRepetitions() : ((SnmpPduBulk) pdu).maxRepetitions;
            varBindListOut = networkElement.getBulkVarBindList(varBindListIn, maxRepetitions);
            pdu = getResponsePdu(pdu);
            pdu.type = 160; // to fool the agent (as we do not implement any
                            // MIB), we will insert our own PDU anyway
            pdu.varBindList = new SnmpVarBind[0];
        } else if (pdu.type == 163) { // set
            SnmpPduRequestType pduRequest = pdu.version == 3 ? (SnmpScopedPduRequest) pdu : (SnmpPduRequest) pdu;
            varBindListOut = networkElement.getVarBindList(varBindListIn);
            for (int i = 0; i < varBindListOut.length; i++) {
                if (varBindListOut[i].isNoSuchInstance() || varBindListOut[i].isReadOnly()) {
                    if (pdu.version == 0) {
                        pduRequest.setErrorStatus(SnmpDefinitions.snmpRspNoSuchName);
                    } else {
                        pduRequest.setErrorStatus(SnmpDefinitions.snmpRspNotWritable);
                    }
                    pduRequest.setErrorIndex(i);
                    return pdu;
                } else if (varBindListIn[i].getType() != varBindListOut[i].getType()) {
                    pduRequest.setErrorStatus(SnmpDefinitions.snmpRspWrongType);
                    pduRequest.setErrorIndex(i);
                    return pdu;
                }
            }
            myPdu = getResponsePdu(pdu);
            varBindListOut = networkElement.setVarBindList(varBindListIn);
            for (int i = 0; i < varBindListOut.length; i++) {
                if (varBindListOut[i].isError()) {
                    myPdu.varBindList = (SnmpVarBind[]) varBindFactory.encodeVarBindList(varBindListIn);
                    ((SnmpPduRequest)myPdu).setErrorStatus(SnmpDefinitions.snmpRspGenErr);
                    ((SnmpPduRequest)myPdu).setErrorIndex(i + 1);
                    reqMap.put(pdu.requestId, myPdu);
                    return pdu;
                }
            }
        }
        if (myPdu == null) {
            return pdu;
        }
        myPdu.varBindList = (SnmpVarBind[]) varBindFactory.encodeVarBindList(varBindListOut);
        reqMap.put(pdu.requestId, myPdu);
        return pdu;
    }

    private SnmpPdu getResponsePdu(SnmpPdu pdu) {
        if (pdu.version == 3) {
            if (pdu.type == 165) {
                return ((SnmpScopedPduBulk) pdu).getResponsePdu();
            } else {
                return ((SnmpScopedPduRequest) pdu).getResponsePdu();
            }
        } else {
            if (pdu.type == 165) {
                return ((SnmpPduBulk) pdu).getResponsePdu();
            } else {
                return ((SnmpPduRequest) pdu).getResponsePdu();
            }
        }
    }

    private void sendColdStartTrap() {
        try {
            InetAddress managerAddress = InetAddress.getByName(trapManager.getIpddress());
            snmpAdaptor.setTrapPort(trapManager.getPort());
            SnmpOid enterpriseOid = new SnmpOid("1.3.6.1.6.3.1.1.5.1");
            if (authority == null) {
                snmpAdaptor.snmpV2Trap(managerAddress, "public", enterpriseOid, new SnmpVarBindList());
            }
            else {
                ((SnmpV3AdaptorServer) snmpAdaptor).snmpV3UsmTrap(managerAddress, authority.getSecurityName(),
                        getSecurityLevel(), "", enterpriseOid, new SnmpVarBindList());
            }
            LOG.info("Cold trap sent from " + networkElement.getIpAddress() + " to " + trapManager);
        } catch (Exception e) {
            LOG.error("Error sending cold trap", e);
        }
    }

    public void sendTrap(String enterpriseOidString, VarBind[] varBinds) throws Exception {
        try {
            InetAddress managerAddress = InetAddress.getByName(trapManager.getIpddress());
            snmpAdaptor.setTrapPort(trapManager.getPort());
            SnmpOid enterpriseOid = new SnmpOid(enterpriseOidString);
            SnmpVarBind[] snmpVarBinds = (SnmpVarBind[])varBindFactory.encodeVarBindList(varBinds);
            SnmpVarBindList varBindList = new SnmpVarBindList();
            for (SnmpVarBind snmpVarBind : snmpVarBinds) {
                varBindList.addVarBind(snmpVarBind);
            }
            if (authority == null) {
                snmpAdaptor.snmpV2Trap(managerAddress, "public", enterpriseOid, varBindList);
            }
            else {
                ((SnmpV3AdaptorServer) snmpAdaptor).snmpV3UsmTrap(managerAddress, authority.getSecurityName(),
                        getSecurityLevel(), "", enterpriseOid, varBindList);
            }
            LOG.info("Trap " + enterpriseOidString + " sent from " + networkElement.getIpAddress() + " to " + trapManager);
        } catch (Exception e) {
            LOG.error("Error sending trap " + enterpriseOidString + " from " + networkElement.getIpAddress() + " to " + trapManager, e);
            throw e;
        }
    }

    public TrapManager getTrapManager() {
        return trapManager;
    }

}
