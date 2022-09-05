package ro.happyhyppo.mss.internal;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.happyhyppo.mss.MainAgent;
import ro.happyhyppo.mss.net.SetHandler;

public class VarBindRepository {

    private static final Logger LOG = LoggerFactory.getLogger(VarBindRepository.class);

    private Map<String, VarBind> varBinds;

    private Comparator<String> comparator = new OidComparator();

    private SetHandler handler;

    public VarBindRepository(SetHandler handler) {
        this(new OidComparator(), handler);
    }

    private VarBindRepository(Comparator<String> comparator, SetHandler handler) {
        this.comparator = comparator;
        this.varBinds = Collections.synchronizedMap(new VarBindMap<String, VarBind>(comparator, System.currentTimeMillis()));
        this.handler = handler;
    }

    public synchronized void add(VarBind varBind) {
        this.varBinds.put(varBind.getInstance(), varBind);
    }

    public synchronized void remove(String instance) {
        this.varBinds.remove(instance);
    }

    public synchronized void set(String instance, String value) {
        VarBind varBind = this.varBinds.get(instance);
        if (varBind != null) {
            varBind.setValue(value);
        }
    }

    public synchronized void add(Map<String, ? extends VarBind> varBinds) {
        this.varBinds.putAll(varBinds);
    }

    public synchronized VarBind get(String instance) {
        return this.varBinds.get(instance);
    }

    private synchronized void set(VarBind varBind) {
        if (this.varBinds.containsKey(varBind.getInstance())) {
            this.varBinds.put(varBind.getInstance(), varBind);
        }
    }

    public VarBind[] getVarBindList(VarBind[] inList) {
        VarBind[] outList = new VarBind[inList.length];
        for (int i = 0; i < outList.length; i++) {
            // first check for deep value first
            if (handler != null) {
                Object value = handler.get(inList[i].getInstance(), inList[i].getValue());
                if (value != null) {
                    VarBind varBind = new VarBind(inList[i]);
                    varBind.setValue(value.toString());
                    if (value instanceof Integer) {
                        varBind.setType(2);
                    } else if (value instanceof Long) {
                        varBind.setType(65);
                    }
                    varBinds.put(inList[i].getInstance(), varBind);
                }
            }
            VarBind varBind = varBinds.get(inList[i].getInstance());
            if (varBind != null) {
                outList[i] = varBind;
                LOG.debug("GET found: " + inList[i].getInstance() + "=" + outList[i].getValue());
            } else {
                outList[i] = new VarBind(inList[i]);
                outList[i].setNoSuchInstance();
                LOG.warn("GET not found: " + inList[i].getInstance());
            }
        }
        return outList;
    }

    public VarBind[] getNextVarBindList(VarBind[] inList) {
        return getNextVarBindList(inList, false);
    }

    private VarBind[] getNextVarBindList(VarBind[] inList, boolean forBulk) {
        Collection<VarBind> snapshot = copyValues();
        VarBind[] outList = new VarBind[inList.length];
        for (int i = 0; i < inList.length; i++) {
            boolean found = false;
            for (VarBind varBind : snapshot) {
                if (comparator.compare(varBind.getInstance(), inList[i].getInstance()) > 0) {
                    found = true;
                    // check for deep value first
                    if (handler != null) {
                        Object value = handler.get(varBind.getInstance(), varBind.getValue());
                        if (value != null) {
                            varBind.setValue(value.toString());
                        }
                    }
                    outList[i] = varBind;
                    LOG.debug("GET-NEXT found: " + inList[i].getInstance() + "=" + outList[i].getValue() + " on " + outList[i].getInstance());
                    break;
                }
            }
            if (!found) {
                outList[i] = new VarBind(inList[i]);
                outList[i].setEndOfMib();
                LOG.debug("GET-NEXT not found: " + inList[i].getInstance() + " end of MIB?");
            }
        }
        return outList;
    }

    public VarBind[] getBulkVarBindList(VarBind[] inList, int maxRepetitions) {
        Collection<VarBind> snapshot = copyValues();
        List<VarBind> outList = new ArrayList<>();
        int count = 0;
        for (VarBind varBind : inList) {
            List<VarBind> subtree = new ArrayList<>(snapshot);
            if (count == maxRepetitions * inList.length) {
                break;
            }
            VarBind nextVarBind = getNextVarBind(varBind, subtree);
            outList.add(nextVarBind);
            count++;
            while (!nextVarBind.isEndOfMib() && count != maxRepetitions * inList.length) {
                nextVarBind = getNextVarBindList(new VarBind[] { nextVarBind }, true)[0];
                outList.add(nextVarBind);
                count++;
            }
        }
        return outList.toArray(new VarBind[0]);
    }

