package ro.happyhyppo.mss.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.management.snmp.SnmpCounter;
import com.sun.management.snmp.SnmpCounter64;
import com.sun.management.snmp.SnmpDataTypeEnums;
import com.sun.management.snmp.SnmpGauge;
import com.sun.management.snmp.SnmpInt;
import com.sun.management.snmp.SnmpIpAddress;
import com.sun.management.snmp.SnmpOid;
import com.sun.management.snmp.SnmpStatusException;
import com.sun.management.snmp.SnmpString;
import com.sun.management.snmp.SnmpTimeticks;
import com.sun.management.snmp.SnmpUnsignedInt;
import com.sun.management.snmp.SnmpVarBind;

import java.io.UnsupportedEncodingException;

public class VarBindFactory {

    private static final Logger LOG = LoggerFactory.getLogger(VarBindFactory.class);

    public VarBind[] decodeVarBindList(Object data, int operation) {
        SnmpVarBind[] snmpVarBindList = (SnmpVarBind[]) data;
        VarBind[] varBindList = new VarBind[snmpVarBindList.length];
        for (int i = 0; i < snmpVarBindList.length; i++) {
            int type = getType(snmpVarBindList[i]);
            String value = null;
            if (type == 4) {
                byte[] bytes = snmpVarBindList[i].getSnmpStringValue().byteValue();
                try {
                    value = new String(bytes, "ISO-8859-1");
                } catch (UnsupportedEncodingException e) {
                    LOG.error("Decoding exception on " + snmpVarBindList[i]);
                }
            }
            if (value == null) {
                value = snmpVarBindList[i].getStringValue();
            }
            varBindList[i] = new VarBind(snmpVarBindList[i].getOid().toString(), type, value);
        }
        return varBindList;
    }

    public Object encodeVarBindList(VarBind[] varBindList) {
        SnmpVarBind[] snmpVarBindList = new SnmpVarBind[varBindList.length];
        for (int i = 0; i < snmpVarBindList.length; i++) {
            try {
                snmpVarBindList[i] = new SnmpVarBind(varBindList[i].getInstance());
            } catch (SnmpStatusException e) {
                // shall we do more here?
                LOG.error(e.getMessage(), e);
            }
            setValue(snmpVarBindList[i], varBindList[i].getType(), varBindList[i].getValue());
        }
        return snmpVarBindList;
    }

    private int getType(SnmpVarBind snmpVarBind) {
        if (snmpVarBind.getSnmpValue() instanceof SnmpString) {
            return SnmpDataTypeEnums.OctetStringTag;
        } else if (snmpVarBind.getSnmpValue() instanceof SnmpIpAddress) {
            return SnmpDataTypeEnums.IpAddressTag;
        } else if (snmpVarBind.getSnmpValue() instanceof SnmpGauge) {
            return SnmpDataTypeEnums.GaugeTag;
        } else if (snmpVarBind.getSnmpValue() instanceof SnmpCounter) {
            return SnmpDataTypeEnums.CounterTag;
        } else if (snmpVarBind.getSnmpValue() instanceof SnmpCounter64) {
            return SnmpDataTypeEnums.Counter64Tag;
        } else if (snmpVarBind.getSnmpValue() instanceof SnmpTimeticks) {
            return SnmpDataTypeEnums.TimeticksTag;
        } else if (snmpVarBind.getSnmpValue() instanceof SnmpOid) {
            return SnmpDataTypeEnums.ObjectIdentifierTag;
        } else if (snmpVarBind.getSnmpValue() instanceof SnmpCounter) {
            return SnmpDataTypeEnums.CounterTag;
        } else if (snmpVarBind.getSnmpValue() instanceof SnmpUnsignedInt) {
            return SnmpDataTypeEnums.UintegerTag;
        } else if (snmpVarBind.getSnmpValue() instanceof SnmpInt) {
            return SnmpDataTypeEnums.IntegerTag;
        } else {
            return -1;
        }
    }

    private void setValue(SnmpVarBind snmpVarBind, int type, String value) {
        switch (type) {
        case SnmpDataTypeEnums.IntegerTag:
        case SnmpDataTypeEnums.UintegerTag:
            snmpVarBind.setSnmpIntValue(Long.parseLong(value));
            break;
        case SnmpDataTypeEnums.OctetStringTag:
            try {
                if (value == null) {
                    LOG.error(snmpVarBind.toString());
                }
                snmpVarBind.setSnmpValue(new SnmpString(value.getBytes("ISO-8859-1")));
            } catch (UnsupportedEncodingException e) {
                LOG.error("Encoding exception on " + value);
                snmpVarBind.setSnmpStringValue(value);
            }
            break;
        case SnmpDataTypeEnums.IpAddressTag:
            snmpVarBind.setSnmpIpAddressValue(value);
            break;
        case SnmpDataTypeEnums.GaugeTag:
            snmpVarBind.setSnmpGaugeValue(Long.parseLong(value));
            break;
        case SnmpDataTypeEnums.CounterTag:
            snmpVarBind.setSnmpCounterValue(Long.parseLong(value));
            break;
        case SnmpDataTypeEnums.Counter64Tag:
            snmpVarBind.setSnmpCounter64Value(Long.parseLong(value));
            break;
        case SnmpDataTypeEnums.TimeticksTag:
            snmpVarBind.setSnmpTimeticksValue(Long.parseLong(value));
            break;
        case SnmpDataTypeEnums.ObjectIdentifierTag:
            snmpVarBind.setSnmpOidValue(value);
            break;
        case SnmpVarBind.errNoSuchObjectTag:
            snmpVarBind.setSnmpValue(SnmpVarBind.noSuchObject);
            break;
        case SnmpVarBind.errNoSuchInstanceTag:
            snmpVarBind.setSnmpValue(SnmpVarBind.noSuchInstance);
            break;
        case SnmpVarBind.errEndOfMibViewTag:
            snmpVarBind.setSnmpValue(SnmpVarBind.endOfMibView);
            break;
        default:
            snmpVarBind.setSnmpValue(SnmpVarBind.noSuchObject);
        }
    }
}
