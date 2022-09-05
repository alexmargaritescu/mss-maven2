package ro.happyhyppo.mss.internal;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.happyhyppo.mss.net.NetworkElement;

public class HttpServer implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(HttpServer.class);

    private NetworkElement networkElement;

    private ServerSocket serverSocket = null;

    private boolean running = false;

    public HttpServer(NetworkElement networkElement) {
        this.networkElement = networkElement;
    }

    public void run() {
        Thread.currentThread().setName(networkElement.getIpAddress());
        running = true;
        try {
            InetAddress address = InetAddress.getByName(networkElement.getIpAddress());
            serverSocket = new ServerSocket(80, 50, address);
            LOG.info("Starting HTTP server on " + address);
            while (running) {
                Socket socket = serverSocket.accept();
                process(socket);
            }
        } catch (Exception e) {
            LOG.error("Failed to start HTTP server", e);
        }
    }

    public void stop() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            LOG.warn("Error closing server socket");
        }
    }

    private byte[] loadThumbnail() {
        InputStream inputStream = null;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            inputStream = getClass().getClassLoader().getResourceAsStream("style.webp");
            if (inputStream == null) {
                throw new FileNotFoundException("not found");
            }
            byte[] buffer = new byte[4096];
            int length = 0;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            return outputStream.toByteArray();
        } catch (Exception e) {
            LOG.error("Could not load file " + e.getMessage());
            return new byte[0];
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private void process(Socket socket) {
        InputStream inputStream = null;
        DataInputStream dataInputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = socket.getInputStream();
            dataInputStream = new DataInputStream(inputStream);
            outputStream = socket.getOutputStream();
            Map<String, String> headers = processHeaders(dataInputStream);
            if (headers.get("GET") != null) {
                String response  = networkElement.process(headers);
                outputStream.write(response.getBytes());
                outputStream.write(networkElement.getBinaryData(headers));
                return;
            }
            int contentLength = headers.get("content-length") != null ? Integer.parseInt(headers.get("content-length")) : 0;
            if (contentLength != 0) {
                if (headers.get("content-type") != null && headers.get("content-type").contains("multipart/form-data")) {
                    // TODO what if there are more sections between boudaries?
                    Map<String, String> innerHeaders = processHeaders(dataInputStream);
                    int innerContentLength = innerHeaders.get("content-length") != null ? Integer.parseInt(innerHeaders.get("content-length")) : 0;
                    if (innerContentLength != 0) {
                        byte[] content = processContent(dataInputStream, innerContentLength);
                        String response  = networkElement.process(headers, content);
                        outputStream.write(response.getBytes());
                    }
                } else {
                    byte[] content = processContent(dataInputStream, contentLength);
                    String response  = networkElement.process(headers, content);
                    outputStream.write(response.getBytes());
                }
            } else {
                String response  = networkElement.process(headers);
                outputStream.write(response.getBytes());
            }
        } catch (Exception e) {
            LOG.error("Error processing input", e);
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                LOG.warn("Error closing output stream");
            }
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                LOG.warn("Error closing input stream");
            }
            try {
                if (dataInputStream != null) {
                    dataInputStream.close();
                }
            } catch (IOException e) {
                LOG.warn("Error closing data input stream");
            }
            try {
                socket.close();
            } catch (IOException e) {
                LOG.warn("Error closing input stream");
            }
        }
    }

    private Map<String, String> processHeaders(BufferedReader reader) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        String line = reader.readLine();
        if (line == null) {
            return headers;
        }
        LOG.debug("HTTP Request " + line);
        String[] request = line.split("\\s");
        headers.put(request[0], decode(request[1].trim()));
        while (!line.isEmpty()) {
            line = reader.readLine();
            String[] fields = line.trim().split(":");
            if (fields.length != 2) {
                continue;
            }
            headers.put(fields[0].trim().toLowerCase(), decode(fields[1].trim()));
        }
        return headers;
    }

    private Map<String, String> processHeaders(DataInputStream dataInputStream) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        ByteArrayOutputStream temp = new ByteArrayOutputStream();
        int b;
        boolean firstLine = true;
        while ((b = dataInputStream.read()) != -1) {
            if (b == 10) {
                // new line
                String line = new String(temp.toByteArray()).trim();
                if (line.isEmpty()) {
                    break;
                }
                // first line
                if (firstLine) {
                    firstLine = false;
                    String[] request = line.split("\\s");
                    if (request.length >= 2) {
                        LOG.debug("HTTP Request " + line);
                        headers.put(request[0], decode(request[1].trim()));
                    } else {
                        LOG.info("Unknown Request " + line);
                    }
                } else {
                    String[] fields = line.trim().split(":");
                    if (fields.length == 2) {
                        headers.put(fields[0].trim().toLowerCase(), decode(fields[1].trim()));
                    }
                }
                temp.reset();
            } else {
                temp.write(b);
            }
        }
        return headers;
    }

    private String processContent(BufferedReader reader, int contentLength) throws IOException {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < contentLength; i++) {
            char c = (char) reader.read();
            content.append(c);
        }
        return content.toString();
    }

    private byte[] processContent(DataInputStream dataInputStream, int contentLength) throws IOException {
        byte[] content = new byte[contentLength];
        dataInputStream.readFully(content, 0, contentLength);
        return content;
    }

    private String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            LOG.error(e.getMessage());
            return null;
        }
    }

    public static Charset getCharset() {
        String charsetName = System.getProperty("mss.http.charset");
        if (charsetName == null) {
            return StandardCharsets.ISO_8859_1;
        }
        try {
            return Charset.forName(charsetName);
        } catch (Exception e) {
            LOG.warn("Cannot instantiate charset " + charsetName);
            return null;
        }
    }

}