    private VarBind getNextVarBind(VarBind in, List<VarBind> subtree) {
        VarBind out = null;
        for (VarBind varBind : subtree) {
            if (comparator.compare(varBind.getInstance(), in.getInstance()) > 0) {
                out = varBind;
                subtree.remove(varBind);
                break;
            }
        }
        if (out == null) {
            out = new VarBind(in);
            out.setEndOfMib();
        }
        return out;
    }

    public VarBind[] setVarBindList(VarBind[] inList) {
        Map<String, String> values = new LinkedHashMap<>();
        for(VarBind varBind : inList) {
            values.put(varBind.getInstance(), varBind.getValue());
        }
        if (handler != null) {
            values = handler.set(values);
        }
        VarBind[] outList = new VarBind[inList.length];
        Iterator<String> iterator = values.values().iterator();
        for (int i = 0; i < outList.length; i++) {
            outList[i] = new VarBind(inList[i]);
            String value = iterator.next();
            if (value != null) {
                outList[i].setValue(value);
                set(outList[i]);
            } else {
                outList[i].setError();
            }
        }
        return outList;
    }

    private synchronized List<VarBind> copyValues() {
        return new ArrayList<>(this.varBinds.values());
    }

    public Map<String, VarBind> get() {
        return varBinds;
    }

    public Set<String> set(Map<String, VarBind> configuration) {
        Set<String> varBindTypesChanged = new TreeSet<>();
        for (Iterator<String> iterator = configuration.keySet().iterator(); iterator.hasNext();) {
            String key = iterator.next();
            VarBind newVarBind = configuration.get(key);
            VarBind existingVarBind = varBinds.get(key);
            if (existingVarBind != null) {
                if (existingVarBind.getType() != newVarBind.getType()) {
                    // the type in the code is always the latest
                    newVarBind.setType(existingVarBind.getType());
                    varBindTypesChanged.add(key);
                }
            }
            varBinds.put(key, newVarBind);
        }
        return varBindTypesChanged;
    }

    public synchronized void deleteSubTree(String instance) {
        for (Iterator<String> iterator = varBinds.keySet().iterator(); iterator.hasNext();) {
            String key = iterator.next();
            if (key.startsWith(instance)) {
                iterator.remove();
            }
        }
    }

    public synchronized void deleteSubTree(String instance, String index) {
        for (Iterator<String> iterator = varBinds.keySet().iterator(); iterator.hasNext();) {
            String key = iterator.next();
            if (key.startsWith(instance) && key.endsWith(index)) {
                iterator.remove();
            }
        }
    }

    public Map<String, VarBind> getSubtree(String instance) {
        Map<String, VarBind> result = new HashMap<>();
        for (Iterator<String> iterator = varBinds.keySet().iterator(); iterator.hasNext();) {
            String key = iterator.next();
            if (key.startsWith(instance)) {
                result.put(key, varBinds.get(key));
            }
        }
        return result;
    }

    public void loadConfiguration(String ipAddress) {
        FileInputStream fis = null;
        ObjectInputStream ois = null;
        try {
            fis = new FileInputStream(MainAgent.USER_APP_DIR + ipAddress + ".ser");
            ois = new ObjectInputStream(fis);
            @SuppressWarnings("unchecked")
            Map<String, VarBind> configuration = (Map<String, VarBind>) ois.readObject();
            Set<String> varBindTypesChanged = new TreeSet<>();
            synchronized (this) {
                varBindTypesChanged = this.set(configuration);
            }
            if (!varBindTypesChanged.isEmpty()) {
                LOG.info(ipAddress + " has new types for " + varBindTypesChanged);
            }
            LOG.info("Configuration loaded for " + ipAddress);
        } catch (FileNotFoundException e) {
        } catch (Exception e) {
            LOG.error("Failed to load configuration for " + ipAddress + " :" + e.getMessage());
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                }
            }
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public synchronized void saveConfiguration(String ipAddress) {
        if (varBinds.isEmpty()) {
            return;
        }
        FileOutputStream fout = null;
        ObjectOutputStream oos = null;
        try {
            fout = new FileOutputStream(MainAgent.USER_APP_DIR + ipAddress + ".ser");
            oos = new ObjectOutputStream(fout);
            oos.writeObject(varBinds);
            LOG.info("Configuration saved for " + ipAddress);
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("Failed to save configuration for " + ipAddress + ": " + e.getMessage());
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException e) {
                }
            }
            if (fout != null) {
                try {
                    fout.close();
                } catch (IOException e) {
                }
            }
        }
    }

}
