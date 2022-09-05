package ro.happyhyppo.mss.internal;

import java.io.Serializable;
import java.util.Comparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OidComparator implements Comparator<String>, Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(OidComparator.class);

    public int compare(String o1, String o2) {
        try {
            String[] index1 = o1.replace('.', '_').split("_");
            String[] index2 = o2.replace('.', '_').split("_");
            int i = 0;
            for (i = 0; i < index1.length - 1 && i < index2.length - 1; i++) {
                if (Integer.parseInt(index1[i]) != Integer.parseInt(index2[i])) {
                    break;
                }
            }
            if (Integer.parseInt(index1[i]) > Integer.parseInt(index2[i])) {
                return 1;
            } else if (Integer.parseInt(index1[i]) < Integer.parseInt(index2[i])) {
                return -1;
            } else {
                if (o1.length() == o2.length()) {
                    return 0;
                } else if (o1.length() > o2.length()) {
                    return 1;
                } else {
                    return -1;
                }
            }
        } catch (NumberFormatException e) {
            LOG.error(e.getMessage());
            throw e;
        }
    }

}
