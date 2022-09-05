package ro.happyhyppo.mss.internal;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmbeddedFtpClient {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedFtpClient.class);

    private FTPClient ftpClient = new FTPClient();

    private boolean saveToDisk = false;

    public static void main(String[] args) {
        byte[] data = new EmbeddedFtpClient().download("192.168.100.15", "drm", "drm",
                "1_20200819T222138Z.GPG");
        for (byte b : data) {
            System.out.println(b);
        }
    }

    public byte[] download(String address, String user, String password, String fileName) {
        byte[] data = null;
        try {
            ftpClient.connect(address);
            ftpClient.login(user, password);
            int reply = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftpClient.disconnect();
                return data;
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            LOG.info("Downloading " + fileName + " from " + address);
            ftpClient.enterLocalPassiveMode();
            ftpClient.setRemoteVerificationEnabled(false);
            ftpClient.retrieveFile(fileName, output);
            LOG.info(fileName + " downloaded");
            data = output.toByteArray();
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage());
        } finally {
            try {
                ftpClient.disconnect();
            } catch (Exception e) {
                LOG.error(e.getMessage());
            }
        }
        try {
            if (saveToDisk) {
                FileUtils.writeByteArrayToFile(new File(fileName), data);
            }
        } catch (IOException e) {
            LOG.warn("Cannot save file " + fileName + ": " + e.getMessage());
        }
        return data;
    }

}
