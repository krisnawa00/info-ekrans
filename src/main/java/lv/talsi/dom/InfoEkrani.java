package lv.talsi.dom;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class InfoEkrani extends Application {
    
    private String localImagesFolder;
    private int slideshowInterval;
    private int fileCheckInterval;
    private String mode;
    
    private ImageView imageView;
    private Label timeLabel;
    private Label statusLabel;
    private List<ImageInfo> imageInfos = new ArrayList<>();
    private int currentImageIndex = 0;
    private Timeline slideshow;
    private ScheduledExecutorService fileChecker;
    private long lastModified = 0;
    
    private SMBImageClient smbClient;
    private Properties config;
    
    @Override
    public void start(Stage primaryStage) {
        loadConfig();
        setupUI(primaryStage);
        setupSMBClient();
        setupFileMonitoring();
        loadImages();
        startSlideshow();
        startClock();
    }
    
    private void loadConfig() {
        config = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                config.load(input);
            }
        } catch (IOException e) {
            // Izmantot noklusējuma vērtības
        }
        
        // Ielādēt no faila, ja eksistē
        File configFile = new File("config.properties");
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                config.load(fis);
            } catch (IOException e) {
                updateStatus("Kļūda ielādējot konfigurāciju: " + e.getMessage());
            }
        }
        
        localImagesFolder = config.getProperty("local.images.folder", "images");
        slideshowInterval = Integer.parseInt(config.getProperty("slideshow.interval", "10"));
        fileCheckInterval = Integer.parseInt(config.getProperty("file.check.interval", "30"));
        mode = config.getProperty("mode", "local");
        
        updateStatus("Režīms: " + mode);
    }
    
    private void setupSMBClient() {
        if ("smb".equals(mode) || "hybrid".equals(mode)) {
            smbClient = new SMBImageClient(config);
            // Mēģināt savienojumu fonā
            new Thread(() -> {
                if (smbClient.connect()) {
                    Platform.runLater(() -> updateStatus("SMB savienojums izveidots"));
                } else {
                    Platform.runLater(() -> updateStatus("SMB savienojums neizdevās"));
                }
            }).start();
        }
    }
    
    private void setupUI(Stage primaryStage) {
        // Izveidot UI elementus
        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        
        // Laika rādījums
        timeLabel = new Label();
        timeLabel.setTextFill(Color.WHITE);
        timeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        
        // Statusa rādījums
        statusLabel = new Label("Sistēma startē...");
        statusLabel.setTextFill(Color.YELLOW);
        statusLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 14));
        
        // Statusa panelis
        HBox statusBox = new HBox(20);
        statusBox.setAlignment(Pos.CENTER);
        statusBox.getChildren().addAll(timeLabel, statusLabel);
        statusBox.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7); -fx-padding: 10px;");
        
        // Galvenais layout
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: black;");
        root.getChildren().addAll(imageView, statusBox);
        StackPane.setAlignment(statusBox, Pos.TOP_CENTER);
        
        // Scene un Stage konfigurācija
        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("Talsu novada Info Ekrāns");
        primaryStage.setScene(scene);
        primaryStage.setFullScreen(true);
        primaryStage.setFullScreenExitHint("");
        primaryStage.show();
        
        // Pielāgot attēla izmēru logam
        imageView.fitWidthProperty().bind(scene.widthProperty());
        imageView.fitHeightProperty().bind(scene.heightProperty().subtract(80));
    }
    
    private void setupFileMonitoring() {
        // Izveidot lokālo images mapi, ja nepastāv
        File imagesDir = new File(localImagesFolder);
        if (!imagesDir.exists()) {
            imagesDir.mkdirs();
            updateStatus("Izveidota lokālā mape: " + localImagesFolder);
        }
        
        // Failu pārbaudes serviss
        fileChecker = Executors.newScheduledThreadPool(1);
        fileChecker.scheduleAtFixedRate(this::checkForNewFiles, 
                                       fileCheckInterval, 
                                       fileCheckInterval, 
                                       TimeUnit.SECONDS);
    }
    
    private void loadImages() {
        imageInfos.clear();
        
        // Ielādēt no SMB
        if ("smb".equals(mode) || "hybrid".equals(mode)) {
            loadSMBImages();
        }
        
        // Ielādēt lokālos attēlus
        if ("local".equals(mode) || "hybrid".equals(mode)) {
            loadLocalImages();
        }
        
        // Kārtot attēlus pēc nosaukuma
        imageInfos.sort(Comparator.comparing(ImageInfo::getFileName));
        
        updateStatus("Kopā ielādēti " + imageInfos.size() + " attēli");
    }
    
    private void loadSMBImages() {
        if (smbClient == null || !smbClient.isConnected()) {
            updateStatus("Nav SMB savienojuma");
            return;
        }
        
        try {
            List<SMBImageClient.SMBImageInfo> smbImages = smbClient.listImages();
            for (SMBImageClient.SMBImageInfo smbImage : smbImages) {
                imageInfos.add(new ImageInfo(
                    smbImage.getFileName(),
                    ImageInfo.Source.SMB,
                    smbImage.getRemotePath(),
                    smbImage.getLastModified()
                ));
            }
            updateStatus("Ielādēti " + smbImages.size() + " attēli no SMB");
        } catch (Exception e) {
            updateStatus("Kļūda ielādējot SMB attēlus: " + e.getMessage());
        }
    }
    
    private void loadLocalImages() {
        File imagesDir = new File(localImagesFolder);
        if (!imagesDir.exists()) {
            return;
        }
        
        File[] files = imagesDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || 
                   lower.endsWith(".png") || lower.endsWith(".gif") || 
                   lower.endsWith(".bmp");
        });
        
        if (files != null) {
            for (File file : files) {
                imageInfos.add(new ImageInfo(
                    file.getName(),
                    ImageInfo.Source.LOCAL,
                    file.getAbsolutePath(),
                    file.lastModified()
                ));
            }
            updateStatus("Ielādēti " + files.length + " lokālie attēli");
            lastModified = imagesDir.lastModified();
        }
    }
    
    private void checkForNewFiles() {
        // Pārbaudīt SMB savienojumu
        if (("smb".equals(mode) || "hybrid".equals(mode)) && smbClient != null) {
            if (!smbClient.isConnected()) {
                Platform.runLater(() -> updateStatus("Mēģina atjaunot SMB savienojumu..."));
                if (smbClient.connect()) {
                    Platform.runLater(() -> updateStatus("SMB savienojums atjaunots"));
                }
            }
        }
        
        // Pārbaudīt lokālos failus
        if ("local".equals(mode) || "hybrid".equals(mode)) {
            File imagesDir = new File(localImagesFolder);
            if (imagesDir.exists() && imagesDir.lastModified() > lastModified) {
                Platform.runLater(() -> {
                    updateStatus("Atrasti jauni lokālie attēli...");
                    loadImages();
                    if (!imageInfos.isEmpty()) {
                        restartSlideshow();
                    }
                });
            }
        }
        
        // Pārbaudīt SMB failus (vienmēr pārbaudīt, jo SMB lastModified ir sarežģīti)
        if (("smb".equals(mode) || "hybrid".equals(mode)) && smbClient != null && smbClient.isConnected()) {
            Platform.runLater(() -> {
                loadImages();
                if (!imageInfos.isEmpty()) {
                    restartSlideshow();
                }
            });
        }
    }
    
    private void startSlideshow() {
        if (slideshow != null) {
            slideshow.stop();
        }
        
        slideshow = new Timeline(new KeyFrame(Duration.seconds(slideshowInterval), e -> showNextImage()));
        slideshow.setCycleCount(Timeline.INDEFINITE);
        slideshow.play();
        
        // Rādīt pirmo attēlu uzreiz
        showNextImage();
    }
    
    private void restartSlideshow() {
        currentImageIndex = 0;
        startSlideshow();
    }
    
    private void showNextImage() {
        if (imageInfos.isEmpty()) {
            showPlaceholderImage();
            return;
        }
        
        ImageInfo imageInfo = imageInfos.get(currentImageIndex);
        
        try {
            Image image = loadImage(imageInfo);
            if (image != null) {
                imageView.setImage(image);
                
                currentImageIndex = (currentImageIndex + 1) % imageInfos.size();
                updateStatus("Rāda: " + imageInfo.getFileName() + " (" + 
                            imageInfo.getSource() + ") " +
                            "(" + (currentImageIndex == 0 ? imageInfos.size() : currentImageIndex) + 
                            "/" + imageInfos.size() + ")");
            } else {
                // Izņemt bojāto attēlu
                imageInfos.remove(currentImageIndex);
                if (currentImageIndex >= imageInfos.size()) {
                    currentImageIndex = 0;
                }
                updateStatus("Izlaists bojāts attēls: " + imageInfo.getFileName());
            }
            
        } catch (Exception e) {
            updateStatus("Kļūda rādot attēlu: " + e.getMessage());
            // Izņemt bojāto attēlu
            imageInfos.remove(currentImageIndex);
            if (currentImageIndex >= imageInfos.size()) {
                currentImageIndex = 0;
            }
        }
    }
    
    private Image loadImage(ImageInfo imageInfo) {
        try {
            if (imageInfo.getSource() == ImageInfo.Source.LOCAL) {
                return new Image(new FileInputStream(imageInfo.getPath()));
            } else if (imageInfo.getSource() == ImageInfo.Source.SMB) {
                InputStream inputStream = smbClient.downloadImage(imageInfo.getPath());
                if (inputStream != null) {
                    return new Image(inputStream);
                }
            }
        } catch (Exception e) {
            updateStatus("Kļūda ielādējot attēlu: " + imageInfo.getFileName() + " - " + e.getMessage());
        }
        return null;
    }
    
    private void showPlaceholderImage() {
        imageView.setImage(null);
        String message = "Nav attēlu";
        if ("smb".equals(mode)) {
            message += "\n\nSMB: " + config.getProperty("smb.server") + "/" + config.getProperty("smb.share") + "/" + config.getProperty("smb.folder");
        } else if ("local".equals(mode)) {
            message += "\n\nIevietojiet attēlus mapē: " + localImagesFolder;
        } else {
            message += "\n\nSMB un lokālā mapē: " + localImagesFolder;
        }
        updateStatus(message);
    }
    
    private void startClock() {
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateTime()));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
        updateTime();
    }
    
    private void updateTime() {
        LocalDateTime now = LocalDateTime.now();
        timeLabel.setText(now.format(DateTimeFormatter.ofPattern("HH:mm:ss  dd.MM.yyyy")));
    }
    
    private void updateStatus(String message) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            System.out.println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + message);
        });
    }
    
    @Override
    public void stop() {
        if (slideshow != null) {
            slideshow.stop();
        }
        if (fileChecker != null) {
            fileChecker.shutdown();
        }
        if (smbClient != null) {
            smbClient.disconnect();
        }
    }
    
    public static void main(String[] args) {
        launch(args);
    }
    
    // Palīgklase attēlu informācijas glabāšanai
    private static class ImageInfo {
        public enum Source { LOCAL, SMB }
        
        private final String fileName;
        private final Source source;
        private final String path;
        private final long lastModified;
        
        public ImageInfo(String fileName, Source source, String path, long lastModified) {
            this.fileName = fileName;
            this.source = source;
            this.path = path;
            this.lastModified = lastModified;
        }
        
        public String getFileName() { return fileName; }
        public Source getSource() { return source; }
        public String getPath() { return path; }
        public long getLastModified() { return lastModified; }
    }
}