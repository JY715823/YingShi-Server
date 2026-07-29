package com.yingshi.server.service.upload;

import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.repository.MediaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * R1-H-3: 上传配额校验服务。
 * 校验单文件大小、library 总存储量，防止滥用。
 */
@Service
public class UploadQuotaService {

    private final MediaRepository mediaRepository;

    @Value("${app.upload.quota.per-library-bytes:10737418240}") // 默认 10GB
    private long perLibraryBytesQuota;

    @Value("${app.upload.quota.per-month-count:5000}") // 默认 5000 条/月
    private long perMonthCountQuota;

    @Value("${app.upload.quota.max-image-bytes:104857600}") // 默认 100MB
    private long maxImageBytes;

    @Value("${app.upload.quota.max-video-bytes:524288000}") // 默认 500MB
    private long maxVideoBytes;

    public UploadQuotaService(MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    public void checkQuota(String libraryId, String mediaType, long fileSizeBytes) {
        // 单文件大小校验
        if ("IMAGE".equals(mediaType) && fileSizeBytes > maxImageBytes) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.UPLOAD_QUOTA_EXCEEDED,
                    "Image exceeds max size: " + fileSizeBytes + " > " + maxImageBytes);
        }
        if ("VIDEO".equals(mediaType) && fileSizeBytes > maxVideoBytes) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.UPLOAD_QUOTA_EXCEEDED,
                    "Video exceeds max size: " + fileSizeBytes + " > " + maxVideoBytes);
        }
        // library 总量校验
        long totalBytes = mediaRepository.sumStorageBytesByLibraryId(libraryId);
        if (totalBytes + fileSizeBytes > perLibraryBytesQuota) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.UPLOAD_QUOTA_EXCEEDED,
                    "Library storage quota exceeded");
        }
    }
}