package ro.happyhyppo.mss.internal;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ro.happyhyppo.mss.MainAgent;
import ro.happyhyppo.mss.net.SetHandler;

public class JsonHandler {

    private static final Logger LOG = LoggerFactory.getLogger(JsonHandler.class);

    private Map<String, Object> configJson;

    private SetHandler setHandler;

    private ObjectMapper mapper = new ObjectMapper();

    private String ipAddress;

    public JsonHandler(Map<String, Object> configJson, SetHandler setHandler) {
        this.configJson = configJson;
        this.setHandler = setHandler;
    }

    public void saveConfiguration(String ipAddress) {
        synchronized (configJson) {
            if (configJson.isEmpty()) {
                return;
            }
            File file = new File(MainAgent.USER_APP_DIR + ipAddress + ".json");
            try {
                mapper.writeValue(file, configJson);
                LOG.info("Configuration saved for " + ipAddress);
            } catch (IOException e) {
                LOG.error("Failed to save configuration for " + ipAddress + ": " + e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void loadConfiguration(String ipAddress) {
        this.ipAddress = ipAddress; // a bit of a hack
        File file = new File(MainAgent.USER_APP_DIR + ipAddress + ".json");
        if (!file.exists()) {
            return;
        }
        try {
            Map<String, Object> config = (Map<String, Object>)mapper.readValue(file, Object.class);
            // we need to merge the saved config with the model which can change with upgrades
            configJson.keySet().forEach(k -> mergeConfig(configJson, k, configJson.get(k), config.get(k)));
            //configJson.putAll((Map<String, Object>)mapper.readValue(file, Object.class));
            LOG.info("Configuration was loaded from " + file);
        } catch (IOException e) {
            LOG.error("Failed to load configuration for " + ipAddress + ": " + e.getMessage());
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void mergeConfig(Object parent, Object key, Object model, Object config) {
        // parent is model's parent
        // key is the parent map key or list index
        // model is the json coming with the library
        // config is the current saved (and loaded) configuration
        if (config == null) {
            // something new in model
            LOG.info("New attribute added in model: " + key);
        } else {
            if (model instanceof Map) {
                if (!(config instanceof Map)) {
                    // radical change of the model, override saved config
                    LOG.warn("Change in model: " + model.getClass() + " vs. " + config.getClass() + " for key " + key);
                } else {
                    Map m = (Map) model;
                    Map c = (Map) config;
                    // continue iterations
                    m.keySet().forEach(k -> mergeConfig(m, k, m.get(k), c.get(k)));
                }
            } else if (model instanceof List) {
                if (!(config instanceof List)) {
                    // radical change of the model, override saved config
                    LOG.warn("Change in model: " + model.getClass() + " vs. " + config.getClass() + " for key " + key);
                } else {
                    List m = (List) model;
                    List c = (List) config;
                    if (m.size() != c.size()) {
                        // change of the model, override saved config
                        if (m.size() >= c.size()) {
                            // more elements added in the model, merge the previous ones
                            ListIterator<Object> iterator = c.listIterator();
                            while (iterator.hasNext()) {
                                int index = iterator.nextIndex();
                                mergeConfig(m.get(index), index, iterator.next(), c);
                            }
                        } else {
                            // model has lost some elements, merge the remaining ones
                            ListIterator<Object> iterator = m.listIterator();
                            while (iterator.hasNext()) {
                                int index = iterator.nextIndex();
                                mergeConfig(m, index, iterator.next(), c.get(index));
                            }
                        }
                    } else {
                        ListIterator<Object> iterator = m.listIterator();
                        while (iterator.hasNext()) {
                            int index = iterator.nextIndex();
                            mergeConfig(m, index, iterator.next(), c.get(index));
                        }
                    }
                }
            } else {
                // simple type
                if (model.getClass() != config.getClass()) {
                    LOG.warn("Change in model: " + model.getClass() + " vs. " + config.getClass() + " for key " + key);
                    // change of the model, keep the model type and attempt to convert saved type
                    if (config instanceof Map || config instanceof List) {
                        // radical change of the model, dismiss saved config
                    } else if (model instanceof Boolean) {
                        updateParent(parent, key, Boolean.parseBoolean(config.toString()));
                    } else if (model instanceof Integer) {
                        updateParent(parent, key, Integer.parseInt(config.toString()));
                    } else if (model instanceof Long) {
                        updateParent(parent, key, Long.parseLong(config.toString()));
                    } else if (model instanceof Double) {
                        updateParent(parent, key, Double.parseDouble(config.toString()));
                    } else if (model instanceof Float) {
                        updateParent(parent, key, Float.parseFloat(config.toString()));
                    } else if (model instanceof String) {
                        updateParent(parent, key, config.toString());
                    } else {
                        LOG.error("Invalid model type: " + model.getClass());
                    }
                } else {
                    // same type, keep the saved value
                    updateParent(parent, key, config);
                }
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void updateParent(Object parent, Object key, Object config) {
        if (parent instanceof Map) {
            ((Map)parent).put(key, config);
        } else if (parent instanceof List) {
            ((List)parent).set((Integer)key, config);
        } else {
            // should not happen
            LOG.error("Invalid collection type: " + parent.getClass());
        }
    }

    public Map<String, Object> getSubTrees(List<String> paths) {
        Map<String, Object> subTrees = new LinkedHashMap<>();
        for (String path : paths) {
            try {
                subTrees.put(path, getSubTree(path));
            } catch (JsonProcessingException e) {
                LOG.error(e.getMessage());
                subTrees.put(path, e.getMessage());
            }
        }
        return subTrees;
    }

    private Object getSubTree(String path) throws JsonProcessingException {
        String[] keys = path.replace(".", "_").split("_");
        Object subTree = mapper.readValue(mapper.writeValueAsString(configJson), Object.class);
        Object node = subTree;
        for (String key : keys) {
            if (node == null) {
                break;
            }
            node = getNode(key, node);
        }
        return node;
    }

    public Object getSubTree(Object tree, String path) throws JsonProcessingException {
        String[] keys = path.replace(".", "_").split("_");
        Object node = mapper.readValue(mapper.writeValueAsString(tree), Object.class);
        for (String key : keys) {
            if (node == null) {
                break;
            }
            node = getNode(key, node);
        }
        return node;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Object getNode(String key, Object node) {
        if (node instanceof Map) {
            Map<String, Object> map = (Map) node;
            for (Iterator<String> iterator = map.keySet().iterator(); iterator.hasNext();) {
                if (!iterator.next().equals(key)) {
                    iterator.remove();
                }
            }
            return map.get(key);
        } else if (node instanceof List) {
            try {
                List<Object> list = (List) node;
                Object keep = null;
                for (int j = 0; j < list.size(); j++) {
                    if (j == Integer.parseInt(key)) {
                        keep = list.get(j);
                    }
                }
                list.clear();
                if (keep != null) {
                    list.add(keep);
                    return list.get(0);
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Object getFullSubTree(String path) throws JsonProcessingException {
        String[] keys = path.replace(".", "_").split("_");
        Object node = configJson;
        for (String key : keys) {
            if (node == null) {
                break;
            }
            node = getFullNode(key, node);
        }
        return node;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Object getFullNode(String key, Object node) {
        if (node instanceof Map) {
            Map<String, Object> map = (Map) node;
            return map.get(key);
        } else if (node instanceof List) {
            try {
                List<Object> list = (List) node;
                for (int j = 0; j < list.size(); j++) {
                    if (j == Integer.parseInt(key)) {
                        return list.get(j);
                    }
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Map<String, Object> setSubTrees(Map<String, Object> values) {
        Map<String, Object> subTrees = new LinkedHashMap<>();
        boolean saveNeeded = false;
        for (String path : values.keySet()) {
            try {
                String lastKey = path.substring(path.lastIndexOf('.') + 1);
                String parentKeys = path.substring(0, path.lastIndexOf('.'));
                Object node = getFullSubTree(parentKeys);
                if (node instanceof Map) {
                    Map map = (Map) node;
                    Object value = map.get(lastKey);
                    if (value == null) {
                        subTrees.put(path, null);
                        break;
                    }
                    String json = mapper.writeValueAsString(values.get(path));
                    Object newValue = mapper.readValue(json, Object.class);
                    // try to match the class/type
                    if (value instanceof Boolean) {
                        if (newValue.toString().equals("true") || newValue.toString().equals("false")) {
                            newValue = Boolean.parseBoolean(newValue.toString());
                        } else {
                            subTrees.put(path, "Wrong value " + newValue + ", expected true or false");
                            break;
                        }
                    } if (value instanceof Integer) {
                        try {
                            newValue = Integer.parseInt(newValue.toString());
                        } catch (Exception e) {
                            subTrees.put(path, "Wrong value " + newValue + ", expected number");
                            break;
                        }
                    } else {
                        if (!newValue.getClass().equals(value.getClass())) {
                            subTrees.put(path, "Wrong value, expected " + value.getClass());
                            break;
                        }
                    }
                    saveNeeded = saveNeeded || setHandler.setValue(parentKeys, lastKey, node, value, newValue);
                    map.put(lastKey, newValue);
                }
                subTrees.put(path, getSubTree(path));
                if (saveNeeded) {
                    saveConfiguration(ipAddress);
                }
            } catch (JsonProcessingException e) {
                LOG.error(e.getMessage());
                subTrees.put(path, e.getMessage());
                break;
            }
        }
        return subTrees;
    }

    @SuppressWarnings({ "unchecked" })
    public Map<String, Object> pathify(Object json, LinkedList<String> path, Map<String, Object> result) {
        if (json instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) json;
            map.keySet().forEach(k -> {
                path.addLast(k);
                pathify(map.get(k), path, result);
            });
        } else if (json instanceof List) {
            List<?> list = (List<?>) json;
            int i = 0;
            for (Object o : list) {
                path.addLast("" + (i++));
                pathify(o, path, result);
            }
        } else {
            StringBuilder sb = new StringBuilder();
            for (String p : path) {
                sb.append(p);
                sb.append(".");
            }
            if (sb.length() > 0) {
                sb.deleteCharAt(sb.length() - 1);
            }
            result.put(sb.toString(), json);
        }
        if (!path.isEmpty()) {
            path.removeLast();
        }
        return result;
    }

}
