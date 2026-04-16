package com.social.backend.components.storage.service.impl;

import com.social.backend.components.storage.exception.FileStorageException;
import com.social.backend.components.storage.exception.InvalidFileException;
import com.social.backend.components.storage.service.StorageService;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageServiceImpl implements StorageService {

    @Value("${storage.local.upload-dir}")
    private String uploadDir;

    @Value("${app.base-url}")
    private String baseUrl;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "gif");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    // Whitelist per file raw (audio/video)
    private static final List<String> ALLOWED_RAW_CONTENT_TYPES = Arrays.asList(
            "audio/webm", "audio/ogg", "audio/mp4", "audio/mpeg", "audio/wav",
            "video/mp4", "video/quicktime", "video/webm"
    );
    private static final List<String> ALLOWED_RAW_EXTENSIONS = Arrays.asList(
            "webm", "ogg", "mp4", "mov", "wav", "mp3"
    );
    private static final long MAX_RAW_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    @Override
    public String store(MultipartFile file, String directory) {
        // Validazione
        validateFile(file);

        // Ottieni nome file originale (null-safe)
        String originalFileName = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "file"
        );

        // Genera nome file unico
        String extension = getFileExtension(originalFileName);
        String fileName = UUID.randomUUID() + "." + extension;

        try {
            Path uploadPath = Paths.get(directory);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path filePath = uploadPath.resolve(fileName);

            if (isImage(extension)) {
                optimizeAndSaveImage(file, filePath.toFile());
            } else {
                Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            }

            return fileName;

        } catch (IOException e) {
            throw new FileStorageException("Errore durante il salvataggio del file: " + fileName, e);
        }
    }

    // Salva file raw (audio, video) senza ottimizzazione immagine
    @Override
    public String storeRaw(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("File vuoto");
        }

        if (file.getSize() > MAX_RAW_FILE_SIZE) {
            throw new InvalidFileException("File troppo grande. Massimo 50MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_RAW_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidFileException("Tipo file non permesso. Consentiti: audio e video (webm, ogg, mp4, mov, wav, mp3)");
        }

        String originalFileName = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "file"
        );

        String extension = getFileExtension(originalFileName);
        if (!ALLOWED_RAW_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new InvalidFileException("Estensione non permessa: " + extension);
        }

        String fileName = UUID.randomUUID() + "." + extension;

        try {
            Path uploadPath = Paths.get(directory);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            return fileName;

        } catch (IOException e) {
            throw new FileStorageException("Errore durante il salvataggio del file raw: " + fileName, e);
        }
    }

    @Override
    public void delete(String fileName) {
        try {
            Path filePath = Paths.get(uploadDir).resolve(fileName);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new FileStorageException("Errore durante l'eliminazione del file: " + fileName, e);
        }
    }

    @Override
    public boolean exists(String fileName) {
        Path filePath = Paths.get(uploadDir).resolve(fileName);
        return Files.exists(filePath);
    }

    @Override
    public String getFileUrl(String fileName, String directory) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/" + directory + "/" + fileName;
    }

    // ========== METODI PRIVATI ==========

    private void validateFile(MultipartFile file) {
        // Verifica che il file non sia vuoto
        if (file.isEmpty()) {
            throw new InvalidFileException("File vuoto");
        }

        // Verifica dimensione
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidFileException("File troppo grande. Massimo 5MB");
        }

        // Verifica estensione
        String extension = getFileExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new InvalidFileException("Tipo file non permesso. Consentiti: " + ALLOWED_EXTENSIONS);
        }

        // Verifica content type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidFileException("Il file deve essere un'immagine");
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            throw new InvalidFileException("Nome file non valido");
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    private boolean isImage(String extension) {
        return Arrays.asList("jpg", "jpeg", "png", "gif").contains(extension.toLowerCase());
    }

    private void optimizeAndSaveImage(MultipartFile file, File destination) throws IOException {
        // Ridimensiona se troppo grande (max 1200px)
        Thumbnails.of(file.getInputStream())
                .size(1200, 1200)
                .outputQuality(0.85)
                .toFile(destination);
    }
}