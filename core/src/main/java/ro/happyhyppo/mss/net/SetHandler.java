package ro.happyhyppo.mss.net;

import java.util.Map;

public interface SetHandler {

    Object get(String instance, String value);

    Map<String, String> set(Map<String, String> values);

    boolean setValue(String parentKeys, String lastKey, Object node, Object value, Object newValue);
}
