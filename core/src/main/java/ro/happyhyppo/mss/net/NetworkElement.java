package ro.happyhyppo.mss.net;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import ro.happyhyppo.mss.MainAgent;
import ro.happyhyppo.mss.internal.Authority;
import ro.happyhyppo.mss.internal.Community;
import ro.happyhyppo.mss.internal.EngineImpl;
import ro.happyhyppo.mss.internal.JsonHandler;
import ro.happyhyppo.mss.internal.TrapManager;
import ro.happyhyppo.mss.internal.VarBind;
import ro.happyhyppo.mss.internal.VarBindRepository;

public class NetworkElement implements SetHandler, WebHandler {

    private static final Logger LOG = LoggerFactory.getLogger(NetworkElement.class);

    private EngineImpl snmpEngine;

    protected static final boolean READ_ONLY = true;

    protected static final boolean READ_WRITE = false;

    public static final byte TYPE_BIT_STRING = 3;

    public static final byte TYPE_OCTET_STRING = 4;

    public static final byte TYPE_INTEGER = 2;

    public static final byte TYPE_OBJECT_ID = 6;

    public static final byte TYPE_IP_ADDRESS = 64;

    public static final byte TYPE_COUNTER = 65;

    public static final byte TYPE_UNSIGNED_32 = 66;

    private int id;

    private String ipAddress;

    private int port;
    private byte[] macAddress;

    private String serialNumber;

    private Community community;

    private Authority authority;

    private VarBindRepository varBinds;

    private Pattern pattern = Pattern.compile("\\((r|w)\\)(.*)\\[(.+)\\](\\s*=\\s*)\\{(s|i|a|x|o|u)\\}(.*)");

    private static Map<String, Byte> typeMap = new HashMap<>();

    private int http = 0;

    private String version;

    protected static final String sysDescr = "1.3.6.1.2.1.1.1";
    protected static final String sysObjectID = "1.3.6.1.2.1.1.2";
    protected static final String sysName = "1.3.6.1.2.1.1.5";

    protected static final String nexogSystemEmulatorTaskDelay = "1.3.6.1.4.1.57535.999.1";
    protected static final String nexogSystemEmulatorPersistency = "1.3.6.1.4.1.57535.999.2";

    protected SimpleDateFormat format;

    protected Map<String, Object> configJson = new LinkedHashMap<>();

    protected final JsonHandler jsonHandler = new JsonHandler(configJson, this);

    static {
        typeMap.put("i", TYPE_INTEGER);
        typeMap.put("s", TYPE_OCTET_STRING);
        typeMap.put("o", TYPE_OBJECT_ID);
        typeMap.put("a", TYPE_IP_ADDRESS);
        typeMap.put("u", TYPE_UNSIGNED_32);
    }

    private NetworkElement(int id, String ipAddress, int port) {
        this.id = id;
        this.ipAddress = ipAddress;
        this.port = port;
        serialNumber = generateSerialNumber();
        macAddress = getMacAddress();
        format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        community = new Community();
        varBinds = new VarBindRepository(this);
        addOid("1.3.6.1.2.1.1.4", "0", "Alex M", TYPE_OCTET_STRING, false);
        addOid(sysName, "0", ipAddress, TYPE_OCTET_STRING, false);
        addOid("1.3.6.1.2.1.1.6", "0", "Tellence", TYPE_OCTET_STRING, false);
        init();
        loadConfiguration();
        load();
        postInit();
    }

    protected void postInit() {
    }

    public NetworkElement(int id, String ipAddress, int port, String readCommunity, String writeCommunity) {
        this(id, ipAddress, port);
        setCommunities(readCommunity, writeCommunity);
    }

    public void setSnmpEngine(EngineImpl snmpEngine) {
    	this.snmpEngine = snmpEngine;
    }

    public Authority getAuthority() {
        return authority;
    }

    public void setAuthority(Authority authority) {
        this.authority = authority;
    }

