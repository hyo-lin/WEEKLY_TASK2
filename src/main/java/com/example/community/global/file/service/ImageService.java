package com.example.community.global.file.service;

import com.example.community.global.exception.GeneralException;
import com.example.community.global.response.StatusCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
public class ImageService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.url-path}")
    private String urlPath;

    public String upload(MultipartFile file) {
        try {
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath);

            return urlPath + fileName;

        } catch (IOException e) {
            log.error("이미지 업로드 실패", e);
            throw new GeneralException(StatusCode.IMAGE_UPLOAD_FAILED);
        }
    }
}
