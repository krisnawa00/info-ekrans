package lv.talsi.dom;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;

public class SMBImageClient {

    private final String server;
    private final String share;
    private final String folder;
    private final String username;
    private final String password;
    private final String domain;

    private SMBClient client;
    private Connection connection;
    private Session session;
    private DiskShare diskShare;

    public SMBImageClient(Properties config) {
        this.server = config.getProperty("smb.server", "172.16.10.44");
        this.share = config.getProperty("smb.share", "ZShare");
        this.folder = config.getProperty("smb.folder", "Display");
        this.username = config.getProperty("smb.username", "");
        this.password = config.getProperty("smb.password", "");
        this.domain = config.getProperty("smb.domain", "");
    }

    public boolean connect() {
        try {
            client = new SMBClient();
            connection = client.connect(server);

            AuthenticationContext auth = username.isEmpty()
                    ? AuthenticationContext.anonymous()
                    : new AuthenticationContext(username, password.toCharArray(), domain);

            session = connection.authenticate(auth);
            diskShare = (DiskShare) session.connectShare(share);

            log("Savienojums ar SMB serveri izveidots: " + server);
            return true;

        } catch (Exception e) {
            log("Kļūda savienojumā ar SMB serveri: " + e.getMessage());
            return false;
        }
    }

    public List<SMBImageInfo> listImages() {
        List<SMBImageInfo> images = new ArrayList<>();

        if (diskShare == null) {
            log("Nav savienojuma ar SMB serveri");
            return images;
        }

        try {
            if (!diskShare.folderExists(folder)) {
                log("SMB mape neeksistē: " + folder);
                return images;
            }

            for (FileIdBothDirectoryInformation fileInfo : diskShare.list(folder)) {
                String fileName = fileInfo.getFileName();
                String lowerName = fileName.toLowerCase();

                if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                    lowerName.endsWith(".png") || lowerName.endsWith(".gif") ||
                    lowerName.endsWith(".bmp")) {

                    images.add(new SMBImageInfo(
                            fileName,
                            folder + "/" + fileName,
                            fileInfo.getChangeTime().toInstant().getEpochSecond(),
                            fileInfo.getEndOfFile()
                    ));
                }
            }

            log("Atrasti " + images.size() + " attēli SMB serverī");

        } catch (Exception e) {
            log("Kļūda nolasot SMB mapi: " + e.getMessage());
        }

        return images;
    }

    public InputStream downloadImage(String remotePath) {
        if (diskShare == null) {
            log("Nav savienojuma ar SMB serveri");
            return null;
        }

        try {
            File file = diskShare.openFile(
                    remotePath,
                    EnumSet.of(AccessMask.FILE_READ_DATA),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
            );

            return file.getInputStream();

        } catch (Exception e) {
            log("Kļūda lejupielādējot attēlu: " + remotePath + " - " + e.getMessage());
            return null;
        }
    }

    public void disconnect() {
        try {
            if (diskShare != null) diskShare.close();
            if (session != null) session.close();
            if (connection != null) connection.close();
            if (client != null) client.close();

            log("SMB savienojums aizvērts");

        } catch (Exception e) {
            log("Kļūda aizverot SMB savienojumu: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return connection != null && connection.isConnected() &&
               session != null && diskShare != null;
    }

    private void log(String message) {
        System.out.println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] SMB: " + message);
    }

    public static class SMBImageInfo {
        private final String fileName;
        private final String remotePath;
        private final long lastModified;
        private final long fileSize;

        public SMBImageInfo(String fileName, String remotePath, long lastModified, long fileSize) {
            this.fileName = fileName;
            this.remotePath = remotePath;
            this.lastModified = lastModified;
            this.fileSize = fileSize;
        }

        public String getFileName() { return fileName; }
        public String getRemotePath() { return remotePath; }
        public long getLastModified() { return lastModified; }
        public long getFileSize() { return fileSize; }
    }
}
