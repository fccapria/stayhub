package com.stayhub.backend.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class FileStorageService {

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "webp");
    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/webp"
    );

    @Value("${stayhub.upload.dir:uploads/rooms}")
    private String uploadDir;

    private Path rootLocation;

    @PostConstruct
    public void init() {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Impossibile inizializzare la cartella di archiviazione per le immagini", e);
        }
    }

    public String storeRoomImage(Long roomId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Il file caricato è vuoto.");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "image.jpg");

        // Prevenzione attacchi di Path Traversal
        if (originalFilename.contains("..")) {
            throw new IllegalArgumentException("Nome file non valido (contiene sequenze relative non consentite): " + originalFilename);
        }

        // Estrazione e validazione estensione
        String extension = getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Formato file non supportato. Formati ammessi: JPG, JPEG, PNG, WEBP.");
        }

        // Validazione Content-Type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Tipo MIME non valido: " + contentType + ". Formati ammessi: JPG, PNG, WEBP.");
        }

        // Rimuove eventuali immagini pregresse per questa camera per evitare accumulo su disco
        deleteOldImagesForRoom(roomId);

        // Generazione nome univoco: room_{roomId}_{uuid}.{ext}
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String targetFilename = "room_" + roomId + "_" + uniqueSuffix + "." + extension;
        Path destinationFile = this.rootLocation.resolve(targetFilename).normalize();

        if (!destinationFile.getParent().equals(this.rootLocation)) {
            throw new SecurityException("Impossibile salvare il file al di fuori della cartella designata.");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Errore durante il salvataggio fisico dell'immagine per la camera #" + roomId, e);
        }

        // Restituisce l'URL relativo pubblico dell'immagine
        return "/api/v1/rooms/" + roomId + "/image";
    }

    public Resource loadRoomImage(Long roomId) {
        try {
            // Cerca il file corrispondente al prefisso room_{roomId}_
            String prefix = "room_" + roomId + "_";
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(this.rootLocation, prefix + "*")) {
                for (Path entry : stream) {
                    Resource resource = new UrlResource(entry.toUri());
                    if (resource.exists() && resource.isReadable()) {
                        return resource;
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Errore nella lettura dell'immagine per la camera #" + roomId, e);
        }
        return null;
    }

    public void deleteOldImagesForRoom(Long roomId) {
        String prefix = "room_" + roomId + "_";
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(this.rootLocation, prefix + "*")) {
            for (Path entry : stream) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException ignored) {
        }
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) return "";
        return filename.substring(lastDot + 1);
    }
}