    public void load() {
        FileReader fileReader = null;
        BufferedReader bufferedReader = null;
        try {
            File file = new File(MainAgent.USER_APP_DIR + ipAddress + ".varbinds");
            fileReader = new FileReader(file);
            bufferedReader = new BufferedReader(fileReader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                Matcher matcher = pattern.matcher(line);
                if (!matcher.find()) {
                    LOG.warn("Error parsing line " + line + " in " + file);
                    continue;
                }
                String rw = matcher.group(1);
                String oid = matcher.group(2);
                String index = matcher.group(3);
                String type = matcher.group(5);
                String value = matcher.group(6);
                if (value.equals("{now}")) {
                    value = new String(makeDateAndTime((GregorianCalendar)GregorianCalendar.getInstance()));
                }
                addOid(oid, index, value, typeMap.get(type), rw.equals("r"));
            }
            LOG.info("Configuration was initiated from " + file);
        } catch (FileNotFoundException e) {
        } catch (IOException e) {
            LOG.warn("Failed to read file " + e.getMessage());
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    LOG.warn("Error closing buffer " + e.getMessage());
                }
            }
            if (fileReader != null) {
                try {
                    fileReader.close();
                } catch (IOException e) {
                    LOG.warn("Error closing reader " + e.getMessage());
                }
            }
        }
    }

    public void loadAndInit() {
        load();
        postInit();
    }

    private void loadConfiguration() {
        varBinds.loadConfiguration(ipAddress);
        jsonHandler.loadConfiguration(ipAddress);
    }

    protected void init() {
    }

    private void setCommunities(String readOnly, String readWrite) {
        community.setReadOnly(readOnly);
        community.setReadWrite(readWrite);
    }

