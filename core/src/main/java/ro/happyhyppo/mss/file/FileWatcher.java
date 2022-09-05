package ro.happyhyppo.mss.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.happyhyppo.mss.MainAgent;
import ro.happyhyppo.mss.net.NetworkElement;

public class FileWatcher extends Thread {

    private static final Logger LOG = LoggerFactory.getLogger(FileWatcher.class);

    private Map<String, NetworkElement> elements;

    private MessageDigest messageDigest;

    private int SLEEP = 10;

    private Map<String, Integer> checksums = new HashMap<>();

    private long lastChanged;

    public FileWatcher(Map<String, NetworkElement> elements) {
        try {
            SLEEP = Integer.parseInt(System.getProperty("mss.watch.interval", "10"));
        } catch (Exception e) {
            LOG.warn("Cannot use watch interval value from system properties: " + e.getMessage());
        }
        LOG.info("Using watch interval as " + SLEEP + " seconds");
        this.elements = elements;
        try {
            messageDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    public Integer checksum(String ipAddress) {
        if (messageDigest == null) {
            return null;
        }
        DigestInputStream dis = null;
        try {
            File file = new File(MainAgent.USER_APP_DIR + ipAddress + ".varbinds");
            if (file.exists()) {
                dis = new DigestInputStream(new FileInputStream(file), messageDigest);
                byte[] buffer = new byte[1024];
                int count;
                do {
                    count = dis.read(buffer);
                    if (count > 0) {
                        messageDigest.update(buffer, 0, count);
                    }
                } while (count != -1);
                int checksum = Arrays.hashCode(messageDigest.digest());
                checksums.put(ipAddress, checksum);
                return checksum;
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        } finally {
            if (dis != null) {
                try {
                    dis.close();
                } catch (IOException e) {
                }
            }
        }
        return null;
    }

    @Override
    public void run() {
        if (messageDigest == null) {
            return;
        }
        LOG.info("Starting file watcher...");
        File dir = new File(MainAgent.USER_APP_DIR);
        lastChanged = dir.lastModified();
        while (true) {
            try {
                Thread.sleep(SLEEP * 1000);
            } catch (InterruptedException e) {
            }
            long changed = dir.lastModified();
            if (changed != lastChanged) {
                LOG.info("Directory " + dir + " changed");
                lastChanged = changed;
                elements.forEach((k, v) -> {
                    File file = new File(MainAgent.USER_APP_DIR + k + ".varbinds");
                    if (file.exists()) {
                        if (checksums.get(k) == null) {
                            // new file
                            checksums.put(k, null);
                        }
                    }
                });
            }
            checksums.forEach((k, v) -> {
                Integer checksum = checksum(k);
                if (checksum != null && !checksum.equals(v)) {
                    LOG.info("Changes detected in " + k);
                    NetworkElement element = elements.get(k);
                    if (element != null) {
                        element.loadAndInit();
                    } else {
                        LOG.error("Network element " + k + " not found!");
                    }
                }
            });
        }
    }

    public void startWatchService() {
        Path path = Paths.get(MainAgent.USER_APP_DIR);
        WatchService watchService;
        try {
            watchService = path.getFileSystem().newWatchService();
            path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
            while (true) {
                WatchKey watchKey = null;
                try {
                    watchKey = watchService.take();
                    List<WatchEvent<?>> events = watchKey.pollEvents();
                    for (WatchEvent<?> event : events) {
                        if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                            String fileName = event.context().toString();
                            if (fileName.endsWith(".varbinds")) {
                                LOG.info("Changes detected in " + fileName);
                                // String ipAddress = fileName.substring(0,
                                // fileName.lastIndexOf('.'));
                                // NetworkElement networkElement =
                                // elements.get(ipAddress);
                                // networkElement.load();
                            } else {
                            }
                        }
                    }
                } catch (Exception e) {
                    LOG.error(e.getMessage(), e);
                } finally {
                    if (watchKey != null) {
                        watchKey.reset();
                    }
                    Thread.sleep(5000);
                }
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }

}
