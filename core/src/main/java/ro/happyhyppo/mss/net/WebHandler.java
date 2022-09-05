package ro.happyhyppo.mss.net;

import java.util.Map;

public interface WebHandler {

    String process(Map<String, String> headers);

    String process(Map<String, String> headers, String content);

    String process(Map<String, String> headers, byte[] data);

    byte[] getBinaryData(Map<String, String> headers);
}
