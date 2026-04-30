package com.yingshi.server.service.upload;

import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.config.StorageProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class LocalMediaStorageService {

    private final Path rootPath;

    public LocalMediaStorageService(StorageProperties storageProperties) {
        this.rootPath = Paths.get(storageProperties.localRoot()).toAbsolutePath().normalize();
    }

    public StoredFile store(String spaceId, String uploadId, String originalFileName, MultipartFile file) {
        String sanitizedName = sanitizeFileName(originalFileName);
        Path directory = rootPath.resolve(spaceId).resolve(uploadId);
        Path target = directory.resolve(sanitizedName).normalize();
        try {
            Files.createDirectories(directory);
            file.transferTo(target);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_STORAGE_ERROR, "Failed to store uploaded file.");
        }
        return new StoredFile(target.toString(), sanitizedName);
    }

    public Resource load(String storagePath) {
        Path path = Paths.get(storagePath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MEDIA_NOT_FOUND, "Stored media file was not found.");
        }
        return new FileSystemResource(path);
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public record StoredFile(String absolutePath, String storedFileName) {
    }
}
