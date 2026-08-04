package com.yingshi.server.service.upload;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * 探测图片/视频真实元数据，校验客户端声明值与服务端实测值一致。
 * R1-H-2: 真实尺寸/像素校验，防止解压炸弹和元数据造假。
 */
public class MediaMetadataProbe {

    // 允许 5% 误差（客户端 EXIF 可能略有偏差）
    private static final double SIZE_TOLERANCE = 0.05;
    // 最大像素数 1 亿（防止解压炸弹 OOM）
    private static final long MAX_PIXELS = 100_000_000L;

    public record ImageProbeResult(int width, int height, String format) {}

    public record VideoProbeResult(int width, int height, long durationMillis) {}

    public static ImageProbeResult probeImage(Path sourcePath) throws IOException {
        return probeImage(sourcePath.toFile());
    }

    public static ImageProbeResult probeImage(java.io.File sourceFile) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(sourceFile)) {
            return probeImageFromStream(iis);
        }
    }

    public static ImageProbeResult probeImage(InputStream inputStream) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(inputStream)) {
            return probeImageFromStream(iis);
        }
    }

    private static ImageProbeResult probeImageFromStream(ImageInputStream iis) throws IOException {
        if (iis == null) {
            throw new IOException("Failed to create ImageInputStream (input is null or unreadable).");
        }
        Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
        if (!readers.hasNext()) {
            throw new IOException("Unsupported image format");
        }
        ImageReader reader = readers.next();
        reader.setInput(iis);
        try {
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if ((long) width * height > MAX_PIXELS) {
                throw new IOException("Image exceeds max pixels: " + width + "x" + height);
            }
            String format = reader.getFormatName();
            return new ImageProbeResult(width, height, format);
        } finally {
            reader.dispose();
        }
    }

    public static void verifyImage(Path sourcePath, int declaredWidth, int declaredHeight) throws IOException {
        verifyImage(sourcePath.toFile(), declaredWidth, declaredHeight);
    }

    public static void verifyImage(java.io.File sourceFile, int declaredWidth, int declaredHeight) throws IOException {
        ImageProbeResult actual = probeImage(sourceFile);
        verifyDimensions(actual, declaredWidth, declaredHeight);
    }

    public static void verifyImage(InputStream inputStream, int declaredWidth, int declaredHeight) throws IOException {
        ImageProbeResult actual = probeImage(inputStream);
        verifyDimensions(actual, declaredWidth, declaredHeight);
    }

    private static void verifyDimensions(ImageProbeResult actual, int declaredWidth, int declaredHeight) throws IOException {
        // EXIF 方向契约: 客户端(LifeConsoleUploadBridge / SystemMediaUploadSupport)上报的宽高是
        // "应用 EXIF orientation 旋转后的显示尺寸"——方向为 90°/270° 时会把原始宽高对调。
        // 而 ImageIO 读出的是原始容器尺寸。因此带旋转 EXIF 的图片 declared 与 actual 恰好互为转置,
        // 这是合法匹配而非元数据造假(像素总量不变,解压炸弹防护不受影响)。
        boolean normalMatch = dimensionWithinTolerance(actual.width(), declaredWidth)
                && dimensionWithinTolerance(actual.height(), declaredHeight);
        boolean swappedMatch = dimensionWithinTolerance(actual.width(), declaredHeight)
                && dimensionWithinTolerance(actual.height(), declaredWidth);
        if (!normalMatch && !swappedMatch) {
            throw new IOException("Image dimension mismatch: declared=" + declaredWidth + "x" + declaredHeight
                    + " actual=" + actual.width() + "x" + actual.height());
        }
    }

    /** declared <= 0 表示客户端未提供该维度, 视为通过; 否则要求相对误差在容差内. */
    private static boolean dimensionWithinTolerance(int actual, int declared) {
        if (declared <= 0 || actual <= 0) return true;
        double diff = Math.abs(actual - declared) / (double) actual;
        return diff <= SIZE_TOLERANCE;
    }

    // 视频用 ffmpeg，本项目 LocalMediaStorageService 已有 ffmpeg 调用参考
    // 这里先实现图片校验，视频校验在 LocalMediaStorageService 内扩展
}