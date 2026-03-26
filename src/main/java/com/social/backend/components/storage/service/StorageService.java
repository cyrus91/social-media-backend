package com.social.backend.components.storage.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    String store(MultipartFile file, String directory);

    // Salva file raw (audio, video) senza forzare resource_type=image
    String storeRaw(MultipartFile file, String directory);

    void delete(String fileUrl);

    boolean exists(String fileUrl);

    String getFileUrl(String fileName, String directory);
}