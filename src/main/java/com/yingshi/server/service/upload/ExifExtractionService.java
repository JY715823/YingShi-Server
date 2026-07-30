package com.yingshi.server.service.upload;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 从上传的图片文件中提取EXIF拍摄参数（曝光三要素+相机信息）。
 * <p>
 * 使用已有的 metadata-extractor 库 (v2.19.0) 读取EXIF数据。
 * 提取的字段存储在 media.exif_metadata JSONB列中，支持灵活扩展。
 * <p>
 * 提取失败不阻塞上传流程，exif_metadata 保持 null。
 */
@Service
public class ExifExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ExifExtractionService.class);

    /**
     * 从图片文件中提取EXIF拍摄参数。
     *
     * @param sourceFile 原始图片文件
     * @return EXIF参数Map，提取失败返回null
     */
    public Map<String, Object> extractExifMetadata(File sourceFile) {
        if (sourceFile == null || !sourceFile.exists() || !sourceFile.isFile()) {
            return null;
        }
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(sourceFile);
            Map<String, Object> exif = new LinkedHashMap<>();

            // --- ExifIFD0: 相机品牌/型号 ---
            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0 != null) {
                putIfPresent(exif, "camera_make", ifd0.getString(ExifIFD0Directory.TAG_MAKE));
                putIfPresent(exif, "camera_model", ifd0.getString(ExifIFD0Directory.TAG_MODEL));
            }

            // --- ExifSubIFD: 曝光三要素 + 焦距 + 镜头 ---
            ExifSubIFDDirectory subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (subIfd != null) {
                // 光圈 (f-number), 例如 "f/2.8"
                String fNumber = subIfd.getString(ExifSubIFDDirectory.TAG_FNUMBER);
                if (fNumber != null) {
                    exif.put("aperture", fNumber);
                }

                // 快门速度 (曝光时间), 例如 "1/125" 或 "0.5 sec"
                String exposureTime = subIfd.getString(ExifSubIFDDirectory.TAG_EXPOSURE_TIME);
                if (exposureTime != null) {
                    exif.put("shutter_speed", exposureTime);
                }

                // ISO感光度
                Integer iso = subIfd.getInteger(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT);
                if (iso != null && iso > 0) {
                    exif.put("iso", iso);
                }

                // 焦距, 例如 "50 mm"
                String focalLength = subIfd.getString(ExifSubIFDDirectory.TAG_FOCAL_LENGTH);
                if (focalLength != null) {
                    exif.put("focal_length", focalLength);
                }

                // 镜头型号
                putIfPresent(exif, "lens_model", subIfd.getString(ExifSubIFDDirectory.TAG_LENS_MODEL));

                // 闪光灯
                Integer flash = subIfd.getInteger(ExifSubIFDDirectory.TAG_FLASH);
                if (flash != null) {
                    exif.put("flash", flash);
                }

                // 白平衡
                Integer whiteBalance = subIfd.getInteger(ExifSubIFDDirectory.TAG_WHITE_BALANCE_MODE);
                if (whiteBalance != null) {
                    exif.put("white_balance", whiteBalance);
                }

                // 测光模式
                Integer meteringMode = subIfd.getInteger(ExifSubIFDDirectory.TAG_METERING_MODE);
                if (meteringMode != null) {
                    exif.put("metering_mode", meteringMode);
                }
            }

            return exif.isEmpty() ? null : exif;
        } catch (Exception e) {
            log.warn("Failed to extract EXIF metadata from {}: {}", sourceFile.getName(), e.getMessage());
            return null;
        }
    }

    private void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value.trim());
        }
    }
}
