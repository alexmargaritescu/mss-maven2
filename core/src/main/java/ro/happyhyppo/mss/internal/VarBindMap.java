package ro.happyhyppo.mss.internal;

import java.util.Comparator;
import java.util.TreeMap;

@SuppressWarnings("serial")
public class VarBindMap<K, V> extends TreeMap<String, VarBind> {

    private long createTime;

    public VarBindMap(Comparator<String> comparator, long createTime) {
        super(comparator);
        this.createTime = createTime;
    }

    @Override
    public VarBind get(Object key) {
        VarBind vb = super.get(key);
        if (vb == null && key.equals("1.3.6.1.2.1.1.3.0")) {
            long upTime = (System.currentTimeMillis() - createTime) / 10;
            return new VarBind((String)key, 2, new Long(upTime).toString());
        }
        return super.get(key);
    }

}
