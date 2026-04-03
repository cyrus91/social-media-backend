package com.social.backend.components.storage.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.social.backend.components.storage.exception.FileStorageException;
import com.social.backend.components.storage.exception.InvalidFileException;
import com.social.backend.components.storage.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "cloudinary")
public class CloudinaryStorageServiceImpl implements StorageService {

    private final Cloudinary cloudinary;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "gif", "webp");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    public CloudinaryStorageServiceImpl(
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}") String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret) {

        System.out.println("☁️ Inizializzazione Cloudinary Storage");
        System.out.println("📦 Cloud Name: " + cloudName);

        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));

        System.out.println("✅ Cloudinary configurato correttamente!");
    }

    @Override
    public String store(MultipartFile file, String directory) {
        System.out.println("📤 Upload su Cloudinary - Directory: " + directory);
        System.out.println("📦 File size: " + file.getSize() + " bytes");
        System.out.println("📦 File name: " + file.getOriginalFilename());

        validateFile(file);

        String fileName = UUID.randomUUID().toString();
        String publicId = directory + "/" + fileName;

        System.out.println("🔑 Public ID: " + publicId);

        try {
            System.out.println("⏳ Upload in corso su Cloudinary...");
            System.out.println("🔑 Cloud Name: " + cloudinary.config.cloudName);

            // ✅ NON usare "folder" - il path è già nel publicId!
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(),
                    ObjectUtils.asMap(
                            "public_id", publicId,
                            "resource_type", "image"
                    ));

            String resultPublicId = (String) uploadResult.get("public_id");
            String secureUrl = (String) uploadResult.get("secure_url");

            System.out.println("✅ Upload completato!");
            System.out.println("📋 Public ID: " + resultPublicId);
            System.out.println("🔗 URL: " + secureUrl);

            return secureUrl;

        } catch (IOException e) {
            System.err.println("❌ ERRORE UPLOAD CLOUDINARY!");
            System.err.println("❌ Message: " + e.getMessage());
            e.printStackTrace();
            throw new FileStorageException("Errore durante l'upload su Cloudinary: " + e.getMessage(), e);
        } catch (Exception e) {
            System.err.println("❌ ERRORE GENERICO!");
            System.err.println("❌ Type: " + e.getClass().getName());
            System.err.println("❌ Message: " + e.getMessage());
            e.printStackTrace();
            throw new FileStorageException("Errore imprevisto: " + e.getMessage(), e);
        }
    }

    @Override
    public String storeRaw(MultipartFile file, String directory) {
        try {
            String fileName = UUID.randomUUID().toString();
            String publicId = directory + "/" + fileName;
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(),
                    ObjectUtils.asMap(
                            "public_id", publicId,
                            "resource_type", "raw"  // audio/video/file generico
                    ));
            return (String) uploadResult.get("secure_url");
        } catch (IOException e) {
            throw new FileStorageException("Errore upload audio: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String publicId) {
        try {
            System.out.println("🗑️ Eliminazione da Cloudinary: " + publicId);
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            System.out.println("✅ File eliminato da Cloudinary");
        } catch (IOException e) {
            System.err.println("❌ Errore eliminazione Cloudinary: " + e.getMessage());
            throw new FileStorageException("Errore durante l'eliminazione da Cloudinary: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String publicId) {
        try {
            Map result = cloudinary.api().resource(publicId, ObjectUtils.emptyMap());
            return result != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getFileUrl(String publicId, String directory) {
        // ✅ URL SEMPLICE SENZA TRANSFORMATION
        String url = cloudinary.url()
                .secure(true)
                .generate(publicId);

        System.out.println("🔗 URL generato: " + url);
        return url;
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidFileException("File vuoto");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidFileException("File troppo grande. Massimo 5MB");
        }

        String extension = getFileExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new InvalidFileException("Tipo file non permesso. Consentiti: " + ALLOWED_EXTENSIONS);
        }

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
}