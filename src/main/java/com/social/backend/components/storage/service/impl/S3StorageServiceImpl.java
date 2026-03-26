package com.social.backend.components.storage.service.impl;

import com.social.backend.components.storage.service.StorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3StorageServiceImpl implements StorageService {

    // TODO: Implementare con AWS S3 SDK in futuro

    @Override
    public String store(MultipartFile file, String directory) {
        throw new UnsupportedOperationException("S3 Storage non ancora implementato");
    }

    @Override
    public String storeRaw(MultipartFile file, String directory) {
        throw new UnsupportedOperationException("S3 Storage non ancora implementato");
    }

    @Override
    public void delete(String fileUrl) {
        throw new UnsupportedOperationException("S3 Storage non ancora implementato");
    }

    @Override
    public boolean exists(String fileUrl) {
        throw new UnsupportedOperationException("S3 Storage non ancora implementato");
    }

    @Override
    public String getFileUrl(String fileName, String directory) {
        throw new UnsupportedOperationException("S3 Storage non ancora implementato");
    }
}