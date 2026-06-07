package com.example.community.image.service;

import com.example.community.global.exception.GeneralException;
import com.example.community.global.response.StatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

@Component
public class ImageProcessor {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_MIME_TYPES  = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;

    private static final List<byte[]> MAGIC_BYTES = List.of(
            new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},  // JPEG
            new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47},          // PNG
            new byte[]{0x52, 0x49, 0x46, 0x46}                  // WEBP
    );
    // 이미지 유효성 검증
    public void validate(MultipartFile file) {
        checkNotEmpty(file);
        checkSize(file);
        checkExtension(file.getOriginalFilename());
        checkMimeType(file.getContentType());
        checkMagicBytes(file);
    }
    // 빈 파일 검증
    private void checkNotEmpty(MultipartFile file) {
        if (file.isEmpty())
            throw new GeneralException(StatusCode.INVALID_IMAGE_EMPTY);
    }
    // 파일 크기 검증 (최대 10MB)
    private void checkSize(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE)
            throw new GeneralException(StatusCode.INVALID_IMAGE_SIZE);
    }
    // 확장자 검증 (jpg, jpeg, png, gif, webp 허용)
    private void checkExtension(String filename) {
        String ext = extractExtension(filename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext))
            throw new GeneralException(StatusCode.INVALID_IMAGE_EXTENSION);
    }

    private void checkMimeType(String contentType) {
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase()))
            throw new GeneralException(StatusCode.INVALID_IMAGE_MIME_TYPE);
    }

    private void checkMagicBytes(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = is.readNBytes(8);
            boolean valid = MAGIC_BYTES.stream()
                    .anyMatch(magic -> startsWith(header, magic));
            if (!valid)
                throw new GeneralException(StatusCode.INVALID_IMAGE_CONTENT);
        } catch (IOException e) {
            throw new GeneralException(StatusCode.IMAGE_STORE_FAILED);
        }
    }

    public String extractExtension(String filename) {
        if (filename == null || !filename.contains("."))
            throw new GeneralException(StatusCode.INVALID_IMAGE_EXTENSION);
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private boolean startsWith(byte[] source, byte[] prefix) {
        if (source.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (source[i] != prefix[i]) return false;
        }
        return true;
    }


}