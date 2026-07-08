package com.yingshi.server.service.chat;

import com.yingshi.server.domain.chat.ImportedResourceEntity;
import com.yingshi.server.repository.chat.ImportedResourceRepository;
import com.yingshi.server.service.storage.ObjectMetadata;
import com.yingshi.server.service.storage.ObjectStorageService;
import com.yingshi.server.service.storage.StoredObject;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.Optional;

@Service
public class ChatMediaService {

    private final ObjectStorageService storageService;
    private final ImportedResourceRepository resourceRepository;

    public ChatMediaService(ObjectStorageService storageService,
                            ImportedResourceRepository resourceRepository) {
        this.storageService = storageService;
        this.resourceRepository = resourceRepository;
    }

    /**
     * Upload a media file.
     * Key format: chat-imports/{libraryId}/{chatStableKey}/resources/{fileName}
     * If md5 is provided and a resource with same libraryId+md5 already exists with a storedObjectKey,
     * skip the upload and return the existing key (MD5 dedup).
     *
     * @return the stored object key
     */
    public String upload(String libraryId, String chatStableKey, String fileName,
                         String contentType, String md5, InputStream input, long sizeBytes) {
        if (StringUtils.hasText(md5)) {
            Optional<ImportedResourceEntity> existing =
                    resourceRepository.findFirstByLibraryIdAndMd5AndStoredObjectKeyIsNotNull(libraryId, md5);
            if (existing.isPresent()) {
                return existing.get().getStoredObjectKey();
            }
        }

        String objectKey = "chat-imports/" + libraryId + "/" + chatStableKey + "/resources/" + fileName;
        storageService.put(objectKey, contentType, sizeBytes, input);
        return objectKey;
    }

    /**
     * Download a media file by object key.
     *
     * @return the stored object
     */
    public StoredObject download(String objectKey) {
        return storageService.get(objectKey);
    }

    /**
     * Check if a media file exists.
     *
     * @return metadata if exists, empty otherwise
     */
    public Optional<ObjectMetadata> exists(String objectKey) {
        return storageService.getMetadata(objectKey);
    }
}
