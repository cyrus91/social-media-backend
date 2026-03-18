package com.social.backend.components.storage.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    /**
     * Salva un file e restituisce il path/URL
     */
    String store(MultipartFile file, String directory);

    /**
     * Elimina un file
     */
    void delete(String fileUrl);

    /**
     * Verifica se un file esiste
     */
    boolean exists(String fileUrl);

    /**
     * Ottieni URL completo del file
     */
    String getFileUrl(String fileName, String directory);
}