//    private void addOid(String oid, String value, boolean readOnly) {
//        String index = value.length() + ".";
//        for (int i = 0; i < value.length(); i++) {
//            index += (byte) value.charAt(i);
//            if (i < value.length() - 1) {
//                index += ".";
//            }
//        }
//        varBinds.add(oid + "." + index, new VarBind(oid, index, value, (byte) 4, readOnly));
//    }

    protected void setDescription(String description) {
        addOid(sysDescr, "0", description, TYPE_OCTET_STRING, READ_ONLY);
    }

    protected void setObjectID(String objectID) {
        addOid(sysObjectID, "0", objectID, TYPE_OBJECT_ID, READ_ONLY);
    }

    protected void addOid(String oid, Object value) {
        varBinds.add(new VarBind(oid, value));
    }

    protected void addOid(String oid, Object value, boolean readOnly) {
        varBinds.add(new VarBind(oid, value, readOnly));
    }

    protected void addOid(String oid, String index, Object value, int type, boolean readOnly) {
        varBinds.add(new VarBind(oid, index, value, (byte) type, readOnly));
    }

    protected void setOid(String instance, String value) {
        varBinds.set(instance, value);
    }

    protected void removeOid(String instance) {
        varBinds.remove(instance);
    }

    public VarBind[] getVarBindList(final VarBind[] inList) {
        return varBinds.getVarBindList(inList);
    }

    public VarBind[] getNextVarBindList(final VarBind[] inList) {
        return varBinds.getNextVarBindList(inList);
    }

    public VarBind[] getBulkVarBindList(VarBind[] inList, int maxRepetitions) {
        return varBinds.getBulkVarBindList(inList, maxRepetitions);
    }

    public VarBind[] setVarBindList(final VarBind[] inList) {
        return varBinds.setVarBindList(inList);
    }

    protected byte[] getMacAddress() {
        StringTokenizer tokenizer = new StringTokenizer(ipAddress, ".");
        List<String> elements = new ArrayList<>();
        while (tokenizer.hasMoreTokens()) {
            elements.add(tokenizer.nextToken());
        }
        int ip1 = Integer.parseInt(elements.get(0));
        int ip2 = Integer.parseInt(elements.get(1));
        int ip3 = Integer.parseInt(elements.get(2));
        int ip4 = Integer.parseInt(elements.get(3));
        return new byte[] { 0x00, (byte) 0xe0, (byte) ip1, (byte) ip2, (byte) ip3, (byte) ip4 };
    }

    protected String getMacAddressString(char separator) {
        return getMacAddressString(ipAddress, separator);
    }

    protected String getMacAddressString(String ipAddress, char separator) {
        StringTokenizer tokenizer = new StringTokenizer(ipAddress, ".");
        List<String> elements = new ArrayList<>();
        while (tokenizer.hasMoreTokens()) {
            elements.add(tokenizer.nextToken());
        }
        String ip1 = String.format("%02X", Integer.parseInt(elements.get(0)));
        String ip2 = String.format("%02X", Integer.parseInt(elements.get(1)));
        String ip3 = String.format("%02X", Integer.parseInt(elements.get(2)));
        String ip4 = String.format("%02X", Integer.parseInt(elements.get(3)));
        return "00" + separator + "E0" + separator + ip1 + separator + ip2 + separator + ip3 + separator + ip4;
    }

    protected String getMacAddressString() {
        return getMacAddressString(ipAddress);
    }

    protected String getMacAddressString(String ipAddress) {
        StringTokenizer tokenizer = new StringTokenizer(ipAddress, ".");
        List<String> elements = new ArrayList<>();
        while (tokenizer.hasMoreTokens()) {
            elements.add(tokenizer.nextToken());
        }
        String ip1 = String.format("%02X", Integer.parseInt(elements.get(0)));
        String ip2 = String.format("%02X", Integer.parseInt(elements.get(1)));
        String ip3 = String.format("%02X", Integer.parseInt(elements.get(2)));
        String ip4 = String.format("%02X", Integer.parseInt(elements.get(3)));
        return "00" + "E0" + ip1 + ip2 + ip3 + ip4;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    protected byte[] getIpAddressAsHex() {
        try {
            return InetAddress.getByName(ipAddress).getAddress();
        } catch (UnknownHostException e) {
            LOG.error("Error parsing IP address " + ipAddress);
            return new byte[] {0x0, 0x0, 0x0, 0x0};
        }
    }

    public int getPort() {
        return port;
    }

    public Community getCommunity() {
        return community;
    }

    private String generateSerialNumber() {
        String classAndId = getClass().getSimpleName() + id;
        return Base64.getEncoder().withoutPadding().encodeToString(classAndId.getBytes());
    }

    protected String getSerialNumber() {
        return serialNumber;
    }

    protected int getSerialNumerAsInt() {
        return getClass().hashCode() + id;
    }

    @Override
    public Object get(String instance, String value) {
        return null;
    }

    @Override
    public Map<String, String> set(Map<String, String> values) {
        // to be overriden by sub-classes
        return values;
    }

    private byte[] makeDateAndTime(GregorianCalendar dateAndTime) {
        List<Byte> byteList = new ArrayList<>();
        byteList.add((byte)(dateAndTime.get(Calendar.YEAR)/256));
        byteList.add((byte)(dateAndTime.get(Calendar.YEAR)%256));
        byteList.add((byte)(dateAndTime.get(Calendar.MONTH)+1));
        byteList.add((byte)(dateAndTime.get(Calendar.DAY_OF_MONTH)));
        byteList.add((byte)(dateAndTime.get(Calendar.HOUR_OF_DAY)));
        byteList.add((byte)(dateAndTime.get(Calendar.MINUTE)));
        byteList.add((byte)(dateAndTime.get(Calendar.SECOND)));
        byteList.add((byte)(dateAndTime.get(Calendar.MILLISECOND)/100));
        byte[] bytes = new byte[byteList.size()];
        int i = 0;
        for(Byte b: byteList) {
            bytes[i++] = b;
        }
        return bytes;
    }

    protected String toDateAndTimeHexFormat(Calendar cal) {
        String DELIMITATOR = ":";
        StringBuilder buff = new StringBuilder();
        buff.append(Integer.toHexString(cal.get(Calendar.YEAR))).insert(1, DELIMITATOR).append(DELIMITATOR);
        buff.append(Integer.toHexString(cal.get(Calendar.MONTH) + 1)).append(DELIMITATOR);
        buff.append(Integer.toHexString(cal.get(Calendar.DAY_OF_MONTH))).append(DELIMITATOR);
        buff.append(Integer.toHexString(cal.get(Calendar.HOUR_OF_DAY))).append(DELIMITATOR);
        buff.append(Integer.toHexString(cal.get(Calendar.MINUTE))).append(DELIMITATOR);
        buff.append(Integer.toHexString(cal.get(Calendar.SECOND))).append(DELIMITATOR);
        buff.append(Integer.toHexString(0));
        return buff.toString();
    }

    protected String getIndex(String instance, String oid) {
        return instance.substring(oid.length() + 1);
    }

    protected String getValue(String instance) {
        VarBind varBind = varBinds.get(instance);
        if (varBind == null) {
            return null;
        }
        return varBind.getValue();
    }

    protected Map<String, String> setValues(Map<String, String> values, boolean error) {
        if (!error) {
            return values;
        }
        for (String instance : values.keySet()) {
            values.put(instance, null);
        }
        return values;
    }

    protected String getRequest(byte[] input) {
        String[] lines = new String(input).split("\r\n");
        if (lines.length == 0) {
            return null;
        } else {
            return lines[0];
        }
    }

    protected Map<String, String> getHeaders(byte[] input) {
        HashMap<String, String> headers = new LinkedHashMap<>();
        String[] lines = new String(input).split("\r\n");
        for (String line: lines) {
            if (line.startsWith("GET")) {
                continue;
            }
            String[] fields = line.trim().split(":");
            if (fields.length != 2) {
                continue;
            }
            headers.put(fields[0], fields[1]);
        }
        return headers;
    }

    public int getHttp() {
        return http;
    }

    public void setHttp(int http) {
        this.http = http;
    }

    @Override
    public String process(Map<String, String> headers) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("HTTP/1.1 200 OK\r\n");
        stringBuilder.append("Date: " + format.format(new Date()) + " GMT" + "\r\n");
        stringBuilder.append("Content-Length: 0\r\n");
        stringBuilder.append("\r\n");
        return stringBuilder.toString();
    }

    @Override
    public String process(Map<String, String> headers, String content) {
        return process(headers);
    }

    @Override
    public String process(Map<String, String> headers, byte[] data) {
        return process(headers);
    }

    @Override
    public byte[] getBinaryData(Map<String, String> headers) {
        return new byte[0];
    }

    protected Integer getStaticIntegerValue(String oid) {
        String value = getValue(oid + ".0");
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            return null;
        }
        return Integer.parseInt(value);
    }

    public int getId() {
        return id;
    }

    public void sendTrap(String oid, String index, Map<String, Object> objects) throws Exception {
        List<VarBind> varBindList = objects.entrySet().stream().map(e -> new VarBind(e.getKey(), index, e.getValue())).collect(Collectors.toList());
        snmpEngine.sendTrap(oid, varBindList.toArray(new VarBind[0]));
    }

    public TrapManager getTrapManager() {
        return snmpEngine.getTrapManager();
    }

    protected void saveConfiguration() {
        String persist = getValue(nexogSystemEmulatorPersistency + "." + 0);
        if (persist == null || persist.equals("1")) {
            varBinds.saveConfiguration(ipAddress);
            jsonHandler.saveConfiguration(ipAddress);
        } else {
            LOG.warn("Persistent configuration is disabled!");
        }
    }

    protected void saveConfiguration(long delay) {
        Thread t = new Thread() {
            public void run() {
                setName(getIpAddress());
                try {
                    sleep(delay);
                } catch (InterruptedException e) {
                }
                saveConfiguration();
            }
        };
        t.start();
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Map<String, String> getOids(List<String> instances) {
        Map<String, String> values = new TreeMap<>();
        for (Iterator<String> iterator = varBinds.get().keySet().iterator(); iterator.hasNext();) {
            String key = iterator.next();
            for (String instance : instances) {
                if (key.startsWith(instance)) {
                    values.put(key, varBinds.get(key).getValue());
                }
            }
        }
//        instances.forEach(i -> {
//            VarBind varBind = null;
//            try {
//                varBind = varBinds.get(i);
//            } catch (NumberFormatException e) {
//            }
//            if (varBind != null) {
//                values.put(i, varBinds.get(i).getValue());
//            } else {
//                values.put(i, null);
//            }
//        });
        return values;
    }

    public Map<String, String> setOids(Map<String, String> values) {
        Thread.currentThread().setName(getIpAddress());
        Map<String, String> result = new TreeMap<>();
        synchronized (varBinds) {
            this.set(values);
            values.forEach((i, v) -> {
                VarBind varBind = null;
                try {
                    varBind = varBinds.get(i);
                } catch (NumberFormatException e) {
                }
                if (varBind == null) {
                    result.put(i, null);
                } else {
                    varBind.setValue(v);
                    result.put(i, v);
                }
            });
        }
        return result;
    }

    public void deleteTable(String instance) {
        varBinds.deleteSubTree(instance);
    }

    public void deleteEntry(String instance, String index) {
        varBinds.deleteSubTree(instance, index);
    }

    protected Map<String, Map<String, String>> getTable(String entry, int indexCount) {
        Map<String, VarBind> subTree = varBinds.getSubtree(entry);
        Map<String, Map<String, String>> result = new HashMap<>();
        for (Iterator<String> iterator = subTree.keySet().iterator(); iterator.hasNext();) {
            String key = iterator.next();
            String instance = key;
            for (int i = 0; i < indexCount; i++) {
                instance = key.substring(0, instance.lastIndexOf('.'));
            }
            String index = key.substring(instance.length() + 1);
            VarBind varBind = subTree.get(key);
            Map<String, String> map = result.get(index);
            if (map == null) {
                map = new HashMap<>();
                result.put(index, map);
            }
            map.put(instance, varBind.getValue());
        }
        return result;
    }

    protected DelayedTask createSaveTask() {
        return new DelayedTask() {
            @Override
            public void run() {
                saveConfiguration();
            }
        };
    }

    protected class DelayedTask extends TimerTask {

        private static final long DEFAULT_DELAY = 2000L;

        private Long delay;

        protected DelayedTask() {
            this.delay = DEFAULT_DELAY;
        }

        protected DelayedTask(Long delay) {
            this.delay = delay == null? DEFAULT_DELAY : delay;
        }

        protected DelayedTask(String oid) {
            String delay = getValue(nexogSystemEmulatorTaskDelay + "." + oid);
            if (delay == null) {
                this.delay = DEFAULT_DELAY;
            } else {
                try {
                    this.delay = Long.parseLong(delay);
                } catch (NumberFormatException e) {
                    LOG.warn("Cannot parse delay value " + delay);
                    this.delay = DEFAULT_DELAY;
                }
            }
        }

        public Long getDelay() {
            return delay;
        }

        @Override
        public void run() {
        }

    }

    protected String buildResponse(int code, String reason) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("HTTP/1.1 " + code + " " + reason + "\r\n");
        stringBuilder.append("Date: " + format.format(new Date()) + " GMT" + "\r\n");
        stringBuilder.append("Content-Length: 0\r\n");
        stringBuilder.append("\r\n");
        return stringBuilder.toString();
    }

    public Map<String, Object> getSubTrees(List<String> paths) {
        return jsonHandler.getSubTrees(paths);
    }

    public Map<String, Object> setSubTrees(Map<String, Object> values) {
        return jsonHandler.setSubTrees(values);
    }

    @Override
    public boolean setValue(String parentKeys, String lastKey, Object node, Object value, Object newValue) {
        return false;
    }

    protected Map<String, Object> pathify(Object json, LinkedList<String> path, Map<String, Object> result) {
        return jsonHandler.pathify(json, path, result);
    }

    protected Object getSubTree(Object tree, String path) {
        try {
            return jsonHandler.getSubTree(tree, path);
        } catch (JsonProcessingException e) {
            LOG.error(e.getMessage());
            return null;
        }
    }

    protected void reboot(long delay) {
        Thread t = new Thread() {
            public void run() {
                setName(getIpAddress());
                snmpEngine.shutdownAgent();
                LOG.info("Rebooting...");
                try {
                    sleep(delay);
                } catch (InterruptedException e) {
                }
                init();
                loadConfiguration();
                load();
                postInit();
                snmpEngine.startAgent();
            }
        };
        t.start();
    }

}
