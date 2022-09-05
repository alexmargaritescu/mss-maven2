package ro.happyhyppo.mss.internal;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MVMap {

    private Map<Object, List<Object>> map = new HashMap<>();

    public void put(Object key, Object...all) {
        List<Object> values = map.get(key);
        if (values == null) {
            values = new LinkedList<>();
            map.put(key, values);
        }
        for (Object value : all) {
            values.add(value);
        }
    }

    public boolean put(Object key, int index, Object value) {
        List<Object> values = map.get(key);
        if (values == null) {
            return false;
        }
        if (index > values.size()) {
            return false;
        }
        values.set(index, value);
        return true;
    }

    public Object get(Object key, int index) {
        List<Object> values = map.get(key);
        if (values == null) {
            return null;
        }
        return values.get(index);
    }

}
